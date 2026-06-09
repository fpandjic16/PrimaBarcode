package com.prima.barcode.data.auth

import android.content.Context
import com.prima.barcode.data.model.DocumentType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExtSystemConfigStore @Inject constructor(@param:ApplicationContext private val context: Context) {

    private val prefs by lazy { context.getSharedPreferences("ext_system_config", Context.MODE_PRIVATE) }

    fun get(): ExtSystemConfig = ExtSystemConfig(
        serverBaseUrl      = prefs.getString("serverBaseUrl", "") ?: "",
        credentialTtlHours = prefs.getInt("credentialTtlHours", 24),
        endpointUrls       = DocumentType.entries.associateWith { type ->
            prefs.getString("endpoint_${type.key}", "") ?: ""
        },
        documentTypeCodes = DocumentType.entries.associateWith { type ->
            prefs.getString("doc_type_code_${type.key}", "") ?: ""
        },
        recordingSyncUrl            = prefs.getString("recordingSyncUrl", "") ?: "",
        locationsUrl               = prefs.getString("locationsUrl", "") ?: "",
        responsibilityCentersUrl   = prefs.getString("responsibilityCentersUrl", "") ?: "",
    )

    fun clear() = prefs.edit().clear().apply()

    fun save(config: ExtSystemConfig) {
        val ed = prefs.edit()
            .putString("serverBaseUrl",      config.serverBaseUrl)
            .putInt   ("credentialTtlHours", config.credentialTtlHours)
            .putString("recordingSyncUrl",          config.recordingSyncUrl)
            .putString("locationsUrl",              config.locationsUrl)
            .putString("responsibilityCentersUrl",  config.responsibilityCentersUrl)
        config.endpointUrls.forEach { (type, url) ->
            ed.putString("endpoint_${type.key}", url)
        }
        config.documentTypeCodes.forEach { (type, code) ->
            ed.putString("doc_type_code_${type.key}", code)
        }
        ed.apply()
    }
}
