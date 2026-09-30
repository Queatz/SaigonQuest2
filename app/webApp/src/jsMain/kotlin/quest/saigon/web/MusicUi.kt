package quest.saigon.web

import quest.saigon.Limits
import web.dom.document
import web.html.HTMLElement
import web.window.window

private var previewAudio: dynamic = null
private var previewUrl: String? = null
private var previewButton: HTMLElement? = null

internal fun stopMusicPreview() {
    try {
        previewAudio?.pause()
        previewAudio?.removeAttribute("src")
        previewAudio?.load()
    } catch (_: Throwable) {
    }
    previewButton?.let { paintPreviewButton(it, false) }
    previewAudio = null
    previewUrl = null
    previewButton = null
}

internal fun musicPicker(host: HTMLElement, onSelect: (String?) -> Unit): HTMLElement {
    val box = el("div")
    box.appendChild(el("h3", text = Town.t("selectMusic")))
    val search = el("input", "music-search")
    search.setAttribute("type", "search")
    search.setAttribute("placeholder", Town.t("searchMusic"))
    var selectedId: String? = null
    var searchTimer: dynamic = null
    val row = el("div", "music-row")
    val results = el("div", "music-results")
    val none = el("button", "music-choice selected", text = Town.t("noMusic"))
    none.setAttribute("type", "button")
    val studio = el("div", "studio")
    studio.asDynamic().hidden = true
    val ghost = el("button", "music-choice ghost")
    ghost.setAttribute("type", "button")
    ghost.setAttribute("aria-label", Town.t("createMusic"))
    ghost.textContent = "+"

    fun markSelected(id: String?) {
        selectedId = id
        none.asDynamic().classList.toggle("selected", id == null)
        val wraps = results.asDynamic().querySelectorAll(".music-choice-wrap")
        val count = wraps.length as Int
        for (i in 0 until count) {
            val wrap = wraps[i]
            val chip = wrap.querySelector("[data-id]")
            val match = chip != null && chip != undefined && chip.getAttribute("data-id") == id
            wrap.classList.toggle("selected", match)
            if (chip != null && chip != undefined) chip.classList.toggle("selected", match)
        }
        onSelect(id)
    }

    none.onClick { markSelected(null) }
    ghost.onClick {
        val opening = studio.asDynamic().hidden == true
        studio.asDynamic().hidden = !opening
        ghost.asDynamic().classList.toggle("open", opening)
        if (opening) {
            val input = studio.asDynamic().querySelector("input, textarea")
            if (input != null && input != undefined) input.focus()
        }
    }

    fun addTrack(track: dynamic, select: Boolean = false): HTMLElement {
        val id = track.id as String
        val name = (track.name as? String).orEmpty().ifBlank { Town.t("createMusic") }
        val wrap = el("div", "music-choice-wrap")
        val choice = el("button", "music-choice", text = "🎵 $name")
        choice.setAttribute("type", "button")
        choice.setAttribute("data-id", id)
        choice.onClick { markSelected(id) }
        wrap.appendChild(choice)
        val audioUrl = (track.audioUrl as? String)?.let(::prefixServerOrigin)
        if (track.status == "ready" && !audioUrl.isNullOrBlank()) {
            wrap.appendChild(el("button", "tiny music-play", text = "▶") { button ->
                button.setAttribute("type", "button")
                button.setAttribute("data-play-icon", "1")
                button.setAttribute("aria-label", Town.t("preview"))
                button.onClick { toggleMusicPreview(audioUrl, button) }
            })
        }
        results.appendChild(wrap)
        if (select) markSelected(id)
        return wrap
    }

    fun load(query: String) {
        val path = "/api/music?limit=20" + if (query.isBlank()) "" else "&q=${encodeQuery(query)}"
        http("GET", path) { status, body ->
            if (status != 200) return@http
            results.clear()
            val items = parseJson(body).music ?: return@http
            val length = items.length as Int
            for (i in 0 until length) addTrack(items[i])
            if (selectedId != null) markSelected(selectedId)
        }
    }

    search.asDynamic().oninput = {
        window.asDynamic().clearTimeout(searchTimer)
        searchTimer = window.asDynamic().setTimeout({
            load((search.asDynamic().value as? String).orEmpty().trim())
        }, 250)
    }

    studio.appendChild(musicPromptForm(host) { created ->
        studio.asDynamic().hidden = true
        ghost.asDynamic().classList.remove("open")
        val pending = el("div", "music-choice-wrap generating")
        pending.appendChild(el("button", "music-choice", text = "🎵 ${Town.t("generatingMusic")}").also {
            it.setAttribute("type", "button")
            it.setAttribute("disabled", "disabled")
        })
        pending.appendChild(el("span", "avatar-spinner"))
        results.asDynamic().insertBefore(pending, results.firstChild)
        pollMusic(created.id as String) { track ->
            pending.asDynamic().remove()
            if (track.status == "ready") {
                val wrap = addTrack(track, select = true)
                val url = (track.audioUrl as? String)?.let(::prefixServerOrigin)
                val play = wrap.asDynamic().querySelector(".music-play")
                if (!url.isNullOrBlank()) {
                    toggleMusicPreview(url, if (play == null || play == undefined) null else play as HTMLElement)
                }
            } else {
                toast(host, musicFailureText(track.error as? String))
            }
        }
    })

    row.appendChild(none)
    row.appendChild(results)
    row.appendChild(ghost)
    box.appendChild(search)
    box.appendChild(row)
    box.appendChild(studio)
    load("")
    return box
}

internal fun musicStudio(host: HTMLElement, router: HistoryRouter): HTMLElement {
    val box = el("div", "studio")
    val list = el("div", "music-list")
    box.appendChild(list)
    box.appendChild(musicPromptForm(host) { created ->
        pollMusic(created.id as String) { refresh(router) }
        refresh(router)
    })
    http("GET", "/api/me/music") { status, body ->
        if (status != 200) return@http
        val items = parseJson(body).music
        if (items == null || items.length == 0) {
            list.appendChild(el("p", "hint", text = Town.t("noMusicYet")))
            return@http
        }
        val length = items.length as Int
        for (i in 0 until length) list.appendChild(musicTile(items[i], router))
    }
    return box
}

internal fun musicPromptForm(host: HTMLElement, onCreated: (dynamic) -> Unit): HTMLElement {
    val box = el("div")
    val prompt = el("input")
    prompt.setAttribute("type", "text")
    prompt.setAttribute("maxlength", Limits.MUSIC_PROMPT_MAX.toString())
    prompt.setAttribute("placeholder", Town.t("musicPrompt"))
    var gender: String? = null
    var instrumental = false
    val male = el("button", "chip", text = Town.t("male"))
    male.setAttribute("type", "button")
    val female = el("button", "chip", text = Town.t("female"))
    female.setAttribute("type", "button")
    fun paintGender() {
        male.setAttribute("class", if (gender == "male") "chip selected" else "chip")
        female.setAttribute("class", if (gender == "female") "chip selected" else "chip")
        male.asDynamic().disabled = instrumental
        female.asDynamic().disabled = instrumental
    }
    male.onClick {
        gender = if (gender == "male") null else "male"
        paintGender()
    }
    female.onClick {
        gender = if (gender == "female") null else "female"
        paintGender()
    }
    val genderRow = el("div", "chip-row")
    genderRow.appendChild(male)
    genderRow.appendChild(female)
    val check = el("input")
    check.setAttribute("type", "checkbox")
    check.asDynamic().onchange = {
        instrumental = check.asDynamic().checked == true
        paintGender()
    }
    val checkRow = el("label", "check-row")
    checkRow.appendChild(check)
    checkRow.appendChild(el("span", text = Town.t("instrumental")))
    genderRow.appendChild(checkRow)
    val create = el("button", "secondary", text = Town.t("createMusic"))
    create.setAttribute("type", "button")
    create.setAttribute("style", "margin-top: 8px;")
    create.onClick {
        val text = (prompt.asDynamic().value as String).trim()
        if (text.isEmpty()) {
            toast(host, Town.t("invalidMusicPrompt"))
            return@onClick
        }
        create.setAttribute("disabled", "disabled")
        create.asDynamic().classList.add("busy")
        create.textContent = ""
        create.appendChild(el("span", "avatar-spinner"))
        val payload = js("({})")
        payload.prompt = text
        if (!instrumental && gender != null) payload.gender = gender
        payload.instrumental = instrumental
        http("POST", "/api/music", jsonStringify(payload)) { status, body ->
            create.removeAttribute("disabled")
            create.asDynamic().classList.remove("busy")
            create.textContent = Town.t("createMusic")
            if (status == 202 || status == 200) {
                prompt.asDynamic().value = ""
                onCreated(parseJson(body))
            } else {
                toast(host, errorText(body, status))
            }
        }
    }
    box.appendChild(prompt)
    box.appendChild(el("p", "hint", text = Town.t("musicPublicHint")))
    box.appendChild(genderRow)
    box.appendChild(create)
    return box
}

private fun musicTile(track: dynamic, router: HistoryRouter): HTMLElement {
    val tile = el("div", "music-tile")
    val status = track.status as? String
    val id = track.id as String
    var name = (track.name as? String).orEmpty()
    val title = el("strong", text = "🎵 $name")
    val renameInput = el("input")
    renameInput.setAttribute("type", "text")
    renameInput.setAttribute("maxlength", Limits.MUSIC_NAME_MAX.toString())
    renameInput.asDynamic().value = name
    renameInput.asDynamic().hidden = true
    tile.appendChild(title)
    tile.appendChild(renameInput)
    when (status) {
        "failed" -> tile.appendChild(el("span", "avatar-state", text = musicFailureText(track.error as? String)))
        "generating" -> {
            tile.appendChild(el("span", "avatar-spinner"))
            tile.appendChild(el("span", "avatar-state", text = Town.t("generatingMusic")))
            pollMusic(id) { refresh(router) }
        }
        else -> tile.appendChild(el("small", text = track.prompt as? String ?: ""))
    }
    val lyricsText = (track.lyrics as? String).orEmpty()
    val lyricsBox = el("pre", "music-lyrics", text = lyricsText)
    lyricsBox.asDynamic().hidden = true
    val actions = el("div", "avatar-actions")
    val audioUrl = (track.audioUrl as? String)?.let(::prefixServerOrigin)
    if (status == "ready" && !audioUrl.isNullOrBlank()) {
        actions.appendChild(el("button", "tiny", text = Town.t("preview")) { button ->
            button.setAttribute("type", "button")
            button.onClick { toggleMusicPreview(audioUrl, button) }
        })
    }
    if (status != "generating") {
        val renameBtn = el("button", "tiny", text = Town.t("rename"))
        renameBtn.setAttribute("type", "button")
        fun setEditing(on: Boolean) {
            title.asDynamic().hidden = on
            renameInput.asDynamic().hidden = !on
            renameBtn.textContent = Town.t(if (on) "save" else "rename")
        }
        renameBtn.onClick {
            if (renameInput.asDynamic().hidden == true) {
                renameInput.asDynamic().value = name
                setEditing(true)
                renameInput.asDynamic().focus()
                renameInput.asDynamic().select()
                return@onClick
            }
            val next = (renameInput.asDynamic().value as? String).orEmpty().trim()
            if (next.isEmpty()) {
                toast(shellOfMusic(tile), Town.t("invalidMusicName"))
                return@onClick
            }
            renameBtn.setAttribute("disabled", "disabled")
            val payload = js("({})")
            payload.name = next
            http("PATCH", "/api/music/$id", jsonStringify(payload)) { code, body ->
                renameBtn.removeAttribute("disabled")
                if (code == 200) {
                    val parsed = parseJson(body)
                    name = (parsed.name as? String).orEmpty().ifBlank { next }
                    title.textContent = "🎵 $name"
                    setEditing(false)
                } else {
                    toast(shellOfMusic(tile), errorText(body, code))
                }
            }
        }
        actions.appendChild(renameBtn)
    }
    if (lyricsText.isNotBlank()) {
        actions.appendChild(el("button", "tiny", text = Town.t("lyrics")) { button ->
            button.setAttribute("type", "button")
            button.onClick {
                val show = lyricsBox.asDynamic().hidden == true
                lyricsBox.asDynamic().hidden = !show
            }
        })
    }
    if (status == "failed") {
        actions.appendChild(el("button", "tiny", text = Town.t("retry")) { button ->
            button.setAttribute("type", "button")
            button.onClick {
                button.setAttribute("disabled", "disabled")
                http("POST", "/api/music/$id/retry") { code, body ->
                    if (code == 202 || code == 200) {
                        pollMusic(id) { refresh(router) }
                        refresh(router)
                    } else {
                        button.removeAttribute("disabled")
                        toast(shellOfMusic(tile), errorText(body, code))
                    }
                }
            }
        })
    }
    if (status != "generating") {
        actions.appendChild(el("button", "tiny danger", text = Town.t("delete")) { button ->
            button.setAttribute("type", "button")
            button.onClick {
                confirmDialog(shellOfMusic(tile), Town.t("dismissMusicAsk"), Town.t("dismissMusicHint")) {
                    http("DELETE", "/api/music/$id") { code, body ->
                        if (code == 200) refresh(router) else toast(shellOfMusic(tile), errorText(body, code))
                    }
                }
            }
        })
    }
    if (actions.childNodes.length > 0) tile.appendChild(actions)
    if (lyricsText.isNotBlank()) tile.appendChild(lyricsBox)
    return tile
}

internal fun pollMusic(id: String, onDone: (dynamic) -> Unit) {
    window.asDynamic().setTimeout({
        http("GET", "/api/music/$id") { status, body ->
            if (status != 200) return@http
            val parsed = parseJson(body)
            val statusName = parsed.status as? String
            if (statusName == "generating") pollMusic(id, onDone) else onDone(parsed)
        }
    }, 2000)
}

internal fun musicLine(post: PostCard): HTMLElement {
    val line = el("div", "music-line")
    line.appendChild(el("span", "music-name", text = "🎵 ${post.musicName}"))
    val muted = feedMusicMuted()
    val mute = el("button", "mute-btn", text = if (muted) "🔇" else "🔊")
    mute.setAttribute("type", "button")
    mute.setAttribute("aria-label", Town.t(if (muted) "unmute" else "mute"))
    mute.onClick { setFeedMusicMuted(!feedMusicMuted()) }
    line.appendChild(mute)
    return line
}

private var feedAudio: dynamic = null
private var feedAudioPostId: String? = null
private var feedMuted = false
private var feedMuteLoaded = false

internal fun feedMusicMuted(): Boolean {
    if (!feedMuteLoaded) {
        feedMuted = window.asDynamic().localStorage.getItem("sq_music_muted") == "1"
        feedMuteLoaded = true
    }
    return feedMuted
}

internal fun setFeedMusicMuted(muted: Boolean) {
    feedMuted = muted
    feedMuteLoaded = true
    window.asDynamic().localStorage.setItem("sq_music_muted", if (muted) "1" else "0")
    val audio = feedAudioNode()
    audio.muted = muted
    if (!muted) playFeedAudio(audio)
    refreshMuteButtons()
}

internal fun syncFeedAudio(post: PostCard) {
    val url = post.musicUrl
    if (url.isNullOrBlank()) {
        pauseFeedAudio()
        return
    }
    val audio = feedAudioNode()
    if (feedAudioPostId == post.id && audio.paused != true) return
    feedAudioPostId = post.id
    audio.muted = feedMusicMuted()
    audio.src = url
    playFeedAudio(audio)
}

internal fun stopFeedAudio() {
    pauseFeedAudio()
    try {
        feedAudio?.removeAttribute("src")
        feedAudio?.load()
    } catch (_: Throwable) {
    }
    feedAudioPostId = null
}

private fun pauseFeedAudio() {
    try {
        feedAudio?.pause()
    } catch (_: Throwable) {
    }
    feedAudioPostId = null
}

private fun feedAudioNode(): dynamic {
    if (feedAudio == null) {
        val node = document.createElement("audio")
        node.setAttribute("playsinline", "true")
        node.asDynamic().loop = true
        node.asDynamic().preload = "auto"
        node.asDynamic().muted = feedMusicMuted()
        feedAudio = node
    }
    return feedAudio
}

private fun playFeedAudio(audio: dynamic) {
    try {
        val playing = audio.play()
        if (playing != null && playing != undefined) {
            playing.catch { _: dynamic ->
                if (feedMusicMuted()) return@catch
                audio.muted = true
                feedMuted = true
                feedMuteLoaded = true
                try {
                    audio.play()
                } catch (_: Throwable) {
                }
                refreshMuteButtons()
            }
        }
    } catch (_: Throwable) {
    }
}

private fun refreshMuteButtons() {
    val buttons = document.asDynamic().querySelectorAll(".mute-btn")
    val count = buttons.length as Int
    val muted = feedMusicMuted()
    for (i in 0 until count) {
        val button = buttons[i]
        button.textContent = if (muted) "🔇" else "🔊"
        button.setAttribute("aria-label", Town.t(if (muted) "unmute" else "mute"))
    }
}

private fun toggleMusicPreview(url: String, button: HTMLElement? = null) {
    if (previewUrl == url) {
        stopMusicPreview()
        return
    }
    stopMusicPreview()
    pauseFeedAudio()
    val audio = document.createElement("audio")
    audio.setAttribute("playsinline", "true")
    audio.setAttribute("src", url)
    previewAudio = audio
    previewUrl = url
    previewButton = button
    button?.let { paintPreviewButton(it, true) }
    try {
        audio.asDynamic().play()
    } catch (_: Throwable) {
    }
    audio.asDynamic().onended = {
        button?.let { paintPreviewButton(it, false) }
        previewUrl = null
        previewAudio = null
        previewButton = null
    }
}

private fun paintPreviewButton(button: HTMLElement, playing: Boolean) {
    if (button.getAttribute("data-play-icon") == "1") {
        button.textContent = if (playing) "⏸" else "▶"
        button.setAttribute("aria-label", Town.t(if (playing) "pause" else "preview"))
    } else {
        button.textContent = Town.t(if (playing) "pause" else "preview")
    }
}

private fun musicFailureText(reason: String?): String {
    if (reason.isNullOrBlank() || reason == "generation_failed") return Town.t("musicFailed")
    return Town.t("musicFailedDetail", mapOf("reason" to reason))
}

private fun shellOfMusic(node: HTMLElement): HTMLElement {
    val shell = node.asDynamic().closest(".shell")
    return if (shell == null || shell == undefined) node else shell as HTMLElement
}
