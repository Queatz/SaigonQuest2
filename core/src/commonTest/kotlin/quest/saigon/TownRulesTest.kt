package quest.saigon

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TownRulesTest {
    @Test
    fun vietnameseLocalNumberBecomesZaloDigits() {
        assertEquals("84901234567", PhoneNumbers.canonicalize("090 123 4567"))
        assertEquals("84901234567", PhoneNumbers.canonicalize("+84 90-123-4567"))
        assertEquals("https://zalo.me/84901234567", PhoneNumbers.zaloUrl("84901234567"))
        assertEquals("+84901234567", PhoneNumbers.display("84901234567"))
    }

    @Test
    fun rejectsShortNumbers() {
        assertNull(PhoneNumbers.canonicalize("12345"))
        assertNull(PhoneNumbers.canonicalize("call me"))
    }

    @Test
    fun languageFollowsPreferenceThenBrowser() {
        assertEquals(Language.VI, resolveLanguage("system", "vi-VN"))
        assertEquals(Language.EN, resolveLanguage("system", "en-US"))
        assertEquals(Language.EN, resolveLanguage("en", "vi-VN"))
        assertEquals(Language.VI, resolveLanguage("vi", "en-US"))
        assertEquals("system", languagePreference("System"))
        assertNull(languagePreference("fr"))
    }

    @Test
    fun cardMovesFasterThanThePhoto() {
        val motion = feedMotion(100.0)
        assertEquals(100.0, motion.photoOffset)
        assertEquals(110.0, motion.cardOffset, 0.001)
        assertEquals(50, Limits.AVATAR_ENTER_PX)
        assertEquals(33, Limits.TYPEWRITER_MILLIS_PER_CHAR)
        assertEquals(20, Limits.PHOTOS_MAX)
    }

    @Test
    fun neighboringMessageBoxesFollowTheirOwnPost() {
        val height = 640.0
        val dragTowardPrevious = 200.0
        val previous = feedSlideMotion(dragTowardPrevious, -1, height)
        val current = feedSlideMotion(dragTowardPrevious, 0, height)
        val next = feedSlideMotion(dragTowardPrevious, 1, height)

        fun cardShift(motion: FeedMotion) = motion.cardOffset - motion.photoOffset

        assertEquals(-440.0, previous.photoOffset, 0.001)
        assertEquals(200.0, current.photoOffset, 0.001)
        assertEquals(840.0, next.photoOffset, 0.001)
        assertEquals(-44.0, cardShift(previous), 0.001)
        assertEquals(20.0, cardShift(current), 0.001)
        assertEquals(84.0, cardShift(next), 0.001)
    }

    @Test
    fun textLinksTurnUrlsIntoPieces() {
        val pieces = TextLinks.pieces("Meet at https://saigon.town/c/1, tonight")
        assertEquals(3, pieces.size)
        assertEquals("Meet at ", pieces[0].text)
        assertEquals(null, pieces[0].href)
        assertEquals("https://saigon.town/c/1", pieces[1].text)
        assertEquals("https://saigon.town/c/1", pieces[1].href)
        assertEquals(", tonight", pieces[2].text)
        val bare = TextLinks.pieces("Meet at saigon.town/c/1 tonight")
        assertEquals(3, bare.size)
        assertEquals("Meet at ", bare[0].text)
        assertEquals("saigon.town/c/1", bare[1].text)
        assertEquals("https://saigon.town/c/1", bare[1].href)
        assertEquals(" tonight", bare[2].text)
        val www = TextLinks.pieces("see www.example.com.")
        assertEquals("www.example.com", www[1].text)
        assertEquals("https://www.example.com", www[1].href)
        assertEquals(emptyList(), TextLinks.pieces(""))
        assertEquals(listOf(TextLinks.Piece("no links here", null)), TextLinks.pieces("no links here"))
    }

    @Test
    fun bothLanguagesCoverTheSameCopy() {
        Copy.keys().forEach { key ->
            assertTrue(Copy.text(Language.EN, key).isNotBlank())
            assertTrue(Copy.text(Language.VI, key).isNotBlank())
        }
        assertEquals(10, Copy.uploadPrompts(Language.EN).distinct().size)
        assertEquals(10, Copy.uploadPrompts(Language.VI).distinct().size)
        assertEquals("Whatcha doin'?", Copy.uploadPrompts(Language.EN).first())
        assertEquals("Reply to Linh", Copy.text(Language.EN, "replyTo", mapOf("name" to "Linh")))
        assertEquals("Trả lời Linh", Copy.text(Language.VI, "replyTo", mapOf("name" to "Linh")))
        assertEquals("Saigon.Town", Copy.text(Language.EN, "appName"))
        assertEquals("Saigon.Town", Copy.text(Language.VI, "appName"))
        assertEquals("Dành cho Sài Gòn!", Copy.text(Language.EN, "motto"))
        assertEquals("Dành cho Sài Gòn!", Copy.text(Language.VI, "motto"))
        assertEquals(
            "Bí mật bé nhất Sài Gòn nè! Kết nối thiệt lòng, xây dựng mối quan hệ đáng giá, chia sẻ với mấy người xung quanh mình thui, chứ hong phải cả thế giới đâu.",
            Copy.text(Language.VI, "visitorWelcome"),
        )
        assertEquals("Let's go!", Copy.text(Language.EN, "visitorWelcomeGo"))
        assertEquals("Vào thui!", Copy.text(Language.VI, "visitorWelcomeGo"))
        assertEquals("Swipe down", Copy.text(Language.EN, "swipeDown"))
        assertEquals("Vuốt xuống", Copy.text(Language.VI, "swipeDown"))
        assertEquals("Select music", Copy.text(Language.EN, "selectMusic"))
        assertEquals("Chọn nhạc", Copy.text(Language.VI, "selectMusic"))
        assertEquals(
            "Be the first to give Linh a comment.",
            Copy.text(Language.EN, "beFirstComment", mapOf("name" to "Linh")),
        )
        assertEquals("Comments", Copy.text(Language.EN, "comments"))
        assertEquals("Bình luận", Copy.text(Language.VI, "comments"))
        assertEquals("Your account is paused", Copy.text(Language.EN, "accountPaused"))
        assertEquals("Tài khoản của bạn đã bị tạm khóa", Copy.text(Language.VI, "accountPaused"))
        assertEquals("Admin", Copy.text(Language.EN, "admin"))
        assertEquals("Quản trị", Copy.text(Language.VI, "admin"))
        assertEquals(20, Limits.PHOTOS_MAX)
        assertEquals("Add photo", Copy.text(Language.EN, "addPhoto"))
        assertEquals("Thêm ảnh", Copy.text(Language.VI, "addPhoto"))
        assertEquals("2 events today", Copy.text(Language.EN, "eventsToday", mapOf("count" to "2")))
        assertEquals("2 sự kiện hôm nay", Copy.text(Language.VI, "eventsToday", mapOf("count" to "2")))
        assertEquals(100_000_000, Limits.EVENT_PRICE_MAX)
        assertEquals("Nice ✨", Copy.quickReplies(Language.EN).first())
        assertEquals("Hay quá ✨", Copy.quickReplies(Language.VI).first())
        assertEquals(5, Copy.quickReplies(Language.EN).distinct().size)
        assertEquals(5, Copy.quickReplies(Language.VI).distinct().size)
    }

    @Test
    fun postAgeUsesShortEnglishAndVietnameseLabels() {
        val now = 1_700_000_000_000L
        fun age(ago: Long, language: Language) = PostAge.relative(now - ago, now, language)

        assertEquals("1m", age(30_000L, Language.EN))
        assertEquals("1p", age(30_000L, Language.VI))
        assertEquals("1h", age(60 * 60_000L, Language.EN))
        assertEquals("1g", age(60 * 60_000L, Language.VI))
        assertEquals("5h", age(5 * 60 * 60_000L, Language.EN))
        assertEquals("5g", age(5 * 60 * 60_000L, Language.VI))
        assertEquals("6d", age(6 * 24 * 60 * 60_000L, Language.EN))
        assertEquals("6n", age(6 * 24 * 60 * 60_000L, Language.VI))
        assertEquals("1w", age(7 * 24 * 60 * 60_000L, Language.EN))
        assertEquals("1t", age(7 * 24 * 60 * 60_000L, Language.VI))
        assertNull(age(30 * 24 * 60 * 60_000L, Language.EN))
        assertEquals("", PostAge.relative(0L, now, Language.EN))
    }

    @Test
    fun notificationCopyMatchesInboxTextAndPostPath() {
        assertEquals(
            "Minh commented on your post",
            NotificationCopy.title(Language.EN, "post_comment", "Minh", "I'm down"),
        )
        assertEquals(
            "Minh đã bình luận bài của bạn",
            NotificationCopy.title(Language.VI, "post_comment", "Minh", "I'm down"),
        )
        assertEquals(
            "Minh also commented",
            NotificationCopy.title(Language.EN, "thread_comment", "Minh", "Same"),
        )
        assertEquals(
            "Minh joined your event",
            NotificationCopy.title(Language.EN, "event_joined", "Minh", "Minh"),
        )
        assertEquals(
            "Minh left your event",
            NotificationCopy.title(Language.EN, "event_left", "Minh", "Minh"),
        )
        assertEquals(
            "Minh joined an event you're going to",
            NotificationCopy.title(Language.EN, "event_friend_joined", "Minh", "Minh"),
        )
        assertEquals(
            "Coffee is starting in an hour",
            NotificationCopy.title(Language.EN, "event_starting_soon", "", "Coffee"),
        )
        assertEquals(
            "Coffee is starting now",
            NotificationCopy.title(Language.EN, "event_starting_now", "", "Coffee"),
        )
        assertEquals("I'm down", NotificationCopy.body("I'm down"))
        assertEquals("/p/abc123", NotificationCopy.path("abc123"))
        assertEquals(AppRoute.Post("abc123"), matchAppRoute(NotificationCopy.path("abc123")))
    }

    @Test
    fun musicNamesPreferLyricsThenPrompt() {
        assertEquals("Sáng sớm trời mưa", MusicNames.fromPrompt("Sáng sớm trời mưa"))
        assertEquals(
            "Coffee aroma fills the room",
            MusicNames.fromLyrics("[verse]\nCoffee aroma fills the room"),
        )
        assertEquals("Rain song", MusicNames.cleanTitle("  \"Rain song\"  "))
    }
}
