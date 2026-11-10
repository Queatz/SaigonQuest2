package quest.saigon.web

import quest.saigon.Limits
import quest.saigon.encodeLocationToken
import web.html.HTMLElement
import web.window.WindowTarget
import web.window._blank
import web.window.window

internal fun openLocationSheet(
    host: HTMLElement,
    router: HistoryRouter,
    location: String,
    placeId: String?,
    lat: Double? = null,
    lng: Double? = null,
    customLocationId: String? = null,
) {
    val place = location.trim()
    if (place.isBlank()) return
    if (host.asDynamic().querySelector(".location-sheet") != null) return
    val chrome = SheetChrome("comment-sheet location-sheet", place)
    val body = el("div", "location-sheet-body")
    val mapBox = el("div", "location-sheet-map")
    val directions = el("button", "primary location-sheet-directions", text = Town.t("directions"))
    directions.setAttribute("type", "button")
    directions.onClick { window.open(mapsUrl(place, placeId, lat, lng), WindowTarget._blank) }
    val actions = el("div", "location-sheet-actions")
    actions.appendChild(directions)
    val countHead = el("h3", "location-sheet-count")
    countHead.asDynamic().hidden = true
    val grid = el("div", "location-sheet-grid")
    val canShowMap = lat != null && lng != null
    if (canShowMap) body.appendChild(mapBox)
    val linkId = placeId?.takeIf { it.isNotBlank() }
        ?: customLocationId?.takeIf { it.isNotBlank() }
        ?: encodeLocationToken(place)
    actions.appendChild(copyLinkIconButton(host, "/l/$linkId", "location-sheet-copy-link"))
    body.appendChild(actions)
    val galleryLocationId = customLocationId?.takeIf { it.isNotBlank() }
    val galleryPlaceId = placeId?.takeIf { it.isNotBlank() && galleryLocationId == null }
    val galleryQuery = when {
        galleryLocationId != null -> "&locationId=${encodeQuery(galleryLocationId)}"
        galleryPlaceId != null -> "&placeId=${encodeQuery(galleryPlaceId)}"
        else -> ""
    }
    val galleryFields = buildMap {
        put("name", place)
        if (galleryLocationId != null) put("locationId", galleryLocationId)
        else if (galleryPlaceId != null) put("placeId", galleryPlaceId)
    }
    body.appendChild(locationDescriptionSection(host, place, galleryPlaceId, galleryLocationId))
    body.appendChild(
        userPhotosSection(
            host,
            "/api/places/photos?name=${encodeQuery(place)}$galleryQuery",
            "/api/places/photos",
            galleryFields,
        ) { photoId -> "/api/places/photos/${encodeQuery(photoId)}" },
    )
    val activityList = el("div", "activity-list")
    val activitiesHead = el("div", "section-head")
    activitiesHead.appendChild(el("h3", text = Town.t("activities")))
    body.appendChild(activitiesHead)
    body.appendChild(activityList)
    body.appendChild(countHead)
    body.appendChild(grid)
    chrome.sheet.appendChild(body)

    var cursor: String? = null
    var loading = false
    var done = false

    fun load() {
        if (loading || done) return
        loading = true
        val id = if (placeId.isNullOrBlank()) "" else "&placeId=${encodeQuery(placeId)}"
        val customId = customLocationId?.takeIf { it.isNotBlank() }
            ?.let { "&locationId=${encodeQuery(it)}" }.orEmpty()
        val next = cursor
        val more = if (next.isNullOrBlank()) "" else "&cursor=${encodeQuery(next)}"
        http("GET", "/api/places/posts?name=${encodeQuery(place)}$id$customId$more&limit=24") { status, text ->
            loading = false
            if (status != 200) {
                if (grid.childElementCount == 0) {
                    grid.appendChild(el("p", "hint", text = Town.t("unavailable")))
                }
                done = true
                return@http
            }
            val parsed = parseJson(text)
            val total = num(parsed.count).toInt()
            countHead.textContent = Town.t("postCount", mapOf("count" to total.toString()))
            countHead.asDynamic().hidden = false
            val posts = readLocationPosts(parsed.posts)
            if (posts.isEmpty() && grid.childElementCount == 0) {
                grid.appendChild(el("p", "hint", text = Town.t("noPosts")))
            } else {
                posts.forEach { post -> grid.appendChild(locationPostTile(post, router, showMessage = true)) }
            }
            cursor = parsed.nextCursor as? String
            if (cursor.isNullOrBlank()) done = true
        }
    }

    body.asDynamic().addEventListener("scroll", {
        val top = num(body.asDynamic().scrollTop)
        val height = num(body.asDynamic().clientHeight)
        val full = num(body.asDynamic().scrollHeight)
        if (top + height > full - 80) load()
    })
    chrome.open(host) {
        if (canShowMap) mountPlaceMap(mapBox, lat, lng, place, placeId)
        loadLocationActivities(activityList, host, router, place, placeId, customLocationId, lat, lng)
        load()
    }
}

private fun locationDescriptionSection(
    host: HTMLElement,
    locationName: String,
    placeId: String?,
    locationId: String?,
): HTMLElement {
    val section = el("section", "location-details")
    val head = el("div", "section-head")
    head.appendChild(el("h3", text = Town.t("details")))
    val content = el("div", "location-description-about")
    section.appendChild(head)
    section.appendChild(content)
    var description = ""
    var editing = false
    var loaded = false

    fun paint() {
        content.clear()
        if (editing) {
            val input = el("textarea", "location-description-input")
            input.setAttribute("maxlength", Limits.LOCATION_DESCRIPTION_MAX.toString())
            input.setAttribute("placeholder", Town.t("locationDescriptionHint"))
            input.setAttribute("aria-label", Town.t("locationDescription"))
            input.asDynamic().value = description
            val actions = el("div", "location-description-actions")
            val save = el("button", "primary", text = Town.t("save"))
            save.setAttribute("type", "button")
            val cancel = el("button", "text-button", text = Town.t("cancel"))
            cancel.setAttribute("type", "button")
            save.onClick {
                val payload = js("({})")
                payload.name = locationName
                if (locationId != null) payload.locationId = locationId
                else if (placeId != null) payload.placeId = placeId
                payload.description = (input.asDynamic().value as? String).orEmpty()
                save.setAttribute("disabled", "disabled")
                http("PATCH", "/api/places/description", jsonStringify(payload)) { status, body ->
                    save.removeAttribute("disabled")
                    if (status != 200) {
                        toast(host, errorText(body, status))
                        return@http
                    }
                    description = (parseJson(body).description as? String).orEmpty()
                    editing = false
                    paint()
                }
            }
            cancel.onClick {
                editing = false
                paint()
            }
            actions.appendChild(save)
            actions.appendChild(cancel)
            content.appendChild(input)
            content.appendChild(actions)
            window.asDynamic().setTimeout({ input.asDynamic().focus() }, 30)
            return
        }
        if (description.isNotBlank()) {
            content.appendChild(linkedParagraph(description, "location-description"))
        }
        if (loaded) {
            val label = Town.t(if (description.isBlank()) "addDescription" else "edit")
            val edit = el("button", "text-button", text = label)
            edit.setAttribute("type", "button")
            edit.onClick {
                if (Town.me == null) {
                    showAuthDialog(host, onSuccess = {
                        editing = true
                        paint()
                    })
                } else {
                    editing = true
                    paint()
                }
            }
            content.appendChild(edit)
        }
    }

    paint()
    val query = buildString {
        append("?name=${encodeQuery(locationName)}")
        if (locationId != null) append("&locationId=${encodeQuery(locationId)}")
        else if (placeId != null) append("&placeId=${encodeQuery(placeId)}")
    }
    http("GET", "/api/places/description$query") { status, body ->
        loaded = true
        if (status == 200 && !editing) {
            description = (parseJson(body).description as? String).orEmpty()
        }
        paint()
    }
    return section
}

internal fun locationPostTile(
    post: PostCard,
    router: HistoryRouter,
    showAuthor: Boolean = true,
    onClick: (() -> Unit)? = null,
    showMessage: Boolean = false,
): HTMLElement {
    val tile = el("button", "location-post")
    tile.setAttribute("type", "button")
    tile.setAttribute("aria-label", post.authorName)
    if (post.photoUrl.isNotBlank()) {
        tile.asDynamic().style.backgroundImage = "url(\"${post.photoUrl}\")"
    }
    val author = if (showAuthor) {
        val author = el("span", "location-post-author")
        if (!post.authorPhotoUrl.isNullOrBlank()) {
            val face = el("img", "face")
            face.setAttribute("alt", "")
            face.setAttribute("src", post.authorPhotoUrl)
            author.appendChild(face)
        } else {
            author.appendChild(el("span", "face initial", text = post.authorName.take(1).uppercase()))
        }
        author.appendChild(el("span", "location-post-name", text = post.authorName))
        author
    } else {
        null
    }
    if (showMessage && post.message.isNotBlank()) {
        val caption = el("span", "location-post-caption")
        caption.appendChild(el("span", "location-post-message", text = post.message))
        if (author != null) caption.appendChild(author)
        tile.appendChild(caption)
    } else if (author != null) {
        tile.appendChild(author)
    }
    tile.onClick { if (onClick == null) router.navigate("/p/${post.id}") else onClick() }
    return tile
}

internal fun readLocationPosts(array: dynamic): List<PostCard> {
    val posts = mutableListOf<PostCard>()
    if (array == null || array == undefined) return posts
    val length = array.length as Int
    for (i in 0 until length) posts += postFrom(array[i])
    return posts
}
