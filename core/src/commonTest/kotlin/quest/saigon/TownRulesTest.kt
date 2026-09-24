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
        assertEquals(50, Limits.TYPEWRITER_MILLIS_PER_CHAR)
    }

    @Test
    fun bothLanguagesCoverTheSameCopy() {
        Copy.keys().forEach { key ->
            assertTrue(Copy.text(Language.EN, key).isNotBlank())
            assertTrue(Copy.text(Language.VI, key).isNotBlank())
        }
        assertEquals("Reply to Linh", Copy.text(Language.EN, "replyTo", mapOf("name" to "Linh")))
        assertEquals("Trả lời Linh", Copy.text(Language.VI, "replyTo", mapOf("name" to "Linh")))
    }
}
