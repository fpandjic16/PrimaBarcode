package com.prima.barcode.di

import android.content.Context
import androidx.room.Room
import com.prima.barcode.data.db.LocationDao
import com.prima.barcode.data.db.PrimaDatabase
import com.prima.barcode.data.repository.DocumentRepository
import com.prima.barcode.data.repository.DocumentRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DatabaseModule {

    @Binds
    @Singleton
    abstract fun bindDocumentRepository(impl: DocumentRepositoryImpl): DocumentRepository

    companion object {
        /**
         * Note there is no `fallbackToDestructiveMigration` here, and adding one back would be a
         * mistake.
         *
         * This database holds recordings that exist nowhere else until they reach the ERP — a
         * shift's scanning lives here and only here. A destructive fallback turns any future
         * version bump that forgets a migration into silent, total loss of that work at app
         * launch, with nothing shown to the operator. It is also not hypothetical: the chain
         * used to start at 10 while devices had shipped on schemas 7, 8 and 9, so every one of
         * those upgraded straight into a wipe.
         *
         * Without the fallback, a gap in the chain fails loudly at startup instead. That is a
         * worse-looking failure and a far better one: support can act on a device that won't
         * open, and cannot act on scans that quietly disappeared.
         *
         * So: the chain must stay complete. Every schema bump needs its migration added here.
         */
        @Provides
        @Singleton
        fun provideDatabase(@ApplicationContext context: Context): PrimaDatabase =
            Room.databaseBuilder(
                context,
                PrimaDatabase::class.java,
                "prima_barcode.db",
            ).addMigrations(
                PrimaDatabase.MIGRATION_7_8,
                PrimaDatabase.MIGRATION_8_9,
                PrimaDatabase.MIGRATION_9_10,
                PrimaDatabase.MIGRATION_10_11,
                PrimaDatabase.MIGRATION_11_12,
                PrimaDatabase.MIGRATION_12_13,
                PrimaDatabase.MIGRATION_13_14,
                PrimaDatabase.MIGRATION_14_15,
                PrimaDatabase.MIGRATION_15_16,
            ).build()

        @Provides
        @Singleton
        fun provideLocationDao(db: PrimaDatabase): LocationDao = db.locationDao()
    }
}
