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
    fun observeDocument(documentNo: String, type: String): Flow<Document?>
    suspend fun replaceDownloadedDocuments(type: DocumentType, docs: List<Document>)
    suspend fun recordScan(
        documentNo: String,
        type: String,
        lineNo: Int,
        barcodeNo: String,
        userId: String,
        quantity: Double,
    )
    suspend fun setLineScanned(documentNo: String, type: String, lineNo: Int, scanned: Double, userId: String)
    suspend fun updateDocState(documentNo: String, type: String, state: DocState)
    suspend fun deleteDocument(documentNo: String, type: String)
    suspend fun getRecordings(documentNo: String, type: String): List<RecordingEntity>
    suspend fun deleteRecording(documentNo: String, type: String, documentLine: Int, recordingLineNo: Int)
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

    override fun observeDocument(documentNo: String, type: String): Flow<Document?> =
        combine(
            db.documentHeaderDao().observeHeader(documentNo, type),
            db.documentLineDao().observeByDoc(documentNo, type),
            db.recordingDao().observeByDoc(documentNo, type),
        ) { header, lines, recordings ->
            header?.let { DocumentHeaderWithLines(it, lines, recordings).toDomain() }
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
     * recordings. Header/lines always refresh to the downloaded values; docState is
     * always recomputed from the merged recordings.
     */
    private suspend fun mergeDocument(doc: Document, type: String) {
        val recordings = db.recordingDao().getByDoc(doc.documentNo, type)
        val state = computeStateAfterMerge(doc.lines, recordings)
        db.documentHeaderDao().upsert(doc.toEntity().copy(docState = state.toDbString()))
        db.documentLineDao().deleteAllForDoc(doc.documentNo, type)
        db.documentLineDao().upsertAll(doc.lines.map { it.toEntity(type) })
    }

    private fun computeStateAfterMerge(lines: List<Line>, recordings: List<RecordingEntity>): DocState {
        if (recordings.isEmpty()) return DocState.Downloaded
        val scannedByLine = recordings.groupBy { it.documentLine }
            .mapValues { (_, recs) -> recs.sumOf { it.quantity } }
        val allExact = lines.all { line -> (scannedByLine[line.lineNo] ?: 0.0) == line.expected }
        return if (allExact) DocState.Completed else DocState.InProgress
    }

    override suspend fun recordScan(
        documentNo: String,
        type: String,
        lineNo: Int,
        barcodeNo: String,
        userId: String,
        quantity: Double,
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
                    userId = userId,
                    destinationCode = line.destinationCode,
                    sourceCode = line.sourceCode,
                    unitOfMeasureCode = line.unitOfMeasureCode,
                    rcCode = header.rcCode,
                )
            )
            advanceToInProgressIfNeeded(documentNo, type)
            regressFromCompletedIfNeeded(documentNo, type)
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

    override suspend fun updateDocState(documentNo: String, type: String, state: DocState) {
        db.documentHeaderDao().updateState(documentNo, type, state.toDbString())
    }

    override suspend fun deleteDocument(documentNo: String, type: String) {
        db.documentHeaderDao().deleteByKey(documentNo, type)
    }

    override suspend fun getRecordings(documentNo: String, type: String): List<RecordingEntity> =
        db.recordingDao().getByDoc(documentNo, type)

    override suspend fun deleteRecording(documentNo: String, type: String, documentLine: Int, recordingLineNo: Int) {
        db.recordingDao().deleteByPk(documentNo, type, documentLine, recordingLineNo)
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
        val scannedByLine = recordings.groupBy { it.documentLine }
            .mapValues { (_, recs) -> recs.sumOf { it.quantity } }
        val allLinesExact = lines.all { line -> (scannedByLine[line.lineNo] ?: 0.0) == line.expected }
        if (!allLinesExact) {
            db.documentHeaderDao().updateState(documentNo, type, DocState.InProgress.toDbString())
        }
    }
}