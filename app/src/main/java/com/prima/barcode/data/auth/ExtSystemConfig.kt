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
