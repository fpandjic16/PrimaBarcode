package com.prima.barcode.data.haptic

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator

class HapticEngine(context: Context) {
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

    // Short single pulse — confirmed scan or exact-quantity reached
    fun confirm() {
        vibrator.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    // Double pulse (growing) — unknown barcode or scan error
    fun error() {
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 70, 60, 120), -1))
    }
}
