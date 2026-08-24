package com.prima.barcode.ui.screen

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.prima.barcode.data.haptic.HapticEngine
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import com.prima.barcode.data.model.Document
import com.prima.barcode.data.model.Line
import com.prima.barcode.data.model.LineStatus
import com.prima.barcode.data.model.TapeEntry
import com.prima.barcode.data.model.color
import com.prima.barcode.data.model.formatQty
import com.prima.barcode.data.model.scanStatus
import com.prima.barcode.ui.component.PrimaTopBar
import com.prima.barcode.ui.component.ScanBar
import com.prima.barcode.ui.component.ScanTape
import com.prima.barcode.ui.component.StatusProgressBar
import com.prima.barcode.ui.theme.LocalTextSizeOffset
import com.prima.barcode.ui.theme.PrimaPalette
import com.prima.barcode.ui.theme.monoCounter
import com.prima.barcode.ui.theme.monoLabel
import kotlinx.coroutines.delay
import java.time.Instant
import java.util.UUID
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.prima.barcode.data.barcode.DataWedgeManager
import com.prima.barcode.ui.component.CameraPreview
import androidx.compose.ui.res.stringResource
import com.prima.barcode.R

private enum class RecordingView { OVERVIEW, ACTIVE_LINE, KEYPAD }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingScreen(
    doc: Document,
    docTypeCode: String = "",
    onBack: () -> Unit,
    onScan: (barcode: String, multiplier: Double) -> Unit,
    onLineUpdate: (lineNo: Int, newScanned: Double) -> Unit,
    onUpload: () -> Unit = {},
    lastScannedLines: Int = 5,
    autoScan: Boolean = false,
    hapticEnabled: Boolean = true,
    debounceTime: Int = 500,
    warnOnOver: Boolean = true,
) {
    var view by remember { mutableStateOf(RecordingView.OVERVIEW) }
    var activeLineNo by remember { mutableStateOf<Int?>(null) }
    val activeLine = activeLineNo?.let { no -> doc.lines.find { it.lineNo == no } }
    var typedQty by remember { mutableStateOf("") }
    var tape by remember { mutableStateOf(emptyList<TapeEntry>()) }
    var scanErrorFlash by remember { mutableStateOf(false) }
    var localScanned by remember(activeLineNo) { mutableStateOf<Double?>(null) }
    var overScanWarning by remember { mutableStateOf<OverScanInfo?>(null) }
    var barcodeNotFoundError by remember { mutableStateOf<String?>(null) }
    var uomMismatchWarning by remember { mutableStateOf<UomMismatchInfo?>(null) }
    val sizeOffset = LocalTextSizeOffset.current
    val showUpload = doc.lines.any { it.scanned > 0.0 }
    val context = LocalContext.current
    val hapticEngine = remember { HapticEngine(context) }
    var cameraOpen by remember { mutableStateOf(false) }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) cameraOpen = true }

    LaunchedEffect(scanErrorFlash) {
        if (scanErrorFlash) { delay(600); scanErrorFlash = false }
    }

    fun handleScan(rawInput: String) {
        // The scanned/matched barcode is always the full raw input, e.g. "NTR1234|M|5.6"
        // in its entirety — that's the literal value on the label and what line.barcodeNo
        // matches against. UOM and quantity are additionally parsed out of it for
        // recording purposes when there are exactly two "|" separators and the last
        // part is a valid number; they never change what's searched for or stored as
        // the barcode.
        val barcode = rawInput
        val pipeParts = rawInput.split("|")
        val parsedQty = if (pipeParts.size == 3) pipeParts[2].toDoubleOrNull() else null
        val parsedUom = if (parsedQty != null) pipeParts[1] else null

        val matchedLine = doc.lines.find { it.barcodeNo == barcode }
        val wasExact = matchedLine?.status == LineStatus.EXACT
        if (matchedLine == null) {
            scanErrorFlash = true
            if (hapticEnabled) hapticEngine.error()
            barcodeNotFoundError = barcode
        } else {
            val qty = parsedQty ?: matchedLine.scanningQty
            onScan(barcode, qty)
            val newScanned = matchedLine.scanned + qty
            val newStatus = LineStatus.of(newScanned, matchedLine.expected)
            if (!wasExact && newStatus == LineStatus.EXACT && hapticEnabled) hapticEngine.confirm()
            tape = listOf(TapeEntry(UUID.randomUUID().toString(), barcode, matchedLine.item.name, qty, Instant.now(), newStatus)) + tape
            if (warnOnOver && newStatus == LineStatus.OVER) {
                overScanWarning = OverScanInfo(matchedLine.item.no, matchedLine.item.name, barcode, matchedLine.expected, newScanned)
            }
            if (parsedUom != null && parsedUom != matchedLine.unitOfMeasureCode) {
                uomMismatchWarning = UomMismatchInfo(matchedLine.item.no, matchedLine.item.name, barcode, matchedLine.unitOfMeasureCode, parsedUom)
            }
        }
    }

    DisposableEffect(Unit) {
        val receiver = DataWedgeManager.createReceiver { barcode -> handleScan(barcode) }
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, DataWedgeManager.intentFilter(), Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, DataWedgeManager.intentFilter())
        }
        onDispose { context.unregisterReceiver(receiver) }
    }

    val scanBarBg by animateColorAsState(
        targetValue = if (scanErrorFlash) Color(0xFF7A1A1A) else PrimaPalette.Slate,
        animationSpec = tween(durationMillis = 300),
        label = "scanBarBg",
    )

    fun handleBack() {
        when (view) {
            RecordingView.OVERVIEW -> onBack()
            RecordingView.ACTIVE_LINE -> { view = RecordingView.OVERVIEW; activeLineNo = null }
            RecordingView.KEYPAD -> view = RecordingView.ACTIVE_LINE
        }
    }

    BackHandler { handleBack() }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize().background(PrimaPalette.Cream)) {
        PrimaTopBar(
            title = when (view) {
                RecordingView.OVERVIEW -> stringResource(R.string.recording_title)
                RecordingView.ACTIVE_LINE, RecordingView.KEYPAD -> activeLine?.item?.no ?: doc.documentNo
            },
            subtitle = when (view) {
                RecordingView.OVERVIEW -> buildString {
                    append("${doc.documentNo} · ${doc.linesExact}/${doc.linesTotal} lines")
                    if (docTypeCode.isNotBlank()) append(" · $docTypeCode")
                }
                RecordingView.ACTIVE_LINE -> null
                RecordingView.KEYPAD -> null
            },
            onBack = { handleBack() },
            actions = {
                if (showUpload && view == RecordingView.OVERVIEW) {
                    Box(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(PrimaPalette.Coral)
                            .clickable(onClick = onUpload),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.ArrowUpward,
                            contentDescription = stringResource(R.string.btn_upload),
                            tint = Color.White,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
                if (view == RecordingView.ACTIVE_LINE) {
                    activeLine?.let { line ->
                        StatusChip(status = line.status, sizeOffset = sizeOffset)
                        Spacer(Modifier.width(8.dp))
                    }
                }
                if (view == RecordingView.KEYPAD) {
                    activeLine?.let { line ->
                        val parsedTypedQty = typedQty.toDoubleOrNull()
                        val previewStatus = if (parsedTypedQty != null && typedQty.isNotEmpty()) LineStatus.of(parsedTypedQty, line.expected) else line.status
                        val previewLabel = when (previewStatus) {
                            LineStatus.EMPTY   -> if (typedQty.isEmpty()) stringResource(R.string.keypad_is_empty) else stringResource(R.string.keypad_becomes_empty)
                            LineStatus.PARTIAL -> if (typedQty.isEmpty()) stringResource(R.string.keypad_is_partial) else stringResource(R.string.keypad_becomes_partial)
                            LineStatus.EXACT   -> if (typedQty.isEmpty()) stringResource(R.string.keypad_is_exact) else stringResource(R.string.keypad_becomes_exact)
                            LineStatus.OVER    -> if (typedQty.isEmpty()) stringResource(R.string.keypad_is_over) else stringResource(R.string.keypad_becomes_over)
                        }
                        StatusChip(status = previewStatus, sizeOffset = sizeOffset, label = previewLabel.uppercase())
                        Spacer(Modifier.width(8.dp))
                    }
                }
            },
        )

        if (view == RecordingView.OVERVIEW) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PrimaPalette.SlateAlt)
                    .padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "${doc.sourceCode} · ${doc.destinationCode}",
                        style = monoLabel.copy(color = Color(0xC5FFFFFF), fontSize = (11 + sizeOffset).sp),
                    )
                    val allDone = doc.linesExact == doc.linesTotal && doc.linesTotal > 0
                    Text(
                        "${doc.scannedQty.formatQty()}/${doc.expectedQty.formatQty()}",
                        style = monoLabel.copy(
                            color = if (allDone) LineStatus.EXACT.color else Color.White,
                            fontWeight = FontWeight.Medium,
                            fontSize = (11 + sizeOffset).sp,
                        ),
                    )
                }
                Spacer(Modifier.height(8.dp))
                StatusProgressBar(segments = doc.lines.map { it.status })
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (view) {
                RecordingView.OVERVIEW -> OverviewContent(
                    doc = doc,
                    onLineTap = { line -> activeLineNo = line.lineNo; localScanned = null; typedQty = ""; view = RecordingView.ACTIVE_LINE },
                )
                RecordingView.ACTIVE_LINE -> activeLine?.let { line ->
                    val displayLine = localScanned?.let { line.copy(scanned = it) } ?: line
                    ItemQtyDetails(
                        line = displayLine,
                        onIncrement = { if (hapticEnabled) hapticEngine.bump(); localScanned = ((localScanned ?: line.scanned) + 1.0).coerceAtLeast(0.0) },
                        onDecrement = { if (hapticEnabled) hapticEngine.bump(); localScanned = ((localScanned ?: line.scanned) - 1.0).coerceAtLeast(0.0) },
                        onTypeQuantity = { typedQty = ""; view = RecordingView.KEYPAD },
                        onApply = { if (hapticEnabled) hapticEngine.confirm(); onLineUpdate(line.lineNo, localScanned ?: line.scanned); view = RecordingView.OVERVIEW; activeLineNo = null },
                    )
                }
                RecordingView.KEYPAD -> activeLine?.let { line ->
                    val displayLine = localScanned?.let { line.copy(scanned = it) } ?: line
                    ItemQtyExtraDetails(
                        line = displayLine,
                        typed = typedQty,
                        onKey = { k ->
                            if (hapticEnabled) hapticEngine.tick()
                            when (k) {
                                "C" -> typedQty = ""
                                "X" -> typedQty = typedQty.dropLast(1)
                                "." -> if (!typedQty.contains('.')) typedQty += if (typedQty.isEmpty()) "0." else "."
                                else -> {
                                    val next = typedQty + k
                                    val cleaned = if (next.startsWith("0") && next.length > 1 && next[1] != '.') next.trimStart('0').ifEmpty { "0" } else next
                                    if (cleaned.substringAfter('.', "").length <= 5) typedQty = cleaned
                                }
                            }
                        },
                        onConfirm = {
                            if (hapticEnabled) hapticEngine.confirm()
                            val qty = typedQty.toDoubleOrNull()?.coerceAtLeast(0.0) ?: line.scanned
                            onLineUpdate(line.lineNo, qty)
                            if (warnOnOver && qty > line.expected) {
                                overScanWarning = OverScanInfo(line.item.no, line.item.name, line.barcodeNo, line.expected, qty)
                            }
                            localScanned = null
                            activeLineNo = null
                            view = RecordingView.OVERVIEW
                        },
                        onConfirmRequired = {
                            if (hapticEnabled) hapticEngine.confirm()
                            onLineUpdate(line.lineNo, line.expected)
                            localScanned = null
                            activeLineNo = null
                            view = RecordingView.OVERVIEW
                        },
                    )
                }
            }
        }

        if (view == RecordingView.OVERVIEW) {
            ScanTape(tape = tape, maxLines = lastScannedLines)
            ScanBar(
                onScan = { handleScan(it) },
                onCameraTap = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                            == PackageManager.PERMISSION_GRANTED) cameraOpen = true
                    else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                },
                containerColor = scanBarBg,
            )
        }
    }
    if (cameraOpen) {
        CameraPreview(
            continuous = autoScan,
            onBarcode = { barcode -> handleScan(barcode) },
            onClose = { cameraOpen = false },
            debounceMs = debounceTime,
        )
    }
    } // end Box


    overScanWarning?.let { info ->
        AlertDialog(
            onDismissRequest = { overScanWarning = null },
            title = { Text(stringResource(R.string.recording_overscan_title), fontWeight = FontWeight.Bold, color = LineStatus.OVER.color) },
            text = {
                Text(
                    stringResource(
                        R.string.recording_overscan_body,
                        info.itemNo, info.itemName, info.barcode, info.expected.formatQty(), info.scanned.formatQty(),
                    )
                )
            },
            confirmButton = {
                Button(onClick = { overScanWarning = null }) { Text(stringResource(R.string.btn_ok), fontWeight = FontWeight.SemiBold) }
            },
        )
    }

    barcodeNotFoundError?.let { barcode ->
        AlertDialog(
            onDismissRequest = { barcodeNotFoundError = null },
            title = { Text(stringResource(R.string.recording_barcode_not_found_title), fontWeight = FontWeight.Bold, color = Color(0xFFCE3A3A)) },
            text = { Text(stringResource(R.string.recording_barcode_not_found_text, barcode)) },
            confirmButton = {
                Button(onClick = { barcodeNotFoundError = null }) { Text(stringResource(R.string.btn_ok), fontWeight = FontWeight.SemiBold) }
            },
        )
    }

    uomMismatchWarning?.let { info ->
        AlertDialog(
            onDismissRequest = { uomMismatchWarning = null },
            title = { Text(stringResource(R.string.recording_uom_mismatch_title), fontWeight = FontWeight.Bold, color = Color(0xFFC7943A)) },
            text = {
                Text(
                    stringResource(
                        R.string.recording_uom_mismatch_body,
                        info.itemNo, info.itemName, info.barcode, info.expectedUom, info.scannedUom,
                    )
                )
            },
            confirmButton = {
                Button(onClick = { uomMismatchWarning = null }) { Text(stringResource(R.string.btn_ok), fontWeight = FontWeight.SemiBold) }
            },
        )
    }

}

private data class OverScanInfo(
    val itemNo: String,
    val itemName: String,
    val barcode: String,
    val expected: Double,
    val scanned: Double,
)

private data class UomMismatchInfo(
    val itemNo: String,
    val itemName: String,
    val barcode: String,
    val expectedUom: String,
    val scannedUom: String,
)

@Composable
private fun OverviewContent(
    doc: Document,
    onLineTap: (Line) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(doc.lines, key = { it.lineNo }) { line ->
            BigNumberLineRow(line = line, onClick = { onLineTap(line) })
            HorizontalDivider(color = Color(0x0F000000), thickness = 1.dp)
        }
    }
}

@Composable
private fun BigNumberLineRow(line: Line, onClick: () -> Unit) {
    val sizeOffset = LocalTextSizeOffset.current
    val statusColor = line.status.color
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(statusColor.copy(alpha = 0.10f))
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.width(6.dp).fillMaxHeight().background(statusColor))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 16.dp),
        ) {
            val itemText = buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Normal)) {
                    append(line.item.no)
                }
                append(" - ")
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(line.item.name)
                }
            }
            Text(
                itemText,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = (15 + sizeOffset).sp,
                    color = if (line.status == LineStatus.EMPTY) PrimaPalette.Ink2 else PrimaPalette.Ink,
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.End),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    line.scanned.formatQty(),
                    style = monoCounter.copy(color = statusColor, fontSize = (25 + sizeOffset).sp, fontWeight = FontWeight.Medium),
                )
                Text(
                    "/",
                    style = monoCounter.copy(color = statusColor.copy(alpha = 0.4f), fontSize = (25 + sizeOffset).sp),
                )
                Text(
                    line.expected.formatQty(),
                    style = monoCounter.copy(color = statusColor.copy(alpha = 0.7f), fontSize = (25 + sizeOffset).sp),
                )
            }
        }
    }
}

@Composable
private fun StatusChip(status: LineStatus, sizeOffset: Int, label: String? = null) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(status.color)
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(modifier = Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(Color.White))
        Text(
            label ?: when (status) {
                LineStatus.EMPTY   -> stringResource(R.string.status_chip_empty)
                LineStatus.PARTIAL -> stringResource(R.string.status_chip_partial)
                LineStatus.EXACT   -> stringResource(R.string.status_chip_exact)
                LineStatus.OVER    -> stringResource(R.string.status_chip_over)
            },
            style = monoLabel.copy(color = Color.White, fontSize = (11 + sizeOffset).sp, letterSpacing = 1.sp, fontWeight = FontWeight.Medium),
        )
    }
}


@Composable
private fun ItemQtyDetails(
    line: Line,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onTypeQuantity: () -> Unit,
    onApply: () -> Unit,
) {
    val sizeOffset = LocalTextSizeOffset.current
    val statusColor = line.status.color
    val bgColor by animateColorAsState(
        targetValue = statusColor.copy(alpha = 0.08f),
        animationSpec = tween(durationMillis = 300),
        label = "activeLineBg",
    )
    val initialScanned = remember(line.lineNo) { line.scanned }
    val hasChanged = line.scanned != initialScanned

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PrimaPalette.Cream)
            .background(bgColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp, vertical = 18.dp),
        ) {
            val metaStyle = monoLabel.copy(color = PrimaPalette.Ink3, fontSize = (13 + sizeOffset).sp)
            Text(stringResource(R.string.recording_barcode_prefix) + line.barcodeNo, style = metaStyle)
            Spacer(Modifier.height(4.dp))

            Text(
                line.item.name,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium,
                    color = PrimaPalette.Ink,
                    fontSize = (16 + sizeOffset).sp,
                ),
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clickable(onClick = onTypeQuantity),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        line.scanned.formatQty(),
                        style = monoCounter.copy(color = statusColor, fontSize = (40 + sizeOffset).sp, fontWeight = FontWeight.Medium),
                    )
                    Text(
                        "/",
                        style = monoCounter.copy(color = statusColor.copy(alpha = 0.35f), fontSize = (18 + sizeOffset).sp),
                    )
                    Text(
                        line.expected.formatQty(),
                        style = monoCounter.copy(color = statusColor.copy(alpha = 0.55f), fontSize = (40 + sizeOffset).sp),
                    )

                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(if (hasChanged) 1f else 0f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(statusColor)
                    .clickable(enabled = hasChanged, onClick = onApply)
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Outlined.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Text(
                        stringResource(R.string.btn_apply),
                        style = monoLabel.copy(color = Color.White, fontWeight = FontWeight.Medium, fontSize = (15 + sizeOffset).sp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f).height(64.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, Color(0x24000000), RoundedCornerShape(14.dp))
                        .background(Color.White)
                        .clickable(onClick = onDecrement),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "-1",
                        style = monoCounter.copy(color = PrimaPalette.Ink, fontSize = (26 + sizeOffset).sp, fontWeight = FontWeight.Medium),
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f).height(64.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(PrimaPalette.Coral)
                        .clickable(onClick = onIncrement),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "+1",
                        style = monoCounter.copy(color = Color.White, fontSize = (26 + sizeOffset).sp, fontWeight = FontWeight.Medium),
                    )
                }
            }
        }
    }
}


@Composable
private fun ItemQtyExtraDetails(
    line: Line,
    typed: String,
    onKey: (String) -> Unit,
    onConfirm: () -> Unit,
    onConfirmRequired: () -> Unit,
) {
    val sizeOffset = LocalTextSizeOffset.current
    val parsedQty = typed.toDoubleOrNull()
    val previewStatus = if (parsedQty != null && typed.isNotEmpty()) LineStatus.of(parsedQty, line.expected) else line.status
    val previewColor by animateColorAsState(
        targetValue = previewStatus.color,
        animationSpec = tween(durationMillis = 200),
        label = "keypadPreviewColor",
    )

    var caretVisible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) { delay(500); caretVisible = !caretVisible }
    }

    val confirmEnabled = typed.isNotEmpty() && parsedQty != null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PrimaPalette.Cream)
            .padding(horizontal = 22.dp, vertical = 16.dp),
    ) {
        // Hero card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .border(2.dp, previewColor.copy(alpha = 0.55f), RoundedCornerShape(18.dp))
                .background(Color.White)
                .padding(horizontal = 18.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        typed.ifEmpty { line.scanned.formatQty() },
                        style = monoCounter.copy(
                            color = if (typed.isEmpty()) previewColor.copy(alpha = 0.38f) else previewColor,
                            fontSize = (40 + sizeOffset).sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                    Box(
                        modifier = Modifier
                            .padding(start = 3.dp, bottom = 5.dp)
                            .width(3.dp).height(30.dp)
                            .alpha(if (caretVisible) 1f else 0f)
                            .clip(RoundedCornerShape(2.dp))
                            .background(PrimaPalette.Coral),
                    )
                }
                Text(
                    "/",
                    style = monoCounter.copy(color = previewColor.copy(alpha = 0.35f), fontSize = (18 + sizeOffset).sp),
                )
                Text(
                    line.expected.formatQty(),
                    style = monoCounter.copy(color = previewColor.copy(alpha = 0.55f), fontSize = (40 + sizeOffset).sp),
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        // Keypad â€” phone layout (1-9 top, C/0/backspace bottom)
        val keyRows = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf(".", "0", "X"),
        )
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            keyRows.forEach { row ->
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    row.forEach { key ->
                        val isMuted = key == "X"
                        Box(
                            modifier = Modifier
                                .weight(1f).fillMaxHeight()
                                .clip(RoundedCornerShape(14.dp))
                                .border(1.dp, Color(0x14000000), RoundedCornerShape(14.dp))
                                .background(if (isMuted) PrimaPalette.CreamAlt else Color.White)
                                .clickable { onKey(key) },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (key == "X") {
                                Icon(
                                    Icons.AutoMirrored.Outlined.Backspace,
                                    contentDescription = null,
                                    tint = PrimaPalette.Ink,
                                    modifier = Modifier.size((26 + sizeOffset).dp),
                                )
                            } else {
                                Text(
                                    if (key == ".") "," else key,
                                    style = monoCounter.copy(color = PrimaPalette.Ink, fontSize = (26 + sizeOffset).sp, fontWeight = FontWeight.Medium),
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))

        val btnText = if (typed.isEmpty()) "Set to ${line.expected.formatQty()}" else "Set to $typed"
        val btnColor = when {
            typed.isEmpty()  -> LineStatus.EXACT.color
            confirmEnabled   -> previewColor
            else             -> PrimaPalette.CreamAlt
        }
        val btnEnabled = typed.isEmpty() || confirmEnabled
        val btnAction: () -> Unit = if (typed.isEmpty()) onConfirmRequired else onConfirm
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(btnColor)
                .clickable(enabled = btnEnabled, onClick = btnAction),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                modifier = Modifier.padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    btnText,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Medium,
                        color = if (btnEnabled) Color.White else PrimaPalette.Ink4,
                        fontSize = (18 + sizeOffset).sp,
                    ),
                )
                if (btnEnabled) {
                    Icon(Icons.Outlined.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

