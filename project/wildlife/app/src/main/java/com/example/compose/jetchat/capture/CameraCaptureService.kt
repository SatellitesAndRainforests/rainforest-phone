package com.example.compose.jetchat.capture

import android.app.Notification
import android.app.Service
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.MediaStore
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Foreground service that takes a single photo with the BACK camera and exits.
 * Start with:
 *   ContextCompat.startForegroundService(
 *     context,
 *     CameraCaptureService.intent(context, flashMode = ImageCapture.FLASH_MODE_OFF, jpegQuality = 90)
 *   )
 */
class CameraCaptureService : LifecycleService() {

    private lateinit var executor: ExecutorService
    private var wakeLock: PowerManager.WakeLock? = null
    private var imageCapture: ImageCapture? = null

    override fun onCreate() {
        super.onCreate()
        executor = Executors.newSingleThreadExecutor()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Start foreground immediately (mandatory for long-running/background camera work)
        startForeground(NOTIF_ID, buildNotification("Preparing camera…"))

        // Acquire a partial wakelock so CPU stays on while we open camera & save
        acquireWakeLock()

        val flashMode = intent?.getIntExtra(EXTRA_FLASH_MODE, ImageCapture.FLASH_MODE_OFF)
            ?: ImageCapture.FLASH_MODE_OFF
        val jpegQuality = intent?.getIntExtra(EXTRA_JPEG_QUALITY, 92) ?: 92

        bindCameraAndShootOnce(flashMode, jpegQuality)

        // We finish ourselves after the capture; no need to restart if killed.
        return super.onStartCommand(intent, flags, startId)

    }

    override fun onDestroy() {
        super.onDestroy()
        imageCapture = null
        releaseWakeLock()
        executor.shutdown()
    }

    override fun onBind(intent: Intent): IBinder? = super.onBind(intent)

    // ---- Camera flow ----

    private fun bindCameraAndShootOnce(
        flashMode: Int,
        jpegQuality: Int
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()

                // Build ImageCapture use case
                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setJpegQuality(jpegQuality.coerceIn(1, 100))
                    .build()
                capture.flashMode = flashMode
                imageCapture = capture

                val selector = CameraSelector.DEFAULT_BACK_CAMERA

                cameraProvider.unbindAll()
                // LifecycleService gives us a LifecycleOwner (`this`)
                cameraProvider.bindToLifecycle(this, selector, capture)

                // Save destination: Pictures/RainforestCam
                val fileName = "rainforest_${System.currentTimeMillis()}.jpg"
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(
                            MediaStore.Images.Media.RELATIVE_PATH,
                            "Pictures/RainforestCam"
                        )
                    }
                }

                val output = ImageCapture.OutputFileOptions.Builder(
                    contentResolver,
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    values
                ).build()

                // Update notification while capturing
                updateNotification("Capturing…")

                capture.takePicture(
                    output,
                    executor,
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                            updateNotification("Saved: $fileName")
                            stopSafely()
                        }
                        override fun onError(exception: ImageCaptureException) {
                            updateNotification("Capture failed: ${exception.message}")
                            stopSafely()
                        }
                    }
                )
            } catch (t: Throwable) {
                updateNotification("Camera error: ${t.message}")
                stopSafely()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    // ---- Foreground notification helpers ----

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val ch = NotificationChannel(
                NOTIF_CHANNEL_ID,
                "Rainforest Camera",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Background capture status" }
            nm.createNotificationChannel(ch)
        }
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("Rainforest capture")
            .setContentText(text)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification(text))
    }

    // ---- Power/wakelock helpers ----

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$WAKE_TAG:Capture").apply {
            setReferenceCounted(false)
            acquire(2 * 60 * 1000L) // safety timeout: 2 minutes
        }
    }

    private fun releaseWakeLock() {
        try { if (wakeLock?.isHeld == true) wakeLock?.release() } catch (_: Throwable) {}
        wakeLock = null
    }

    private fun stopSafely() {

      releaseWakeLock()

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        stopForeground(Service.STOP_FOREGROUND_REMOVE)
      } else {
        @Suppress("DEPRECATION")
        stopForeground(true) // pre-24 API
      }
        stopSelf()
      }

    companion object {
        private const val NOTIF_CHANNEL_ID = "rainforest_camera_channel"
        private const val NOTIF_ID = 7
        private const val WAKE_TAG = "RainforestCam"

        const val EXTRA_FLASH_MODE = "extra_flash_mode"   // ImageCapture.FLASH_MODE_*
        const val EXTRA_JPEG_QUALITY = "extra_jpeg_quality" // 1..100

        fun intent(
            context: Context,
            flashMode: Int = ImageCapture.FLASH_MODE_OFF,
            jpegQuality: Int = 92
        ): Intent = Intent(context, CameraCaptureService::class.java).apply {
            putExtra(EXTRA_FLASH_MODE, flashMode)
            putExtra(EXTRA_JPEG_QUALITY, jpegQuality)
        }
    }

}

