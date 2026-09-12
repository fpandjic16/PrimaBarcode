package com.prima.barcode.data.sound

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import timber.log.Timber

/** Percentage of the notification stream's volume. Matches what CameraPreview used. */
private const val VOLUME = 80

/**
 * Audible scan feedback, the counterpart to `HapticEngine`.
 *
 * A warehouse is loud and a handheld is often on a belt or in a gloved hand, so vibration alone is
 * easy to miss — particularly the one signal that matters most, a scan that did *not* land. This
 * plays on both input paths: the tone used to live inside `CameraPreview`, which meant the camera
 * beeped and the hardware trigger never did.
 *
 * Unlike the vibrator, [ToneGenerator] holds an audio track that has to be released, so instances
 * come from [rememberSoundEngine] rather than being constructed directly.
 */
class SoundEngine {

    // Constructing this allocates an AudioTrack and is documented to fail when audio resources are
    // busy. A device that can't give us one should simply be quiet, not crash mid-scan.
    private var toneGen: ToneGenerator? = runCatching {
        ToneGenerator(AudioManager.STREAM_NOTIFICATION, VOLUME)
    }.onFailure { Timber.w(it, "No ToneGenerator — scan sounds are off on this device") }
        .getOrNull()

    /** Short single beep — a scan that landed. */
    fun confirm() = play(ToneGenerator.TONE_PROP_BEEP, 80)

    /** Rejection tone — barcode not on the document, or a code that isn't a sign-in code. */
    fun error() = play(ToneGenerator.TONE_PROP_NACK, 200)

    private fun play(tone: Int, durationMs: Int) {
        runCatching { toneGen?.startTone(tone, durationMs) }
    }

    fun release() {
        runCatching { toneGen?.release() }
        toneGen = null
    }
}

/** Ties a [SoundEngine]'s audio track to the composition that uses it. */
@Composable
fun rememberSoundEngine(): SoundEngine {
    val engine = remember { SoundEngine() }
    DisposableEffect(engine) { onDispose { engine.release() } }
    return engine
}
