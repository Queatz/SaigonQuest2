package quest.saigon.ai

import quest.saigon.media.ImageOps

fun interface WantPhotoRenderer {
    fun render(wantName: String): ByteArray
}

class WantPipeline(
    private val qwen: QwenClient,
    private val images: ImageOps = ImageOps(),
) : WantPhotoRenderer {
    fun buildSafePrompt(wantName: String): String {
        if (!WantPrompts.isLocallySafe(wantName)) {
            throw UnsafeWantPromptException("unsafe_local")
        }
        val screening = qwen.chat(
            system = WantPrompts.SAFETY_SYSTEM,
            user = WantPrompts.userPrompt(wantName),
            temperature = 0.1,
            timeoutSeconds = 45,
        ) ?: throw QwenException("safety_check_unavailable")
        val decision = WantPrompts.parseSafetyResponse(screening)
            ?: throw QwenException("safety_check_invalid")
        return when (decision) {
            WantPromptSafety.Unsafe -> throw UnsafeWantPromptException("unsafe_model")
            is WantPromptSafety.Safe -> WantPrompts.handDrawnPrompt(decision.scene)
        }
    }

    override fun render(wantName: String): ByteArray {
        val safePrompt = buildSafePrompt(wantName)
        val generated = qwen.generateImage(
            prompt = safePrompt,
            size = "1280*720",
            negativePrompt = WantPrompts.NEGATIVE_PROMPT,
        )
        return images.prepareWantPhoto(generated)
    }
}
