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
