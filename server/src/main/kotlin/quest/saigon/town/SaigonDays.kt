package quest.saigon.town

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

object SaigonDays {
    val ZONE: ZoneOffset = ZoneOffset.ofHours(7)
    const val LOOKBACK_DAYS = 30L

    fun dayKey(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis).atOffset(ZONE).toLocalDate().toString()

    fun startMillis(day: String): Long =
        LocalDate.parse(day).atStartOfDay(ZONE).toInstant().toEpochMilli()

    fun range(now: Long, days: Long = LOOKBACK_DAYS): List<String> {
        val today = Instant.ofEpochMilli(now).atOffset(ZONE).toLocalDate()
        val start = today.minusDays(days - 1)
        return generateSequence(start) { it.plusDays(1) }
            .takeWhile { !it.isAfter(today) }
            .map { it.toString() }
            .toList()
    }
}
