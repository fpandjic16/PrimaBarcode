package com.prima.barcode.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.prima.barcode.data.model.LineStatus
import com.prima.barcode.data.model.bgColor
import com.prima.barcode.data.model.color
import com.prima.barcode.ui.theme.PrimaPalette
import com.prima.barcode.ui.theme.monoLabel

enum class ChipTone { DEFAULT, INK, CORAL, TEAL, SLATE, CREAM }

@Composable
fun Chip(
    text: String,
    modifier: Modifier = Modifier,
    tone: ChipTone = ChipTone.DEFAULT,
) {
    val (bg, fg, border) = when (tone) {
        ChipTone.DEFAULT -> Triple(Color.White,           PrimaPalette.Ink2, Color(0x24000000))
        ChipTone.INK     -> Triple(PrimaPalette.Ink,      Color.White,       PrimaPalette.Ink)
        ChipTone.CORAL   -> Triple(PrimaPalette.Coral,    Color.White,       PrimaPalette.Coral)
        ChipTone.TEAL    -> Triple(PrimaPalette.Teal,     Color.White,       PrimaPalette.Teal)
        ChipTone.SLATE   -> Triple(PrimaPalette.Slate,    Color.White,       PrimaPalette.Slate)
        ChipTone.CREAM   -> Triple(PrimaPalette.CreamAlt, PrimaPalette.Ink2, Color(0x24000000))
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = text, style = monoLabel.copy(color = fg))
    }
}

/** Status-tinted chip variant — uses the four-state palette. */
@Composable
fun StatusChip(
    text: String,
    status: LineStatus,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(status.bgColor)
            .padding(horizontal = 10.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = text, style = monoLabel.copy(color = status.color))
    }
}
