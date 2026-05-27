package com.prima.barcode.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

@Composable
fun PrimaTheme(
    textSizeOffset: Int = 0,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalTextSizeOffset provides textSizeOffset) {
        MaterialTheme(
            colorScheme = PrimaLightColors,
            typography = scaledTypography(textSizeOffset),
            shapes = PrimaShapes,
            content = content,
        )
    }
}
