package com.prima.barcode.ui.component

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.prima.barcode.data.barcode.BarcodeAnalyzer
import timber.log.Timber
import java.util.concurrent.Executors

@Composable
fun CameraPreview(
    continuous: Boolean,
    onBarcode: (String) -> Unit,
    onClose: () -> Unit,
    muteSound: Boolean = false,
    debounceMs: Int = 500,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }

    val latestOnBarcode = rememberUpdatedState(onBarcode)
    val latestOnClose = rememberUpdatedState(onClose)
    val latestContinuous = rememberUpdatedState(continuous)
    val latestMuteSound = rememberUpdatedState(muteSound)
    val latestDebounceMs = rememberUpdatedState(debounceMs)

    DisposableEffect(lifecycleOwner) {
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val analysisExecutor = Executors.newSingleThreadExecutor()
        var cameraProvider: ProcessCameraProvider? = null
        var toneGen: ToneGenerator? = null
        var lastScanMs = 0L

        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            runCatching {
                val provider = future.get()
                cameraProvider = provider
                toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build().also { imageAnalysis ->
                        imageAnalysis.setAnalyzer(analysisExecutor, BarcodeAnalyzer { barcode ->
                            val now = System.currentTimeMillis()
                            if (now - lastScanMs < latestDebounceMs.value) return@BarcodeAnalyzer
                            lastScanMs = now
                            mainExecutor.execute {
                                if (!latestMuteSound.value) runCatching { toneGen?.startTone(ToneGenerator.TONE_PROP_BEEP, 80) }
                                latestOnBarcode.value(barcode)
                                if (!latestContinuous.value) latestOnClose.value()
                            }
                        })
                    }

                provider.unbindAll()
                val camera = provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis,
                )

                previewView.post {
                    if (previewView.width > 0 && previewView.height > 0) {
                        runCatching {
                            val point = previewView.meteringPointFactory.createPoint(0.5f, 0.5f)
                            val action = FocusMeteringAction.Builder(point)
                                .disableAutoCancel()
                                .build()
                            camera.cameraControl.startFocusAndMetering(action)
                        }
                    }
                }
            }.onFailure { e -> Timber.e(e, "CameraX bind failed") }
        }, mainExecutor)

        onDispose {
            cameraProvider?.unbindAll()
            analysisExecutor.shutdown()
            toneGen?.release()
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        ScanningOverlay(modifier = Modifier.fillMaxSize())
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(end = 16.dp, top = 8.dp)
                .size(44.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0x66000000))
                .clickable(onClick = { latestOnClose.value() }),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = "Close camera",
                tint = Color.White,
            )
        }
    }
}

@Composable
private fun ScanningOverlay(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "scan")
    val lineProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue  = 1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "scanLine",
    )

    androidx.compose.foundation.Canvas(
        modifier = modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
    ) {
        val reticleW = 280.dp.toPx()
        val reticleH = 170.dp.toPx()
        val left   = (size.width  - reticleW) / 2f
        val top    = (size.height - reticleH) / 2f
        val right  = left + reticleW
        val bottom = top  + reticleH

        drawRect(color = Color(0x99000000))
        drawRect(
            color     = Color.Transparent,
            topLeft   = Offset(left, top),
            size      = Size(reticleW, reticleH),
            blendMode = BlendMode.Clear,
        )

        val scanY = top + lineProgress * reticleH
        drawLine(
            color       = Color(0xFF4CF58A),
            start       = Offset(left + 4f, scanY),
            end         = Offset(right - 4f, scanY),
            strokeWidth = 2.dp.toPx(),
        )

        drawCornerBrackets(left, top, right, bottom)
    }
}

private fun DrawScope.drawCornerBrackets(
    left: Float, top: Float, right: Float, bottom: Float,
) {
    val cl = 22.dp.toPx()
    val cs = 3.dp.toPx()
    val c  = Color.White
    drawLine(c, Offset(left,  top),    Offset(left + cl, top),     cs)
    drawLine(c, Offset(left,  top),    Offset(left,  top + cl),    cs)
    drawLine(c, Offset(right, top),    Offset(right - cl, top),    cs)
    drawLine(c, Offset(right, top),    Offset(right, top + cl),    cs)
    drawLine(c, Offset(left,  bottom), Offset(left + cl, bottom),  cs)
    drawLine(c, Offset(left,  bottom), Offset(left, bottom - cl),  cs)
    drawLine(c, Offset(right, bottom), Offset(right - cl, bottom), cs)
    drawLine(c, Offset(right, bottom), Offset(right, bottom - cl), cs)
}
