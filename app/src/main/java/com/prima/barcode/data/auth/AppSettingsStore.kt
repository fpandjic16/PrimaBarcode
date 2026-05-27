package com.prima.barcode.data.auth

import android.content.Context
import com.prima.barcode.ui.theme.Language
import com.prima.barcode.ui.theme.TextSize
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppSettingsStore @Inject constructor(@ApplicationContext private val context: Context) {

    private val prefs by lazy { context.getSharedPreferences("app_settings", Context.MODE_PRIVATE) }

    fun get(): AppSettings = AppSettings(
        textSize         = TextSize.entries.firstOrNull { it.name == prefs.getString("textSize", null) } ?: TextSize.NORMAL,
        uppercaseText    = prefs.getBoolean("uppercaseText", false),
        language         = Language.entries.firstOrNull { it.name == prefs.getString("language", null) } ?: Language.ENGLISH,
        lastScannedLines = prefs.getInt("lastScannedLines", 5),
        autoScan         = prefs.getBoolean("autoScan", false),
        debounceTime     = prefs.getInt("debounceTime", 500),
        hapticEnabled    = prefs.getBoolean("hapticEnabled", true),
        muteSound        = prefs.getBoolean("muteSound", false),
        lastLocationCode = prefs.getString("lastLocationCode", "") ?: "",
        lastRcCode       = prefs.getString("lastRcCode", "") ?: "",
        liveMode         = prefs.getBoolean("liveMode", false),
        disabledDocTypes = prefs.getString("disabledDocTypes", "")?.split(",")?.filter { it.isNotEmpty() }?.toSet() ?: emptySet(),
    )

    fun clear() = prefs.edit().clear().apply()

    fun save(settings: AppSettings) {
        prefs.edit()
            .putString ("textSize",          settings.textSize.name)
            .putBoolean("uppercaseText",      settings.uppercaseText)
            .putString ("language",           settings.language.name)
            .putInt    ("lastScannedLines",   settings.lastScannedLines)
            .putBoolean("autoScan",           settings.autoScan)
            .putInt    ("debounceTime",        settings.debounceTime)
            .putBoolean("hapticEnabled",      settings.hapticEnabled)
            .putBoolean("muteSound",          settings.muteSound)
            .putString ("lastLocationCode",   settings.lastLocationCode)
            .putString ("lastRcCode",         settings.lastRcCode)
            .putBoolean("liveMode",           settings.liveMode)
            .putString ("disabledDocTypes",   settings.disabledDocTypes.joinToString(","))
            .apply()
    }
}
