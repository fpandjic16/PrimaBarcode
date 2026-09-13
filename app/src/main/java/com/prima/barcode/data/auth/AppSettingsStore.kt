package com.prima.barcode.data.auth

import android.content.Context
import android.content.SharedPreferences
import com.prima.barcode.data.model.DocTypeFilterMode
import com.prima.barcode.ui.theme.Language
import com.prima.barcode.ui.theme.TextSize
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Settings, split by who they actually belong to.
 *
 * **Personal, one set per profile:** text size, uppercase, language, debounce, haptics, sound,
 * over-scan warning, background sync, and the last location / responsibility centre. These
 * describe a person — how they read the screen, where they are working today — and it would be
 * rude for each shift to reset the next one's.
 *
 * **The device's, shared by everyone:** which document types are switched off, how each type is
 * scoped, and the debugger flag. These describe the installation, the same way `ExtSystemConfig`
 * does; an administrator sets them up once and a new operator should not have to repeat that
 * before they can work.
 *
 * [AppSettings] stays one type across that seam, so nothing above this class knows the split
 * exists.
 */
@Singleton
class AppSettingsStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val profileStore: UserProfileStore,
) {

    private val devicePrefs by lazy { context.getSharedPreferences("app_settings", Context.MODE_PRIVATE) }

    /**
     * Falls back to the device file when nobody is signed in, so a read before sign-in returns
     * defaults instead of throwing. Nothing writes there in that state — every settings screen
     * sits behind sign-in.
     */
    private fun personalPrefs(): SharedPreferences {
        val id = profileStore.currentId() ?: return devicePrefs
        return context.getSharedPreferences("app_settings_$id", Context.MODE_PRIVATE)
    }

    fun get(): AppSettings {
        val personal = personalPrefs()
        return AppSettings(
            textSize         = TextSize.entries.firstOrNull { it.name == personal.getString("textSize", null) } ?: TextSize.NORMAL,
            uppercaseText    = personal.getBoolean("uppercaseText", false),
            language         = Language.entries.firstOrNull { it.name == personal.getString("language", null) } ?: Language.ENGLISH,
            debounceTime     = personal.getInt("debounceTime", 500),
            hapticEnabled    = personal.getBoolean("hapticEnabled", true),
            soundEnabled     = personal.getBoolean("soundEnabled", true),
            warnOnOver          = personal.getBoolean("warnOnOver", true),
            backgroundSync      = personal.getBoolean("backgroundSync", false),
            lastLocationCode = personal.getString("lastLocationCode", "") ?: "",
            lastRcCode       = personal.getString("lastRcCode", "") ?: "",
            disabledDocTypes = devicePrefs.getString("disabledDocTypes", "")?.split(",")?.filter { it.isNotEmpty() }?.toSet() ?: emptySet(),
            docTypeFilters = devicePrefs.getString("docTypeFilters", "")
                ?.split(",")?.filter { it.isNotEmpty() }
                ?.mapNotNull { entry ->
                    val parts = entry.split(":")
                    if (parts.size != 2) return@mapNotNull null
                    val mode = DocTypeFilterMode.entries.firstOrNull { it.name == parts[1] } ?: return@mapNotNull null
                    parts[0] to mode
                }?.toMap() ?: emptyMap(),
            debuggerActive = devicePrefs.getBoolean("debuggerActive", false),
        )
    }

    /**
     * The language the user explicitly picked, or null if they never touched the setting.
     * [get] can't answer this — it folds a missing key into [Language.ENGLISH] — and the
     * difference matters at startup: an explicit choice must be re-applied, while "never
     * chosen" has to be left alone so the device's own locale still decides.
     */
    fun savedLanguageOrNull(): Language? =
        personalPrefs().getString("language", null)?.let { saved -> Language.entries.firstOrNull { it.name == saved } }

    fun save(settings: AppSettings) {
        personalPrefs().edit()
            .putString ("textSize",          settings.textSize.name)
            .putBoolean("uppercaseText",      settings.uppercaseText)
            .putString ("language",           settings.language.name)
            .putInt    ("debounceTime",        settings.debounceTime)
            .putBoolean("hapticEnabled",      settings.hapticEnabled)
            .putBoolean("soundEnabled",       settings.soundEnabled)
            .putBoolean("warnOnOver",          settings.warnOnOver)
            .putBoolean("backgroundSync",      settings.backgroundSync)
            .putString ("lastLocationCode",   settings.lastLocationCode)
            .putString ("lastRcCode",         settings.lastRcCode)
            .apply()
        devicePrefs.edit()
            .putString ("disabledDocTypes",   settings.disabledDocTypes.joinToString(","))
            .putString ("docTypeFilters",     settings.docTypeFilters.entries.joinToString(",") { "${it.key}:${it.value.name}" })
            .putBoolean("debuggerActive",     settings.debuggerActive)
            .apply()
    }
}
