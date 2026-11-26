package quest.saigon

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WantSpaceLayoutTest {
    @Test
    fun animateInDelaysSpanTwoSecondsSequentially() {
        assertEquals(0L, WantSpaceLayout.animateInDelayMs(0, 1))
        assertEquals(0L, WantSpaceLayout.animateInDelayMs(0, 10))
        val delays = (0 until 10).map { WantSpaceLayout.animateInDelayMs(it, 10) }
        for (i in 1 until delays.size) {
            assertTrue(delays[i] > delays[i - 1], "Expected strictly increasing delay at index $i")
        }
        assertEquals(
            WantSpaceLayout.ANIMATE_IN_WINDOW_MS,
            delays.last() + WantSpaceLayout.ANIMATE_IN_ITEM_MS,
        )
    }

    @Test
    fun positionsAreDeterministicAndNonOverlapping() {
        val ids = (1..30).map { "want-$it" }
        val first = WantSpaceLayout.layout(ids)
        val second = WantSpaceLayout.layout(ids)
        assertEquals(first, second)
        assertEquals(30, first.size)

        val w = WantSpaceLayout.CARD_WIDTH_PX
        val h = WantSpaceLayout.CARD_HEIGHT_PX
        val list = first.values.toList()
        for (i in list.indices) {
            for (j in i + 1 until list.size) {
                val dx = abs(list[i].x - list[j].x)
                val dy = abs(list[i].y - list[j].y)
                assertTrue(
                    dx >= w || dy >= h,
                    "Cards $i and $j overlap: dx=$dx, dy=$dy",
                )
            }
        }
        assertTrue(abs(list.first().x) < 40.0 && abs(list.first().y) < 40.0)
    }
}
