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

/**
 * A recording left without a line, because NAV dropped that line from the document after the
 * operator had already scanned it.
 *
 * There is no [Item] here on purpose: the line that carried the item number and description is
 * gone, and the barcode is the only thing the scan itself recorded. Inventing a name would be
 * guessing at what the operator scanned.
 */
data class OrphanedScan(
    val barcodeNo: String,
    val quantity: Double,
    val unitOfMeasureCode: String,
    val lineNo: Int,
    /** Null if the stored timestamp can't be parsed — worth showing the scan anyway. */
    val at: Instant?,
    val userId: String,
)

/**
 * A queued recording the ERP refused, kept together with the reason.
 *
 * Unlike an [OrphanedScan] this still belongs to a real line — it is the row itself the ERP would
 * not take, typically a field it considers too long or of the wrong type. Such a row fails again
 * on every retry, so the operator needs to see it and decide.
 */
data class FailedScan(
    val barcodeNo: String,
    val quantity: Double,
    val unitOfMeasureCode: String,
    val lineNo: Int,
    val error: String,
)

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
    /** Scans whose line NAV removed. Blocks upload until the operator resolves them. */
    val orphanedScans: List<OrphanedScan> = emptyList(),
    /** Queued scans the ERP rejected; they will not go through on their own. */
    val failedScans: List<FailedScan> = emptyList(),
    /**
     * Scans that reached the ERP while their line still existed, and whose line the ERP has
     * since removed.
     *
     * Reportable, not actionable: the row is already recorded in the ERP, so discarding it here
     * would change nothing there — only an ERP-side correction would. Deliberately kept out of
     * [needsReview] so it neither blocks the upload nor asks the operator for a decision they
     * cannot carry out from the device.
     */
    val sentOrphanedScans: List<OrphanedScan> = emptyList(),
    /** Already accepted by the ERP, still held locally until the whole document is removed. */
    val sentScans: Int = 0,
    /** Still waiting to be sent — including [failedScans], which are queued but stuck. */
    val pendingScans: Int = 0,
) {
    val linesExact: Int get() = lines.count { it.status == LineStatus.EXACT }
    val linesTotal: Int get() = lines.size
    val scannedQty: Double get() = lines.sumOf { it.scanned }
    val expectedQty: Double get() = lines.sumOf { it.expected }
    val hasProgress: Boolean get() = lines.any { it.scanned > 0.0 }
    val needsReview: Boolean get() = orphanedScans.isNotEmpty() || failedScans.isNotEmpty()
    /** Everything recorded on this document, sent or not — what the operator actually scanned. */
    val totalScans: Int get() = sentScans + pendingScans
    val partlySent: Boolean get() = sentScans > 0 && pendingScans > 0
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
