package com.prima.barcode.data.extsystem

import com.prima.barcode.data.db.RecordingEntity
import com.google.gson.annotations.SerializedName

// ── Upload request ────────────────────────────────────────────────────────────
// Barcode App Recordings: flat OData entity, one POST creates one recording row.

data class NavBarcodeAppRecording(
    @SerializedName("Document_Type")           val documentType: String,
    // Retail and warehouse documents share a Document_Type code, so the recording has to carry
    // the same discriminator the download filtered on or NAV can't tell which one it belongs
    // to. Null (transport sheets, which are neither) is omitted from the JSON by Gson.
    @SerializedName("Retail_Location")         val retailLocation: Boolean?,
    @SerializedName("Document_No")             val documentNo: String,
    @SerializedName("Document_Line_No")        val documentLineNo: Int,
    @SerializedName("Recording_Line_No")       val recordingLineNo: Int,
    @SerializedName("Recording_Guid")          val recordingGuid: String,
    @SerializedName("Barcode")                 val barcode: String,
    @SerializedName("Scanned_Quantity")        val scannedQuantity: Double,
    @SerializedName("Unit_Of_Measure_Code")    val unitOfMeasureCode: String,
    @SerializedName("Source_Creation_DateTime") val sourceCreationDateTime: String,
    @SerializedName("Source_User_ID")          val sourceUserId: String,
    @SerializedName("Source_Code")             val sourceCode: String,
    @SerializedName("Destination_Code")        val destinationCode: String,
)

// ── Mapping ───────────────────────────────────────────────────────────────────
// recordingGuid comes from the stored recording and is the same on every attempt. It used to be
// generated per attempt, on the reasoning that a retry should not collide with the row NAV
// already holds — but colliding is exactly what makes a retry safe. If NAV commits a row and the
// response is lost, a fresh GUID makes the retry look like a new recording and the quantity is
// counted twice; the same GUID lets NAV recognise it and reject the duplicate.

fun RecordingEntity.toNavRecording(
    documentTypeCode: String,
    retailLocation: Boolean?,
): NavBarcodeAppRecording = NavBarcodeAppRecording(
    documentType             = documentTypeCode,
    retailLocation           = retailLocation,
    documentNo               = documentNo,
    documentLineNo           = documentLine,
    recordingLineNo          = recordingLineNo,
    recordingGuid            = recordingGuid,
    barcode                  = barcodeNo,
    scannedQuantity          = quantity,
    unitOfMeasureCode        = unitOfMeasureCode,
    sourceCreationDateTime   = creationDateTime,
    sourceUserId             = userId,
    sourceCode               = sourceCode,
    destinationCode          = destinationCode,
)

// ── Download response wrapper ──────────────────────────────────────────────────

data class NavODataList<T>(
    @SerializedName("value") val value: List<T> = emptyList()
)

// Barcode App Entry: one record per document line, header fields repeated on every row.
// A single NAV table/OData page serves every document type, discriminated by Document_Type.
data class NavBarcodeAppEntry(
    @SerializedName("Document_Type")         val documentType:      String  = "",
    @SerializedName("Document_No")           val documentNo:        String,
    @SerializedName("Line_No")               val lineNo:            Int     = 0,
    @SerializedName("Source_No")             val sourceCode:        String  = "",
    @SerializedName("Retail_Location")       val isRetailLocation:  Boolean = false,
    @SerializedName("Destination_No")        val destinationCode:   String  = "",
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
