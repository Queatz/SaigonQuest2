package quest.saigon

import quest.saigon.town.SaigonDays
import kotlin.test.Test
import kotlin.test.assertEquals

class SaigonDaysTest {
    @Test
    fun dayKeyUsesHoChiMinhOffset() {
        val start = SaigonDays.startMillis("2023-11-15")
        assertEquals("2023-11-14", SaigonDays.dayKey(start - 1))
        assertEquals("2023-11-15", SaigonDays.dayKey(start))
        assertEquals("2023-11-15", SaigonDays.dayKey(1_700_000_000_000L))
    }

    @Test
    fun rangeIsInclusiveLookbackEndingToday() {
        val start = SaigonDays.startMillis("2023-11-15")
        val days = SaigonDays.range(start)
        assertEquals(30, days.size)
        assertEquals("2023-10-17", days.first())
        assertEquals("2023-11-15", days.last())
        assertEquals(7, SaigonDays.range(start, 7).size)
    }
}
