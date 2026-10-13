package quest.saigon.web

import web.dom.document
import web.html.HTMLElement
import web.window.window
import kotlin.math.PI
import kotlin.math.min

private const val SHARE_PX = 1080.0
private const val SHARE_FONT =
    "\"Be Vietnam Pro\", Nunito, \"Avenir Next\", \"Segoe UI\", \"Noto Sans\", sans-serif"

private val newClipboardItem: (dynamic) -> dynamic =
    js("function(data) { return new ClipboardItem(data); }")

internal fun shareButton(post: PostCard, host: HTMLElement): HTMLElement =
    el("button", "share-btn") { button ->
        button.setAttribute("type", "button")
        button.setAttribute("aria-label", Town.t("share"))
        button.asDynamic().innerHTML = """
            <svg viewBox="0 0 24 24" aria-hidden="true">
                <path fill="currentColor" stroke="currentColor" stroke-width="1.75" stroke-linejoin="miter" stroke-miterlimit="4" d="M22.15 9.15 L14.2 2.6 L14.2 6.45 C9.2 6.45 5.2 13.2 3.15 21.65 C5.9 15.6 9.5 10.85 14.2 10.85 L14.2 15.7 Z"/>
            </svg>
        """.trimIndent()
        button.onClick {
            http("POST", "/api/posts/${post.id}/share", "{}") { _, _ -> }
            openShare(post, host)
        }
    }

internal fun openShare(post: PostCard, host: HTMLElement) {
    if (host.asDynamic().querySelector(".share-sheet") != null) return
    val backdrop = el("div", "dialog-backdrop share-sheet")
    val panel = el("div", "share-panel")
    panel.setAttribute("role", "dialog")
    panel.setAttribute("aria-modal", "true")
    panel.setAttribute("aria-label", Town.t("share"))
    panel.setAttribute("tabindex", "-1")

    val preview = el("div", "share-preview")
    preview.setAttribute("aria-busy", "true")
    preview.appendChild(el("span", "avatar-spinner"))
    val image = el("img", "share-image")
    image.setAttribute("alt", "${post.authorName}. ${Town.t("appName")}")
    image.setAttribute("draggable", "false")
    image.asDynamic().hidden = true
    preview.appendChild(image)

    var blob: dynamic = null
    var previewUrl: String? = null
    var closed = false
    var onKey: dynamic = null

    fun dismiss() {
        if (closed) return
        closed = true
        if (onKey != null) window.asDynamic().removeEventListener("keydown", onKey)
        if (previewUrl != null) window.asDynamic().URL.revokeObjectURL(previewUrl)
        backdrop.asDynamic().remove()
    }

    onKey = { event: dynamic ->
        if (event.key == "Escape") {
            event.preventDefault()
            dismiss()
        }
    }
    window.asDynamic().addEventListener("keydown", onKey)

    val download = el("button", "primary", text = Town.t("downloadImage")) { button ->
        button.setAttribute("type", "button")
        button.setAttribute("disabled", "disabled")
        button.onClick {
            val data = blob ?: return@onClick
            downloadBlob(data, "saigon-town.png")
        }
    }
    val copy = el("button", "secondary", text = Town.t("copyImage")) { button ->
        button.setAttribute("type", "button")
        button.setAttribute("disabled", "disabled")
        button.onClick {
            val data = blob ?: return@onClick
            copyImageBlob(data) { ok ->
                toast(host, Town.t(if (ok) "imageCopied" else "copyFailed"))
            }
        }
    }
    panel.appendChild(preview)
    panel.appendChild(download)
    panel.appendChild(copy)
    panel.appendChild(el("button", "text-button", text = Town.t("close")) { button ->
        button.setAttribute("type", "button")
        button.onClick { dismiss() }
    })
    backdrop.appendChild(panel)
    backdrop.closeOnOutsideClick { dismiss() }
    host.appendChild(backdrop)
    window.asDynamic().setTimeout({ panel.asDynamic().focus() }, 30)

    renderShareCard(post) { data ->
        if (closed) return@renderShareCard
        val spinner = preview.asDynamic().querySelector(".avatar-spinner")
        if (spinner != null && spinner != undefined) spinner.remove()
        preview.setAttribute("aria-busy", "false")
        if (data == null) {
            preview.appendChild(el("p", "share-error", text = Town.t("shareFailed")))
            toast(host, Town.t("shareFailed"))
            return@renderShareCard
        }
        blob = data
        val url = window.asDynamic().URL.createObjectURL(data) as String
        previewUrl = url
        image.setAttribute("src", url)
        image.asDynamic().hidden = false
        download.removeAttribute("disabled")
        copy.removeAttribute("disabled")
    }
}

private fun downloadBlob(blob: dynamic, filename: String) {
    val url = window.asDynamic().URL.createObjectURL(blob) as String
    val link = document.createElement("a")
    link.setAttribute("href", url)
    link.setAttribute("download", filename)
    link.asDynamic().style.display = "none"
    document.body.appendChild(link)
    link.asDynamic().click()
    link.asDynamic().remove()
    window.asDynamic().setTimeout({ window.asDynamic().URL.revokeObjectURL(url) }, 1500)
}

private fun copyImageBlob(blob: dynamic, done: (Boolean) -> Unit) {
    val clipboard = window.asDynamic().navigator.clipboard
    if (clipboard == null || clipboard == undefined || clipboard.write == undefined) {
        done(false)
        return
    }
    try {
        val data = js("({})")
        data["image/png"] = window.asDynamic().Promise.resolve(blob)
        val item = newClipboardItem(data)
        val items = js("[]")
        items.push(item)
        clipboard.write(items).then({ done(true) }, { _: dynamic -> done(false) })
    } catch (_: Throwable) {
        done(false)
    }
}

private fun renderShareCard(post: PostCard, done: (dynamic?) -> Unit) {
    loadBitmap(post.photoUrl) { photo ->
        loadBitmap(post.avatarUrl) { avatar ->
            loadBitmap(post.authorPhotoUrl) { face ->
                whenFontsReady {
                    try {
                        val canvas = paintShareCard(post, photo, avatar, face)
                        canvas.toBlob({ blob: dynamic ->
                            if (blob == null || blob == undefined) done(null) else done(blob)
                        }, "image/png")
                    } catch (_: Throwable) {
                        done(null)
                    }
                }
            }
        }
    }
}

private fun loadBitmap(url: String?, done: (dynamic?) -> Unit) {
    if (url.isNullOrBlank()) {
        done(null)
        return
    }
    val init = js("({})")
    init.credentials = "include"
    window.asDynamic().fetch(url, init).then({ response: dynamic ->
        if (response.ok != true) {
            done(null)
            return@then
        }
        response.blob().then({ blob: dynamic ->
            val objectUrl = window.asDynamic().URL.createObjectURL(blob) as String
            val image = js("new Image()")
            image.onload = {
                window.asDynamic().URL.revokeObjectURL(objectUrl)
                done(image)
            }
            image.onerror = {
                window.asDynamic().URL.revokeObjectURL(objectUrl)
                done(null)
            }
            image.src = objectUrl
        }, { _: dynamic -> done(null) })
    }, { _: dynamic -> done(null) })
}

private fun whenFontsReady(then: () -> Unit) {
    val fonts = document.asDynamic().fonts
    if (fonts == null || fonts == undefined || fonts.load == undefined) {
        then()
        return
    }
    fonts.load("700 42px \"Be Vietnam Pro\", Nunito, sans-serif").then({ then() }, { _: dynamic -> then() })
}

private fun paintShareCard(post: PostCard, photo: dynamic, avatar: dynamic, face: dynamic): dynamic {
    val canvas = document.createElement("canvas").asDynamic()
    canvas.width = SHARE_PX.toInt()
    canvas.height = SHARE_PX.toInt()
    val ctx = canvas.getContext("2d")
    ctx.imageSmoothingEnabled = true
    ctx.imageSmoothingQuality = "high"

    ctx.fillStyle = "#1c1916"
    ctx.fillRect(0.0, 0.0, SHARE_PX, SHARE_PX)
    if (photo != null) drawCover(ctx, photo, SHARE_PX)

    val footerH = 156.0
    val footerY = SHARE_PX - footerH
    val side = 40.0
    val cardW = SHARE_PX - side * 2
    val cardX = side
    val gap = 20.0
    val cardBottom = footerY - gap
    val minCardTop = 280.0
    val padX = 36.0
    val padTop = 64.0
    val padBottom = 32.0
    val replyH = 84.0
    val replyGap = 22.0
    val eventStart = post.eventStartAt ?: 0L
    val eventRelative = if (post.hasEvent) eventRelativeLabel(eventStart, post.eventEndAt) else null
    val eventH = if (post.hasEvent) if (eventRelative != null) 214.0 else 180.0 else 0.0
    val eventGap = if (post.hasEvent) 18.0 else 0.0
    val textW = cardW - padX * 2
    val maxMessageH = (cardBottom - minCardTop) - padTop - replyGap - replyH - eventH - eventGap - padBottom
    val fitted = fitMessage(ctx, post.message, textW, maxMessageH.coerceAtLeast(80.0))
    val messageH = fitted.lines.size * fitted.lineHeight
    val cardH = padTop + messageH + eventGap + eventH + replyGap + replyH + padBottom
    val cardY = cardBottom - cardH

    if (avatar != null) drawAvatar(ctx, avatar, cardX, cardY, cardW, footerY)

    ctx.save()
    ctx.shadowColor = "rgba(0,0,0,0.18)"
    ctx.shadowBlur = 32
    ctx.shadowOffsetY = 14
    traceRound(ctx, cardX, cardY, cardW, cardH, 46.0)
    ctx.fillStyle = "#f6f1e8"
    ctx.fill()
    ctx.restore()

    drawNameChip(ctx, post.authorName, cardX, cardY, cardW, face)
    val communityName = post.communityName
    if (!communityName.isNullOrBlank()) {
        val tag = "${post.communityEmoji ?: "✨"} $communityName"
        ctx.font = "800 28px $SHARE_FONT"
        val tagW = num(ctx.measureText(tag).width) + 36.0
        traceRound(ctx, 36.0, 36.0, tagW, 52.0, 26.0)
        ctx.fillStyle = "rgba(0,0,0,0.45)"
        ctx.fill()
        ctx.fillStyle = "#fffdf8"
        ctx.textAlign = "left"
        ctx.textBaseline = "middle"
        ctx.fillText(tag, 54.0, 62.0)
    }
    ctx.font = "700 ${fitted.size.toInt()}px $SHARE_FONT"
    ctx.fillStyle = "#2a241e"
    ctx.textAlign = "left"
    ctx.textBaseline = "top"
    var lineY = cardY + padTop
    for (line in fitted.lines) {
        ctx.fillText(line, cardX + padX, lineY)
        lineY += fitted.lineHeight
    }

    if (post.hasEvent) {
        val eventY = cardY + padTop + messageH + eventGap
        val eventX = cardX + 28.0
        val eventW = cardW - 56.0
        traceRound(ctx, eventX, eventY, eventW, eventH, 28.0)
        ctx.fillStyle = "#ffffff"
        ctx.fill()
        ctx.strokeStyle = "#e6ddd2"
        ctx.lineWidth = 3.0
        ctx.stroke()
        ctx.fillStyle = "#2a241e"
        ctx.font = "800 32px $SHARE_FONT"
        ctx.textAlign = "left"
        ctx.textBaseline = "top"
        val schedule = "📅 ${eventScheduleLabel(eventStart, post.eventEndAt)}"
        ctx.fillText(ellipsize(ctx, schedule, eventW - 48.0), eventX + 24.0, eventY + 16.0)
        ctx.font = "600 28px $SHARE_FONT"
        ctx.fillStyle = "#746b62"
        var infoY = eventY + 56.0
        if (eventRelative != null) {
            ctx.fillText(ellipsize(ctx, eventRelative, eventW - 48.0), eventX + 24.0, infoY)
            infoY += 34.0
        }
        ctx.fillText(ellipsize(ctx, "$PLACE_ICON ${post.location}", eventW - 48.0), eventX + 24.0, infoY)
        val price = post.eventPrice
        val priceText = if (price != null && price > 0) formatVnd(price) else Town.t("free")
        ctx.fillText(ellipsize(ctx, "$PRICE_ICON $priceText", eventW - 48.0), eventX + 24.0, infoY + 34.0)
        traceRound(ctx, eventX + eventW - 196.0, eventY + eventH - 64.0, 172.0, 48.0, 24.0)
        ctx.fillStyle = "#e9897c"
        ctx.fill()
        ctx.fillStyle = "#ffffff"
        ctx.font = "800 28px $SHARE_FONT"
        ctx.textAlign = "center"
        ctx.textBaseline = "middle"
        ctx.fillText(Town.t("joinEvent"), eventX + eventW - 110.0, eventY + eventH - 40.0)
    }

    val replyX = cardX + 28.0
    val replyY = cardY + padTop + messageH + eventGap + eventH + replyGap
    val replyW = cardW - 56.0
    traceRound(ctx, replyX, replyY, replyW, replyH, 24.0)
    ctx.fillStyle = "#ffffff"
    ctx.fill()
    ctx.strokeStyle = "#e6ddd2"
    ctx.lineWidth = 3.0
    ctx.stroke()
    ctx.font = "600 34px $SHARE_FONT"
    ctx.fillStyle = "#b0a89e"
    ctx.textAlign = "left"
    ctx.textBaseline = "middle"
    val reply = ellipsize(ctx, Town.t("replyTo", mapOf("name" to post.authorName)), replyW - 52.0)
    ctx.fillText(reply, replyX + 26.0, replyY + replyH / 2)

    ctx.fillStyle = "#1c1916"
    ctx.fillRect(0.0, footerY, SHARE_PX, footerH)
    ctx.fillStyle = "#e9897c"
    ctx.fillRect(0.0, footerY, SHARE_PX, 6.0)
    ctx.textAlign = "center"
    ctx.textBaseline = "middle"
    ctx.fillStyle = "#fffdf8"
    ctx.font = "800 46px $SHARE_FONT"
    ctx.fillText(ellipsize(ctx, Town.t("appName"), SHARE_PX - 80.0), SHARE_PX / 2, footerY + 62.0)
    ctx.fillStyle = "#e9897c"
    ctx.font = "700 32px $SHARE_FONT"
    ctx.fillText(ellipsize(ctx, Town.t("motto"), SHARE_PX - 80.0), SHARE_PX / 2, footerY + 112.0)
    return canvas
}

private fun drawCover(ctx: dynamic, image: dynamic, size: Double) {
    val sw = num(image.naturalWidth)
    val sh = num(image.naturalHeight)
    if (sw < 1.0 || sh < 1.0) return
    val scale = maxOf(size / sw, size / sh)
    val cropW = size / scale
    val cropH = size / scale
    ctx.drawImage(image, (sw - cropW) / 2, (sh - cropH) / 2, cropW, cropH, 0.0, 0.0, size, size)
}

private fun drawAvatar(ctx: dynamic, image: dynamic, cardX: Double, cardY: Double, cardW: Double, footerY: Double) {
    val width = num(image.naturalWidth).toInt()
    val height = num(image.naturalHeight).toInt()
    if (width < 2 || height < 2) return
    val crop = contentCrop(image, width, height)
    val maxH = min(cardY - 8.0, SHARE_PX * 0.46)
    if (maxH < 48.0) return
    val maxW = cardW * 0.68
    val scale = minOf(maxW / crop.w, maxH / crop.h)
    val dw = crop.w * scale
    val dh = crop.h * scale
    val dx = cardX + cardW - dw
    val dy = (cardY - dh + dh * 0.18).coerceAtLeast(8.0)
    ctx.save()
    ctx.beginPath()
    ctx.rect(0.0, 0.0, SHARE_PX, footerY)
    ctx.clip()
    ctx.filter = "drop-shadow(0px 16px 14px rgba(0,0,0,0.28))"
    ctx.drawImage(image, crop.x, crop.y, crop.w, crop.h, dx, dy, dw, dh)
    ctx.restore()
}

private data class Crop(val x: Int, val y: Int, val w: Int, val h: Int)

private fun contentCrop(image: dynamic, width: Int, height: Int): Crop {
    return try {
        val canvas = document.createElement("canvas").asDynamic()
        canvas.width = width
        canvas.height = height
        val context = canvas.getContext("2d")
        context.drawImage(image, 0, 0)
        val pixels = context.getImageData(0, 0, width, height).data
        var minX = width
        var minY = height
        var maxX = -1
        var maxY = -1
        var pixel = 0
        val count = width * height
        while (pixel < count) {
            if (num(pixels[pixel * 4 + 3]) > 12.0) {
                val x = pixel % width
                val y = pixel / width
                if (x < minX) minX = x
                if (y < minY) minY = y
                if (x > maxX) maxX = x
                if (y > maxY) maxY = y
            }
            pixel += 1
        }
        if (maxX < minX) return Crop(0, 0, width, height)
        minX = (minX - 1).coerceAtLeast(0)
        minY = (minY - 1).coerceAtLeast(0)
        maxX = (maxX + 1).coerceAtMost(width - 1)
        maxY = (maxY + 1).coerceAtMost(height - 1)
        Crop(minX, minY, maxX - minX + 1, maxY - minY + 1)
    } catch (_: Throwable) {
        Crop(0, 0, width, height)
    }
}

private fun drawNameChip(ctx: dynamic, name: String, cardX: Double, cardY: Double, cardW: Double, face: dynamic) {
    val chipH = 58.0
    val hasFace = face != null
    val photo = if (hasFace) 46.0 else 0.0
    val leftPad = if (hasFace) 6.0 else 26.0
    val textPad = if (hasFace) leftPad + photo + 10.0 else 26.0
    ctx.font = "800 44px $SHARE_FONT"
    val label = ellipsize(ctx, name.ifBlank { " " }, cardW - 140.0 - photo)
    val textW = num(ctx.measureText(label).width)
    val chipW = textPad + textW + 26.0
    val chipX = cardX + 32.0
    val chipY = cardY - chipH / 2
    traceRound(ctx, chipX + 2.0, chipY + 2.0, chipW, chipH, chipH / 2)
    ctx.fillStyle = "#00000024"
    ctx.fill()
    traceRound(ctx, chipX, chipY, chipW, chipH, chipH / 2)
    ctx.fillStyle = "#e9897c"
    ctx.fill()
    if (hasFace) {
        drawCircleImage(ctx, face, chipX + leftPad + photo / 2, chipY + chipH / 2, photo)
    }
    ctx.fillStyle = "#ffffff"
    ctx.textAlign = "left"
    ctx.textBaseline = "middle"
    ctx.fillText(label, chipX + textPad, chipY + chipH / 2)
}

private fun drawCircleImage(ctx: dynamic, image: dynamic, cx: Double, cy: Double, diameter: Double) {
    val sw = num(image.naturalWidth)
    val sh = num(image.naturalHeight)
    if (sw < 1.0 || sh < 1.0) return
    val radius = diameter / 2
    val scale = maxOf(diameter / sw, diameter / sh)
    val dw = sw * scale
    val dh = sh * scale
    ctx.save()
    ctx.beginPath()
    ctx.arc(cx, cy, radius, 0.0, PI * 2)
    ctx.closePath()
    ctx.clip()
    ctx.drawImage(image, cx - dw / 2, cy - dh / 2, dw, dh)
    ctx.restore()
}

private data class FittedText(val size: Double, val lineHeight: Double, val lines: List<String>)

private fun fitMessage(ctx: dynamic, text: String, maxWidth: Double, maxHeight: Double): FittedText {
    var size = 52.0
    while (size >= 30.0) {
        ctx.font = "700 ${size.toInt()}px $SHARE_FONT"
        val wrapped = wrapText(ctx, text, maxWidth)
        val lineHeight = size * 1.35
        val maxLines = (maxHeight / lineHeight).toInt().coerceAtLeast(1)
        if (wrapped.size <= maxLines || size <= 30.0) {
            val lines = if (wrapped.size <= maxLines) wrapped else clampLines(ctx, wrapped, maxLines, maxWidth)
            return FittedText(size, lineHeight, lines)
        }
        size -= 2.0
    }
    ctx.font = "700 30px $SHARE_FONT"
    return FittedText(30.0, 40.5, clampLines(ctx, wrapText(ctx, text, maxWidth), 1, maxWidth))
}

private fun wrapText(ctx: dynamic, text: String, maxWidth: Double): List<String> {
    if (text.isBlank()) return listOf("")
    val lines = mutableListOf<String>()
    for (paragraph in text.replace("\r\n", "\n").replace('\r', '\n').split('\n')) {
        if (paragraph.isBlank()) {
            lines += ""
            continue
        }
        var line = ""
        for (word in paragraph.split(Regex("\\s+")).filter { it.isNotEmpty() }) {
            val next = if (line.isEmpty()) word else "$line $word"
            if (num(ctx.measureText(next).width) <= maxWidth) {
                line = next
            } else {
                if (line.isNotEmpty()) lines += line
                line = if (num(ctx.measureText(word).width) <= maxWidth) word else breakWord(ctx, word, maxWidth, lines)
            }
        }
        if (line.isNotEmpty()) lines += line
    }
    return if (lines.isEmpty()) listOf("") else lines
}

private fun breakWord(ctx: dynamic, word: String, maxWidth: Double, lines: MutableList<String>): String {
    var chunk = ""
    for (ch in word) {
        val next = chunk + ch
        if (chunk.isNotEmpty() && num(ctx.measureText(next).width) > maxWidth) {
            lines += chunk
            chunk = ch.toString()
        } else {
            chunk = next
        }
    }
    return chunk
}

private fun clampLines(ctx: dynamic, lines: List<String>, maxLines: Int, maxWidth: Double): List<String> {
    if (lines.size <= maxLines) return lines
    val kept = lines.take(maxLines).toMutableList()
    kept[kept.lastIndex] = ellipsize(ctx, kept.last(), maxWidth)
    return kept
}

private fun ellipsize(ctx: dynamic, text: String, maxWidth: Double): String {
    if (num(ctx.measureText(text).width) <= maxWidth) return text
    var value = text.trimEnd()
    while (value.isNotEmpty() && num(ctx.measureText("$value…").width) > maxWidth) {
        value = value.dropLast(1).trimEnd()
    }
    return if (value.isEmpty()) "…" else "$value…"
}

private fun traceRound(ctx: dynamic, x: Double, y: Double, w: Double, h: Double, r: Double) {
    val radius = minOf(r, w / 2.0, h / 2.0)
    ctx.beginPath()
    ctx.moveTo(x + radius, y)
    ctx.arcTo(x + w, y, x + w, y + h, radius)
    ctx.arcTo(x + w, y + h, x, y + h, radius)
    ctx.arcTo(x, y + h, x, y, radius)
    ctx.arcTo(x, y, x + w, y, radius)
    ctx.closePath()
}
