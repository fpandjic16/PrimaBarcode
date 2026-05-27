package com.prima.barcode.data.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Credential storage backed by EncryptedSharedPreferences / Android Keystore.
 *
 * Security properties:
 *  - AES-256-GCM encryption; keys live in the hardware-backed Keystore on API 28+
 *  - App-private storage: inaccessible to other apps without root
 *  - TTL enforced on every read; stale data wiped automatically
 *  - Credentials are never logged
 *  - Domain is stored separately in ExtSystemConfigStore (not sensitive)
 */
@Singleton
class ExtSystemCredentialStore @Inject constructor(@ApplicationContext private val context: Context) {

    private val masterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val prefs by lazy {
        EncryptedSharedPreferences.create(
            context,
            "ext_system_credentials",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun save(username: String, password: String, ttlHours: Int) {
        prefs.edit()
            .putString("username", username)
            .putString("password", password)
            .putLong("expiry", System.currentTimeMillis() + ttlHours * 3_600_000L)
            .apply()
    }

    fun get(): ExtSystemCredentials? {
        val expiry = prefs.getLong("expiry", 0L)
        if (System.currentTimeMillis() > expiry) { clear(); return null }
        val username = prefs.getString("username", null) ?: return null
        val password = prefs.getString("password", null) ?: return null
        return ExtSystemCredentials(username = username, password = password)
    }

    fun isValid(): Boolean = get() != null

    fun clear() { prefs.edit().clear().apply() }
}
