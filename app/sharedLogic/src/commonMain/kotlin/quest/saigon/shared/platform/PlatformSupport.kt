package quest.saigon.shared.platform

import io.ktor.client.engine.HttpClientEngine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface TokenStorage {
    suspend fun readSessionToken(): String?
    suspend fun writeSessionToken(token: String?)
    suspend fun readLanguage(): String
    suspend fun writeLanguage(preference: String)
    suspend fun readFcmToken(): String? = null
    suspend fun writeFcmToken(token: String?) {}
}

class InMemoryTokenStorage(
    initialToken: String? = null,
    initialLanguage: String = "system",
    initialFcmToken: String? = null,
) : TokenStorage {
    private val mutex = Mutex()
    private var sessionToken: String? = initialToken
    private var language: String = initialLanguage
    private var fcmToken: String? = initialFcmToken

    override suspend fun readSessionToken(): String? = mutex.withLock { sessionToken }

    override suspend fun writeSessionToken(token: String?) {
        mutex.withLock { sessionToken = token?.takeIf { it.isNotBlank() } }
    }

    override suspend fun readLanguage(): String = mutex.withLock { language }

    override suspend fun writeLanguage(preference: String) {
        mutex.withLock { language = preference.ifBlank { "system" } }
    }

    override suspend fun readFcmToken(): String? = mutex.withLock { fcmToken }

    override suspend fun writeFcmToken(token: String?) {
        mutex.withLock { fcmToken = token?.takeIf { it.isNotBlank() } }
    }
}

expect fun createPlatformTokenStorage(): TokenStorage

expect fun systemLanguageTag(): String

expect fun createHttpClientEngine(): HttpClientEngine

expect fun defaultServerOrigin(): String

expect fun currentTimeMillis(): Long

expect val includeCookieHeaderOnRequests: Boolean
