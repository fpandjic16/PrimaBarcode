package com.prima.barcode.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
    user: User,
    location: Location?,
    rc: ResponsibilityCenter,
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
    muteSound: Boolean = false,
    onMuteSoundChange: (Boolean) -> Unit = {},
    liveMode: Boolean = false,
    onLiveModeChange: (Boolean) -> Unit = {},
    onExport: () -> Unit = {},
    onClearCache: () -> Unit = {},
    onInsertTestData: () -> Unit = {},
    onBack: () -> Unit,
    onChangeLocation: () -> Unit,
    onOpenExtSystemConfig: () -> Unit = {},
    credentialTtlHours: Int = 24,
    navDomain: String = "",
    onSaveCredentials: (domain: String, username: String, password: String) -> Unit = { _, _, _ -> },
    onSignOut: () -> Unit,
) {
    var autoCollapseTape by remember { mutableStateOf(true) }
    var confirmOnOver by remember { mutableStateOf(true) }
    var autoUpload by remember { mutableStateOf(false) }
    var syncOnWifiOnly by remember { mutableStateOf(true) }
    var loginSheetOpen by remember { mutableStateOf(false) }
    var showClearCacheDialog by remember { mutableStateOf(false) }
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
            // ── Location & RC ─────────────────────────────────────────────────
            item { SectionHeader(stringResource(R.string.settings_sec_location_rc)) }

            item {
                Column(modifier = Modifier.fillMaxWidth().background(Color.White)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onChangeLocation)
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SettingsIcon(Icons.Outlined.LocationOn)
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                location?.name ?: stringResource(R.string.settings_no_location),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Medium,
                                    color = if (location != null) PrimaPalette.Ink else PrimaPalette.Ink3,
                                ),
                            )
                            Text(
                                rc.name + if (location != null) " · ${location.code}" else "",
                                style = monoLabel.copy(color = PrimaPalette.Ink3),
                            )
                        }
                        Text(
                            stringResource(R.string.settings_change).uppercased,
                            style = monoLabel.copy(color = PrimaPalette.Coral, fontWeight = FontWeight.Medium),
                        )
                    }
                }
            }

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
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SettingsIcon(Icons.Outlined.Language)
                            Spacer(Modifier.width(14.dp))
                            Column {
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
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.padding(start = 54.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Language.entries.forEach { lang ->
                                val selected = lang == language
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (selected) PrimaPalette.Slate else PrimaPalette.CreamAlt)
                                        .clickable { onLanguageChange(lang) }
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        lang.label,
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
                                    color = if (autoScan) PrimaPalette.Ink else PrimaPalette.Ink3,
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
                                    .background(
                                        when {
                                            !autoScan  -> PrimaPalette.CreamAlt
                                            selected   -> PrimaPalette.Slate
                                            else       -> PrimaPalette.CreamAlt
                                        }
                                    )
                                    .clickable(enabled = autoScan) { onDebounceTimeChange(ms) }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    if (ms >= 1000) "${ms / 1000}s" else "${ms}ms",
                                    style = monoLabel.copy(
                                        color = when {
                                            !autoScan -> PrimaPalette.Ink3
                                            selected  -> Color.White
                                            else      -> PrimaPalette.Ink2
                                        },
                                        fontWeight = if (selected && autoScan) FontWeight.Medium else FontWeight.Normal,
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
                        icon = Icons.Outlined.VolumeOff,
                        label = stringResource(R.string.settings_mute),
                        description = stringResource(R.string.settings_mute_desc),
                        checked = muteSound,
                        onCheckedChange = onMuteSoundChange,
                    )
                    SettingsDivider()
                    ToggleRow(
                        icon = Icons.Outlined.ViewStream,
                        label = stringResource(R.string.settings_auto_collapse),
                        comingSoon = true,
                        description = stringResource(R.string.settings_auto_collapse_desc),
                        checked = autoCollapseTape,
                        onCheckedChange = { autoCollapseTape = it },
                    )
                    SettingsDivider()
                    ToggleRow(
                        icon = Icons.Outlined.Warning,
                        label = stringResource(R.string.settings_warn_over),
                        comingSoon = true,
                        description = stringResource(R.string.settings_warn_over_desc),
                        checked = confirmOnOver,
                        onCheckedChange = { confirmOnOver = it },
                    )
                    SettingsDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SettingsIcon(Icons.Outlined.List)
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
                    }
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
                                    .clickable { onLastScannedLinesChange(n) }
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

            // ── Sync ──────────────────────────────────────────────────────────
            item { SectionHeader(stringResource(R.string.settings_sec_sync)) }

            item {
                Column(modifier = Modifier.fillMaxWidth().background(Color.White)) {
                    ToggleRow(
                        icon = Icons.Outlined.CloudSync,
                        label = stringResource(R.string.settings_live_mode),
                        description = stringResource(R.string.settings_live_mode_desc),
                        checked = liveMode,
                        onCheckedChange = onLiveModeChange,
                    )
                    SettingsDivider()
                    ToggleRow(
                        icon = Icons.Outlined.CloudUpload,
                        label = stringResource(R.string.settings_auto_upload),
                        comingSoon = true,
                        description = stringResource(R.string.settings_auto_upload_desc),
                        checked = autoUpload,
                        onCheckedChange = { autoUpload = it },
                    )
                    SettingsDivider()
                    ToggleRow(
                        icon = Icons.Outlined.Wifi,
                        label = stringResource(R.string.settings_wifi_only),
                        comingSoon = true,
                        description = stringResource(R.string.settings_wifi_only_desc),
                        checked = syncOnWifiOnly,
                        onCheckedChange = { syncOnWifiOnly = it },
                    )
                    SettingsDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { loginSheetOpen = true }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SettingsIcon(Icons.Outlined.Sync)
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.settings_test_signin),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = PrimaPalette.Ink,
                                    fontWeight = FontWeight.Normal,
                                ),
                            )
                            Text(
                                stringResource(R.string.settings_test_signin_desc),
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

            // ── Data ──────────────────────────────────────────────────────────
            item { SectionHeader(stringResource(R.string.settings_sec_data)) }

            item {
                Column(modifier = Modifier.fillMaxWidth().background(Color.White)) {
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
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(PrimaPalette.SlateAlt),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                user.initials,
                                style = monoLabel.copy(color = Color.White, fontWeight = FontWeight.Medium),
                            )
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                user.displayName,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Medium,
                                    color = PrimaPalette.Ink,
                                ),
                            )
                            Text(
                                user.username,
                                style = monoLabel.copy(color = PrimaPalette.Ink3),
                            )
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
                            Icons.Outlined.Logout,
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

    if (loginSheetOpen) {
        LoginSheet(
            initialDomain = navDomain,
            credentialTtlHours = credentialTtlHours,
            onSubmit = { d, u, p -> onSaveCredentials(d, u, p); loginSheetOpen = false },
            onDismiss = { loginSheetOpen = false },
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
