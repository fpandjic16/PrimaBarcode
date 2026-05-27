package com.prima.barcode.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Place Geist TTFs in res/font/.
// import com.prima.barcode.R

// TODO: place Geist TTFs in res/font/ and uncomment the Font() lines below
val Geist = FontFamily.Default
//    FontFamily(
//        Font(R.font.geist_regular, FontWeight.Normal),
//        Font(R.font.geist_medium,  FontWeight.Medium),
//        Font(R.font.geist_semibold, FontWeight.SemiBold),
//    )

val GeistMono = FontFamily.Monospace
//    FontFamily(
//        Font(R.font.geist_mono_regular, FontWeight.Normal),
//        Font(R.font.geist_mono_medium,  FontWeight.Medium),
//    )

/** Drives the +2 / +4 sp text-size preference. Provided by [PrimaBarcodeTheme]. */
val LocalTextSizeOffset = compositionLocalOf { 0 }

/** Drives the uppercase-text preference. Provided by [PrimaBarcodeTheme]. */
val LocalUppercaseEnabled = compositionLocalOf { false }

/** Transforms [this] to uppercase when the uppercase-text preference is active. */
val String.uppercased: String
    @Composable
    @ReadOnlyComposable
    get() = if (LocalUppercaseEnabled.current) this.uppercase() else this

enum class TextSize(val spOffset: Int, val label: String) {
    NORMAL(2, "Normal"),
    LARGER(4, "Larger"),
}

/**
 * Base typography — consumed by [scaledTypography] in the theme.
 * Never use directly in composables; go through [MaterialTheme.typography] instead.
 */
internal val BasePrimaTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = Geist, fontWeight = FontWeight.Medium,
        fontSize = 40.sp, lineHeight = 44.sp, letterSpacing = (-0.5).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = Geist, fontWeight = FontWeight.Medium,
        fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.2).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Geist, fontWeight = FontWeight.Medium,
        fontSize = 18.sp, lineHeight = 24.sp, letterSpacing = (-0.1).sp,
    ),
    titleSmall = TextStyle(
        fontFamily = Geist, fontWeight = FontWeight.Medium,
        fontSize = 15.sp, lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Geist, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Geist, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Geist, fontWeight = FontWeight.Normal,
        fontSize = 13.sp, lineHeight = 18.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = GeistMono, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 1.1.sp,
    ),
)

internal fun scaledTypography(offset: Int) = Typography(
    displayLarge = TextStyle(
        fontFamily = Geist, fontWeight = FontWeight.Medium,
        fontSize = (40 + offset).sp, lineHeight = (44 + offset).sp, letterSpacing = (-0.5).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = Geist, fontWeight = FontWeight.Medium,
        fontSize = (22 + offset).sp, lineHeight = (28 + offset).sp, letterSpacing = (-0.2).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Geist, fontWeight = FontWeight.Medium,
        fontSize = (18 + offset).sp, lineHeight = (24 + offset).sp, letterSpacing = (-0.1).sp,
    ),
    titleSmall = TextStyle(
        fontFamily = Geist, fontWeight = FontWeight.Medium,
        fontSize = (15 + offset).sp, lineHeight = (20 + offset).sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Geist, fontWeight = FontWeight.Normal,
        fontSize = (16 + offset).sp, lineHeight = (24 + offset).sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Geist, fontWeight = FontWeight.Normal,
        fontSize = (14 + offset).sp, lineHeight = (20 + offset).sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Geist, fontWeight = FontWeight.Normal,
        fontSize = (13 + offset).sp, lineHeight = (18 + offset).sp,
    ),
    labelSmall = TextStyle(
        fontFamily = GeistMono, fontWeight = FontWeight.Medium,
        fontSize = (11 + offset).sp, lineHeight = (14 + offset).sp, letterSpacing = 1.1.sp,
    ),
)

/**
 * Counter style — large mono digits for x/Y on Recording.
 * Reads [LocalTextSizeOffset] automatically; use inside any @Composable.
 */
val monoCounter: TextStyle
    @Composable
    @ReadOnlyComposable
    get() = TextStyle(
        fontFamily = GeistMono, fontWeight = FontWeight.Medium,
        fontSize = (17 + LocalTextSizeOffset.current).sp, letterSpacing = 0.4.sp,
    )

/**
 * Inline mono label — codes, timestamps.
 * Reads [LocalTextSizeOffset] automatically; use inside any @Composable.
 */
val monoLabel: TextStyle
    @Composable
    @ReadOnlyComposable
    get() = TextStyle(
        fontFamily = GeistMono, fontWeight = FontWeight.Normal,
        fontSize = (12 + LocalTextSizeOffset.current).sp, letterSpacing = 0.4.sp,
    )
