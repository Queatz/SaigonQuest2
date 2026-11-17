package quest.saigon.shared.platform

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.js.Js
import web.window.window
import kotlin.js.Date

class JsTokenStorage : TokenStorage {
    private var inMemoryToken: String? = null

    override suspend fun readSessionToken(): String? = inMemoryToken

    override suspend fun writeSessionToken(token: String?) {
        inMemoryToken = token?.takeIf { it.isNotBlank() }
    }

    override suspend fun readLanguage(): String =
        try {
            window.asDynamic().localStorage.getItem("sq_lang") as? String ?: "system"
        } catch (_: Throwable) {
            "system"
        }

    override suspend fun writeLanguage(preference: String) {
        try {
            window.asDynamic().localStorage.setItem("sq_lang", preference.ifBlank { "system" })
        } catch (_: Throwable) {
        }
    }
}

actual fun createPlatformTokenStorage(): TokenStorage = JsTokenStorage()

actual fun systemLanguageTag(): String =
    try {
        window.asDynamic().navigator.language as? String ?: "en"
    } catch (_: Throwable) {
        "en"
    }

actual fun createHttpClientEngine(): HttpClientEngine = Js.create()

actual fun defaultServerOrigin(): String =
    try {
        val raw = window.asDynamic().SAIGON_SERVER_ORIGIN as? String
        raw?.trim()?.trimEnd('/').orEmpty()
    } catch (_: Throwable) {
        ""
    }

actual fun currentTimeMillis(): Long = Date.now().toLong()
