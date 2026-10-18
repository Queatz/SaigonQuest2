package quest.saigon.web

import quest.saigon.Limits
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
    val intro = area(Town.t("introduceYourself"), Town.t("introduceYourselfHint"), Limits.INTRO_MAX)
    intro.input.asDynamic().value = me.intro
    val languageSelect = languageField(me.language) { value ->
        val typed = Town.me
        if (typed != null) {
            Town.me = typed.copy(
                name = name.value(),
                phone = phone.value(),
                location = location.value(),
                intro = intro.value(),
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
                    intro = pending.intro,
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
        payload.intro = intro.value()
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
    body.appendChild(intro.root)
    body.appendChild(error)
    body.appendChild(el("h3", text = Town.t("communities")))
    body.appendChild(communitiesPanel(shell, router))
    body.appendChild(el("h3", text = Town.t("createAvatar")))
    body.appendChild(avatarStudio(shell, router, me))
    body.appendChild(el("h3", text = Town.t("yourMusic")))
    body.appendChild(musicStudio(shell, router))
    body.appendChild(postsPreview(shell, router))
    body.appendChild(el("h3", text = Town.t("settings")))
    body.appendChild(languageSelect)
    body.appendChild(el("button", "text-button danger", text = Town.t("signOut")) { button ->
        button.setAttribute("type", "button")
        button.onClick {
            http("POST", "/api/auth/logout") { _, _ ->
                Town.me = null
                Town.applyLanguage()
                NotifyInbox.disconnect()
                router.navigate("/")
            }
        }
    })
    body.appendChild(suggestFeaturePanel(shell, router, me.admin))
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
    box.appendChild(avatarPromptForm(host, me) { id ->
        pollAvatar(id) { refresh(router) }
        refresh(router)
    })
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
        tile.asDynamic().classList.add("generating")
        tile.appendChild(el("span", "avatar-spinner"))
        tile.appendChild(el("span", "avatar-state", text = Town.t("generating")))
        pollAvatar(id) { refresh(router) }
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
                        pollAvatar(id) { refresh(router) }
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
    confirmDialog(host, Town.t("deleteAvatarAsk"), Town.t("deleteAvatarHint"), onYes)
}

internal fun confirmDialog(host: HTMLElement, title: String, hint: String, onYes: () -> Unit) {
    if (host.asDynamic().querySelector(".confirm-backdrop") != null) return
    val backdrop = el("div", "dialog-backdrop confirm-backdrop")
    val card = el("div", "dialog")
    card.appendChild(el("h2", text = title))
    card.appendChild(el("p", "hint", text = hint))
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
    backdrop.closeOnOutsideClick { backdrop.asDynamic().remove() }
    host.appendChild(backdrop)
}

internal fun avatarPromptForm(host: HTMLElement, me: Me, onCreated: (String) -> Unit): HTMLElement {
    val box = el("div")
    box.setAttribute("style", "display: contents;")
    val prompt = el("input")
    prompt.setAttribute("type", "text")
    prompt.setAttribute("maxlength", "200")
    prompt.setAttribute("placeholder", Town.t("avatarPrompt"))
    val create = el("button", "secondary", text = Town.t("createAvatar"))
    create.setAttribute("type", "button")
    create.setAttribute("style", "margin-top: 8px;")
    val note = el("p", "form-error")
    note.asDynamic().hidden = true
    create.onClick {
        if (!me.hasProfilePhoto) {
            note.textContent = Town.t("needProfilePhoto")
            note.asDynamic().hidden = false
            return@onClick
        }
        val text = (prompt.asDynamic().value as String).trim()
        if (text.isEmpty()) {
            toast(host, Town.t("invalidPrompt"))
            return@onClick
        }
        create.setAttribute("disabled", "disabled")
        create.asDynamic().classList.add("busy")
        create.textContent = ""
        create.appendChild(el("span", "avatar-spinner"))
        val payload = js("({})")
        payload.prompt = text
        http("POST", "/api/avatars", jsonStringify(payload)) { status, body ->
            create.removeAttribute("disabled")
            create.asDynamic().classList.remove("busy")
            create.textContent = Town.t("createAvatar")
            if (status == 202 || status == 200) {
                prompt.asDynamic().value = ""
                onCreated(parseJson(body).id as String)
            } else {
                toast(host, errorText(body, status))
            }
        }
    }
    box.appendChild(prompt)
    box.appendChild(create)
    box.appendChild(note)
    return box
}

internal fun pollAvatar(id: String, onDone: (dynamic) -> Unit) {
    window.asDynamic().setTimeout({
        http("GET", "/api/avatars/$id") { status, body ->
            if (status != 200) return@http
            val parsed = parseJson(body)
            val statusName = parsed.status as? String
            if (statusName == "generating") pollAvatar(id, onDone) else onDone(parsed)
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

internal fun featureSuggestionsScreen(router: HistoryRouter): HTMLElement {
    if (Town.me == null) return el("section", "shell")
    val shell = el("section", "shell page-shell")
    shell.appendChild(pageHead(router, Town.t("allFeatures"), showProfile = false))
    val body = el("div", "page-body")
    val list = el("div", "feature-list")
    body.appendChild(list)
    shell.appendChild(body)
    loadFeatures(list, shell, router, limit = 100, openOnly = false, admin = Town.me?.admin == true)
    return shell
}

private fun suggestFeaturePanel(shell: HTMLElement, router: HistoryRouter, admin: Boolean): HTMLElement {
    val section = el("section", "features-preview")
    val head = el("div", "section-head")
    head.appendChild(el("h3", text = Town.t("suggestFeature")))
    val seeAll = el("button", "see-all", text = Town.t("seeAll"))
    seeAll.setAttribute("type", "button")
    seeAll.onClick { go(router, "/profile/features") }
    head.appendChild(seeAll)
    section.appendChild(head)
    val form = el("div", "feature-form")
    val input = el("textarea")
    input.setAttribute("maxlength", Limits.FEATURE_MAX.toString())
    input.setAttribute("placeholder", Town.t("suggestFeatureHint"))
    val submit = el("button", "secondary", text = Town.t("submitFeature"))
    submit.setAttribute("type", "button")
    submit.onClick {
        val text = (input.asDynamic().value as String).trim()
        if (text.isEmpty()) {
            toast(shell, Town.t("invalidFeature"))
            return@onClick
        }
        submit.setAttribute("disabled", "disabled")
        val payload = js("({})")
        payload.text = text
        http("POST", "/api/features", jsonStringify(payload)) { status, body ->
            submit.removeAttribute("disabled")
            if (status == 200 || status == 201) {
                input.asDynamic().value = ""
                toast(shell, Town.t("featureSubmitted"))
                refresh(router)
            } else {
                toast(shell, errorText(body, status))
            }
        }
    }
    form.appendChild(input)
    form.appendChild(submit)
    section.appendChild(form)
    val list = el("div", "feature-list")
    section.appendChild(list)
    loadFeatures(list, shell, router, limit = 3, openOnly = true, admin)
    return section
}

private fun loadFeatures(
    list: HTMLElement,
    shell: HTMLElement,
    router: HistoryRouter,
    limit: Int,
    openOnly: Boolean,
    admin: Boolean,
) {
    val query = if (openOnly) "/api/features?limit=$limit&open=1" else "/api/features?limit=$limit"
    http("GET", query) { status, text ->
        if (status != 200) {
            if (list.childNodes.length == 0) list.appendChild(el("p", "hint", text = Town.t("unavailable")))
            return@http
        }
        val parsed = parseJson(text)
        val length = postCount(parsed.features)
        if (length == 0) {
            list.appendChild(el("p", "hint", text = Town.t("noFeatures")))
            return@http
        }
        for (i in 0 until length) list.appendChild(featureRow(parsed.features[i], shell, router, admin))
    }
}

private fun featureRow(item: dynamic, shell: HTMLElement, router: HistoryRouter, admin: Boolean): HTMLElement {
    val row = el("article", "feature-row")
    fun paint(current: dynamic) {
        row.clear()
        if (current.resolved == true) {
            row.asDynamic().classList.add("resolved")
        } else {
            row.asDynamic().classList.remove("resolved")
        }
        row.appendChild(el("p", "feature-text", text = current.text as? String ?: ""))
        val meta = el("div", "feature-meta")
        if (current.resolved == true) {
            meta.appendChild(el("span", "feature-resolved", text = Town.t("featureResolved")))
        }
        val voted = current.voted == true
        val vote = el("button", if (voted) "tiny voted" else "tiny", text = Town.t(if (voted) "voted" else "vote"))
        vote.setAttribute("type", "button")
        vote.onClick {
            if (Town.me == null) {
                toast(shell, Town.t("needSignInToVote"))
                return@onClick
            }
            val id = current.id as String
            vote.setAttribute("disabled", "disabled")
            http("POST", "/api/features/$id/vote") { status, body ->
                vote.removeAttribute("disabled")
                if (status == 200) paint(parseJson(body)) else toast(shell, errorText(body, status))
            }
        }
        meta.appendChild(vote)
        meta.appendChild(
            el(
                "span",
                "feature-votes",
                text = Town.t("votes", mapOf("count" to num(current.voteCount).toLong().toString())),
            ),
        )
        if (admin) {
            if (current.resolved != true) {
                meta.appendChild(el("button", "tiny", text = Town.t("resolveFeature")) { button ->
                    button.setAttribute("type", "button")
                    button.onClick {
                        button.setAttribute("disabled", "disabled")
                        http("POST", "/api/features/${current.id}/resolve") { code, body ->
                            if (code == 200) paint(parseJson(body)) else {
                                button.removeAttribute("disabled")
                                toast(shell, errorText(body, code))
                            }
                        }
                    }
                })
            }
            meta.appendChild(el("button", "tiny danger", text = Town.t("delete")) { button ->
                button.setAttribute("type", "button")
                button.onClick {
                    confirmDialog(shell, Town.t("deleteFeatureAsk"), Town.t("deleteFeatureHint")) {
                        http("DELETE", "/api/features/${current.id}") { code, body ->
                            if (code == 200) {
                                row.asDynamic().remove()
                                if (shell.asDynamic().querySelector(".feature-row") == null) refresh(router)
                            } else {
                                toast(shell, errorText(body, code))
                            }
                        }
                    }
                }
            })
        }
        row.appendChild(meta)
    }
    paint(item)
    return row
}

internal fun myPostsScreen(router: HistoryRouter): HTMLElement {
    if (Town.me == null) return el("section", "shell")
    val shell = el("section", "shell page-shell")
    shell.appendChild(pageHead(router, Town.t("yourPosts"), showProfile = false))
    val body = el("div", "page-body")
    val list = el("div", "own-posts")
    body.appendChild(list)
    shell.appendChild(body)
    loadOwnPosts(list, shell, router, null)
    return shell
}

private fun postsPreview(shell: HTMLElement, router: HistoryRouter): HTMLElement {
    val section = el("section", "posts-preview")
    val head = el("div", "section-head")
    head.appendChild(el("h3", text = Town.t("yourPosts")))
    val seeAll = el("button", "see-all", text = Town.t("seeAll"))
    seeAll.setAttribute("type", "button")
    seeAll.asDynamic().hidden = true
    seeAll.onClick { go(router, "/profile/posts") }
    head.appendChild(seeAll)
    section.appendChild(head)
    val list = el("div", "own-posts")
    section.appendChild(list)
    http("GET", "/api/me/posts?limit=3") { status, text ->
        if (status != 200) return@http
        val posts = parseJson(text).posts
        val length = postCount(posts)
        if (length == 0) {
            list.appendChild(el("p", "hint", text = Town.t("noPosts")))
            return@http
        }
        seeAll.asDynamic().hidden = false
        for (i in 0 until length) list.appendChild(ownPostRow(posts[i], shell, router))
    }
    return section
}

private fun loadOwnPosts(list: HTMLElement, shell: HTMLElement, router: HistoryRouter, cursor: String?) {
    val query = if (cursor.isNullOrBlank()) {
        "/api/me/posts?limit=20"
    } else {
        "/api/me/posts?limit=20&cursor=${encodeQuery(cursor)}"
    }
    http("GET", query) { status, text ->
        if (status != 200) {
            if (list.childNodes.length == 0) list.appendChild(el("p", "hint", text = Town.t("unavailable")))
            return@http
        }
        val parsed = parseJson(text)
        val posts = parsed.posts
        val length = postCount(posts)
        if (length == 0 && list.childNodes.length == 0) {
            list.appendChild(el("p", "hint", text = Town.t("noPosts")))
            return@http
        }
        for (i in 0 until length) list.appendChild(ownPostRow(posts[i], shell, router))
        val next = parsed.nextCursor as? String
        if (!next.isNullOrBlank()) {
            list.appendChild(el("button", "text-button", text = Town.t("loadMore")) { button ->
                button.setAttribute("type", "button")
                button.onClick {
                    button.asDynamic().remove()
                    loadOwnPosts(list, shell, router, next)
                }
            })
        }
    }
}

private fun ownPostRow(post: dynamic, shell: HTMLElement, router: HistoryRouter): HTMLElement {
    val row = el("article", "own-post")
    val photo = el("img", "own-post-photo")
    photo.setAttribute("src", prefixServerOrigin(post.photoUrl as? String ?: ""))
    photo.setAttribute("alt", "")
    row.appendChild(photo)
    val copy = el("div", "own-post-copy")
    copy.appendChild(el("p", "own-post-message", text = post.message as? String ?: ""))
    val communityId = post.communityId as? String
    val communityName = post.communityName as? String
    if (!communityId.isNullOrBlank() && !communityName.isNullOrBlank()) {
        val line = el("div", "own-post-community")
        line.appendChild(
            communityChip(
                router,
                CommunityCard(communityId, communityName, post.communityEmoji as? String ?: "✨"),
            ),
        )
        if (post.communityOnly == true) {
            line.appendChild(el("span", "own-post-community-only", text = Town.t("communityOnly")))
        }
        copy.appendChild(line)
    }
    val musicName = post.musicName as? String
    if (!musicName.isNullOrBlank()) {
        copy.appendChild(el("p", "own-post-music", text = "🎵 $musicName"))
    }
    val stats = el("p", "post-stats")
    stats.appendChild(stat(countLabel(post.views), Town.t("views")))
    stats.appendChild(stat(countLabel(post.replyClicks), Town.t("repliesClicked")))
    stats.appendChild(stat(countLabel(post.shareClicks), Town.t("shareClicked")))
    copy.appendChild(stats)
    copy.appendChild(el("button", "tiny danger", text = Town.t("delete")) { button ->
        button.setAttribute("type", "button")
        button.onClick {
            confirmDialog(shell, Town.t("deletePostAsk"), Town.t("deletePostHint")) {
                val id = post.id as String
                http("DELETE", "/api/posts/$id") { code, body ->
                    if (code == 200) {
                        row.asDynamic().remove()
                        if (shell.asDynamic().querySelector(".own-post") == null) refresh(router)
                    } else {
                        toast(shell, errorText(body, code))
                    }
                }
            }
        }
    })
    row.appendChild(copy)
    return row
}

private fun stat(count: String, label: String): HTMLElement {
    val node = el("span", "stat")
    node.appendChild(el("b", text = count))
    node.asDynamic().append(" $label")
    return node
}

private fun countLabel(value: dynamic): String = num(value).toLong().toString()

private fun postCount(posts: dynamic): Int {
    if (posts == null || posts == undefined) return 0
    return posts.length as Int
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
