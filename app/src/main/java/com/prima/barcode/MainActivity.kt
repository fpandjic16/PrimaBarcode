package com.prima.barcode

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import com.prima.barcode.data.model.DocState
import com.prima.barcode.data.model.Document
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
import com.prima.barcode.ui.screen.MainMenuScreen
import com.prima.barcode.ui.screen.RecordingScreen
import com.prima.barcode.ui.screen.SettingsScreen
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        DataWedgeManager.configure(this)
        setContent {
            val appVm: AppViewModel = hiltViewModel()
            val initialSettings = remember { appVm.loadSettings() }
            var textSize         by remember { mutableStateOf(initialSettings.textSize) }
            var uppercaseText    by remember { mutableStateOf(initialSettings.uppercaseText) }
            var language         by remember { mutableStateOf(initialSettings.language) }
            var lastScannedLines by remember { mutableStateOf(initialSettings.lastScannedLines) }
            var autoScan         by remember { mutableStateOf(initialSettings.autoScan) }
            var debounceTime     by remember { mutableStateOf(initialSettings.debounceTime) }
            var hapticEnabled     by remember { mutableStateOf(initialSettings.hapticEnabled) }
            var muteSound        by remember { mutableStateOf(initialSettings.muteSound) }
            var liveMode         by remember { mutableStateOf(initialSettings.liveMode) }
            var disabledDocTypes by remember { mutableStateOf(initialSettings.disabledDocTypes) }
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
                muteSound        = muteSound,
                lastLocationCode = locationCode,
                lastRcCode       = rcCode,
                liveMode         = liveMode,
                disabledDocTypes = disabledDocTypes,
            )

            PrimaBarcodeTheme(textSizeOffset = textSize.spOffset, uppercaseEnabled = uppercaseText) {
                PrimaBarcodeApp(
                    locationCode              = locationCode,
                    rcCode                    = rcCode,
                    onLocationCodeChange      = { code -> locationCode = code; appVm.saveSettings(buildSettings().copy(lastLocationCode = code)) },
                    onRcCodeChange            = { code -> rcCode = code; appVm.saveSettings(buildSettings().copy(lastRcCode = code)) },
                    textSize                  = textSize,
                    onTextSizeChange          = { textSize = it; appVm.saveSettings(buildSettings().copy(textSize = it)) },
                    uppercaseText             = uppercaseText,
                    onUppercaseTextChange     = { uppercaseText = it; appVm.saveSettings(buildSettings().copy(uppercaseText = it)) },
                    language                  = language,
                    onLanguageChange          = { lang ->
                        language = lang
                        appVm.saveSettings(buildSettings().copy(language = lang))
                        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(lang.tag))
                    },
                    lastScannedLines          = lastScannedLines,
                    onLastScannedLinesChange  = { lastScannedLines = it; appVm.saveSettings(buildSettings().copy(lastScannedLines = it)) },
                    autoScan                  = autoScan,
                    onAutoScanChange          = { autoScan = it; appVm.saveSettings(buildSettings().copy(autoScan = it)) },
                    debounceTime              = debounceTime,
                    onDebounceTimeChange      = { debounceTime = it; appVm.saveSettings(buildSettings().copy(debounceTime = it)) },
                    hapticEnabled             = hapticEnabled,
                    onHapticEnabledChange     = { hapticEnabled = it; appVm.saveSettings(buildSettings().copy(hapticEnabled = it)) },
                    muteSound                 = muteSound,
                    onMuteSoundChange         = { muteSound = it; appVm.saveSettings(buildSettings().copy(muteSound = it)) },
                    liveMode                  = liveMode,
                    onLiveModeChange          = { liveMode = it; appVm.saveSettings(buildSettings().copy(liveMode = it)) },
                    disabledDocTypes          = disabledDocTypes,
                    onDisabledDocTypesChange  = { disabledDocTypes = it; appVm.saveSettings(buildSettings().copy(disabledDocTypes = it)) },
                )
            }
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
    onTextSizeChange: (TextSize) -> Unit,
    uppercaseText: Boolean,
    onUppercaseTextChange: (Boolean) -> Unit,
    language: Language,
    onLanguageChange: (Language) -> Unit,
    lastScannedLines: Int,
    onLastScannedLinesChange: (Int) -> Unit,
    autoScan: Boolean,
    onAutoScanChange: (Boolean) -> Unit,
    debounceTime: Int,
    onDebounceTimeChange: (Int) -> Unit,
    hapticEnabled: Boolean,
    onHapticEnabledChange: (Boolean) -> Unit,
    muteSound: Boolean,
    onMuteSoundChange: (Boolean) -> Unit,
    liveMode: Boolean,
    onLiveModeChange: (Boolean) -> Unit,
    disabledDocTypes: Set<String>,
    onDisabledDocTypesChange: (Set<String>) -> Unit,
) {
    val nav = rememberNavController()
    val appVm: AppViewModel = hiltViewModel()
    val context = LocalContext.current

    val user = User(id = "1", username = "fpandic", displayName = "Filip Pandic", initials = "FP")

    val locations by appVm.locations.collectAsState()
    val rcs by appVm.responsibilityCenters.collectAsState()

    val rc = rcs.find { it.code == rcCode } ?: rcs.firstOrNull()
    val location = if (locationCode.isNotEmpty()) locations.find { it.code == locationCode } else null

    // Auto-select first RC if code is empty or no longer matches any seeded RC
    LaunchedEffect(rcs, rcCode) {
        val match = rcs.find { it.code == rcCode }
        if (match == null && rcs.isNotEmpty()) onRcCodeChange(rcs.first().code)
    }
    LaunchedEffect(rc, locations, locationCode) {
        if (rc != null) {
            val locMatch = locations.find { it.code == locationCode && it.rc == rc.code }
            if (locMatch == null) {
                locations.find { it.rc == rc.code }?.let { onLocationCodeChange(it.code) }
            }
        }
    }
    LaunchedEffect(muteSound) {
        DataWedgeManager.setAudioFeedback(context, muteSound)
    }

    if (rc == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val documents by appVm.documents.collectAsState()

    val filteredDocs = documents.filter { doc ->
        if (doc.type.filterByRc) doc.rcCode == rc.code
        else location != null && doc.sourceCode == location.code
    }

    val docTypes = DocumentType.entries.map { type ->
        val short = when (type) {
            DocumentType.WAREHOUSE_SHIPMENT -> "Warehouse to Store"
            DocumentType.WAREHOUSE_RECEIPT  -> "Supplier to Warehouse"
            DocumentType.RETAIL_SHIPMENT    -> "Store to Customer"
            DocumentType.RETAIL_RECEIPT     -> "Store to Warehouse"
            DocumentType.TRANSPORT_SHEET    -> "Transfer sheet"
        }
        DocTypeSummary(
            type = type,
            short = short,
            count = filteredDocs.count { it.type == type },
            statusMini = filteredDocs.filter { it.type == type }.mapNotNull { doc ->
                when {
                    doc.state is DocState.UploadFailed -> LineStatus.EMPTY
                    doc.state == DocState.Downloaded   -> null
                    else -> doc.scanStatus().takeIf { it != LineStatus.EMPTY }
                }
            },
        )
    }.filter { it.type.key !in disabledDocTypes }

    val shiftScans  = filteredDocs.sumOf { d -> d.lines.sumOf { it.scanned } + d.extraLines.sumOf { it.quantity } }.toInt()
    val errorDocs   = filteredDocs.filter { it.state is DocState.UploadFailed }
    val readyDocs   = filteredDocs.filter { it.state == DocState.Completed }
    val partialDocs = filteredDocs.filter { it.state == DocState.InProgress && it.lines.any { l -> l.scanned > 0.0 } }
    var selectedDocType by remember { mutableStateOf(DocumentType.WAREHOUSE_SHIPMENT) }
    var docFilter by remember { mutableStateOf(DocumentFilter()) }
    var overviewFilter by remember { mutableStateOf(DocumentFilter()) }
    var overviewLockedSource by remember { mutableStateOf<String?>(null) }
    var overviewLockedRc     by remember { mutableStateOf<String?>(null) }
    var showSyncErrorDialog by remember { mutableStateOf(false) }
    var processingMessage by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            appVm.exportDatabase(it) {
                Toast.makeText(context, "Export saved", Toast.LENGTH_SHORT).show()
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
                onChangeLocationRc = { nav.navigate("location_rc_pick") },
                onOpenSettings = { nav.navigate("settings") },
                onTypeTap = { type ->
                    selectedDocType = type
                    nav.navigate("docs")
                },
                onDocumentOverview = { nav.navigate("dashboard?tab=1") },
                onShowErrors = { nav.navigate("dashboard") },
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
                navDomain = appVm.extSystemConfig.domain,
                credentialTtlHours = appVm.extSystemConfig.credentialTtlHours,
                onSelect = { rc, loc ->
                    onRcCodeChange(rc)
                    onLocationCodeChange(loc)
                    nav.popBackStack()
                },
                onRefresh = { appVm.downloadLocations(liveMode = liveMode) },
                onSaveCredentials = { d, u, p -> appVm.saveCredentials(d, u, p) },
                onBack = { nav.popBackStack() },
            )
        }
        composable("ext_system_config") {
            ExtSystemConfigScreen(
                initial = appVm.extSystemConfig,
                onSave  = { config ->
                    appVm.saveExtSystemConfig(config)
                    nav.popBackStack()
                },
                disabledDocTypes = disabledDocTypes,
                onDisabledDocTypesChange = onDisabledDocTypesChange,
            )
        }
        composable("settings") {
            SettingsScreen(
                user = user,
                location = location,
                rc = rc,
                textSize = textSize,
                onTextSizeChange = onTextSizeChange,
                uppercaseText = uppercaseText,
                onUppercaseTextChange = onUppercaseTextChange,
                language = language,
                onLanguageChange = onLanguageChange,
                lastScannedLines = lastScannedLines,
                onLastScannedLinesChange = onLastScannedLinesChange,
                autoScan = autoScan,
                onAutoScanChange = onAutoScanChange,
                hapticEnabled = hapticEnabled,
                onHapticEnabledChange = onHapticEnabledChange,
                muteSound = muteSound,
                onMuteSoundChange = onMuteSoundChange,
                onExport = {
                    val ts = exportTimestampFmt.format(Instant.now())
                    exportLauncher.launch("prima_export_${ts}.json")
                },
                onClearCache = { appVm.clearCache() },
                onInsertTestData = { appVm.insertTestData() },
                onBack = { nav.popBackStack() },
                onChangeLocation = { nav.navigate("location_rc_pick") },
                onOpenExtSystemConfig = { nav.navigate("ext_system_config") },
                credentialTtlHours = appVm.extSystemConfig.credentialTtlHours,
                navDomain = appVm.extSystemConfig.domain,
                onSaveCredentials = { d, u, p -> appVm.saveCredentials(d, u, p) },
                liveMode = liveMode,
                onLiveModeChange = onLiveModeChange,
                onSignOut = {},
            )
        }
        composable("docs") {
            val typeDocs = documents.filter { doc ->
                doc.type == selectedDocType &&
                    (if (selectedDocType.filterByRc) doc.rcCode == rc.code
                     else location != null && doc.sourceCode == location.code)
            }
            DocumentListScreen(
                docType = selectedDocType,
                locationCode = location?.code ?: "",
                documents = typeDocs,
                onBack = { nav.popBackStack() },
                onDocTap = { selected -> nav.navigate("recording/${selected.documentNo}/${selected.type.key}") },
                onDownload = { nav.navigate("download_filter") },
                onUpload = { docs ->
                    processingMessage = "Uploading..."
                    if (liveMode) {
                        appVm.uploadToExtSystem(docs) { failures ->
                            processingMessage = null
                            nav.popBackStack("main", false)
                            if (failures > 0) showSyncErrorDialog = true
                        }
                    } else {
                        appVm.testImportDocs(docs) { failures ->
                            processingMessage = null
                            nav.popBackStack("main", false)
                            if (failures > 0) showSyncErrorDialog = true
                        }
                    }
                },
                onErrorTap = { doc -> nav.navigate("upload_error/${doc.documentNo}") },
                onCreateDoc = { docNo, srcCode ->
                    val newDoc = Document(
                        documentNo       = docNo,
                        type             = selectedDocType,
                        destinationCode  = "",
                        sourceCode       = srcCode,
                        rcCode           = rc.code,
                        ownerUserId      = user.id,
                        creationDateTime = Instant.now(),
                        lines            = emptyList(),
                        state            = DocState.Downloaded,
                    )
                    appVm.createDocument(newDoc)
                    nav.navigate("recording/$docNo/${selectedDocType.key}")
                },
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
                rcCode = rc.code,
                documents = documents,
                onBack = { nav.popBackStack() },
                onDocTap = { selected -> nav.navigate("recording/${selected.documentNo}/${selected.type.key}") },
                onClearErrors = { appVm.clearErrorDocs() },
                onErrorTap = { doc -> nav.navigate("upload_error/${doc.documentNo}") },
                filter = overviewFilter,
                onOpenFilter = { src, rc ->
                    overviewLockedSource = src
                    overviewLockedRc = rc
                    nav.navigate("overview_filter")
                },
                initialTab = initialTab,
            )
        }
        composable("filter") {
            DocumentFilterScreen(
                initialFilter = docFilter,
                showDocTypeFilter = false,
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
                onApply = { newFilter ->
                    overviewFilter = newFilter
                    nav.popBackStack()
                },
                onBack = { nav.popBackStack() },
            )
        }
        composable("download_filter") {
            DownloadFilterScreen(
                hasCredentials = liveMode && appVm.extSystemCredentialStore.isValid(),
                onConfirm = { _, _, _ ->
                    processingMessage = "Downloading..."
                    if (liveMode) {
                        appVm.realDownloadDocuments { _ ->
                            processingMessage = null
                            nav.popBackStack()
                        }
                    } else {
                        appVm.testDownload {
                            processingMessage = null
                            nav.popBackStack()
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
                    onBack = { nav.popBackStack() },
                    onScan = { barcode, multiplier ->
                        currentDoc.lines.find { it.barcodeNo == barcode }?.let { line ->
                            vm.recordScan(line.lineNo, barcode, user.id, multiplier)
                        }
                    },
                    onLineUpdate = { lineNo, newScanned -> vm.setLineScanned(lineNo, newScanned) },
                    onExtraLineAdd = { barcodeNo, quantity -> vm.addExtraLine(barcodeNo, user.id, quantity) },
                    onExtraLineUpdate = { recordingLineNo, quantity -> vm.updateExtraLineQuantity(recordingLineNo, quantity) },
                    onExtraLineDelete = { recordingLineNo -> vm.deleteExtraLine(recordingLineNo) },
                    onUpload = {
                        processingMessage = "Uploading..."
                        if (liveMode) {
                            appVm.uploadToExtSystem(listOf(currentDoc)) { failures ->
                                processingMessage = null
                                nav.popBackStack("main", false)
                                if (failures > 0) showSyncErrorDialog = true
                            }
                        } else {
                            appVm.testImportDocs(listOf(currentDoc)) { failures ->
                                processingMessage = null
                                nav.popBackStack("main", false)
                                if (failures > 0) showSyncErrorDialog = true
                            }
                        }
                    },
                    lastScannedLines = lastScannedLines,
                    autoScan = autoScan,
                    hapticEnabled = hapticEnabled,
                    muteSound = muteSound,
                    debounceTime = debounceTime,
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
                        processingMessage = "Uploading..."
                        if (liveMode) {
                            appVm.uploadToExtSystem(listOf(currentDoc)) { failures ->
                                processingMessage = null
                                if (failures > 0) { showSyncErrorDialog = true } else { nav.popBackStack() }
                            }
                        } else {
                            appVm.testImportDocs(listOf(currentDoc)) { failures ->
                                processingMessage = null
                                if (failures > 0) { showSyncErrorDialog = true } else { nav.popBackStack() }
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

    if (showSyncErrorDialog) {
        AlertDialog(
            onDismissRequest = { showSyncErrorDialog = false },
            title = { Text("Sync completed with errors", fontWeight = FontWeight.Bold) },
            text = { Text("There are errors while sync. Do you want to see errors?") },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        onClick = { showSyncErrorDialog = false; nav.navigate("dashboard") },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFCE3A3A)),
                    ) { Text("See errors", fontWeight = FontWeight.Bold) }
                    OutlinedButton(
                        onClick = { showSyncErrorDialog = false },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) { Text("Dismiss") }
                }
            },
        )
    }
}
