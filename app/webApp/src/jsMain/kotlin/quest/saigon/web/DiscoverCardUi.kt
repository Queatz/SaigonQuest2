package quest.saigon.web

import web.html.HTMLElement

internal data class DiscoverCardPerson(val name: String, val photoUrl: String? = null)
internal data class DiscoverCardPhoto(val id: String, val photoUrl: String)
internal data class DiscoverCardDetail(
    val text: String,
    val emphasis: Boolean = false,
    val expanded: Boolean = false,
)

internal fun discoverBannerCard(
    photoUrl: String?,
    fallback: String,
    title: String,
    details: List<DiscoverCardDetail>,
    people: List<DiscoverCardPerson> = emptyList(),
    titlePerson: DiscoverCardPerson? = null,
    photos: List<DiscoverCardPhoto> = emptyList(),
    action: HTMLElement? = null,
    onClick: (() -> Unit)? = null,
    onPhotoClick: ((String) -> Unit)? = null,
): HTMLElement {
    val card = el("article", if (onClick == null) "discover-card" else "discover-card is-clickable")
    val banner = el("div", "discover-card-banner")
    if (photoUrl.isNullOrBlank()) {
        banner.appendChild(el("span", "discover-card-fallback", text = fallback))
    } else {
        val image = el("img", "discover-card-photo")
        image.setAttribute("src", prefixServerOrigin(photoUrl))
        image.setAttribute("alt", "")
        image.setAttribute("loading", "lazy")
        banner.appendChild(image)
    }
    card.appendChild(banner)

    val content = el("div", "discover-card-content")
    val titleRow = el("div", "discover-card-title-row")
    titlePerson?.let { person ->
        val avatar = if (person.photoUrl.isNullOrBlank()) {
            el("span", "discover-card-title-avatar", text = person.name.take(1).uppercase())
        } else {
            el("img", "discover-card-title-avatar")
        }
        if (avatar.tagName.equals("IMG", ignoreCase = true)) {
            avatar.setAttribute("src", prefixServerOrigin(person.photoUrl.orEmpty()))
            avatar.setAttribute("alt", person.name)
            avatar.setAttribute("loading", "lazy")
        } else {
            avatar.setAttribute("aria-label", person.name)
        }
        titleRow.appendChild(avatar)
    }
    titleRow.appendChild(el("strong", "discover-card-title", text = title))
    content.appendChild(titleRow)
    details.filter { it.text.isNotBlank() }.forEach { detail ->
        val classes = listOf(
            "discover-card-detail",
            if (detail.emphasis) "emphasized" else "",
            if (detail.expanded) "expanded" else "",
        ).filter(String::isNotBlank).joinToString(" ")
        content.appendChild(el("span", classes, text = detail.text))
    }
    if (photos.isNotEmpty()) {
        val thumbnails = el("div", "discover-card-thumbnails")
        photos.forEach { photo ->
            val thumbnail = el("button", "discover-card-thumbnail")
            thumbnail.setAttribute("type", "button")
            thumbnail.setAttribute("aria-label", title)
            val image = el("img")
            image.setAttribute("src", prefixServerOrigin(photo.photoUrl))
            image.setAttribute("alt", "")
            image.setAttribute("loading", "lazy")
            thumbnail.appendChild(image)
            onPhotoClick?.let { onOpen ->
                thumbnail.asDynamic().addEventListener("click", { event: dynamic ->
                    event.stopPropagation()
                    onOpen(photo.id)
                })
            }
            thumbnails.appendChild(thumbnail)
        }
        content.appendChild(thumbnails)
    }
    val footer = el("div", "discover-card-footer")
    val faces = el("div", "discover-card-people")
    people.take(5).forEach { person ->
        val face = if (person.photoUrl.isNullOrBlank()) {
            el("span", "discover-card-face initial", text = person.name.take(1).uppercase())
        } else {
            el("img", "discover-card-face")
        }
        if (face.tagName.equals("IMG", ignoreCase = true)) {
            face.setAttribute("src", prefixServerOrigin(person.photoUrl.orEmpty()))
            face.setAttribute("alt", person.name)
            face.setAttribute("loading", "lazy")
        } else {
            face.setAttribute("aria-label", person.name)
        }
        faces.appendChild(face)
    }
    footer.appendChild(faces)
    if (action != null) {
        action.asDynamic().addEventListener("click", { event: dynamic -> event.stopPropagation() })
        footer.appendChild(action)
    }
    content.appendChild(footer)
    card.appendChild(content)
    onClick?.let(card::onClick)
    return card
}