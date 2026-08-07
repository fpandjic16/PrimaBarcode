package com.prima.barcode.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.prima.barcode.data.auth.AppSettings
import com.prima.barcode.data.auth.AppSettingsStore
import com.prima.barcode.data.auth.ExtSystemConfig
import com.prima.barcode.data.auth.ExtSystemConfigStore
import com.prima.barcode.data.auth.ExtSystemCredentialStore
import com.prima.barcode.data.auth.ExtSystemCredentials
import com.prima.barcode.data.db.LocationDao
import com.prima.barcode.data.db.LocationEntity
import com.prima.barcode.data.db.ResponsibilityCenterEntity
import com.prima.barcode.data.db.toDomain
import com.prima.barcode.data.export.DatabaseExporter
import com.prima.barcode.data.extsystem.ExtSystemODataClient
import com.prima.barcode.data.extsystem.ExtSystemResult
import com.prima.barcode.data.extsystem.toNavRecording
import com.prima.barcode.data.model.DocState
import com.prima.barcode.data.model.Document
import com.prima.barcode.data.model.DocumentType
import com.prima.barcode.data.model.DownloadFilter
import com.prima.barcode.data.model.Item
import com.prima.barcode.data.model.Line
import com.prima.barcode.data.model.Location
import com.prima.barcode.data.model.ResponsibilityCenter
import com.prima.barcode.data.repository.DocumentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.prima.barcode.data.extsystem.NavBarcodeAppEntry
import com.prima.barcode.data.extsystem.NavLocation
import com.prima.barcode.data.extsystem.NavODataList
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject
import timber.log.Timber

@HiltViewModel
class AppViewModel @Inject constructor(
    @param:dagger.hilt.android.qualifiers.ApplicationContext private val appContext: Context,
    private val repository: DocumentRepository,
    private val locationDao: LocationDao,
    private val exporter: DatabaseExporter,
    val extSystemConfigStore: ExtSystemConfigStore,
    val extSystemCredentialStore: ExtSystemCredentialStore,
    private val extSystemClient: ExtSystemODataClient,
    private val appSettingsStore: AppSettingsStore,
) : ViewModel() {

    private val gson = Gson()

    private val _credentials = MutableStateFlow(extSystemCredentialStore.get())
    val credentials: StateFlow<ExtSystemCredentials?> = _credentials

    val locations: StateFlow<List<Location>> = locationDao.observeLocations()
        .map { it.map { e -> e.toDomain() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val responsibilityCenters: StateFlow<List<ResponsibilityCenter>> = locationDao.observeRcs()
        .map { it.map { e -> e.toDomain() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())


    private val _isRefreshingLocations = MutableStateFlow(false)
    val isRefreshingLocations: StateFlow<Boolean> get() = _isRefreshingLocations

    private val _lastLocationSyncAt = MutableStateFlow<Instant?>(null)
    val lastLocationSyncAt: StateFlow<Instant?> get() = _lastLocationSyncAt

    fun downloadLocations(onComplete: (error: String?) -> Unit = {}) {
        viewModelScope.launch {
            _isRefreshingLocations.value = true
            val error = realDownloadLocations()
            if (error == null) _lastLocationSyncAt.value = Instant.now()
            _isRefreshingLocations.value = false
            onComplete(error)
        }
    }

    /** Returns null on success, or an error message on failure. */
    private suspend fun realDownloadLocations(): String? {
        val config = extSystemConfig.value
        val creds  = extSystemCredentialStore.get() ?: return "Not signed in"
        if (!config.isConfigured) return "External system not configured"
        if (config.locationsUrl.isBlank()) return "Locations URL not configured"
        extSystemClient.configure(config, creds)
        return when (val result = extSystemClient.downloadRaw(config.locationsUrl)) {
            is ExtSystemResult.Success -> {
                val typeToken = object : TypeToken<NavODataList<NavLocation>>() {}.type
                val odata = gson.fromJson<NavODataList<NavLocation>>(result.data, typeToken)
                val rcCodes = odata.value.map { it.rcCode }.filter { it.isNotBlank() }.distinct()
                locationDao.clearRcs()
                locationDao.upsertRcs(rcCodes.map { ResponsibilityCenterEntity(it, it, null) })
                locationDao.clearLocations()
                locationDao.upsertLocations(odata.value.map { LocationEntity(it.code, it.name, it.rcCode) })
                null
            }
            is ExtSystemResult.Failure -> result.message
        }
    }

    private fun buildODataFilterString(filter: DownloadFilter, typeCode: String): String? {
        val clauses = mutableListOf<String>()
        if (typeCode.isNotBlank()) clauses.add("Document_Type eq '$typeCode'")
        filter.dateFrom?.let { clauses.add("Document_Date ge $it") }
        filter.dateTo?.let { clauses.add("Document_Date le $it") }
        if (filter.destinationCode.isNotBlank()) clauses.add("Destination_No eq '${filter.destinationCode}'")
        if (filter.sourceCode.isNotBlank()) clauses.add("Source_No eq '${filter.sourceCode}'")
        if (filter.rcCode.isNotBlank()) clauses.add("Responsibility_Center eq '${filter.rcCode}'")
        return if (clauses.isEmpty()) null else clauses.joinToString(" and ")
    }

    private fun appendODataFilter(url: String, filterStr: String): String {
        val encoded = java.net.URLEncoder.encode(filterStr, "UTF-8").replace("+", "%20")
        val sep = if ('?' in url) '&' else '?'
        return "$url${sep}\$filter=$encoded"
    }

    fun buildDownloadUrls(filter: DownloadFilter, docType: DocumentType? = null): List<Pair<String, String>> {
        val config = extSystemConfig.value
        if (config.documentLinesUrl.isBlank()) return emptyList()
        val types = if (docType != null) listOf(docType) else DocumentType.entries
        return types.map { type ->
            val typeCode = config.docTypeCodeFor(type)
            val filterStr = buildODataFilterString(filter, typeCode)
            val finalUrl = if (filterStr != null) appendODataFilter(config.documentLinesUrl, filterStr) else config.documentLinesUrl
            type.display to finalUrl
        }
    }

    fun getLocationsUrl(): String = extSystemConfig.value.locationsUrl
    fun getRecordingSyncUrl(): String = extSystemConfig.value.recordingSyncUrl

    fun realDownloadDocuments(
        filter: DownloadFilter = DownloadFilter(),
        docType: DocumentType? = null,
        onComplete: (failureCount: Int, errors: List<String>) -> Unit = { _, _ -> },
    ) {
        viewModelScope.launch {
            val config = extSystemConfig.value
            val creds  = extSystemCredentialStore.get()
            if (!config.isConfigured || creds == null) {
                val msg = if (creds == null) "Not signed in" else "External system not configured"
                onComplete(1, listOf(msg))
                return@launch
            }
            if (config.documentLinesUrl.isBlank()) {
                onComplete(1, listOf("Document lines URL not configured"))
                return@launch
            }
            extSystemClient.configure(config, creds)
            val typesToDownload = if (docType != null) listOf(docType) else DocumentType.entries
            var failures = 0
            val errorMessages = mutableListOf<String>()
            for (type in typesToDownload) {
                val typeCode = config.docTypeCodeFor(type)
                val filterStr = buildODataFilterString(filter, typeCode)
                val finalUrl = if (filterStr != null) appendODataFilter(config.documentLinesUrl, filterStr) else config.documentLinesUrl
                val result = extSystemClient.downloadRaw(finalUrl)
                when (result) {
                    is ExtSystemResult.Success -> {
                        val now = Instant.now()
                        val typeToken = object : TypeToken<NavODataList<NavBarcodeAppEntry>>() {}.type
                        val odata = gson.fromJson<NavODataList<NavBarcodeAppEntry>>(result.data, typeToken)
                        val documents = odata.value.groupBy { it.documentNo }.map { (docNo, rows) ->
                            val first = rows.first()
                            val lines = rows.filter { it.lineNo > 0 }.map { row ->
                                Line(
                                    documentNo        = docNo,
                                    lineNo            = row.lineNo,
                                    item              = Item(row.itemNo, row.description),
                                    barcodeNo         = row.barcodeNo,
                                    expected          = row.qtyOutstanding,
                                    scanned           = 0.0,
                                    destinationCode   = row.destinationCode,
                                    sourceCode        = row.sourceCode,
                                    unitOfMeasureCode = row.unitOfMeasureCode,
                                    scanningQty       = row.scanningQty,
                                )
                            }
                            Document(
                                documentNo       = docNo,
                                type             = type,
                                destinationCode  = first.destinationCode,
                                sourceCode       = first.sourceCode,
                                rcCode           = first.rcCode,
                                isSourceRetail   = first.isRetailLocation,
                                creationDateTime = now,
                                documentDate     = runCatching {
                                    first.documentDate?.let { Instant.parse("${it}T00:00:00Z") } ?: now
                                }.getOrDefault(now),
                                lines            = lines,
                                state            = DocState.Downloaded,
                            )
                        }
                        repository.replaceDownloadedDocuments(type, documents)
                    }
                    is ExtSystemResult.Failure -> {
                        Timber.w("Failed to download ${type.display}: ${result.message}")
                        failures++
                        errorMessages.add(result.message)
                    }
                }
            }
            onComplete(failures, errorMessages)
        }
    }

    fun loadSettings(): AppSettings = appSettingsStore.get()

    fun saveSettings(settings: AppSettings) = appSettingsStore.save(settings)

    val documents: StateFlow<List<Document>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _extSystemConfig = MutableStateFlow(extSystemConfigStore.get())
    val extSystemConfig: StateFlow<ExtSystemConfig> = _extSystemConfig

    fun saveExtSystemConfig(config: ExtSystemConfig) {
        extSystemConfigStore.save(config)
        _extSystemConfig.value = config
    }

    fun saveCredentials(username: String, password: String) {
        extSystemCredentialStore.save(username, password, extSystemConfig.value.credentialTtlHours)
        _credentials.value = extSystemCredentialStore.get()
    }

    fun signOut() {
        extSystemCredentialStore.clear()
        _credentials.value = null
    }

    /**
     * Performs an authenticated NTLM GET against [serverBaseUrl] to verify that the
     * server is reachable and the supplied Windows credentials are accepted.
     * The username carries the domain (user@domain or DOMAIN\user). Credentials are
     * persisted regardless of outcome, so a failed test (e.g. a server-side error)
     * doesn't force the user to retype them for the next attempt.
     * The password is never logged.
     */
    fun testExtSystemConnection(
        serverBaseUrl: String,
        username: String,
        password: String,
        onResult: (ExtSystemResult<Unit>) -> Unit,
    ) {
        viewModelScope.launch {
            val url = serverBaseUrl.trim()
            if (url.isBlank()) {
                onResult(ExtSystemResult.Failure("Server URL is empty")); return@launch
            }
            saveCredentials(username.trim(), password)
            val config = extSystemConfig.value.copy(serverBaseUrl = url)
            val creds  = ExtSystemCredentials(username.trim(), password)
            extSystemClient.configure(config, creds)
            val result = extSystemClient.testConnection(url)
            onResult(result)
        }
    }

    /**
     * Parses an `ext_system_defaults.json`-formatted string into an [ExtSystemConfig].
     * Returns null if the JSON is missing required fields or malformed.
     * Does not persist — the caller fills the editable form fields.
     */
    fun parseExtSystemConfigJson(json: String): ExtSystemConfig? = runCatching {
        val dto = gson.fromJson(json, ExtSystemDefaultsDto::class.java)
        ExtSystemConfig(
            serverBaseUrl            = dto.serverBaseUrl.orEmpty(),
            credentialTtlHours       = dto.credentialTtlHours ?: 24,
            documentLinesUrl         = dto.documentLinesUrl.orEmpty(),
            documentTypeCodes        = DocumentType.entries.associateWith { type ->
                dto.documentTypeCodes?.get(type.name).orEmpty()
            },
            locationsUrl     = dto.locationsUrl.orEmpty(),
            recordingSyncUrl = dto.recordingSyncUrl.orEmpty(),
        )
    }.onFailure { Timber.w(it, "parseExtSystemConfigJson failed") }.getOrNull()

    /** Loads predefined parameters from the bundled asset `ext_system_defaults.json`. */
    fun loadExtSystemDefaults(): ExtSystemConfig? = runCatching {
        appContext.assets.open("ext_system_defaults.json")
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
    }.onFailure { Timber.w(it, "Failed to load ext_system_defaults.json") }
     .getOrNull()
     ?.let { parseExtSystemConfigJson(it) }

    /** Raw text of the bundled `ext_system_defaults.json` asset, for "download as file". */
    fun getExtSystemDefaultsJsonText(): String? = runCatching {
        appContext.assets.open("ext_system_defaults.json")
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
    }.onFailure { Timber.w(it, "Failed to read ext_system_defaults.json") }.getOrNull()

    private data class ExtSystemDefaultsDto(
        val serverBaseUrl: String? = null,
        val credentialTtlHours: Int? = null,
        val documentLinesUrl: String? = null,
        val documentTypeCodes: Map<String, String>? = null,
        val locationsUrl: String? = null,
        val recordingSyncUrl: String? = null,
    )

    /** Uploads each doc; on success deletes it, on failure marks UploadFailed. Returns failure count. */
    private suspend fun runUpload(docs: List<Document>): Int {
        val config = extSystemConfig.value
        val creds  = extSystemCredentialStore.get()
        if (!config.isConfigured || creds == null) {
            docs.forEach {
                repository.updateDocState(it.documentNo, it.type.key,
                    DocState.UploadFailed("External system not configured or not signed in"))
            }
            return docs.size
        }
        if (config.recordingSyncUrl.isBlank()) {
            docs.forEach {
                repository.updateDocState(it.documentNo, it.type.key,
                    DocState.UploadFailed("Recording sync URL not configured"))
            }
            return docs.size
        }
        val url = config.recordingSyncUrl
        extSystemClient.configure(config, creds)
        var failures = 0
        for (doc in docs) {
            val docTypeCode = config.docTypeCodeFor(doc.type)
            val rows = repository.getRecordings(doc.documentNo, doc.type.key)
            var failureMessage: String? = null
            for (row in rows) {
                val recordingGuid = java.util.UUID.randomUUID().toString()
                val result = extSystemClient.uploadRecording(url, row.toNavRecording(docTypeCode, recordingGuid))
                when (result) {
                    // Delete each row as it's confirmed uploaded, so a retry after a
                    // partial failure only resends what NAV hasn't received yet.
                    is ExtSystemResult.Success -> repository.deleteRecording(
                        doc.documentNo, doc.type.key, row.documentLine, row.recordingLineNo,
                    )
                    is ExtSystemResult.Failure -> {
                        failureMessage = result.message
                        break
                    }
                }
            }
            if (failureMessage != null) {
                repository.updateDocState(doc.documentNo, doc.type.key, DocState.UploadFailed(failureMessage))
                failures++
            } else {
                repository.deleteDocument(doc.documentNo, doc.type.key)
            }
        }
        return failures
    }

    /** Blocking upload path — the caller shows a progress dialog and reacts in [onComplete]. */
    fun uploadToExtSystem(
        docs: List<Document>,
        onComplete: (failureCount: Int) -> Unit = {},
    ) {
        viewModelScope.launch { onComplete(runUpload(docs)) }
    }

    /**
     * Background upload path. Marks each doc [DocState.PendingUpload] immediately so the UI can
     * return control, then uploads in the background. On success the doc is deleted; on failure
     * it becomes UploadFailed. The documents Flow reflects these transitions reactively.
     */
    fun uploadInBackground(docs: List<Document>) {
        viewModelScope.launch {
            docs.forEach { repository.updateDocState(it.documentNo, it.type.key, DocState.PendingUpload) }
            runUpload(docs)
        }
    }

    fun exportDatabase(uri: Uri, onComplete: () -> Unit) {
        viewModelScope.launch {
            exporter.exportTo(uri)
            onComplete()
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            repository.clearAll()
            appSettingsStore.clear()
            extSystemConfigStore.clear()
            extSystemCredentialStore.clear()
        }
    }

    fun deleteAllDocuments() {
        viewModelScope.launch {
            repository.clearAll()
        }
    }

    fun createDocument(doc: Document) {
        viewModelScope.launch { repository.upsertDocument(doc) }
    }

    fun clearDocumentRecordings(documentNo: String, type: DocumentType) {
        viewModelScope.launch {
            repository.deleteDocumentRecordings(documentNo, type.key)
        }
    }

    fun clearErrorDocs() {
        viewModelScope.launch {
            documents.value
                .filter { it.state is DocState.UploadFailed }
                .forEach { doc ->
                    val resetState = when {
                        doc.lines.all { it.scanned >= it.expected && it.expected > 0 } -> DocState.Completed
                        doc.lines.any { it.scanned > 0 } || doc.extraLines.isNotEmpty() -> DocState.InProgress
                        else -> DocState.Downloaded
                    }
                    repository.updateDocState(doc.documentNo, doc.type.key, resetState)
                }
        }
    }

}
