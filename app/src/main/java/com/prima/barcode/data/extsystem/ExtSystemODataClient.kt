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
import java.net.Proxy
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
    private var clientKey: Triple<String, String, String>? = null  // domain, rawUsername, pass
    private var ntlmAuth: NtlmAuthenticator? = null

    fun configure(config: ExtSystemConfig, creds: ExtSystemCredentials): ExtSystemODataClient {
        val key = Triple(config.domain, creds.username, creds.password)
        if (clientKey != key) {
            httpClient?.close()
            httpClient = buildClient(config.domain, creds.username, creds.password)
            clientKey = key
        }
        return this
    }

    private fun buildClient(configuredDomain: String, rawUsername: String, password: String): HttpClient {
        // What the user typed always wins over the configured domain, so a login can be
        // corrected on the spot without touching Settings:
        //   DOMAIN\user / user@domain -> that domain
        //   .\user  (and bare \user)  -> no domain at all, deliberately overriding Settings
        //                                (the Windows "this machine, not the domain" form)
        //   user                      -> fall back to Settings → Credential session
        // A username written with a separator states its own domain and is taken at its
        // word — including when what it states is "none", which is why the check is on the
        // separator rather than on the parsed domain being non-blank. The username handed
        // to NTLM is always the bare one, so "DOMAIN\user" plus a configured domain can't
        // send the domain through twice.
        val typed = rawUsername.trim()
        val (typedDomain, username) = parseDomainUser(typed)
        val statesOwnDomain = '\\' in typed || '@' in typed
        val domain = if (statesOwnDomain) typedDomain else configuredDomain.trim()
        val auth = NtlmAuthenticator(domain, username, password)
        ntlmAuth = auth
        val okHttp = OkHttpClient.Builder()
            .proxy(Proxy.NO_PROXY)  // bypass system proxy; NAV is always on local LAN
            .authenticator(auth)
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
        ntlmAuth?.resetPhase()
        return runCatching {
            val response = client.get(baseUrl) { accept(ContentType.Application.Json) }
            if (response.status.isSuccess()) ExtSystemResult.Success(Unit)
            else {
                val phase = ntlmAuth?.phaseReached ?: 0
                Timber.w("testConnection: HTTP ${response.status.value}, NTLM phase=$phase")
                val msg = when {
                    response.status.value == 401 && phase == 0 ->
                        "Server did not issue an NTLM challenge. Verify Windows Authentication is enabled on this NAV OData endpoint."
                    response.status.value == 401 ->
                        "NTLM handshake completed (phase $phase) but server rejected the credentials. Check domain\\username and password."
                    else -> {
                        val body = runCatching { response.bodyAsText() }.getOrDefault("")
                        "HTTP ${response.status.value}: $body".take(300)
                    }
                }
                ExtSystemResult.Failure(msg, response.status.value)
            }
        }.getOrElse {
            Timber.e(it, "OData connection test failed")
            ExtSystemResult.Failure(it.message ?: "Unknown error")
        }
    }

    suspend fun uploadRecording(url: String, row: NavBarcodeAppRecording): ExtSystemResult<Unit> {
        val client = httpClient ?: return ExtSystemResult.Failure("Client not configured")
        return runCatching {
            val body = gson.toJson(row)
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
                ExtSystemResult.Failure("HTTP ${response.status.value}: $errorBody".take(300), response.status.value)
            }
        }.getOrElse {
            Timber.e(it, "OData upload error")
            ExtSystemResult.Failure(it.message ?: "Network error")
        }
    }

    suspend fun downloadRaw(url: String): ExtSystemResult<String> {
        val client = httpClient ?: return ExtSystemResult.Failure("Client not configured")
        return runCatching {
            val allValues = com.google.gson.JsonArray()
            var nextUrl: String? = url
            while (nextUrl != null) {
                val response = client.get(nextUrl) {
                    accept(ContentType.Application.Json)
                    header("Accept", "application/json;odata=nometadata")
                }
                if (!response.status.isSuccess()) {
                    val body = runCatching { response.bodyAsText() }.getOrDefault("")
                    Timber.w("NAV download failed [${response.status.value}]: $body")
                    return ExtSystemResult.Failure(
                        "HTTP ${response.status.value}: ${response.status.description}",
                        response.status.value,
                    )
                }
                val page = com.google.gson.JsonParser.parseString(response.bodyAsText()).asJsonObject
                page.getAsJsonArray("value")?.forEach { allValues.add(it) }
                nextUrl = page.get("@odata.nextLink")?.takeIf { !it.isJsonNull }?.asString
                Timber.d("OData page: ${allValues.size()} rows total, more=${nextUrl != null}")
            }
            val merged = com.google.gson.JsonObject().apply { add("value", allValues) }
            ExtSystemResult.Success(gson.toJson(merged))
        }.getOrElse {
            Timber.e(it, "NAV download error: $url")
            ExtSystemResult.Failure(it.message ?: "Network error")
        }
    }

    fun close() {
        httpClient?.close()
        httpClient = null
        clientKey = null
        ntlmAuth = null
    }

    companion object {
        /** Windows' "this machine, not a domain" prefix — `.\user`. */
        private const val LOCAL_MACHINE = "."

        /**
         * Splits a NAV login into (domain, bareUsername). Accepts `DOMAIN\user` and
         * `user@domain`. The domain comes back empty when none was given, and also for
         * `.\user` — Windows' "this machine, not a domain" form is a statement of *no*
         * domain, not a domain literally named ".".
         */
        fun parseDomainUser(raw: String): Pair<String, String> {
            val trimmed = raw.trim()
            return when {
                '\\' in trimmed -> {
                    val domain = trimmed.substringBefore('\\')
                    (if (domain == LOCAL_MACHINE) "" else domain) to trimmed.substringAfter('\\')
                }
                '@'  in trimmed -> trimmed.substringAfter('@') to trimmed.substringBefore('@')
                else            -> "" to trimmed
            }
        }
    }
}
