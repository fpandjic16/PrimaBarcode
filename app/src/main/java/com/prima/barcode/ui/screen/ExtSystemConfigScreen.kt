package com.prima.barcode.ui.screen

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.prima.barcode.data.auth.ExtSystemConfig
import com.prima.barcode.data.model.DocTypeFilterMode
import com.prima.barcode.data.model.DocumentType
import com.prima.barcode.ui.component.PrimaTopBar
import com.prima.barcode.ui.theme.PrimaPalette
import com.prima.barcode.ui.theme.monoLabel
import androidx.compose.ui.res.stringResource
import com.prima.barcode.R

private val TTL_OPTIONS = listOf(8, 24, 48, 168)
private fun Int.ttlLabel() = when (this) {
    8    -> "8 h"
    24   -> "24 h"
    48   -> "48 h"
    168  -> "7 d"
    else -> "$this h"
}

@androidx.compose.runtime.Composable
private fun DocumentType.localizedDisplay(): String = when (this) {
    DocumentType.WAREHOUSE_SHIPMENT -> stringResource(R.string.doctype_warehouse_shipment)
    DocumentType.WAREHOUSE_RECEIPT  -> stringResource(R.string.doctype_warehouse_receipt)
    DocumentType.RETAIL_SHIPMENT    -> stringResource(R.string.doctype_retail_shipment)
    DocumentType.RETAIL_RECEIPT     -> stringResource(R.string.doctype_retail_receipt)
    DocumentType.TRANSPORT_SHEET    -> stringResource(R.string.doctype_transport_sheet)
}

@Composable
fun ExtSystemConfigScreen(
    initial: ExtSystemConfig,
    onSave: (ExtSystemConfig) -> Unit,
    onDiscard: () -> Unit = {},
    loadDefaults: () -> ExtSystemConfig? = { null },
    disabledDocTypes: Set<String> = emptySet(),
    onDisabledDocTypesChange: (Set<String>) -> Unit = {},
    docTypeFilters: Map<String, DocTypeFilterMode> = emptyMap(),
    onDocTypeFiltersChange: (Map<String, DocTypeFilterMode>) -> Unit = {},
    onTestConnection: (
        serverBaseUrl: String,
        username: String,
        password: String,
        onResult: (success: Boolean, message: String) -> Unit,
    ) -> Unit = { _, _, _, cb -> cb(false, "Test connection is not wired up") },
) {
    var serverBaseUrl             by remember { mutableStateOf(initial.serverBaseUrl) }
    var ttlHours                 by remember { mutableIntStateOf(initial.credentialTtlHours) }
    val endpointUrls = remember {
        mutableStateMapOf<DocumentType, String>().also { map ->
            DocumentType.entries.forEach { map[it] = initial.endpointFor(it) }
        }
    }
    var recordingSyncUrl          by remember { mutableStateOf(initial.recordingSyncUrl) }
    var locationsUrl              by remember { mutableStateOf(initial.locationsUrl) }
    var responsibilityCentersUrl  by remember { mutableStateOf(initial.responsibilityCentersUrl) }

    var showLoginSheet by remember { mutableStateOf(false) }
    var testing        by remember { mutableStateOf(false) }
    var testResult     by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var showExitDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current

    fun buildConfig() = ExtSystemConfig(
        serverBaseUrl            = serverBaseUrl.trim(),
        credentialTtlHours       = ttlHours,
        endpointUrls             = endpointUrls.toMap(),
        recordingSyncUrl         = recordingSyncUrl.trim(),
        locationsUrl             = locationsUrl.trim(),
        responsibilityCentersUrl = responsibilityCentersUrl.trim(),
    )

    fun applyConfig(c: ExtSystemConfig) {
        serverBaseUrl = c.serverBaseUrl
        ttlHours = c.credentialTtlHours
        DocumentType.entries.forEach { endpointUrls[it] = c.endpointFor(it) }
        recordingSyncUrl = c.recordingSyncUrl
        locationsUrl = c.locationsUrl
        responsibilityCentersUrl = c.responsibilityCentersUrl
    }

    fun attemptExit() {
        if (buildConfig() != initial) showExitDialog = true else onDiscard()
    }

    BackHandler { attemptExit() }

    Column(modifier = Modifier.fillMaxSize().background(PrimaPalette.Cream)) {
        PrimaTopBar(
            title    = stringResource(R.string.ext_config_title),
            subtitle = stringResource(R.string.ext_config_subtitle),
            onBack   = { attemptExit() },
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 40.dp),
        ) {
            // ── Insert default parameters ─────────────────────────────────────
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                    Button(
                        onClick = {
                            val defaults = loadDefaults()
                            if (defaults != null) {
                                applyConfig(defaults)
                                Toast.makeText(context, "Default parameters inserted", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Could not load ext_system_defaults.json", Toast.LENGTH_LONG).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) {
                        Text("Insert default parameters", fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        "Fills every field below from the bundled ext_system_defaults.json.",
                        style = monoLabel.copy(color = PrimaPalette.Ink3),
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }

            // ── Connection ────────────────────────────────────────────────────
            item { ConfigSectionHeader(stringResource(R.string.ext_config_sec_connection)) }
            item {
                ConfigCard {
                    ConfigField(
                        label = stringResource(R.string.ext_config_server_url),
                        hint  = "http://server:7048/ExternalSystem",
                        value = serverBaseUrl,
                        onValueChange = { serverBaseUrl = it },
                        keyboardType = KeyboardType.Uri,
                    )
                    ConfigDivider()
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        OutlinedButton(
                            onClick = { showLoginSheet = true },
                            enabled = serverBaseUrl.isNotBlank() && !testing,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                        ) {
                            Text("Test connection", fontWeight = FontWeight.SemiBold)
                        }
                        Text(
                            "Signs in with your Windows credentials and runs an authenticated request against the server URL.",
                            style = monoLabel.copy(color = PrimaPalette.Ink3),
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }

            // ── Session TTL ───────────────────────────────────────────────────
            item { ConfigSectionHeader(stringResource(R.string.ext_config_sec_session)) }
            item {
                ConfigCard {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                        Text(
                            stringResource(R.string.ext_config_session_duration),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Medium, color = PrimaPalette.Ink,
                            ),
                        )
                        Text(
                            stringResource(R.string.ext_config_session_desc),
                            style = monoLabel.copy(color = PrimaPalette.Ink3),
                        )
                        Spacer(Modifier.height(12.dp))
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            TTL_OPTIONS.forEachIndexed { idx, hours ->
                                SegmentedButton(
                                    selected = ttlHours == hours,
                                    onClick  = { ttlHours = hours },
                                    shape    = SegmentedButtonDefaults.itemShape(idx, TTL_OPTIONS.size),
                                ) { Text(hours.ttlLabel(), style = monoLabel) }
                            }
                        }
                    }
                }
            }

            // ── Document endpoints ────────────────────────────────────────────
            item { ConfigSectionHeader(stringResource(R.string.ext_config_sec_endpoints)) }
            for (type in DocumentType.entries) {
                item(key = type.key) {
                    val enabled = type.key !in disabledDocTypes
                    ConfigCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = if (enabled) 0.dp else 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                type.localizedDisplay(),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Medium,
                                    color = if (enabled) PrimaPalette.Ink else PrimaPalette.Ink3,
                                ),
                                modifier = Modifier.weight(1f),
                            )
                            Switch(
                                checked = enabled,
                                onCheckedChange = { on ->
                                    // Disabling a doc type clears its endpoint URL (not just hides the field)
                                    if (!on) endpointUrls[type] = ""
                                    onDisabledDocTypesChange(
                                        if (on) disabledDocTypes - type.key else disabledDocTypes + type.key
                                    )
                                },
                                colors = SwitchDefaults.colors(
                                    checkedTrackColor = PrimaPalette.Slate,
                                    checkedThumbColor = Color.White,
                                ),
                            )
                        }
                        if (enabled) {
                            ConfigField(
                                label = "URL",
                                hint  = "e.g. /OData/Company('Name')/WMS_Lines",
                                value = endpointUrls[type] ?: "",
                                onValueChange = { endpointUrls[type] = it },
                            )
                            ConfigDivider()
                            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                Text(
                                    "Filter by",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.Medium, color = PrimaPalette.Ink,
                                    ),
                                )
                                Spacer(Modifier.height(8.dp))
                                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                    listOf(DocTypeFilterMode.LOCATION, DocTypeFilterMode.RESPONSIBILITY_CENTER).forEachIndexed { idx, mode ->
                                        SegmentedButton(
                                            selected = (docTypeFilters[type.key] ?: DocTypeFilterMode.LOCATION) == mode,
                                            onClick = { onDocTypeFiltersChange(docTypeFilters + (type.key to mode)) },
                                            shape = SegmentedButtonDefaults.itemShape(idx, 2),
                                        ) {
                                            Text(
                                                when (mode) {
                                                    DocTypeFilterMode.LOCATION -> "Location"
                                                    DocTypeFilterMode.RESPONSIBILITY_CENTER -> "Responsibility Center"
                                                },
                                                style = monoLabel,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (type != DocumentType.entries.last()) ConfigDivider()
                }
            }

            // ── Reference data ────────────────────────────────────────────────
            item { ConfigSectionHeader(stringResource(R.string.ext_config_sec_reference)) }
            item {
                ConfigCard {
                    ConfigField(
                        label = stringResource(R.string.ext_config_locations_url),
                        hint  = "e.g. /OData/Company('Name')/WMS_Locations",
                        value = locationsUrl,
                        onValueChange = { locationsUrl = it },
                    )
                    ConfigDivider()
                    ConfigField(
                        label = stringResource(R.string.ext_config_rc_url),
                        hint  = "e.g. /OData/Company('Name')/WMS_ResponsibilityCenters",
                        value = responsibilityCentersUrl,
                        onValueChange = { responsibilityCentersUrl = it },
                    )
                }
            }

            // ── Recording sync ────────────────────────────────────────────────
            item { ConfigSectionHeader(stringResource(R.string.ext_config_sec_recording)) }
            item {
                ConfigCard {
                    ConfigField(
                        label = stringResource(R.string.ext_config_recording_url),
                        hint  = "e.g. /OData/Company('Name')/WMS_RecordingSync",
                        value = recordingSyncUrl,
                        onValueChange = { recordingSyncUrl = it },
                        imeAction = ImeAction.Done,
                    )
                }
            }
        }
    }

    if (showLoginSheet) {
        LoginSheet(
            credentialTtlHours = ttlHours,
            ctaLabel           = "Test connection",
            onDismiss          = { showLoginSheet = false },
            onSubmit           = { u, p ->
                showLoginSheet = false
                testing = true
                onTestConnection(serverBaseUrl.trim(), u, p) { ok, msg ->
                    testing = false
                    testResult = ok to msg
                }
            },
        )
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Save changes?", fontWeight = FontWeight.Bold) },
            text = { Text("Save the external system configuration before leaving?") },
            confirmButton = {
                Button(onClick = { showExitDialog = false; onSave(buildConfig()) }) {
                    Text("Yes", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showExitDialog = false; onDiscard() }) {
                    Text("No")
                }
            },
        )
    }

    if (testing) {
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        ) {
            Surface(shape = RoundedCornerShape(16.dp), color = Color.White) {
                Row(
                    modifier = Modifier.padding(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                    Spacer(Modifier.width(16.dp))
                    Text("Testing connection…", fontWeight = FontWeight.Medium)
                }
            }
        }
    }

    testResult?.let { (ok, message) ->
        AlertDialog(
            onDismissRequest = { testResult = null },
            title = {
                Text(
                    if (ok) "Connection OK" else "Connection failed",
                    fontWeight = FontWeight.Bold,
                    color = if (ok) PrimaPalette.Teal else Color(0xFFCE3A3A),
                )
            },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { testResult = null }) { Text("OK") }
            },
        )
    }
}

@Composable
private fun ConfigSectionHeader(title: String) {
    Text(
        title.uppercase(),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp),
        style = monoLabel.copy(color = PrimaPalette.Ink3),
    )
}

@Composable
private fun ConfigCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White),
        content = content,
    )
}

@Composable
private fun ConfigField(
    label: String,
    hint: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Medium, color = PrimaPalette.Ink,
            ),
        )
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(hint, style = monoLabel.copy(color = PrimaPalette.Ink4)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            textStyle = monoLabel.copy(color = PrimaPalette.Ink),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            shape = RoundedCornerShape(8.dp),
        )
    }
}

@Composable
private fun ConfigDivider() {
    HorizontalDivider(modifier = Modifier.padding(start = 16.dp), color = Color(0x0A000000))
}
