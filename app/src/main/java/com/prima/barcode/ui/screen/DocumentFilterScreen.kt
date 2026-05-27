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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import com.prima.barcode.data.model.DocumentFilter
import com.prima.barcode.data.model.DocumentType
import com.prima.barcode.data.model.LineStatus
import com.prima.barcode.data.model.bgColor
import com.prima.barcode.data.model.color
import com.prima.barcode.ui.component.PrimaTopBar
import com.prima.barcode.ui.component.verticalScrollbar
import com.prima.barcode.ui.theme.PrimaPalette
import com.prima.barcode.ui.theme.monoLabel
import com.prima.barcode.ui.theme.uppercased
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.ui.res.stringResource
import com.prima.barcode.R

private val filterDateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

private fun Instant.toLocalDate(): LocalDate = atZone(ZoneId.systemDefault()).toLocalDate()

private enum class FilterDateTarget { FROM, TO }

@androidx.compose.runtime.Composable
private fun LineStatus.localizedLabel(): String = when (this) {
    LineStatus.EMPTY   -> stringResource(R.string.doc_status_empty)
    LineStatus.PARTIAL -> stringResource(R.string.doc_status_partial)
    LineStatus.EXACT   -> stringResource(R.string.doc_status_ready)
    LineStatus.OVER    -> stringResource(R.string.doc_status_over_qty)
}

@androidx.compose.runtime.Composable
private fun DocumentType.localizedDisplay(): String = when (this) {
    DocumentType.WAREHOUSE_SHIPMENT -> stringResource(R.string.doctype_warehouse_shipment)
    DocumentType.WAREHOUSE_RECEIPT  -> stringResource(R.string.doctype_warehouse_receipt)
    DocumentType.RETAIL_SHIPMENT    -> stringResource(R.string.doctype_retail_shipment)
    DocumentType.RETAIL_RECEIPT     -> stringResource(R.string.doctype_retail_receipt)
    DocumentType.TRANSPORT_SHEET    -> stringResource(R.string.doctype_transport_sheet)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentFilterScreen(
    initialFilter: DocumentFilter = DocumentFilter(),
    lockedSourceCode: String? = null,
    lockedRcCode: String? = null,
    showDocTypeFilter: Boolean = true,
    onApply: (DocumentFilter) -> Unit,
    onBack: () -> Unit,
) {
    val focusManager = LocalFocusManager.current

    var dateFrom        by remember { mutableStateOf(initialFilter.dateFrom) }
    var dateTo          by remember { mutableStateOf(initialFilter.dateTo) }
    var selectedStates  by remember { mutableStateOf(initialFilter.states) }
    var selectedTypes   by remember { mutableStateOf(initialFilter.types) }
    var destinationCode by remember { mutableStateOf(initialFilter.destinationCode) }
    var sourceCode      by remember { mutableStateOf(lockedSourceCode ?: initialFilter.sourceCode) }
    var rcCode          by remember { mutableStateOf(lockedRcCode ?: initialFilter.rcCode) }
    var dateTarget      by remember { mutableStateOf<FilterDateTarget?>(null) }

    fun buildFilter() = DocumentFilter(
        dateFrom        = dateFrom,
        dateTo          = dateTo,
        states          = selectedStates,
        types           = selectedTypes,
        destinationCode = destinationCode.trim(),
        sourceCode      = sourceCode.trim(),
        rcCode          = rcCode.trim(),
    )

    Column(modifier = Modifier
        .fillMaxSize()
        .background(PrimaPalette.Cream)
        .pointerInput(Unit) { detectTapGestures { focusManager.clearFocus() } }
    ) {
        PrimaTopBar(
            title    = stringResource(R.string.doc_filter_title),
            subtitle = stringResource(R.string.doc_filter_subtitle),
            onBack   = onBack,
        )

        val filterScrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScrollbar(filterScrollState)
                .verticalScroll(filterScrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {

            // ── Status ────────────────────────────────────────────────────
            FilterSection(label = stringResource(R.string.doc_filter_status)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    LineStatus.entries.forEach { ls ->
                        val selected = ls in selectedStates
                        val bg = if (selected) ls.color else ls.bgColor
                        val fg = if (selected) Color.White else ls.color
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(bg)
                                .clickable {
                                    selectedStates = if (selected) selectedStates - ls else selectedStates + ls
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                ls.localizedLabel().uppercased,
                                style = monoLabel.copy(color = fg, fontWeight = FontWeight.Medium),
                            )
                        }
                    }
                }
            }

            // ── Document type ─────────────────────────────────────────────────
            if (showDocTypeFilter) {
                FilterSection(label = stringResource(R.string.doc_filter_type)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White)
                            .border(1.dp, Color(0x18000000), RoundedCornerShape(8.dp)),
                    ) {
                        DocumentType.entries.forEachIndexed { idx, type ->
                            val selected = type in selectedTypes
                            if (idx > 0) HorizontalDivider(color = Color(0x0A000000), thickness = 1.dp)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedTypes = if (selected) selectedTypes - type else selectedTypes + type
                                    }
                                    .padding(start = 8.dp, end = 14.dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = selected,
                                    onCheckedChange = null,
                                    colors = CheckboxDefaults.colors(checkedColor = PrimaPalette.Slate),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    type.localizedDisplay(),
                                    style = monoLabel.copy(
                                        color = if (selected) PrimaPalette.Ink else PrimaPalette.Ink3,
                                        fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                                    ),
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }

            // ── Date downloaded ───────────────────────────────────────────────────
            FilterSection(label = stringResource(R.string.filter_document_date)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterDateChip(
                        label   = stringResource(R.string.filter_date_from),
                        value   = dateFrom?.format(filterDateFmt),
                        onClick = { dateTarget = FilterDateTarget.FROM },
                        onClear = { dateFrom = null },
                        modifier = Modifier.weight(1f),
                    )
                    FilterDateChip(
                        label   = stringResource(R.string.filter_date_to),
                        value   = dateTo?.format(filterDateFmt),
                        onClick = { dateTarget = FilterDateTarget.TO },
                        onClear = { dateTo = null },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // ── Destination code ──────────────────────────────────────────────────
            FilterSection(label = stringResource(R.string.filter_destination_code)) {
                FilterTextField(
                    value         = destinationCode,
                    onValueChange = { destinationCode = it },
                    placeholder   = "e.g. MP1091",
                )
            }

            // ── Source code ───────────────────────────────────────────────────────
            FilterSection(label = stringResource(R.string.filter_source_code)) {
                FilterTextField(
                    value         = sourceCode,
                    onValueChange = { if (lockedSourceCode == null) sourceCode = it },
                    placeholder   = "e.g. CS175",
                    enabled       = lockedSourceCode == null,
                )
            }

            // ── Responsibility center ─────────────────────────────────────────────
            FilterSection(label = stringResource(R.string.filter_responsibility_center)) {
                FilterTextField(
                    value         = rcCode,
                    onValueChange = { if (lockedRcCode == null) rcCode = it },
                    placeholder   = "e.g. CENT_SKL",
                    enabled       = lockedRcCode == null,
                )
            }
        }

        // ── Bottom action bar ─────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f).height(64.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, Color(0x24000000), RoundedCornerShape(14.dp))
                    .background(Color.White)
                    .clickable {
                        dateFrom = null; dateTo = null
                        selectedStates  = emptySet()
                        selectedTypes   = emptySet()
                        destinationCode = ""
                        sourceCode      = lockedSourceCode ?: ""
                        rcCode          = lockedRcCode ?: ""
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.btn_reset).uppercased, style = monoLabel.copy(color = PrimaPalette.Ink, fontWeight = FontWeight.Medium))
            }
            Box(
                modifier = Modifier
                    .weight(1f).height(64.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(PrimaPalette.Coral)
                    .clickable { onApply(buildFilter()) },
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.btn_apply).uppercased, style = monoLabel.copy(color = Color.White, fontWeight = FontWeight.Medium))
            }
        }
    }

    // ── Date picker dialogs ─────────────────────────────────────────────────────────────
    dateTarget?.let { target ->
        val state = rememberDatePickerState(
            initialSelectedDateMillis = when (target) {
                FilterDateTarget.FROM -> dateFrom?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
                FilterDateTarget.TO   -> dateTo?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
            }
        )
        DatePickerDialog(
            onDismissRequest = { dateTarget = null },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        val picked = Instant.ofEpochMilli(millis).toLocalDate()
                        when (target) {
                            FilterDateTarget.FROM -> {
                                dateFrom = picked
                                if (dateTo != null && dateTo!!.isBefore(picked)) dateTo = picked
                            }
                            FilterDateTarget.TO -> {
                                dateTo = picked
                                if (dateFrom != null && dateFrom!!.isAfter(picked)) dateFrom = picked
                            }
                        }
                    }
                    dateTarget = null
                }) { Text(stringResource(R.string.btn_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { dateTarget = null }) { Text(stringResource(R.string.btn_cancel)) }
            },
        ) { DatePicker(state = state) }
    }
}

@Composable
private fun FilterSection(label: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label.uppercased, style = monoLabel.copy(color = PrimaPalette.Ink3, fontWeight = FontWeight.Medium))
        content()
    }
}

@Composable
private fun FilterDateChip(
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
private fun FilterTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    enabled: Boolean = true,
) {
    OutlinedTextField(
        value         = value,
        onValueChange = onValueChange,
        placeholder   = { Text(placeholder, style = monoLabel.copy(color = PrimaPalette.Ink3)) },
        singleLine    = true,
        enabled       = enabled,
        modifier      = Modifier.fillMaxWidth(),
        textStyle     = monoLabel.copy(color = PrimaPalette.Ink),
        colors        = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor  = Color.White,
            focusedContainerColor    = Color.White,
            disabledContainerColor   = Color.White,
            disabledTextColor        = Color(0xFF888888),
            disabledBorderColor      = Color(0x18000000),
        ),
        trailingIcon  = {
            if (value.isNotEmpty() && enabled) {
                IconButton(onClick = { onValueChange("") }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.Clear, null, tint = PrimaPalette.Ink3, modifier = Modifier.size(16.dp))
                }
            }
        },
    )
}
