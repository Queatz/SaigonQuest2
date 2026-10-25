package quest.saigon.web

import quest.saigon.CallToAction
import quest.saigon.Limits
import web.dom.document
import web.html.HTMLElement
import web.window.window
import kotlin.math.abs

internal fun composerScreen(router: HistoryRouter): HTMLElement {
    val me = Town.me ?: return el("section", "shell")
    val shell = el("section", "shell page-shell")
    shell.appendChild(pageHead(router, Town.t("newPost"), showProfile = true))
    val body = el("div", "page-body")
    val error = el("p", "form-error")
    error.asDynamic().hidden = true
    val photos = mutableListOf<ComposerPhoto>()
    var selectedPhoto = 0
    var selectedAvatar: String? = null
    var selectedMusicId: String? = null

    val photosWrap = el("div", "composer-photos")
    val stage = el("div", "composer-photo-stage")
    val upload = el("button", "upload")
    upload.setAttribute("type", "button")
    val preview = el("img", "upload-preview")
    preview.asDynamic().hidden = true
    upload.appendChild(preview)
    val label = el("span", "upload-label", text = Town.t("uploadPrompt1"))
    upload.appendChild(label)
    rotateUploadPrompts(label)
    val remove = el("button", "icon-btn composer-photo-remove", text = "🗑️")
    remove.setAttribute("type", "button")
    remove.setAttribute("aria-label", Town.t("removePhoto"))
    remove.asDynamic().hidden = true
    val thumbs = el("div", "photo-thumbs")
    thumbs.asDynamic().hidden = true
    val picker = el("input")
    picker.setAttribute("type", "file")
    picker.setAttribute("accept", "image/*")
    picker.setAttribute("multiple", "multiple")
    picker.asDynamic().hidden = true
    stage.appendChild(upload)
    stage.appendChild(remove)
    photosWrap.appendChild(stage)
    photosWrap.appendChild(thumbs)

    fun refreshPhotos() {
        if (photos.isEmpty()) {
            preview.removeAttribute("src")
            preview.asDynamic().hidden = true
            label.asDynamic().hidden = false
            remove.asDynamic().hidden = true
            thumbs.asDynamic().hidden = true
            thumbs.clear()
            selectedPhoto = 0
            return
        }
        selectedPhoto = selectedPhoto.coerceIn(0, photos.lastIndex)
        preview.setAttribute("src", photos[selectedPhoto].url)
        preview.asDynamic().hidden = false
        label.asDynamic().hidden = true
        remove.asDynamic().hidden = false
        thumbs.asDynamic().hidden = false
        renderPhotoThumbs(thumbs, photos, selectedPhoto, photos.size < Limits.PHOTOS_MAX, { index ->
            selectedPhoto = index
            refreshPhotos()
        }, { from, to ->
            if (from == to || from !in photos.indices || to !in photos.indices) return@renderPhotoThumbs
            val moved = photos.removeAt(from)
            photos.add(to, moved)
            selectedPhoto = to
            refreshPhotos()
        }, {
            picker.asDynamic().click()
        })
    }

    fun removeSelectedPhoto() {
        if (selectedPhoto !in photos.indices) return
        val gone = photos.removeAt(selectedPhoto)
        window.asDynamic().URL.revokeObjectURL(gone.url)
        if (selectedPhoto > photos.lastIndex) selectedPhoto = photos.lastIndex.coerceAtLeast(0)
        refreshPhotos()
    }

    fun addPhotoFiles(list: dynamic) {
        if (list == null || list == undefined) return
        val length = list.length as Int
        val remaining = Limits.PHOTOS_MAX - photos.size
        if (remaining <= 0) {
            show(error, Town.t("tooManyPhotos"))
            return
        }
        if (length > remaining) show(error, Town.t("tooManyPhotos"))
        val take = minOf(length, remaining)
        fun next(i: Int) {
            if (i >= take) return
            val file = list[i] ?: return next(i + 1)
            preparePhoto(file, crop916 = true, { blob ->
                if (photos.size >= Limits.PHOTOS_MAX) return@preparePhoto
                photos += ComposerPhoto(
                    blob = blob,
                    url = window.asDynamic().URL.createObjectURL(blob) as String,
                )
                selectedPhoto = photos.lastIndex
                error.asDynamic().hidden = true
                refreshPhotos()
                next(i + 1)
            }, {
                error.textContent = Town.t("invalidPhoto")
                error.asDynamic().hidden = false
                next(i + 1)
            })
        }
        next(0)
    }

    upload.onClick { if (photos.isEmpty()) picker.asDynamic().click() }
    remove.onClick { removeSelectedPhoto() }
    picker.asDynamic().onchange = {
        addPhotoFiles(picker.asDynamic().files)
        picker.asDynamic().value = ""
    }

    val message = el("textarea")
    message.setAttribute("maxlength", Limits.MESSAGE_MAX.toString())
    message.setAttribute("placeholder", Town.t("messagePlaceholder"))
    val messageLabel = el("label", "field")
    messageLabel.appendChild(el("span", text = "💬 ${Town.t("message")}"))
    messageLabel.appendChild(message)
    val communities = communityPicker(shell, message)

    val location = field("📍 ${Town.t("location")}", "text", Town.t("locationHint"))
    location.input.asDynamic().value = me.location
    val firstComment = area("💭 ${Town.t("firstComment")}", "", Limits.COMMENT_MAX)
    val pickedPlace = PlacePick(name = me.location, placeId = me.placeId, lat = me.lat, lng = me.lng)
    val mapBtn = el("button", "icon-btn location-map-btn")
    mapBtn.setAttribute("type", "button")
    mapBtn.setAttribute("aria-label", Town.t("pickLocation"))
    mapBtn.textContent = "🗺️"
    val locationRow = el("div", "location-input-row")
    location.root.asDynamic().insertBefore(locationRow, location.input)
    locationRow.appendChild(location.input)
    locationRow.appendChild(mapBtn)
    val event = composerEventFields()
    val poll = composerPollFields()
    val question = composerQuestionFields()
    val cta = composerCtaFields()
    bindLocationMapPicker(shell, location.input, mapBtn, pickedPlace) { communities.second.communityId }

    val avatarLabel = el("h3", text = "🙆 ${Town.t("selectAvatar")}")
    val avatarRow = el("div", "avatar-row")
    val studio = el("div", "studio")
    studio.asDynamic().hidden = true
    val none = el("button", "avatar-choice none selected", text = Town.t("noAvatar"))
    none.setAttribute("type", "button")
    val ghost = el("button", "avatar-choice ghost")
    ghost.setAttribute("type", "button")
    ghost.setAttribute("aria-label", Town.t("createAvatar"))
    ghost.textContent = "+"
    fun pickAvatar(id: String?, button: HTMLElement) {
        if (id != null && selectedAvatar == id) {
            selectedAvatar = null
            markAvatarSelected(avatarRow, none)
            return
        }
        selectedAvatar = id
        hideAvatarStudio(studio, ghost)
        markAvatarSelected(avatarRow, button)
    }
    none.onClick { pickAvatar(null, none) }
    ghost.onClick {
        val opening = studio.asDynamic().hidden == true
        studio.asDynamic().hidden = !opening
        if (opening) {
            ghost.asDynamic().classList.add("open")
            val input = studio.asDynamic().querySelector("input")
            if (input != null && input != undefined) input.focus()
        } else {
            ghost.asDynamic().classList.remove("open")
        }
    }
    avatarRow.appendChild(ghost)
    avatarRow.appendChild(none)
    studio.appendChild(avatarPromptForm(shell, me) { id ->
        studio.asDynamic().hidden = true
        ghost.asDynamic().classList.remove("open")
        val pending = el("button", "avatar-choice generating")
        pending.setAttribute("type", "button")
        pending.setAttribute("disabled", "disabled")
        pending.appendChild(el("span", "avatar-spinner"))
        avatarRow.asDynamic().insertBefore(pending, none)
        pollAvatar(id) { avatar ->
            if (avatar.status == "ready" && avatar.imageUrl != null) {
                val choice = composerAvatarChoice(avatar) { selectedId, button ->
                    pickAvatar(selectedId, button)
                }
                pending.asDynamic().replaceWith(choice)
                pickAvatar(avatar.id as String, choice)
            } else {
                pending.asDynamic().remove()
                toast(shell, Town.t("avatarFailed"))
            }
        }
    })
    http("GET", "/api/avatars") { status, text ->
        if (status != 200) return@http
        val avatars = parseJson(text).avatars
        if (avatars == null) return@http
        val length = avatars.length as Int
        for (i in 0 until length) {
            val avatar = avatars[i]
            if (avatar.status != "ready" || avatar.imageUrl == null) continue
            avatarRow.appendChild(composerAvatarChoice(avatar) { selectedId, button ->
                pickAvatar(selectedId, button)
            })
        }
    }

    val post = el("button", "primary", text = Town.t(if (me.paused) "accountPaused" else "postNow"))
    post.setAttribute("type", "button")
    if (me.paused) post.setAttribute("disabled", "disabled")
    fun applyPaused(paused: Boolean) {
        if (paused) {
            post.setAttribute("disabled", "disabled")
            post.textContent = Town.t("accountPaused")
        }
    }
    http("GET", "/api/me") { status, text ->
        if (status != 200) return@http
        val latest = meFrom(parseJson(text))
        Town.me = latest
        applyPaused(latest.paused)
    }
    post.onClick {
        if (Town.me?.paused == true) {
            applyPaused(true)
            return@onClick
        }
        error.asDynamic().hidden = true
        val text = (message.asDynamic().value as String).trim()
        val place = location.value().trim().ifBlank { me.location.trim() }
        when {
            photos.isEmpty() -> show(error, Town.t("photoRequired"))
            text.isEmpty() -> show(error, Town.t("invalidMessage"))
            place.isEmpty() -> show(error, Town.t("invalidLocation"))
            else -> {
                post.setAttribute("disabled", "disabled")
                post.textContent = Town.t("posting")
                val form = js("new FormData()")
                photos.forEachIndexed { index, photo ->
                    form.append("photo", photo.blob, "photo$index.jpg")
                }
                form.append("message", text)
                form.append("location", place)
                val first = firstComment.value().trim()
                if (first.isNotEmpty()) form.append("firstComment", first)
                val selectedPlaceId = pickedPlace.placeId
                if (!selectedPlaceId.isNullOrBlank()) form.append("placeId", selectedPlaceId)
                val lat = pickedPlace.lat
                val lng = pickedPlace.lng
                if (lat != null && lng != null) {
                    form.append("lat", lat.toString())
                    form.append("lng", lng.toString())
                }
                if (event.enabled) {
                    val start = event.startAt
                    if (start == null) {
                        post.removeAttribute("disabled")
                        post.textContent = Town.t("postNow")
                        show(error, Town.t("invalidEvent"))
                        return@onClick
                    }
                    val end = event.endAt
                    if (end != null && end < start) {
                        post.removeAttribute("disabled")
                        post.textContent = Town.t("postNow")
                        show(error, Town.t("invalidEventEnd"))
                        return@onClick
                    }
                    form.append("eventStartAt", start.toString())
                    event.endAt?.let { form.append("eventEndAt", it.toString()) }
                    event.price?.let { form.append("eventPrice", it.toString()) }
                }
                if (poll.enabled) {
                    val options = poll.options()
                    if (options.size < Limits.POLL_OPTIONS_MIN) {
                        post.removeAttribute("disabled")
                        post.textContent = Town.t("postNow")
                        show(error, Town.t("invalidPoll"))
                        return@onClick
                    }
                    options.forEachIndexed { index, option ->
                        form.append("pollOption$index", option)
                    }
                }
                if (question.enabled) {
                    form.append("askQuestion", "true")
                    val title = question.title.trim()
                    if (title.isNotEmpty()) form.append("questionTitle", title)
                }
                if (cta.enabled) {
                    val parsed = CallToAction.parse(cta.title, cta.text, cta.destination, cta.url)
                    if (parsed == null) {
                        post.removeAttribute("disabled")
                        post.textContent = Town.t("postNow")
                        val urlInvalid = cta.destination == CallToAction.WEBSITE &&
                            CallToAction.cleanUrl(cta.url) == null
                        show(error, Town.t(if (urlInvalid) "invalidCallToActionUrl" else "invalidCallToAction"))
                        return@onClick
                    }
                    form.append("addCta", "true")
                    if (parsed.title.isNotEmpty()) form.append("ctaTitle", parsed.title)
                    form.append("ctaText", parsed.text)
                    form.append("ctaDestination", parsed.destination)
                    if (parsed.url.isNotEmpty()) form.append("ctaUrl", parsed.url)
                }
                val avatarId = selectedAvatar
                if (!avatarId.isNullOrBlank()) form.append("avatarId", avatarId)
                val musicId = selectedMusicId
                if (!musicId.isNullOrBlank()) form.append("musicId", musicId)
                val postedCommunity = communities.second.communityId
                val exclusive = communities.second.exclusive && !postedCommunity.isNullOrBlank()
                if (!postedCommunity.isNullOrBlank()) form.append("communityId", postedCommunity)
                if (exclusive) form.append("communityOnly", "true")
                http("POST", "/api/posts", form = form) { status, body ->
                    if (status == 201) {
                        val card = postFrom(parseJson(body))
                        if (exclusive && !card.communityId.isNullOrBlank()) {
                            router.navigate("/c/${card.communityId}")
                        } else {
                            Town.pending = card
                            router.navigate("/")
                        }
                    } else if (status == 403) {
                        applyPaused(true)
                        show(error, errorText(body, status))
                    } else {
                        post.removeAttribute("disabled")
                        post.textContent = Town.t("postNow")
                        show(error, errorText(body, status))
                    }
                }
            }
        }
    }

    body.appendChild(photosWrap)
    body.appendChild(picker)
    body.appendChild(messageLabel)
    body.appendChild(communities.first)
    body.appendChild(location.root)
    body.appendChild(firstComment.root)
    body.appendChild(event.root)
    body.appendChild(poll.root)
    body.appendChild(question.root)
    body.appendChild(cta.root)
    body.appendChild(musicPicker(shell) { selectedMusicId = it })
    body.appendChild(avatarLabel)
    body.appendChild(avatarRow)
    body.appendChild(studio)
    body.appendChild(error)
    shell.appendChild(body)
    val bar = el("div", "dock dock-wide")
    bar.appendChild(post)
    shell.appendChild(bar)
    return shell
}

private class ComposerPhoto(val blob: dynamic, val url: String)

private fun renderPhotoThumbs(
    row: HTMLElement,
    photos: List<ComposerPhoto>,
    selected: Int,
    canAdd: Boolean,
    onSelect: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onAdd: () -> Unit,
) {
    row.clear()
    photos.forEachIndexed { index, photo ->
        val thumb = el("button", if (index == selected) "photo-thumb selected" else "photo-thumb")
        thumb.setAttribute("type", "button")
        val image = el("img")
        image.setAttribute("src", photo.url)
        image.setAttribute("alt", "")
        image.setAttribute("draggable", "false")
        thumb.appendChild(image)
        var dragged = false
        thumb.onClick { if (!dragged) onSelect(index) }
        bindPhotoThumbDrag(thumb, row, index, { dragged = true }, onMove)
        row.appendChild(thumb)
    }
    if (canAdd) {
        val ghost = el("button", "photo-thumb ghost")
        ghost.setAttribute("type", "button")
        ghost.setAttribute("aria-label", Town.t("addPhoto"))
        ghost.textContent = "+"
        ghost.onClick(onAdd)
        row.appendChild(ghost)
    }
}

private fun bindPhotoThumbDrag(
    thumb: HTMLElement,
    row: HTMLElement,
    origin: Int,
    onDrag: () -> Unit,
    onMove: (Int, Int) -> Unit,
) {
    var startX = 0.0
    var startY = 0.0
    var holding = false
    var dragging = false
    var lift: dynamic = null
    var target = origin
    var stride = 0.0
    var centers = emptyList<Double>()

    fun thumbs(): dynamic = row.asDynamic().querySelectorAll(".photo-thumb:not(.ghost)")

    fun contentX(clientX: Double): Double {
        val box = row.asDynamic().getBoundingClientRect()
        return clientX - num(box.left) + num(row.asDynamic().scrollLeft)
    }

    fun shiftTo(next: Int) {
        target = next
        val nodes = thumbs()
        val count = nodes.length as Int
        for (i in 0 until count) {
            val dx = photoThumbShift(i, origin, target, stride)
            nodes[i].style.transform = if (dx == 0.0) "" else "translate3d(${dx}px, 0, 0)"
        }
    }

    fun startLift(): Boolean {
        val nodes = thumbs()
        val count = nodes.length as Int
        if (count <= 1) return false
        val rowBox = row.asDynamic().getBoundingClientRect()
        val scroll = num(row.asDynamic().scrollLeft)
        centers = (0 until count).map { i ->
            val box = nodes[i].getBoundingClientRect()
            num(box.left) - num(rowBox.left) + scroll + num(box.width) / 2.0
        }
        stride = if (count >= 2) centers[1] - centers[0] else num(nodes[0].getBoundingClientRect().width)
        val box = thumb.asDynamic().getBoundingClientRect()
        val clone = thumb.asDynamic().cloneNode(true)
        clone.classList.add("lifting")
        clone.style.position = "fixed"
        clone.style.left = "${num(box.left)}px"
        clone.style.top = "${num(box.top)}px"
        clone.style.width = "${num(box.width)}px"
        clone.style.height = "${num(box.height)}px"
        clone.style.margin = "0"
        clone.style.zIndex = "40"
        clone.style.pointerEvents = "none"
        clone.style.transform = "translate3d(0, 0, 0) scale(1.08)"
        document.asDynamic().body.appendChild(clone)
        lift = clone
        thumb.asDynamic().classList.add("photo-ghost")
        row.asDynamic().classList.add("reordering")
        target = origin
        return true
    }

    fun moveLift(event: dynamic) {
        if (lift == null || lift == undefined) return
        val dx = num(event.clientX) - startX
        val dy = num(event.clientY) - startY
        lift.style.transform = "translate3d(${dx}px, ${dy}px, 0) scale(1.08)"
        scrollPhotoThumbs(row, num(event.clientX))
        val next = dropIndexForMids(centers, contentX(num(event.clientX)), origin)
        if (next != target) shiftTo(next)
    }

    fun cleanup() {
        if (lift != null && lift != undefined) {
            lift.parentNode?.removeChild(lift)
            lift = null
        }
        row.asDynamic().classList.remove("reordering")
        val nodes = thumbs()
        val count = nodes.length as Int
        for (i in 0 until count) {
            nodes[i].style.transform = ""
            nodes[i].classList.remove("photo-ghost")
        }
    }

    thumb.asDynamic().addEventListener("pointerdown", { event: dynamic ->
        if (event.button != null && event.button != 0 && event.button != undefined) {
            val button = num(event.button).toInt()
            if (button != 0) return@addEventListener
        }
        startX = num(event.clientX)
        startY = num(event.clientY)
        holding = true
        dragging = false
        target = origin
        if (thumb.asDynamic().setPointerCapture != undefined) thumb.asDynamic().setPointerCapture(event.pointerId)
    })
    thumb.asDynamic().addEventListener("pointermove", { event: dynamic ->
        if (!holding) return@addEventListener
        val dx = num(event.clientX) - startX
        val dy = num(event.clientY) - startY
        if (!dragging && abs(dx) < 8 && abs(dy) < 8) return@addEventListener
        if (!dragging) {
            if (!startLift()) {
                holding = false
                return@addEventListener
            }
            dragging = true
            onDrag()
        }
        moveLift(event)
        event.preventDefault()
    })
    val endPointer = { _: dynamic ->
        if (holding) {
            holding = false
            if (dragging) {
                val dropAt = target
                if (lift != null && lift != undefined) {
                    lift.parentNode?.removeChild(lift)
                    lift = null
                }
                row.asDynamic().classList.remove("reordering")
                if (dropAt != origin) onMove(origin, dropAt) else cleanup()
            }
            dragging = false
        }
    }
    thumb.asDynamic().addEventListener("pointerup", endPointer)
    thumb.asDynamic().addEventListener("pointercancel", endPointer)
    thumb.asDynamic().addEventListener("lostpointercapture", endPointer)
}

private fun photoThumbShift(index: Int, origin: Int, target: Int, stride: Double): Double = when {
    index == origin -> (target - origin) * stride
    origin < target && index in (origin + 1)..target -> -stride
    target < origin && index in target until origin -> stride
    else -> 0.0
}

private fun dropIndexForMids(mids: List<Double>, x: Double, origin: Int): Int {
    if (mids.size <= 1) return origin
    for (i in mids.indices) if (x < mids[i]) return i
    return mids.lastIndex
}

private fun scrollPhotoThumbs(row: HTMLElement, clientX: Double) {
    val box = row.asDynamic().getBoundingClientRect()
    val max = num(row.asDynamic().scrollWidth) - num(row.asDynamic().clientWidth)
    if (max <= 0) return
    val edge = 32.0
    val sl = num(row.asDynamic().scrollLeft)
    when {
        clientX < num(box.left) + edge -> row.asDynamic().scrollLeft = maxOf(0.0, sl - 16.0)
        clientX > num(box.right) - edge -> row.asDynamic().scrollLeft = minOf(max, sl + 16.0)
    }
}

private fun rotateUploadPrompts(label: HTMLElement) {
    var index = 0
    var timer: dynamic = null
    timer = window.asDynamic().setInterval({
        if (label.asDynamic().isConnected != true) {
            window.asDynamic().clearInterval(timer)
        } else if (label.asDynamic().hidden != true) {
            index = (index + 1) % 10
            label.textContent = Town.t("uploadPrompt${index + 1}")
        }
    }, 3000)
}

private fun composerAvatarChoice(avatar: dynamic, onSelect: (String, HTMLElement) -> Unit): HTMLElement {
    val choice = el("button", "avatar-choice")
    choice.setAttribute("type", "button")
    val image = el("img")
    image.setAttribute("src", prefixServerOrigin(avatar.imageUrl as String))
    image.setAttribute("alt", avatar.prompt as? String ?: "")
    choice.appendChild(image)
    choice.onClick { onSelect(avatar.id as String, choice) }
    return choice
}

private fun hideAvatarStudio(studio: HTMLElement, ghost: HTMLElement) {
    studio.asDynamic().hidden = true
    ghost.asDynamic().classList.remove("open")
}

private fun markAvatarSelected(row: HTMLElement, choice: HTMLElement) {
    val choices = row.asDynamic().querySelectorAll(".avatar-choice")
    val count = choices.length as Int
    for (n in 0 until count) choices[n].classList.remove("selected")
    choice.asDynamic().classList.add("selected")
}

private fun show(error: HTMLElement, message: String) {
    error.textContent = message
    error.asDynamic().hidden = false
}
