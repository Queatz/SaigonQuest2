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
    var selectedMusicId: String? = null

    val upload = el("button", "upload")
    upload.setAttribute("type", "button")
    val preview = el("img", "upload-preview")
    preview.asDynamic().hidden = true
    upload.appendChild(preview)
    val label = el("span", "upload-label", text = Town.t("uploadPrompt1"))
    upload.appendChild(label)
    rotateUploadPrompts(label)
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
    val studio = el("div", "studio")
    studio.asDynamic().hidden = true
    val ghost = el("button", "avatar-choice ghost")
    ghost.setAttribute("type", "button")
    ghost.setAttribute("aria-label", Town.t("createAvatar"))
    ghost.textContent = "+"
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
    studio.appendChild(avatarPromptForm(shell, me) { id ->
        studio.asDynamic().hidden = true
        ghost.asDynamic().classList.remove("open")
        val pending = el("button", "avatar-choice generating")
        pending.setAttribute("type", "button")
        pending.setAttribute("disabled", "disabled")
        pending.appendChild(el("span", "avatar-spinner"))
        avatarRow.asDynamic().insertBefore(pending, ghost.asDynamic().nextSibling)
        pollAvatar(id) { avatar ->
            if (avatar.status == "ready" && avatar.imageUrl != null) {
                val choice = composerAvatarChoice(avatar) { selectedId, button ->
                    selectedAvatar = selectedId
                    hideAvatarStudio(studio, ghost)
                    markAvatarSelected(avatarRow, button)
                }
                pending.asDynamic().replaceWith(choice)
                selectedAvatar = avatar.id as String
                hideAvatarStudio(studio, ghost)
                markAvatarSelected(avatarRow, choice)
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
                selectedAvatar = selectedId
                hideAvatarStudio(studio, ghost)
                markAvatarSelected(avatarRow, button)
            })
        }
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
                val musicId = selectedMusicId
                if (!musicId.isNullOrBlank()) form.append("musicId", musicId)
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
