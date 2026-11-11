package com.example.voicecontrol

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream

class VoiceService : Service(), RecognitionListener {

    companion object {
        const val ACTION_STOP = "com.example.voicecontrol.STOP"
        private const val CHANNEL = "voice"
        private const val NOTIF_ID = 1
        private const val WAKE = "hey phone"
        private const val ARMED_MS = 6000L
        // सिर्फ़ इन्हीं वाक्यों को सुनता है, इसीलिए हल्का चलता है
        private const val GRAMMAR =
            "[\"hey phone\",\"torch on\",\"torch off\",\"flashlight on\",\"flashlight off\"," +
                "\"volume up\",\"volume down\",\"play music\",\"pause music\"," +
                "\"next song\",\"previous song\",\"[unk]\"]"
    }

    private var model: Model? = null
    private var speech: SpeechService? = null
    private var tone: ToneGenerator? = null
    private var armedUntil = 0L
    @Volatile private var loading = false
    private lateinit var actions: Actions

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        actions = Actions(this)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Voice control", NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        val n = notification("Loading...")
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIF_ID, n)
        }
        if (speech == null && !loading) {
            loading = true
            Thread { startListening() }.start()
        }
        return START_NOT_STICKY
    }

    private fun notification(text: String): Notification {
        val stop = PendingIntent.getService(
            this, 0,
            Intent(this, VoiceService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Voice Control")
            .setContentText(text)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(android.R.drawable.ic_delete, "Stop", stop).build())
            .build()
    }

    private fun status(text: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, notification(text))
    }

    private fun startListening() {
        try {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                status("Mic permission missing")
                return
            }
            val dir = File(filesDir, "model")
            if (!File(dir, "am").exists()) {
                dir.deleteRecursively()
                val tmp = File(filesDir, "model_tmp")
                tmp.deleteRecursively()
                copyAsset("model", tmp)
                tmp.renameTo(dir)
            }
            model = Model(dir.absolutePath)
            val rec = Recognizer(model, 16000f, GRAMMAR)
            speech = SpeechService(rec, 16000f)
            speech?.startListening(this)
            status("Listening: say \"hey phone\"")
        } catch (e: Exception) {
            status("Error: ${e.message}")
        } finally {
            loading = false
        }
    }

    private fun copyAsset(path: String, dest: File) {
        val children = assets.list(path)
        if (children.isNullOrEmpty()) {
            try {
                dest.parentFile?.mkdirs()
                assets.open(path).use { i -> FileOutputStream(dest).use { o -> i.copyTo(o) } }
            } catch (e: FileNotFoundException) {
                dest.mkdirs()
            }
        } else {
            dest.mkdirs()
            for (c in children) copyAsset("$path/$c", File(dest, c))
        }
    }

    private fun beep() {
        if (tone == null) tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
        tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
    }

    private fun handle(json: String?) {
        val raw = try {
            JSONObject(json ?: return).optString("text")
        } catch (e: Exception) {
            return
        }
        val text = raw.replace("[unk]", "").trim().replace(Regex("\\s+"), " ")
        if (text.isEmpty()) return

        val now = SystemClock.elapsedRealtime()
        var cmd = text
        if (text.startsWith(WAKE)) {
            armedUntil = now + ARMED_MS
            beep()
            cmd = text.removePrefix(WAKE).trim()
            if (cmd.isEmpty()) return
        }
        if (now > armedUntil) return
        if (actions.run(cmd)) armedUntil = 0L
    }

    override fun onResult(hypothesis: String?) = handle(hypothesis)
    override fun onFinalResult(hypothesis: String?) = handle(hypothesis)
    override fun onPartialResult(hypothesis: String?) {}
    override fun onError(exception: Exception?) {
        status("Error: ${exception?.message}")
    }
    override fun onTimeout() {}

    override fun onDestroy() {
        speech?.stop()
        speech?.shutdown()
        speech = null
        model?.close()
        model = null
        tone?.release()
        super.onDestroy()
    }
}
