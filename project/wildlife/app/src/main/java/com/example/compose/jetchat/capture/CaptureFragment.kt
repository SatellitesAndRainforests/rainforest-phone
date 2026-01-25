package com.example.compose.jetchat.capture

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.compose.jetchat.theme.JetchatTheme

// for usb
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.example.compose.jetchat.usb.UsbSensorService
import android.app.PendingIntent
import android.hardware.usb.UsbManager


class CaptureFragment : Fragment() {

  private val status = mutableStateOf("Ready")

  private val requestPerms = registerForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
  ) { results ->
    val camOk = results[Manifest.permission.CAMERA] == true
    val notifOk =
    if (Build.VERSION.SDK_INT >= 33) results[Manifest.permission.POST_NOTIFICATIONS] == true else true

    if (camOk && notifOk) startCapture() else status.value = "Permission denied"
  }

  private val ACTION_USB_PERMISSION = "com.example.compose.jetchat.USB_PERMISSION"

  private val usbPermReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
      if (intent.action != ACTION_USB_PERMISSION) return
      val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
      if (granted) {
        sensorStatus.value = "USB permission granted. Listening…"
        val ctx = requireContext()
        ContextCompat.startForegroundService(ctx, Intent(ctx, UsbSensorService::class.java))
      } else {
        sensorStatus.value = "USB permission denied"
      }
    }
  }

  private val sensorStatus = mutableStateOf("Sensor: stopped")

  private val usbReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
      if (intent.action == UsbSensorService.ACTION_DETECTION) {
        sensorStatus.value = "DETECTED! " + System.currentTimeMillis()
      }

    }
  }

  override fun onStart() {

    super.onStart()

    ContextCompat.registerReceiver(
      requireContext(),
      usbReceiver,
      IntentFilter(UsbSensorService.ACTION_DETECTION),
      ContextCompat.RECEIVER_NOT_EXPORTED
    )

    ContextCompat.registerReceiver(
      requireContext(),
      usbPermReceiver,
      IntentFilter(ACTION_USB_PERMISSION),
      ContextCompat.RECEIVER_NOT_EXPORTED
    )
  }

  override fun onStop() {
    try { requireContext().unregisterReceiver(usbReceiver) } catch (_: Throwable) {}
    try { requireContext().unregisterReceiver(usbPermReceiver) } catch (_: Throwable) {}
    super.onStop()
  }

  override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
    return ComposeView(requireContext()).apply {
      setContent {
        JetchatTheme {
          Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
          ) {

            Text("")

            Text("ForestCam")

            Button(onClick = { onTakePhotoPressed() }) {
              Text("Take photo")
            }

            Button(onClick = {
              val ctx = requireContext()
              val usb = ctx.getSystemService(Context.USB_SERVICE) as UsbManager
              val device = usb.deviceList.values.firstOrNull()
              if (device == null) {
                sensorStatus.value = "No USB device found"
                return@Button
              }

              if (usb.hasPermission(device)) {
                ContextCompat.startForegroundService(ctx, Intent(ctx, UsbSensorService::class.java))
                sensorStatus.value = "Sensor: listening…"
              } else {
                val permIntent = Intent(ACTION_USB_PERMISSION).setPackage(ctx.packageName)
                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                PendingIntent.FLAG_MUTABLE
                else 0
                val pi = PendingIntent.getBroadcast(ctx, 0, permIntent, flags)
                usb.requestPermission(device, pi)
                sensorStatus.value = "Requesting USB permission… (watch popup)"
              }
            }) { Text("Start sensor") }


            Button(onClick = {
              val ctx = requireContext()
              ctx.stopService(Intent(ctx, UsbSensorService::class.java))
              sensorStatus.value = "Sensor: stopped"
            }) { Text("Stop sensor") }

            Text(sensorStatus.value)
            Text("- - - - - -")

            Text(status.value)

            Text("Saves to Pictures/RainforestCam (check Photos/Gallery).")

          }
        }
      }
    }
  }

  private fun onTakePhotoPressed() {
    if (hasAllPerms()) startCapture()
    else requestPerms.launch(requiredPerms())
  }

  private fun startCapture() {
    status.value = "Capturing… (watch notification)"
    val ctx = requireContext()
    ContextCompat.startForegroundService(ctx, CameraCaptureService.intent(ctx))
  }

  private fun hasAllPerms(): Boolean {
    val camOk = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) ==
    PackageManager.PERMISSION_GRANTED
    val notifOk = if (Build.VERSION.SDK_INT >= 33) {
      ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) ==
      PackageManager.PERMISSION_GRANTED
    } else true
    return camOk && notifOk
  }

  private fun requiredPerms(): Array<String> =
  if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.CAMERA, Manifest.permission.POST_NOTIFICATIONS)
  else arrayOf(Manifest.permission.CAMERA)
}
