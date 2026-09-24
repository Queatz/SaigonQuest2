package quest.saigon.auth

import java.security.SecureRandom
import java.util.Base64

object SessionTokens {
    private val random = SecureRandom()

    fun new(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
