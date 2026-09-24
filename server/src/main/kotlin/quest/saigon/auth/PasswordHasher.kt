package quest.saigon.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import quest.saigon.Limits

object PasswordHasher {
    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256
    private val random = SecureRandom()

    fun hash(password: String): String {
        val salt = ByteArray(16).also(random::nextBytes)
        return encode(password, salt, ITERATIONS)
    }

    fun verify(password: String, stored: String): Boolean {
        val parts = stored.split('$')
        if (parts.size != 4 || parts[0] != "pbkdf2") return false
        val iterations = parts[1].toIntOrNull() ?: return false
        if (iterations !in 10_000..2_000_000) return false
        val salt = decode(parts[2]) ?: return false
        val expected = decode(parts[3]) ?: return false
        val actual = derive(password, salt, iterations)
        return MessageDigest.isEqual(expected, actual)
    }

    fun acceptable(password: String): Boolean =
        password.length in Limits.PASSWORD_MIN..Limits.PASSWORD_MAX

    private fun encode(password: String, salt: ByteArray, iterations: Int): String {
        val key = derive(password, salt, iterations)
        return "pbkdf2$$iterations$${b64(salt)}$${b64(key)}"
    }

    private fun derive(password: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    private fun b64(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private fun decode(value: String): ByteArray? =
        try {
            Base64.getUrlDecoder().decode(value)
        } catch (_: IllegalArgumentException) {
            null
        }
}
