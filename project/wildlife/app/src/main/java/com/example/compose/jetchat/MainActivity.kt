package com.example.compose.jetchat

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.compose.jetchat.capture.CaptureFragment

class MainActivity : AppCompatActivity(R.layout.activity_main) {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    if (savedInstanceState == null) {
      supportFragmentManager.beginTransaction()
        .replace(R.id.container, CaptureFragment())
        .commit()
    }
  }
}

