package quest.saigon

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.delete
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
import quest.saigon.auth.RateLimiter
import quest.saigon.media.UploadStore
import quest.saigon.town.AvatarService
import quest.saigon.town.ChatType
import quest.saigon.town.InMemoryTownRepository
import quest.saigon.town.MusicService
import quest.saigon.town.TownServices
import quest.saigon.town.UserPhotoRecord
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ActivityBehaviorTest {
    private val mapper = ObjectMapper()

    @Test
    fun activitiesCanBeCreatedListedJoinedAndDeleted() = testApplication {
        var now = 1_700_000_000_000L
        val services = services({ now })
        application { module(databaseHealth = { HealthStatus(true, "saigonquest", "3") }, services) }
        val linh = createClient { install(HttpCookies) }
        val minh = createClient { install(HttpCookies) }
        val an = createClient { install(HttpCookies) }
        register(linh, "Linh", "0905550201")
        register(minh, "Minh", "0905550202")
        register(an, "An", "0905550203")
        val linhId = mapper.readTree(linh.get("/api/me").bodyAsText()).path("id").asText()
        val minhId = mapper.readTree(minh.get("/api/me").bodyAsText()).path("id").asText()
        val anId = mapper.readTree(an.get("/api/me").bodyAsText()).path("id").asText()

        val created = linh.post("/api/activities") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "emoji": "☕",
                  "name": "Coffee walk",
                  "description": "Hang out at The Workshop",
                  "locationName": "The Workshop",
                  "locationId": "",
                  "placeId": "ChIJCoffee",
                  "lat": 10.77,
                  "lng": 106.70,
                  "hostUserId": "$minhId",
                  "interested": true,
                  "schedule": [
                    {"dayOfWeek": 1, "startMinutes": 540, "endMinutes": 720}
                  ]
                }
                """.trimIndent(),
            )
        }
        assertEquals(HttpStatusCode.Created, created.status)
        val activity = mapper.readTree(created.bodyAsText())
        val activityId = activity.path("id").asText()
        assertEquals("Coffee walk", activity.path("name").asText())
        assertFalse(activity.has("translations"))
        assertEquals("☕", activity.path("emoji").asText())
        assertTrue(activity.path("interested").asBoolean())
        assertEquals(1, activity.path("interestedCount").asInt())
        assertEquals(minhId, activity.path("hostUserId").asText())
        assertEquals(1, activity.path("schedule").size())
        assertEquals(540, activity.path("schedule").path(0).path("startMinutes").asInt())
        services.repo.insertUserPhoto(
            UserPhotoRecord(
                id = "activity-preview-photo",
                userId = linhId,
                photoPath = "activities/preview.jpg",
                createdAt = now,
                activityId = activityId,
            ),
        )

        val listed = mapper.readTree(
            linh.get("/api/activities?name=The%20Workshop&placeId=ChIJCoffee").bodyAsText(),
        ).path("activities")
        assertEquals(1, listed.size())
        assertEquals(activityId, listed.path(0).path("id").asText())
        assertEquals(1, listed.path(0).path("interestedPeople").size())
        assertEquals("/media/activities/preview.jpg", listed.path(0).path("photoUrls").path(0).asText())

        val linhActivities = mapper.readTree(linh.get("/api/users/$linhId/activities").bodyAsText()).path("activities")
        assertEquals(1, linhActivities.size())
        assertEquals(activityId, linhActivities.path(0).path("id").asText())
        assertEquals(HttpStatusCode.NotFound, linh.get("/api/users/missing-user/activities").status)

        val invalidSchedule = linh.post("/api/activities") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {
                  "name": "Broken hours",
                  "locationName": "The Workshop",
                  "schedule": [{"dayOfWeek": 2, "startMinutes": 720, "endMinutes": 540}]
                }
                """.trimIndent(),
            )
        }
        assertEquals(HttpStatusCode.BadRequest, invalidSchedule.status)
        assertEquals("invalid_activity_schedule", mapper.readTree(invalidSchedule.bodyAsText()).path("error").asText())

        now += 1_000
        val later = linh.post("/api/activities") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Later hang","locationName":"Saigon","interested":false}""")
        }
        assertEquals(HttpStatusCode.Created, later.status)
        val laterId = mapper.readTree(later.bodyAsText()).path("id").asText()
        assertEquals(0, mapper.readTree(later.bodyAsText()).path("interestedCount").asInt())

        minh.post("/api/activities/$activityId/join")
        an.post("/api/activities/$activityId/join")
        an.post("/api/activities/$laterId/join")
        val afterJoin = mapper.readTree(linh.get("/api/activities/$activityId").bodyAsText())
        assertEquals(3, afterJoin.path("interestedCount").asInt())
        assertEquals(3, afterJoin.path("interestedPeople").size())

        val firstProfilePage = mapper.readTree(an.get("/api/users/$anId/activities?limit=1").bodyAsText())
        assertEquals(1, firstProfilePage.path("activities").size())
        assertTrue(firstProfilePage.path("nextCursor").asText().isNotBlank())
        val secondProfilePage = mapper.readTree(
            an.get("/api/users/$anId/activities?limit=1&cursor=${firstProfilePage.path("nextCursor").asText()}").bodyAsText(),
        )
        assertEquals(1, secondProfilePage.path("activities").size())
        assertNotEquals(
            firstProfilePage.path("activities").path(0).path("id").asText(),
            secondProfilePage.path("activities").path(0).path("id").asText(),
        )
        assertTrue(secondProfilePage.path("nextCursor").isNull || secondProfilePage.path("nextCursor").asText().isBlank())

        val discover = mapper.readTree(linh.get("/api/activities").bodyAsText()).path("activities")
        assertEquals(activityId, discover.path(0).path("id").asText())
        assertEquals(laterId, discover.path(1).path("id").asText())

        val events = mapper.readTree(linh.get("/api/events").bodyAsText()).path("locations")
        val coffee = (0 until events.size()).map { events.path(it) }.firstOrNull { it.path("placeId").asText() == "ChIJCoffee" }
        if (coffee != null) assertTrue(coffee.path("hasActivities").asBoolean())

        val mine = mapper.readTree(linh.get("/api/me/activities").bodyAsText()).path("activities")
        assertEquals(2, mine.size())

        val patched = linh.patch("/api/activities/$activityId") {
            contentType(ContentType.Application.Json)
            setBody("""{"emoji":"🍵","name":"Tea walk","description":"Calm hang","hostUserId":"","schedule":[]}""")
        }
        assertEquals(HttpStatusCode.OK, patched.status)
        val patchedBody = mapper.readTree(patched.bodyAsText())
        assertEquals("Tea walk", patchedBody.path("name").asText())
        assertTrue(patchedBody.path("host").isNull || patchedBody.path("host").isMissingNode)
        assertEquals(0, patchedBody.path("schedule").size())

        val forbidden = minh.patch("/api/activities/$activityId") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Nope","locationName":"The Workshop"}""")
        }
        assertEquals(HttpStatusCode.NotFound, forbidden.status)

        val left = an.post("/api/activities/$activityId/leave")
        assertEquals(HttpStatusCode.OK, left.status)
        assertFalse(mapper.readTree(left.bodyAsText()).path("interested").asBoolean())

        val deleted = linh.delete("/api/activities/$laterId")
        assertEquals(HttpStatusCode.OK, deleted.status)
        assertEquals(HttpStatusCode.NotFound, linh.get("/api/activities/$laterId").status)

        minh.post("/api/activities/$activityId/join")
        val chat = linh.post("/api/activities/$activityId/chat") {
            contentType(ContentType.Application.Json)
            setBody("""{"userIds":["$minhId"]}""")
        }
        assertEquals(HttpStatusCode.Created, chat.status)
        val chatBody = mapper.readTree(chat.bodyAsText())
        assertEquals(ChatType.DIRECT, chatBody.path("type").asText())
        val chatId = chatBody.path("id").asText()
        val messages = mapper.readTree(linh.get("/api/chats/$chatId/messages").bodyAsText()).path("messages")
        val last = messages.path(messages.size() - 1)
        assertEquals("activity", last.path("attachment").path("type").asText())
        assertEquals(activityId, last.path("attachment").path("activityId").asText())
        assertEquals("Tea walk", last.path("attachment").path("activityName").asText())

        an.post("/api/activities/$activityId/join")
        val group = linh.post("/api/activities/$activityId/chat") {
            contentType(ContentType.Application.Json)
            setBody("""{"userIds":["$minhId","$anId"]}""")
        }
        assertEquals(HttpStatusCode.Created, group.status)
        assertEquals(ChatType.GROUP, mapper.readTree(group.bodyAsText()).path("type").asText())
        assertNotEquals(chatId, mapper.readTree(group.bodyAsText()).path("id").asText())

        val emptyChat = linh.post("/api/activities/$activityId/chat") {
            contentType(ContentType.Application.Json)
            setBody("""{"userIds":[]}""")
        }
        assertEquals(HttpStatusCode.BadRequest, emptyChat.status)
        assertEquals("no_chat_members", mapper.readTree(emptyChat.bodyAsText()).path("error").asText())

        val removed = linh.delete("/api/activities/$activityId")
        assertEquals(HttpStatusCode.OK, removed.status)
        assertEquals(HttpStatusCode.NotFound, linh.get("/api/activities/$activityId").status)
        assertEquals(0, mapper.readTree(linh.get("/api/activities").bodyAsText()).path("activities").size())
    }

    @Test
    fun activityEmojiCanBeSuggested() = testApplication {
        val services = services({ 1_700_000_000_000L })
        application { module(databaseHealth = { HealthStatus(true, "saigonquest", "3") }, services) }
        val linh = createClient { install(HttpCookies) }
        register(linh, "Linh", "0905550204")
        val response = linh.post("/api/activities/emoji") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Sunset picnic"}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(mapper.readTree(response.bodyAsText()).path("emoji").asText().isNotBlank())
    }

    @Test
    fun mapLocationsIncludeActivityCountsAndDistinctEmojis() = testApplication {
        var now = 1_700_000_000_000L
        val services = services({ now })
        application { module(databaseHealth = { HealthStatus(true, "saigonquest", "3") }, services) }
        val linh = createClient { install(HttpCookies) }
        register(linh, "Linh", "0905550205")
        val linhId = mapper.readTree(linh.get("/api/me").bodyAsText()).path("id").asText()
        val location = services.repo.insertUserLocation("Climbing Gym", 10.77, 106.70, linhId, now)

        suspend fun createActivity(emoji: String, name: String, interested: Boolean = false) {
            val response = linh.post("/api/activities") {
                contentType(ContentType.Application.Json)
                setBody(
                    """{"emoji":"$emoji","name":"$name","locationName":"Climbing Gym","locationId":"${location.id}","interested":$interested}""",
                )
            }
            assertEquals(HttpStatusCode.Created, response.status)
            now += 1
        }
        createActivity("🧗", "Climb", interested = true)
        createActivity("☕", "Coffee")
        createActivity("🧗", "Bouldering")

        val locations = mapper.readTree(linh.get("/api/events").bodyAsText()).path("locations")
        val gym = (0 until locations.size()).map { locations.path(it) }
            .first { it.path("id").asText() == location.id }
        assertTrue(gym.path("hasActivities").asBoolean())
        assertEquals(3, gym.path("activityCount").asInt())
        assertEquals(
            listOf("🧗", "☕"),
            (0 until gym.path("activityEmojis").size()).map { gym.path("activityEmojis").path(it).asText() },
        )
    }

    private suspend fun register(client: io.ktor.client.HttpClient, name: String, phone: String) {
        val response = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"$name","phone":"$phone","password":"secretpass"}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    private fun services(clock: () -> Long): TownServices {
        val repo = InMemoryTownRepository()
        val uploads = UploadStore(File.createTempFile("sq-activities", "").apply {
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
        )
    }
}
