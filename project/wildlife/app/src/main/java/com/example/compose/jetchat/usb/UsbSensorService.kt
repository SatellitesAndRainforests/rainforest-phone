package com.example.compose.jetchat.usb

import android.app.*
import android.content.*
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.compose.jetchat.capture.CameraCaptureService
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import java.util.concurrent.Executors

class UsbSensorService : Service() {

  private val io = Executors.newSingleThreadExecutor()
  @Volatile private var running = true
  private var lastShotMs = 0L

  companion object {
    const val ACTION_DETECTION = "com.example.compose.jetchat.ACTION_DETECTION"
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    startForeground(101, notif("Sensor listening…"))

    io.execute {
      val usb = getSystemService(USB_SERVICE) as UsbManager
      val driver = UsbSerialProber.getDefaultProber().findAllDrivers(usb).firstOrNull()
      if (driver == null) { Log.i("UsbSensor", "No driver"); stopSelf(); return@execute }

      val conn = usb.openDevice(driver.device)
      if (conn == null) { Log.i("UsbSensor", "No permission (tap Allow on phone)"); stopSelf(); return@execute }

      val port = driver.ports.first()
      port.open(conn)
      port.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)

      val buf = ByteArray(64)
      val sb = StringBuilder()

      while (running) {
        val n = port.read(buf, 200)
        if (n > 0) {
          sb.append(String(buf, 0, n))
          while (true) {
            val idx = sb.indexOf("\n")
            if (idx < 0) break
            val line = sb.substring(0, idx).trim()
            sb.delete(0, idx + 1)

            Log.i("UsbSensor", "RX: $line")

            if (line == "DETECTION") {

              sendBroadcast(Intent(ACTION_DETECTION))

              val now = System.currentTimeMillis()
              if (now - lastShotMs > 1500) { // extra cooldown
                lastShotMs = now
                ContextCompat.startForegroundService(
                  this,
                  CameraCaptureService.intent(this)
                )
              }
            }
          }
        }
      }
    }

    return START_STICKY
  }

  override fun onDestroy() { running = false; io.shutdownNow(); super.onDestroy() }
  override fun onBind(intent: Intent?): IBinder? = null

  private fun notif(text: String): Notification {
    val chId = "sensor_ch"
    if (Build.VERSION.SDK_INT >= 26) {
      val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
      nm.createNotificationChannel(NotificationChannel(chId, "Sensor", NotificationManager.IMPORTANCE_LOW))
    }
    return NotificationCompat.Builder(this, chId)
      .setSmallIcon(android.R.drawable.ic_menu_info_details)
      .setContentTitle("Rainforest sensor")
      .setContentText(text)
      .setOngoing(true)
      .build()
  }
}

