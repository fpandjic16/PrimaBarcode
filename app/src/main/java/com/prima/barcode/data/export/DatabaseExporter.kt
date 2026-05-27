package com.prima.barcode.data.export

import android.content.Context
import android.net.Uri
import com.google.gson.GsonBuilder
import com.prima.barcode.data.db.PrimaDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DatabaseExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: PrimaDatabase,
) {
    private val gson = GsonBuilder().setPrettyPrinting().create()

    suspend fun exportTo(uri: Uri) = withContext(Dispatchers.IO) {
        val headers    = db.documentHeaderDao().getAll()
        val lines      = db.documentLineDao().getAll()
        val recordings = db.recordingDao().getAll()

        val payload = mapOf(
            "exportedAt"      to Instant.now().toString(),
            "documentHeaders" to headers,
            "documentLines"   to lines,
            "recordings"      to recordings,
        )

        context.contentResolver.openOutputStream(uri)?.use { stream ->
            stream.writer().use { it.write(gson.toJson(payload)) }
        }
    }
}