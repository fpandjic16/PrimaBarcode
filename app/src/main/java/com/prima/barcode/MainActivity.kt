package com.prima.barcode

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.prima.barcode.data.auth.AppSettings
import com.prima.barcode.data.barcode.DataWedgeManager
import com.prima.barcode.data.extsystem.ExtSystemResult
import com.prima.barcode.data.model.DocState
import com.prima.barcode.data.model.DocTypeFilterMode
import com.prima.barcode.data.model.DocumentFilter
import com.prima.barcode.data.model.DownloadFilter
import com.prima.barcode.data.model.DocumentType
import com.prima.barcode.data.model.LineStatus
import com.prima.barcode.data.model.scanStatus
import com.prima.barcode.data.model.User
import com.prima.barcode.ui.screen.DocTypeSummary
import com.prima.barcode.ui.screen.ExtSystemConfigScreen
import com.prima.barcode.ui.screen.DocumentOverviewScreen
import com.prima.barcode.ui.screen.DocumentListScreen
import com.prima.barcode.ui.screen.UploadErrorScreen
import com.prima.barcode.ui.screen.DownloadFilterScreen
import com.prima.barcode.ui.screen.DocumentFilterScreen
import com.prima.barcode.ui.screen.LocationRcPickScreen
import com.prima.barcode.ui.screen.LoginSheet
import com.prima.barcode.ui.screen.MainMenuScreen
import com.prima.barcode.ui.screen.RecordingScreen
import com.prima.barcode.ui.screen.SettingsScreen
import com.prima.barcode.ui.screen.UserInfoScreen
import com.prima.barcode.ui.theme.Language
import com.prima.barcode.ui.theme.PrimaBarcodeTheme
import com.prima.barcode.ui.theme.TextSize
import com.prima.barcode.ui.viewmodel.AppViewModel
import com.prima.barcode.ui.viewmodel.RecordingViewModel
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import dagger.hilt.android.AndroidEntryPoint
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hideNavBar()
        DataWedgeManager.configure(this)
        setContent {
            // Showing the IME re-shows the nav bar on many devices/OS versions even though we
            // asked for it hidden — re-hide it whenever the keyboard becomes visible so it
            // doesn't linger onscreen for the whole time the user is typing. Reading this via
            // Compose's own WindowInsets (rather than a View-level OnApplyWindowInsetsListener
            // on the decor view) avoids interfering with how insets propagate to Compose's own
            // status-bar padding.
            val imeVisible = WindowInsets.isImeVisible
            LaunchedEffect(imeVisible) {
                if (imeVisible) hideNavBar()
            }

            val appVm: AppViewModel = hiltViewModel()
            val initialSettings = remember { appVm.loadSettings() }
            var textSize         by remember { mutableStateOf(initialSettings.textSize) }
            var uppercaseText    by remember { mutableStateOf(initialSettings.uppercaseText) }
            var language         by remember { mutableStateOf(initialSettings.language) }
            var lastScannedLines by remember { mutableStateOf(initialSettings.lastScannedLines) }
            var autoScan         by remember { mutableStateOf(initialSettings.autoScan) }
            var debounceTime     by remember { mutableStateOf(initialSettings.debounceTime) }
            var hapticEnabled     by remember { mutableStateOf(initialSettings.hapticEnabled) }
            var warnOnOver       by remember { mutableStateOf(initialSettings.warnOnOver) }
            var backgroundSync   by remember { mutableStateOf(initialSettings.backgroundSync) }
            var disabledDocTypes by remember { mutableStateOf(initialSettings.disabledDocTypes) }
            var docTypeFilters    by remember { mutableStateOf(initialSettings.docTypeFilters) }
            var debuggerActive   by remember { mutableStateOf(initialSettings.debuggerActive) }
            var locationCode     by remember { mutableStateOf(initialSettings.lastLocationCode) }
            var rcCode           by remember { mutableStateOf(initialSettings.lastRcCode) }

            fun buildSettings() = AppSettings(
                textSize         = textSize,
                uppercaseText    = uppercaseText,
                language         = language,
                lastScannedLines = lastScannedLines,
                autoScan         = autoScan,
                debounceTime     = debounceTime,
                hapticEnabled    = hapticEnabled,
                warnOnOver          = warnOnOver,
                backgroundSync      = backgroundSync,
                lastLocationCode = locationCode,
                lastRcCode       = rcCode,
                disabledDocTypes = disabledDocTypes,
                docTypeFilters   = docTypeFilters,
                debuggerActive   = debuggerActive,
            )

            // Bulk-applies settings saved (and confirmed) from the Settings screen —
            // called once on exit-confirm, not per-field, since Settings now buffers
            // edits locally instead of auto-saving each change.
            fun applySettings(s: AppSettings) {
                if (language != s.language) {
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(s.language.tag))
                }
                textSize = s.textSize
                uppercaseText = s.uppercaseText
                language = s.language
                lastScannedLines = s.lastScannedLines
                autoScan = s.autoScan
                debounceTime = s.debounceTime
                hapticEnabled = s.hapticEnabled
                warnOnOver = s.warnOnOver
                backgroundSync = s.backgroundSync
                debuggerActive = s.debuggerActive
                appVm.saveSettings(s)
            }

            PrimaBarcodeTheme(textSizeOffset = textSize.spOffset, uppercaseEnabled = uppercaseText) {
                PrimaBarcodeApp(
                    locationCode              = locationCode,
                    rcCode                    = rcCode,
                    onLocationCodeChange      = { code -> locationCode = code; appVm.saveSettings(buildSettings().copy(lastLocationCode = code)) },
                    onRcCodeChange            = { code -> rcCode = code; appVm.saveSettings(buildSettings().copy(lastRcCode = code)) },
                    textSize                  = textSize,
                    uppercaseText             = uppercaseText,
                    language                  = language,
                    lastScannedLines          = lastScannedLines,
                    autoScan                  = autoScan,
                    debounceTime              = debounceTime,
                    hapticEnabled             = hapticEnabled,
                    warnOnOver                = warnOnOver,
                    backgroundSync            = backgroundSync,
                    disabledDocTypes          = disabledDocTypes,
                    onDisabledDocTypesChange  = { disabledDocTypes = it; appVm.saveSettings(buildSettings().copy(disabledDocTypes = it)) },
                    docTypeFilters            = docTypeFilters,
                    onDocTypeFiltersChange    = { docTypeFilters = it; appVm.saveSettings(buildSettings().copy(docTypeFilters = it)) },
                    debuggerActive            = debuggerActive,
                    onSettingsSaved           = { s -> applySettings(s) },
                )
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideNavBar()
    }

    private fun hideNavBar() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.navigationBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}

private val exportTimestampFmt = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
    .withZone(ZoneId.systemDefault())

@Composable
private fun PrimaBarcodeApp(
    locationCode: String,
    rcCode: String,
    onLocationCodeChange: (String) -> Unit,
    onRcCodeChange: (String) -> Unit,
    textSize: TextSize,
    uppercaseText: Boolean,
    language: Language,
    lastScannedLines: Int,
    autoScan: Boolean,
    debounceTime: Int,
    hapticEnabled: Boolean,
    warnOnOver: Boolean,
    backgroundSync: Boolean,
    disabledDocTypes: Set<String>,
    onDisabledDocTypesChange: (Set<String>) -> Unit,
    docTypeFilters: Map<String, DocTypeFilterMode>,
    onDocTypeFiltersChange: (Map<String, DocTypeFilterMode>) -> Unit,
    debuggerActive: Boolean,
    onSettingsSaved: (AppSettings) -> Unit,
) {
    val nav = rememberNavController()
    val appVm: AppViewModel = hiltViewModel()
    val context = LocalContext.current

    val credentials by appVm.credentials.collectAsState()
    val user: User? = credentials?.let { creds ->
        val plain = creds.username.substringAfterLast('\\').substringBefore('@')
        User(
            id          = creds.username,
            username    = creds.username,
            displayName = plain,
            initials    = plain.take(2).uppercase(),
        )
    }

    val locations by appVm.locations.collectAsState()
    val rcs by appVm.responsibilityCenters.collectAsState()

    val rc = if (rcCode.isBlank()) null else rcs.find { it.code == rcCode }
    val location = if (locationCode.isNotEmpty()) locations.find { it.code == locationCode } else null

    // Auto-select first RC only when a non-blank code no longer matches any available RC (stale code recovery)
    LaunchedEffect(rcs, rcCode) {
        val match = rcs.find { it.code == rcCode }
        if (rcCode.isNotBlank() && match == null && rcs.isNotEmpty()) onRcCodeChange(rcs.first().code)
    }
    LaunchedEffect(rc, locations, locationCode) {
        if (rc != null) {
            val locMatch = locations.find { it.code == locationCode && it.rc == rc.code }
            if (locMatch == null) {
                locations.find { it.rc == rc.code }?.let { onLocationCodeChange(it.code) }
            }
        }
    }
    LaunchedEffect(autoScan, debounceTime) {
        DataWedgeManager.setContinuousScan(context, autoScan, debounceTime)
    }

    val documents by appVm.documents.collectAsState()
    val extSystemConfig by appVm.extSystemConfig.collectAsState()

    val filteredDocs = documents.filter { doc ->
        doc.hasProgress ||
            when (docTypeFilters[doc.type.key] ?: DocTypeFilterMode.LOCATION) {
                DocTypeFilterMode.LOCATION -> location != null && doc.sourceCode == location.code
                DocTypeFilterMode.RESPONSIBILITY_CENTER -> rc == null || doc.rcCode == rc.code
            }
    }

    val locationsManaged = extSystemConfig.locationsUrl.isNotBlank()
    val docTypes = DocumentType.entries.map { type ->
        val filterMode = docTypeFilters[type.key] ?: DocTypeFilterMode.LOCATION
        val blocked = locationsManaged && when (filterMode) {
            DocTypeFilterMode.LOCATION -> locations.isEmpty()
            DocTypeFilterMode.RESPONSIBILITY_CENTER -> rcs.isEmpty()
        }
        DocTypeSummary(
            type = type,
            count = filteredDocs.count { it.type == type },
            statusMini = filteredDocs.filter { it.type == type }.mapNotNull { doc ->
                when {
                    doc.state is DocState.UploadFailed -> LineStatus.EMPTY
                    doc.state == DocState.Downloaded   -> null
                    else -> doc.scanStatus().takeIf { it != LineStatus.EMPTY }
                }
            },
            blocked = blocked,
        )
    }.filter { it.type.key !in disabledDocTypes }

    val shiftScans  = filteredDocs.sumOf { d -> d.lines.count { it.scanned > 0 } }
    val errorDocs   = filteredDocs.filter { it.state is DocState.UploadFailed }
    val readyDocs   = filteredDocs.filter { it.state !is DocState.UploadFailed && it.scanStatus() == LineStatus.EXACT }
    val partialDocs = filteredDocs.filter { it.state !is DocState.UploadFailed && it.scanStatus() == LineStatus.PARTIAL }
    val overDocs    = filteredDocs.filter { it.state !is DocState.UploadFailed && it.scanStatus() == LineStatus.OVER }
    var selectedDocType by remember { mutableStateOf(DocumentType.WAREHOUSE_SHIPMENT) }
    var docFilter by remember { mutableStateOf(DocumentFilter()) }
    var overviewFilter by remember { mutableStateOf(DocumentFilter()) }
    var overviewLockedSource by remember { mutableStateOf<String?>(null) }
    var overviewLockedRc     by remember { mutableStateOf<String?>(null) }
    var showSyncErrorDialog     by remember { mutableStateOf(false) }
    var showDownloadErrorDialog by remember { mutableStateOf(false) }
    var downloadErrorMessage    by remember { mutableStateOf("") }
    var processingMessage by remember { mutableStateOf<String?>(null) }

    var debugUrls     by remember { mutableStateOf<List<String>>(emptyList()) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingCancel by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showDebugDialog by remember { mutableStateOf(false) }

    fun launchWithDebug(urls: List<String>, onCancel: () -> Unit = {}, action: () -> Unit) {
        if (debuggerActive && urls.isNotEmpty()) {
            debugUrls = urls; pendingAction = action; pendingCancel = onCancel; showDebugDialog = true
        } else {
            action()
        }
    }

    var showUploadLoginSheet by remember { mutableStateOf(false) }
    var pendingUploadAction  by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showMainLoginSheet   by remember { mutableStateOf(false) }

    fun requireCredentials(action: () -> Unit) {
        if (appVm.extSystemCredentialStore.isValid()) {
            action()
        } else {
            pendingUploadAction = action
            showUploadLoginSheet = true
        }
    }

    // Shared by every LoginSheet that gates access behind a real sign-in (as opposed to
    // ExtSystemConfigScreen's own LoginSheet usage, which *is* the explicit "Test connection"
    // action and already handles testing itself) — verifies the NAV server actually accepts
    // the credentials before the sheet treats sign-in as successful.
    fun testSignIn(username: String, password: String, onResult: (success: Boolean, error: String?) -> Unit) {
        appVm.testExtSystemConnection(extSystemConfig.serverBaseUrl, username, password) { result ->
            when (result) {
                is ExtSystemResult.Success -> onResult(true, null)
                is ExtSystemResult.Failure -> onResult(false, if (result.code > 0) "HTTP ${result.code}: ${result.message}" else result.message)
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            appVm.exportDatabase(it) {
                Toast.makeText(context, context.getString(R.string.export_saved), Toast.LENGTH_SHORT).show()
            }
        }
    }

    NavHost(navController = nav, startDestination = "main") {
        composable("main") {
            MainMenuScreen(
                user = user,
                location = location,
                rc = rc,
                docTypes = docTypes,
                shiftScans = shiftScans,
                shiftErrors = errorDocs.size,
                shiftReady = readyDocs.size,
                shiftPartial = partialDocs.size,
                shiftOver = overDocs.size,
                onChangeLocationRc = { nav.navigate("location_rc_pick") },
                onOpenSettings = { nav.navigate("settings") },
                onTypeTap = { type ->
                    selectedDocType = type
                    nav.navigate("docs")
                },
                onDocumentOverview = { nav.navigate("dashboard?tab=1") },
                onShowErrors = { nav.navigate("dashboard") },
                onUserInfoTap = {
                    if (appVm.extSystemCredentialStore.isValid()) nav.navigate("user_info")
                    else showMainLoginSheet = true
                },
            )
        }
        composable("user_info") {
            UserInfoScreen(
                user = user,
                location = location,
                rc = rc,
                onBack = { nav.popBackStack() },
                onSignOut = { appVm.signOut(); nav.popBackStack() },
            )
        }
        composable("location_rc_pick") {
            val isRefreshing by appVm.isRefreshingLocations.collectAsState()
            val lastSyncedAt by appVm.lastLocationSyncAt.collectAsState()
            LocationRcPickScreen(
                currentRcCode = rcCode,
                currentLocationCode = locationCode,
                availableRcs = rcs,
                availableLocations = locations,
                isRefreshing = isRefreshing,
                lastSyncedAt = lastSyncedAt,
                hasCredentials = appVm.extSystemCredentialStore.isValid(),
                credentialTtlHours = extSystemConfig.credentialTtlHours,
                onSelect = { rc, loc ->
                    onRcCodeChange(rc)
                    onLocationCodeChange(loc)
                    nav.popBackStack()
                },
                onRefresh = {
                    launchWithDebug(
                        listOf(appVm.getLocationsUrl()).filter { it.isNotBlank() }
                    ) {
                        appVm.downloadLocations { error ->
                            if (error != null) {
                                downloadErrorMessage = error
                                showDownloadErrorDialog = true
                            }
                        }
                    }
                },
                onSaveCredentials = { u, p -> appVm.saveCredentials(u, p) },
                onTestConnection = ::testSignIn,
                onBack = { nav.popBackStack() },
            )
        }
        composable("ext_system_config") {
            ExtSystemConfigScreen(
                initial = extSystemConfig,
                onSave  = { config ->
                    appVm.saveExtSystemConfig(config)
                    nav.popBackStack()
                },
                onDiscard = { nav.popBackStack() },
                loadDefaults = { fileName -> appVm.loadExtSystemDefaults(fileName) },
                listCompanies = { appVm.listExtSystemDefaultsCompanies() },
                getDefaultsJsonText = { fileName -> appVm.getExtSystemDefaultsJsonText(fileName) },
                disabledDocTypes = disabledDocTypes,
                onDisabledDocTypesChange = onDisabledDocTypesChange,
                docTypeFilters = docTypeFilters,
                onDocTypeFiltersChange = onDocTypeFiltersChange,
                savedCredentials = appVm.extSystemCredentialStore.get(),
                onTestConnection = { serverUrl, username, password, cb ->
                    launchWithDebug(
                        listOf(serverUrl.trim()),
                        onCancel = { cb(false, null) },
                    ) {
                        appVm.testExtSystemConnection(serverUrl, username, password) { result ->
                            when (result) {
                                is ExtSystemResult.Success -> cb(true, "NTLM authentication succeeded and the server responded.")
                                is ExtSystemResult.Failure -> cb(
                                    false,
                                    if (result.code > 0) "HTTP ${result.code}: ${result.message}" else result.message,
                                )
                            }
                        }
                    }
                },
                onImportJson = { json -> appVm.parseExtSystemConfigJson(json) },
            )
        }
        composable("settings") {
            val initialSettingsSnapshot = AppSettings(
                textSize = textSize,
                uppercaseText = uppercaseText,
                language = language,
                lastScannedLines = lastScannedLines,
                autoScan = autoScan,
                debounceTime = debounceTime,
                hapticEnabled = hapticEnabled,
                warnOnOver = warnOnOver,
                backgroundSync = backgroundSync,
                lastLocationCode = locationCode,
                lastRcCode = rcCode,
                disabledDocTypes = disabledDocTypes,
                docTypeFilters = docTypeFilters,
                debuggerActive = debuggerActive,
            )
            SettingsScreen(
                user = user,
                location = location,
                rc = rc,
                initial = initialSettingsSnapshot,
                onSave = { s -> onSettingsSaved(s); nav.popBackStack() },
                onDiscard = { nav.popBackStack() },
                onSaveExtSystemConfig = { config -> appVm.saveExtSystemConfig(config) },
                loadExtSystemConfigDefaults = { fileName -> appVm.loadExtSystemDefaults(fileName) },
                listExtSystemDefaultsCompanies = { appVm.listExtSystemDefaultsCompanies() },
                parseExtSystemConfigJson = { json -> appVm.parseExtSystemConfigJson(json) },
                getExtSystemDefaultsJsonText = { fileName -> appVm.getExtSystemDefaultsJsonText(fileName) },
                onExport = {
                    val ts = exportTimestampFmt.format(Instant.now())
                    exportLauncher.launch("prima_export_${ts}.json")
                },
                onClearCache = { appVm.clearCache() },
                onDeleteAllDocuments = { appVm.deleteAllDocuments() },
                onChangeLocation = { nav.navigate("location_rc_pick") },
                onOpenExtSystemConfig = { nav.navigate("ext_system_config") },
                onSignOut = { appVm.signOut() },
                onSignInTap = { requireCredentials {} },
            )
        }
        composable("docs") {
            val typeDocs = documents.filter { doc ->
                doc.type == selectedDocType &&
                    (
                        doc.hasProgress ||
                        when (docTypeFilters[selectedDocType.key] ?: DocTypeFilterMode.LOCATION) {
                            DocTypeFilterMode.LOCATION -> location != null && doc.sourceCode == location.code
                            DocTypeFilterMode.RESPONSIBILITY_CENTER -> rc == null || doc.rcCode == rc.code
                        }
                    )
            }
            DocumentListScreen(
                docType = selectedDocType,
                locationCode = location?.code ?: "",
                docTypeCode = extSystemConfig.docTypeCodeFor(selectedDocType),
                documents = typeDocs,
                onBack = { nav.popBackStack() },
                onDocTap = { selected -> nav.navigate("recording/${selected.documentNo}/${selected.type.key}") },
                onDownload = { nav.navigate("download_filter") },
                onUpload = { docs ->
                    requireCredentials {
                        if (backgroundSync) {
                            launchWithDebug(listOf(appVm.getRecordingSyncUrl())) {
                                appVm.uploadInBackground(docs)
                                nav.popBackStack("main", false)
                            }
                        } else {
                            val cb: (Int) -> Unit = { failures ->
                                processingMessage = null
                                nav.popBackStack("main", false)
                                if (failures > 0) showSyncErrorDialog = true
                            }
                            launchWithDebug(listOf(appVm.getRecordingSyncUrl())) {
                                processingMessage = "Uploading..."
                                appVm.uploadToExtSystem(docs, cb)
                            }
                        }
                    }
                },
                onErrorTap = { doc -> nav.navigate("upload_error/${doc.documentNo}") },
                onDeleteRecordings = { doc -> appVm.clearDocumentRecordings(doc.documentNo, doc.type) },
                onClearErrors = { appVm.clearErrorDocs() },
                filter = docFilter,
                onOpenFilter = { nav.navigate("filter") },
            )
        }
        composable(
            route = "dashboard?tab={tab}",
            arguments = listOf(navArgument("tab") { type = NavType.IntType; defaultValue = 0 }),
        ) { backStackEntry ->
            val initialTab = backStackEntry.arguments?.getInt("tab") ?: 0
            DocumentOverviewScreen(
                locationCode = location?.code ?: "",
                rcCode = rcCode,
                documents = documents,
                onBack = { nav.popBackStack() },
                onDocTap = { selected -> nav.navigate("recording/${selected.documentNo}/${selected.type.key}") },
                onClearErrors = { appVm.clearErrorDocs() },
                onUpload = { docs ->
                    requireCredentials {
                        if (backgroundSync) {
                            launchWithDebug(listOf(appVm.getRecordingSyncUrl())) {
                                appVm.uploadInBackground(docs)
                            }
                        } else {
                            val cb: (Int) -> Unit = { failures ->
                                processingMessage = null
                                if (failures > 0) showSyncErrorDialog = true
                            }
                            launchWithDebug(listOf(appVm.getRecordingSyncUrl())) {
                                processingMessage = "Uploading..."
                                appVm.uploadToExtSystem(docs, cb)
                            }
                        }
                    }
                },
                onErrorTap = { doc -> nav.navigate("upload_error/${doc.documentNo}") },
                filter = overviewFilter,
                onOpenFilter = { src, rc ->
                    overviewLockedSource = src
                    overviewLockedRc = rc
                    nav.navigate("overview_filter")
                },
                docTypeFilters = docTypeFilters,
                initialTab = initialTab,
            )
        }
        composable("filter") {
            DocumentFilterScreen(
                initialFilter = docFilter,
                showDocTypeFilter = false,
                locations = locations,
                rcs = rcs,
                onApply = { newFilter ->
                    docFilter = newFilter
                    nav.popBackStack()
                },
                onBack = { nav.popBackStack() },
            )
        }
        composable("overview_filter") {
            DocumentFilterScreen(
                initialFilter = overviewFilter,
                lockedSourceCode = overviewLockedSource,
                lockedRcCode = overviewLockedRc,
                locations = locations,
                rcs = rcs,
                onApply = { newFilter ->
                    overviewFilter = newFilter
                    nav.popBackStack()
                },
                onBack = { nav.popBackStack() },
            )
        }
        composable("download_filter") {
            val dlFilterMode = docTypeFilters[selectedDocType.key] ?: DocTypeFilterMode.LOCATION
            DownloadFilterScreen(
                hasCredentials  = appVm.extSystemCredentialStore.isValid(),
                docType         = selectedDocType,
                fixedSourceCode = if (dlFilterMode == DocTypeFilterMode.LOCATION) locationCode else null,
                fixedRcCode     = if (dlFilterMode == DocTypeFilterMode.RESPONSIBILITY_CENTER) rcCode else null,
                locations       = locations,
                rcs             = rcs,
                onTestConnection = ::testSignIn,
                onConfirm = { filter, username, password ->
                    if (username != null && password != null) appVm.saveCredentials(username, password)
                    val urls = appVm.buildDownloadUrls(filter, selectedDocType).map { (type, url) -> "$type: $url" }
                    launchWithDebug(urls) {
                        processingMessage = "Downloading..."
                        appVm.realDownloadDocuments(filter, docType = selectedDocType) { failures, errors ->
                            processingMessage = null
                            nav.popBackStack()
                            if (failures > 0) {
                                downloadErrorMessage = errors.firstOrNull() ?: ""
                                showDownloadErrorDialog = true
                            }
                        }
                    }
                },
                onCancel = { nav.popBackStack() },
            )
        }
        composable(
            route = "recording/{documentNo}/{type}",
            arguments = listOf(
                navArgument("documentNo") { type = NavType.StringType },
                navArgument("type") { type = NavType.StringType },
            ),
        ) {
            val vm: RecordingViewModel = hiltViewModel()
            val doc by vm.document.collectAsState()
            doc?.let { currentDoc ->
                RecordingScreen(
                    doc = currentDoc,
                    docTypeCode = extSystemConfig.docTypeCodeFor(currentDoc.type),
                    onBack = { nav.popBackStack() },
                    onScan = { barcode, multiplier ->
                        currentDoc.lines.find { it.barcodeNo == barcode }?.let { line ->
                            vm.recordScan(line.lineNo, barcode, user?.id.orEmpty(), multiplier)
                        }
                    },
                    onLineUpdate = { lineNo, newScanned -> vm.setLineScanned(lineNo, newScanned, user?.id.orEmpty()) },
                    onUpload = {
                        requireCredentials {
                            if (backgroundSync) {
                                launchWithDebug(listOf(appVm.getRecordingSyncUrl())) {
                                    appVm.uploadInBackground(listOf(currentDoc))
                                    nav.popBackStack("main", false)
                                }
                            } else {
                                val cb: (Int) -> Unit = { failures ->
                                    processingMessage = null
                                    nav.popBackStack("main", false)
                                    if (failures > 0) showSyncErrorDialog = true
                                }
                                launchWithDebug(listOf(appVm.getRecordingSyncUrl())) {
                                    processingMessage = "Uploading..."
                                    appVm.uploadToExtSystem(listOf(currentDoc), cb)
                                }
                            }
                        }
                    },
                    lastScannedLines = lastScannedLines,
                    autoScan = autoScan,
                    hapticEnabled = hapticEnabled,
                    debounceTime = debounceTime,
                    warnOnOver = warnOnOver,
                )
            }
        }
        composable(
            route = "upload_error/{documentNo}",
            arguments = listOf(navArgument("documentNo") { type = NavType.StringType }),
        ) { backStackEntry ->
            val docNo = backStackEntry.arguments?.getString("documentNo") ?: ""
            val doc = documents.find { it.documentNo == docNo }
            doc?.let { currentDoc ->
                UploadErrorScreen(
                    document = currentDoc,
                    onBack = { nav.popBackStack() },
                    onRetryUpload = {
                        requireCredentials {
                            if (backgroundSync) {
                                launchWithDebug(listOf(appVm.getRecordingSyncUrl())) {
                                    appVm.uploadInBackground(listOf(currentDoc))
                                    nav.popBackStack()
                                }
                            } else {
                                val cb: (Int) -> Unit = { failures ->
                                    processingMessage = null
                                    if (failures > 0) { showSyncErrorDialog = true } else { nav.popBackStack() }
                                }
                                launchWithDebug(listOf(appVm.getRecordingSyncUrl())) {
                                    processingMessage = "Uploading..."
                                    appVm.uploadToExtSystem(listOf(currentDoc), cb)
                                }
                            }
                        }
                    },
                )
            }
        }
    }

    processingMessage?.let { message ->
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        ) {
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    CircularProgressIndicator()
                    Text(message, fontWeight = FontWeight.Medium)
                }
            }
        }
    }

    if (showDownloadErrorDialog) {
        AlertDialog(
            onDismissRequest = { showDownloadErrorDialog = false },
            title = { Text(stringResource(R.string.download_error_title), fontWeight = FontWeight.Bold) },
            text  = { Text(downloadErrorMessage) },
            confirmButton = {
                Button(
                    onClick = { showDownloadErrorDialog = false },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text(stringResource(R.string.btn_ok), fontWeight = FontWeight.Bold) }
            },
        )
    }

    if (showDebugDialog) {
        AlertDialog(
            onDismissRequest = { showDebugDialog = false; pendingCancel?.invoke() },
            title = { Text(stringResource(R.string.debug_request_urls_title), fontWeight = FontWeight.Bold) },
            text = { Text(debugUrls.joinToString("\n\n")) },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = { showDebugDialog = false; pendingAction?.invoke() },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) { Text(stringResource(R.string.btn_proceed), fontWeight = FontWeight.Bold) }
                    OutlinedButton(
                        onClick = { showDebugDialog = false; pendingCancel?.invoke() },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) { Text(stringResource(R.string.btn_cancel)) }
                }
            },
        )
    }

    if (showUploadLoginSheet) {
        LoginSheet(
            credentialTtlHours = extSystemConfig.credentialTtlHours,
            ctaLabel           = stringResource(R.string.btn_sign_in),
            initialUsername    = appVm.extSystemCredentialStore.get()?.username ?: "",
            initialPassword    = appVm.extSystemCredentialStore.get()?.password ?: "",
            onDismiss          = { showUploadLoginSheet = false; pendingUploadAction = null },
            onTestConnection   = ::testSignIn,
            onSubmit           = { _, _ ->
                showUploadLoginSheet = false
                pendingUploadAction?.invoke()
                pendingUploadAction = null
            },
        )
    }

    if (showMainLoginSheet) {
        LoginSheet(
            credentialTtlHours = extSystemConfig.credentialTtlHours,
            ctaLabel           = stringResource(R.string.btn_sign_in),
            onDismiss          = { showMainLoginSheet = false },
            onTestConnection   = ::testSignIn,
            onSubmit           = { _, _ -> showMainLoginSheet = false },
        )
    }

    if (showSyncErrorDialog) {
        AlertDialog(
            onDismissRequest = { showSyncErrorDialog = false },
            title = { Text(stringResource(R.string.sync_errors_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.sync_errors_text)) },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        onClick = { showSyncErrorDialog = false; nav.navigate("dashboard") },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFCE3A3A)),
                    ) { Text(stringResource(R.string.btn_see_errors), fontWeight = FontWeight.Bold) }
                    OutlinedButton(
                        onClick = { showSyncErrorDialog = false },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) { Text(stringResource(R.string.btn_dismiss)) }
                }
            },
        )
    }
}
