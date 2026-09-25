package quest.saigon

import com.arangodb.ArangoDB
import org.slf4j.LoggerFactory

class ArangoDatabaseHealth(
    private val config: ArangoConfig,
    clientFactory: () -> ArangoDB = {
        ArangoDB.Builder()
            .host(config.host, config.port)
            .user(config.user)
            .password(config.password)
            .build()
    },
) : DatabaseHealth {
    private val log = LoggerFactory.getLogger(ArangoDatabaseHealth::class.java)
    private val client: ArangoDB by lazy(clientFactory)

    fun ensureDatabase() {
        val db = client.db(config.database)
        if (!db.exists()) {
            db.create()
            log.info("Created ArangoDB database '{}'", config.database)
        }
    }

    override fun check(): HealthStatus {
        return try {
            ensureDatabase()
            // /_api/version lives on _system. A normal database user cannot call it.
            client.db(config.database).query("RETURN 1", Long::class.java, emptyMap<String, Any>()).use { cursor ->
                if (!cursor.hasNext()) error("ArangoDB returned no rows")
            }
            HealthStatus(
                connected = true,
                database = config.database,
                version = runCatching { client.version.version }.getOrNull(),
            )
        } catch (error: Exception) {
            log.warn("ArangoDB is unavailable: {}", error.message)
            HealthStatus(
                connected = false,
                database = config.database,
                error = error.message,
            )
        }
    }
}
