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

/**
 * [retailLocation] is what separates the retail types from the warehouse ones on the NAV side.
 * They deliberately share a `Document_Type` code — retail shipment and warehouse shipment are
 * both `SHIPMENT`, retail receipt and warehouse receipt are both `RECEIPT` — so `Document_Type`
 * alone no longer identifies a bucket. Every download filter and every uploaded recording for
 * these four carries `Retail_Location` alongside the code to disambiguate.
 *
 * Null means the type doesn't participate: transport sheets, complaints and inventory each carry
 * their own `Document_Type` code, so there is nothing to disambiguate — they get no
 * `Retail_Location` clause on download nor the field on upload.
 *
 * [defaultFilterMode] is the scope a type falls back to until the user sets one in Settings.
 * Location suits the document types that move goods between two places; complaints are tracked
 * by responsibility centre instead.
 */
enum class DocumentType(
    val key: String,
    val display: String,
    val retailLocation: Boolean? = null,
    val defaultFilterMode: DocTypeFilterMode = DocTypeFilterMode.LOCATION,
) {
    WAREHOUSE_SHIPMENT("WHSE_SHIP", "Warehouse Shipment",    retailLocation = false),
    WAREHOUSE_RECEIPT( "WHSE_RCPT", "Warehouse Receipt",     retailLocation = false),
    RETAIL_SHIPMENT(   "RT_SHIP",   "Retail Shipment",       retailLocation = true),
    RETAIL_RECEIPT(    "RT_RCPT",   "Retail Whse. Receipt",  retailLocation = true),
    TRANSPORT_SHEET(   "TRANSPORT", "Transport Sheet"),
    COMPLAINT(         "COMPLAINT", "Complaint",             defaultFilterMode = DocTypeFilterMode.RESPONSIBILITY_CENTER),
    // Counted against quantities NAV supplies, exactly like the document types above, so nothing
    // about scanning, status colours or upload differs. Note the consequence: an item found on
    // the shelf that NAV didn't send is still rejected as "barcode not found" (§A.3), the same as
    // everywhere else in the app.
    INVENTORY(         "INVENTORY", "Inventory"),
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
    val scanningQty: Double = 1.0,
) {
    val status: LineStatus get() = LineStatus.of(scanned, expected)
}

data class Document(
    val documentNo: String,
    val type: DocumentType,
    val destinationCode: String,
    val sourceCode: String,
    val rcCode: String,
    val isSourceRetail: Boolean = false,
    val creationDateTime: Instant,
    val documentDate: Instant? = null,
    val lines: List<Line>,
    val state: DocState,
) {
    val linesExact: Int get() = lines.count { it.status == LineStatus.EXACT }
    val linesTotal: Int get() = lines.size
    val scannedQty: Double get() = lines.sumOf { it.scanned }
    val expectedQty: Double get() = lines.sumOf { it.expected }
    val hasProgress: Boolean get() = lines.any { it.scanned > 0.0 }
}

sealed interface DocState {
    data object Downloaded   : DocState
    data object InProgress   : DocState
    data object Completed    : DocState
    data object PendingUpload : DocState
    data class  UploadFailed(val reason: String) : DocState
}

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
