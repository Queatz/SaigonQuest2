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

    @Test
    fun corsAllowsConfiguredHostsAndRejectsOthers() = testApplication {
        application {
            module(
                databaseHealth = DatabaseHealth { HealthStatus(true, "saigonquest", "3.12.0") },
                config = AppConfig(
                    corsAllowedHosts = parseCorsAllowedHosts("https://town.example.com,localhost:3000"),
                ),
            )
        }
        val allowed = client.get("/api/health") {
            header(HttpHeaders.Origin, "https://town.example.com")
        }
        assertEquals("https://town.example.com", allowed.headers[HttpHeaders.AccessControlAllowOrigin])

        val dev = client.get("/api/health") {
            header(HttpHeaders.Origin, "http://localhost:3000")
        }
        assertEquals("http://localhost:3000", dev.headers[HttpHeaders.AccessControlAllowOrigin])

        val denied = client.get("/api/health") {
            header(HttpHeaders.Origin, "https://evil.example")
        }
        assertEquals(null, denied.headers[HttpHeaders.AccessControlAllowOrigin])
    }
}
