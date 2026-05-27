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
        @Provides
        @Singleton
        fun provideDatabase(@ApplicationContext context: Context): PrimaDatabase =
            Room.databaseBuilder(
                context,
                PrimaDatabase::class.java,
                "prima_barcode.db",
            ).fallbackToDestructiveMigration().build()

        @Provides
        @Singleton
        fun provideLocationDao(db: PrimaDatabase): LocationDao = db.locationDao()
    }
}
