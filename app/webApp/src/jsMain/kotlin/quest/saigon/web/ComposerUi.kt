package quest.saigon.web

import web.html.HTMLElement
import web.window.window

internal fun composerScreen(router: HistoryRouter): HTMLElement {
    val me = Town.me ?: return el("section", "shell")
    val shell = el("section", "shell page-shell")
    shell.appendChild(pageHead(router, Town.t("newPost"), showProfile = true))
    val body = el("div", "page-body")
    val error = el("p", "form-error")
    error.asDynamic().hidden = true
    var photoBlob: dynamic = null
    var selectedAvatar: String? = null

    val upload = el("button", "upload")
    upload.setAttribute("type", "button")
    val preview = el("img", "upload-preview")
    preview.asDynamic().hidden = true
    upload.appendChild(preview)
    upload.appendChild(el("span", "upload-label", text = Town.t("uploadPhoto")))
    val picker = el("input")
    picker.setAttribute("type", "file")
    picker.setAttribute("accept", "image/*")
    picker.asDynamic().hidden = true
    upload.onClick { picker.asDynamic().click() }
    picker.asDynamic().onchange = change@{
        val file = picker.asDynamic().files[0] ?: return@change
        preparePhoto(file, crop916 = true, { blob ->
            photoBlob = blob
            preview.setAttribute("src", window.asDynamic().URL.createObjectURL(blob) as String)
            preview.asDynamic().hidden = false
            upload.asDynamic().querySelector(".upload-label").hidden = true
        }, {
            error.textContent = Town.t("invalidPhoto")
            error.asDynamic().hidden = false
        })
    }

    val message = el("textarea")
    message.setAttribute("maxlength", "280")
    message.setAttribute("placeholder", Town.t("messagePlaceholder"))
    val messageLabel = el("label", "field")
    messageLabel.appendChild(el("span", text = Town.t("message")))
    messageLabel.appendChild(message)

    val location = field(Town.t("location"), "text", Town.t("locationHint"))
    location.input.asDynamic().value = me.location

    val avatarLabel = el("h3", text = Town.t("selectAvatar"))
    val avatarRow = el("div", "avatar-row")
    http("GET", "/api/avatars") { status, text ->
        if (status != 200) return@http
        val avatars = parseJson(text).avatars
        var found = false
        if (avatars != null) {
            val length = avatars.length as Int
            for (i in 0 until length) {
                val avatar = avatars[i]
                if (avatar.status != "ready" || avatar.imageUrl == null) continue
                found = true
                val choice = el("button", "avatar-choice")
                choice.setAttribute("type", "button")
                val image = el("img")
                image.setAttribute("src", prefixServerOrigin(avatar.imageUrl as String))
                image.setAttribute("alt", avatar.prompt as? String ?: "")
                choice.appendChild(image)
                choice.onClick {
                    selectedAvatar = avatar.id as String
                    val choices = avatarRow.asDynamic().querySelectorAll(".avatar-choice")
                    val count = choices.length as Int
                    for (n in 0 until count) choices[n].classList.remove("selected")
                    choice.asDynamic().classList.add("selected")
                }
                avatarRow.appendChild(choice)
            }
        }
        if (!found) avatarRow.appendChild(el("p", "hint", text = Town.t("noAvatars")))
    }

    val post = el("button", "primary", text = Town.t("postNow"))
    post.setAttribute("type", "button")
    post.onClick {
        error.asDynamic().hidden = true
        val text = (message.asDynamic().value as String).trim()
        val place = location.value().trim().ifBlank { me.location.trim() }
        when {
            photoBlob == null -> show(error, Town.t("photoRequired"))
            text.isEmpty() -> show(error, Town.t("invalidMessage"))
            place.isEmpty() -> show(error, Town.t("invalidLocation"))
            selectedAvatar == null -> show(error, Town.t("avatarRequired"))
            else -> {
                post.setAttribute("disabled", "disabled")
                post.textContent = Town.t("posting")
                val form = js("new FormData()")
                form.append("photo", photoBlob, "photo.jpg")
                form.append("message", text)
                form.append("location", place)
                form.append("avatarId", selectedAvatar)
                http("POST", "/api/posts", form = form) { status, body ->
                    post.removeAttribute("disabled")
                    post.textContent = Town.t("postNow")
                    if (status == 201) {
                        Town.pending = postFrom(parseJson(body))
                        router.navigate("/")
                    } else {
                        show(error, errorText(body, status))
                    }
                }
            }
        }
    }

    body.appendChild(upload)
    body.appendChild(picker)
    body.appendChild(messageLabel)
    body.appendChild(location.root)
    body.appendChild(avatarLabel)
    body.appendChild(avatarRow)
    body.appendChild(error)
    shell.appendChild(body)
    val bar = el("div", "dock dock-wide")
    bar.appendChild(post)
    shell.appendChild(bar)
    return shell
}

private fun show(error: HTMLElement, message: String) {
    error.textContent = message
    error.asDynamic().hidden = false
}
