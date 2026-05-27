package com.prima.barcode.data.repository

import androidx.room.withTransaction
import com.prima.barcode.data.db.*
import com.prima.barcode.data.model.DocState
import com.prima.barcode.data.model.Document
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
    suspend fun setLineScanned(documentNo: String, type: String, lineNo: Int, scanned: Double)
    suspend fun addExtraLine(documentNo: String, type: String, barcodeNo: String, userId: String, quantity: Double)
    suspend fun updateExtraLineQuantity(documentNo: String, type: String, recordingLineNo: Int, quantity: Double)
    suspend fun deleteExtraLine(documentNo: String, type: String, recordingLineNo: Int)
    suspend fun updateDocState(documentNo: String, type: String, state: DocState)
    suspend fun deleteDocument(documentNo: String, type: String)
    suspend fun getUploadableDocs(): List<Document>
    suspend fun clearAll()
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
                            creationDateTime = doc.creationDateTime.toEpochMilli(),
                            format = null,
                            userId = doc.ownerUserId,
                            destinationCode = line.destinationCode,
                            sourceCode = line.sourceCode,
                        )
                    )
                }
            }
        }
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
            val nextNo = db.recordingDao().getNextRecordingLineNo(documentNo, type, lineNo)
            db.recordingDao().insert(
                RecordingEntity(
                    documentNo = documentNo,
                    type = type,
                    documentLine = lineNo,
                    recordingLineNo = nextNo,
                    barcodeNo = barcodeNo,
                    quantity = quantity,
                    creationDateTime = Instant.now().toEpochMilli(),
                    format = format,
                    userId = userId,
                    destinationCode = line.destinationCode,
                    sourceCode = line.sourceCode,
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

    override suspend fun setLineScanned(documentNo: String, type: String, lineNo: Int, scanned: Double) {
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
                        creationDateTime = Instant.now().toEpochMilli(),
                        format = null,
                        userId = header.ownerUserId,
                        destinationCode = line.destinationCode,
                        sourceCode = line.sourceCode,
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
            val nextNo = db.recordingDao().getNextRecordingLineNo(documentNo, type, 0)
            db.recordingDao().insert(
                RecordingEntity(
                    documentNo = documentNo,
                    type = type,
                    documentLine = 0,
                    recordingLineNo = nextNo,
                    barcodeNo = barcodeNo,
                    quantity = quantity,
                    creationDateTime = Instant.now().toEpochMilli(),
                    format = null,
                    userId = userId,
                    destinationCode = header.destinationCode,
                    sourceCode = header.sourceCode,
                )
            )
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

    override suspend fun clearAll() {
        db.documentHeaderDao().deleteAll()
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