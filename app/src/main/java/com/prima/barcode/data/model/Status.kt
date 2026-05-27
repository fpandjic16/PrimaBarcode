package com.prima.barcode.data.model

import androidx.compose.ui.graphics.Color
import com.prima.barcode.ui.theme.PrimaStatus

/**
 * The four-state status language used across every screen that
 * compares scanned vs expected quantity.
 *
 * Cannot go negative — repository must floor at 0 before computing.
 */
enum class LineStatus {
    EMPTY, PARTIAL, EXACT, OVER;

    companion object {
        fun of(scanned: Double, expected: Double): LineStatus = when {
            scanned == 0.0     -> EMPTY
            scanned < expected -> PARTIAL
            scanned == expected -> EXACT
            else               -> OVER
        }
    }
}

/** Foreground / accent color for each state. */
val LineStatus.color: Color
    get() = when (this) {
        LineStatus.EMPTY   -> PrimaStatus.Empty
        LineStatus.PARTIAL -> PrimaStatus.Partial
        LineStatus.EXACT   -> PrimaStatus.Exact
        LineStatus.OVER    -> PrimaStatus.Over
    }

/** Soft tinted background — used on chips and badges. */
val LineStatus.bgColor: Color
    get() = when (this) {
        LineStatus.EMPTY   -> PrimaStatus.EmptyBg
        LineStatus.PARTIAL -> PrimaStatus.PartialBg
        LineStatus.EXACT   -> PrimaStatus.ExactBg
        LineStatus.OVER    -> PrimaStatus.OverBg
    }

/**
 * Floor an Int at 0 — used when undoing scans.
 *  Example: `(scanned - 1).flooredAtZero()`
 */
fun Int.flooredAtZero(): Int = if (this < 0) 0 else this

val LineStatus.label: String get() = when (this) {
    LineStatus.EMPTY   -> "Empty"
    LineStatus.PARTIAL -> "Partial"
    LineStatus.EXACT   -> "Ready"
    LineStatus.OVER    -> "Over-qty"
}

fun Document.scanStatus(): LineStatus {
    if (lines.isEmpty()) return LineStatus.EMPTY
    val s = lines.map { it.status }
    return when {
        s.all { it == LineStatus.EMPTY }                          -> LineStatus.EMPTY
        s.any { it == LineStatus.OVER }                           -> LineStatus.OVER
        s.all { it == LineStatus.EXACT } && extraLines.isEmpty()  -> LineStatus.EXACT
        else                                                      -> LineStatus.PARTIAL
    }
}
