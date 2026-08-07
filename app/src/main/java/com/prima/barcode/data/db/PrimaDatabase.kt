package com.prima.barcode.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        DocumentHeaderEntity::class,
        DocumentLineEntity::class,
        RecordingEntity::class,
        LocationEntity::class,
        ResponsibilityCenterEntity::class,
    ],
    version = 16,
    exportSchema = true,
)
abstract class PrimaDatabase : RoomDatabase() {
    abstract fun documentHeaderDao(): DocumentHeaderDao
    abstract fun documentLineDao(): DocumentLineDao
    abstract fun recordingDao(): RecordingDao
    abstract fun locationDao(): LocationDao

    companion object {
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE documentLine ADD COLUMN scanningQty REAL NOT NULL DEFAULT 1.0")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE recordings_new (
                        documentNo TEXT NOT NULL,
                        type TEXT NOT NULL,
                        documentLine INTEGER NOT NULL,
                        recordingLineNo INTEGER NOT NULL,
                        barcodeNo TEXT NOT NULL,
                        quantity REAL NOT NULL,
                        creationDateTime TEXT NOT NULL,
                        format TEXT,
                        userId TEXT NOT NULL,
                        destinationCode TEXT NOT NULL,
                        sourceCode TEXT NOT NULL,
                        unitOfMeasureCode TEXT NOT NULL,
                        PRIMARY KEY(documentNo, type, documentLine, recordingLineNo),
                        FOREIGN KEY(documentNo, type) REFERENCES documentHeader(documentNo, type) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO recordings_new
                    SELECT documentNo, type, documentLine, recordingLineNo, barcodeNo, quantity,
                           strftime('%Y-%m-%dT%H:%M:%SZ', CAST(creationDateTime AS REAL) / 1000.0, 'unixepoch'),
                           format, userId, destinationCode, sourceCode, unitOfMeasureCode
                    FROM recordings
                """.trimIndent())
                db.execSQL("DROP TABLE recordings")
                db.execSQL("ALTER TABLE recordings_new RENAME TO recordings")
                db.execSQL("CREATE INDEX index_recordings_documentNo_type ON recordings(documentNo, type)")
                db.execSQL("CREATE INDEX index_recordings_documentLine ON recordings(documentLine)")
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE documentHeader_new (
                        documentNo TEXT NOT NULL,
                        type TEXT NOT NULL,
                        destinationCode TEXT NOT NULL,
                        sourceCode TEXT NOT NULL,
                        rcCode TEXT NOT NULL,
                        creationDateTime INTEGER NOT NULL,
                        documentDate INTEGER,
                        docState TEXT NOT NULL,
                        PRIMARY KEY(documentNo, type)
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO documentHeader_new
                    SELECT documentNo, type, destinationCode, sourceCode, rcCode,
                           creationDateTime, documentDate, docState
                    FROM documentHeader
                """.trimIndent())
                db.execSQL("DROP TABLE documentHeader")
                db.execSQL("ALTER TABLE documentHeader_new RENAME TO documentHeader")
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE documentHeader ADD COLUMN isSourceRetail INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE recordings ADD COLUMN rcCode TEXT NOT NULL DEFAULT ''")
                db.execSQL("""
                    UPDATE recordings
                    SET rcCode = (
                        SELECT rcCode FROM documentHeader
                        WHERE documentHeader.documentNo = recordings.documentNo
                          AND documentHeader.type = recordings.type
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE recordings_new (
                        documentNo TEXT NOT NULL,
                        type TEXT NOT NULL,
                        documentLine INTEGER NOT NULL,
                        recordingLineNo INTEGER NOT NULL,
                        barcodeNo TEXT NOT NULL,
                        quantity REAL NOT NULL,
                        creationDateTime TEXT NOT NULL,
                        userId TEXT NOT NULL,
                        destinationCode TEXT NOT NULL,
                        sourceCode TEXT NOT NULL,
                        unitOfMeasureCode TEXT NOT NULL,
                        rcCode TEXT NOT NULL,
                        PRIMARY KEY(documentNo, type, documentLine, recordingLineNo),
                        FOREIGN KEY(documentNo, type) REFERENCES documentHeader(documentNo, type) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO recordings_new
                    SELECT documentNo, type, documentLine, recordingLineNo, barcodeNo, quantity,
                           creationDateTime, userId, destinationCode, sourceCode, unitOfMeasureCode, rcCode
                    FROM recordings
                """.trimIndent())
                db.execSQL("DROP TABLE recordings")
                db.execSQL("ALTER TABLE recordings_new RENAME TO recordings")
                db.execSQL("CREATE INDEX index_recordings_documentNo_type ON recordings(documentNo, type)")
                db.execSQL("CREATE INDEX index_recordings_documentLine ON recordings(documentLine)")
            }
        }
    }
}
