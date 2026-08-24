package com.prima.barcode.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.prima.barcode.data.model.DocumentType
import com.prima.barcode.data.model.DownloadFilter
import com.prima.barcode.data.model.Location
import com.prima.barcode.data.model.ResponsibilityCenter
import com.prima.barcode.ui.component.PrimaTopBar
import com.prima.barcode.ui.component.verticalScrollbar
import com.prima.barcode.ui.theme.PrimaPalette
import com.prima.barcode.ui.theme.monoLabel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.ui.res.stringResource
import com.prima.barcode.R

private val dlDateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

private fun Instant.toLocalDateDl(): LocalDate = atZone(ZoneId.systemDefault()).toLocalDate()

private enum class DlDateTarget { FROM, TO }

@Composable
private fun DocumentType.localizedDisplay(): String = when (this) {
    DocumentType.WAREHOUSE_SHIPMENT -> stringResource(R.string.doctype_warehouse_shipment)
    DocumentType.WAREHOUSE_RECEIPT  -> stringResource(R.string.doctype_warehouse_receipt)
    DocumentType.RETAIL_SHIPMENT    -> stringResource(R.string.doctype_retail_shipment)
    DocumentType.RETAIL_RECEIPT     -> stringResource(R.string.doctype_retail_receipt)
    DocumentType.TRANSPORT_SHEET    -> stringResource(R.string.doctype_transport_sheet)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadFilterScreen(
    hasCredentials: Boolean = false,
    docType: DocumentType? = null,
    fixedSourceCode: String? = null,
    fixedRcCode: String? = null,
    locations: List<Location> = emptyList(),
    rcs: List<ResponsibilityCenter> = emptyList(),
    onConfirm: (filter: DownloadFilter, username: String?, password: String?) -> Unit,
    onTestConnection: (username: String, password: String, onResult: (success: Boolean, error: String?) -> Unit) -> Unit = { _, _, cb -> cb(true, null) },
    onCancel: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var dateFrom        by remember { mutableStateOf<LocalDate?>(null) }
    var dateTo          by remember { mutableStateOf<LocalDate?>(null) }
    var destinationCode by remember { mutableStateOf("") }
    var sourceCode      by remember { mutableStateOf(fixedSourceCode ?: "") }
    var rcCode          by remember { mutableStateOf(fixedRcCode ?: "") }
    var dateTarget      by remember { mutableStateOf<DlDateTarget?>(null) }
    var showLogin       by remember { mutableStateOf(false) }
    var showSourceSheet by remember { mutableStateOf(false) }
    var showRcPickSheet by remember { mutableStateOf(false) }
    val selectedLocation = remember(sourceCode, locations) { locations.find { it.code == sourceCode } }
    val selectedRc       = remember(rcCode, rcs) { rcs.find { it.code == rcCode } }

    LaunchedEffect(Unit) {
        if (!hasCredentials) showLogin = true
    }

    fun currentFilter() = DownloadFilter(
        dateFrom        = dateFrom,
        dateTo          = dateTo,
        destinationCode = destinationCode.trim(),
        sourceCode      = sourceCode.trim(),
        rcCode          = rcCode.trim(),
    )

    fun onOkClick() {
        if (hasCredentials) onConfirm(currentFilter(), null, null)
        else showLogin = true
    }

    Column(modifier = Modifier
        .fillMaxSize()
        .background(PrimaPalette.Cream)
        .pointerInput(Unit) { detectTapGestures { focusManager.clearFocus(); keyboardController?.hide() } }
    ) {
        PrimaTopBar(
            title    = stringResource(R.string.download_title),
            subtitle = docType?.localizedDisplay() ?: stringResource(R.string.download_subtitle),
            onBack   = onCancel,
        )

        val dlScrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScrollbar(dlScrollState)
                .verticalScroll(dlScrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            DlFilterSection(label = stringResource(R.string.filter_document_date)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DlDateChip(
                        label    = stringResource(R.string.filter_date_from),
                        value    = dateFrom?.format(dlDateFmt),
                        onClick  = { dateTarget = DlDateTarget.FROM },
                        onClear  = { dateFrom = null },
                        modifier = Modifier.weight(1f),
                    )
                    DlDateChip(
                        label    = stringResource(R.string.filter_date_to),
                        value    = dateTo?.format(dlDateFmt),
                        onClick  = { dateTarget = DlDateTarget.TO },
                        onClear  = { dateTo = null },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            DlFilterSection(label = stringResource(R.string.filter_destination_code)) {
                DlTextField(value = destinationCode, onValueChange = { destinationCode = it }, placeholder = "e.g. MP1091")
            }

            if (fixedRcCode == null) DlFilterSection(label = stringResource(R.string.filter_source_code)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White)
                        .then(if (fixedSourceCode != null) Modifier.alpha(0.5f) else Modifier),
                ) {
                    LrcSelectionRow(
                        avatar = {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (selectedLocation != null) PrimaPalette.Teal else PrimaPalette.CreamAlt),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Outlined.LocationOn,
                                    contentDescription = null,
                                    tint = if (selectedLocation != null) Color.White else PrimaPalette.Ink3,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        },
                        title = selectedLocation?.code ?: stringResource(R.string.lrc_location_placeholder),
                        subtitle = selectedLocation?.name,
                        hasValue = sourceCode.isNotEmpty(),
                        showChevron = fixedSourceCode == null,
                        onClick = { if (fixedSourceCode == null) showSourceSheet = true },
                    )
                }
            }

            if (fixedSourceCode == null) DlFilterSection(label = stringResource(R.string.filter_responsibility_center)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White)
                        .then(if (fixedRcCode != null) Modifier.alpha(0.5f) else Modifier),
                ) {
                    LrcSelectionRow(
                        avatar = {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (selectedRc != null) PrimaPalette.Slate else PrimaPalette.CreamAlt),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = selectedRc?.let { it.short ?: it.code.take(2) } ?: "—",
                                    style = monoLabel.copy(
                                        color = if (selectedRc != null) Color.White else PrimaPalette.Ink3,
                                        fontWeight = FontWeight.Medium,
                                    ),
                                )
                            }
                        },
                        title = selectedRc?.name ?: stringResource(R.string.lrc_rc_placeholder),
                        subtitle = selectedRc?.code,
                        hasValue = rcCode.isNotEmpty(),
                        showChevron = fixedRcCode == null,
                        onClick = { if (fixedRcCode == null) showRcPickSheet = true },
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                onClick = {
                    dateFrom = null; dateTo = null
                    destinationCode = ""
                    sourceCode = fixedSourceCode ?: ""
                    rcCode = fixedRcCode ?: ""
                },
                modifier = Modifier.weight(1f).height(52.dp),
            ) { Text(stringResource(R.string.btn_reset), style = monoLabel.copy(fontWeight = FontWeight.Medium)) }
            Button(
                onClick = ::onOkClick,
                modifier = Modifier.weight(1f).height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaPalette.Coral),
            ) { Text(stringResource(R.string.btn_ok), style = monoLabel.copy(color = Color.White, fontWeight = FontWeight.Medium)) }
        }
    }

    dateTarget?.let { target ->
        val state = rememberDatePickerState(
            initialSelectedDateMillis = when (target) {
                DlDateTarget.FROM -> dateFrom?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
                DlDateTarget.TO   -> dateTo?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
            }
        )
        DatePickerDialog(
            onDismissRequest = { dateTarget = null },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        val picked = Instant.ofEpochMilli(millis).toLocalDateDl()
                        when (target) {
                            DlDateTarget.FROM -> {
                                dateFrom = picked
                                if (dateTo != null && dateTo!!.isBefore(picked)) dateTo = picked
                            }
                            DlDateTarget.TO -> {
                                dateTo = picked
                                if (dateFrom != null && dateFrom!!.isAfter(picked)) dateFrom = picked
                            }
                        }
                    }
                    dateTarget = null
                }) { Text(stringResource(R.string.btn_ok)) }
            },
            dismissButton = { TextButton(onClick = { dateTarget = null }) { Text(stringResource(R.string.btn_cancel)) } },
        ) { DatePicker(state = state) }
    }

    if (showSourceSheet) {
        LocationPickerSheet(
            locations            = locations,
            selectedLocationCode = sourceCode,
            onSelect             = { loc -> sourceCode = loc.code; showSourceSheet = false },
            onDismiss            = { showSourceSheet = false },
        )
    }

    if (showRcPickSheet) {
        RcPickerSheet(
            availableRcs   = rcs,
            selectedRcCode = rcCode,
            onSelect       = { rc -> rcCode = rc.code; showRcPickSheet = false },
            onClear        = { rcCode = ""; showRcPickSheet = false },
            onDismiss      = { showRcPickSheet = false },
        )
    }

    if (showLogin) {
        LoginSheet(
            onTestConnection = onTestConnection,
            onSubmit = { username, password ->
                showLogin = false
                onConfirm(currentFilter(), username, password)
            },
            onDismiss = { showLogin = false },
        )
    }
}

@Composable
private fun DlFilterSection(label: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = monoLabel.copy(color = PrimaPalette.Ink3, fontWeight = FontWeight.Medium))
        content()
    }
}

@Composable
private fun DlDateChip(
    label: String,
    value: String?,
    onClick: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .border(1.dp, Color(0x28000000), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.Outlined.CalendarMonth, null, tint = PrimaPalette.Ink3, modifier = Modifier.size(15.dp))
        Text(
            text = value ?: label,
            style = monoLabel.copy(color = if (value != null) PrimaPalette.Ink else PrimaPalette.Ink3),
            modifier = Modifier.weight(1f),
        )
        if (value != null) {
            Icon(
                Icons.Outlined.Clear,
                contentDescription = "Clear",
                tint = PrimaPalette.Ink3,
                modifier = Modifier.size(14.dp).clickable(onClick = onClear),
            )
        }
    }
}

@Composable
private fun DlTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
) {
    OutlinedTextField(
        value         = value,
        onValueChange = onValueChange,
        placeholder   = { Text(placeholder, style = monoLabel.copy(color = PrimaPalette.Ink3)) },
        singleLine    = true,
        modifier      = Modifier.fillMaxWidth(),
        textStyle     = monoLabel.copy(color = PrimaPalette.Ink),
        colors        = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = Color.White,
            focusedContainerColor   = Color.White,
        ),
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.Clear, null, tint = PrimaPalette.Ink3, modifier = Modifier.size(16.dp))
                }
            }
        },
    )
}

