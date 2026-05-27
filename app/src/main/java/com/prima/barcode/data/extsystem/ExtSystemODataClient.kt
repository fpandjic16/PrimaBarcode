package com.prima.barcode.data.extsystem

import com.google.gson.Gson
import com.prima.barcode.data.auth.ExtSystemConfig
import com.prima.barcode.data.auth.ExtSystemCredentials
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import okhttp3.OkHttpClient
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

sealed class ExtSystemResult<out T> {
    data class Success<T>(val data: T) : ExtSystemResult<T>()
    data class Failure(val message: String, val code: Int = -1) : ExtSystemResult<Nothing>()
}

@Singleton
class ExtSystemODataClient @Inject constructor() {

    private val gson = Gson()
    private var httpClient: HttpClient? = null
    private var clientKey: Triple<String, String, String>? = null  // domain, user, pass

    fun configure(config: ExtSystemConfig, creds: ExtSystemCredentials): ExtSystemODataClient {
        val key = Triple(config.domain, creds.username, creds.password)
        if (clientKey != key) {
            httpClient?.close()
            httpClient = buildClient(config.domain, creds.username, creds.password)
            clientKey = key
        }
        return this
    }

    private fun buildClient(domain: String, username: String, password: String): HttpClient {
        val okHttp = OkHttpClient.Builder()
            .authenticator(NtlmAuthenticator(domain, username, password))
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

        return HttpClient(OkHttp) {
            engine { preconfigured = okHttp }
            expectSuccess = false
        }
    }

    suspend fun testConnection(baseUrl: String): ExtSystemResult<Unit> {
        val client = httpClient ?: return ExtSystemResult.Failure("Client not configured")
        return runCatching {
            val response = client.get(baseUrl) { accept(ContentType.Application.Json) }
            if (response.status.isSuccess()) ExtSystemResult.Success(Unit)
            else ExtSystemResult.Failure(response.status.description, response.status.value)
        }.getOrElse {
            Timber.e(it, "OData connection test failed")
            ExtSystemResult.Failure(it.message ?: "Unknown error")
        }
    }

    suspend fun upload(url: String, payload: ExtSystemUploadPayload): ExtSystemResult<Unit> {
        val client = httpClient ?: return ExtSystemResult.Failure("Client not configured")
        return runCatching {
            val body = gson.toJson(payload)
            val response = client.post(url) {
                contentType(ContentType.Application.Json)
                accept(ContentType.Application.Json)
                setBody(body)
            }
            if (response.status.isSuccess()) {
                ExtSystemResult.Success(Unit)
            } else {
                val errorBody = runCatching { response.bodyAsText() }.getOrDefault("")
                Timber.w("OData upload failed [${response.status.value}]: $errorBody")
                ExtSystemResult.Failure("HTTP ${response.status.value}: ${response.status.description}", response.status.value)
            }
        }.getOrElse {
            Timber.e(it, "OData upload error")
            ExtSystemResult.Failure(it.message ?: "Network error")
        }
    }

    suspend fun downloadRaw(url: String): ExtSystemResult<String> {
        val client = httpClient ?: return ExtSystemResult.Failure("Client not configured")
        return runCatching {
            val response = client.get(url) {
                accept(ContentType.Application.Json)
                // OData minimal metadata reduces payload size
                header("Accept", "application/json;odata=nometadata")
            }
            if (response.status.isSuccess()) {
                ExtSystemResult.Success(response.bodyAsText())
            } else {
                val body = runCatching { response.bodyAsText() }.getOrDefault("")
                Timber.w("NAV download failed [${"$"}{response.status.value}]: ${"$"}body")
                ExtSystemResult.Failure("HTTP ${"$"}{response.status.value}: ${"$"}{response.status.description}", response.status.value)
            }
        }.getOrElse {
            Timber.e(it, "NAV download error: ${"$"}url")
            ExtSystemResult.Failure(it.message ?: "Network error")
        }
    }

    fun close() {
        httpClient?.close()
        httpClient = null
        clientKey = null
    }
}
