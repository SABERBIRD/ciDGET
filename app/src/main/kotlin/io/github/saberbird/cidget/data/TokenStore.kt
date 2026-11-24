package io.github.saberbird.cidget.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** Whether the saved token may be used. `INVALID` after a 401, until the token is replaced. */
@Serializable
enum class TokenState { OK, INVALID }

@Serializable
private data class TokenPayload(val token: String, val state: TokenState, val expiresAt: Instant? = null)

/** What the UI may know about the token: never the token itself, only its last 4 characters. */
data class TokenSummary(val last4: String, val expiresAt: Instant?, val state: TokenState)

/**
 * The encrypted token file (SECURITY.md). The plaintext exists only inside the methods below and in the
 * caller's local variable; nothing here logs it or puts it in an exception. If the file cannot be
 * decrypted, the file and the key are deleted and the state becomes "no token".
 */
class TokenStore(
    private val file: File,
    private val keys: TokenKeyProvider,
) {
    private val lock = Any()
    private val _summary = MutableStateFlow<TokenSummary?>(null)

    /** Current summary, or null when there is no token. */
    val summary: StateFlow<TokenSummary?> = _summary

    init {
        publish(read())
    }

    /** The token if one is saved and not `INVALID`; this is what API requests use. */
    fun usableToken(): String? = synchronized(lock) { read() }?.takeIf { it.state == TokenState.OK }?.token

    /** True when a token exists, whatever its state. */
    fun hasToken(): Boolean = _summary.value != null

    /** Saves [token] as `OK`, replacing any previous one. */
    fun save(token: String, expiresAt: Instant?) = synchronized(lock) {
        write(TokenPayload(token, TokenState.OK, expiresAt))
    }

    /** Marks the saved token `INVALID` (after a 401). */
    fun markInvalid() = synchronized(lock) {
        read()?.let { write(it.copy(state = TokenState.INVALID)) }
        Unit
    }

    /** Updates the expiry date seen in a response header. */
    fun updateExpiry(expiresAt: Instant?) = synchronized(lock) {
        read()?.takeIf { it.expiresAt != expiresAt }?.let { write(it.copy(expiresAt = expiresAt)) }
        Unit
    }

    /** Deletes the file and the key. */
    fun delete() = synchronized(lock) {
        wipe()
        publish(null)
    }

    private fun write(payload: TokenPayload) {
        val bytes = Json.encodeToString(TokenPayload.serializer(), payload).toByteArray(Charsets.UTF_8)
        val blob = TokenCodec.encrypt(keys.getOrCreate(), bytes)
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeBytes(blob)
        if (!tmp.renameTo(file)) {
            file.delete()
            check(tmp.renameTo(file)) { "cannot write token file" }
        }
        publish(payload)
    }

    private fun read(): TokenPayload? {
        if (!file.exists()) return null
        return try {
            val plain = TokenCodec.decrypt(keys.getOrCreate(), file.readBytes())
            Json.decodeFromString(TokenPayload.serializer(), plain.toString(Charsets.UTF_8))
        } catch (_: Exception) {
            // Corrupt file, lost key or bad tag: start clean. Never include the cause; it may hold data.
            wipe()
            publish(null)
            null
        }
    }

    private fun wipe() {
        file.delete()
        File(file.parentFile, file.name + ".tmp").delete()
        keys.delete()
    }

    private fun publish(payload: TokenPayload?) {
        _summary.value = payload?.let { TokenSummary(it.token.takeLast(4), it.expiresAt, it.state) }
    }
}
