package quest.saigon.web

import quest.saigon.Limits
import web.dom.document
import web.html.HTMLElement

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
