package quest.saigon.ai

import org.slf4j.LoggerFactory
import quest.saigon.media.ImageOps

fun interface AvatarRenderer {
    fun render(profilePhoto: ByteArray, prompt: String): ByteArray
}

class AvatarPipeline(
    private val qwen: QwenClient,
    private val images: ImageOps = ImageOps(),
) : AvatarRenderer {
    private val log = LoggerFactory.getLogger(AvatarPipeline::class.java)

    override fun render(profilePhoto: ByteArray, prompt: String): ByteArray {
        val expanded = try {
            qwen.expandPrompt(prompt)
        } catch (error: Exception) {
            log.info("Prompt expansion skipped: {}", error.message)
            null
        }
        val generated = qwen.editImage(profilePhoto, AvatarPrompts.editInstruction(prompt, expanded), "image/jpeg")
        val color = images.read(generated)
        val segmented = qwen.segment(generated)
        val maskBytes = segmented ?: try {
            qwen.editImage(generated, AvatarPrompts.MASK_INSTRUCTION.trim(), "image/png")
        } catch (error: Exception) {
            log.info("Mask generation skipped: {}", error.message)
            null
        }
        val cutout = if (maskBytes != null) {
            val oriented = images.orientMask(images.read(maskBytes))
            if (images.maskIsUsable(oriented)) {
                log.info("Merged {} mask as alpha", if (segmented != null) "instance-segmentation" else "image")
                images.mergeMaskAsAlpha(color, oriented)
            } else {
                images.chromaKeyGray(color)
            }
        } else {
            images.chromaKeyGray(color)
        }
        return images.writePng(images.trimToContent(cutout))
    }
}
