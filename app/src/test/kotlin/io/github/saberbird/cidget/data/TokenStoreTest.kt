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
        val id = store.add(fake, "me", Instant.parse("2026-12-24T15:32:55Z"))
        assertEquals(fake, store.usableToken(id))
        val reopened = TokenStore(file, keys)
        assertEquals(fake, reopened.usableToken(id))
        val s = reopened.summaries.value.single()
        assertEquals("abcd", s.last4)
        assertEquals("me", s.label)
        assertEquals(Instant.parse("2026-12-24T15:32:55Z"), s.expiresAt)
    }

    @Test fun fileDoesNotContainPlaintext() {
        TokenStore(file, keys).add(fake, "", null)
        assertFalse(String(file.readBytes(), Charsets.ISO_8859_1).contains("github_pat"))
        assertEquals(1, file.readBytes()[0].toInt())
        assertEquals(12, file.readBytes()[1].toInt())
    }

    @Test fun invalidStateHidesTokenFromRequests() {
        val store = TokenStore(file, keys)
        val id = store.add(fake, "", null)
        store.markInvalid(id)
        assertNull(store.usableToken(id))
        assertEquals(TokenState.INVALID, store.summaries.value.single().state)
        store.add("github_pat_TEST_new1", "", null, replaceId = id)
        assertEquals("github_pat_TEST_new1", store.usableToken(id))
        assertEquals(1, store.summaries.value.size)
    }

    @Test fun severalTokensAreKeptAndResolvedById() {
        val store = TokenStore(file, keys)
        val a = store.add("github_pat_TEST_aaaa", "personal", null)
        val b = store.add("github_pat_TEST_bbbb", "org", null)
        assertEquals(2, store.summaries.value.size)
        assertEquals("github_pat_TEST_aaaa", store.usableToken(a))
        assertEquals("github_pat_TEST_bbbb", store.usableToken(b))
        // Unknown or missing id falls back to the first OK token.
        assertEquals("github_pat_TEST_aaaa", store.usableToken(null))
        assertEquals("github_pat_TEST_aaaa", store.usableToken("gone"))
        store.markInvalid(a)
        assertEquals("github_pat_TEST_bbbb", store.usableToken(null))
        assertNull(store.usableToken(a))
        assertEquals(TokenState.INVALID, store.stateOf(a))
        assertEquals(TokenState.OK, store.stateOf(b))
    }

    @Test fun sameValueIsNotAddedTwice() {
        val store = TokenStore(file, keys)
        val a = store.add(fake, "x", null)
        assertEquals(a, store.add(fake, "", null))
        assertEquals(1, store.summaries.value.size)
        assertEquals("x", store.summaries.value.single().label)
    }

    @Test fun removingOneKeepsTheOthersAndTheLastRemovesFileAndKey() {
        val store = TokenStore(file, keys)
        val a = store.add("github_pat_TEST_aaaa", "", null)
        val b = store.add("github_pat_TEST_bbbb", "", null)
        store.remove(a)
        assertEquals("github_pat_TEST_bbbb", TokenStore(file, keys).usableToken(b))
        store.remove(b)
        assertFalse(file.exists())
        assertNull(keys.key)
        assertFalse(store.hasToken())
    }

    @Test fun legacySingleTokenFileIsMigrated() {
        val legacy = """{"token":"$fake","state":"OK"}"""
        file.writeBytes(TokenCodec.encrypt(keys.getOrCreate(), legacy.toByteArray()))
        val store = TokenStore(file, keys)
        assertEquals(fake, store.usableToken(null))
        assertEquals(1, store.summaries.value.size)
    }

    @Test fun corruptFileIsWipedAndTreatedAsNoToken() {
        val store = TokenStore(file, keys)
        store.add(fake, "", null)
        val bytes = file.readBytes()
        bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 0x55).toByte()
        file.writeBytes(bytes)
        assertNull(store.usableToken(null))
        assertFalse(file.exists())
        assertTrue(keys.deletes > 0)
        assertTrue(store.summaries.value.isEmpty())
        val id = store.add(fake, "", null)
        assertEquals(fake, store.usableToken(id))
    }

    @Test fun truncatedAndGarbageFilesAreRecovered() {
        for (garbage in listOf(byteArrayOf(), byteArrayOf(1), byteArrayOf(9, 9, 9, 9), ByteArray(40) { 7 })) {
            file.writeBytes(garbage)
            val store = TokenStore(file, keys)
            assertNull(store.usableToken(null))
            assertFalse(file.exists())
        }
    }

    @Test fun lostKeyIsRecovered() {
        val store = TokenStore(file, keys)
        store.add(fake, "", null)
        keys.key = null // simulates a key that did not survive
        assertNull(store.usableToken(null))
        assertFalse(file.exists())
    }

    @Test fun deleteRemovesFileAndKey() {
        val store = TokenStore(file, keys)
        store.add(fake, "", null)
        store.delete()
        assertFalse(file.exists())
        assertNull(keys.key)
        assertTrue(store.summaries.value.isEmpty())
        assertNotNull(dir)
    }
}
