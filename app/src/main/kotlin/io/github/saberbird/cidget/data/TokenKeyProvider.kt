package io.github.saberbird.cidget.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** Supplies the AES key that protects the token file. */
interface TokenKeyProvider {
    /** Returns the key, creating it if needed. */
    fun getOrCreate(): SecretKey

    /** Deletes the key (used when the file is unreadable or the token is deleted). */
    fun delete()
}

/** Android Keystore key `cidget_token_v1` (SECURITY.md): AES-256, GCM, randomized IV, no user authentication. */
class KeystoreKeyProvider : TokenKeyProvider {
    private fun store(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    override fun getOrCreate(): SecretKey {
        (store().getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val spec = KeyGenParameterSpec.Builder(
            ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setKeySize(256)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(spec)
        return generator.generateKey()
    }

    override fun delete() {
        runCatching { store().deleteEntry(ALIAS) }
    }

    private companion object {
        const val ALIAS = "cidget_token_v1"
    }
}
