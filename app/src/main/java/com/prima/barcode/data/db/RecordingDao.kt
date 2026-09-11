package com.prima.barcode.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingDao {

    @Query("SELECT * FROM recordings WHERE documentNo = :documentNo AND type = :type ORDER BY documentLine, recordingLineNo")
    fun observeByDoc(documentNo: String, type: String): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings")
    fun observeAll(): Flow<List<RecordingEntity>>

    @Query("SELECT COALESCE(MAX(recordingLineNo), 0) + 1 FROM recordings WHERE documentNo = :documentNo AND type = :type AND documentLine = :documentLine")
    suspend fun getNextRecordingLineNo(documentNo: String, type: String, documentLine: Int): Int

    @Query("SELECT * FROM recordings WHERE documentNo = :documentNo AND type = :type")
    suspend fun getByDoc(documentNo: String, type: String): List<RecordingEntity>

    /**
     * Recordings whose `documentLine` no longer matches any line of the document.
     *
     * They appear when NAV drops a line the operator had already scanned: `mergeDocument`
     * replaces the line rows, and the CASCADE only reaches recordings through the header, so
     * these survive with nothing to attach to. Every other query here selects by document alone
     * and cannot tell them apart, which is how they ended up invisible to the UI and still
     * included in uploads.
     */
    @Query(
        """
        SELECT * FROM recordings AS r
        WHERE r.documentNo = :documentNo AND r.type = :type
          AND NOT EXISTS (
            SELECT 1 FROM documentLine AS l
            WHERE l.documentNo = r.documentNo AND l.type = r.type AND l.lineNo = r.documentLine
          )
        ORDER BY r.documentLine, r.recordingLineNo
        """
    )
    suspend fun getOrphansByDoc(documentNo: String, type: String): List<RecordingEntity>

    /**
     * The complement of [getOrphansByDoc] — everything that still belongs to a real line, and
     * therefore the only thing that may be uploaded. Ordered, unlike [getByDoc]: the upload loop
     * stops at the first failure, so which rows survive a partial failure should not depend on
     * whatever order SQLite happens to return.
     */
    @Query(
        """
        SELECT * FROM recordings AS r
        WHERE r.documentNo = :documentNo AND r.type = :type
          AND EXISTS (
            SELECT 1 FROM documentLine AS l
            WHERE l.documentNo = r.documentNo AND l.type = r.type AND l.lineNo = r.documentLine
          )
        ORDER BY r.documentLine, r.recordingLineNo
        """
    )
    suspend fun getLinkedByDoc(documentNo: String, type: String): List<RecordingEntity>

    @Query(
        """
        DELETE FROM recordings
        WHERE documentNo = :documentNo AND type = :type
          AND NOT EXISTS (
            SELECT 1 FROM documentLine AS l
            WHERE l.documentNo = recordings.documentNo
              AND l.type = recordings.type
              AND l.lineNo = recordings.documentLine
          )
        """
    )
    suspend fun deleteOrphansByDoc(documentNo: String, type: String)

    @Insert
    suspend fun insert(recording: RecordingEntity)

    @Query("DELETE FROM recordings WHERE documentNo = :documentNo AND type = :type AND documentLine = :documentLine AND recordingLineNo = :recordingLineNo")
    suspend fun deleteByPk(documentNo: String, type: String, documentLine: Int, recordingLineNo: Int)

    @Query("DELETE FROM recordings WHERE documentNo = :documentNo AND type = :type AND documentLine = :lineNo")
    suspend fun deleteAllForLine(documentNo: String, type: String, lineNo: Int)

    @Query("DELETE FROM recordings WHERE documentNo = :documentNo AND type = :type")
    suspend fun deleteAllForDoc(documentNo: String, type: String)

    @Query("SELECT * FROM recordings")
    suspend fun getAll(): List<RecordingEntity>
}
