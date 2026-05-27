package com.prima.barcode.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prima.barcode.data.model.DocState
import com.prima.barcode.data.model.DocumentFilter
import com.prima.barcode.data.model.Document
import com.prima.barcode.data.model.DocumentType
import com.prima.barcode.data.model.LineStatus
import com.prima.barcode.data.model.bgColor
import com.prima.barcode.data.model.color
import com.prima.barcode.data.model.label
import com.prima.barcode.data.model.scanStatus
import com.prima.barcode.ui.component.PrimaTopBar
import com.prima.barcode.ui.component.verticalScrollbar
import com.prima.barcode.ui.component.ScanField
import com.prima.barcode.ui.component.StatusProgressBar
import com.prima.barcode.ui.theme.LocalTextSizeOffset
import com.prima.barcode.ui.theme.PrimaPalette
import com.prima.barcode.ui.theme.monoLabel
import com.prima.barcode.ui.theme.uppercased
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.ui.res.stringResource
import com.prima.barcode.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val dateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    .withZone(ZoneId.systemDefault())
private fun Instant.toLocalDate(): LocalDate =
    atZone(ZoneId.systemDefault()).toLocalDate()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentListScreen(
    docType: DocumentType,
    locationCode: String,
    documents: List<Document>,
    onBack: () -> Unit,
    onDocTap: (Document) -> Unit,
    onDownload: () -> Unit,
    onUpload: (List<Document>) -> Unit,
    onErrorTap: (Document) -> Unit = {},
    onCreateDoc: (docNo: String, locationCode: String) -> Unit = { _, _ -> },
    onDeleteRecordings: (Document) -> Unit = {},
    onClearErrors: () -> Unit = {},
    filter: DocumentFilter = DocumentFilter(),
    onOpenFilter: () -> Unit = {},
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var createDocNo by remember { mutableStateOf<String?>(null) }
    var showDeleteDialog by remember { mutableStateOf<Document?>(null) }
    var showClearErrorsDialog by remember { mutableStateOf(false) }
    val filtered = remember(documents, locationCode, filter) {
        documents.filter { doc ->
            if (doc.sourceCode != locationCode) return@filter false
            val docDate = (doc.documentDate ?: doc.creationDateTime).toLocalDate()
            val dateOk  = (filter.dateFrom == null || !docDate.isBefore(filter.dateFrom)) &&
                          (filter.dateTo   == null || !docDate.isAfter(filter.dateTo))
            val stateOk = filter.states.isEmpty() || doc.scanStatus() in filter.states
            val destOk  = filter.destinationCode.isBlank() || doc.destinationCode.contains(filter.destinationCode, ignoreCase = true)
            val srcOk   = filter.sourceCode.isBlank() || doc.sourceCode.contains(filter.sourceCode, ignoreCase = true)
            val rcOk    = filter.rcCode.isBlank() || doc.rcCode.contains(filter.rcCode, ignoreCase = true)
            dateOk && stateOk && destOk && srcOk && rcOk
        }
    }

    val orders     = remember(filtered) { filtered.filter { it.state == DocState.Downloaded || it.state == DocState.InProgress || it.state is DocState.UploadFailed } }
    val recordings = remember(filtered) {
        filtered.filter { doc ->
            doc.state == DocState.Completed ||
            doc.state is DocState.UploadFailed ||
            (doc.state == DocState.InProgress && (doc.lines.any { it.scanned > 0.0 } || doc.extraLines.isNotEmpty()))
        }
    }
    val errors     = remember(filtered) { filtered.filter { it.state is DocState.UploadFailed } }

    val tabs = listOf(
        stringResource(R.string.doc_list_tab_orders) to orders.size,
        stringResource(R.string.doc_list_tab_recordings) to recordings.size,
        stringResource(R.string.doc_list_tab_errors) to errors.size,
    )

    val visibleDocs = when (selectedTab) {
        0    -> orders
        1    -> recordings
        2    -> errors
        else -> emptyList()
    }

    fun handleDocScan(barcode: String) {
        val found = documents.firstOrNull { it.documentNo == barcode }
        if (found != null) onDocTap(found) else createDocNo = barcode
    }

    Column(modifier = Modifier.fillMaxSize().background(PrimaPalette.Cream)) {
        PrimaTopBar(
            title = docType.display,
            subtitle = "${docType.key} · $locationCode",
            onBack = onBack,
            actions = {
                IconButton(onClick = onOpenFilter) {
                    Icon(
                        Icons.Outlined.Tune,
                        contentDescription = "Filter",
                        tint = if (filter.isActive) PrimaPalette.Coral else Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
            },
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(PrimaPalette.SlateAlt)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            ScanField(
                placeholder = stringResource(R.string.doc_list_scan_placeholder),
                onScan = { handleDocScan(it) },
                onCameraTap = {},
                dark = true,
            )
        }

        PrimaryTabRow(
            selectedTabIndex = selectedTab,
            containerColor = PrimaPalette.SlateAlt,
            contentColor = Color.White,
            indicator = {
                Box(
                    Modifier
                        .tabIndicatorOffset(selectedTab)
                        .height(2.dp)
                        .background(PrimaPalette.Coral)
                )
            },
        ) {
            tabs.forEachIndexed { index, (label, count) ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(label.uppercased, style = monoLabel.copy(color = Color.White))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0x22FFFFFF))
                                    .padding(horizontal = 5.dp, vertical = 1.dp),
                            ) {
                                Text(count.toString(), style = monoLabel.copy(color = Color.White))
                            }
                        }
                    },
                )
            }
        }

        val listState = rememberLazyListState()
        if (visibleDocs.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.doc_list_empty), style = monoLabel.copy(color = PrimaPalette.Ink3))
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScrollbar(listState),
                state = listState,
            ) {
                items(visibleDocs, key = { it.documentNo }) { doc ->
                    DocRow(
                        doc = doc,
                        showErrorDetails = selectedTab == 2,
                        onLongHold = if (selectedTab == 1) { { showDeleteDialog = doc } } else null,
                        onClick = {
                            if (selectedTab == 2 && doc.state is DocState.UploadFailed) onErrorTap(doc)
                            else onDocTap(doc)
                        },
                    )
                    HorizontalDivider(color = Color(0x0F000000), thickness = 1.dp)
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when (selectedTab) {
                1 -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth().height(64.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(PrimaPalette.Coral)
                            .clickable { onUpload(visibleDocs) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Outlined.CloudUpload, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Text(stringResource(R.string.btn_upload).uppercased, style = monoLabel.copy(color = Color.White, fontWeight = FontWeight.Medium))
                        }
                    }
                }
                2 -> {
                    Box(
                        modifier = Modifier
                            .weight(1f).height(64.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .border(1.dp, Color(0x24000000), RoundedCornerShape(14.dp))
                            .background(Color.White)
                            .clickable { showClearErrorsDialog = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(stringResource(R.string.overview_clear_errors).uppercased, style = monoLabel.copy(color = PrimaPalette.Ink, fontWeight = FontWeight.Medium))
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f).height(64.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(PrimaPalette.Coral)
                            .clickable { onUpload(visibleDocs) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Outlined.CloudUpload, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Text(stringResource(R.string.btn_upload).uppercased, style = monoLabel.copy(color = Color.White, fontWeight = FontWeight.Medium))
                        }
                    }
                }
                else -> {
                    Box(
                        modifier = Modifier
                            .weight(1f).height(64.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .border(1.dp, Color(0x24000000), RoundedCornerShape(14.dp))
                            .background(Color.White)
                            .clickable(onClick = onDownload),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Outlined.CloudDownload, null, tint = PrimaPalette.Ink, modifier = Modifier.size(18.dp))
                            Text(stringResource(R.string.btn_download).uppercased, style = monoLabel.copy(color = PrimaPalette.Ink, fontWeight = FontWeight.Medium))
                        }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f).height(64.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(PrimaPalette.Coral)
                            .clickable { onUpload(visibleDocs) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Outlined.CloudUpload, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Text(stringResource(R.string.btn_upload).uppercased, style = monoLabel.copy(color = Color.White, fontWeight = FontWeight.Medium))
                        }
                    }
                }
            }
        }
    }

    createDocNo?.let { no ->
        AlertDialog(
            onDismissRequest = { createDocNo = null },
            title = { Text(stringResource(R.string.doc_list_create_title)) },
            text = { Text(stringResource(R.string.doc_list_create_text, no)) },
            confirmButton = {
                Button(onClick = {
                    onCreateDoc(no, locationCode)
                    createDocNo = null
                }) { Text(stringResource(R.string.btn_create)) }
            },
            dismissButton = {
                OutlinedButton(onClick = { createDocNo = null }) { Text(stringResource(R.string.btn_cancel)) }
            },
        )
    }

    showDeleteDialog?.let { doc ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text(stringResource(R.string.doc_delete_recordings_title), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.doc_delete_recordings_text, doc.documentNo)) },
            confirmButton = {
                Button(
                    onClick = { showDeleteDialog = null; onDeleteRecordings(doc) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFCE3A3A)),
                ) { Text(stringResource(R.string.btn_clear), fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteDialog = null }) { Text(stringResource(R.string.btn_cancel)) }
            },
        )
    }

    if (showClearErrorsDialog) {
        AlertDialog(
            onDismissRequest = { showClearErrorsDialog = false },
            title = { Text(stringResource(R.string.overview_clear_errors_title), fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    if (errors.size == 1) stringResource(R.string.overview_clear_errors_text_single, errors.size)
                    else stringResource(R.string.overview_clear_errors_text_plural, errors.size)
                )
            },
            confirmButton = {
                Button(
                    onClick = { showClearErrorsDialog = false; onClearErrors() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFCE3A3A)),
                ) { Text(stringResource(R.string.btn_clear), fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                OutlinedButton(onClick = { showClearErrorsDialog = false }) { Text(stringResource(R.string.btn_cancel)) }
            },
        )
    }
}


@androidx.compose.runtime.Composable
private fun com.prima.barcode.data.model.LineStatus.localizedLabel(): String = when (this) {
    com.prima.barcode.data.model.LineStatus.EMPTY   -> stringResource(R.string.doc_status_empty)
    com.prima.barcode.data.model.LineStatus.PARTIAL -> stringResource(R.string.doc_status_partial)
    com.prima.barcode.data.model.LineStatus.EXACT   -> stringResource(R.string.doc_status_ready)
    com.prima.barcode.data.model.LineStatus.OVER    -> stringResource(R.string.doc_status_over_qty)
}


@Composable
private fun DocRow(
    doc: Document,
    showErrorDetails: Boolean = false,
    onLongHold: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val sizeOffset = LocalTextSizeOffset.current
    val status = doc.scanStatus()
    val statusColor = status.color
    val coroutineScope = rememberCoroutineScope()
    var holdProgress by remember { mutableFloatStateOf(0f) }

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .background(statusColor.copy(alpha = 0.10f))
                .let { m ->
                    if (onLongHold == null) m.clickable(onClick = onClick)
                    else m.pointerInput(onClick, onLongHold) {
                        detectTapGestures(
                            onTap = { onClick() },
                            onPress = {
                                val job = coroutineScope.launch {
                                    var elapsed = 0L
                                    while (elapsed < 5000L) {
                                        delay(16L)
                                        elapsed += 16L
                                        holdProgress = elapsed / 5000f
                                    }
                                    holdProgress = 0f
                                    onLongHold()
                                }
                                tryAwaitRelease()
                                job.cancel()
                                holdProgress = 0f
                            },
                        )
                    }
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.width(6.dp).fillMaxHeight().background(statusColor))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            doc.documentNo,
                            style = monoLabel.copy(
                                color = PrimaPalette.Ink,
                                fontWeight = FontWeight.Bold,
                                fontSize = (14 + sizeOffset).sp,
                            ),
                        )
                        Spacer(Modifier.height(5.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            DocScanStatusChip(status)
                            if (doc.state is DocState.UploadFailed) DocStateErrorChip()
                        }
                    }
                    Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(top = 2.dp)) {
                        Text(
                            doc.sourceCode,
                            style = monoLabel.copy(color = PrimaPalette.Ink3, fontSize = (12 + sizeOffset).sp),
                        )
                        Text(
                            dateFmt.format(doc.documentDate ?: doc.creationDateTime),
                            style = monoLabel.copy(color = PrimaPalette.Ink3, fontSize = (12 + sizeOffset).sp),
                        )
                    }
                }
                if (doc.state == DocState.InProgress || doc.state == DocState.Completed) {
                    Spacer(Modifier.height(10.dp))
                    StatusProgressBar(segments = doc.lines.map { it.status }, height = 4.dp, gap = 2.dp)
                }
                if (doc.state is DocState.UploadFailed && showErrorDetails) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        (doc.state as DocState.UploadFailed).reason,
                        style = monoLabel.copy(color = Color(0xFFCE3A3A), fontSize = (12 + sizeOffset).sp),
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (holdProgress > 0f) {
            Box(
                modifier = Modifier.matchParentSize().background(Color(0x28000000)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    progress = { holdProgress },
                    color = PrimaPalette.Coral,
                    trackColor = Color(0x40FFFFFF),
                    modifier = Modifier.size(40.dp),
                )
            }
        }
    }
}

@Composable
private fun DocStateErrorChip() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFFCE3A3A))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(stringResource(R.string.doc_chip_error).uppercased, style = monoLabel.copy(color = Color.White, fontWeight = FontWeight.Medium))
    }
}

@Composable
private fun DocScanStatusChip(status: LineStatus) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(status.color)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(status.localizedLabel().uppercased, style = monoLabel.copy(color = Color.White, fontWeight = FontWeight.Medium))
    }
}
