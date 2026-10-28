package quest.saigon.web

import quest.saigon.Limits
import web.dom.document

internal fun preparePhoto(
    file: dynamic,
    crop916: Boolean,
    done: (dynamic) -> Unit,
    fail: () -> Unit,
    cropSquare: Boolean = false,
) {
    val reader = js("new FileReader()")
    reader.onerror = { fail() }
    reader.onload = {
        val img = js("new Image()")
        img.onerror = { fail() }
        img.onload = {
            val sw = num(img.naturalWidth).toInt().coerceAtLeast(1)
            val sh = num(img.naturalHeight).toInt().coerceAtLeast(1)
            val target = if (cropSquare) 1.0 else 9.0 / 16.0
            val source = sw.toDouble() / sh
            val cropW: Int
            val cropH: Int
            val sx: Int
            val sy: Int
            if (cropSquare) {
                val edge = minOf(sw, sh)
                cropW = edge
                cropH = edge
                sx = (sw - edge) / 2
                sy = (sh - edge) / 2
            } else if (!crop916) {
                cropW = sw
                cropH = sh
                sx = 0
                sy = 0
            } else if (source > target) {
                cropH = sh
                cropW = (sh * target).toInt().coerceAtLeast(1)
                sx = (sw - cropW) / 2
                sy = 0
            } else {
                cropW = sw
                cropH = (sw / target).toInt().coerceAtLeast(1)
                sx = 0
                sy = (sh - cropH) / 2
            }
            var outW = cropW
            var outH = cropH
            val maxEdge = Limits.PHOTO_MAX_EDGE
            val edge = maxOf(outW, outH)
            if (edge > maxEdge) {
                val scale = maxEdge.toDouble() / edge
                outW = (outW * scale).toInt().coerceAtLeast(1)
                outH = (outH * scale).toInt().coerceAtLeast(1)
            }
            val canvas = document.createElement("canvas")
            canvas.asDynamic().width = outW
            canvas.asDynamic().height = outH
            val context = canvas.asDynamic().getContext("2d")
            context.drawImage(img, sx, sy, cropW, cropH, 0, 0, outW, outH)
            canvas.asDynamic().toBlob({ blob: dynamic ->
                if (blob == null || blob == undefined) fail() else done(blob)
            }, "image/jpeg", Limits.JPEG_QUALITY)
        }
        img.src = reader.result
    }
    reader.readAsDataURL(file)
}
