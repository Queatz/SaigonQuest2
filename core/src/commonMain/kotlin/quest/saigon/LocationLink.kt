package quest.saigon

private const val LOCATION_TOKEN_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
private val MAX_LOCATION_TOKEN_LENGTH = (Limits.LOCATION_MAX * 4 * 4 + 2) / 3

fun encodeLocationToken(name: String): String {
    val bytes = name.trim().encodeToByteArray()
    val token = StringBuilder((bytes.size * 4 + 2) / 3)
    var index = 0
    while (index < bytes.size) {
        val first = bytes[index].toInt() and 0xff
        val second = if (index + 1 < bytes.size) bytes[index + 1].toInt() and 0xff else 0
        val third = if (index + 2 < bytes.size) bytes[index + 2].toInt() and 0xff else 0

        token.append(LOCATION_TOKEN_ALPHABET[first ushr 2])
        token.append(LOCATION_TOKEN_ALPHABET[((first and 0x03) shl 4) or (second ushr 4)])
        if (index + 1 < bytes.size) {
            token.append(LOCATION_TOKEN_ALPHABET[((second and 0x0f) shl 2) or (third ushr 6)])
        }
        if (index + 2 < bytes.size) {
            token.append(LOCATION_TOKEN_ALPHABET[third and 0x3f])
        }
        index += 3
    }
    return token.toString()
}

fun decodeLocationToken(token: String): String? {
    if (token.isEmpty() || token.length > MAX_LOCATION_TOKEN_LENGTH || token.length % 4 == 1) return null

    val bytes = ArrayList<Byte>(token.length * 3 / 4)
    var buffer = 0
    var bits = 0
    for (character in token) {
        val value = LOCATION_TOKEN_ALPHABET.indexOf(character)
        if (value < 0) return null
        buffer = (buffer shl 6) or value
        bits += 6
        if (bits >= 8) {
            bits -= 8
            bytes += ((buffer ushr bits) and 0xff).toByte()
            buffer = buffer and ((1 shl bits) - 1)
        }
    }
    if (buffer != 0) return null

    val name = try {
        bytes.toByteArray().decodeToString(throwOnInvalidSequence = true).trim()
    } catch (_: Exception) {
        return null
    }
    return name.takeIf { it.isNotBlank() && it.length <= Limits.LOCATION_MAX }
}
