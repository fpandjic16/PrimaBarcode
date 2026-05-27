package com.prima.barcode.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        DocumentHeaderEntity::class,
        DocumentLineEntity::class,
        RecordingEntity::class,
        LocationEntity::class,
        ResponsibilityCenterEntity::class,
    ],
    version = 9,
    exportSchema = true,
)
abstract class PrimaDatabase : RoomDatabase() {
    abstract fun documentHeaderDao(): DocumentHeaderDao
    abstract fun documentLineDao(): DocumentLineDao
    abstract fun recordingDao(): RecordingDao
    abstract fun locationDao(): LocationDao
}
