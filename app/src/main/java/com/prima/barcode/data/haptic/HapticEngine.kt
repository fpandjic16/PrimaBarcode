package com.prima.barcode.data.haptic

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator

class HapticEngine(context: Context) {
    private val vibrator = context.getSystemService(Vibrator::class.java)

    private fun vibrate(effect: VibrationEffect) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) vibrator.vibrate(effect)
    }

    // Very light tap — keypad key press
    fun tick() = vibrate(VibrationEffect.createOneShot(12, 60))

    // Medium pulse — quantity increment / decrement
    fun bump() = vibrate(VibrationEffect.createOneShot(28, VibrationEffect.DEFAULT_AMPLITUDE))

    // Short single pulse — confirmed scan or exact-quantity reached
    fun confirm() = vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))

    // Double pulse (growing) — unknown barcode or scan error
    fun error() = vibrate(VibrationEffect.createWaveform(longArrayOf(0, 70, 60, 120), -1))
}
