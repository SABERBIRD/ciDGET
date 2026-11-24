package io.github.saberbird.cidget.data

import kotlinx.datetime.Instant
import java.io.File
import java.nio.file.Files
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** JVM tests of the file format and recovery with an ordinary AES key (the Keystore itself needs a device). */
class TokenStoreTest {
    private class FakeKeys : TokenKeyProvider {
        var key: SecretKey? = null
        var deletes = 0
        override fun getOrCreate(): SecretKey =
            key ?: KeyGenerator.getInstance("AES").apply { init(256) }.generateKey().also { key = it }
        override fun delete() { key = null; deletes++ }
    }

    private val dir: File = Files.createTempDirectory("tok").toFile()
    private val file = File(dir, "token.bin")
    private val keys = FakeKeys()
    private val fake = "github_pat_TEST_abcd"

    @Test fun roundTrip() {
        val store = TokenStore(file, keys)
        store.save(fake, Instant.parse("2026-12-24T15:32:55Z"))
        assertEquals(fake, store.usableToken())
        val reopened = TokenStore(file, keys)
        assertEquals(fake, reopened.usableToken())
        assertEquals("abcd", reopened.summary.value?.last4)
        assertEquals(Instant.parse("2026-12-24T15:32:55Z"), reopened.summary.value?.expiresAt)
    }

    @Test fun fileDoesNotContainPlaintext() {
        TokenStore(file, keys).save(fake, null)
        assertFalse(String(file.readBytes(), Charsets.ISO_8859_1).contains("github_pat"))
        assertEquals(1, file.readBytes()[0].toInt())
        assertEquals(12, file.readBytes()[1].toInt())
    }

    @Test fun invalidStateHidesTokenFromRequests() {
        val store = TokenStore(file, keys)
        store.save(fake, null)
        store.markInvalid()
        assertNull(store.usableToken())
        assertEquals(TokenState.INVALID, store.summary.value?.state)
        store.save("github_pat_TEST_new1", null)
        assertEquals("github_pat_TEST_new1", store.usableToken())
    }

    @Test fun corruptFileIsWipedAndTreatedAsNoToken() {
        val store = TokenStore(file, keys)
        store.save(fake, null)
        val bytes = file.readBytes()
        bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 0x55).toByte()
        file.writeBytes(bytes)
        assertNull(store.usableToken())
        assertFalse(file.exists())
        assertTrue(keys.deletes > 0)
        assertNull(store.summary.value)
        store.save(fake, null)
        assertEquals(fake, store.usableToken())
    }

    @Test fun truncatedAndGarbageFilesAreRecovered() {
        for (garbage in listOf(byteArrayOf(), byteArrayOf(1), byteArrayOf(9, 9, 9, 9), ByteArray(40) { 7 })) {
            file.writeBytes(garbage)
            val store = TokenStore(file, keys)
            assertNull(store.usableToken())
            assertFalse(file.exists())
        }
    }

    @Test fun lostKeyIsRecovered() {
        val store = TokenStore(file, keys)
        store.save(fake, null)
        keys.key = null // simulates a key that did not survive
        assertNull(store.usableToken())
        assertFalse(file.exists())
    }

    @Test fun deleteRemovesFileAndKey() {
        val store = TokenStore(file, keys)
        store.save(fake, null)
        store.delete()
        assertFalse(file.exists())
        assertNull(keys.key)
        assertNull(store.summary.value)
        assertNotNull(dir)
    }
}
