package com.prima.barcode.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Prima brand palette — extracted from the 2021 Prima rebrand
 * (Bruketa&Žinić&Grey, custom typography by Nikola Đurek).
 *
 * Each color has a role; do not reuse outside its role unless extending
 * the system deliberately.
 */
object PrimaPalette {
    // Chrome / data surfaces
    val Slate    = Color(0xFF2E3539)
    val SlateAlt = Color(0xFF3A4146)
    val Cream    = Color(0xFFF2EBDE)
    val CreamAlt = Color(0xFFE7DECF)

    // Brand accent
    val Coral     = Color(0xFFC95B4D)
    val CoralDeep = Color(0xFFB04638)

    // Supporting
    val Teal    = Color(0xFF2F5455)
    val Mustard = Color(0xFFB89A3A)
    val Pink    = Color(0xFFE89AAA)
    val Oak     = Color(0xFFB49880)

    // Ink scale
    val Ink  = Color(0xFF1A1C1F)
    val Ink2 = Color(0xFF3D4046)
    val Ink3 = Color(0xFF6F7378)
    val Ink4 = Color(0xFFA4A6AC)
}

/**
 * The four-state status language. Utility colors, *not* brand colors.
 * Read these via [com.prima.barcode.data.model.LineStatus.color] — never
 * hard-code outside that mapping.
 */
object PrimaStatus {
    val Empty   = Color(0xFFCE3A3A)
    val Partial = Color(0xFFC7943A)
    val Exact   = Color(0xFF2E8C5E)
    val Over    = Color(0xFF2D6CE0)

    val EmptyBg   = Color(0x1ACE3A3A) // 10% alpha
    val PartialBg = Color(0x1FC7943A)
    val ExactBg   = Color(0x1A2E8C5E)
    val OverBg    = Color(0x1A2D6CE0)
}

val PrimaLightColors = lightColorScheme(
    primary        = PrimaPalette.Coral,
    onPrimary      = Color.White,
    primaryContainer = PrimaPalette.CreamAlt,
    onPrimaryContainer = PrimaPalette.Ink,

    secondary      = PrimaPalette.Slate,
    onSecondary    = Color.White,

    tertiary       = PrimaPalette.Teal,
    onTertiary     = Color.White,

    background     = PrimaPalette.Cream,
    onBackground   = PrimaPalette.Ink,

    surface        = Color.White,
    onSurface      = PrimaPalette.Ink,
    surfaceVariant = PrimaPalette.CreamAlt,
    onSurfaceVariant = PrimaPalette.Ink2,

    error          = PrimaStatus.Empty,
    onError        = Color.White,

    outline        = Color(0x24000000),
    outlineVariant = Color(0x12000000),
)

val PrimaDarkColors = darkColorScheme(
    primary        = PrimaPalette.Coral,
    onPrimary      = Color.White,
    secondary      = Color(0xFFCBD0D6),
    onSecondary    = PrimaPalette.Slate,
    background     = Color(0xFF0F1113),
    onBackground   = Color(0xFFE7E8EA),
    surface        = Color(0xFF15181C),
    onSurface      = Color(0xFFE7E8EA),
    surfaceVariant = Color(0xFF1F2429),
    onSurfaceVariant = Color(0xFFB6B8BC),
    error          = PrimaStatus.Empty,
    onError        = Color.White,
    outline        = Color(0x40FFFFFF),
    outlineVariant = Color(0x14FFFFFF),
)
