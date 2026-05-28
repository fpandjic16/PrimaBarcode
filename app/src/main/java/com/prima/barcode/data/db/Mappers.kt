package com.prima.barcode.data.db

import com.prima.barcode.data.model.DocState
import com.prima.barcode.data.model.Document
import com.prima.barcode.data.model.DocumentType
import com.prima.barcode.data.model.ExtraLine
import com.prima.barcode.data.model.Item
import com.prima.barcode.data.model.Line
import com.prima.barcode.data.model.Location
import com.prima.barcode.data.model.ResponsibilityCenter
import java.time.Instant

// ── DocState serialization ────────────────────────────────────────────────────

fun DocState.toDbString(): String = when (this) {
    DocState.Downloaded      -> "Downloaded"
    DocState.InProgress      -> "InProgress"
    DocState.Completed       -> "Completed"
    is DocState.UploadFailed -> "UploadFailed:${reason}"
}

fun String.toDocState(): DocState = when {
    this == "InProgress"        -> DocState.InProgress
    this == "Completed"         -> DocState.Completed
    startsWith("UploadFailed:") -> DocState.UploadFailed(removePrefix("UploadFailed:"))
    else                        -> DocState.Downloaded
}

// ── DocumentType serialization ────────────────────────────────────────────────

fun String.toDocumentType(): DocumentType =
    DocumentType.entries.firstOrNull { it.key == this } ?: DocumentType.WAREHOUSE_SHIPMENT

// ── Entity -> Domain ──────────────────────────────────────────────────────────

fun DocumentLineEntity.toDomain(scanned: Double): Line = Line(
    documentNo = documentNo,
    lineNo = lineNo,
    item = Item(itemNo, itemName),
    barcodeNo = barcodeNo,
    expected = expected,
    scanned = scanned,
    destinationCode = destinationCode,
    sourceCode = sourceCode,
    unitOfMeasureCode = unitOfMeasureCode,
)

fun DocumentHeaderWithLines.toDomain(): Document = Document(
    documentNo = document.documentNo,
    type = document.type.toDocumentType(),
    destinationCode = document.destinationCode,
    sourceCode = document.sourceCode,
    rcCode = document.rcCode,
    ownerUserId = document.ownerUserId,
    creationDateTime = Instant.ofEpochMilli(document.creationDateTime),
    documentDate = document.documentDate?.let { Instant.ofEpochMilli(it) },
    lines = lines.sortedBy { it.lineNo }.map { lineEntity ->
        val scanned = recordings.filter { it.documentLine == lineEntity.lineNo }.sumOf { it.quantity }
        lineEntity.toDomain(scanned)
    },
    extraLines = recordings.filter { it.documentLine == 0 }.map { recording ->
        ExtraLine(
            recordingLineNo = recording.recordingLineNo,
            barcodeNo = recording.barcodeNo,
            quantity = recording.quantity,
            unitOfMeasureCode = recording.unitOfMeasureCode,
        )
    },
    state = document.docState.toDocState(),
)

// ── Domain -> Entity ──────────────────────────────────────────────────────────

fun Document.toEntity(): DocumentHeaderEntity = DocumentHeaderEntity(
    documentNo = documentNo,
    type = type.key,
    destinationCode = destinationCode,
    sourceCode = sourceCode,
    rcCode = rcCode,
    ownerUserId = ownerUserId,
    creationDateTime = creationDateTime.toEpochMilli(),
    documentDate = documentDate?.toEpochMilli(),
    docState = state.toDbString(),
)

fun Line.toEntity(type: String): DocumentLineEntity = DocumentLineEntity(
    documentNo = documentNo,
    type = type,
    lineNo = lineNo,
    itemNo = item.no,
    itemName = item.name,
    barcodeNo = barcodeNo,
    expected = expected,
    destinationCode = destinationCode,
    sourceCode = sourceCode,
    unitOfMeasureCode = unitOfMeasureCode,
)

// ── Location / RC mappers ─────────────────────────────────────────────────────

fun LocationEntity.toDomain() = Location(code = code, name = name, rc = rcCode)
fun ResponsibilityCenterEntity.toDomain() = ResponsibilityCenter(code = code, name = name, short = short)
fun Location.toEntity() = LocationEntity(code = code, name = name, rcCode = rc)
fun ResponsibilityCenter.toEntity() = ResponsibilityCenterEntity(code = code, name = name, short = short)
