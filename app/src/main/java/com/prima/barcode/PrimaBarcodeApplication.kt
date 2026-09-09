package com.prima.barcode

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.prima.barcode.data.auth.AppSettingsStore
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class PrimaBarcodeApplication : Application() {

    @Inject lateinit var appSettingsStore: AppSettingsStore

    override fun onCreate() {
        super.onCreate()   // Hilt injects appSettingsStore here, before it's read below.
        Timber.plant(Timber.DebugTree())
        applySavedLanguage()
    }

    /**
     * Re-applies the language stored in [AppSettingsStore] to AppCompat.
     *
     * The chosen language is persisted in the app's own settings, but the locale that actually
     * renders the UI lives in AppCompatDelegate, which keeps its own storage — and below API 33
     * it persists nothing at all unless the app declares `AppLocalesMetadataHolderService`.
     * Without this the two drift apart on every process restart, and most visibly after an APK
     * upgrade: the Settings screen still reads "Croatian" while the whole UI renders English.
     * Doing it here, before any Activity exists, means the first frame is already correct rather
     * than flashing English and recreating.
     *
     * This only fills a gap — it never overrides a locale already in effect, so a per-app
     * language set from Android's own system settings (API 33+) keeps winning. A user who has
     * never touched the setting is left alone too, so the device locale still decides on a
     * fresh install.
     */
    private fun applySavedLanguage() {
        if (!AppCompatDelegate.getApplicationLocales().isEmpty) return
        val language = appSettingsStore.savedLanguageOrNull() ?: return
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.tag))
    }
}
