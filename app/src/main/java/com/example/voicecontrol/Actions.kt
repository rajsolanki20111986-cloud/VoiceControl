package com.example.voicecontrol

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.view.KeyEvent

class Actions(context: Context) {

    private val audio = context.getSystemService(AudioManager::class.java)
    private val cam = context.getSystemService(CameraManager::class.java)

    private val torchId: String? = try {
        cam.cameraIdList.firstOrNull {
            cam.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    } catch (e: Exception) {
        null
    }

    // true अगर कमांड पहचानी गई
    fun run(cmd: String): Boolean = when (cmd) {
        "torch on", "flashlight on" -> torch(true)
        "torch off", "flashlight off" -> torch(false)
        "volume up" -> volume(AudioManager.ADJUST_RAISE)
        "volume down" -> volume(AudioManager.ADJUST_LOWER)
        "play music" -> media(KeyEvent.KEYCODE_MEDIA_PLAY)
        "pause music" -> media(KeyEvent.KEYCODE_MEDIA_PAUSE)
        "next song" -> media(KeyEvent.KEYCODE_MEDIA_NEXT)
        "previous song" -> media(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
        else -> false
    }

    private fun torch(on: Boolean): Boolean {
        val id = torchId ?: return true
        try {
            cam.setTorchMode(id, on)
        } catch (e: Exception) {
        }
        return true
    }

    private fun volume(direction: Int): Boolean {
        repeat(2) {
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
        }
        return true
    }

    // Spotify (या कोई भी चालू म्यूज़िक ऐप) को मीडिया-की भेजता है, API key नहीं चाहिए
    private fun media(code: Int): Boolean {
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        return true
    }
}
