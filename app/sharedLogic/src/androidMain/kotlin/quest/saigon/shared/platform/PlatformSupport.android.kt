package quest.saigon.shared.platform

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.saigonDataStore: DataStore<Preferences> by preferencesDataStore(name = "saigon_town_prefs")

object AndroidPlatformContext {
    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    internal fun contextOrNull(): Context? = appContext
}

private object KeystoreCipher {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "saigon_town_session_aes"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    private fun secretKey(): SecretKey? = try {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = ks.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) {
            existing
        } else {
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            generator.init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generator.generateKey()
        }
    } catch (_: Throwable) {
        null
    }

    fun encrypt(plain: String): String {
        val key = secretKey() ?: return "plain:$plain"
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            val encrypted = cipher.doFinal(plain.toByteArray(StandardCharsets.UTF_8))
            val combined = ByteArray(1 + iv.size + encrypted.size)
            combined[0] = iv.size.toByte()
            iv.copyInto(combined, destinationOffset = 1)
            encrypted.copyInto(combined, destinationOffset = 1 + iv.size)
            "enc:" + Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (_: Throwable) {
            "plain:$plain"
        }
    }

    fun decrypt(stored: String): String? {
        if (stored.startsWith("plain:")) return stored.removePrefix("plain:")
        if (!stored.startsWith("enc:")) return stored
        val key = secretKey() ?: return null
        return try {
            val combined = Base64.decode(stored.removePrefix("enc:"), Base64.NO_WRAP)
            if (combined.size < 2) return null
            val ivLen = combined[0].toInt() and 0xFF
            if (combined.size <= 1 + ivLen) return null
            val iv = combined.copyOfRange(1, 1 + ivLen)
            val encrypted = combined.copyOfRange(1 + ivLen, combined.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            String(cipher.doFinal(encrypted), StandardCharsets.UTF_8)
        } catch (_: Throwable) {
            null
        }
    }
}

class AndroidDataStoreTokenStorage(private val context: Context) : TokenStorage {
    private val tokenKey = stringPreferencesKey("session_token")
    private val langKey = stringPreferencesKey("language_pref")
    private val fcmKey = stringPreferencesKey("fcm_token")

    override suspend fun readSessionToken(): String? {
        val raw = context.saigonDataStore.data.map { it[tokenKey] }.first() ?: return null
        return KeystoreCipher.decrypt(raw)?.takeIf { it.isNotBlank() }
    }

    override suspend fun writeSessionToken(token: String?) {
        context.saigonDataStore.edit { prefs ->
            val clean = token?.takeIf { it.isNotBlank() }
            if (clean == null) prefs.remove(tokenKey)
            else prefs[tokenKey] = KeystoreCipher.encrypt(clean)
        }
    }

    override suspend fun readLanguage(): String =
        context.saigonDataStore.data.map { it[langKey] ?: "system" }.first()

    override suspend fun writeLanguage(preference: String) {
        context.saigonDataStore.edit { prefs ->
            prefs[langKey] = preference.ifBlank { "system" }
        }
    }

    override suspend fun readFcmToken(): String? =
        context.saigonDataStore.data.map { it[fcmKey] }.first()?.takeIf { it.isNotBlank() }

    override suspend fun writeFcmToken(token: String?) {
        context.saigonDataStore.edit { prefs ->
            val clean = token?.takeIf { it.isNotBlank() }
            if (clean == null) prefs.remove(fcmKey)
            else prefs[fcmKey] = clean
        }
    }
}

actual fun createPlatformTokenStorage(): TokenStorage {
    val context = AndroidPlatformContext.contextOrNull()
    return if (context != null) AndroidDataStoreTokenStorage(context) else InMemoryTokenStorage()
}

actual fun systemLanguageTag(): String = Locale.getDefault().toLanguageTag()

actual fun createHttpClientEngine(): HttpClientEngine = OkHttp.create {
    config {
        connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        callTimeout(90, java.util.concurrent.TimeUnit.SECONDS)
    }
}

actual fun defaultServerOrigin(): String = "https://saigon.town"

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

actual val includeCookieHeaderOnRequests: Boolean = true

actual val clientPlatform: String = "android"
