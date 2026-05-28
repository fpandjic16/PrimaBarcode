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
import com.prima.barcode.data.db.LocationDao
import com.prima.barcode.data.db.LocationEntity
import com.prima.barcode.data.db.ResponsibilityCenterEntity
import com.prima.barcode.data.db.toDomain
import com.prima.barcode.data.export.DatabaseExporter
import com.prima.barcode.data.extsystem.ExtSystemODataClient
import com.prima.barcode.data.extsystem.ExtSystemResult
import com.prima.barcode.data.extsystem.toUploadPayload
import com.prima.barcode.data.model.DocState
import com.prima.barcode.data.model.Document
import com.prima.barcode.data.model.DocumentType
import com.prima.barcode.data.model.Item
import com.prima.barcode.data.model.Line
import com.prima.barcode.data.model.Location
import com.prima.barcode.data.model.ResponsibilityCenter
import com.prima.barcode.data.repository.DocumentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.prima.barcode.data.extsystem.NavDocumentLine
import com.prima.barcode.data.extsystem.NavLocation
import com.prima.barcode.data.extsystem.NavODataList
import com.prima.barcode.data.extsystem.NavResponsibilityCenter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject
import timber.log.Timber

@HiltViewModel
class AppViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: Context,
    private val repository: DocumentRepository,
    private val locationDao: LocationDao,
    private val exporter: DatabaseExporter,
    val extSystemConfigStore: ExtSystemConfigStore,
    val extSystemCredentialStore: ExtSystemCredentialStore,
    private val extSystemClient: ExtSystemODataClient,
    private val appSettingsStore: AppSettingsStore,
) : ViewModel() {

    private val gson = Gson()

    init {
        viewModelScope.launch {
            if (locationDao.observeLocations().first().isEmpty()) {
                seedSampleLocations()
            }
        }
    }

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

    fun downloadLocations(liveMode: Boolean = false, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            _isRefreshingLocations.value = true
            if (liveMode) {
                realDownloadLocations()
            } else {
                delay(1500)
                locationDao.clearLocations()
                locationDao.clearRcs()
                seedSampleLocations()
            }
            _lastLocationSyncAt.value = Instant.now()
            _isRefreshingLocations.value = false
            onComplete()
        }
    }

    private suspend fun realDownloadLocations() {
        val config = extSystemConfig
        val creds  = extSystemCredentialStore.get() ?: return
        if (!config.isConfigured) return
        extSystemClient.configure(config, creds)
        if (config.locationsUrl.isNotBlank()) {
            val result = extSystemClient.downloadRaw(config.locationsUrl)
            if (result is ExtSystemResult.Success) {
                val typeToken = object : TypeToken<NavODataList<NavLocation>>() {}.type
                val odata = gson.fromJson<NavODataList<NavLocation>>(result.data, typeToken)
                locationDao.clearLocations()
                locationDao.upsertLocations(odata.value.map { LocationEntity(it.code, it.name, it.rcCode) })
            }
        }
        if (config.responsibilityCentersUrl.isNotBlank()) {
            val result = extSystemClient.downloadRaw(config.responsibilityCentersUrl)
            if (result is ExtSystemResult.Success) {
                val typeToken = object : TypeToken<NavODataList<NavResponsibilityCenter>>() {}.type
                val odata = gson.fromJson<NavODataList<NavResponsibilityCenter>>(result.data, typeToken)
                locationDao.clearRcs()
                locationDao.upsertRcs(odata.value.map { ResponsibilityCenterEntity(it.code, it.name, it.short) })
            }
        }
    }

    fun realDownloadDocuments(onComplete: (failureCount: Int) -> Unit = {}) {
        viewModelScope.launch {
            val config = extSystemConfig
            val creds  = extSystemCredentialStore.get()
            if (!config.isConfigured || creds == null) { onComplete(DocumentType.entries.size); return@launch }
            extSystemClient.configure(config, creds)
            repository.clearAll()
            var failures = 0
            for (type in DocumentType.entries) {
                val url = config.endpointFor(type)
                if (url.isBlank()) continue
                val result = extSystemClient.downloadRaw(url)
                when (result) {
                    is ExtSystemResult.Success -> {
                        val typeToken = object : TypeToken<NavODataList<NavDocumentLine>>() {}.type
                        val odata = gson.fromJson<NavODataList<NavDocumentLine>>(result.data, typeToken)
                        val now = Instant.now()
                        odata.value.groupBy { it.documentNo }.forEach { (docNo, rows) ->
                            val first = rows.first()
                            val lines = rows.map { row ->
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
                                )
                            }
                            val doc = Document(
                                documentNo       = docNo,
                                type             = type,
                                destinationCode  = first.destinationCode,
                                sourceCode       = first.sourceCode,
                                rcCode           = first.rcCode,
                                ownerUserId      = first.ownerUserId,
                                creationDateTime = now,
                                documentDate     = runCatching {
                                    first.documentDate?.let { Instant.parse(it + "T00:00:00Z") } ?: now
                                }.getOrDefault(now),
                                lines            = lines,
                                state            = DocState.Downloaded,
                            )
                            repository.upsertDocument(doc)
                        }
                    }
                    is ExtSystemResult.Failure -> {
                        Timber.w("Failed to download ${type.display}: ${result.message}")
                        failures++
                    }
                }
            }
            onComplete(failures)
        }
    }

    private suspend fun seedSampleLocations() {
        val csvLines = appContext.assets.open("Data_RC_Location.csv")
            .bufferedReader(Charsets.UTF_8)
            .readLines()
            .drop(1)
        val locationEntities = mutableListOf<LocationEntity>()
        val rcCodes = mutableSetOf<String>()
        for (line in csvLines) {
            val parts = line.split(";")
            val code = parts.getOrNull(0)?.trim() ?: continue
            val rc   = parts.getOrNull(1)?.trim() ?: ""
            if (code.isBlank()) continue
            val effectiveRc = rc.ifBlank { code }
            locationEntities.add(LocationEntity(code, code, effectiveRc))
            if (rc.isNotBlank()) rcCodes.add(rc)
        }
        val rcEntities = rcCodes.map { rc ->
            val short = rc.replace(" ", "").take(3).uppercase()
            ResponsibilityCenterEntity(rc, rc, short)
        }
        locationDao.clearLocations()
        locationDao.clearRcs()
        locationDao.upsertLocations(locationEntities)
        locationDao.upsertRcs(rcEntities)
    }

    fun loadSettings(): AppSettings = appSettingsStore.get()

    fun saveSettings(settings: AppSettings) = appSettingsStore.save(settings)

    val documents: StateFlow<List<Document>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _extSystemConfig = MutableStateFlow(extSystemConfigStore.get())
    val extSystemConfig: ExtSystemConfig get() = _extSystemConfig.value

    fun saveExtSystemConfig(config: ExtSystemConfig) {
        extSystemConfigStore.save(config)
        _extSystemConfig.value = config
    }

    fun saveCredentials(domain: String, username: String, password: String) {
        extSystemCredentialStore.save(username, password, extSystemConfig.credentialTtlHours)
        if (domain != extSystemConfig.domain) saveExtSystemConfig(extSystemConfig.copy(domain = domain))
    }

    fun saveCredentialTtl(hours: Int) {
        saveExtSystemConfig(extSystemConfig.copy(credentialTtlHours = hours))
    }

    fun uploadToExtSystem(
        docs: List<Document>,
        onComplete: (failureCount: Int) -> Unit = {},
    ) {
        viewModelScope.launch {
            val config = extSystemConfig
            val creds  = extSystemCredentialStore.get()
            if (!config.isConfigured || creds == null) { onComplete(docs.size); return@launch }
            val url = config.recordingSyncUrl.ifBlank {
                "${config.serverBaseUrl.trimEnd('/')}/OData/WMS_RecordingSync"
            }
            extSystemClient.configure(config, creds)
            var failures = 0
            for (doc in docs) {
                val singlePayload = listOf(doc).toUploadPayload()
                val result = extSystemClient.upload(url, singlePayload)
                when (result) {
                    is ExtSystemResult.Success -> repository.deleteDocument(doc.documentNo, doc.type.key)
                    is ExtSystemResult.Failure -> {
                        repository.updateDocState(doc.documentNo, doc.type.key,
                            DocState.UploadFailed(result.message))
                        failures++
                    }
                }
            }
            onComplete(failures)
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

    fun createDocument(doc: Document) {
        viewModelScope.launch { repository.upsertDocument(doc) }
    }

    fun testDownload(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.clearAll()
            seedSampleData()
            onComplete()
        }
    }

    fun testImport(onComplete: (failureCount: Int) -> Unit = {}) {
        viewModelScope.launch { onComplete(runTestImport()) }
    }

    fun testImportDocs(docs: List<Document>, onComplete: (failureCount: Int) -> Unit = {}) {
        viewModelScope.launch {
            val uploadable = docs.filter { it.state != DocState.Downloaded }
            var failures = 0
            uploadable.forEachIndexed { index, doc ->
                delay(300)
                if ((index + 1) % 3 == 0) {
                    repository.updateDocState(
                        doc.documentNo, doc.type.key,
                        DocState.UploadFailed("Test failure: document ${index + 1} of ${uploadable.size}"),
                    )
                    failures++
                } else {
                    repository.deleteDocument(doc.documentNo, doc.type.key)
                }
            }
            onComplete(failures)
        }
    }

    private suspend fun runTestImport(): Int {
        val docs = repository.getUploadableDocs()
        var failures = 0
        docs.forEachIndexed { index, doc ->
            delay(300)
            if ((index + 1) % 3 == 0) {
                repository.updateDocState(
                    doc.documentNo, doc.type.key,
                    DocState.UploadFailed("Test failure: document ${index + 1} of ${docs.size}"),
                )
                failures++
            } else {
                repository.deleteDocument(doc.documentNo, doc.type.key)
            }
        }
        return failures
    }

    fun insertTestData() {
        viewModelScope.launch { seedSampleData() }
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

    private suspend fun seedSampleData() {
        val userId = "1"
        val rc     = "BJELOVAR"
        val now    = Instant.now()

        fun line(docNo: String, no: Int, itemNo: String, name: String,
                 barcode: String, expected: Double, src: String, dst: String): Line {
            val uom = if (expected == kotlin.math.floor(expected)) "KOM" else "M"
            return Line(docNo, no, Item(itemNo, name), barcode, expected, 0.0, dst, src, uom)
        }

        suspend fun scan(docNo: String, type: DocumentType, lineNo: Int,
                         barcode: String, qty: Double) =
            repository.recordScan(docNo, type.key, lineNo, barcode, userId, qty, null)

        // ── S-OTP-26-17612  SHIPMENT -> MP1091  InProgress ──────────────
        val d1 = "S-OTP-26-17612"
        repository.upsertDocument(Document(d1, DocumentType.WAREHOUSE_SHIPMENT,
            "MP1091", "CS175", rc, userId, creationDateTime = now, documentDate = now, lines = listOf(
                line(d1, 1, "NTR102078", "ISG IOLANI kut L Giulia 9713 EX.",               "NTR102078",    1.0,       "CS175", "MP1091"),
                line(d1, 2, "NTR102079", "ISG IOLANI kut D Giulia 9713 EX.",               "NTR102079", 1124.0102,   "CS175", "MP1091"),
                line(d1, 3, "NTR99757",  "ISG WINTON kut Olio 135 OT FSC MIX Credit EX.", "NTR99757",     5.52,      "CS175", "MP1091"),
                line(d1, 4, "NTR102080", "ISG IKANO U kut L Degan 2513 bukva natur EX.",  "NTR102080",    2.0,       "CS175", "MP1091"),
                line(d1, 5, "NTR102081", "ISG IKANO U kut D Degan 2501 bukva natur EX.",  "NTR102081",    1.10152,   "CS175", "MP1091"),
            ), state = DocState.Downloaded))
        scan(d1, DocumentType.WAREHOUSE_SHIPMENT, 1, "NTR102078",    1.0)
        scan(d1, DocumentType.WAREHOUSE_SHIPMENT, 3, "NTR99757",     3.0)
        repository.addExtraLine(d1, DocumentType.WAREHOUSE_SHIPMENT.key, "NTR999001", userId, 1.0)

        // ── S-OTP-26-17685  SHIPMENT -> MP1092  Downloaded ──────────────
        val d2 = "S-OTP-26-17685"
        repository.upsertDocument(Document(d2, DocumentType.WAREHOUSE_SHIPMENT,
            "MP1092", "CS175", rc, userId, creationDateTime = now, documentDate = now, lines = listOf(
                line(d2, 1,  "NGP202233", "PC LIBERTA regal komoda 3VR+OTV. TIP 1 KORPUS hrast classic pak.1/3 ZA.", "NGP202233", 1.452,   "CS175", "MP1092"),
                line(d2, 2,  "NGP202417", "PC LIBERTA regal TV komoda viseca PLAFON 240 hrast classic ZA.",          "NGP202417", 1.0,     "CS175", "MP1092"),
                line(d2, 3,  "NGP202234", "PC LIBERTA regal komoda 3VR+OTV. TIP 2 KORPUS hrast classic pak.2/3 ZA.", "NGP202234", 1.10152,"CS175", "MP1092"),
                line(d2, 4,  "NGP202235", "PC LIBERTA regal komoda 3VR+OTV. TIP 3 KORPUS hrast classic pak.3/3 ZA.", "NGP202235", 1.0,    "CS175", "MP1092"),
                line(d2, 5,  "NGP202287", "PC LIBERTA regal komoda 3VR+OTV. PLAFON hrast classic pak.1/2 ZA.",       "NGP202287", 1.0,    "CS175", "MP1092"),
                line(d2, 6,  "NGP202288", "PC LIBERTA regal komoda 3VR+OTV. NOGA hrast classic pak.2/2 ZA.",         "NGP202288", 1.0,    "CS175", "MP1092"),
                line(d2, 7,  "NGP202283", "PC LIBERTA regal komoda 3VR+OTV. VRATA akril satin mat ZA.",              "NGP202283", 1.0,    "CS175", "MP1092"),
                line(d2, 8,  "NGP202369", "PC LIBERTA regal TV komoda viseca 2L/80 KORPUS hrast classic pak.1/2 ZA.","NGP202369", 3.0,    "CS175", "MP1092"),
                line(d2, 9,  "NGP202371", "PC LIBERTA regal TV komoda viseca 2L/80 OKOV hrast classic pak.2/2 ZA.",  "NGP202371", 3.0,    "CS175", "MP1092"),
                line(d2, 10, "NGP202393", "PC LIBERTA regal TV komoda viseca 2L/80 MASKA akril satin mat ZA.",       "NGP202393", 3.0,    "CS175", "MP1092"),
            ), state = DocState.Downloaded))

        // ── S-OTP-26-17705  SHIPMENT -> MP1093  Completed ───────────────
        val d3 = "S-OTP-26-17705"
        repository.upsertDocument(Document(d3, DocumentType.WAREHOUSE_SHIPMENT,
            "MP1093", "CS175", rc, userId, creationDateTime = now, documentDate = now, lines = listOf(
                line(d3, 1, "NGP239862", "IPC stol VITA 160x90 PLOCA hrast ontario/crno pak.1/2", "NGP239862", 1.452, "CS175", "MP1093"),
                line(d3, 2, "NGP239863", "IPC stol VITA 160x90 POSTOLJE hrast ontario/crno pak.2/2", "NGP239863", 1.0, "CS175", "MP1093"),
            ), state = DocState.Downloaded))
        scan(d3, DocumentType.WAREHOUSE_SHIPMENT, 1, "NGP239862", 1.452)
        scan(d3, DocumentType.WAREHOUSE_SHIPMENT, 2, "NGP239863", 1.0)
        repository.updateDocState(d3, DocumentType.WAREHOUSE_SHIPMENT.key, DocState.Completed)

        // ── S-OTP-26-17860  SHIPMENT -> MP1094  InProgress ──────────────
        val d4 = "S-OTP-26-17860"
        repository.upsertDocument(Document(d4, DocumentType.WAREHOUSE_SHIPMENT,
            "MP1094", "CS175", rc, userId, creationDateTime = now, documentDate = now, lines = listOf(
                line(d4, 1, "NTR87569", "PSS sjediste fotelja TENOR Magnum 207 tamno zelena pak.1/2", "NTR87569",  2.0, "CS175", "MP1094"),
                line(d4, 2, "NTR87576", "PSS sjediste stolica TENOR Magnum 207 tamno zelena pak.1/2", "NTR87576", 12.0, "CS175", "MP1094"),
                line(d4, 3, "NTR87577", "PSS sjediste stolica TENOR Magnum 28 konjak smeda pak.1/2",  "NTR87577",  6.0, "CS175", "MP1094"),
            ), state = DocState.Downloaded))
        scan(d4, DocumentType.WAREHOUSE_SHIPMENT, 1, "NTR87569",  2.0)
        scan(d4, DocumentType.WAREHOUSE_SHIPMENT, 2, "NTR87576",  6.0)

        // ── S-OTP-26-17902  SHIPMENT -> MP1095  Downloaded ──────────────
        val d5 = "S-OTP-26-17902"
        repository.upsertDocument(Document(d5, DocumentType.WAREHOUSE_SHIPMENT,
            "MP1095", "CS175", rc, userId, creationDateTime = now, documentDate = now, lines = listOf(
                line(d5, 1, "NGP253724", "PCKK OPTIMA NEXT / 24 tam.tek/cr.pla-cr.pla SKOKO INES",  "NGP253724", 1.0, "CS175", "MP1095"),
                line(d5, 2, "NGP254894", "PCKK SARA / 24 bij/sah.bez-sah.bez BANOVIC VILIM",         "NGP254894", 1.0, "CS175", "MP1095"),
                line(d5, 3, "NGP254913", "PCKK AURORA / 24 bij/champ-champ RESETAR MIRNA",           "NGP254913", 1.0, "CS175", "MP1095"),
            ), state = DocState.Downloaded))

        // ── S-OTP-26-17904  SHIPMENT -> MP1096  UploadFailed ────────────
        val d6 = "S-OTP-26-17904"
        repository.upsertDocument(Document(d6, DocumentType.WAREHOUSE_SHIPMENT,
            "MP1096", "CS175", rc, userId, creationDateTime = now, documentDate = now, lines = listOf(
                line(d6, 1, "NGP253760", "PCKK LIBERTA LUX / 24 bij/akr.bijS-akr.bijS LUKANEC LEVACIC ELIZABETA", "NGP253760", 1.0, "CS175", "MP1096"),
                line(d6, 2, "NGP253788", "PCKK CVITA / 24 bij/led.si-bij COOK NEILL",                             "NGP253788", 1.0, "CS175", "MP1096"),
                line(d6, 3, "NGP253869", "PCKK NEA / 24 bij/si.gra-si.pla SIROLA TEREZA",                         "NGP253869", 1.0, "CS175", "MP1096"),
                line(d6, 4, "NGP254923", "PCKK LORENA / 24 bij/mag-mag MICIC MAJA",                               "NGP254923", 1.0, "CS175", "MP1096"),
            ), state = DocState.Downloaded))
        scan(d6, DocumentType.WAREHOUSE_SHIPMENT, 1, "NGP253760", 1.0)
        scan(d6, DocumentType.WAREHOUSE_SHIPMENT, 2, "NGP253788", 1.0)
        scan(d6, DocumentType.WAREHOUSE_SHIPMENT, 3, "NGP253869", 1.0)
        scan(d6, DocumentType.WAREHOUSE_SHIPMENT, 4, "NGP254923", 1.0)
        repository.updateDocState(d6, DocumentType.WAREHOUSE_SHIPMENT.key, DocState.UploadFailed("OData service returned HTTP 503 Service Unavailable after 3 retry attempts. The external system application server failed to respond within the 30-second timeout window. This may indicate the service tier is overloaded, the application pool has recycled, or the warehouse posting batch job is currently locked by another session. Please retry the upload or contact your system administrator if the issue persists."))

        // ── S-PR-26-47279  RECEIPT CS175 <- PS101  InProgress ───────────
        val d7 = "S-PR-26-47279"
        repository.upsertDocument(Document(d7, DocumentType.WAREHOUSE_RECEIPT,
            "CS175", "PS101", rc, userId, creationDateTime = now, documentDate = now, lines = listOf(
                line(d7, 1, "NTR82270", "PCM RITA 2 fran. krevet 200x160 TAPET Helena 103 cappuccino",       "NTR82270", 20.0, "PS101", "CS175"),
                line(d7, 2, "NTR82414", "PCM RITA 2 fran. krevet 200x180 UZGLAVLJE Helena 103 cappuccino",   "NTR82414", 10.0, "PS101", "CS175"),
                line(d7, 3, "NTR82417", "PCM RITA 2 fran. krevet 200x90 TAPET DESNI Helena 103 cappuccino",  "NTR82417", 10.0, "PS101", "CS175"),
                line(d7, 4, "NTR82420", "PCM RITA 2 fran. krevet 200x90 TAPET LIJEVI Helena 103 cappuccino", "NTR82420", 10.0, "PS101", "CS175"),
                line(d7, 5, "NTR82477", "PCM RITA 2 fran. krevet 200x90 KORPUS DESNI Helena 103 cappuccino", "NTR82477", 10.0, "PS101", "CS175"),
                line(d7, 6, "NTR82482", "PCM RITA 2 fran. krevet 200x90 KORPUS LIJEVI Helena 103 cappuccino","NTR82482", 10.0, "PS101", "CS175"),
                line(d7, 7, "NTR82532", "PCM RITA 2 fran. krevet 180 OKOV+NOGE",                             "NTR82532", 10.0, "PS101", "CS175"),
                line(d7, 8, "NTR82271", "PCM RITA 2 fran. krevet 200x160 UZGLAVLJE Helena 103 cappuccino",   "NTR82271", 20.0, "PS101", "CS175"),
            ), state = DocState.Downloaded))
        scan(d7, DocumentType.WAREHOUSE_RECEIPT, 1, "NTR82270", 12.0)
        scan(d7, DocumentType.WAREHOUSE_RECEIPT, 2, "NTR82414",  5.0)
        scan(d7, DocumentType.WAREHOUSE_RECEIPT, 3, "NTR82417", 10.0)

        // ── S-PR-26-47363  RECEIPT CS175 <- PS101  Downloaded ───────────
        val d8 = "S-PR-26-47363"
        repository.upsertDocument(Document(d8, DocumentType.WAREHOUSE_RECEIPT,
            "CS175", "PS101", rc, userId, creationDateTime = now, documentDate = now, lines = listOf(
                line(d8, 1, "NGP250012", "PC DAVOS predsoblje ogledalo hrast cremona cannolo ZA.", "NGP250012", 16.0, "PS101", "CS175"),
            ), state = DocState.Downloaded))

        // ── S-PR-26-47477  RECEIPT CS175 <- PS121  Completed ────────────
        val d9 = "S-PR-26-47477"
        repository.upsertDocument(Document(d9, DocumentType.WAREHOUSE_RECEIPT,
            "CS175", "PS121", rc, userId, creationDateTime = now, documentDate = now, lines = listOf(
                line(d9, 1, "NTR68925", "PSS noge BRIGITA metal flah crno mat pak.2/2", "NTR68925", 20.0, "PS121", "CS175"),
                line(d9, 2, "NTR75682", "PSS postolje ALBA crno mat fiksno pak.2/2",    "NTR75682", 20.0, "PS121", "CS175"),
                line(d9, 3, "NTR86165", "PSS fotelja SENA postolje crno mat metal DZ.", "NTR86165", 30.0, "PS121", "CS175"),
            ), state = DocState.Downloaded))
        scan(d9, DocumentType.WAREHOUSE_RECEIPT, 1, "NTR68925", 20.0)
        scan(d9, DocumentType.WAREHOUSE_RECEIPT, 2, "NTR75682", 20.0)
        scan(d9, DocumentType.WAREHOUSE_RECEIPT, 3, "NTR86165", 30.0)
        repository.updateDocState(d9, DocumentType.WAREHOUSE_RECEIPT.key, DocState.Completed)
    }
}
