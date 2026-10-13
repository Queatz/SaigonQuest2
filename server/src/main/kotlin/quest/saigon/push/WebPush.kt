package quest.saigon.push

import java.math.BigInteger
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.Signature
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPrivateKeySpec
import java.security.spec.ECPublicKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class VapidKeys(
    val publicKey: String,
    val privateKey: String,
    val subject: String,
    internal val ecPrivate: ECPrivateKey,
    internal val publicBytes: ByteArray,
) {
    companion object {
        const val DEFAULT_SUBJECT = "mailto:hello@saigon.town"

        fun generate(subject: String = DEFAULT_SUBJECT): VapidKeys {
            val generator = KeyPairGenerator.getInstance("EC")
            generator.initialize(ECGenParameterSpec("secp256r1"))
            val pair = generator.generateKeyPair()
            val publicBytes = uncompressed(pair.public as ECPublicKey)
            val privateBytes = unsigned((pair.private as ECPrivateKey).s, 32)
            return VapidKeys(
                publicKey = encodeBase64Url(publicBytes),
                privateKey = encodeBase64Url(privateBytes),
                subject = subject.ifBlank { DEFAULT_SUBJECT },
                ecPrivate = pair.private as ECPrivateKey,
                publicBytes = publicBytes,
            )
        }

        fun parse(publicKey: String?, privateKey: String?, subject: String?): VapidKeys? {
            if (publicKey.isNullOrBlank() || privateKey.isNullOrBlank()) return null
            return try {
                val publicBytes = decodeBase64Url(publicKey.trim())
                val privateBytes = decodeBase64Url(privateKey.trim())
                require(publicBytes.size == 65 && publicBytes[0] == 0x04.toByte())
                val scalar = unsigned(BigInteger(1, privateBytes), 32)
                val factory = KeyFactory.getInstance("EC")
                val private = factory.generatePrivate(ECPrivateKeySpec(BigInteger(1, scalar), ecParams())) as ECPrivateKey
                VapidKeys(
                    publicKey = publicKey.trim(),
                    privateKey = privateKey.trim(),
                    subject = subject?.trim()?.ifBlank { null } ?: DEFAULT_SUBJECT,
                    ecPrivate = private,
                    publicBytes = publicBytes,
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}

class WebPushRequest(
    val endpoint: String,
    val headers: Map<String, String>,
    val body: ByteArray,
)

enum class WebPushResult {
    SENT,
    GONE,
    FAILED,
    SKIPPED,
}

object WebPush {
    private val random = SecureRandom()

    fun request(
        keys: VapidKeys,
        endpoint: String,
        p256dh: String,
        auth: String,
        payload: String,
        nowMillis: Long = System.currentTimeMillis(),
        ttlSeconds: Int = 86_400,
    ): WebPushRequest {
        val uaPublic = decodePublicKey(p256dh)
        val uaAuth = decodeBase64Url(auth)
        require(uaAuth.size == 16) { "auth must be 16 bytes" }
        val asPair = generateAsKeys()
        val asPublic = uncompressed(asPair.public as ECPublicKey)
        val secret = ecdh(asPair.private as ECPrivateKey, uaPublic)
        val ikm = hkdf(
            salt = uaAuth,
            ikm = secret,
            info = concat("WebPush: info".toByteArray(StandardCharsets.US_ASCII), byteArrayOf(0), uncompressed(uaPublic), asPublic),
            length = 32,
        )
        val salt = ByteArray(16).also(random::nextBytes)
        val cek = hkdf(salt, ikm, "Content-Encoding: aes128gcm".asciiNul(), 16)
        val nonce = hkdf(salt, ikm, "Content-Encoding: nonce".asciiNul(), 12)
        val padded = payload.toByteArray(StandardCharsets.UTF_8) + 0x02
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(cek, "AES"), GCMParameterSpec(128, nonce))
        val encrypted = cipher.doFinal(padded)
        val header = ByteBuffer.allocate(16 + 4 + 1 + asPublic.size)
            .put(salt)
            .putInt(4096)
            .put(asPublic.size.toByte())
            .put(asPublic)
            .array()
        val body = header + encrypted
        val audience = audience(endpoint)
        val jwt = vapidJwt(keys, audience, nowMillis)
        return WebPushRequest(
            endpoint = endpoint,
            headers = mapOf(
                "Authorization" to "vapid t=$jwt, k=${encodeBase64Url(keys.publicBytes)}",
                "TTL" to ttlSeconds.toString(),
                "Urgency" to "normal",
                "Content-Encoding" to "aes128gcm",
                "Content-Type" to "application/octet-stream",
            ),
            body = body,
        )
    }

    fun audience(endpoint: String): String {
        val uri = URI(endpoint)
        val defaultPort = when (uri.scheme?.lowercase()) {
            "https" -> 443
            "http" -> 80
            else -> -1
        }
        val port = if (uri.port != -1 && uri.port != defaultPort) ":${uri.port}" else ""
        return "${uri.scheme}://${uri.host}$port"
    }

    private fun vapidJwt(keys: VapidKeys, audience: String, nowMillis: Long): String {
        val exp = (nowMillis / 1000L) + 12 * 60 * 60
        val header = encodeBase64Url("""{"typ":"JWT","alg":"ES256"}""".toByteArray(StandardCharsets.US_ASCII))
        val payload = encodeBase64Url(
            """{"aud":${jsonString(audience)},"exp":$exp,"sub":${jsonString(keys.subject)}}"""
                .toByteArray(StandardCharsets.US_ASCII),
        )
        val signing = "$header.$payload"
        val signature = signEs256(keys.ecPrivate, signing.toByteArray(StandardCharsets.US_ASCII))
        return "$signing.${encodeBase64Url(signature)}"
    }

    private fun signEs256(privateKey: ECPrivateKey, data: ByteArray): ByteArray {
        val signature = try {
            Signature.getInstance("SHA256withECDSAinP1363Format")
        } catch (_: Exception) {
            Signature.getInstance("SHA256withECDSA")
        }
        signature.initSign(privateKey)
        signature.update(data)
        val raw = signature.sign()
        return if (raw.size == 64) raw else derToP1363(raw)
    }

    private fun generateAsKeys() = KeyPairGenerator.getInstance("EC").let { generator ->
        generator.initialize(ECGenParameterSpec("secp256r1"))
        generator.generateKeyPair()
    }

    private fun ecdh(privateKey: ECPrivateKey, publicKey: ECPublicKey): ByteArray {
        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(privateKey)
        agreement.doPhase(publicKey, true)
        return unsigned(BigInteger(1, agreement.generateSecret()), 32)
    }

    private fun decodePublicKey(p256dh: String): ECPublicKey {
        val bytes = decodeBase64Url(p256dh)
        require(bytes.size == 65 && bytes[0] == 0x04.toByte()) { "p256dh must be an uncompressed P-256 key" }
        val x = BigInteger(1, bytes.copyOfRange(1, 33))
        val y = BigInteger(1, bytes.copyOfRange(33, 65))
        val spec = ECPublicKeySpec(ECPoint(x, y), ecParams())
        return KeyFactory.getInstance("EC").generatePublic(spec) as ECPublicKey
    }
}

private fun hkdf(salt: ByteArray, ikm: ByteArray, info: ByteArray, length: Int): ByteArray {
    val extract = hmacSha256(salt, ikm)
    return hmacSha256(extract, info + 0x01).copyOf(length)
}

private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(key, "HmacSHA256"))
    return mac.doFinal(data)
}

private fun String.asciiNul(): ByteArray = toByteArray(StandardCharsets.US_ASCII) + 0x00

private fun concat(vararg parts: ByteArray): ByteArray {
    val size = parts.sumOf { it.size }
    val out = ByteArray(size)
    var offset = 0
    parts.forEach { part ->
        part.copyInto(out, offset)
        offset += part.size
    }
    return out
}

private fun jsonString(value: String): String = buildString {
    append('"')
    value.forEach { ch ->
        when (ch) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            else -> append(ch)
        }
    }
    append('"')
}

private fun derToP1363(der: ByteArray): ByteArray {
    // SEQUENCE { INTEGER r, INTEGER s }
    var index = 2
    if (der[1].toInt() and 0x80 != 0) index += der[1].toInt() and 0x7F
    require(der[index] == 0x02.toByte())
    val rLen = der[index + 1].toInt() and 0xFF
    val r = der.copyOfRange(index + 2, index + 2 + rLen)
    index += 2 + rLen
    require(der[index] == 0x02.toByte())
    val sLen = der[index + 1].toInt() and 0xFF
    val s = der.copyOfRange(index + 2, index + 2 + sLen)
    return unsigned(BigInteger(1, r), 32) + unsigned(BigInteger(1, s), 32)
}

internal fun encodeBase64Url(bytes: ByteArray): String =
    Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

internal fun decodeBase64Url(value: String): ByteArray {
    val padded = value.replace('-', '+').replace('_', '/').let { raw ->
        raw + "=".repeat((4 - raw.length % 4) % 4)
    }
    return Base64.getDecoder().decode(padded)
}

internal fun uncompressed(key: ECPublicKey): ByteArray =
    byteArrayOf(0x04) + unsigned(key.w.affineX, 32) + unsigned(key.w.affineY, 32)

internal fun unsigned(value: BigInteger, size: Int): ByteArray {
    val raw = value.toByteArray()
    val start = if (raw.isNotEmpty() && raw[0] == 0.toByte()) 1 else 0
    val length = raw.size - start
    return when {
        length == size -> raw.copyOfRange(start, raw.size)
        length > size -> raw.copyOfRange(raw.size - size, raw.size)
        else -> ByteArray(size - length) + raw.copyOfRange(start, raw.size)
    }
}

internal fun ecParams(): ECParameterSpec {
    val parameters = java.security.AlgorithmParameters.getInstance("EC")
    parameters.init(ECGenParameterSpec("secp256r1"))
    return parameters.getParameterSpec(ECParameterSpec::class.java)
}
