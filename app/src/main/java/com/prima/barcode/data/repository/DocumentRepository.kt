package com.prima.barcode.data.repository

import androidx.room.withTransaction
import com.prima.barcode.data.db.*
import com.prima.barcode.data.model.DocState
import com.prima.barcode.data.model.Document
import com.prima.barcode.data.model.DocumentType
import com.prima.barcode.data.model.Line
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

interface DocumentRepository {
    fun observeAll(): Flow<List<Document>>
    fun observeDocuments(sourceCode: String, rcCode: String): Flow<List<Document>>
    fun observeDocument(documentNo: String, type: String): Flow<Document?>
    suspend fun upsertDocument(doc: Document)
    suspend fun replaceDownloadedDocuments(type: DocumentType, docs: List<Document>)
    suspend fun recordScan(
        documentNo: String,
        type: String,
        lineNo: Int,
        barcodeNo: String,
        userId: String,
        quantity: Double,
        format: String?,
    )
    suspend fun undoLastScan(documentNo: String, type: String, lineNo: Int)
    suspend fun setLineScanned(documentNo: String, type: String, lineNo: Int, scanned: Double, userId: String)
    suspend fun addExtraLine(documentNo: String, type: String, barcodeNo: String, userId: String, quantity: Double)
    suspend fun updateExtraLineQuantity(documentNo: String, type: String, recordingLineNo: Int, quantity: Double)
    suspend fun deleteExtraLine(documentNo: String, type: String, recordingLineNo: Int)
    suspend fun updateDocState(documentNo: String, type: String, state: DocState)
    suspend fun deleteDocument(documentNo: String, type: String)
    suspend fun getUploadableDocs(): List<Document>
    suspend fun getRecordings(documentNo: String, type: String): List<RecordingEntity>
    suspend fun deleteRecording(documentNo: String, type: String, documentLine: Int, recordingLineNo: Int)
    suspend fun clearAll()
    suspend fun clearByType(type: DocumentType)
    suspend fun deleteDocumentRecordings(documentNo: String, type: String)
}

@Singleton
class DocumentRepositoryImpl @Inject constructor(
    private val db: PrimaDatabase,
) : DocumentRepository {

    override fun observeAll(): Flow<List<Document>> =
        combine(
            db.documentHeaderDao().observeAllHeaders(),
            db.documentLineDao().observeAll(),
            db.recordingDao().observeAll(),
        ) { headers, lines, recordings ->
            assembleDocuments(headers, lines, recordings)
        }

    override fun observeDocuments(sourceCode: String, rcCode: String): Flow<List<Document>> =
        combine(
            db.documentHeaderDao().observeHeaders(sourceCode, rcCode),
            db.documentLineDao().observeAll(),
            db.recordingDao().observeAll(),
        ) { headers, lines, recordings ->
            assembleDocuments(headers, lines, recordings)
        }

    override fun observeDocument(documentNo: String, type: String): Flow<Document?> =
        combine(
            db.documentHeaderDao().observeHeader(documentNo, type),
            db.documentLineDao().observeByDoc(documentNo, type),
            db.recordingDao().observeByDoc(documentNo, type),
        ) { header, lines, recordings ->
            header?.let { DocumentHeaderWithLines(it, lines, recordings).toDomain() }
        }

    override suspend fun upsertDocument(doc: Document) {
        db.withTransaction {
            db.documentHeaderDao().upsert(doc.toEntity())
            db.documentLineDao().upsertAll(doc.lines.map { it.toEntity(doc.type.key) })
            doc.lines.filter { it.scanned > 0.0 }.forEach { line ->
                val hasRecordings = db.recordingDao().getLastForLine(doc.documentNo, doc.type.key, line.lineNo) != null
                if (!hasRecordings) {
                    val nextNo = db.recordingDao().getNextRecordingLineNo(doc.documentNo, doc.type.key, line.lineNo)
                    db.recordingDao().insert(
                        RecordingEntity(
                            documentNo = doc.documentNo,
                            type = doc.type.key,
                            documentLine = line.lineNo,
                            recordingLineNo = nextNo,
                            barcodeNo = line.barcodeNo,
                            quantity = line.scanned,
                            creationDateTime = doc.creationDateTime.toString(),
                            format = null,
                            userId = "",
                            destinationCode = line.destinationCode,
                            sourceCode = line.sourceCode,
                            unitOfMeasureCode = line.unitOfMeasureCode,
                            rcCode = doc.rcCode,
                        )
                    )
                }
            }
        }
    }

    override suspend fun replaceDownloadedDocuments(type: DocumentType, docs: List<Document>) {
        db.withTransaction {
            val existingHeaders = db.documentHeaderDao().getAll().filter { it.type == type.key }
            val downloadedKeys = docs.map { it.documentNo }.toSet()

            // A document no longer in this download is only removed if it has no
            // recordings — recordings (real scan progress) must never be deleted.
            for (header in existingHeaders) {
                if (header.documentNo !in downloadedKeys) {
                    val hasRecordings = db.recordingDao().getByDoc(header.documentNo, header.type).isNotEmpty()
                    if (!hasRecordings) db.documentHeaderDao().deleteByKey(header.documentNo, header.type)
                }
            }

            for (doc in docs) {
                mergeDocument(doc, type.key)
            }
        }
    }

    /**
     * Merges a freshly downloaded document into local state without ever deleting
     * recordings. Extra (not-on-document) recordings are re-attributed to real lines
     * by matching barcode, in case they were scanned offline before this document's
     * lines were known locally. Header/lines always refresh to the downloaded values;
     * docState is always recomputed from the merged recordings.
     */
    private suspend fun mergeDocument(doc: Document, type: String) {
        val existingRecordings = db.recordingDao().getByDoc(doc.documentNo, type)
        val lineTiedRecordings = existingRecordings.filter { it.documentLine != 0 }
        val scannedByLine = lineTiedRecordings.groupBy { it.documentLine }
            .mapValues { (_, recs) -> recs.sumOf { it.quantity } }.toMutableMap()

        val extras = existingRecordings.filter { it.documentLine == 0 }
        for (extra in extras) {
            val matchingLines = doc.lines.filter { it.barcodeNo == extra.barcodeNo }.sortedBy { it.lineNo }
            if (matchingLines.isEmpty()) continue

            var remainingQty = extra.quantity
            for ((index, line) in matchingLines.withIndex()) {
                val isLast = index == matchingLines.lastIndex
                val alreadyScanned = scannedByLine[line.lineNo] ?: 0.0
                val room = (line.expected - alreadyScanned).coerceAtLeast(0.0)
                // The last matching line absorbs whatever is left, even over its expected qty.
                val allocation = if (isLast) remainingQty else minOf(remainingQty, room)
                if (allocation <= 0.0) continue

                val nextNo = db.recordingDao().getNextRecordingLineNo(doc.documentNo, type, line.lineNo)
                db.recordingDao().insert(
                    RecordingEntity(
                        documentNo = doc.documentNo,
                        type = type,
                        documentLine = line.lineNo,
                        recordingLineNo = nextNo,
                        barcodeNo = extra.barcodeNo,
                        quantity = allocation,
                        creationDateTime = extra.creationDateTime,
                        format = extra.format,
                        userId = extra.userId,
                        destinationCode = line.destinationCode,
                        sourceCode = line.sourceCode,
                        unitOfMeasureCode = line.unitOfMeasureCode,
                        rcCode = doc.rcCode,
                    )
                )
                scannedByLine[line.lineNo] = alreadyScanned + allocation
                remainingQty -= allocation
            }
            // Every match fully absorbs the extra's quantity (the last line takes any
            // remainder), so once there's at least one match the old row is spent.
            db.recordingDao().deleteByPk(doc.documentNo, type, 0, extra.recordingLineNo)
        }

        val finalRecordings = db.recordingDao().getByDoc(doc.documentNo, type)
        val state = computeStateAfterMerge(doc.lines, finalRecordings)
        db.documentHeaderDao().upsert(doc.toEntity().copy(docState = state.toDbString()))
        db.documentLineDao().deleteAllForDoc(doc.documentNo, type)
        db.documentLineDao().upsertAll(doc.lines.map { it.toEntity(type) })
    }

    private fun computeStateAfterMerge(lines: List<Line>, recordings: List<RecordingEntity>): DocState {
        if (recordings.isEmpty()) return DocState.Downloaded
        val scannedByLine = recordings.filter { it.documentLine != 0 }
            .groupBy { it.documentLine }
            .mapValues { (_, recs) -> recs.sumOf { it.quantity } }
        val hasExtras = recordings.any { it.documentLine == 0 }
        val allExact = lines.all { line -> (scannedByLine[line.lineNo] ?: 0.0) == line.expected }
        return if (allExact && !hasExtras) DocState.Completed else DocState.InProgress
    }

    override suspend fun recordScan(
        documentNo: String,
        type: String,
        lineNo: Int,
        barcodeNo: String,
        userId: String,
        quantity: Double,
        format: String?,
    ) {
        db.withTransaction {
            val line = db.documentLineDao().getByKey(documentNo, type, lineNo) ?: return@withTransaction
            val header = db.documentHeaderDao().getByKey(documentNo, type) ?: return@withTransaction
            val nextNo = db.recordingDao().getNextRecordingLineNo(documentNo, type, lineNo)
            db.recordingDao().insert(
                RecordingEntity(
                    documentNo = documentNo,
                    type = type,
                    documentLine = lineNo,
                    recordingLineNo = nextNo,
                    barcodeNo = barcodeNo,
                    quantity = quantity,
                    creationDateTime = Instant.now().toString(),
                    format = format,
                    userId = userId,
                    destinationCode = line.destinationCode,
                    sourceCode = line.sourceCode,
                    unitOfMeasureCode = line.unitOfMeasureCode,
                    rcCode = header.rcCode,
                )
            )
            advanceToInProgressIfNeeded(documentNo, type)
        }
    }

    override suspend fun undoLastScan(documentNo: String, type: String, lineNo: Int) {
        db.withTransaction {
            val last = db.recordingDao().getLastForLine(documentNo, type, lineNo) ?: return@withTransaction
            db.recordingDao().deleteByPk(documentNo, type, lineNo, last.recordingLineNo)
            regressFromCompletedIfNeeded(documentNo, type)
            regressToDownloadedIfNeeded(documentNo, type)
        }
    }

    override suspend fun setLineScanned(documentNo: String, type: String, lineNo: Int, scanned: Double, userId: String) {
        db.withTransaction {
            db.recordingDao().deleteAllForLine(documentNo, type, lineNo)
            if (scanned > 0.0) {
                val line = db.documentLineDao().getByKey(documentNo, type, lineNo) ?: return@withTransaction
                val header = db.documentHeaderDao().getByKey(documentNo, type) ?: return@withTransaction
                val nextNo = db.recordingDao().getNextRecordingLineNo(documentNo, type, lineNo)
                db.recordingDao().insert(
                    RecordingEntity(
                        documentNo = documentNo,
                        type = type,
                        documentLine = lineNo,
                        recordingLineNo = nextNo,
                        barcodeNo = line.barcodeNo,
                        quantity = scanned,
                        creationDateTime = Instant.now().toString(),
                        format = null,
                        userId = userId,
                        destinationCode = line.destinationCode,
                        sourceCode = line.sourceCode,
                        unitOfMeasureCode = line.unitOfMeasureCode,
                        rcCode = header.rcCode,
                    )
                )
            }
            advanceToInProgressIfNeeded(documentNo, type)
            regressFromCompletedIfNeeded(documentNo, type)
            regressToDownloadedIfNeeded(documentNo, type)
        }
    }

    override suspend fun addExtraLine(documentNo: String, type: String, barcodeNo: String, userId: String, quantity: Double) {
        db.withTransaction {
            val header = db.documentHeaderDao().getByKey(documentNo, type) ?: return@withTransaction
            // Same not-on-document barcode scanned again → accumulate into the existing
            // extra line so one row shows the running total, instead of many rows.
            val existing = db.recordingDao().getExtraByBarcode(documentNo, type, barcodeNo)
            if (existing != null) {
                db.recordingDao().updateQuantity(documentNo, type, 0, existing.recordingLineNo, existing.quantity + quantity)
            } else {
                val nextNo = db.recordingDao().getNextRecordingLineNo(documentNo, type, 0)
                db.recordingDao().insert(
                    RecordingEntity(
                        documentNo = documentNo,
                        type = type,
                        documentLine = 0,
                        recordingLineNo = nextNo,
                        barcodeNo = barcodeNo,
                        quantity = quantity,
                        creationDateTime = Instant.now().toString(),
                        format = null,
                        userId = userId,
                        destinationCode = header.destinationCode,
                        sourceCode = header.sourceCode,
                        unitOfMeasureCode = "",
                        rcCode = header.rcCode,
                    )
                )
            }
            advanceToInProgressIfNeeded(documentNo, type)
            regressFromCompletedIfNeeded(documentNo, type)
        }
    }

    override suspend fun updateExtraLineQuantity(documentNo: String, type: String, recordingLineNo: Int, quantity: Double) {
        db.withTransaction {
            db.recordingDao().updateQuantity(documentNo, type, 0, recordingLineNo, quantity)
            advanceToInProgressIfNeeded(documentNo, type)
            regressFromCompletedIfNeeded(documentNo, type)
        }
    }

    override suspend fun deleteExtraLine(documentNo: String, type: String, recordingLineNo: Int) {
        db.withTransaction {
            db.recordingDao().deleteByPk(documentNo, type, 0, recordingLineNo)
            regressFromCompletedIfNeeded(documentNo, type)
            regressToDownloadedIfNeeded(documentNo, type)
        }
    }

    override suspend fun updateDocState(documentNo: String, type: String, state: DocState) {
        db.documentHeaderDao().updateState(documentNo, type, state.toDbString())
    }

    override suspend fun deleteDocument(documentNo: String, type: String) {
        db.documentHeaderDao().deleteByKey(documentNo, type)
    }

    override suspend fun getUploadableDocs(): List<Document> {
        val headers = db.documentHeaderDao().getAll()
        return headers.map { header ->
            val lines = db.documentLineDao().getByDoc(header.documentNo, header.type)
            val recordings = db.recordingDao().getByDoc(header.documentNo, header.type)
            DocumentHeaderWithLines(header, lines, recordings).toDomain()
        }.filter { it.lines.any { l -> l.scanned > 0.0 } || it.extraLines.isNotEmpty() }
    }

    override suspend fun getRecordings(documentNo: String, type: String): List<RecordingEntity> =
        db.recordingDao().getByDoc(documentNo, type)

    override suspend fun deleteRecording(documentNo: String, type: String, documentLine: Int, recordingLineNo: Int) {
        db.recordingDao().deleteByPk(documentNo, type, documentLine, recordingLineNo)
    }

    override suspend fun clearAll() {
        db.documentHeaderDao().deleteAll()
    }

    override suspend fun clearByType(type: DocumentType) {
        db.documentHeaderDao().deleteAllByType(type.key)
    }

    override suspend fun deleteDocumentRecordings(documentNo: String, type: String) {
        db.withTransaction {
            db.recordingDao().deleteAllForDoc(documentNo, type)
            db.documentHeaderDao().updateState(documentNo, type, DocState.Downloaded.toDbString())
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun assembleDocuments(
        headers: List<DocumentHeaderEntity>,
        lines: List<DocumentLineEntity>,
        recordings: List<RecordingEntity>,
    ): List<Document> {
        val linesByDoc = lines.groupBy { it.documentNo to it.type }
        val recordingsByDoc = recordings.groupBy { it.documentNo to it.type }
        return headers.map { header ->
            DocumentHeaderWithLines(
                document = header,
                lines = linesByDoc[header.documentNo to header.type] ?: emptyList(),
                recordings = recordingsByDoc[header.documentNo to header.type] ?: emptyList(),
            ).toDomain()
        }
    }

    private suspend fun advanceToInProgressIfNeeded(documentNo: String, type: String) {
        val doc = db.documentHeaderDao().getByKey(documentNo, type)
        if (doc?.docState == DocState.Downloaded.toDbString() || doc?.docState?.startsWith("UploadFailed:") == true) {
            db.documentHeaderDao().updateState(documentNo, type, DocState.InProgress.toDbString())
        }
    }

    private suspend fun regressToDownloadedIfNeeded(documentNo: String, type: String) {
        val header = db.documentHeaderDao().getByKey(documentNo, type) ?: return
        if (header.docState != DocState.InProgress.toDbString()) return
        val recordings = db.recordingDao().getByDoc(documentNo, type)
        if (recordings.isEmpty()) {
            db.documentHeaderDao().updateState(documentNo, type, DocState.Downloaded.toDbString())
        }
    }

    private suspend fun regressFromCompletedIfNeeded(documentNo: String, type: String) {
        val header = db.documentHeaderDao().getByKey(documentNo, type) ?: return
        if (header.docState != DocState.Completed.toDbString()) return
        val lines = db.documentLineDao().getByDoc(documentNo, type)
        val recordings = db.recordingDao().getByDoc(documentNo, type)
        val scannedByLine = recordings.filter { it.documentLine != 0 }
            .groupBy { it.documentLine }
            .mapValues { (_, recs) -> recs.sumOf { it.quantity } }
        val hasExtraLines = recordings.any { it.documentLine == 0 }
        val allLinesExact = lines.all { line -> (scannedByLine[line.lineNo] ?: 0.0) == line.expected }
        if (!allLinesExact || hasExtraLines) {
            db.documentHeaderDao().updateState(documentNo, type, DocState.InProgress.toDbString())
        }
    }
}