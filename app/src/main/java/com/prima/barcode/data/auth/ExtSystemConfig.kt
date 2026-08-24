package com.prima.barcode.data.auth

import com.prima.barcode.data.model.DocumentType

data class ExtSystemConfig(
    val serverBaseUrl: String = "",
    val credentialTtlHours: Int = 24,
    val documentLinesUrl: String = "",
    val documentTypeCodes: Map<DocumentType, String> = emptyMap(),
    val recordingSyncUrl: String = "",
    val locationsUrl: String = "",
) {
    fun docTypeCodeFor(type: DocumentType): String = documentTypeCodes[type] ?: ""
    val isConfigured: Boolean get() = serverBaseUrl.isNotBlank()
}

data class ExtSystemCredentials(
    val username: String,
    val password: String,
)

/**
 * One bundled per-company NAV connection defaults file, selectable from "Load built-in
 * defaults". Discovered at runtime from `ext_system_defaults_*.json` assets — see
 * [com.prima.barcode.ui.viewmodel.AppViewModel.listExtSystemDefaultsCompanies] — rather than
 * a fixed list, so adding a new company is just adding a new asset file.
 */
data class ExtSystemDefaultsCompany(val label: String, val assetFileName: String)
