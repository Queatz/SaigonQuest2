package quest.saigon.media

import quest.saigon.Limits
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

class ImageOps {
    fun read(bytes: ByteArray): BufferedImage =
        ImageIO.read(ByteArrayInputStream(bytes)) ?: throw IllegalArgumentException("invalid_photo")

    fun preparePostPhoto(bytes: ByteArray): ByteArray {
        val image = centerCrop916(read(bytes))
        val fitted = limitEdge(image, Limits.PHOTO_MAX_EDGE)
        return writeJpeg(fitted, Limits.JPEG_QUALITY.toFloat())
    }

    fun prepareProductPhoto(bytes: ByteArray): ByteArray {
        val image = centerCropSquare(read(bytes))
        return writeJpeg(limitEdge(image, Limits.PHOTO_MAX_EDGE), Limits.JPEG_QUALITY.toFloat())
    }

    fun prepareChatPhoto(bytes: ByteArray): ByteArray {
        val fitted = limitEdge(read(bytes), Limits.PHOTO_MAX_EDGE)
        return writeJpeg(fitted, Limits.JPEG_QUALITY.toFloat())
    }

    fun prepareProfilePhoto(bytes: ByteArray): ByteArray {
        val fitted = limitEdge(read(bytes), 1600)
        return writeJpeg(fitted, Limits.JPEG_QUALITY.toFloat())
    }

    fun prepareWantPhoto(bytes: ByteArray): ByteArray {
        val fitted = limitEdge(read(bytes), Limits.PHOTO_MAX_EDGE)
        return writeJpeg(fitted, Limits.JPEG_QUALITY.toFloat())
    }

    fun prepareAvatar(image: BufferedImage): ByteArray =
        writePng(limitEdge(trimToContent(image), Limits.AVATAR_MAX_EDGE))

    fun limitEdge(image: BufferedImage, maxEdge: Int): BufferedImage {
        val edge = max(image.width, image.height)
        if (edge <= maxEdge) return image
        val scale = maxEdge.toDouble() / edge
        return scale(image, max(1, (image.width * scale).roundToInt()), max(1, (image.height * scale).roundToInt()))
    }

    fun centerCrop916(image: BufferedImage): BufferedImage {
        val target = 9.0 / 16.0
        val source = image.width.toDouble() / image.height
        val cropWidth: Int
        val cropHeight: Int
        val x: Int
        val y: Int
        if (source > target) {
            cropHeight = image.height
            cropWidth = max(1, (image.height * target).roundToInt())
            x = (image.width - cropWidth) / 2
            y = 0
        } else {
            cropWidth = image.width
            cropHeight = max(1, (image.width / target).roundToInt())
            x = 0
            y = (image.height - cropHeight) / 2
        }
        return image.getSubimage(x, y, min(cropWidth, image.width - x), min(cropHeight, image.height - y))
    }

    fun centerCropSquare(image: BufferedImage): BufferedImage {
        val edge = min(image.width, image.height)
        val x = (image.width - edge) / 2
        val y = (image.height - edge) / 2
        return image.getSubimage(x, y, edge, edge)
    }

    fun orientMask(mask: BufferedImage): BufferedImage {
        val corners = averageLuminance(mask, corner = true)
        val center = averageLuminance(mask, corner = false)
        return if (corners > center + 24) invert(mask) else mask
    }

    fun maskIsUsable(mask: BufferedImage): Boolean {
        var min = 255
        var max = 0
        val stepX = max(1, mask.width / 32)
        val stepY = max(1, mask.height / 32)
        var y = 0
        while (y < mask.height) {
            var x = 0
            while (x < mask.width) {
                val lum = luminance(mask.getRGB(x, y))
                if (lum < min) min = lum
                if (lum > max) max = lum
                x += stepX
            }
            y += stepY
        }
        return max - min >= 40
    }

    fun mergeMaskAsAlpha(color: BufferedImage, mask: BufferedImage): BufferedImage {
        val fitted = if (mask.width == color.width && mask.height == color.height) {
            mask
        } else {
            scale(mask, color.width, color.height, nearest = true)
        }
        val out = BufferedImage(color.width, color.height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until color.height) {
            for (x in 0 until color.width) {
                val rgb = color.getRGB(x, y)
                val alpha = if (luminance(fitted.getRGB(x, y)) <= 128) 0 else 255
                out.setRGB(x, y, (alpha shl 24) or (rgb and 0x00FFFFFF))
            }
        }
        return out
    }

    fun chromaKeyGray(image: BufferedImage): BufferedImage {
        val sample = sampleCornerColor(image)
        val keyed = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val rgb = image.getRGB(x, y)
                val alpha = if (colorDistance(rgb, sample) <= 40.0) 0 else 255
                keyed.setRGB(x, y, (alpha shl 24) or (rgb and 0x00FFFFFF))
            }
        }
        return solidifyAlpha(keyed)
    }

    fun solidifyMask(mask: BufferedImage): BufferedImage {
        val solid = foregroundMask(mask.width, mask.height) { x, y -> luminance(mask.getRGB(x, y)) }
        val out = BufferedImage(mask.width, mask.height, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until mask.height) {
            val row = y * mask.width
            for (x in 0 until mask.width) {
                val value = if (solid[row + x]) 255 else 0
                out.setRGB(x, y, (value shl 16) or (value shl 8) or value)
            }
        }
        return out
    }

    fun solidifyAlpha(image: BufferedImage): BufferedImage {
        val solid = foregroundMask(image.width, image.height) { x, y -> image.getRGB(x, y) ushr 24 }
        if (!alphaWouldChange(image, solid)) return image
        val out = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until image.height) {
            val row = y * image.width
            for (x in 0 until image.width) {
                val rgb = image.getRGB(x, y) and 0x00FFFFFF
                val alpha = if (solid[row + x]) 255 else 0
                out.setRGB(x, y, (alpha shl 24) or rgb)
            }
        }
        return out
    }

    fun trimToContent(image: BufferedImage, alphaThreshold: Int = 12): BufferedImage {
        var minX = image.width
        var minY = image.height
        var maxX = -1
        var maxY = -1
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val alpha = image.getRGB(x, y) ushr 24
                if (alpha <= alphaThreshold) continue
                if (x < minX) minX = x
                if (y < minY) minY = y
                if (x > maxX) maxX = x
                if (y > maxY) maxY = y
            }
        }
        if (maxX < minX) return image
        val x0 = (minX - 1).coerceAtLeast(0)
        val y0 = (minY - 1).coerceAtLeast(0)
        val x1 = (maxX + 1).coerceAtMost(image.width - 1)
        val y1 = (maxY + 1).coerceAtMost(image.height - 1)
        val width = x1 - x0 + 1
        val height = y1 - y0 + 1
        if (width == image.width && height == image.height) return image
        val trimmed = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val graphics = trimmed.createGraphics()
        graphics.drawImage(image, 0, 0, width, height, x0, y0, x1 + 1, y1 + 1, null)
        graphics.dispose()
        return trimmed
    }

    fun writePng(image: BufferedImage): ByteArray {
        val output = ByteArrayOutputStream()
        ImageIO.write(image, "png", output)
        return output.toByteArray()
    }

    fun shrinkForUpload(bytes: ByteArray, maxBytes: Int = 40_000): ByteArray {
        var image = limitEdge(read(bytes), 768)
        var edge = max(image.width, image.height)
        var jpeg = writeJpeg(image, 0.72f)
        while (jpeg.size > maxBytes && edge > 320) {
            edge = max(320, (edge * 0.8).roundToInt())
            image = limitEdge(image, edge)
            jpeg = writeJpeg(image, 0.62f)
        }
        return jpeg
    }

    fun writeJpeg(image: BufferedImage, quality: Float): ByteArray {
        val rgb = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
        val graphics = rgb.createGraphics()
        graphics.color = Color.WHITE
        graphics.fillRect(0, 0, image.width, image.height)
        graphics.drawImage(image, 0, 0, null)
        graphics.dispose()
        val writers = ImageIO.getImageWritersByFormatName("jpeg")
        val writer = writers.next()
        val output = ByteArrayOutputStream()
        ImageIO.createImageOutputStream(output).use { stream ->
            writer.output = stream
            val params = writer.defaultWriteParam
            if (params.canWriteCompressed()) {
                params.compressionMode = ImageWriteParam.MODE_EXPLICIT
                params.compressionQuality = quality.coerceIn(0.5f, 0.95f)
            }
            writer.write(null, IIOImage(rgb, null, null), params)
        }
        writer.dispose()
        return output.toByteArray()
    }

    private fun scale(image: BufferedImage, width: Int, height: Int, nearest: Boolean = false): BufferedImage {
        val type = if (image.colorModel.hasAlpha()) BufferedImage.TYPE_INT_ARGB else BufferedImage.TYPE_INT_RGB
        val scaled = BufferedImage(width, height, type)
        val graphics = scaled.createGraphics()
        graphics.setRenderingHint(
            RenderingHints.KEY_INTERPOLATION,
            if (nearest) RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR else RenderingHints.VALUE_INTERPOLATION_BILINEAR,
        )
        graphics.drawImage(image, 0, 0, width, height, null)
        graphics.dispose()
        return scaled
    }

    private fun foregroundMask(width: Int, height: Int, valueAt: (Int, Int) -> Int): BooleanArray {
        val count = width * height
        val values = IntArray(count)
        var mid = 0
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                val value = valueAt(x, y).coerceIn(0, 255)
                values[row + x] = value
                if (value in 24..230) mid++
            }
        }
        val threshold = if (mid * 100 >= count * 8) 32 else 128
        val foreground = BooleanArray(count) { index -> values[index] >= threshold }
        val background = BooleanArray(count)
        val stack = ArrayDeque<Int>()
        fun push(x: Int, y: Int) {
            if (x < 0 || y < 0 || x >= width || y >= height) return
            val index = y * width + x
            if (background[index] || foreground[index]) return
            background[index] = true
            stack.addLast(index)
        }
        for (x in 0 until width) {
            push(x, 0)
            push(x, height - 1)
        }
        for (y in 0 until height) {
            push(0, y)
            push(width - 1, y)
        }
        while (stack.isNotEmpty()) {
            val index = stack.removeLast()
            val x = index % width
            val y = index / width
            push(x - 1, y)
            push(x + 1, y)
            push(x, y - 1)
            push(x, y + 1)
        }
        for (index in foreground.indices) foreground[index] = !background[index]
        return foreground
    }

    private fun alphaWouldChange(image: BufferedImage, solid: BooleanArray): Boolean {
        for (y in 0 until image.height) {
            val row = y * image.width
            for (x in 0 until image.width) {
                val alpha = image.getRGB(x, y) ushr 24
                val next = if (solid[row + x]) 255 else 0
                if (alpha != next) return true
            }
        }
        return false
    }

    private fun invert(mask: BufferedImage): BufferedImage {
        val out = BufferedImage(mask.width, mask.height, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until mask.height) {
            for (x in 0 until mask.width) {
                val lum = 255 - luminance(mask.getRGB(x, y))
                out.setRGB(x, y, (lum shl 16) or (lum shl 8) or lum)
            }
        }
        return out
    }

    private fun averageLuminance(image: BufferedImage, corner: Boolean): Int {
        val points = if (corner) {
            listOf(0 to 0, image.width - 1 to 0, 0 to image.height - 1, image.width - 1 to image.height - 1)
        } else {
            val x0 = image.width / 3
            val y0 = image.height / 3
            listOf(image.width / 2 to image.height / 2, x0 to y0, image.width - x0 - 1 to image.height - y0 - 1)
        }
        return points.map { (x, y) -> luminance(image.getRGB(x.coerceIn(0, image.width - 1), y.coerceIn(0, image.height - 1))) }
            .average()
            .roundToInt()
    }

    private fun sampleCornerColor(image: BufferedImage): Int {
        val points = listOf(0 to 0, image.width - 1 to 0, 0 to image.height - 1, image.width - 1 to image.height - 1)
        val red = points.map { (x, y) -> (image.getRGB(x, y) shr 16) and 0xFF }.average()
        val green = points.map { (x, y) -> (image.getRGB(x, y) shr 8) and 0xFF }.average()
        val blue = points.map { (x, y) -> image.getRGB(x, y) and 0xFF }.average()
        return (red.roundToInt() shl 16) or (green.roundToInt() shl 8) or blue.roundToInt()
    }

    private fun luminance(rgb: Int): Int {
        val red = (rgb shr 16) and 0xFF
        val green = (rgb shr 8) and 0xFF
        val blue = rgb and 0xFF
        return (0.2126 * red + 0.7152 * green + 0.0722 * blue).roundToInt().coerceIn(0, 255)
    }

    private fun colorDistance(rgb: Int, sample: Int): Double {
        val dr = ((rgb shr 16) and 0xFF) - ((sample shr 16) and 0xFF)
        val dg = ((rgb shr 8) and 0xFF) - ((sample shr 8) and 0xFF)
        val db = (rgb and 0xFF) - (sample and 0xFF)
        return sqrt((dr * dr + dg * dg + db * db).toDouble())
    }
}
