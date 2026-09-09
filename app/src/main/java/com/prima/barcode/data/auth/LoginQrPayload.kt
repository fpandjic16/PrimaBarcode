package com.prima.barcode.data.auth

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.prima.barcode.BuildConfig
import timber.log.Timber
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Credentials handed to the sign-in screen by a scanned QR code, so a handheld can be signed in
 * without typing a Windows password on a keypad.
 *
 * The code is **encrypted**, so a QR reader — or a photo of the code — shows nothing but base64.
 * Wire format, base64 over the whole thing:
 *
 *     [ 12-byte IV ][ AES-256-GCM ciphertext + 16-byte tag ]
 *
 * and the plaintext inside is the JSON below:
 *
 *     {"username":"PRIMA-COMMERCE\\filip","password":"…"}
 *
 * JSON rather than a delimited string because a Windows password may contain the very characters
 * a delimiter would have to reserve (`|`, `:`, `,`); splitting on one would truncate the password
 * into something that merely looks wrong at sign-in. The domain rides inside `username` exactly
 * as if typed — see `ExtSystemODataClient.buildClient`.
 *
 * GCM authenticates as well as encrypts, so a code made with a different key fails to decrypt
 * rather than yielding garbage credentials that would then fail confusingly against NAV.
 *
 * **What this does and doesn't protect.** The key lives in the APK, so this stops someone
 * photographing or glancing at a printed code — the realistic exposure — but not someone who
 * unpacks the app to recover the key. Treat a printed code as sensitive regardless. Rotating the
 * key means a new build (see `loginQrKey` in app/build.gradle.kts), which invalidates every code
 * issued under the old one.
 */
private data class LoginQrDto(
    @SerializedName("username") val username: String? = null,
    @SerializedName("password") val password: String? = null,
)

private val qrGson = Gson()

private const val IV_BYTES = 12
private const val GCM_TAG_BITS = 128
private const val AES_KEY_BYTES = 32

/**
 * Decrypts a scanned QR payload into credentials, or null when the code isn't one this build can
 * read — a plaintext or foreign code, a code encrypted under a different key, or the common case
 * of the camera catching an ordinary product barcode. Callers surface that as a message rather
 * than ignoring it; they can't distinguish the causes, and deliberately aren't told which, since
 * that distinction is only useful to someone probing the codes.
 *
 * The username is trimmed the way typed input is; the password deliberately isn't, since leading
 * or trailing whitespace can be a genuine part of it.
 */
fun parseLoginQr(raw: String): ExtSystemCredentials? {
    val key = loginQrKey() ?: return null
    return runCatching {
        val blob = Base64.getDecoder().decode(raw.trim())
        if (blob.size <= IV_BYTES) return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(key, "AES"),
                GCMParameterSpec(GCM_TAG_BITS, blob, 0, IV_BYTES),
            )
        }
        val json = String(cipher.doFinal(blob, IV_BYTES, blob.size - IV_BYTES), Charsets.UTF_8)
        val dto = qrGson.fromJson(json, LoginQrDto::class.java) ?: return null
        val user = dto.username?.trim().orEmpty()
        val pass = dto.password.orEmpty()
        if (user.isBlank() || pass.isBlank()) null else ExtSystemCredentials(user, pass)
    }.getOrElse {
        // Nothing about the payload is logged — a failure here is either a non-login barcode or
        // a code this build can't read, and neither is worth putting a ciphertext in logcat for.
        Timber.d("Login QR could not be read: ${it.javaClass.simpleName}")
        null
    }
}

/** The build's QR key, or null (logged once per failure) when this build wasn't given a usable one. */
private fun loginQrKey(): ByteArray? {
    val configured = BuildConfig.LOGIN_QR_KEY
    if (configured.isBlank()) {
        Timber.w("No loginQrKey in this build — QR sign-in is inactive. Set it in local.properties.")
        return null
    }
    val bytes = runCatching { Base64.getDecoder().decode(configured) }.getOrNull()
    if (bytes == null || bytes.size != AES_KEY_BYTES) {
        Timber.w("loginQrKey is not base64 for %d bytes — QR sign-in is inactive.", AES_KEY_BYTES)
        return null
    }
    return bytes
}
