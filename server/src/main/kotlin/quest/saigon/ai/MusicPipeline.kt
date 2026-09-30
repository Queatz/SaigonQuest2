package quest.saigon.ai

import org.slf4j.LoggerFactory
import quest.saigon.MusicNames

data class MusicRender(
    val bytes: ByteArray,
    val name: String,
    val lyrics: String? = null,
)

fun interface MusicRenderer {
    fun render(prompt: String, gender: String?, instrumental: Boolean): MusicRender
}

class MusicPipeline(
    private val qwen: QwenClient,
) : MusicRenderer {
    private val log = LoggerFactory.getLogger(MusicPipeline::class.java)

    override fun render(prompt: String, gender: String?, instrumental: Boolean): MusicRender {
        val guided = MusicPrompts.generationPrompt(prompt)
        val titled = try {
            qwen.musicTitle(guided)
        } catch (error: Exception) {
            log.info("Music title skipped: {}", error.message)
            null
        }
        val generated = qwen.generateMusic(guided, gender, instrumental)
        val name = titled
            ?: MusicNames.fromLyrics(generated.lyrics)
            ?: MusicNames.fromPrompt(prompt)
        return MusicRender(generated.bytes, name, generated.lyrics)
    }
}
