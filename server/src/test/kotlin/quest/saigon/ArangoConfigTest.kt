package quest.saigon

import kotlin.test.Test
import kotlin.test.assertEquals

class ArangoConfigTest {
    @Test
    fun usesDefaultsWhenEnvIsEmpty() {
        val config = ArangoConfig.fromEnv { null }
        assertEquals(ArangoConfig.DEFAULT_HOST, config.host)
        assertEquals(ArangoConfig.DEFAULT_PORT, config.port)
        assertEquals(ArangoConfig.DEFAULT_USER, config.user)
        assertEquals(ArangoConfig.DEFAULT_PASSWORD, config.password)
        assertEquals(ArangoConfig.DEFAULT_DATABASE, config.database)
    }

    @Test
    fun readsOverridesFromEnv() {
        val env = mapOf(
            "ARANGO_HOST" to "arangodb",
            "ARANGO_PORT" to "8530",
            "ARANGO_USER" to "quest",
            "ARANGO_PASSWORD" to "secret",
            "ARANGO_DATABASE" to "quests",
        )
        val config = ArangoConfig.fromEnv(env::get)
        assertEquals("arangodb", config.host)
        assertEquals(8530, config.port)
        assertEquals("quest", config.user)
        assertEquals("secret", config.password)
        assertEquals("quests", config.database)
    }
}
