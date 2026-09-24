package quest.saigon

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApplicationTest {
    @Test
    fun helloEndpoint() = testApplication {
        application {
            module(databaseHealth = DatabaseHealth { HealthStatus(true, "saigonquest", "3.12.0") })
        }
        val response = client.get("/api/hello")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("Hello, Ktor!", response.bodyAsText())
    }

    @Test
    fun healthOkWhenArangoIsUp() = testApplication {
        application {
            module(databaseHealth = DatabaseHealth { HealthStatus(true, "saigonquest", "3.12.0") })
        }
        val response = client.get("/api/health")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
        val body = response.bodyAsText()
        assertTrue(body.contains("\"status\":\"ok\""))
        assertTrue(body.contains("\"connected\":true"))
        assertTrue(body.contains("\"database\":\"saigonquest\""))
    }

    @Test
    fun healthDegradedWhenArangoIsDown() = testApplication {
        application {
            module(databaseHealth = DatabaseHealth {
                HealthStatus(false, "saigonquest", error = "Connection refused")
            })
        }
        val response = client.get("/api/health")
        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("\"status\":\"degraded\""))
        assertTrue(body.contains("Connection refused"))
    }
}
