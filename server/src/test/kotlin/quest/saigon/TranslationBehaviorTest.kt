package quest.saigon

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import quest.saigon.ai.MusicRender
import quest.saigon.ai.TranslationPrompts
import quest.saigon.auth.RateLimiter
import quest.saigon.media.UploadStore
import quest.saigon.town.ActivityRecord
import quest.saigon.town.AvatarService
import quest.saigon.town.InMemoryTownRepository
import quest.saigon.town.MusicService
import quest.saigon.town.TownServices
import quest.saigon.town.TranslationService
import quest.saigon.town.Translator
import java.io.File
import java.util.concurrent.Executor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TranslationBehaviorTest {
    private val mapper = ObjectMapper()

    @Test
    fun promptParserPreservesBlankSourcesAndRejectsBlankTranslations() {
        val fields = mapOf("name" to "Coffee walk", "description" to "")
        val valid = TranslationPrompts.parse(
            """{"en":{"name":"Coffee walk","description":""},"vi":{"name":"Đi cà phê","description":""}}""",
            fields,
            TranslationService.LOCALES,
        )
        assertEquals("", valid.getValue("en").getValue("description"))

        assertFailsWith<IllegalArgumentException> {
            TranslationPrompts.parse(
                """{"en":{"name":"","description":""},"vi":{"name":"Đi cà phê","description":""}}""",
                fields,
                TranslationService.LOCALES,
            )
        }
    }

    @Test
    fun createsLocalizesEditsAndKeepsChatSnapshotCanonical() = testApplication {
        var calls = 0
        val translation = TranslationService(
            translator = Translator { fields, locales ->
                calls++
                locales.associateWith { locale -> fields.mapValues { (field, value) -> "$locale:$value" } }
            },
            configured = true,
            executor = Executor { it.run() },
        )
        val services = services({ 1_700_000_000_000L }, translation)
        application { module(databaseHealth = { HealthStatus(true, "saigonquest", "3") }, services) }
        val author = createClient { install(HttpCookies) }
        val viewer = createClient { install(HttpCookies) }
        register(author, "Linh", "0905550301")
        register(viewer, "Minh", "0905550302")
        val viewerId = mapper.readTree(viewer.get("/api/me").bodyAsText()).path("id").asText()

        val created = author.post("/api/activities") {
            contentType(ContentType.Application.Json)
            setBody("""{"emoji":"☕","name":"Coffee walk","description":"Meet at the workshop","locationName":"Workshop","interested":true}""")
        }
        assertEquals(HttpStatusCode.Created, created.status)
        val activity = mapper.readTree(created.bodyAsText())
        val activityId = activity.path("id").asText()
        assertEquals("Coffee walk", activity.path("name").asText())
        assertEquals("ready", activity.path("translations").path("status").asText())
        assertEquals("en:Coffee walk", activity.path("translations").path("en").path("name").asText())
        assertEquals("vi:Meet at the workshop", activity.path("translations").path("vi").path("description").asText())
        assertFalse(activity.path("translations").has("sourceHash"))
        assertEquals(1, calls)
        val listed = mapper.readTree(author.get("/api/activities").bodyAsText()).path("activities")
        assertEquals("en:Coffee walk", listed.path(0).path("translations").path("en").path("name").asText())
        val mine = mapper.readTree(author.get("/api/me/activities").bodyAsText()).path("activities")
        assertEquals("vi:Coffee walk", mine.path(0).path("translations").path("vi").path("name").asText())

        val override = author.patch("/api/activities/$activityId") {
            contentType(ContentType.Application.Json)
            setBody(
                """{"emoji":"☕","name":"Coffee walk","description":"Meet at the workshop","hostUserId":"","schedule":[],"translations":{"en":{"name":"Coffee in English","description":"An English description"},"vi":{"name":"Cà phê","description":"Mô tả tiếng Việt"}}}""",
            )
        }
        assertEquals(HttpStatusCode.OK, override.status)
        val overridden = mapper.readTree(override.bodyAsText())
        assertEquals("Coffee in English", overridden.path("translations").path("en").path("name").asText())
        assertEquals(1, calls)

        val changed = author.patch("/api/activities/$activityId") {
            contentType(ContentType.Application.Json)
            setBody(
                """{"emoji":"🍵","name":"Tea walk","description":"A calm break","hostUserId":"","schedule":[],"translations":{"en":{"name":"ignored","description":"ignored"},"vi":{"name":"ignored","description":"ignored"}}}""",
            )
        }
        assertEquals(HttpStatusCode.OK, changed.status)
        val changedBody = mapper.readTree(changed.bodyAsText())
        assertEquals("Tea walk", changedBody.path("name").asText())
        assertEquals("en:Tea walk", changedBody.path("translations").path("en").path("name").asText())
        assertEquals(2, calls)

        viewer.post("/api/activities/$activityId/join")
        val chat = author.post("/api/activities/$activityId/chat") {
            contentType(ContentType.Application.Json)
            setBody("""{"userIds":["$viewerId"]}""")
        }
        assertEquals(HttpStatusCode.Created, chat.status)
        val chatId = mapper.readTree(chat.bodyAsText()).path("id").asText()
        val messages = mapper.readTree(author.get("/api/chats/$chatId/messages").bodyAsText()).path("messages")
        assertEquals("Tea walk", messages.path(0).path("attachment").path("activityName").asText())
    }

    @Test
    fun getRetriesMissingAndFailedTranslationsAfterCooldownAndClipsOutput() = testApplication {
        var now = 1_700_000_000_000L
        var calls = 0
        val translation = TranslationService(
            translator = Translator { fields, locales ->
                calls++
                if (calls == 1) error("temporary failure")
                locales.associateWith { locale ->
                    mapOf(
                        "name" to "${locale}:" + "n".repeat(Limits.ACTIVITY_NAME_MAX + 10),
                        "description" to "${locale}:" + "d".repeat(Limits.ACTIVITY_DESCRIPTION_MAX + 10),
                    )
                }
            },
            configured = true,
            executor = Executor { it.run() },
            clock = { now },
        )
        val services = services({ now }, translation)
        val activity = services.repo.insertActivity(
            ActivityRecord(
                id = "",
                createdBy = "missing-owner",
                emoji = "☕",
                name = "Coffee walk",
                description = "Meet at the workshop",
                locationName = "Workshop",
                createdAt = now,
            ),
        )
        application { module(databaseHealth = { HealthStatus(true, "saigonquest", "3") }, services) }
        val client = createClient { install(HttpCookies) }

        val failed = mapper.readTree(client.get("/api/activities/${activity.id}").bodyAsText())
        assertEquals("failed", failed.path("translations").path("status").asText())
        assertEquals(1, calls)

        val cooledDown = mapper.readTree(client.get("/api/activities/${activity.id}").bodyAsText())
        assertEquals("failed", cooledDown.path("translations").path("status").asText())
        assertEquals(1, calls)

        now += TranslationService.FAILED_COOLDOWN_MILLIS + 1
        val retried = mapper.readTree(client.get("/api/activities/${activity.id}").bodyAsText())
        assertEquals("ready", retried.path("translations").path("status").asText())
        assertEquals(Limits.ACTIVITY_NAME_MAX, retried.path("translations").path("en").path("name").asText().length)
        assertEquals(Limits.ACTIVITY_DESCRIPTION_MAX, retried.path("translations").path("vi").path("description").asText().length)
        assertEquals(2, calls)
    }

    @Test
    fun blankSourceDescriptionRemainsBlankInReadyTranslations() = testApplication {
        val translation = TranslationService(
            translator = Translator { fields, locales ->
                locales.associateWith { locale ->
                    fields.mapValues { (field, value) -> if (value.isBlank()) "" else "$locale:$field" }
                }
            },
            configured = true,
            executor = Executor { it.run() },
        )
        val services = services({ 1_700_000_000_000L }, translation)
        application { module(databaseHealth = { HealthStatus(true, "saigonquest", "3") }, services) }
        val author = createClient { install(HttpCookies) }
        register(author, "Linh", "0905550305")

        val created = author.post("/api/activities") {
            contentType(ContentType.Application.Json)
            setBody("""{"emoji":"☕","name":"Coffee walk","description":"","locationName":"Workshop"}""")
        }
        val activity = mapper.readTree(created.bodyAsText())
        assertEquals("ready", activity.path("translations").path("status").asText())
        assertEquals("", activity.path("translations").path("en").path("description").asText())
        assertEquals("", activity.path("translations").path("vi").path("description").asText())
    }

    @Test
    fun lateTranslationDoesNotReplaceAuthorOverrides() = testApplication {
        val queued = mutableListOf<Runnable>()
        val translation = TranslationService(
            translator = Translator { fields, locales ->
                locales.associateWith { locale -> fields.mapValues { (_, value) -> "$locale:$value" } }
            },
            configured = true,
            executor = Executor { queued += it },
        )
        val services = services({ 1_700_000_000_000L }, translation)
        application { module(databaseHealth = { HealthStatus(true, "saigonquest", "3") }, services) }
        val author = createClient { install(HttpCookies) }
        register(author, "Linh", "0905550303")

        val created = author.post("/api/activities") {
            contentType(ContentType.Application.Json)
            setBody("""{"emoji":"☕","name":"Coffee walk","description":"Meet up","locationName":"Workshop"}""")
        }
        val activityId = mapper.readTree(created.bodyAsText()).path("id").asText()
        assertEquals("pending", mapper.readTree(created.bodyAsText()).path("translations").path("status").asText())
        assertEquals(1, queued.size)

        val edited = author.patch("/api/activities/$activityId") {
            contentType(ContentType.Application.Json)
            setBody(
                """{"emoji":"☕","name":"Coffee walk","description":"Meet up","hostUserId":"","schedule":[],"translations":{"en":{"name":"Author English","description":"English override"},"vi":{"name":"Tác giả","description":"Bản dịch"}}}""",
            )
        }
        assertEquals("Author English", mapper.readTree(edited.bodyAsText()).path("translations").path("en").path("name").asText())
        queued.single().run()

        val afterLateJob = mapper.readTree(author.get("/api/activities/$activityId").bodyAsText())
        assertEquals("Author English", afterLateJob.path("translations").path("en").path("name").asText())
        assertEquals("Bản dịch", afterLateJob.path("translations").path("vi").path("description").asText())
    }

    @Test
    fun sourceEditWhileTranslationRunsDiscardsStaleResult() = testApplication {
        val queued = mutableListOf<Runnable>()
        val translation = TranslationService(
            translator = Translator { fields, locales ->
                locales.associateWith { locale -> fields.mapValues { (_, value) -> "$locale:$value" } }
            },
            configured = true,
            executor = Executor { queued += it },
        )
        val services = services({ 1_700_000_000_000L }, translation)
        application { module(databaseHealth = { HealthStatus(true, "saigonquest", "3") }, services) }
        val author = createClient { install(HttpCookies) }
        register(author, "Linh", "0905550304")

        val created = author.post("/api/activities") {
            contentType(ContentType.Application.Json)
            setBody("""{"emoji":"☕","name":"Coffee walk","description":"Meet up","locationName":"Workshop"}""")
        }
        val activityId = mapper.readTree(created.bodyAsText()).path("id").asText()
        assertEquals(1, queued.size)

        val changed = author.patch("/api/activities/$activityId") {
            contentType(ContentType.Application.Json)
            setBody("""{"emoji":"🍵","name":"Tea walk","description":"A new plan","hostUserId":"","schedule":[]}""")
        }
        assertEquals("pending", mapper.readTree(changed.bodyAsText()).path("translations").path("status").asText())
        assertEquals(2, queued.size)

        queued[0].run()
        val afterStaleResult = mapper.readTree(author.get("/api/activities/$activityId").bodyAsText())
        assertEquals("Tea walk", afterStaleResult.path("name").asText())
        assertEquals("pending", afterStaleResult.path("translations").path("status").asText())
        assertTrue(afterStaleResult.path("translations").path("en").path("name").asText().isEmpty())

        queued[1].run()
        val afterCurrentResult = mapper.readTree(author.get("/api/activities/$activityId").bodyAsText())
        assertEquals("en:Tea walk", afterCurrentResult.path("translations").path("en").path("name").asText())
    }

    private suspend fun register(client: io.ktor.client.HttpClient, name: String, phone: String) {
        val response = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"$name","phone":"$phone","password":"secretpass"}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    private fun services(clock: () -> Long, translations: TranslationService): TownServices {
        val repo = InMemoryTownRepository()
        val uploads = UploadStore(File.createTempFile("sq-translations", "").apply {
            delete()
            mkdirs()
        })
        return TownServices(
            repo = repo,
            uploads = uploads,
            avatars = AvatarService(
                repo = repo,
                uploads = uploads,
                renderer = { _, _ -> byteArrayOf(1) },
                configured = false,
                executor = { it.run() },
                clock = clock,
            ),
            music = MusicService(
                repo = repo,
                uploads = uploads,
                renderer = { _, _, _ -> MusicRender(byteArrayOf(1), "Song") },
                configured = false,
                executor = { it.run() },
                clock = clock,
            ),
            config = AppConfig(uploadDir = uploads.root, qwenApiKey = null),
            clock = clock,
            contactLimiter = RateLimiter(1, 60_000, clock),
            translations = translations,
        )
    }
}