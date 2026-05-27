package com.prima.barcode.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import com.prima.barcode.data.auth.ExtSystemConfig
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
    disabledDocTypes: Set<String> = emptySet(),
    onDisabledDocTypesChange: (Set<String>) -> Unit = {},
) {
    var serverBaseUrl             by remember { mutableStateOf(initial.serverBaseUrl) }
    var domain                   by remember { mutableStateOf(initial.domain) }
    var ttlHours                 by remember { mutableIntStateOf(initial.credentialTtlHours) }
    val endpointUrls = remember {
        mutableStateMapOf<DocumentType, String>().also { map ->
            DocumentType.entries.forEach { map[it] = initial.endpointFor(it) }
        }
    }
    var recordingSyncUrl          by remember { mutableStateOf(initial.recordingSyncUrl) }
    var locationsUrl              by remember { mutableStateOf(initial.locationsUrl) }
    var responsibilityCentersUrl  by remember { mutableStateOf(initial.responsibilityCentersUrl) }

    fun buildConfig() = ExtSystemConfig(
        serverBaseUrl            = serverBaseUrl.trim(),
        domain                   = domain.trim(),
        credentialTtlHours       = ttlHours,
        endpointUrls             = endpointUrls.toMap(),
        recordingSyncUrl         = recordingSyncUrl.trim(),
        locationsUrl             = locationsUrl.trim(),
        responsibilityCentersUrl = responsibilityCentersUrl.trim(),
    )

    Column(modifier = Modifier.fillMaxSize().background(PrimaPalette.Cream)) {
        PrimaTopBar(
            title    = stringResource(R.string.ext_config_title),
            subtitle = stringResource(R.string.ext_config_subtitle),
            onBack   = { onSave(buildConfig()) },
            actions  = {
                TextButton(onClick = { onSave(buildConfig()) }) {
                    Text(stringResource(R.string.btn_save), color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            },
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 40.dp),
        ) {
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
                    ConfigField(
                        label = stringResource(R.string.ext_config_windows_domain),
                        hint  = "PRIMA",
                        value = domain,
                        onValueChange = { domain = it },
                    )
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
