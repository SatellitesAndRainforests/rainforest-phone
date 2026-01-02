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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return ComposeView(requireContext()).apply {
            setContent {
                JetchatTheme {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("ForestCam")
                        Button(onClick = { onTakePhotoPressed() }) {
                            Text("Take photo")
                        }
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
