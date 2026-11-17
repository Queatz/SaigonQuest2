package quest.saigon.shared

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import quest.saigon.shared.network.SseClient
import quest.saigon.shared.network.SseParser
import quest.saigon.shared.platform.InMemoryTokenStorage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SseClientTest {
    @Test
    fun parserHandlesMultiChunkBoundariesHeartbeatsAndComments() {
        val chunk1 = "event: ready\ndata: {}\n\n: ping\n\ndata: {\"id\":\"n1\","
        val (events1, rem1) = SseParser.parseBlocks(chunk1)
        assertTrue(events1.isEmpty())

        val chunk2 = rem1 + "\"type\":\"post_comment\"}\r\n\r\ndata: {\"id\":\"n2\"}\n\n"
        val (events2, rem2) = SseParser.parseBlocks(chunk2)
        assertEquals(2, events2.size)
        assertEquals("""{"id":"n1","type":"post_comment"}""", events2[0].data)
        assertEquals("""{"id":"n2"}""", events2[1].data)
        assertEquals("", rem2)
    }

    @Test
    fun sseClientReconnectsAfterStreamDropsAndStopsOnUnauthorized() = runTest {
        var attempt = 0
        var unauthorizedTriggered = false
        val reconnectFlags = mutableListOf<Boolean>()
        val engine = MockEngine { request ->
            assertEquals("Bearer tok123", request.headers[HttpHeaders.Authorization])
            attempt += 1
            when (attempt) {
                1 -> respond(
                    content = ByteReadChannel("event: ready\ndata: {}\n\ndata: {\"id\":\"1\"}\n\n"),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Text.EventStream.toString()),
                )
                2 -> respond(
                    content = ByteReadChannel(": ping\n\ndata: {\"id\":\"2\"}\n\n"),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Text.EventStream.toString()),
                )
                else -> respond(
                    content = """{"error":"unauthorized"}""",
                    status = HttpStatusCode.Unauthorized,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            }
        }
        val storage = InMemoryTokenStorage(initialToken = "tok123")
        val client = SseClient(
            httpClient = HttpClient(engine),
            tokenStorage = storage,
            resolveUrl = { "https://saigon.town$it" },
            onUnauthorized = { unauthorizedTriggered = true },
            initialBackoffMs = 1L,
            maxBackoffMs = 2L,
        )

        val collected = client.events("/api/notifications/stream") { reconnected ->
            reconnectFlags += reconnected
        }.toList()

        assertEquals(listOf("""{"id":"1"}""", """{"id":"2"}"""), collected.map { it.data })
        assertEquals(listOf(false, true), reconnectFlags)
        assertTrue(unauthorizedTriggered)
    }

    @Test
    fun sseClientCanCollectFirstEventsFromStream() = runTest {
        val engine = MockEngine {
            respond(
                content = ByteReadChannel("data: {\"id\":\"a\"}\n\ndata: {\"id\":\"b\"}\n\n"),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Text.EventStream.toString()),
            )
        }
        val client = SseClient(
            httpClient = HttpClient(engine),
            tokenStorage = InMemoryTokenStorage(),
            resolveUrl = { it },
            initialBackoffMs = 1L,
        )
        val firstTwo = client.events("/api/notifications/stream").take(2).toList()
        assertEquals(2, firstTwo.size)
        assertEquals("""{"id":"a"}""", firstTwo[0].data)
        assertEquals("""{"id":"b"}""", firstTwo[1].data)
    }
}
