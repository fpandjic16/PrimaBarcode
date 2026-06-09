package com.prima.barcode.data.extsystem

import com.prima.barcode.data.model.Document
import com.prima.barcode.data.model.DocumentType
import com.google.gson.annotations.SerializedName

// ── Upload request ────────────────────────────────────────────────────────────

data class ExtSystemUploadPayload(val documents: List<ExtSystemUploadDocument>)

data class ExtSystemUploadDocument(
    val documentNo: String,
    val type: String,
    val lines: List<ExtSystemUploadLine>,
)

data class ExtSystemUploadLine(
    val itemNo: String,
    val lineNo: String,
    val recordingLineNo: String,
    val barcodeNo: String,
    val quantity: String,
    val creationDateTime: String,
    val userId: String,
)

// ── Mapping ───────────────────────────────────────────────────────────────────

fun Document.toUploadPayload(): ExtSystemUploadDocument {
    val regularLines = lines.flatMap { line ->
        // Each recording for this line becomes an upload line
        // We use scanned qty as the total; recordingLineNo increments from 1
        listOf(ExtSystemUploadLine(
            itemNo          = line.item.no,
            lineNo          = line.lineNo.toString(),
            recordingLineNo = "1",
            barcodeNo       = line.barcodeNo,
            quantity        = line.scanned.toString(),
            creationDateTime = creationDateTime.toString(),
            userId          = "",
        ))
    }.filter { it.quantity != "0.0" }

    val extraLines = extraLines.map { extra ->
        ExtSystemUploadLine(
            itemNo          = "",
            lineNo          = "0",
            recordingLineNo = extra.recordingLineNo.toString(),
            barcodeNo       = extra.barcodeNo,
            quantity        = extra.quantity.toString(),
            creationDateTime = creationDateTime.toString(),
            userId          = "",
        )
    }

    return ExtSystemUploadDocument(
        documentNo = documentNo,
        type       = type.key,
        lines      = regularLines + extraLines,
    )
}

fun List<Document>.toUploadPayload() = ExtSystemUploadPayload(
    documents = map { it.toUploadPayload() }
)

// ── Download response wrapper ──────────────────────────────────────────────────

data class NavODataList<T>(
    @SerializedName("value") val value: List<T> = emptyList()
)

// Flat line model: OData page returns one record per document line.
// Header fields (Document_No, Location_Code, etc.) are repeated on every row.
// Field names must match the actual OData page — adjust as needed.
data class NavDocumentLine(
    @SerializedName("Document_No")           val documentNo:      String,
    @SerializedName("Location_Code")         val sourceCode:      String  = "",
    @SerializedName("Bin_Code")              val destinationCode: String  = "",
    @SerializedName("Responsibility_Center") val rcCode:          String  = "",
    @SerializedName("Assigned_User_ID")      val ownerUserId:     String  = "",
    @SerializedName("Document_Date")         val documentDate:    String? = null,
    @SerializedName("Line_No")               val lineNo:          Int     = 0,
    @SerializedName("Item_No")               val itemNo:          String  = "",
    @SerializedName("Description")           val description:     String  = "",
    @SerializedName("No_2")                  val barcodeNo:       String  = "",
    @SerializedName("Qty_Outstanding")       val qtyOutstanding:  Double  = 0.0,
    @SerializedName("Unit_of_Measure_Code")  val unitOfMeasureCode: String = "",
)

// BEMFOrder page: one record per document line, header fields repeated on every row.
data class NavBarcodeEntriesDownload(
    @SerializedName("Document_Type")         val documentType:      String  = "",
    @SerializedName("Document_No")           val documentNo:        String,
    @SerializedName("Line_No")               val lineNo:            Int     = 0,
    @SerializedName("Source_No")             val sourceCode:        String  = "",
    @SerializedName("Retail_Location")       val retailLocation:    String  = "",
    @SerializedName("Destination_No")        val destinationNo:     String  = "",
    @SerializedName("Document_Date")         val documentDate:      String? = null,
    @SerializedName("Responsibility_Center") val rcCode:            String  = "",
    @SerializedName("Item_No")               val itemNo:            String  = "",
    @SerializedName("Item_Description")      val description:       String  = "",
    @SerializedName("Item_Qty")              val qtyOutstanding:    Double  = 0.0,
    @SerializedName("Scanning_Qty")          val scanningQty:       Double  = 1.0,
    @SerializedName("Item_UoM")              val unitOfMeasureCode: String  = "",
    @SerializedName("Barcode")               val barcodeNo:         String  = "",
)

data class NavLocation(
    @SerializedName("Code")                    val code:   String,
    @SerializedName("Name")                    val name:   String,
    @SerializedName("Responsibility_Center")   val rcCode: String = "",
)

data class NavResponsibilityCenter(
    @SerializedName("Code")  val code:  String,
    @SerializedName("Name")  val name:  String,
    @SerializedName("Short") val short: String? = null,
)
