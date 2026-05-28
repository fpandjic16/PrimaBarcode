package com.prima.barcode.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "documentHeader",
    primaryKeys = ["documentNo", "type"],
)
data class DocumentHeaderEntity(
    val documentNo: String,
    val type: String,
    val destinationCode: String,
    val sourceCode: String,
    val rcCode: String,
    val ownerUserId: String,
    val creationDateTime: Long,
    val documentDate: Long?,
    val docState: String,
)

@Entity(
    tableName = "documentLine",
    primaryKeys = ["documentNo", "type", "lineNo"],
    foreignKeys = [ForeignKey(entity = DocumentHeaderEntity::class, parentColumns = ["documentNo", "type"], childColumns = ["documentNo", "type"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("documentNo", "type"), Index("barcodeNo")],
)
data class DocumentLineEntity(
    val documentNo: String,
    val type: String,
    val lineNo: Int,
    val itemNo: String,
    val itemName: String,
    val barcodeNo: String,
    val expected: Double,
    val destinationCode: String,
    val sourceCode: String,
    val unitOfMeasureCode: String,
)

@Entity(
    tableName = "recordings",
    primaryKeys = ["documentNo", "type", "documentLine", "recordingLineNo"],
    foreignKeys = [ForeignKey(entity = DocumentHeaderEntity::class, parentColumns = ["documentNo", "type"], childColumns = ["documentNo", "type"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("documentNo", "type"), Index("documentLine")],
)
data class RecordingEntity(
    val documentNo: String,
    val type: String,
    val documentLine: Int,
    val recordingLineNo: Int,
    val barcodeNo: String,
    val quantity: Double,
    val creationDateTime: Long,
    val format: String?,
    val userId: String,
    val destinationCode: String,
    val sourceCode: String,
    val unitOfMeasureCode: String,
)

@Entity(tableName = "locations")
data class LocationEntity(
    @PrimaryKey val code: String,
    val name: String,
    val rcCode: String,
)

@Entity(tableName = "responsibility_centers")
data class ResponsibilityCenterEntity(
    @PrimaryKey val code: String,
    val name: String,
    val short: String?,
)
