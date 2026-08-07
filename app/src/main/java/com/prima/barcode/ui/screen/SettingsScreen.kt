package com.prima.barcode.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.prima.barcode.data.model.Location
import com.prima.barcode.data.model.ResponsibilityCenter
import com.prima.barcode.data.model.User
import com.prima.barcode.ui.component.PrimaTopBar
import com.prima.barcode.ui.component.verticalScrollbar
import com.prima.barcode.ui.theme.PrimaPalette
import com.prima.barcode.ui.theme.Language
import com.prima.barcode.ui.theme.TextSize
import android.os.Build
import androidx.compose.ui.platform.LocalContext
import com.prima.barcode.BuildConfig
import com.prima.barcode.ui.theme.monoLabel
import com.prima.barcode.ui.theme.uppercased
import androidx.compose.ui.res.stringResource
import com.prima.barcode.R

private const val DB_SCHEMA_VERSION = "7.0.0"

@Composable
fun SettingsScreen(
    user: User?,
    location: Location?,
    rc: ResponsibilityCenter?,
    textSize: TextSize,
    onTextSizeChange: (TextSize) -> Unit,
    uppercaseText: Boolean = false,
    onUppercaseTextChange: (Boolean) -> Unit = {},
    language: Language = Language.ENGLISH,
    onLanguageChange: (Language) -> Unit = {},
    lastScannedLines: Int = 5,
    onLastScannedLinesChange: (Int) -> Unit = {},
    autoScan: Boolean = false,
    onAutoScanChange: (Boolean) -> Unit = {},
    debounceTime: Int = 500,
    onDebounceTimeChange: (Int) -> Unit = {},
    hapticEnabled: Boolean = true,
    onHapticEnabledChange: (Boolean) -> Unit = {},
    warnOnOver: Boolean = true,
    onWarnOnOverChange: (Boolean) -> Unit = {},
    warnNotOnDocument: Boolean = true,
    onWarnNotOnDocumentChange: (Boolean) -> Unit = {},
    askQtyForUnknownBarcode: Boolean = true,
    onAskQtyForUnknownBarcodeChange: (Boolean) -> Unit = {},
    autoUploadCompleted: Boolean = false,
    onAutoUploadChange: (Boolean) -> Unit = {},
    backgroundSync: Boolean = false,
    onBackgroundSyncChange: (Boolean) -> Unit = {},
    debuggerActive: Boolean = false,
    onDebuggerActiveChange: (Boolean) -> Unit = {},
    onExport: () -> Unit = {},
    onClearCache: () -> Unit = {},
    onDeleteAllDocuments: () -> Unit = {},
    onInsertTestData: () -> Unit = {},
    onBack: () -> Unit,
    onChangeLocation: () -> Unit,
    onOpenExtSystemConfig: () -> Unit = {},
    onSignOut: () -> Unit,
    onSignInTap: () -> Unit = {},
) {
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showDeleteAllDocumentsDialog by remember { mutableStateOf(false) }
    var showInsertTestDataDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(PrimaPalette.Cream)) {
        PrimaTopBar(
            title = stringResource(R.string.settings_title),
            subtitle = stringResource(R.string.settings_subtitle),
            onBack = onBack,
        )

        val listState = rememberLazyListState()
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScrollbar(listState),
            contentPadding = PaddingValues(bottom = 32.dp),
            state = listState,
        ) {
            // ── Appearance ────────────────────────────────────────────────────
            item { SectionHeader(stringResource(R.string.settings_sec_appearance)) }

            item {
                Column(modifier = Modifier.fillMaxWidth().background(Color.White)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SettingsIcon(Icons.Outlined.TextFields)
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.settings_text_size),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = PrimaPalette.Ink,
                                    fontWeight = FontWeight.Normal,
                                ),
                            )
                            Text(
                                stringResource(R.string.settings_text_size_desc),
                                style = monoLabel.copy(color = PrimaPalette.Ink3),
                            )
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 74.dp, end = 20.dp, bottom = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TextSize.entries.forEach { size ->
                            val selected = size == textSize
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) PrimaPalette.Slate else PrimaPalette.CreamAlt)
                                    .clickable { onTextSizeChange(size) }
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    size.label,
                                    style = monoLabel.copy(
                                        color = if (selected) Color.White else PrimaPalette.Ink2,
                                        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                                    ),
                                )
                            }
                        }
                    }
                    SettingsDivider()
                    ToggleRow(
                        icon = Icons.Outlined.TextFormat,
                        label = stringResource(R.string.settings_uppercase),
                        description = stringResource(R.string.settings_uppercase_desc),
                        checked = uppercaseText,
                        onCheckedChange = onUppercaseTextChange,
                    )
                    SettingsDivider()
                    var langMenuOpen by remember { mutableStateOf(false) }
                    Box {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { langMenuOpen = true }
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SettingsIcon(Icons.Outlined.Language)
                            Spacer(Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.settings_language),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = PrimaPalette.Ink,
                                        fontWeight = FontWeight.Normal,
                                    ),
                                )
                                Text(
                                    stringResource(R.string.settings_language_desc),
                                    style = monoLabel.copy(color = PrimaPalette.Ink3),
                                )
                            }
                            Text(
                                language.label,
                                style = monoLabel.copy(color = PrimaPalette.Ink3, fontWeight = FontWeight.Medium),
                            )
                        }
                        DropdownMenu(
                            expanded = langMenuOpen,
                            onDismissRequest = { langMenuOpen = false },
                        ) {
                            Language.entries.forEach { lang ->
                                DropdownMenuItem(
                                    text = { Text(lang.label) },
                                    onClick = { onLanguageChange(lang); langMenuOpen = false },
                                    leadingIcon = if (lang == language) {
                                        { Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                    } else null,
                                )
                            }
                        }
                    }
                }
            }

            // ── Scanning ──────────────────────────────────────────────────────
            item { SectionHeader(stringResource(R.string.settings_sec_scanning)) }

            item {
                Column(modifier = Modifier.fillMaxWidth().background(Color.White)) {
                    ToggleRow(
                        icon = Icons.Outlined.QrCodeScanner,
                        label = stringResource(R.string.settings_continuous_scan),
                        description = stringResource(R.string.settings_continuous_scan_desc),
                        checked = autoScan,
                        onCheckedChange = onAutoScanChange,
                    )
                    SettingsDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SettingsIcon(Icons.Outlined.Timer)
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.settings_debounce),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = PrimaPalette.Ink,
                                    fontWeight = FontWeight.Normal,
                                ),
                            )
                            Text(
                                stringResource(R.string.settings_debounce_desc),
                                style = monoLabel.copy(color = PrimaPalette.Ink3),
                            )
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 74.dp, end = 20.dp, bottom = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        listOf(200, 300, 500, 1000, 2000).forEach { ms ->
                            val selected = ms == debounceTime
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) PrimaPalette.Slate else PrimaPalette.CreamAlt)
                                    .clickable { onDebounceTimeChange(ms) }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    if (ms >= 1000) "${ms / 1000}s" else "${ms}ms",
                                    style = monoLabel.copy(
                                        color = if (selected) Color.White else PrimaPalette.Ink2,
                                        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                                    ),
                                )
                            }
                        }
                    }
                    SettingsDivider()
                    ToggleRow(
                        icon = Icons.Outlined.Vibration,
                        label = stringResource(R.string.settings_haptic),
                        description = stringResource(R.string.settings_haptic_desc),
                        checked = hapticEnabled,
                        onCheckedChange = onHapticEnabledChange,
                    )
                    SettingsDivider()
                    ToggleRow(
                        icon = Icons.Outlined.Warning,
                        label = stringResource(R.string.settings_warn_over),
                        description = stringResource(R.string.settings_warn_over_desc),
                        checked = warnOnOver,
                        onCheckedChange = onWarnOnOverChange,
                    )
                    SettingsDivider()
                    ToggleRow(
                        icon = Icons.Outlined.ErrorOutline,
                        label = "Warn on not on document",
                        description = "Warn when scanning items that aren't on the document.",
                        checked = warnNotOnDocument,
                        onCheckedChange = onWarnNotOnDocumentChange,
                    )
                    SettingsDivider()
                    ToggleRow(
                        icon = Icons.Outlined.Dialpad,
                        label = "Ask for Qty for unknown barcode",
                        description = "Show a quantity screen when scanning a barcode that's not on the document. When off, it's recorded with Qty = 1 automatically.",
                        checked = askQtyForUnknownBarcode,
                        onCheckedChange = onAskQtyForUnknownBarcodeChange,
                    )
                    SettingsDivider()
                    var lastScannedExpanded by remember { mutableStateOf(false) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { lastScannedExpanded = !lastScannedExpanded }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SettingsIcon(Icons.AutoMirrored.Outlined.List)
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.settings_last_scanned),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = PrimaPalette.Ink,
                                    fontWeight = FontWeight.Normal,
                                ),
                            )
                            Text(
                                stringResource(R.string.settings_last_scanned_desc),
                                style = monoLabel.copy(color = PrimaPalette.Ink3),
                            )
                        }
                        Text(
                            lastScannedLines.toString(),
                            style = monoLabel.copy(color = PrimaPalette.Ink3, fontWeight = FontWeight.Medium),
                        )
                    }
                    if (lastScannedExpanded) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 74.dp, end = 20.dp, bottom = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            (0..5).forEach { n ->
                                val selected = n == lastScannedLines
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (selected) PrimaPalette.Slate else PrimaPalette.CreamAlt)
                                        .clickable { onLastScannedLinesChange(n); lastScannedExpanded = false }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        n.toString(),
                                        style = monoLabel.copy(
                                            color = if (selected) Color.White else PrimaPalette.Ink2,
                                            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── Sync ──────────────────────────────────────────────────────────
            item { SectionHeader(stringResource(R.string.settings_sec_sync)) }

            item {
                Column(modifier = Modifier.fillMaxWidth().background(Color.White)) {
                    ToggleRow(
                        icon = Icons.Outlined.CloudUpload,
                        label = stringResource(R.string.settings_auto_upload),
                        description = stringResource(R.string.settings_auto_upload_desc),
                        checked = autoUploadCompleted,
                        onCheckedChange = onAutoUploadChange,
                    )
                    SettingsDivider()
                    ToggleRow(
                        icon = Icons.Outlined.Sync,
                        label = "Enable background sync",
                        description = "Upload in the background so you can keep working while documents are sent.",
                        checked = backgroundSync,
                        onCheckedChange = onBackgroundSyncChange,
                    )
                }
            }

            // ── External System Configuration ─────────────────────────────────────────────────────
            item { SectionHeader(stringResource(R.string.settings_sec_ext_config)) }

            item {
                Column(modifier = Modifier.fillMaxWidth().background(Color.White)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenExtSystemConfig)
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SettingsIcon(Icons.Outlined.Dns)
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.settings_server_endpoints),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = PrimaPalette.Ink,
                                    fontWeight = FontWeight.Normal,
                                ),
                            )
                            Text(
                                stringResource(R.string.settings_server_endpoints_desc),
                                style = monoLabel.copy(color = PrimaPalette.Ink3),
                            )
                        }
                        Icon(
                            Icons.Outlined.ChevronRight,
                            contentDescription = null,
                            tint = PrimaPalette.Ink4,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            // ── Debug ─────────────────────────────────────────────────────────
            item { SectionHeader(stringResource(R.string.settings_sec_debug)) }

            item {
                Column(modifier = Modifier.fillMaxWidth().background(Color.White)) {
                    ToggleRow(
                        icon = Icons.Outlined.BugReport,
                        label = stringResource(R.string.settings_debugger_active),
                        description = stringResource(R.string.settings_debugger_active_desc),
                        checked = debuggerActive,
                        onCheckedChange = onDebuggerActiveChange,
                    )
                    SettingsDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onExport)
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SettingsIcon(Icons.Outlined.FileDownload)
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.settings_export),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = PrimaPalette.Ink,
                                    fontWeight = FontWeight.Normal,
                                ),
                            )
                            Text(
                                stringResource(R.string.settings_export_desc),
                                style = monoLabel.copy(color = PrimaPalette.Ink3),
                            )
                        }
                        Icon(
                            Icons.Outlined.ChevronRight,
                            contentDescription = null,
                            tint = PrimaPalette.Ink4,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    SettingsDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showInsertTestDataDialog = true }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SettingsIcon(Icons.Outlined.DataObject)
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.settings_insert_test),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = PrimaPalette.Ink,
                                    fontWeight = FontWeight.Normal,
                                ),
                            )
                            Text(
                                stringResource(R.string.settings_insert_test_desc),
                                style = monoLabel.copy(color = PrimaPalette.Ink3),
                            )
                        }
                        Icon(
                            Icons.Outlined.ChevronRight,
                            contentDescription = null,
                            tint = PrimaPalette.Ink4,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    SettingsDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showClearCacheDialog = true }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFFFEAEA)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Outlined.DeleteForever, contentDescription = null, tint = SignOutRed, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.settings_clear_cache),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = SignOutRed,
                                    fontWeight = FontWeight.Medium,
                                ),
                            )
                            Text(
                                stringResource(R.string.settings_clear_cache_desc),
                                style = monoLabel.copy(color = PrimaPalette.Ink3),
                            )
                        }
                    }
                    SettingsDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDeleteAllDocumentsDialog = true }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFFFEAEA)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Outlined.DeleteForever, contentDescription = null, tint = SignOutRed, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Delete all documents and recordings",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = SignOutRed,
                                    fontWeight = FontWeight.Medium,
                                ),
                            )
                            Text(
                                "Permanently deletes every downloaded document, line, and recording. Settings and sign-in stay untouched.",
                                style = monoLabel.copy(color = PrimaPalette.Ink3),
                            )
                        }
                    }
                }
            }

            // ── Account ──────────────────────────────────────────────────────
            // System info
            item { SectionHeader(stringResource(R.string.settings_sec_system_info)) }

            item {
                Column(modifier = Modifier.fillMaxWidth().background(Color.White)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SettingsIcon(Icons.Outlined.Info)
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.settings_app_version),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = PrimaPalette.Ink,
                                    fontWeight = FontWeight.Normal,
                                ),
                            )
                            Text(
                                BuildConfig.VERSION_NAME + " (build " + BuildConfig.VERSION_CODE + ")",
                                style = monoLabel.copy(color = PrimaPalette.Ink3),
                            )
                        }
                    }
                    SettingsDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SettingsIcon(Icons.Outlined.PhoneAndroid)
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.settings_android),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = PrimaPalette.Ink,
                                    fontWeight = FontWeight.Normal,
                                ),
                            )
                            Text(
                                "Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")",
                                style = monoLabel.copy(color = PrimaPalette.Ink3),
                            )
                        }
                    }
                    SettingsDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val ctx = LocalContext.current
                        SettingsIcon(Icons.Outlined.Code)
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.settings_sdk),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = PrimaPalette.Ink,
                                    fontWeight = FontWeight.Normal,
                                ),
                            )
                            Text(
                                "Min API " + ctx.applicationInfo.minSdkVersion + " (Android " + apiToAndroid(ctx.applicationInfo.minSdkVersion) + ") · Target API " + ctx.applicationInfo.targetSdkVersion + " (Android " + apiToAndroid(ctx.applicationInfo.targetSdkVersion) + ")",
                                style = monoLabel.copy(color = PrimaPalette.Ink3),
                            )
                        }
                    }
                    SettingsDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SettingsIcon(Icons.Outlined.Storage)
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.settings_db_schema),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = PrimaPalette.Ink,
                                    fontWeight = FontWeight.Normal,
                                ),
                            )
                            Text(
                                "Room v" + DB_SCHEMA_VERSION,
                                style = monoLabel.copy(color = PrimaPalette.Ink3),
                            )
                        }
                    }
                }
            }

            item { SectionHeader(stringResource(R.string.settings_sec_account)) }

            item {
                Column(modifier = Modifier.fillMaxWidth().background(Color.White)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = user == null, onClick = onSignInTap)
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (user != null) PrimaPalette.SlateAlt else Color(0xFFBBBBBB)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                user?.initials ?: "?",
                                style = monoLabel.copy(color = Color.White, fontWeight = FontWeight.Medium),
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                user?.displayName ?: stringResource(R.string.settings_not_signed_in),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Medium,
                                    color = if (user != null) PrimaPalette.Ink else PrimaPalette.Ink3,
                                ),
                            )
                            if (user != null) {
                                Text(
                                    user.username,
                                    style = monoLabel.copy(color = PrimaPalette.Ink3),
                                )
                            }
                        }
                    }
                    SettingsDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onSignOut)
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Outlined.Logout,
                            contentDescription = null,
                            tint = SignOutRed,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            stringResource(R.string.settings_sign_out),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = SignOutRed,
                                fontWeight = FontWeight.Medium,
                            ),
                        )
                    }
                }
            }
        }
    }

    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = { Text(stringResource(R.string.dialog_clear_cache_title)) },
            text = { Text(stringResource(R.string.dialog_clear_cache_text)) },
            confirmButton = {
                TextButton(
                    onClick = { showClearCacheDialog = false; onClearCache() },
                    colors = ButtonDefaults.textButtonColors(contentColor = SignOutRed),
                ) { Text(stringResource(R.string.btn_clear)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) { Text(stringResource(R.string.btn_cancel)) }
            },
        )
    }

    if (showDeleteAllDocumentsDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDocumentsDialog = false },
            title = { Text("Delete all documents and recordings?") },
            text = { Text("This permanently deletes every downloaded document, line, and recording — including any unsynced scans. This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = { showDeleteAllDocumentsDialog = false; onDeleteAllDocuments() },
                    colors = ButtonDefaults.textButtonColors(contentColor = SignOutRed),
                ) { Text(stringResource(R.string.btn_clear)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDocumentsDialog = false }) { Text(stringResource(R.string.btn_cancel)) }
            },
        )
    }

    if (showInsertTestDataDialog) {
        AlertDialog(
            onDismissRequest = { showInsertTestDataDialog = false },
            title = { Text(stringResource(R.string.dialog_insert_test_title)) },
            text = { Text(stringResource(R.string.dialog_insert_test_text)) },
            confirmButton = {
                TextButton(
                    onClick = { showInsertTestDataDialog = false; onInsertTestData() },
                ) { Text(stringResource(R.string.btn_yes_insert)) }
            },
            dismissButton = {
                TextButton(onClick = { showInsertTestDataDialog = false }) { Text(stringResource(R.string.btn_cancel)) }
            },
        )
    }
}

private val SignOutRed = Color(0xFFCE3A3A)

@Composable
private fun SectionHeader(title: String) {
    Text(
        title.uppercased,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp),
        style = monoLabel.copy(color = PrimaPalette.Ink3),
    )
}

@Composable
private fun SettingsIcon(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(PrimaPalette.CreamAlt),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = PrimaPalette.Slate, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 74.dp),
        color = Color(0x0A000000),
    )
}

@Composable
private fun ToggleRow(
    icon: ImageVector,
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    comingSoon: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (comingSoon) 0.45f else 1f)
            .then(if (!comingSoon) Modifier.clickable { onCheckedChange(!checked) } else Modifier)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsIcon(icon)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = PrimaPalette.Ink,
                        fontWeight = FontWeight.Normal,
                    ),
                )
                if (comingSoon) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(PrimaPalette.CreamAlt)
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                    ) {
                        Text(
                            stringResource(R.string.settings_coming_soon),
                            style = monoLabel.copy(
                                color = PrimaPalette.Ink3,
                                fontWeight = FontWeight.Medium,
                            ),
                        )
                    }
                }
            }
            Text(description, style = monoLabel.copy(color = PrimaPalette.Ink3))
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = !comingSoon,
            colors = SwitchDefaults.colors(
                checkedTrackColor = PrimaPalette.Slate,
                checkedThumbColor = Color.White,
            ),
        )
    }
}

private fun apiToAndroid(api: Int): String = when (api) {
    24 -> "7.0"
    25 -> "7.1"
    26 -> "8.0"
    27 -> "8.1"
    28 -> "9"
    29 -> "10"
    30 -> "11"
    31 -> "12"
    32 -> "12L"
    33 -> "13"
    34 -> "14"
    35 -> "15"
    36 -> "16"
    else -> "?"
}
