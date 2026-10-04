package quest.saigon.web

import web.html.HTMLElement

internal fun adminScreen(router: HistoryRouter): HTMLElement {
    if (!Town.meKnown) return el("section", "shell")
    val shell = el("section", "shell page-shell")
    shell.appendChild(pageHead(router, Town.t("admin"), showProfile = true))
    val body = el("div", "page-body")
    shell.appendChild(body)
    http("GET", "/api/admin/me") { status, _ ->
        if (shell.asDynamic().isConnected != true) return@http
        if (status != 200) {
            router.navigate("/", replace = true)
            return@http
        }
        Town.me = Town.me?.copy(admin = true)
        paintAdmin(body, shell, router)
    }
    return shell
}

private fun paintAdmin(body: HTMLElement, shell: HTMLElement, router: HistoryRouter) {
    val chips = el("div", "chip-row")
    val usersChip = el("button", "chip selected", text = Town.t("adminUsers"))
    usersChip.setAttribute("type", "button")
    val postsChip = el("button", "chip", text = Town.t("adminPosts"))
    postsChip.setAttribute("type", "button")
    val commentsChip = el("button", "chip", text = Town.t("adminComments"))
    commentsChip.setAttribute("type", "button")
    chips.appendChild(usersChip)
    chips.appendChild(postsChip)
    chips.appendChild(commentsChip)
    val pane = el("div", "admin-pane")
    body.appendChild(chips)
    body.appendChild(pane)

    fun select(tab: String) {
        usersChip.asDynamic().classList.toggle("selected", tab == "users")
        postsChip.asDynamic().classList.toggle("selected", tab == "posts")
        commentsChip.asDynamic().classList.toggle("selected", tab == "comments")
        pane.clear()
        when (tab) {
            "users" -> {
                val list = el("div", "admin-users")
                pane.appendChild(list)
                loadAdminUsers(list, shell, null)
            }
            "posts" -> {
                val list = el("div", "own-posts")
                pane.appendChild(list)
                loadAdminPosts(list, shell, router, null)
            }
            else -> {
                val list = el("div", "admin-comments")
                pane.appendChild(list)
                loadAdminComments(list, shell, null)
            }
        }
    }

    usersChip.onClick { select("users") }
    postsChip.onClick { select("posts") }
    commentsChip.onClick { select("comments") }
    select("users")
}

private fun loadAdminUsers(list: HTMLElement, shell: HTMLElement, cursor: String?) {
    val query = if (cursor.isNullOrBlank()) {
        "/api/admin/users?limit=20"
    } else {
        "/api/admin/users?limit=20&cursor=${encodeQuery(cursor)}"
    }
    http("GET", query) { status, text ->
        if (status != 200) {
            if (list.childNodes.length == 0) list.appendChild(el("p", "hint", text = Town.t("unavailable")))
            return@http
        }
        val parsed = parseJson(text)
        val users = parsed.users
        val length = nodeCount(users)
        if (length == 0 && list.childNodes.length == 0) {
            list.appendChild(el("p", "hint", text = Town.t("noUsers")))
            return@http
        }
        for (i in 0 until length) list.appendChild(adminUserRow(users[i], shell))
        val next = parsed.nextCursor as? String
        if (!next.isNullOrBlank()) {
            list.appendChild(el("button", "text-button", text = Town.t("loadMore")) { button ->
                button.setAttribute("type", "button")
                button.onClick {
                    button.asDynamic().remove()
                    loadAdminUsers(list, shell, next)
                }
            })
        }
    }
}

private fun adminUserRow(user: dynamic, shell: HTMLElement): HTMLElement {
    val row = el("article", "admin-user")
    val name = (user.name as? String).orEmpty()
    val photoUrl = user.photoUrl as? String
    row.appendChild(publicFace(name, photoUrl))
    val copy = el("div", "admin-user-copy")
    val title = el("div", "admin-user-title")
    title.appendChild(el("strong", text = name))
    val badge = el("span", "admin-paused", text = Town.t("paused"))
    badge.asDynamic().hidden = user.paused != true
    title.appendChild(badge)
    copy.appendChild(title)
    val intro = (user.intro as? String).orEmpty()
    if (intro.isNotBlank()) copy.appendChild(el("p", "admin-user-bio", text = intro))
    copy.appendChild(
        el("p", "admin-user-count", text = Town.t("postCount", mapOf("count" to num(user.postCount).toLong().toString()))),
    )
    row.appendChild(copy)
    val id = user.id as String
    val action = el("button", if (user.paused == true) "tiny" else "tiny danger", text = Town.t(if (user.paused == true) "unpauseUser" else "pauseUser"))
    action.setAttribute("type", "button")
    action.onClick {
        val paused = action.asDynamic().classList.contains("danger") != true
        val path = if (paused) "/api/admin/users/$id/unpause" else "/api/admin/users/$id/pause"
        action.setAttribute("disabled", "disabled")
        http("POST", path) { status, body ->
            action.removeAttribute("disabled")
            if (status != 200) {
                toast(shell, errorText(body, status))
                return@http
            }
            val nextPaused = parseJson(body).paused == true
            badge.asDynamic().hidden = !nextPaused
            action.textContent = Town.t(if (nextPaused) "unpauseUser" else "pauseUser")
            action.asDynamic().classList.toggle("danger", !nextPaused)
        }
    }
    row.appendChild(action)
    return row
}

private fun loadAdminPosts(list: HTMLElement, shell: HTMLElement, router: HistoryRouter, cursor: String?) {
    val query = if (cursor.isNullOrBlank()) {
        "/api/admin/posts?limit=20"
    } else {
        "/api/admin/posts?limit=20&cursor=${encodeQuery(cursor)}"
    }
    http("GET", query) { status, text ->
        if (status != 200) {
            if (list.childNodes.length == 0) list.appendChild(el("p", "hint", text = Town.t("unavailable")))
            return@http
        }
        val parsed = parseJson(text)
        val posts = parsed.posts
        val length = nodeCount(posts)
        if (length == 0 && list.childNodes.length == 0) {
            list.appendChild(el("p", "hint", text = Town.t("noPosts")))
            return@http
        }
        for (i in 0 until length) list.appendChild(adminPostRow(posts[i], shell, router))
        val next = parsed.nextCursor as? String
        if (!next.isNullOrBlank()) {
            list.appendChild(el("button", "text-button", text = Town.t("loadMore")) { button ->
                button.setAttribute("type", "button")
                button.onClick {
                    button.asDynamic().remove()
                    loadAdminPosts(list, shell, router, next)
                }
            })
        }
    }
}

private fun adminPostRow(post: dynamic, shell: HTMLElement, router: HistoryRouter): HTMLElement {
    val row = el("article", "own-post")
    val photo = el("img", "own-post-photo")
    photo.setAttribute("src", prefixServerOrigin(post.photoUrl as? String ?: ""))
    photo.setAttribute("alt", "")
    row.appendChild(photo)
    val copy = el("div", "own-post-copy")
    val author = el("div", "admin-post-author")
    val authorName = (post.authorName as? String).orEmpty()
    author.appendChild(publicFace(authorName, post.authorPhotoUrl as? String))
    author.appendChild(el("strong", text = authorName))
    copy.appendChild(author)
    copy.appendChild(el("p", "own-post-message", text = post.message as? String ?: ""))
    val musicName = post.musicName as? String
    if (!musicName.isNullOrBlank()) {
        copy.appendChild(el("p", "own-post-music", text = "🎵 $musicName"))
    }
    val stats = el("p", "post-stats")
    stats.appendChild(stat(num(post.views).toLong().toString(), Town.t("views")))
    stats.appendChild(stat(num(post.replyClicks).toLong().toString(), Town.t("repliesClicked")))
    stats.appendChild(stat(num(post.shareClicks).toLong().toString(), Town.t("shareClicked")))
    copy.appendChild(stats)
    copy.appendChild(el("button", "tiny danger", text = Town.t("delete")) { button ->
        button.setAttribute("type", "button")
        button.onClick {
            confirmDialog(shell, Town.t("deletePostAsk"), Town.t("deletePostHint")) {
                val id = post.id as String
                http("DELETE", "/api/admin/posts/$id") { code, body ->
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

private fun loadAdminComments(list: HTMLElement, shell: HTMLElement, cursor: String?) {
    val query = if (cursor.isNullOrBlank()) {
        "/api/admin/comments?limit=50"
    } else {
        "/api/admin/comments?limit=50&cursor=${encodeQuery(cursor)}"
    }
    http("GET", query) { status, text ->
        if (status != 200) {
            if (list.childNodes.length == 0) list.appendChild(el("p", "hint", text = Town.t("unavailable")))
            return@http
        }
        val parsed = parseJson(text)
        val comments = parsed.comments
        val length = nodeCount(comments)
        if (length == 0 && list.childNodes.length == 0) {
            list.appendChild(el("p", "hint", text = Town.t("noComments")))
            return@http
        }
        for (i in 0 until length) list.appendChild(adminCommentRow(comments[i], shell))
        val next = parsed.nextCursor as? String
        if (!next.isNullOrBlank()) {
            list.appendChild(el("button", "text-button", text = Town.t("loadMore")) { button ->
                button.setAttribute("type", "button")
                button.onClick {
                    button.asDynamic().remove()
                    loadAdminComments(list, shell, next)
                }
            })
        }
    }
}

private fun adminCommentRow(comment: dynamic, shell: HTMLElement): HTMLElement {
    val card = commentFrom(comment)
    val row = el("article", "admin-comment")
    row.appendChild(publicFace(card.authorName, card.authorPhotoUrl))
    val copy = el("div", "admin-user-copy")
    val title = el("div", "admin-user-title")
    title.appendChild(el("strong", text = card.authorName))
    title.appendChild(el("span", "comment-time", text = relativeTime(card.createdAt)))
    copy.appendChild(title)
    if (!card.replyToText.isNullOrBlank()) {
        copy.appendChild(el("div", "comment-quote", text = "@${card.replyToAuthorName}: ${card.replyToText}"))
    }
    copy.appendChild(el("p", "admin-comment-text", text = card.text))
    if (!card.postText.isNullOrBlank()) {
        val preview = el("div", "admin-comment-post")
        preview.appendChild(el("strong", text = Town.t("onPost")))
        preview.appendChild(el("span", text = card.postText))
        copy.appendChild(preview)
    }
    row.appendChild(copy)
    row.appendChild(el("button", "tiny danger", text = Town.t("delete")) { button ->
        button.setAttribute("type", "button")
        button.onClick {
            confirmDialog(shell, Town.t("deleteCommentAsk"), Town.t("deleteCommentHint")) {
                http("DELETE", "/api/admin/comments/${card.id}") { code, body ->
                    if (code == 200) row.asDynamic().remove()
                    else toast(shell, errorText(body, code))
                }
            }
        }
    })
    return row
}

private fun publicFace(name: String, photoUrl: String?): HTMLElement {
    return if (!photoUrl.isNullOrBlank()) {
        el("img", "face") { image ->
            image.setAttribute("src", prefixServerOrigin(photoUrl))
            image.setAttribute("alt", "")
        }
    } else {
        el("span", "face initial", text = name.take(1).uppercase().ifBlank { "?" })
    }
}

private fun stat(count: String, label: String): HTMLElement {
    val node = el("span", "stat")
    node.appendChild(el("b", text = count))
    node.asDynamic().append(" $label")
    return node
}

private fun nodeCount(nodes: dynamic): Int {
    if (nodes == null || nodes == undefined) return 0
    return nodes.length as Int
}
