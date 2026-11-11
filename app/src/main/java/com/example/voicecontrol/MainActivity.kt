package com.example.voicecontrol

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast

// App खोलते ही permissions माँगता है, सर्विस चालू करता है और बंद हो जाता है।
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ensurePermissionsThenStart()
    }

    private fun neededPermissions(): List<String> {
        val list = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) list.add(Manifest.permission.POST_NOTIFICATIONS)
        return list
    }

    private fun ensurePermissionsThenStart() {
        val missing = neededPermissions().filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) startVoice() else requestPermissions(missing.toTypedArray(), 1)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startVoice()
        } else {
            Toast.makeText(this, "Mic permission चाहिए", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun startVoice() {
        startForegroundService(Intent(this, VoiceService::class.java))
        Toast.makeText(this, "Voice Control चालू", Toast.LENGTH_SHORT).show()
        Handler(Looper.getMainLooper()).postDelayed({ finish() }, 1500)
    }
}
