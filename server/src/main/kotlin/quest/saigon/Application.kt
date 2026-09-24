package quest.saigon

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.File

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module(
    databaseHealth: DatabaseHealth = ArangoDatabaseHealth(ArangoConfig.fromEnv()).also { health ->
        health.check()
    },
) {
    install(CORS) {
        allowHost("localhost:3000")
        allowHost("127.0.0.1:3000")
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Options)
        allowHeader(HttpHeaders.ContentType)
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
        }

        val spaDir = System.getenv("SPA_DIR")?.takeIf { it.isNotBlank() }?.let(::File)
        if (spaDir != null && spaDir.isDirectory) {
            singlePageApplication {
                filesPath = spaDir.absolutePath
                defaultPage = "index.html"
            }
        }
    }
}
