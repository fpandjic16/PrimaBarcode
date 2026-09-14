package com.prima.barcode.data.auth

import com.prima.barcode.data.model.DocTypeFilterMode
import com.prima.barcode.data.model.DocumentType

data class ExtSystemConfig(
    val serverBaseUrl: String = "",
    val credentialTtlHours: Int = 24,
    val documentLinesUrl: String = "",
    val documentTypeCodes: Map<DocumentType, String> = emptyMap(),
    val recordingSyncUrl: String = "",
    val locationsUrl: String = "",
    // Windows domain for NAV's NTLM auth. When set, it's combined with whatever the user
    // types on the login screen so they only ever need to enter a bare username. Left blank,
    // a domain embedded in the username itself (DOMAIN\user or user@domain) still works —
    // see ExtSystemODataClient.buildClient.
    val domain: String = "",
    // AES-256 key (base64, 32 bytes) that login QR codes are encrypted with — see
    // LoginQrPayload.kt. Carried in the config rather than the build so it can be rotated by
    // importing a new configuration instead of shipping a new APK. Blank disables QR sign-in.
    val loginQrKey: String = "",
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

/**
 * Everything a configuration file sets up on a device.
 *
 * Two halves, one owner. [extSystem] is where the ERP lives; the rest is what this
 * installation offers and how it is scoped — the device half of `AppSettings`, set once by
 * an administrator and inherited by every operator.
 *
 * The personal half is deliberately absent. Text size, language, haptics and the working
 * location belong to a person, and a deployment file that reset them on every load would
 * undo the reason those settings are per-operator at all.
 */
data class DeviceConfiguration(
    val extSystem: ExtSystemConfig,
    val disabledDocTypes: Set<String>,
    val docTypeFilters: Map<String, DocTypeFilterMode>,
    val debuggerActive: Boolean,
)
