package quest.saigon.web

import quest.saigon.Limits
import web.dom.document
import web.html.HTMLElement
import kotlin.math.exp
import kotlin.math.hypot

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

internal data class UserPhoto(
    val id: String,
    val url: String,
    val canDelete: Boolean,
)

internal enum class PhotoViewerPointerAction {
    IGNORE,
    SWIPE_START,
    SWIPE_MOVE,
    SWIPE_END,
    CANCEL_SWIPE,
    CONSUMED,
}

internal class PhotoViewerZoom(private val stage: HTMLElement) {
    private data class Point(val x: Double, val y: Double)

    private enum class Gesture {
        IDLE,
        SWIPE,
        PAN,
        PINCH,
    }

    private var image: HTMLElement? = null
    private var scale = 1.0
    private var translateX = 0.0
    private var translateY = 0.0
    private val pointers = linkedMapOf<Int, Point>()
    private var gesture = Gesture.IDLE
    private var swipePointerId: Int? = null
    private var panPointerId: Int? = null
    private var panStart = Point(0.0, 0.0)
    private var panStartX = 0.0
    private var panStartY = 0.0
    private var pinchPointerIds = emptyList<Int>()
    private var pinchStartDistance = 1.0
    private var pinchStartScale = 1.0
    private var pinchStartX = 0.0
    private var pinchStartY = 0.0
    private var pinchStartMidpoint = Point(0.0, 0.0)

    init {
        val options = js("({ passive: false })")
        stage.asDynamic().addEventListener("wheel", { event: dynamic ->
            event.preventDefault()
            if (image == null) return@addEventListener
            val deltaMode = num(event.deltaMode).toInt()
            val deltaMultiplier = when (deltaMode) {
                1 -> 16.0
                2 -> num(stage.asDynamic().clientHeight)
                else -> 1.0
            }
            val delta = num(event.deltaY) * deltaMultiplier
            val point = pointAt(event.clientX, event.clientY)
            zoomTo(scale * exp((-delta * 0.002).coerceIn(-4.0, 4.0)), point)
        }, options)
    }

    fun setImage(next: HTMLElement?) {
        if (image === next) return
        reset()
        image = next
    }

    fun pointerDown(event: dynamic): PhotoViewerPointerAction {
        val id = num(event.pointerId).toInt()
        if (pointers.containsKey(id) || pointers.size >= 2) return PhotoViewerPointerAction.IGNORE
        val existingPointers = pointers.size
        pointers[id] = pointAt(event.clientX, event.clientY)
        return when {
            existingPointers == 0 && scale > 1.0 -> {
                startPan(id)
                PhotoViewerPointerAction.CONSUMED
            }
            existingPointers == 0 -> {
                gesture = Gesture.SWIPE
                swipePointerId = id
                PhotoViewerPointerAction.SWIPE_START
            }
            else -> {
                gesture = Gesture.PINCH
                swipePointerId = null
                startPinch()
                PhotoViewerPointerAction.CANCEL_SWIPE
            }
        }
    }

    fun pointerMove(event: dynamic): PhotoViewerPointerAction {
        val id = num(event.pointerId).toInt()
        if (!pointers.containsKey(id)) return PhotoViewerPointerAction.IGNORE
        pointers[id] = pointAt(event.clientX, event.clientY)
        return when (gesture) {
            Gesture.SWIPE -> if (id == swipePointerId) PhotoViewerPointerAction.SWIPE_MOVE else PhotoViewerPointerAction.CONSUMED
            Gesture.PAN -> {
                if (id == panPointerId) {
                    val point = pointers[id] ?: return PhotoViewerPointerAction.CONSUMED
                    translateX = panStartX + point.x - panStart.x
                    translateY = panStartY + point.y - panStart.y
                    applyTransform()
                }
                PhotoViewerPointerAction.CONSUMED
            }
            Gesture.PINCH -> {
                updatePinch()
                PhotoViewerPointerAction.CONSUMED
            }
            Gesture.IDLE -> PhotoViewerPointerAction.IGNORE
        }
    }

    fun pointerUp(event: dynamic): PhotoViewerPointerAction {
        val id = num(event.pointerId).toInt()
        if (!pointers.containsKey(id)) return PhotoViewerPointerAction.IGNORE
        pointers[id] = pointAt(event.clientX, event.clientY)
        val action = when {
            gesture == Gesture.SWIPE && id == swipePointerId -> PhotoViewerPointerAction.SWIPE_END
            else -> PhotoViewerPointerAction.CONSUMED
        }
        pointers.remove(id)
        when (gesture) {
            Gesture.SWIPE -> {
                gesture = Gesture.IDLE
                swipePointerId = null
            }
            Gesture.PAN -> {
                gesture = Gesture.IDLE
                panPointerId = null
            }
            Gesture.PINCH -> {
                if (pointers.size == 1) {
                    startPan(pointers.keys.first())
                } else if (pointers.isEmpty()) {
                    gesture = Gesture.IDLE
                    pinchPointerIds = emptyList()
                } else {
                    startPinch()
                }
            }
            Gesture.IDLE -> Unit
        }
        return action
    }

    fun reset() {
        scale = 1.0
        translateX = 0.0
        translateY = 0.0
        pointers.clear()
        gesture = Gesture.IDLE
        swipePointerId = null
        panPointerId = null
        pinchPointerIds = emptyList()
        image?.asDynamic()?.style?.removeProperty("transform")
        image?.asDynamic()?.style?.removeProperty("will-change")
        stage.asDynamic().classList.remove("zoomed")
    }

    private fun startPan(id: Int) {
        gesture = Gesture.PAN
        panPointerId = id
        panStart = pointers[id] ?: Point(0.0, 0.0)
        panStartX = translateX
        panStartY = translateY
    }

    private fun startPinch() {
        val selected = pointers.entries.take(2)
        if (selected.size < 2) return
        pinchPointerIds = selected.map { it.key }
        val first = selected[0].value
        val second = selected[1].value
        pinchStartDistance = distance(first, second).coerceAtLeast(1.0)
        pinchStartScale = scale
        pinchStartX = translateX
        pinchStartY = translateY
        pinchStartMidpoint = midpoint(first, second)
    }

    private fun updatePinch() {
        val first = pointers[pinchPointerIds.getOrNull(0)] ?: return
        val second = pointers[pinchPointerIds.getOrNull(1)] ?: return
        val targetScale = (pinchStartScale * distance(first, second) / pinchStartDistance).coerceIn(1.0, 3.0)
        if (targetScale <= 1.0) {
            scale = 1.0
            translateX = 0.0
            translateY = 0.0
        } else {
            val ratio = targetScale / pinchStartScale
            val center = midpoint(first, second)
            translateX = center.x + (pinchStartX - pinchStartMidpoint.x) * ratio
            translateY = center.y + (pinchStartY - pinchStartMidpoint.y) * ratio
            scale = targetScale
        }
        applyTransform()
    }

    private fun zoomTo(target: Double, point: Point) {
        val nextScale = target.coerceIn(1.0, 3.0)
        if (nextScale == scale) return
        if (nextScale <= 1.0) {
            scale = 1.0
            translateX = 0.0
            translateY = 0.0
        } else {
            val ratio = nextScale / scale
            translateX = point.x + (translateX - point.x) * ratio
            translateY = point.y + (translateY - point.y) * ratio
            scale = nextScale
        }
        applyTransform()
    }

    private fun pointAt(clientX: dynamic, clientY: dynamic): Point {
        val rect = stage.asDynamic().getBoundingClientRect()
        return Point(
            num(clientX) - num(rect.left) - num(stage.asDynamic().clientWidth) / 2.0,
            num(clientY) - num(rect.top) - num(stage.asDynamic().clientHeight) / 2.0,
        )
    }

    private fun applyTransform() {
        val element = image ?: return
        val style = element.asDynamic().style
        if (scale <= 1.0) {
            translateX = 0.0
            translateY = 0.0
            style.removeProperty("transform")
            style.removeProperty("will-change")
            stage.asDynamic().classList.remove("zoomed")
            return
        }
        val bounds = translationBounds()
        translateX = translateX.coerceIn(-bounds.first, bounds.first)
        translateY = translateY.coerceIn(-bounds.second, bounds.second)
        style.transformOrigin = "center"
        style.willChange = "transform"
        style.transform = "translate3d(${translateX}px, ${translateY}px, 0) scale($scale)"
        stage.asDynamic().classList.add("zoomed")
    }

    private fun translationBounds(): Pair<Double, Double> {
        val element = image ?: return 0.0 to 0.0
        val width = num(element.asDynamic().offsetWidth)
        val height = num(element.asDynamic().offsetHeight)
        val naturalWidth = num(element.asDynamic().naturalWidth)
        val naturalHeight = num(element.asDynamic().naturalHeight)
        if (width <= 0.0 || height <= 0.0 || naturalWidth <= 0.0 || naturalHeight <= 0.0) return 0.0 to 0.0
        val aspect = naturalWidth / naturalHeight
        val contentWidth: Double
        val contentHeight: Double
        if (aspect >= width / height) {
            contentWidth = width
            contentHeight = width / aspect
        } else {
            contentHeight = height
            contentWidth = height * aspect
        }
        val stageWidth = num(stage.asDynamic().clientWidth)
        val stageHeight = num(stage.asDynamic().clientHeight)
        return ((contentWidth * scale - stageWidth) / 2.0).coerceAtLeast(0.0) to
            ((contentHeight * scale - stageHeight) / 2.0).coerceAtLeast(0.0)
    }

    private fun distance(first: Point, second: Point): Double = hypot(second.x - first.x, second.y - first.y)

    private fun midpoint(first: Point, second: Point): Point =
        Point((first.x + second.x) / 2.0, (first.y + second.y) / 2.0)
}

internal fun userPhotosSection(
    host: HTMLElement,
    photosUrl: String,
    uploadUrl: String,
    uploadFields: Map<String, String>,
    deleteUrl: (String) -> String,
): HTMLElement {
    val section = el("section", "user-photos")
    section.appendChild(el("h3", "activity-section-title", text = Town.t("photos")))
    val row = el("div", "user-photos-row")
    section.appendChild(row)
    val picker = el("input", "user-photos-picker")
    picker.setAttribute("type", "file")
    picker.setAttribute("accept", "image/*")
    picker.setAttribute("multiple", "multiple")
    section.appendChild(picker)
    var photos = emptyList<UserPhoto>()
    lateinit var renderPhotos: () -> Unit
    lateinit var refreshPhotos: () -> Unit
    lateinit var requestDelete: (String, () -> Unit) -> Unit

    fun openPicker() {
        if (Town.me == null) {
            showAuthDialog(host, onSuccess = {
                refreshPhotos()
                picker.asDynamic().click()
            })
        } else {
            picker.asDynamic().click()
        }
    }

    fun parsePhotos(array: dynamic): List<UserPhoto> {
        if (array == null || array == undefined) return emptyList()
        val result = mutableListOf<UserPhoto>()
        val length = num(array.length).toInt()
        for (index in 0 until length) {
            val item = array[index]
            val id = item.id as? String ?: continue
            val url = item.url as? String ?: continue
            result += UserPhoto(id, prefixServerOrigin(url), item.canDelete as? Boolean ?: false)
        }
        return result
    }

    renderPhotos = {
        row.clear()
        val add = el("button", "user-photo-add", text = "+")
        add.setAttribute("type", "button")
        add.setAttribute("aria-label", Town.t("addPhoto"))
        add.onClick { openPicker() }
        row.appendChild(add)
        photos.forEachIndexed { index, photo ->
            val tile = el("div", "user-photo-tile")
            val image = el("img", "user-photo-image")
            image.setAttribute("src", photo.url)
            image.setAttribute("alt", Town.t("photo"))
            val open = el("button", "user-photo-open")
            open.setAttribute("type", "button")
            open.setAttribute("aria-label", Town.t("photo"))
            open.appendChild(image)
            open.onClick { openPhotoViewer(photos, index, requestDelete) }
            tile.appendChild(open)
            if (photo.canDelete) {
                val remove = el("button", "user-photo-delete", text = "×")
                remove.setAttribute("type", "button")
                remove.setAttribute("aria-label", Town.t("removePhoto"))
                remove.onClick { requestDelete(photo.id) {} }
                tile.appendChild(remove)
            }
            row.appendChild(tile)
        }
    }

    refreshPhotos = {
        http("GET", photosUrl) { status, text ->
            if (status != 200) return@http
            photos = parsePhotos(parseJson(text).photos)
            renderPhotos()
        }
    }

    requestDelete = { photoId, onDeleted ->
        val photo = photos.firstOrNull { it.id == photoId }
        if (photo != null && photo.canDelete) {
            val viewerBackdrop = document.asDynamic().querySelector(".photo-viewer-backdrop")
            val dialogHost = if (viewerBackdrop == null || viewerBackdrop == undefined) host else viewerBackdrop
            confirmDialog(dialogHost, Town.t("deletePhotoAsk"), Town.t("deletePhotoHint")) {
                http("DELETE", deleteUrl(photoId)) { status, body ->
                    if (status == 200) {
                        refreshPhotos()
                        onDeleted()
                    } else {
                        toast(host, errorText(body, status))
                    }
                }
            }
        }
    }

    picker.asDynamic().onchange = {
        val files = picker.asDynamic().files
        val count = num(files.length).toInt()
        if (count > Limits.PHOTOS_MAX) {
            toast(host, Town.t("tooManyPhotos"))
            picker.asDynamic().value = ""
        } else {
            val selectedFiles = (0 until count).map { files[it] }
            val prepared = mutableListOf<dynamic>()
            fun prepareAt(index: Int) {
                if (index >= count) {
                    if (prepared.isEmpty()) {
                        toast(host, Town.t("invalidPhoto"))
                        return
                    }
                    val form = js("new FormData()")
                    uploadFields.forEach { (name, value) -> form.append(name, value) }
                    prepared.forEach { blob -> form.append("photos", blob, "photo.jpg") }
                    http("POST", uploadUrl, form = form) { status, body ->
                        if (status == 200 || status == 201) refreshPhotos() else toast(host, errorText(body, status))
                    }
                    return
                }
                preparePhoto(
                    selectedFiles[index],
                    crop916 = false,
                    done = { blob ->
                        prepared.add(blob)
                        prepareAt(index + 1)
                    },
                    fail = { prepareAt(index + 1) },
                )
            }
            prepareAt(0)
            picker.asDynamic().value = ""
        }
    }
    refreshPhotos()
    return section
}
