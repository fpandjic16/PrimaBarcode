package com.prima.barcode.data.auth

import com.prima.barcode.data.model.DocTypeFilterMode
import com.prima.barcode.ui.theme.Language
import com.prima.barcode.ui.theme.TextSize

/**
 * The defaults here are the only copy: [AppSettingsStore] falls back to them for anything not yet
 * stored, so a new operator starts from exactly these values. Croatian, larger text and a 200 ms
 * debounce were chosen for the people who actually use these handhelds; an operator who has saved
 * their settings keeps what they saved.
 */
data class AppSettings(
    val textSize: TextSize = TextSize.LARGER,
    val uppercaseText: Boolean = false,
    val language: Language = Language.CROATIAN,
    val debounceTime: Int = 200,
    val hapticEnabled: Boolean = true,
    val soundEnabled: Boolean = true,
    val warnOnOver: Boolean = true,
    val backgroundSync: Boolean = false,
    val lastLocationCode: String = "",
    val lastRcCode: String = "",
    val disabledDocTypes: Set<String> = emptySet(),
    val docTypeFilters: Map<String, DocTypeFilterMode> = emptyMap(),
    val debuggerActive: Boolean = false,
)
