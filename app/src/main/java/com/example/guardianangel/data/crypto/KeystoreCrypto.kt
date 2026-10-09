package com.example.guardianangel.data.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val TAG = "KeystoreCrypto"

/**
 * AES-GCM encryption for the few things in the database that must never be readable
 * even with the file in hand: the voiceprint and the disarm PIN.
 *
 * ## Why this and not Jetpack Security
 *
 * `androidx.security:security-crypto` — `EncryptedSharedPreferences` and friends — was
 * deprecated in 2025 with no replacement releases, after a history of main-thread
 * strict-mode violations and keyset corruption on some OEM devices. Google's own
 * guidance is now to use the Android Keystore directly, which is what this does: a
 * hardware-backed AES key that never leaves the secure element, with the ciphertext
 * stored as an ordinary Room column.
 *
 * ## What is and is not protected
 *
 * This encrypts **fields**, not the database. Transcripts and location history are
 * stored in plaintext columns, protected by Android's file-based encryption, which
 * covers a lost or stolen phone but not a rooted one. Encrypting the whole database
 * would mean SQLCipher and another native dependency; that is a deliberate follow-up,
 * recorded in the roadmap rather than quietly skipped.
 *
 * The voiceprint and PIN get the stronger treatment because they are credentials:
 * everything else is a record of something that happened, but those two would let
 * someone impersonate the user to her own app.
 */
object KeystoreCrypto {

    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "guardian_angel_field_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val IV_BYTES = 12

    /**
     * Encrypts [plaintext], returning IV ‖ ciphertext.
     *
     * The IV is prepended rather than stored separately: GCM needs a unique IV per
     * message, and keeping it beside the bytes it belongs to removes any chance of the
     * two drifting apart across a schema migration.
     */
    fun encrypt(plaintext: ByteArray): ByteArray? = try {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plaintext)
        iv + encrypted
    } catch (e: Exception) {
        Log.e(TAG, "Encryption failed", e)
        null
    }

    /** Reverses [encrypt]. Returns null if the key is gone or the data was tampered with. */
    fun decrypt(payload: ByteArray): ByteArray? = try {
        require(payload.size > IV_BYTES) { "payload too short to contain an IV" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey(),
            GCMParameterSpec(GCM_TAG_BITS, payload, 0, IV_BYTES),
        )
        cipher.doFinal(payload, IV_BYTES, payload.size - IV_BYTES)
    } catch (e: Exception) {
        // Also the path when the user removed their screen lock and the key was
        // invalidated. Callers treat null as "re-enrol", never as a crash.
        Log.e(TAG, "Decryption failed", e)
        null
    }

    /** Convenience for float embeddings, which is what both callers actually store. */
    fun encryptFloats(values: FloatArray): ByteArray? {
        val buffer = java.nio.ByteBuffer.allocate(values.size * Float.SIZE_BYTES)
            .order(java.nio.ByteOrder.LITTLE_ENDIAN)
        values.forEach(buffer::putFloat)
        return encrypt(buffer.array())
    }

    fun decryptFloats(payload: ByteArray): FloatArray? {
        val bytes = decrypt(payload) ?: return null
        val buffer = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        return FloatArray(bytes.size / Float.SIZE_BYTES) { buffer.float }
    }

    private fun secretKey(): SecretKey {
        val keystore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keystore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let {
            return it.secretKey
        }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    // Deliberately *not* setUserAuthenticationRequired: Angel has to
                    // check a voiceprint while the phone is locked in a pocket, which is
                    // the entire use case. Requiring an unlock to read the key would
                    // disable hands-free activation exactly when it is needed.
                    .build()
            )
        }.generateKey()
    }
}
