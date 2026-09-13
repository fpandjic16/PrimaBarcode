package com.prima.barcode.data.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.prima.barcode.data.extsystem.ExtSystemODataClient
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/** One operator known to this device. [id] is the normalised key; [displayName] is what they typed. */
data class UserProfile(
    val id: String,
    val displayName: String,
)

/**
 * The operators this device knows, and the secret that unlocks each one's data.
 *
 * Deliberately separate from [ExtSystemCredentialStore], because the two answer different
 * questions with different lifetimes:
 *
 *  - the credential store holds the NAV password, which NTLM needs in the clear, and **expires**;
 *  - this holds a one-way PBKDF2 digest of that password, which **never expires**.
 *
 * Tying them together is what made per-user data unsafe to begin with: identity was derived from
 * credentials, credentials self-destruct after their TTL, and a scoped app whose identity vanishes
 * is an app that hides an operator's own unsent work from them. The data does not expire, so
 * neither may the secret that reaches it.
 *
 * The digest adds no exposure the device did not already carry — the plaintext password is stored
 * anyway, because NTLM cannot work without it. A digest is strictly less than that.
 */
@Singleton
class UserProfileStore @Inject constructor(@param:ApplicationContext private val context: Context) {

    private val masterKey by lazy {
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
    }

    private val prefs by lazy {
        EncryptedSharedPreferences.create(
            context,
            "user_profiles",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    // ── Profiles ──────────────────────────────────────────────────────────────

    fun profiles(): List<UserProfile> =
        prefs.getStringSet(KEY_IDS, emptySet()).orEmpty().sorted().map { id ->
            UserProfile(id = id, displayName = prefs.getString("$id.$KEY_NAME", id) ?: id)
        }

    /**
     * Who is signed in — held in memory only, and deliberately so.
     *
     * Persisting it would restore the session on the next app launch, which is precisely what must
     * not happen: a device left on a shelf would open into the last operator's data with no
     * password asked. A process restart therefore means signing in again. What *is* persisted is
     * the enrolment below, so that signing in again works without the server.
     */
    @Volatile
    private var currentProfileId: String? = null

    fun currentId(): String? = currentProfileId

    fun current(): UserProfile? = currentProfileId?.let { id ->
        UserProfile(id = id, displayName = prefs.getString("$id.$KEY_NAME", id) ?: id)
    }

    fun setCurrent(id: String) { currentProfileId = id }

    /** Forgets who is signed in. Leaves every profile and its data exactly where it is. */
    fun clearCurrent() { currentProfileId = null }

    // ── Enrolment and unlock ──────────────────────────────────────────────────

    /**
     * Records (or re-records) the password that unlocks this profile offline.
     *
     * Called only after the ERP has accepted that password, so the digest can never come to
     * describe a password the server would refuse. Re-deriving on every successful sign-in is what
     * makes a password changed in the ERP heal itself here.
     */
    fun enroll(profile: UserProfile, password: String) {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val digest = derive(password, salt, ITERATIONS)
        prefs.edit()
            .putStringSet(KEY_IDS, profiles().mapTo(HashSet()) { it.id }.apply { add(profile.id) })
            .putString("${profile.id}.$KEY_NAME", profile.displayName)
            .putString("${profile.id}.$KEY_SALT", salt.toHex())
            .putInt("${profile.id}.$KEY_ITERATIONS", ITERATIONS)
            .putString("${profile.id}.$KEY_DIGEST", digest.toHex())
            .apply()
    }

    /**
     * Whether [password] unlocks [id] without asking the server.
     *
     * Reads the stored iteration count rather than the constant, so [ITERATIONS] can be raised
     * later without locking out everyone enrolled under the old value.
     */
    fun unlocks(id: String, password: String): Boolean {
        val salt = prefs.getString("$id.$KEY_SALT", null)?.fromHex() ?: return false
        val expected = prefs.getString("$id.$KEY_DIGEST", null)?.fromHex() ?: return false
        val iterations = prefs.getInt("$id.$KEY_ITERATIONS", ITERATIONS)
        return MessageDigest.isEqual(derive(password, salt, iterations), expected)
    }

    private fun derive(password: String, salt: ByteArray, iterations: Int): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS))
            .encoded

    companion object {
        /** Longest user name the ERP will take. Enforced before a profile or a scan can exist. */
        const val MAX_NAME_LENGTH = 50

        private const val KEY_IDS = "ids"
        private const val KEY_NAME = "name"
        private const val KEY_SALT = "salt"
        private const val KEY_ITERATIONS = "iterations"
        private const val KEY_DIGEST = "digest"

        private const val SALT_BYTES = 16
        private const val KEY_BITS = 256

        /**
         * Cost of one unlock. Paid once per shift, so a few hundred milliseconds is fine; measure
         * on the slowest device in the fleet (MC3300) and lower this if it is not.
         */
        private const val ITERATIONS = 120_000

        /**
         * The identity a profile is keyed by, and the value written into every recording's
         * `userId`.
         *
         * Upper case, domain removed. The same person reaches the same data whether they type
         * `alice`, `PRIMA\alice` or `alice@prima.hr` — which they did not before: `User.id` was
         * the raw typed string, so one person could end up with three identities and, under
         * per-profile storage, three separate databases.
         *
         * Returns null for anything that cannot serve as an identity: blank, or longer than
         * [MAX_NAME_LENGTH]. Callers refuse rather than truncate — a silently shortened name is a
         * different person as far as this key is concerned.
         */
        fun normalise(raw: String): String? {
            val bare = ExtSystemODataClient.parseDomainUser(raw).second.trim().uppercase()
            return if (bare.isEmpty() || bare.length > MAX_NAME_LENGTH) null else bare
        }

        /**
         * Database filename for a profile.
         *
         * A hash, never the name itself: the key may legally contain characters a filesystem will
         * not take, and hashing means the filename can never depend on what an operator typed.
         */
        fun databaseName(id: String): String {
            val hash = MessageDigest.getInstance("SHA-256").digest(id.toByteArray()).toHex()
            return "prima_${hash.take(16)}.db"
        }

        private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

        private fun String.fromHex(): ByteArray? =
            if (length % 2 != 0) null
            else runCatching {
                ByteArray(length / 2) { substring(it * 2, it * 2 + 2).toInt(16).toByte() }
            }.getOrNull()
    }
}
