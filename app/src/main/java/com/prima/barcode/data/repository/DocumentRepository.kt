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
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Outcome of a manual quantity edit.
 *
 * [BelowSent] exists so the refusal can be explained rather than swallowed: the operator asked
 * for a number the device cannot deliver, and needs to know the floor and why it is there.
 */
sealed interface SetQuantityResult {
    data object Applied : SetQuantityResult
    /** Refused — [sent] of this line is already in the ERP and cannot be taken back from here. */
    data class BelowSent(val sent: Double) : SetQuantityResult
}

interface DocumentRepository {
    fun observeAll(): Flow<List<Document>>
    fun observeDocument(documentNo: String, type: String): Flow<Document?>
    /** Individual recordings for one document — what [observeDocument] sums away. */
    fun observeRecordings(documentNo: String, type: String): Flow<List<RecordingEntity>>
    suspend fun replaceDownloadedDocuments(type: DocumentType, docs: List<Document>)
    suspend fun recordScan(
        documentNo: String,
        type: String,
        lineNo: Int,
        barcodeNo: String,
        userId: String,
        quantity: Double,
    )
    suspend fun setLineScanned(
        documentNo: String,
        type: String,
        lineNo: Int,
        scanned: Double,
        userId: String,
    ): SetQuantityResult
    suspend fun updateDocState(documentNo: String, type: String, state: DocState)
    suspend fun deleteDocument(documentNo: String, type: String)
    suspend fun getQueuedRecordings(documentNo: String, type: String): List<RecordingEntity>
    suspend fun getOrphanedRecordings(documentNo: String, type: String): List<RecordingEntity>
    suspend fun hasAnyRecordings(documentNo: String, type: String): Boolean
    suspend fun markRecordingSent(documentNo: String, type: String, documentLine: Int, recordingLineNo: Int)
    suspend fun recordRecordingFailure(
        documentNo: String,
        type: String,
        documentLine: Int,
        recordingLineNo: Int,
        error: String,
    )
    suspend fun discardOrphanedScans(documentNo: String, type: String)
    suspend fun discardFailedScans(documentNo: String, type: String)
    suspend fun clearAll()
    suspend fun deleteQueuedRecording(documentNo: String, type: String, documentLine: Int, recordingLineNo: Int)
    suspend fun deleteDocumentRecordings(documentNo: String, type: String)
    suspend fun recoverStalePendingUploads()
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

    override fun observeRecordings(documentNo: String, type: String): Flow<List<RecordingEntity>> =
        db.recordingDao().observeByDoc(documentNo, type)

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
        // Header first: documentLine has a foreign key to it, so a new document needs its parent
        // row before any line can be inserted. docState is corrected at the end.
        db.documentHeaderDao().upsert(doc.toEntity())
        db.documentLineDao().deleteAllForDoc(doc.documentNo, type)
        db.documentLineDao().upsertAll(doc.lines.map { it.toEntity(type) })

        // Only once the new lines are in place, so matching runs against what NAV just sent.
        reattachOrphansByBarcode(doc.documentNo, type)

        // State last, from recordings as they stand after re-attachment — computing it earlier
        // would measure against line numbers that re-attachment is about to change.
        val recordings = db.recordingDao().getByDoc(doc.documentNo, type)
        db.documentHeaderDao().updateState(
            doc.documentNo, type, computeStateAfterMerge(doc.lines, recordings).toDbString(),
        )
    }

    /**
     * Moves recordings whose line has gone onto whichever line now carries the same barcode.
     *
     * This is for NAV renumbering a line rather than removing the item: without it, every
     * recording on a renumbered line would be treated as surplus at once. What genuinely has no
     * home stays orphaned and is surfaced for review instead.
     *
     * `recordingGuid` is carried across untouched: re-attachment moves a recording's position
     * within the document, it does not create a new one, and the GUID is how that same scan stays
     * identifiable to the ERP afterwards. The row has to be deleted and re-inserted rather than
     * updated because `documentLine` is part of the primary key — `sentAt` and `lastError` ride
     * along with it, so a row already accepted stays accepted.
     *
     * Several lines sharing a barcode resolves to the lowest line number, matching what
     * `RecordingScreen.handleScan` already does when a scan could land on more than one line.
     */
    private suspend fun reattachOrphansByBarcode(documentNo: String, type: String) {
        val orphans = db.recordingDao().getOrphansByDoc(documentNo, type)
        if (orphans.isEmpty()) return

        val lineNoByBarcode = db.documentLineDao().getByDoc(documentNo, type)
            .sortedBy { it.lineNo }
            .groupBy { it.barcodeNo }
            .mapValues { (_, sameBarcode) -> sameBarcode.first().lineNo }

        for (orphan in orphans) {
            val target = lineNoByBarcode[orphan.barcodeNo] ?: continue
            db.recordingDao().deleteByPk(documentNo, type, orphan.documentLine, orphan.recordingLineNo)
            val nextNo = db.recordingDao().getNextRecordingLineNo(documentNo, type, target)
            db.recordingDao().insert(orphan.copy(documentLine = target, recordingLineNo = nextNo))
        }
    }

    private fun computeStateAfterMerge(lines: List<Line>, recordings: List<RecordingEntity>): DocState {
        if (recordings.isEmpty()) return DocState.Downloaded
        // A document with no lines is not complete. `lines.all { }` is vacuously true over an
        // empty list, so a document whose lines NAV had all removed used to be written as
        // Completed — showing an empty status chip, with upload greyed out because no line has
        // progress, and nothing able to move it again.
        if (lines.isEmpty()) return DocState.InProgress
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
                    // Generated here, at the moment the scan becomes real, and never again.
                    recordingGuid = UUID.randomUUID().toString(),
                )
            )
            advanceToInProgressIfNeeded(documentNo, type)
            regressFromCompletedIfNeeded(documentNo, type)
        }
    }

    /**
     * Sets a line's total by hand — the +/- steppers and the keypad.
     *
     * Only the *queued* part of the line moves. A scan the ERP has accepted is immutable here:
     * this ERP's recordings table validates nothing and never refuses a row, so there is no
     * delete, no correction, and no way to unsend a quantity from the device. Editing one would
     * mean the device forgetting what the ERP still holds.
     *
     * So an edit below the sent quantity is **refused**, not clamped — a silent clamp would show
     * the operator a number they didn't ask for and leave them thinking the correction went
     * through. It returns [SetQuantityResult.BelowSent] with the floor, and the caller explains it.
     *
     * This used to delete every recording on the line, sent ones included, and re-queue the whole
     * new total. On a partly-sent line that sent the accepted quantity a second time, which the
     * ERP recorded as surplus, and rewrote the scans' attribution and timestamps to the operator
     * doing the editing.
     */
    override suspend fun setLineScanned(
        documentNo: String,
        type: String,
        lineNo: Int,
        scanned: Double,
        userId: String,
    ): SetQuantityResult = db.withTransaction {
        val sent = db.recordingDao().getSentQuantityForLine(documentNo, type, lineNo)
        if (scanned < sent) return@withTransaction SetQuantityResult.BelowSent(sent)

        db.recordingDao().deleteQueuedForLine(documentNo, type, lineNo)
        // What the edit actually changes: the difference between the requested total and what is
        // already beyond recall. Zero means the operator asked for exactly the sent quantity, and
        // the line is then made up entirely of ERP-accepted rows.
        val queued = scanned - sent
        val line = db.documentLineDao().getByKey(documentNo, type, lineNo)
        val header = db.documentHeaderDao().getByKey(documentNo, type)
        // A missing line or header skips the insert but never the state refresh below — the queued
        // rows are already gone by this point, and leaving the document's state describing them
        // would strand it in a state nothing on the device can still justify.
        if (queued > 0.0 && line != null && header != null) {
            val nextNo = db.recordingDao().getNextRecordingLineNo(documentNo, type, lineNo)
            db.recordingDao().insert(
                RecordingEntity(
                    documentNo = documentNo,
                    type = type,
                    documentLine = lineNo,
                    recordingLineNo = nextNo,
                    barcodeNo = line.barcodeNo,
                    quantity = queued,
                    creationDateTime = Instant.now().toString(),
                    userId = userId,
                    destinationCode = line.destinationCode,
                    sourceCode = line.sourceCode,
                    unitOfMeasureCode = line.unitOfMeasureCode,
                    rcCode = header.rcCode,
                    // This replaces the line's queued recordings with a single aggregate row, so
                    // it is a genuinely new recording and gets its own identity. Reusing one of
                    // the GUIDs just deleted would tell NAV this is a row it already has.
                    recordingGuid = UUID.randomUUID().toString(),
                )
            )
        }
        advanceToInProgressIfNeeded(documentNo, type)
        regressFromCompletedIfNeeded(documentNo, type)
        regressToDownloadedIfNeeded(documentNo, type)
        SetQuantityResult.Applied
    }

    override suspend fun updateDocState(documentNo: String, type: String, state: DocState) {
        db.documentHeaderDao().updateState(documentNo, type, state.toDbString())
    }

    override suspend fun deleteDocument(documentNo: String, type: String) {
        db.documentHeaderDao().deleteByKey(documentNo, type)
    }

    /** What still has to go: attached to a real line, and not yet accepted by the ERP. */
    override suspend fun getQueuedRecordings(documentNo: String, type: String): List<RecordingEntity> =
        db.recordingDao().getQueuedByDoc(documentNo, type)

    override suspend fun getOrphanedRecordings(documentNo: String, type: String): List<RecordingEntity> =
        db.recordingDao().getOrphansByDoc(documentNo, type)

    /**
     * Whether anything was ever recorded here, sent or not.
     *
     * Upload needs this to tell two states apart that both leave nothing queued: a freshly
     * downloaded document, which must be left alone, and one whose rows have all been accepted,
     * which should be removed. Getting that backwards is how downloaded documents were once
     * deleted without being sent.
     */
    override suspend fun hasAnyRecordings(documentNo: String, type: String): Boolean =
        db.recordingDao().getByDoc(documentNo, type).isNotEmpty()

    override suspend fun markRecordingSent(
        documentNo: String,
        type: String,
        documentLine: Int,
        recordingLineNo: Int,
    ) {
        db.recordingDao().markSent(
            documentNo, type, documentLine, recordingLineNo, Instant.now().toString(),
        )
    }

    override suspend fun recordRecordingFailure(
        documentNo: String,
        type: String,
        documentLine: Int,
        recordingLineNo: Int,
        error: String,
    ) {
        db.recordingDao().recordFailure(documentNo, type, documentLine, recordingLineNo, error)
    }

    /**
     * Drops the scans the operator has reviewed and decided are surplus.
     *
     * The one place recordings are deleted without having reached NAV, and it takes a deliberate
     * action to get here — a scan is evidence that goods were physically in someone's hands, so
     * discarding it is the operator's call to make after taking them off the pallet, never the
     * app's to make quietly during a sync.
     */
    /**
     * Drops queued rows the ERP keeps refusing, once the operator has decided to give up on them.
     *
     * The alternative is a document that can never finish: a row rejected for its own content —
     * a field too long, a wrong type — fails identically on every retry, and with the send no
     * longer stopping at the first failure it is the only thing left holding the document open.
     */
    override suspend fun discardFailedScans(documentNo: String, type: String) {
        db.withTransaction {
            db.recordingDao().deleteFailedByDoc(documentNo, type)
            refreshStateFromRecordings(documentNo, type)
        }
    }

    override suspend fun discardOrphanedScans(documentNo: String, type: String) {
        db.withTransaction {
            db.recordingDao().deleteOrphansByDoc(documentNo, type)
            // The document may now be back to untouched, or merely no longer blocked.
            refreshStateFromRecordings(documentNo, type)
        }
    }

    override suspend fun clearAll() {
        db.documentHeaderDao().deleteAll()
    }

    /** Removes a single scan the operator judged wrong. Refuses silently if it has already gone. */
    override suspend fun deleteQueuedRecording(
        documentNo: String,
        type: String,
        documentLine: Int,
        recordingLineNo: Int,
    ) {
        db.withTransaction {
            db.recordingDao().deleteQueuedByPk(documentNo, type, documentLine, recordingLineNo)
            refreshStateFromRecordings(documentNo, type)
        }
    }

    /**
     * Clears a document's scans so the operator can start it again.
     *
     * Only the queued ones. This used to take everything and force the document back to
     * Downloaded, which was harmless while accepted scans were deleted the moment the ERP
     * confirmed them — there was nothing else to take. Now that they are kept, wiping them would
     * mean the device forgets what it has already sent, while the ERP still holds it. The state is
     * recomputed from whatever remains rather than assumed.
     */
    override suspend fun deleteDocumentRecordings(documentNo: String, type: String) {
        db.withTransaction {
            db.recordingDao().deleteQueuedForDoc(documentNo, type)
            refreshStateFromRecordings(documentNo, type)
        }
    }

    private suspend fun refreshStateFromRecordings(documentNo: String, type: String) {
        val lines = db.documentLineDao().getByDoc(documentNo, type).map { it.toDomain(0.0) }
        val recordings = db.recordingDao().getByDoc(documentNo, type)
        db.documentHeaderDao().updateState(
            documentNo, type, computeStateAfterMerge(lines, recordings).toDbString(),
        )
    }

    /**
     * Clears PendingUpload left behind by an upload that never finished.
     *
     * PendingUpload is only ever set by a background upload running in `viewModelScope`. If that
     * scope is gone — process killed, battery died, the Activity finished mid-send — the upload
     * is not resumable and the flag is stale by definition, so anything still carrying it at
     * startup is stranded and needs a way out.
     *
     * State is rebuilt from what actually survived: rows still queued mean the send stopped
     * partway, so the document goes back to InProgress and can be retried; no rows left means
     * everything was accepted and only the final delete was missed, so Downloaded is the honest
     * description of what's on the device.
     *
     * Deliberately never deletes. A stale flag is not evidence a document was uploaded — the
     * cheap cost of a document lingering after a completed send is worth far less than the risk
     * of destroying work on the strength of a flag we already know is unreliable.
     */
    override suspend fun recoverStalePendingUploads() {
        db.withTransaction {
            db.documentHeaderDao().getAll()
                .filter { it.docState.toDocState() == DocState.PendingUpload }
                .forEach { header ->
                    val remaining = db.recordingDao().getByDoc(header.documentNo, header.type)
                    val recovered = if (remaining.isEmpty()) DocState.Downloaded else DocState.InProgress
                    db.documentHeaderDao().updateState(header.documentNo, header.type, recovered.toDbString())
                }
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