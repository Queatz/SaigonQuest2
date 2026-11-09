package quest.saigon

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TownRulesTest {
    @Test
    fun locationTokensRoundTripNamesAndRejectInvalidTokens() {
        listOf("Cafe", "  Quán cà phê  ").forEach { name ->
            assertEquals(name.trim(), decodeLocationToken(encodeLocationToken(name)))
        }
        assertTrue(encodeLocationToken("Cafe").matches(Regex("[A-Za-z0-9_-]+")))
        assertNull(decodeLocationToken("abc!"))
        assertNull(decodeLocationToken("a"))
    }

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
    fun openChatExpirationAndCapacityAreBounded() {
        assertEquals(1, Limits.OPEN_CHAT_HOURS_MIN)
        assertEquals(24, Limits.OPEN_CHAT_HOURS_MAX)
        assertEquals(24, Limits.OPEN_CHAT_HOURS_DEFAULT)
        assertEquals(1, Limits.OPEN_CHAT_REMAINING_MIN)
        assertEquals(10, Limits.OPEN_CHAT_REMAINING_MAX)
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
        assertEquals("Remove photo", Copy.text(Language.EN, "removePhoto"))
        assertEquals("Xóa ảnh", Copy.text(Language.VI, "removePhoto"))
        assertEquals("0 posts", Copy.text(Language.EN, "postCount", mapOf("count" to "0")))
        assertEquals("1 post", Copy.text(Language.EN, "postCount", mapOf("count" to "1")))
        assertEquals("2 posts", Copy.text(Language.EN, "postCount", mapOf("count" to "2")))
        assertEquals("0 bài", Copy.text(Language.VI, "postCount", mapOf("count" to "0")))
        assertEquals("1 bài", Copy.text(Language.VI, "postCount", mapOf("count" to "1")))
        assertEquals("2 bài", Copy.text(Language.VI, "postCount", mapOf("count" to "2")))
        assertEquals("2 events today", Copy.text(Language.EN, "eventsToday", mapOf("count" to "2")))
        assertEquals("2 sự kiện hôm nay", Copy.text(Language.VI, "eventsToday", mapOf("count" to "2")))
        assertEquals("3 events in 7 days", Copy.text(Language.EN, "eventsWeek", mapOf("count" to "3")))
        assertEquals("3 sự kiện trong 7 ngày", Copy.text(Language.VI, "eventsWeek", mapOf("count" to "3")))
        assertEquals(100_000_000, Limits.EVENT_PRICE_MAX)
        assertEquals("📊 Poll", Copy.text(Language.EN, "poll"))
        assertEquals("📊 Bình chọn", Copy.text(Language.VI, "poll"))
        assertEquals(5, Limits.POLL_OPTIONS_MAX)
        assertEquals(2, Limits.POLL_OPTIONS_MIN)
        assertEquals(listOf("Pho", "Banh mi"), PollNames.clean(listOf(" Pho ", "Banh mi", "Pho")))
        assertEquals(null, PollNames.clean(listOf("Only one")))
        assertEquals("Nice ✨", Copy.quickReplies(Language.EN).first())
        assertEquals("Hay quá ✨", Copy.quickReplies(Language.VI).first())
        assertEquals(5, Copy.quickReplies(Language.EN).distinct().size)
        assertEquals(5, Copy.quickReplies(Language.VI).distinct().size)
        assertEquals(80, Limits.CTA_TITLE_MAX)
        assertEquals(40, Limits.CTA_TEXT_MAX)
        assertEquals("Add a call to action", Copy.text(Language.EN, "addCallToAction"))
        assertEquals("Thêm lời kêu gọi hành động", Copy.text(Language.VI, "addCallToAction"))
        assertEquals("Book now", CallToAction.parse("", "Book now", "message", "")?.text)
        assertEquals("profile", CallToAction.parse("", "Show your profile", "profile", "")?.destination)
        assertEquals("product", CallToAction.parse("", "Show a product", "product", "product-id")?.destination)
        assertEquals("product-id", CallToAction.parse("", "Show a product", "product", " product-id ")?.url)
        assertEquals("https://saigon.town/menu", CallToAction.parse("", "Open", "website", "saigon.town/menu")?.url)
        assertEquals("http://saigon.town/menu", CallToAction.parse("", "Open", "website", "http://saigon.town/menu")?.url)
        assertEquals(
            "Opens direct messages with you on Saigon.Town.",
            Copy.text(Language.EN, "ctaSendMessageHint"),
        )
        assertEquals("Sends them to your Zalo profile.", Copy.text(Language.EN, "ctaZaloMessageHint"))
        assertEquals("Show your profile", Copy.text(Language.EN, "ctaShowProfile"))
        assertEquals("Opens your profile.", Copy.text(Language.EN, "ctaShowProfileHint"))
        assertEquals("Xem hồ sơ của bạn", Copy.text(Language.VI, "ctaShowProfile"))
        assertEquals("Mở hồ sơ của bạn.", Copy.text(Language.VI, "ctaShowProfileHint"))
        assertEquals("Show a product", Copy.text(Language.EN, "ctaShowProduct"))
        assertEquals("Opens the selected product details.", Copy.text(Language.EN, "ctaShowProductHint"))
        assertEquals("Choose a product", Copy.text(Language.EN, "chooseProduct"))
        assertEquals("Search your products", Copy.text(Language.EN, "searchProducts"))
        assertEquals("Xem sản phẩm", Copy.text(Language.VI, "ctaShowProduct"))
        assertEquals("Mở chi tiết sản phẩm đã chọn.", Copy.text(Language.VI, "ctaShowProductHint"))
        assertEquals("Chọn sản phẩm", Copy.text(Language.VI, "chooseProduct"))
        assertEquals("Tìm sản phẩm của bạn", Copy.text(Language.VI, "searchProducts"))
        assertNull(CallToAction.parse("", "", "message", ""))
        assertNull(CallToAction.parse("", "Show a product", "product", ""))
        assertNull(CallToAction.parse("", "Open", "website", "javascript:alert(1)"))
        assertNull(CallToAction.parse("", "Open", "email", ""))
    }

    @Test
    fun activityCountLabelsPluralizeInBothLanguages() {
        assertEquals("1 activity", Copy.text(Language.EN, "activityCountShort", mapOf("count" to "1")))
        assertEquals("2 activities", Copy.text(Language.EN, "activityCountShort", mapOf("count" to "2")))
        assertEquals("1 hoạt động", Copy.text(Language.VI, "activityCountShort", mapOf("count" to "1")))
        assertEquals("2 hoạt động", Copy.text(Language.VI, "activityCountShort", mapOf("count" to "2")))
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
        assertEquals(
            "Minh voted in Lunch?",
            NotificationCopy.title(Language.EN, "poll_voted", "Minh", "Lunch?"),
        )
        assertEquals(
            "Minh đã bình chọn trong Lunch?",
            NotificationCopy.title(Language.VI, "poll_voted", "Minh", "Lunch?"),
        )
        assertEquals("I'm down", NotificationCopy.body("I'm down"))
        assertEquals("/p/abc123", NotificationCopy.path("abc123"))
        assertEquals("/profile/orders", NotificationCopy.path("product_order", "order123"))
        assertEquals("Mai placed an order for Ceramic cup", NotificationCopy.title(Language.EN, "product_order", "Mai", "Ceramic cup"))
        assertEquals(AppRoute.Post("abc123"), matchAppRoute(NotificationCopy.path("abc123")))
    }

    @Test
    fun longPostMessagesKeepAShortPreview() {
        assertEquals(5000, Limits.MESSAGE_MAX)
        assertEquals(100, Limits.MESSAGE_PREVIEW)
        assertEquals("short", previewPostMessage("short"))
        assertEquals("a".repeat(100), previewPostMessage("a".repeat(100)))
        assertEquals("a".repeat(100), previewPostMessage("a".repeat(101)))
        assertTrue(!postMessageNeedsMore("a".repeat(100)))
        assertTrue(postMessageNeedsMore("a".repeat(101)))
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
