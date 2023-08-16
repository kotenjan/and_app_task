package com.example.timemanager

import android.app.Application
import android.media.MediaPlayer
import android.os.VibrationEffect
import android.os.Vibrator

class TaskSoundSystem(private val application: Application) {

    fun notifyTaskFinished() {
        val mp = MediaPlayer.create(application, R.raw.finish_sound)
        mp.setOnCompletionListener { mp.release() }
        mp.start()

        val vibrator = application.getSystemService(Vibrator::class.java)

        if (vibrator?.hasVibrator() == true) {
            val pattern = longArrayOf(0, 200, 100, 200)
            val effect = VibrationEffect.createWaveform(pattern, -1)
            vibrator.vibrate(effect)
        }
    }

}