package com.prima.barcode.data.sound

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import timber.log.Timber

/** Percentage of the media stream's volume. */
private const val VOLUME = 80

/**
 * The media stream, not notifications.
 *
 * These used to play on STREAM_NOTIFICATION, which Android silences whenever the device is on
 * vibrate or silent and which follows the notification volume. A scanner left on vibrate therefore
 * went quiet while the app's own Sound setting still said "on" — and the tone it lost first was
 * the rejection, the one that matters most. Media is not muted by the ringer mode, so the app's
 * own setting is what decides; the operator still controls loudness with the volume keys.
 */
private const val STREAM = AudioManager.STREAM_MUSIC

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
        ToneGenerator(STREAM, VOLUME)
    }.onFailure { Timber.w(it, "No ToneGenerator — scan sounds are off on this device") }
        .getOrNull()

    /** Short single beep — a scan that landed. */
    fun confirm() = play(ToneGenerator.TONE_PROP_BEEP, 80)

    /** Rejection tone — now only for a scanned code that isn't a sign-in code. */
    fun error() = play(ToneGenerator.TONE_PROP_NACK, 200)

    /**
     * Two short beeps — the scan brought up a warning: an over-scan, a barcode that is not on the
     * document, or a document number that was not found.
     *
     * One signal for all three on purpose. Whether the scan counted or not, the operator's next
     * move is the same — look at the screen — and a single sound for that is easier to learn than
     * a distinction they would have to stop and decode. An operator working through a run of items
     * hears a rhythm of single beeps, and a double one breaks it.
     */
    fun warning() = play(ToneGenerator.TONE_PROP_BEEP2, 300)

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
