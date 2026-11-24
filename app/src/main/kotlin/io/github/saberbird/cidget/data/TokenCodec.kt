package io.github.saberbird.cidget.data

import java.io.ByteArrayOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The `token.bin` byte layout (SECURITY.md): `version(1) | ivLen(1) | iv | ciphertext`, AES/GCM/NoPadding
 * with a 128-bit tag. Pure `javax.crypto`, so it is unit-tested on the JVM with an ordinary AES key; on
 * the device the key lives in the Android Keystore.
 */
internal object TokenCodec {
    private const val VERSION = 1
    private const val TAG_BITS = 128

    fun encrypt(key: SecretKey, plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val out = ByteArrayOutputStream()
        out.write(VERSION)
        out.write(iv.size)
        out.write(iv)
        out.write(cipher.doFinal(plain))
        return out.toByteArray()
    }

    /** Throws on any corruption (bad version, truncated data, wrong key, failed tag). Messages carry no data. */
    fun decrypt(key: SecretKey, blob: ByteArray): ByteArray {
        require(blob.size > 2 && blob[0].toInt() == VERSION) { "bad header" }
        val ivLen = blob[1].toInt() and 0xFF
        require(ivLen in 1..32 && blob.size > 2 + ivLen) { "bad iv" }
        val iv = blob.copyOfRange(2, 2 + ivLen)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        return cipher.doFinal(blob, 2 + ivLen, blob.size - 2 - ivLen)
    }
}
