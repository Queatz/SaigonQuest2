package quest.saigon

data class CalmGradient(val startArgb: Long, val endArgb: Long)

object CalmGradients {
    private val palette = listOf(
        CalmGradient(0xFFE0EDEA, 0xFFB6D4CF),
        CalmGradient(0xFFE8E3F1, 0xFFCFC5E3),
        CalmGradient(0xFFF0E1D8, 0xFFE7C0AC),
        CalmGradient(0xFFDDE7F0, 0xFFB9CEE3),
        CalmGradient(0xFFE7EFD8, 0xFFC7D8B7),
        CalmGradient(0xFFEEDFEA, 0xFFD6C0D4),
        CalmGradient(0xFFF0E9D7, 0xFFE0D2B2),
        CalmGradient(0xFFDDEBE8, 0xFFBFD8D1),
    )

    fun forKey(key: String): CalmGradient {
        val index = (key.hashCode() and Int.MAX_VALUE) % palette.size
        return palette[index]
    }
}