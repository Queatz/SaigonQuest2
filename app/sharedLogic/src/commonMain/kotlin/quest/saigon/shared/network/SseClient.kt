package quest.saigon.shared.network

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import quest.saigon.shared.platform.TokenStorage

data class SseEvent(
    val event: String? = null,
    val data: String,
)

object SseParser {
    fun parseBlocks(buffer: String): Pair<List<SseEvent>, String> {
        val normalized = buffer.replace("\r\n", "\n").replace('\r', '\n')
        val parts = normalized.split("\n\n")
        if (parts.size <= 1) return emptyList<SseEvent>() to normalized
        val events = mutableListOf<SseEvent>()
        for (i in 0 until parts.size - 1) {
            val block = parts[i]
            if (block.isBlank()) continue
            var eventName: String? = null
            val dataLines = mutableListOf<String>()
            for (line in block.split('\n')) {
                if (line.startsWith(":")) continue
                when {
                    line.startsWith("event:") -> eventName = line.removePrefix("event:").trim()
                    line.startsWith("data:") -> {
                        val raw = line.removePrefix("data:")
                        dataLines += if (raw.startsWith(" ")) raw.substring(1) else raw
                    }
                }
            }
            if (dataLines.isEmpty()) continue
            val payload = dataLines.joinToString("\n").trim()
            if (payload.isBlank() || payload == "{}") continue
            events += SseEvent(event = eventName, data = payload)
        }
        return events to parts.last()
    }
}

class SseClient(
    private val httpClient: HttpClient,
    private val tokenStorage: TokenStorage,
    private val resolveUrl: (String) -> String,
    private val onUnauthorized: suspend () -> Unit = {},
    private val initialBackoffMs: Long = 1_200L,
    private val maxBackoffMs: Long = 10_000L,
) {
    fun events(
        path: String,
        onConnected: suspend (reconnected: Boolean) -> Unit = {},
    ): Flow<SseEvent> = flow {
        var everConnected = false
        var backoffMs = initialBackoffMs
        while (currentCoroutineContext().isActive) {
            var unauthorized = false
            try {
                val token = tokenStorage.readSessionToken()
                val url = resolveUrl(path)
                httpClient.prepareGet(url) {
                    header(HttpHeaders.Accept, "text/event-stream")
                    header(HttpHeaders.CacheControl, "no-cache")
                    if (!token.isNullOrBlank()) {
                        header(HttpHeaders.Authorization, "Bearer $token")
                    }
                }.execute { response ->
                    if (response.status == HttpStatusCode.Unauthorized) {
                        unauthorized = true
                        return@execute
                    }
                    if (!response.status.isSuccess()) {
                        return@execute
                    }
                    val reconnected = everConnected
                    everConnected = true
                    backoffMs = initialBackoffMs
                    onConnected(reconnected)

                    val channel: ByteReadChannel = response.bodyAsChannel()
                    val chunk = ByteArray(4096)
                    var buffer = ""
                    while (!channel.isClosedForRead && currentCoroutineContext().isActive) {
                        val read = channel.readAvailable(chunk, 0, chunk.size)
                        if (read < 0) break
                        if (read == 0) {
                            delay(25)
                            continue
                        }
                        buffer += chunk.decodeToString(startIndex = 0, endIndex = read)
                        val (parsed, remainder) = SseParser.parseBlocks(buffer)
                        buffer = remainder
                        for (event in parsed) {
                            emit(event)
                        }
                    }
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Throwable) {
                // Retry with backoff
            }
            if (unauthorized) {
                onUnauthorized()
                break
            }
            if (!currentCoroutineContext().isActive) break
            delay(backoffMs)
            backoffMs = (backoffMs * 2).coerceAtMost(maxBackoffMs)
        }
    }

    fun launchStream(
        scope: CoroutineScope,
        path: String,
        onConnected: suspend (reconnected: Boolean) -> Unit = {},
        onEvent: suspend (SseEvent) -> Unit,
    ): Job = scope.launch {
        events(path, onConnected).collect { event ->
            onEvent(event)
        }
    }
}
