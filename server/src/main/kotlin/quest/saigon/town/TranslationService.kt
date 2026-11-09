package quest.saigon.town

import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.Executors

fun interface Translator {
    fun translate(fields: Map<String, String>, locales: List<String>): Map<String, Map<String, String>>
}

class TranslationService(
    private val translator: Translator = Translator { _, _ -> error("translator_not_configured") },
    private val configured: Boolean = false,
    private val executor: Executor = Executors.newFixedThreadPool(2) { runnable ->
        Thread(runnable, "translation").apply { isDaemon = true }
    },
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val log = LoggerFactory.getLogger(TranslationService::class.java)
    private val inflight = ConcurrentHashMap.newKeySet<String>()

    fun ensure(
        id: String,
        fields: Map<String, String>,
        current: TranslationBlob?,
        persist: (TranslationBlob) -> Unit,
        maxLengths: Map<String, Int> = emptyMap(),
    ): TranslationBlob? {
        if (!configured || fields.isEmpty()) return current
        val sourceFields = fields.toMap()
        val hash = sourceHash(sourceFields)
        val now = clock()
        if (current?.sourceHash == hash && current.status == STATUS_READY) return current
        if (
            current?.sourceHash == hash && current.status == STATUS_FAILED &&
            now - current.updatedAt < FAILED_COOLDOWN_MILLIS
        ) return current

        val pending = if (current?.sourceHash == hash && current.status == STATUS_PENDING) {
            current
        } else {
            TranslationBlob(hash, STATUS_PENDING, updatedAt = now)
        }
        val key = "$id:$hash"
        if (!inflight.add(key)) return current ?: pending

        try {
            executor.execute {
                try {
                    if (current?.sourceHash != hash || current.status != STATUS_PENDING) {
                        persist(pending)
                    }
                    val translations = translator.translate(sourceFields, LOCALES)
                    val complete = LOCALES.associateWith { locale ->
                        sourceFields.keys.associateWith { field ->
                            val text = if (sourceFields[field].isNullOrBlank()) {
                                ""
                            } else {
                                translations[locale]?.get(field)?.trim()
                                    ?.takeIf { it.isNotEmpty() }
                                    ?: throw IllegalArgumentException("translation_missing:$locale:$field")
                            }
                            val maxLength = maxLengths[field]
                            if (maxLength == null) text else text.take(maxLength)
                        }
                    }
                    persist(TranslationBlob(hash, STATUS_READY, updatedAt = clock(), locales = complete))
                } catch (error: Exception) {
                    val failed = TranslationBlob(
                        sourceHash = hash,
                        status = STATUS_FAILED,
                        error = error.javaClass.simpleName.take(180),
                        updatedAt = clock(),
                    )
                    try {
                        persist(failed)
                    } catch (persistError: Exception) {
                        log.warn("Translation {} failed to persist failure state: {}", id, persistError.javaClass.simpleName)
                    }
                    log.warn("Translation {} failed: {}", id, error.javaClass.simpleName)
                } finally {
                    inflight.remove(key)
                }
            }
        } catch (error: Exception) {
            inflight.remove(key)
            throw error
        }
        return pending
    }

    fun override(fields: Map<String, String>, locales: Map<String, Map<String, String>>): TranslationBlob =
        TranslationBlob(
            sourceHash = sourceHash(fields),
            status = STATUS_READY,
            updatedAt = clock(),
            locales = locales,
        )

    companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_READY = "ready"
        const val STATUS_FAILED = "failed"
        const val FAILED_COOLDOWN_MILLIS = 5 * 60_000L
        val LOCALES = listOf("en", "vi")

        fun sourceHash(fields: Map<String, String>): String {
            val source = fields.toSortedMap().entries.joinToString("\u0000") { (field, value) ->
                "$field\u0001$value"
            }
            return MessageDigest.getInstance("SHA-256")
                .digest(source.toByteArray(Charsets.UTF_8))
                .joinToString("") { byte -> "%02x".format(byte) }
        }
    }
}