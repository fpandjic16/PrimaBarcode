package com.prima.barcode.data.model

import java.time.Instant

data class User(
    val id: String,
    val username: String,
    val displayName: String,
    val initials: String,
    val role: String?  = null,
)

data class ResponsibilityCenter(
    val code: String,
    val name: String,
    val short: String? = null,
)

data class Location(
    val code: String,
    val name: String,
    val rc: String,
)

enum class DocTypeFilterMode { LOCATION, RESPONSIBILITY_CENTER }

enum class DocumentType(val key: String, val display: String) {
    WAREHOUSE_SHIPMENT("WHSE_SHIP", "Warehouse Shipment"),
    WAREHOUSE_RECEIPT( "WHSE_RCPT", "Warehouse Receipt"),
    RETAIL_SHIPMENT(   "RT_SHIP",   "Retail Shipment"),
    RETAIL_RECEIPT(    "RT_RCPT",   "Retail Whse. Receipt"),
    TRANSPORT_SHEET(   "TRANSPORT", "Transport Sheet"),
}

data class Item(
    val no:   String,
    val name: String,
)

data class Line(
    val documentNo: String,
    val lineNo: Int,
    val item: Item,
    val barcodeNo: String,
    val expected: Double,
    val scanned: Double,
    val destinationCode: String,
    val sourceCode: String,
    val unitOfMeasureCode: String,
) {
    val status: LineStatus get() = LineStatus.of(scanned, expected)
}

data class ExtraLine(
    val recordingLineNo: Int,
    val barcodeNo: String,
    val quantity: Double,
    val unitOfMeasureCode: String,
)

data class Document(
    val documentNo: String,
    val type: DocumentType,
    val destinationCode: String,
    val sourceCode: String,
    val rcCode: String,
    val ownerUserId: String,
    val creationDateTime: Instant,
    val documentDate: Instant? = null,
    val lines: List<Line>,
    val extraLines: List<ExtraLine> = emptyList(),
    val state: DocState,
) {
    val linesExact: Int get() = lines.count { it.status == LineStatus.EXACT }
    val linesTotal: Int get() = lines.size
}

sealed interface DocState {
    data object Downloaded   : DocState
    data object InProgress   : DocState
    data object Completed    : DocState
    data class  UploadFailed(val reason: String) : DocState
}

sealed interface SyncState {
    data object Offline                              : SyncState
    data object Idle                                 : SyncState
    data class  Pending(val count: Int)              : SyncState
    data class  Syncing(val progress: Float)         : SyncState
    data class  Error(val failures: List<SyncError>) : SyncState
}

data class SyncError(
    val documentNo: String,
    val reason: String,
    val detail: String,
    val at: Instant,
)

data class TapeEntry(
    val id: String,
    val barcode: String,
    val itemName: String?,
    val quantity: Double,
    val at: Instant,
    val lineStatus: LineStatus?,
) {
    val isError: Boolean get() = lineStatus == null
}

/** Show no decimal places for whole numbers; otherwise show up to 5 significant decimal places. */
fun Double.formatQty(): String {
    if (this == kotlin.math.floor(this) && !isInfinite()) return toLong().toString()
    return "%.5f".format(this).trimEnd('0').trimEnd('.')
}
