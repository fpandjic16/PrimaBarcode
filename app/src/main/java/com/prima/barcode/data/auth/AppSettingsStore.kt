package com.prima.barcode.data.auth

import android.content.Context
import com.prima.barcode.data.model.DocTypeFilterMode
import com.prima.barcode.ui.theme.Language
import com.prima.barcode.ui.theme.TextSize
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppSettingsStore @Inject constructor(@param:ApplicationContext private val context: Context) {

    private val prefs by lazy { context.getSharedPreferences("app_settings", Context.MODE_PRIVATE) }

    fun get(): AppSettings = AppSettings(
        textSize         = TextSize.entries.firstOrNull { it.name == prefs.getString("textSize", null) } ?: TextSize.NORMAL,
        uppercaseText    = prefs.getBoolean("uppercaseText", false),
        language         = Language.entries.firstOrNull { it.name == prefs.getString("language", null) } ?: Language.ENGLISH,
        lastScannedLines = prefs.getInt("lastScannedLines", 5),
        debounceTime     = prefs.getInt("debounceTime", 500),
        hapticEnabled    = prefs.getBoolean("hapticEnabled", true),
        soundEnabled     = prefs.getBoolean("soundEnabled", true),
        warnOnOver          = prefs.getBoolean("warnOnOver", true),
        backgroundSync      = prefs.getBoolean("backgroundSync", false),
        lastLocationCode = prefs.getString("lastLocationCode", "") ?: "",
        lastRcCode       = prefs.getString("lastRcCode", "") ?: "",
        disabledDocTypes = prefs.getString("disabledDocTypes", "")?.split(",")?.filter { it.isNotEmpty() }?.toSet() ?: emptySet(),
        docTypeFilters = prefs.getString("docTypeFilters", "")
            ?.split(",")?.filter { it.isNotEmpty() }
            ?.mapNotNull { entry ->
                val parts = entry.split(":")
                if (parts.size != 2) return@mapNotNull null
                val mode = DocTypeFilterMode.entries.firstOrNull { it.name == parts[1] } ?: return@mapNotNull null
                parts[0] to mode
            }?.toMap() ?: emptyMap(),
        debuggerActive = prefs.getBoolean("debuggerActive", false),
    )

    /**
     * The language the user explicitly picked, or null if they never touched the setting.
     * [get] can't answer this — it folds a missing key into [Language.ENGLISH] — and the
     * difference matters at startup: an explicit choice must be re-applied, while "never
     * chosen" has to be left alone so the device's own locale still decides.
     */
    fun savedLanguageOrNull(): Language? =
        prefs.getString("language", null)?.let { saved -> Language.entries.firstOrNull { it.name == saved } }

    fun clear() = prefs.edit().clear().apply()

    fun save(settings: AppSettings) {
        prefs.edit()
            .putString ("textSize",          settings.textSize.name)
            .putBoolean("uppercaseText",      settings.uppercaseText)
            .putString ("language",           settings.language.name)
            .putInt    ("lastScannedLines",   settings.lastScannedLines)
            .putInt    ("debounceTime",        settings.debounceTime)
            .putBoolean("hapticEnabled",      settings.hapticEnabled)
            .putBoolean("soundEnabled",       settings.soundEnabled)
            .putBoolean("warnOnOver",          settings.warnOnOver)
            .putBoolean("backgroundSync",      settings.backgroundSync)
            .putString ("lastLocationCode",   settings.lastLocationCode)
            .putString ("lastRcCode",         settings.lastRcCode)
            .putString ("disabledDocTypes",   settings.disabledDocTypes.joinToString(","))
            .putString ("docTypeFilters",     settings.docTypeFilters.entries.joinToString(",") { "${it.key}:${it.value.name}" })
            .putBoolean("debuggerActive",     settings.debuggerActive)
            .apply()
    }
}
