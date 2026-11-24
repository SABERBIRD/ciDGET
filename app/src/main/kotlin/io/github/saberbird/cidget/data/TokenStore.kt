package io.github.saberbird.cidget.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.util.UUID

/** Whether a saved token may be used. `INVALID` after a 401, until the token is replaced. */
@Serializable
enum class TokenState { OK, INVALID }

/** One saved token (D23). [label] is a free name such as the account or organization; it is not a secret. */
@Serializable
private data class SavedToken(
    val id: String,
    val label: String,
    val token: String,
    val state: TokenState = TokenState.OK,
    val expiresAt: Instant? = null,
)

@Serializable
private data class TokenPayload(val tokens: List<SavedToken> = emptyList())

/** The pre-D23 single-token payload; still readable so an update keeps the token. */
@Serializable
private data class LegacyPayload(val token: String, val state: TokenState, val expiresAt: Instant? = null)

/** What the UI may know about a token: never the token itself, only its last 4 characters. */
data class TokenSummary(val id: String, val label: String, val last4: String, val expiresAt: Instant?, val state: TokenState)

/**
 * The encrypted token file (SECURITY.md) holding one or more tokens. The plaintext exists only inside the
 * methods below and in the caller's local variable; nothing here logs it or puts it in an exception. If the
 * file cannot be decrypted, the file and the key are deleted and the state becomes "no tokens".
 */
class TokenStore(
    private val file: File,
    private val keys: TokenKeyProvider,
) {
    private val lock = Any()
    private val json = Json { ignoreUnknownKeys = true }
    private val _summaries = MutableStateFlow<List<TokenSummary>>(emptyList())

    /** Summaries of all saved tokens, in the order they were added. */
    val summaries: StateFlow<List<TokenSummary>> = _summaries

    init {
        publish(read())
    }

    /** True when at least one token is saved, whatever its state. */
    fun hasToken(): Boolean = _summaries.value.isNotEmpty()

    /**
     * Id of the token a request for [id] uses: [id] if it exists, otherwise (no id, or the token was deleted)
     * the first `OK` token, otherwise the first token. Null when nothing is saved.
     */
    fun resolveId(id: String?): String? {
        val list = _summaries.value
        return (list.firstOrNull { it.id == id } ?: list.firstOrNull { it.state == TokenState.OK } ?: list.firstOrNull())?.id
    }

    /** State of the token [resolveId] picks, or null when nothing is saved. */
    fun stateOf(id: String?): TokenState? = resolveId(id)?.let { r -> _summaries.value.first { it.id == r }.state }

    /** The plaintext of the token for [id] (see [resolveId]) if it is not `INVALID`; this is what API requests use. */
    fun usableToken(id: String?): String? = synchronized(lock) {
        val resolved = resolveId(id) ?: return null
        read().tokens.firstOrNull { it.id == resolved }?.takeIf { it.state == TokenState.OK }?.token
    }

    /**
     * Saves [token] as `OK`. With [replaceId] that token's value is replaced (id and position kept); otherwise
     * a token with the same value is refreshed, or a new one is appended. Returns the token's id.
     */
    fun add(token: String, label: String, expiresAt: Instant?, replaceId: String? = null): String = synchronized(lock) {
        val payload = read()
        val index = payload.tokens.indexOfFirst { it.id == replaceId }.takeIf { it >= 0 }
            ?: payload.tokens.indexOfFirst { it.token == token }.takeIf { it >= 0 }
        val list = payload.tokens.toMutableList()
        val id: String
        if (index != null) {
            id = list[index].id
            list[index] = SavedToken(id, label.trim().ifEmpty { list[index].label }, token, TokenState.OK, expiresAt)
        } else {
            id = UUID.randomUUID().toString()
            list += SavedToken(id, label.trim(), token, TokenState.OK, expiresAt)
        }
        write(TokenPayload(list))
        id
    }

    /** Marks token [id] `INVALID` (after a 401). */
    fun markInvalid(id: String?) = synchronized(lock) {
        val resolved = resolveId(id) ?: return@synchronized
        val payload = read()
        write(TokenPayload(payload.tokens.map { if (it.id == resolved) it.copy(state = TokenState.INVALID) else it }))
    }

    /** Updates the expiry date seen in a response header. */
    fun updateExpiry(id: String?, expiresAt: Instant?) = synchronized(lock) {
        val resolved = resolveId(id) ?: return@synchronized
        val payload = read()
        if (payload.tokens.firstOrNull { it.id == resolved }?.expiresAt == expiresAt) return@synchronized
        write(TokenPayload(payload.tokens.map { if (it.id == resolved) it.copy(expiresAt = expiresAt) else it }))
    }

    /** Deletes token [id]; the file and key go when it was the last one. */
    fun remove(id: String) = synchronized(lock) {
        val rest = read().tokens.filter { it.id != id }
        if (rest.isEmpty()) delete() else write(TokenPayload(rest))
    }

    /** Deletes every token, the file and the key. */
    fun delete() = synchronized(lock) {
        wipe()
        publish(TokenPayload())
    }

    private fun write(payload: TokenPayload) {
        val bytes = json.encodeToString(TokenPayload.serializer(), payload).toByteArray(Charsets.UTF_8)
        val blob = TokenCodec.encrypt(keys.getOrCreate(), bytes)
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeBytes(blob)
        if (!tmp.renameTo(file)) {
            file.delete()
            check(tmp.renameTo(file)) { "cannot write token file" }
        }
        publish(payload)
    }

    private fun read(): TokenPayload {
        if (!file.exists()) return TokenPayload()
        return try {
            val text = TokenCodec.decrypt(keys.getOrCreate(), file.readBytes()).toString(Charsets.UTF_8)
            val obj: JsonObject = json.parseToJsonElement(text).jsonObject
            if ("tokens" in obj) {
                json.decodeFromString(TokenPayload.serializer(), text)
            } else {
                val legacy = json.decodeFromString(LegacyPayload.serializer(), text)
                TokenPayload(listOf(SavedToken(UUID.randomUUID().toString(), "", legacy.token, legacy.state, legacy.expiresAt)))
            }
        } catch (_: Exception) {
            // Corrupt file, lost key or bad tag: start clean. Never include the cause; it may hold data.
            wipe()
            publish(TokenPayload())
            TokenPayload()
        }
    }

    private fun wipe() {
        file.delete()
        File(file.parentFile, file.name + ".tmp").delete()
        keys.delete()
    }

    private fun publish(payload: TokenPayload) {
        _summaries.value = payload.tokens.map { TokenSummary(it.id, it.label, it.token.takeLast(4), it.expiresAt, it.state) }
    }
}
