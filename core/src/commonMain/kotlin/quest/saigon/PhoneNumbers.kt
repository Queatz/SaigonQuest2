package quest.saigon

object PhoneNumbers {
    fun canonicalize(raw: String): String? {
        val digits = raw.filter { it.isDigit() }
        if (digits.length !in 8..15) return null
        val normalized = if (digits.length == 10 && digits.startsWith('0')) {
            "84" + digits.drop(1)
        } else {
            digits
        }
        return normalized.takeIf { it.length in 8..15 }
    }

    fun display(canonical: String): String =
        if (canonical.startsWith('0')) canonical else "+$canonical"

    fun zaloUrl(canonical: String): String = "https://zalo.me/$canonical"
}
