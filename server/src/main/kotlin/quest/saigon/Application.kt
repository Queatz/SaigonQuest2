package quest.saigon

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.singlePageApplication
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import org.slf4j.LoggerFactory
import quest.saigon.http.mediaRoutes
import quest.saigon.http.townApi
import quest.saigon.town.TownException
import quest.saigon.town.TownServices
import java.io.File

fun main() {
    val log = LoggerFactory.getLogger("quest.saigon.Application")
    val config = AppConfig.load()
    val health = ArangoDatabaseHealth(config.arango)
    val status = health.check()
    val services = if (status.connected) {
        try {
            TownServices.open(config)
        } catch (error: Exception) {
            log.warn("Could not open the town store: {}", error.message)
            null
        }
    } else {
        null
    }
    embeddedServer(Netty, port = config.port, host = config.bindHost) {
        module(health, services, config)
    }.start(wait = true)
}

fun Application.module(
    databaseHealth: DatabaseHealth = ArangoDatabaseHealth(ArangoConfig.fromEnv()).also { health ->
        health.check()
    },
    services: TownServices? = null,
    config: AppConfig = AppConfig.load(),
) {
    val town = services ?: if (databaseHealth is ArangoDatabaseHealth) {
        try {
            TownServices.open(config)
        } catch (error: Exception) {
            LoggerFactory.getLogger("quest.saigon.Application")
                .warn("Could not open the town store: {}", error.message)
            null
        }
    } else {
        null
    }

    install(CORS) {
        config.corsAllowedHosts.forEach { allowed ->
            allowHost(allowed.host, schemes = allowed.schemes)
        }
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Options)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowHeader("X-Saigon-Client")
        allowCredentials = true
        allowNonSimpleContentTypes = true
    }

    if (town != null) {
        intercept(ApplicationCallPipeline.Call) {
            try {
                proceed()
            } catch (error: TownException) {
                call.respondText(
                    """{"error":"${error.code}"}""",
                    ContentType.Application.Json,
                    statusFor(error.code),
                )
            }
        }
    }

    routing {
        route("/api") {
            get("/hello") {
                call.respondText(sayHello("Ktor"))
            }
            get("/health") {
                val status = databaseHealth.check()
                call.respondText(
                    status.toJson(),
                    ContentType.Application.Json,
                    if (status.connected) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable,
                )
            }
            if (town != null) {
                townApi(town)
            }
        }
        if (town != null) {
            mediaRoutes(town)
        }
        val spaDir = config.spaDir ?: System.getenv("SPA_DIR")?.takeIf { it.isNotBlank() }?.let(::File)
        if (spaDir != null && spaDir.isDirectory) {
            singlePageApplication {
                filesPath = spaDir.absolutePath
                defaultPage = "index.html"
            }
        }
    }
}

private fun statusFor(code: String): HttpStatusCode = when (code) {
    "unauthorized", "bad_credentials" -> HttpStatusCode.Unauthorized
    "forbidden", "account_paused" -> HttpStatusCode.Forbidden
    "not_found" -> HttpStatusCode.NotFound
    "unavailable", "qwen_not_configured" -> HttpStatusCode.ServiceUnavailable
    "rate_limited" -> HttpStatusCode.TooManyRequests
    "phone_taken", "avatar_busy", "music_busy" -> HttpStatusCode.Conflict
    "too_large" -> HttpStatusCode.PayloadTooLarge
    else -> HttpStatusCode.BadRequest
}
