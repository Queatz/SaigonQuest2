package quest.saigon

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PlaceEmojiTest {
    @Test
    fun knownPlaceTypesHaveOneCategoryEmoji() {
        assertEquals("☕", PlaceEmoji.forPrimaryType("cafe"))
        assertEquals("📚", PlaceEmoji.forPrimaryType("library"))
    }

    @Test
    fun unknownPlaceTypesUseThePickerDefault() {
        assertNull(PlaceEmoji.forPrimaryType("point_of_interest"))
        assertNull(PlaceEmoji.forPrimaryType(null))
    }
}
