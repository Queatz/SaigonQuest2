package quest.saigon

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import quest.saigon.ai.MusicRender
import quest.saigon.auth.RateLimiter
import quest.saigon.maps.PlaceLookup
import quest.saigon.media.ImageOps
import quest.saigon.media.UploadStore
import quest.saigon.town.AvatarService
import quest.saigon.town.EventService
import quest.saigon.town.InMemoryTownRepository
import quest.saigon.town.MusicService
import quest.saigon.town.NotificationType
import quest.saigon.town.PlaceSuggestion
import quest.saigon.town.PostRecord
import quest.saigon.town.SaigonDays
import quest.saigon.town.TownServices
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class EventBehaviorTest {
    private val mapper = ObjectMapper()

    @Test
    fun eventsCanBePostedJoinedAndSummarized() = testApplication {
        var now = 1_700_000_000_000L
        val services = services({ now })
        application { module(databaseHealth = DatabaseHealth { HealthStatus(true, "saigonquest", "3") }, services) }
        val linh = createClient { install(HttpCookies) }
        val minh = createClient { install(HttpCookies) }
        val an = createClient { install(HttpCookies) }
        linh.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Linh","phone":"0905550001","password":"secretpass"}""")
        }
        minh.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Minh","phone":"0905550002","password":"secretpass"}""")
        }
        an.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"An","phone":"0905550003","password":"secretpass"}""")
        }

        val todayStart = SaigonDays.startMillis(SaigonDays.dayKey(now))
        val todayEvent = todayStart + 18 * 60 * 60 * 1000L
        val tomorrowEvent = todayStart + 34 * 60 * 60 * 1000L
        val laterEvent = todayStart + 3 * SaigonDays.DAY_MILLIS + 12 * 60 * 60 * 1000L
        val tooFarEvent = todayStart + 8 * SaigonDays.DAY_MILLIS

        val created = postEvent(linh, "Sunset at the river", todayEvent, todayEvent + 2 * 60 * 60 * 1000L, 50_000, "ChIJ123abc")
        assertEquals(HttpStatusCode.Created, created.status)
        val body = mapper.readTree(created.bodyAsText())
        val postId = body.path("id").asText()
        assertEquals(todayEvent, body.path("eventStartAt").asLong())
        assertEquals(50_000, body.path("eventPrice").asLong())
        assertEquals("ChIJ123abc", body.path("placeId").asText())
        assertTrue(body.path("joined").asBoolean())
        assertEquals(1, body.path("attendeeCount").asInt())

        postEvent(linh, "Breakfast tomorrow", tomorrowEvent, null, null, null)
        postEvent(linh, "Midweek hang", laterEvent, null, null, null)
        postEvent(linh, "Too far out", tooFarEvent, null, null, null)

        val summary = mapper.readTree(linh.get("/api/events/summary").bodyAsText())
        assertEquals(1, summary.path("today").asInt())
        assertEquals(1, summary.path("tomorrow").asInt())
        assertEquals(3, summary.path("week").asInt())

        val joined = minh.post("/api/posts/$postId/join")
        assertEquals(HttpStatusCode.OK, joined.status)
        assertTrue(mapper.readTree(joined.bodyAsText()).path("joined").asBoolean())
        assertEquals(2, mapper.readTree(joined.bodyAsText()).path("attendeeCount").asInt())

        val linhNotes = mapper.readTree(linh.get("/api/notifications").bodyAsText())
        assertEquals(NotificationType.EVENT_JOINED, linhNotes.path("notifications").path(0).path("type").asText())
        assertEquals("Minh joined your event".replace("Minh", linhNotes.path("notifications").path(0).path("actorName").asText()),
            "${linhNotes.path("notifications").path(0).path("actorName").asText()} joined your event")

        an.post("/api/posts/$postId/join")
        val minhNotes = mapper.readTree(minh.get("/api/notifications").bodyAsText())
        assertEquals(NotificationType.EVENT_FRIEND_JOINED, minhNotes.path("notifications").path(0).path("type").asText())

        val left = minh.post("/api/posts/$postId/leave")
        assertEquals(HttpStatusCode.OK, left.status)
        assertFalse(mapper.readTree(left.bodyAsText()).path("joined").asBoolean())
        val linhAfterLeave = mapper.readTree(linh.get("/api/notifications").bodyAsText()).path("notifications")
        assertTrue((0 until linhAfterLeave.size()).any { linhAfterLeave.path(it).path("type").asText() == NotificationType.EVENT_LEFT })

        val attendees = mapper.readTree(linh.get("/api/posts/$postId/attendees").bodyAsText()).path("attendees")
        assertTrue(attendees.size() >= 1)
        assertTrue(attendees.path(0).path("phone").asText().isNotBlank())
        assertTrue(attendees.path(0).path("zaloUrl").asText().startsWith("https://zalo.me/"))

        val events = mapper.readTree(linh.get("/api/events").bodyAsText())
        assertEquals(4, events.path("events").size())
        assertTrue(events.path("people").size() >= 3)

        assertTrue(!services.repo.latestPostPhotoAtPlace("Cafe", "ChIJ123abc", null).isNullOrBlank())
        val places = mapper.readTree(linh.get("/api/places?q=cafe").bodyAsText())
        assertEquals("Cafe", places.path("places").path(0).path("name").asText())
        assertTrue(places.path("recent").path(0).path("photoUrl").asText().startsWith("/media/"))
        assertTrue(places.path("places").path(0).path("photoUrl").asText().startsWith("/media/"), places.toPrettyString())
        val googlePlace = places.path("places").path(1)
        assertEquals("library", googlePlace.path("category").asText())
        assertEquals(
            "/api/places/photo?name=places%2FChIJLibrary%2Fphotos%2FPhoto1",
            googlePlace.path("googlePhotoUrl").asText(),
        )
        val googlePhoto = linh.get("/api/places/photo?name=places%2FChIJLibrary%2Fphotos%2FPhoto1")
        assertEquals(HttpStatusCode.OK, googlePhoto.status)
        assertEquals("image/jpeg", googlePhoto.headers[HttpHeaders.ContentType]?.substringBefore(';'))
        val invalidGooglePhoto = linh.get("/api/places/photo?name=https%3A%2F%2Fexample.org%2Fimage")
        assertEquals(HttpStatusCode.BadRequest, invalidGooglePhoto.status)
        val details = mapper.readTree(linh.get("/api/places/details?id=ChIJ123").bodyAsText())
        assertEquals(10.77, details.path("lat").asDouble(), 0.001)

        now = todayEvent - 60 * 60 * 1000L
        services.events.tick(now)
        val soon = mapper.readTree(an.get("/api/notifications").bodyAsText()).path("notifications")
        assertTrue((0 until soon.size()).any { soon.path(it).path("type").asText() == NotificationType.EVENT_STARTING_SOON })

        now = todayEvent
        services.events.tick(now)
        val starting = mapper.readTree(an.get("/api/notifications").bodyAsText()).path("notifications")
        assertTrue((0 until starting.size()).any { starting.path(it).path("type").asText() == NotificationType.EVENT_STARTING_NOW })

        val bad = postEvent(linh, "No start", null, todayEvent, 20_000, null)
        assertEquals(HttpStatusCode.BadRequest, bad.status)
        assertEquals("invalid_event", mapper.readTree(bad.bodyAsText()).path("error").asText())
        val endBeforeStart = postEvent(linh, "End before start", todayEvent, todayEvent - 1, null, null)
        assertEquals(HttpStatusCode.BadRequest, endBeforeStart.status)
        assertEquals("invalid_event_end", mapper.readTree(endBeforeStart.bodyAsText()).path("error").asText())
        val pricey = postEvent(linh, "Too much", todayEvent, null, Limits.EVENT_PRICE_MAX.toLong() + 1, null)
        assertEquals(HttpStatusCode.BadRequest, pricey.status)
    }

    @Test
    fun eventsPeopleIncludeLatestVisiblePost() = testApplication {
        val services = services { 1_700_000_000_000L }
        application { module(databaseHealth = DatabaseHealth { HealthStatus(true, "saigonquest", "3") }, services) }
        val author = createClient { install(HttpCookies) }
        val outsider = createClient { install(HttpCookies) }
        val authorId = mapper.readTree(
            author.post("/api/auth/register") {
                contentType(ContentType.Application.Json)
                setBody("""{"name":"Linh","phone":"0905550021","password":"secretpass"}""")
            }.bodyAsText(),
        ).path("id").asText()
        outsider.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Minh","phone":"0905550022","password":"secretpass"}""")
        }

        services.repo.insertPost(PostRecord("older", authorId, photoPath = "older.jpg", message = "Older", location = "D1", createdAt = 10))
        services.repo.insertPost(PostRecord("newer", authorId, photoPath = "newer.jpg", message = "Newer", location = "D1", createdAt = 20))
        services.repo.insertPost(
            PostRecord("z-newer", authorId, photoPath = "z-newer.jpg", message = "Same time", location = "D1", createdAt = 20),
        )
        services.repo.insertPost(
            PostRecord(
                "private",
                authorId,
                photoPath = "private.jpg",
                message = "Private",
                location = "D1",
                createdAt = 30,
                communityId = "private-community",
                communityOnly = true,
            ),
        )

        fun person(body: String, id: String) = mapper.readTree(body).path("people").let { people ->
            (0 until people.size()).map(people::path).first { it.path("id").asText() == id }
        }

        val outsiderBody = outsider.get("/api/events").bodyAsText()
        val outsiderAuthor = person(outsiderBody, authorId)
        assertEquals("z-newer", outsiderAuthor.path("latestPostId").asText())
        assertEquals("/media/z-newer.jpg", outsiderAuthor.path("latestPostPhotoUrl").asText())
        val noPostPerson = person(outsiderBody, mapper.readTree(outsiderBody).path("people").let { people ->
            (0 until people.size()).map(people::path).first { it.path("name").asText() == "Minh" }.path("id").asText()
        })
        assertTrue(noPostPerson.path("latestPostId").isNull)
        assertTrue(noPostPerson.path("latestPostPhotoUrl").isNull)
        assertTrue(noPostPerson.has("latestPostId"))
        assertTrue(noPostPerson.has("latestPostPhotoUrl"))

        val authorPerson = person(author.get("/api/events").bodyAsText(), authorId)
        assertEquals("private", authorPerson.path("latestPostId").asText())
    }

    @Test
    fun locationPostsAreGroupedByNameAndPlaceId() = testApplication {
        var now = 1_700_000_000_000L
        val services = services({ now })
        application { module(databaseHealth = DatabaseHealth { HealthStatus(true, "saigonquest", "3") }, services) }
        val linh = createClient { install(HttpCookies) }
        linh.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Linh","phone":"0905550001","password":"secretpass"}""")
        }

        postAt(linh, "Different name", "Park", "ChIJ1")
        now += 1
        postAt(linh, "One", "Cafe", "ChIJ1")
        now += 1
        postAt(linh, "Two", "Cafe", "ChIJ1")
        now += 1
        postAt(linh, "Other id", "Cafe", "ChIJ2")
        now += 1
        postAt(linh, "No id", "Cafe", null)

        val place = mapper.readTree(linh.get("/api/places/ChIJ1").bodyAsText())
        assertEquals("Cafe", place.path("name").asText())
        assertEquals("ChIJ1", place.path("placeId").asText())
        assertEquals(10.77, place.path("lat").asDouble())
        val nameOnlyPlace = mapper.readTree(linh.get("/api/places/${encodeLocationToken("Cafe")}").bodyAsText())
        assertEquals("Cafe", nameOnlyPlace.path("name").asText())
        assertTrue(nameOnlyPlace.path("placeId").isMissingNode || nameOnlyPlace.path("placeId").isNull)
        val missingToken = encodeLocationToken("Not a real place")
        assertEquals(HttpStatusCode.NotFound, linh.get("/api/places/$missingToken").status)
        assertEquals(HttpStatusCode.NotFound, linh.get("/api/places/missing").status)

        val matched = mapper.readTree(linh.get("/api/places/posts?name=Cafe&placeId=ChIJ1").bodyAsText())
        assertEquals(2, matched.path("count").asInt())
        assertEquals(2, matched.path("posts").size())
        assertEquals("Two", matched.path("posts").path(0).path("message").asText())
        assertEquals("One", matched.path("posts").path(1).path("message").asText())

        val unnamed = mapper.readTree(linh.get("/api/places/posts?name=Cafe").bodyAsText())
        assertEquals(1, unnamed.path("count").asInt())
        assertEquals("No id", unnamed.path("posts").path(0).path("message").asText())

        val guest = createClient { }
        val guestView = mapper.readTree(guest.get("/api/places/posts?name=Cafe&placeId=ChIJ1").bodyAsText())
        assertEquals(2, guestView.path("count").asInt())
        val chooser = mapper.readTree(linh.get("/api/places?q=cafe").bodyAsText())
        val recent = chooser.path("recent")
        val chij1 = (0 until recent.size()).map { recent.path(it) }
            .first { it.path("placeId").asText() == "ChIJ1" }
        assertEquals(2, chij1.path("postCount").asInt())
        val googlePlaces = chooser.path("places")
        assertEquals(1, googlePlaces.size())
        assertEquals("ChIJ123", googlePlaces.path(0).path("placeId").asText())
        assertEquals(0, googlePlaces.path(0).path("postCount").asInt())
        assertEquals(HttpStatusCode.BadRequest, linh.get("/api/places/posts?name=").status)
        assertEquals(HttpStatusCode.BadRequest, linh.get("/api/places/posts").status)
    }

    @Test
    fun eventsMapIncludesPostedGooglePlacesWithCoordinates() = testApplication {
        var now = 1_700_000_000_000L
        val services = services({ now })
        application { module(databaseHealth = DatabaseHealth { HealthStatus(true, "saigonquest", "3") }, services) }
        val linh = createClient { install(HttpCookies) }
        val registered = linh.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Linh","phone":"0905550001","password":"secretpass"}""")
        }
        val authorId = mapper.readTree(registered.bodyAsText()).path("id").asText()

        postAt(linh, "First place post", "Place A", "ChIJ1", lat = 10.77, lng = 106.70)
        val firstPhotoPath = services.repo.latestPostPhotoAtPlace("Place A", "ChIJ1", authorId)
        now += 1
        postAt(linh, "Latest place post", "Place A", "ChIJ1", lat = 10.775, lng = 106.705)
        val latestPhotoPath = services.repo.latestPostPhotoAtPlace("Place A", "ChIJ1", authorId)
        now += 1
        postAt(linh, "Second place post", "Place A", "ChIJ2", lat = 10.78, lng = 106.71)
        now += 1
        postAt(linh, "No coordinates", "Place A", "ChIJ3", includeCoordinates = false)
        services.repo.insertPost(
            PostRecord(
                id = "",
                userId = authorId,
                photoPath = "private.jpg",
                message = "Private location post",
                location = "Hidden place",
                createdAt = now + 1,
                communityId = "private-community",
                communityOnly = true,
                placeId = "ChIJ4",
                lat = 10.79,
                lng = 106.72,
            ),
        )

        val locations = mapper.readTree(linh.get("/api/events").bodyAsText()).path("locations")
        fun location(placeId: String) = (0 until locations.size()).map { locations.path(it) }
            .first { it.path("placeId").asText() == placeId }

        val first = location("ChIJ1")
        assertEquals("Place A", first.path("name").asText())
        assertEquals(10.775, first.path("lat").asDouble())
        assertEquals(106.705, first.path("lng").asDouble())
        assertEquals(2, first.path("postCount").asInt())
        assertTrue(!firstPhotoPath.isNullOrBlank())
        assertNotEquals(firstPhotoPath, latestPhotoPath)
        assertEquals("/media/$latestPhotoPath", first.path("photoUrl").asText())
        val second = location("ChIJ2")
        assertEquals("Place A", second.path("name").asText())
        assertEquals(10.78, second.path("lat").asDouble())
        assertEquals(1, second.path("postCount").asInt())
        assertFalse((0 until locations.size()).any { locations.path(it).path("placeId").asText() == "ChIJ3" })
        assertFalse((0 until locations.size()).any { locations.path(it).path("placeId").asText() == "ChIJ4" })

        val guestLocations = mapper.readTree(createClient {}.get("/api/events").bodyAsText()).path("locations")
        assertTrue((0 until guestLocations.size()).any { guestLocations.path(it).path("placeId").asText() == "ChIJ1" })
        assertFalse((0 until guestLocations.size()).any { guestLocations.path(it).path("placeId").asText() == "ChIJ4" })
    }

    @Test
    fun userCreatedPlacesCanBeCreatedSearchedAndKeepCoordinatesWithoutPlaceId() = testApplication {
        val services = services { 1_700_000_000_000L }
        application { module(databaseHealth = DatabaseHealth { HealthStatus(true, "saigonquest", "3") }, services) }
        val linh = createClient { install(HttpCookies) }
        linh.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Linh","phone":"0905550001","password":"secretpass"}""")
        }

        val created = linh.post("/api/places") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Lantern Alley","lat":10.77,"lng":106.70}""")
        }
        assertEquals(HttpStatusCode.Created, created.status)
        val createdPlace = mapper.readTree(created.bodyAsText())
        assertEquals("Lantern Alley", createdPlace.path("name").asText())
        assertTrue(createdPlace.path("placeId").isMissingNode || createdPlace.path("placeId").isNull)
        val id = createdPlace.path("id").asText()
        assertTrue(id.isNotBlank())

        val lookup = mapper.readTree(linh.get("/api/places/$id").bodyAsText())
        assertEquals("Lantern Alley", lookup.path("name").asText())
        assertEquals(10.77, lookup.path("lat").asDouble(), 0.001)
        assertEquals(HttpStatusCode.NotFound, linh.get("/api/places/${encodeLocationToken("Lantern Alley")}").status)
        val search = mapper.readTree(linh.get("/api/places?q=lantern").bodyAsText()).path("places")
        assertTrue((0 until search.size()).any { search.path(it).path("id").asText() == id })
        val events = mapper.readTree(linh.get("/api/events").bodyAsText()).path("locations")
        val mapLocation = (0 until events.size()).map { events.path(it) }.first { it.path("id").asText() == id }
        assertTrue(mapLocation.path("photoUrl").isMissingNode || mapLocation.path("photoUrl").isNull)

        val profile = linh.patch("/api/me") {
            contentType(ContentType.Application.Json)
            setBody("""{"location":"Lantern Alley","locationId":"$id","lat":10.77,"lng":106.70}""")
        }
        assertEquals(HttpStatusCode.OK, profile.status)
        val savedProfile = mapper.readTree(profile.bodyAsText())
        assertTrue(savedProfile.path("placeId").isMissingNode || savedProfile.path("placeId").isNull)
        assertEquals(id, savedProfile.path("locationId").asText())
        assertEquals(10.77, savedProfile.path("lat").asDouble(), 0.001)
        assertEquals(106.70, savedProfile.path("lng").asDouble(), 0.001)

        val blank = linh.post("/api/places") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"  ","lat":10.77,"lng":106.70}""")
        }
        assertEquals(HttpStatusCode.BadRequest, blank.status)
        val outside = linh.post("/api/places") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Outside","lat":91,"lng":106.70}""")
        }
        assertEquals(HttpStatusCode.BadRequest, outside.status)
        val zeroCoordinates = linh.post("/api/places") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Zero Coordinates","lat":0,"lng":0}""")
        }
        assertEquals(HttpStatusCode.Created, zeroCoordinates.status)
        val zeroPlace = mapper.readTree(zeroCoordinates.bodyAsText())
        assertEquals(0.0, zeroPlace.path("lat").asDouble())
        assertEquals(0.0, zeroPlace.path("lng").asDouble())
    }

    @Test
    fun sameNamedUserLocationsKeepPostsAndLinksDistinct() = testApplication {
        val services = services { 1_700_000_000_000L }
        application { module(databaseHealth = DatabaseHealth { HealthStatus(true, "saigonquest", "3") }, services) }
        val linh = createClient { install(HttpCookies) }
        linh.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"name":"Linh","phone":"0905550001","password":"secretpass"}""")
        }

        suspend fun createLocation(lat: Double) = mapper.readTree(
            linh.post("/api/places") {
                contentType(ContentType.Application.Json)
                setBody("""{"name":"Place A","lat":$lat,"lng":106.70}""")
            }.bodyAsText(),
        ).path("id").asText()

        val firstId = createLocation(10.77)
        val secondId = createLocation(10.78)
        val emptyId = createLocation(10.79)
        assertNotEquals(firstId, secondId)
        assertNotEquals(secondId, emptyId)
        postAt(linh, "First location post", "Place A", null, firstId)
        postAt(linh, "Second location post", "Place A", null, secondId)
        postAt(linh, "Name only post", "Place A", null)

        val eventLocations = mapper.readTree(linh.get("/api/events").bodyAsText()).path("locations")
        fun postCount(id: String) = (0 until eventLocations.size()).map { eventLocations.path(it) }
            .first { it.path("id").asText() == id }
            .path("postCount").asInt()
        assertEquals(1, postCount(firstId))
        assertEquals(1, postCount(secondId))
        assertEquals(0, postCount(emptyId))

        val searchedPlaces = mapper.readTree(linh.get("/api/places?q=Place%20A").bodyAsText()).path("places")
        fun photoUrl(id: String) = (0 until searchedPlaces.size()).map { searchedPlaces.path(it) }
            .first { it.path("id").asText() == id }
            .path("photoUrl").asText()
        val firstPhotoUrl = photoUrl(firstId)
        val secondPhotoUrl = photoUrl(secondId)
        assertTrue(firstPhotoUrl.startsWith("/media/"))
        assertTrue(secondPhotoUrl.startsWith("/media/"))
        assertNotEquals(firstPhotoUrl, secondPhotoUrl)

        val firstPosts = mapper.readTree(linh.get("/api/places/posts?name=Place%20A&locationId=$firstId").bodyAsText())
        assertEquals(1, firstPosts.path("count").asInt())
        assertEquals("First location post", firstPosts.path("posts").path(0).path("message").asText())
        val secondPosts = mapper.readTree(linh.get("/api/places/posts?name=Place%20A&locationId=$secondId").bodyAsText())
        assertEquals(1, secondPosts.path("count").asInt())
        assertEquals("Second location post", secondPosts.path("posts").path(0).path("message").asText())
        val nameOnlyPosts = mapper.readTree(linh.get("/api/places/posts?name=Place%20A").bodyAsText())
        assertEquals(1, nameOnlyPosts.path("count").asInt())
        assertEquals("Name only post", nameOnlyPosts.path("posts").path(0).path("message").asText())
        assertEquals(firstId, mapper.readTree(linh.get("/api/places/$firstId").bodyAsText()).path("id").asText())
        assertEquals(secondId, mapper.readTree(linh.get("/api/places/$secondId").bodyAsText()).path("id").asText())
    }

    private suspend fun postAt(
        client: io.ktor.client.HttpClient,
        message: String,
        location: String,
        placeId: String?,
        locationId: String? = null,
        includeCoordinates: Boolean = true,
        lat: Double = 10.77,
        lng: Double = 106.70,
    ) = client.post("/api/posts") {
        setBody(
            MultiPartFormDataContent(
                formData {
                    val jpeg = ImageOps().writeJpeg(solid(12, 16, Color(40, 120, 80)), 0.85f)
                    append(
                        "photo",
                        jpeg,
                        Headers.build {
                            append(HttpHeaders.ContentType, "image/jpeg")
                            append(HttpHeaders.ContentDisposition, "filename=\"photo.jpg\"")
                        },
                    )
                    append("message", message)
                    append("location", location)
                    if (!locationId.isNullOrBlank()) append("locationId", locationId)
                    if (!placeId.isNullOrBlank()) {
                        append("placeId", placeId)
                        if (includeCoordinates) {
                            append("lat", lat.toString())
                            append("lng", lng.toString())
                        }
                    }
                },
            ),
        )
    }

    private suspend fun postEvent(
        client: io.ktor.client.HttpClient,
        message: String,
        start: Long?,
        end: Long?,
        price: Long?,
        placeId: String?,
    ) = client.post("/api/posts") {
        setBody(
            MultiPartFormDataContent(
                formData {
                    val jpeg = ImageOps().writeJpeg(solid(12, 16, Color(40, 120, 80)), 0.85f)
                    append(
                        "photo",
                        jpeg,
                        Headers.build {
                            append(HttpHeaders.ContentType, "image/jpeg")
                            append(HttpHeaders.ContentDisposition, "filename=\"photo.jpg\"")
                        },
                    )
                    append("message", message)
                    append("location", "Saigon River")
                    if (start != null) append("eventStartAt", start.toString())
                    if (end != null) append("eventEndAt", end.toString())
                    if (price != null) append("eventPrice", price.toString())
                    if (!placeId.isNullOrBlank()) {
                        append("placeId", placeId)
                        append("lat", "10.77")
                        append("lng", "106.70")
                    }
                },
            ),
        )
    }

    private fun services(clock: () -> Long): TownServices {
        val repo = InMemoryTownRepository()
        val uploads = UploadStore(File.createTempFile("sq-event", "").apply {
            delete()
            mkdirs()
        })
        val hub = quest.saigon.town.NotificationHub()
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
            config = AppConfig(uploadDir = uploads.root, googleMapsApiKey = "test-maps"),
            clock = clock,
            hub = hub,
            contactLimiter = RateLimiter(1, 60_000, clock),
            places = object : PlaceLookup {
                override fun search(query: String) = listOf(
                    PlaceSuggestion("Cafe", "D1", "ChIJ123abc", 10.77, 106.70),
                    PlaceSuggestion("Library", "D2", "ChIJLibrary", 10.78, 106.71),
                )
                override fun details(placeId: String) = when (placeId) {
                    "ChIJ123" -> PlaceSuggestion("Cafe", "D1", placeId, 10.77, 106.70)
                    "ChIJLibrary" -> PlaceSuggestion(
                        "Library",
                        "D2",
                        placeId,
                        10.78,
                        106.71,
                        googlePhotoName = "places/ChIJLibrary/photos/Photo1",
                        category = "library",
                    )
                    else -> null
                }
                override fun photo(photoName: String) = byteArrayOf(1, 2, 3).takeIf {
                    photoName == "places/ChIJLibrary/photos/Photo1"
                }
            },
            events = EventService(repo, hub, clock),
        )
    }

    private fun solid(width: Int, height: Int, color: Color): BufferedImage {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        graphics.color = color
        graphics.fillRect(0, 0, width, height)
        graphics.dispose()
        return image
    }
}
