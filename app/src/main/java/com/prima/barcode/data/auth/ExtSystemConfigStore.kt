package com.prima.barcode.data.auth

import android.content.Context
import com.prima.barcode.data.model.DocumentType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExtSystemConfigStore @Inject constructor(@ApplicationContext private val context: Context) {

    private val prefs by lazy { context.getSharedPreferences("ext_system_config", Context.MODE_PRIVATE) }

    fun get(): ExtSystemConfig = ExtSystemConfig(
        serverBaseUrl      = prefs.getString("serverBaseUrl", "") ?: "",
        domain             = prefs.getString("domain", "") ?: "",
        credentialTtlHours = prefs.getInt("credentialTtlHours", 24),
        endpointUrls       = DocumentType.entries.associateWith { type ->
            prefs.getString("endpoint_${type.key}", "") ?: ""
        },
        recordingSyncUrl            = prefs.getString("recordingSyncUrl", "") ?: "",
        locationsUrl               = prefs.getString("locationsUrl", "") ?: "",
        responsibilityCentersUrl   = prefs.getString("responsibilityCentersUrl", "") ?: "",
    )

    fun clear() = prefs.edit().clear().apply()

    fun save(config: ExtSystemConfig) {
        val ed = prefs.edit()
            .putString("serverBaseUrl",      config.serverBaseUrl)
            .putString("domain",             config.domain)
            .putInt   ("credentialTtlHours", config.credentialTtlHours)
            .putString("recordingSyncUrl",          config.recordingSyncUrl)
            .putString("locationsUrl",              config.locationsUrl)
            .putString("responsibilityCentersUrl",  config.responsibilityCentersUrl)
        config.endpointUrls.forEach { (type, url) ->
            ed.putString("endpoint_${type.key}", url)
        }
        ed.apply()
    }
}
