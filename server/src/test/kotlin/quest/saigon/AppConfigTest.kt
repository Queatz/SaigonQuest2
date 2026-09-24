package quest.saigon

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AppConfigTest {
    @Test
    fun corsDefaultsKeepLocalDevHosts() {
        assertEquals(
            listOf(
                CorsAllowedHost("localhost:3000"),
                CorsAllowedHost("127.0.0.1:3000"),
            ),
            parseCorsAllowedHosts(null),
        )
        assertEquals(defaultCorsHosts(), parseCorsAllowedHosts("  "))
    }

    @Test
    fun corsParsesOriginsSchemesAndPorts() {
        val hosts = parseCorsAllowedHosts("https://Town.Example.com:443, http://127.0.0.1:3000")
        assertEquals(CorsAllowedHost("town.example.com", listOf("https")), hosts[0])
        assertEquals(CorsAllowedHost("127.0.0.1:3000", listOf("http")), hosts[1])
    }

    @Test
    fun corsRejectsUnsupportedSchemes() {
        assertFailsWith<IllegalArgumentException> {
            parseCorsHost("ftp://town.example.com")
        }
    }

    @Test
    fun loadReadsProductionOverridesFromEnv() {
        val env = mapOf(
            "PORT" to "9090",
            "BIND_HOST" to "127.0.0.1",
            "CORS_ALLOWED_HOSTS" to "https://town.example.com",
            "COOKIE_SAMESITE" to "none",
            "COOKIE_SECURE" to "true",
            "TRUST_PROXY" to "true",
        )
        val config = AppConfig.load(env::get, File("missing.env"))
        assertEquals(9090, config.port)
        assertEquals("127.0.0.1", config.bindHost)
        assertEquals(listOf(CorsAllowedHost("town.example.com", listOf("https"))), config.corsAllowedHosts)
        assertEquals("None", config.cookieSameSite)
        assertEquals(true, config.cookieSecure)
        assertEquals(true, config.trustProxy)
    }
}
