package quest.saigon.shared

import quest.saigon.Copy
import quest.saigon.Language
import quest.saigon.shared.model.*
import quest.saigon.shared.network.ApiErrorMapper
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SerializationAndErrorMapperTest {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    @Test
    fun deserializesServerPostAndMePayloads() {
        val meJson = """
            {
              "id": "u1",
              "name": "Linh",
              "phone": "+84901234567",
              "location": "District 1",
              "placeId": "ChIJ1",
              "locationId": null,
              "lat": 10.77,
              "lng": 106.70,
              "intro": "Coffee fan",
              "language": "vi",
              "hasProfilePhoto": true,
              "photoUrl": "/api/me/photo",
              "paused": false,
              "admin": true,
              "token": "tok_abc"
            }
        """.trimIndent()
        val me = json.decodeFromString(MeDto.serializer(), meJson)
        assertEquals("u1", me.id)
        assertEquals("tok_abc", me.token)
        assertTrue(me.admin)
        assertNull(me.locationId)

        val feedJson = """
            {
              "posts": [
                {
                  "id": "p1",
                  "authorId": "u1",
                  "authorName": "Linh",
                  "photoUrl": "/media/posts/a.jpg",
                  "photoUrls": ["/media/posts/b.jpg"],
                  "message": "Morning pho",
                  "location": "Ben Thanh",
                  "createdAt": 1700000000000,
                  "quickReplies": ["Nice!"],
                  "commentCount": 1,
                  "comments": [{"id": "c1", "authorName": "Minh", "text": "Omw", "createdAt": 1700000001000}],
                  "pollOptions": [{"index": 0, "text": "Pho", "voteCount": 2, "voted": true, "voters": []}],
                  "eventStartAt": 1700010000000,
                  "attendeeCount": 3,
                  "joined": true,
                  "ctaText": "Open",
                  "ctaDestination": "website",
                  "ctaUrl": "https://saigon.town",
                  "openChatId": "chat1",
                  "openChatRemaining": 5,
                  "openChatJoined": false
                }
              ],
              "nextCursor": "1700000000000:p1"
            }
        """.trimIndent()
        val page = json.decodeFromString(FeedPageDto.serializer(), feedJson)
        assertEquals(1, page.posts.size)
        val post = page.posts.first()
        assertTrue(post.hasEvent)
        assertTrue(post.hasPoll)
        assertTrue(post.hasCta)
        assertTrue(post.hasOpenChat)
        assertEquals(listOf("/media/posts/a.jpg", "/media/posts/b.jpg"), post.allPhotoUrls())
        assertEquals("1700000000000:p1", page.nextCursor)
    }

    @Test
    fun mapsServerErrorCodesAndNetworkFailuresToLocalizedCopy() {
        val badCreds = ApiErrorMapper.toFailure(401, """{"error":"bad_credentials"}""", Language.EN)
        assertEquals("badCredentials", badCreds.copyKey)
        assertEquals(Copy.text(Language.EN, "badCredentials"), badCreds.message)

        val rateLimited = ApiErrorMapper.toFailure(429, """{"error":"rate_limited"}""", Language.VI)
        assertEquals("rateLimited", rateLimited.copyKey)
        assertEquals(Copy.text(Language.VI, "rateLimited"), rateLimited.message)

        val offline = ApiErrorMapper.toFailure(0, "", Language.EN)
        assertEquals("unavailable", offline.copyKey)
        assertEquals(Copy.text(Language.EN, "unavailable"), offline.message)

        val unknown = ApiErrorMapper.toFailure(500, "unexpected", Language.EN)
        assertEquals("errorGeneric", unknown.copyKey)
    }
}
