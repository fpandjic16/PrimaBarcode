package com.prima.barcode.data.auth

import com.prima.barcode.ui.theme.Language
import com.prima.barcode.ui.theme.TextSize

data class AppSettings(
    val textSize: TextSize = TextSize.NORMAL,
    val uppercaseText: Boolean = false,
    val language: Language = Language.ENGLISH,
    val lastScannedLines: Int = 5,
    val autoScan: Boolean = false,
    val debounceTime: Int = 500,
    val hapticEnabled: Boolean = true,
    val muteSound: Boolean = false,
    val lastLocationCode: String = "",
    val lastRcCode: String = "",
    val liveMode: Boolean = false,
    val disabledDocTypes: Set<String> = emptySet(),
)
