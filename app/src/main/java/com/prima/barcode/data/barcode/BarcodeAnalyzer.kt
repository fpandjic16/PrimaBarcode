package com.prima.barcode.data.barcode

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage

class BarcodeAnalyzer(
    private val debounceMs: Long = 1500L,
    private val onResult: (String) -> Unit,
) : ImageAnalysis.Analyzer {

    private val scanner = BarcodeScanning.getClient()
    private var lastValue: String? = null
    private var lastTime: Long = 0L

    override fun analyze(image: ImageProxy) {
        val mediaImage = image.image
        if (mediaImage == null) { image.close(); return }
        val inputImage = InputImage.fromMediaImage(mediaImage, image.imageInfo.rotationDegrees)
        scanner.process(inputImage)
            .addOnSuccessListener { barcodes ->
                val best = barcodes
                    .filter { it.rawValue != null }
                    .maxByOrNull { b -> b.boundingBox?.let { r -> r.width() * r.height() } ?: 0 }
                val value = best?.rawValue ?: return@addOnSuccessListener
                val now = System.currentTimeMillis()
                if (value != lastValue || now - lastTime > debounceMs) {
                    lastValue = value
                    lastTime = now
                    onResult(value)
                }
            }
            .addOnCompleteListener { image.close() }
    }
}
