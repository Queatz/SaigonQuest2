package quest.saigon.shared.platform

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO
import java.util.Locale

actual fun createPlatformTokenStorage(): TokenStorage = InMemoryTokenStorage()

actual fun systemLanguageTag(): String = Locale.getDefault().toLanguageTag()

actual fun createHttpClientEngine(): HttpClientEngine = CIO.create()

actual fun defaultServerOrigin(): String = ""

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

actual val includeCookieHeaderOnRequests: Boolean = true

actual val clientPlatform: String = "jvm"
