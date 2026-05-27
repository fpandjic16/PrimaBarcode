package com.prima.barcode.data.auth

import com.prima.barcode.data.model.DocumentType

data class ExtSystemConfig(
    val serverBaseUrl: String = "",
    val domain: String = "",
    val credentialTtlHours: Int = 24,
    val endpointUrls: Map<DocumentType, String> = emptyMap(),
    val recordingSyncUrl: String = "",
    val locationsUrl: String = "",
    val responsibilityCentersUrl: String = "",
) {
    fun endpointFor(type: DocumentType): String = endpointUrls[type] ?: ""
    val isConfigured: Boolean get() = serverBaseUrl.isNotBlank()
}

data class ExtSystemCredentials(
    val username: String,
    val password: String,
)
