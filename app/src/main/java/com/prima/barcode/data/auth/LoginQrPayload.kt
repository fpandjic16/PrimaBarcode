package com.prima.barcode.data.auth

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName

/**
 * Credentials handed to the sign-in screen by a scanned QR code, so a handheld can be signed
 * in without typing a Windows password on a keypad.
 *
 * Expected payload:
 *
 *     {"username":"PRIMA-COMMERCE\\filip","password":"…"}
 *
 * JSON rather than a delimited string on purpose. A Windows password may legitimately contain
 * any character a delimiter format would have to reserve — `|`, `:`, `,` — and splitting on one
 * would silently truncate such a password into something that merely *looks* wrong at sign-in.
 * JSON escaping makes that impossible.
 *
 * The domain travels inside `username` exactly as if typed: `DOMAIN\user`, `user@domain`, or a
 * bare name that falls back to the configured domain — see `ExtSystemODataClient.buildClient`.
 */
private data class LoginQrDto(
    @SerializedName("username") val username: String? = null,
    @SerializedName("password") val password: String? = null,
)

private val qrGson = Gson()

/**
 * Parses a scanned QR payload into credentials, or null when the code isn't a well-formed
 * sign-in code — which includes the common case of the camera catching an ordinary product
 * barcode instead. Callers are expected to surface that as a message rather than ignore it.
 *
 * The username is trimmed the same way typed input is; the password deliberately isn't, since
 * leading or trailing whitespace can be a genuine part of it.
 */
fun parseLoginQr(raw: String): ExtSystemCredentials? = runCatching {
    val dto = qrGson.fromJson(raw.trim(), LoginQrDto::class.java) ?: return null
    val user = dto.username?.trim().orEmpty()
    val pass = dto.password.orEmpty()
    if (user.isBlank() || pass.isBlank()) null else ExtSystemCredentials(user, pass)
}.getOrNull()
