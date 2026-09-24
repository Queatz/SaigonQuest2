package quest.saigon.web

import web.html.HTMLElement
import web.window.window

internal fun profileScreen(router: HistoryRouter): HTMLElement {
    val me = Town.me ?: return el("section", "shell")
    val shell = el("section", "shell page-shell")
    shell.appendChild(pageHead(router, Town.t("profile"), showProfile = false))
    val body = el("div", "page-body")
    val error = el("p", "form-error")
    error.asDynamic().hidden = true
    val photo = el("button", "profile-upload")
    photo.setAttribute("type", "button")
    photo.appendChild(profileFace(me, large = true))
    photo.appendChild(el("span", text = Town.t(if (me.hasProfilePhoto) "changePhoto" else "profilePhoto")))
    val picker = el("input")
    picker.setAttribute("type", "file")
    picker.setAttribute("accept", "image/*")
    picker.asDynamic().hidden = true
    photo.onClick { picker.asDynamic().click() }
    picker.asDynamic().onchange = change@{
        val file = picker.asDynamic().files[0] ?: return@change
        preparePhoto(file, crop916 = false, { blob ->
            val form = js("new FormData()")
            form.append("photo", blob, "profile.jpg")
            http("POST", "/api/me/photo", form = form) { status, text ->
                if (status == 200) {
                    Town.me = meFrom(parseJson(text))
                    refresh(router)
                } else {
                    error.textContent = errorText(text, status)
                    error.asDynamic().hidden = false
                }
            }
        }, {
            error.textContent = Town.t("invalidPhoto")
            error.asDynamic().hidden = false
        })
    }

    val name = field(Town.t("yourName"), "text", "")
    name.input.asDynamic().value = me.name
    val phone = field(Town.t("phone"), "tel", Town.t("phoneHint"))
    phone.input.asDynamic().value = me.phone
    val location = field(Town.t("yourLocation"), "text", Town.t("locationHint"))
    location.input.asDynamic().value = me.location
    val languageSelect = languageField(me.language) { value ->
        val typed = Town.me
        if (typed != null) {
            Town.me = typed.copy(
                name = name.value(),
                phone = phone.value(),
                location = location.value(),
                language = value,
            )
        }
        Town.rememberLanguage(value)
        Town.applyLanguage()
        refresh(router)
        val payload = js("({})")
        payload.language = value
        http("PATCH", "/api/me", jsonStringify(payload)) { status, text ->
            if (status == 200) {
                val saved = meFrom(parseJson(text))
                val pending = Town.me
                Town.me = if (pending == null) saved else saved.copy(
                    name = pending.name,
                    phone = pending.phone,
                    location = pending.location,
                )
                Town.rememberLanguage(saved.language)
                Town.applyLanguage()
            } else {
                val host = shellOf(shell)
                toast(host, errorText(text, status))
            }
        }
    }
    val save = el("button", "primary", text = Town.t("save"))
    save.setAttribute("type", "button")
    save.onClick {
        save.setAttribute("disabled", "disabled")
        save.textContent = Town.t("saving")
        val payload = js("({})")
        payload.name = name.value()
        payload.phone = phone.value()
        payload.location = location.value()
        payload.language = languageSelect.asDynamic().querySelector("select").value
        http("PATCH", "/api/me", jsonStringify(payload)) { status, text ->
            save.removeAttribute("disabled")
            save.textContent = Town.t("save")
            if (status == 200) {
                Town.me = meFrom(parseJson(text))
                Town.rememberLanguage(Town.me?.language ?: "system")
                Town.applyLanguage()
                toast(shell, Town.t("saved"))
                refresh(router)
            } else {
                error.textContent = errorText(text, status)
                error.asDynamic().hidden = false
            }
        }
    }

    body.appendChild(photo)
    body.appendChild(picker)
    body.appendChild(name.root)
    body.appendChild(phone.root)
    body.appendChild(location.root)
    body.appendChild(error)
    body.appendChild(el("h3", text = Town.t("createAvatar")))
    body.appendChild(avatarStudio(shell, router, me))
    body.appendChild(el("h3", text = Town.t("settings")))
    body.appendChild(languageSelect)
    body.appendChild(el("button", "text-button danger", text = Town.t("signOut")) { button ->
        button.setAttribute("type", "button")
        button.onClick {
            http("POST", "/api/auth/logout") { _, _ ->
                Town.me = null
                Town.applyLanguage()
                router.navigate("/")
            }
        }
    })
    shell.appendChild(body)
    val bar = el("div", "dock dock-wide")
    bar.appendChild(save)
    shell.appendChild(bar)
    return shell
}

private fun avatarStudio(host: HTMLElement, router: HistoryRouter, me: Me): HTMLElement {
    val box = el("div", "studio")
    val row = el("div", "avatar-row")
    box.appendChild(row)
    val prompt = el("input")
    prompt.setAttribute("type", "text")
    prompt.setAttribute("maxlength", "200")
    prompt.setAttribute("placeholder", Town.t("avatarPrompt"))
    val create = el("button", "secondary", text = Town.t("createAvatar"))
    create.setAttribute("type", "button")
    val note = el("p", "hint", text = if (me.hasProfilePhoto) "" else Town.t("needProfilePhoto"))
    create.onClick {
        if (!me.hasProfilePhoto) return@onClick
        val text = (prompt.asDynamic().value as String).trim()
        if (text.isEmpty()) {
            toast(host, Town.t("invalidPrompt"))
            return@onClick
        }
        create.setAttribute("disabled", "disabled")
        val payload = js("({})")
        payload.prompt = text
        http("POST", "/api/avatars", jsonStringify(payload)) { status, body ->
            create.removeAttribute("disabled")
            if (status == 202 || status == 200) {
                prompt.asDynamic().value = ""
                pollAvatar(parseJson(body).id as String, router)
                refresh(router)
            } else {
                toast(host, errorText(body, status))
            }
        }
    }
    box.appendChild(prompt)
    box.appendChild(create)
    box.appendChild(note)
    http("GET", "/api/avatars") { status, body ->
        if (status != 200) return@http
        val avatars = parseJson(body).avatars
        if (avatars == null || avatars.length == 0) {
            row.appendChild(el("p", "hint", text = Town.t("noAvatars")))
            return@http
        }
        val length = avatars.length as Int
        for (i in 0 until length) {
            row.appendChild(avatarTile(avatars[i], router))
        }
    }
    return box
}

private fun avatarTile(avatar: dynamic, router: HistoryRouter): HTMLElement {
    val tile = el("div", "avatar-tile")
    val status = avatar.status as? String
    val id = avatar.id as String
    if (status == "ready" && avatar.imageUrl != null) {
        val image = el("img")
        image.setAttribute("src", prefixServerOrigin(avatar.imageUrl as String))
        image.setAttribute("alt", avatar.prompt as? String ?: "")
        tile.appendChild(image)
    } else if (status == "failed") {
        tile.appendChild(el("span", "avatar-state", text = failureText(avatar.error as? String)))
    } else {
        tile.appendChild(el("span", "avatar-state", text = Town.t("generating")))
        pollAvatar(id, router)
    }
    tile.appendChild(el("small", text = avatar.prompt as? String ?: ""))
    val actions = el("div", "avatar-actions")
    if (status == "failed") {
        actions.appendChild(el("button", "tiny", text = Town.t("retry")) { button ->
            button.setAttribute("type", "button")
            button.onClick {
                button.setAttribute("disabled", "disabled")
                http("POST", "/api/avatars/$id/retry") { code, body ->
                    if (code == 202 || code == 200) {
                        pollAvatar(id, router)
                        refresh(router)
                    } else {
                        button.removeAttribute("disabled")
                        toast(shellOf(tile), errorText(body, code))
                    }
                }
            }
        })
    }
    if (status != "generating") {
        actions.appendChild(el("button", "tiny danger", text = Town.t("delete")) { button ->
            button.setAttribute("type", "button")
            button.onClick {
                confirmDelete(shellOf(tile)) {
                    http("DELETE", "/api/avatars/$id") { code, body ->
                        if (code == 200) refresh(router) else toast(shellOf(tile), errorText(body, code))
                    }
                }
            }
        })
    }
    if (actions.childNodes.length > 0) tile.appendChild(actions)
    return tile
}

private fun failureText(reason: String?): String {
    if (reason.isNullOrBlank() || reason == "generation_failed") return Town.t("avatarFailed")
    return Town.t("avatarFailedDetail", mapOf("reason" to reason))
}

private fun shellOf(node: HTMLElement): HTMLElement {
    val shell = node.asDynamic().closest(".shell")
    return if (shell == null || shell == undefined) node else shell as HTMLElement
}

private fun confirmDelete(host: HTMLElement, onYes: () -> Unit) {
    if (host.asDynamic().querySelector(".dialog-backdrop") != null) return
    val backdrop = el("div", "dialog-backdrop")
    val card = el("div", "dialog")
    card.appendChild(el("h2", text = Town.t("deleteAvatarAsk")))
    card.appendChild(el("p", "hint", text = Town.t("deleteAvatarHint")))
    val actions = el("div", "dialog-actions")
    actions.appendChild(el("button", "text-button", text = Town.t("cancel")) { button ->
        button.setAttribute("type", "button")
        button.onClick { backdrop.asDynamic().remove() }
    })
    actions.appendChild(el("button", "primary danger", text = Town.t("delete")) { button ->
        button.setAttribute("type", "button")
        button.onClick {
            backdrop.asDynamic().remove()
            onYes()
        }
    })
    card.appendChild(actions)
    backdrop.appendChild(card)
    host.appendChild(backdrop)
}

private fun pollAvatar(id: String, router: HistoryRouter) {
    window.asDynamic().setTimeout({
        http("GET", "/api/avatars/$id") { status, body ->
            if (status != 200) return@http
            val statusName = parseJson(body).status as? String
            if (statusName == "generating") pollAvatar(id, router) else refresh(router)
        }
    }, 2000)
}

private fun languageField(current: String, onChange: (String) -> Unit): HTMLElement {
    val label = el("label", "field")
    label.appendChild(el("span", text = Town.t("language")))
    val select = el("select")
    listOf("system" to Town.t("languageSystem"), "vi" to Town.t("languageVi"), "en" to Town.t("languageEn")).forEach { (value, text) ->
        val option = el("option", text = text)
        option.setAttribute("value", value)
        if (value == current) option.setAttribute("selected", "selected")
        select.appendChild(option)
    }
    select.asDynamic().onchange = { onChange(select.asDynamic().value as String) }
    label.appendChild(select)
    return label
}

internal fun profileFace(me: Me, large: Boolean): HTMLElement {
    return if (me.hasProfilePhoto) {
        el("img", if (large) "face large" else "face") { image ->
            image.setAttribute("src", prefixServerOrigin("/api/me/photo?v=${me.id}"))
            image.setAttribute("alt", "")
        }
    } else {
        el("span", if (large) "face large initial" else "face initial", text = me.name.take(1).uppercase())
    }
}
