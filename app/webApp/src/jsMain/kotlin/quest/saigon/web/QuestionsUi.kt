package quest.saigon.web

import quest.saigon.Limits
import web.html.HTMLElement

internal fun composerQuestionFields(): ComposerQuestion {
    val state = ComposerQuestion()
    val box = el("div", "question-composer")
    val toggle = el("input")
    toggle.setAttribute("type", "checkbox")
    val row = el("label", "switch-row")
    row.appendChild(toggle)
    row.appendChild(el("span", "switch-ui"))
    row.appendChild(el("span", text = "❓ ${Town.t("askQuestion")}"))
    val fields = el("div", "question-fields")
    fields.asDynamic().hidden = true
    val title = field(Town.t("questionTitle"), "text", Town.t("questionTitleHint"))
    title.input.setAttribute("maxlength", Limits.QUESTION_TITLE_MAX.toString())
    title.input.setAttribute("autocomplete", "off")
    title.input.asDynamic().oninput = {
        state.title = title.value()
    }
    fields.appendChild(title.root)
    fields.appendChild(el("p", "hint", text = Town.t("questionPrivateHint")))
    toggle.asDynamic().onchange = {
        val on = toggle.asDynamic().checked == true
        fields.asDynamic().hidden = !on
        state.enabled = on
    }
    box.appendChild(row)
    box.appendChild(fields)
    state.root = box
    return state
}

internal class ComposerQuestion {
    var enabled: Boolean = false
    var title: String = ""
    lateinit var root: HTMLElement
}

internal fun questionCard(
    post: PostCard,
    host: HTMLElement,
    router: HistoryRouter,
    onUpdate: (PostCard) -> Unit,
): HTMLElement {
    val card = el("div", "question-card")
    fun paint(current: PostCard) {
        card.clear()
        val heading = current.questionTitle.trim()
        if (heading.isNotBlank()) {
            card.appendChild(el("p", "question-title", text = heading))
        }
        val mine = Town.me?.id == current.authorId
        if (mine) {
            card.appendChild(answerField(disabled = true))
            val responses = el(
                "button",
                "question-responses",
                text = Town.t("questionResponses", mapOf("count" to current.answerCount.toString())),
            )
            responses.setAttribute("type", "button")
            responses.onClick { openQuestionAnswers(current, host, router) }
            card.appendChild(responses)
        } else if (current.answered) {
            card.appendChild(
                el(
                    "p",
                    "question-sent",
                    text = Town.t("questionSent", mapOf("name" to current.authorName)),
                ),
            )
        } else {
            val input = answerField()
            val send = el(
                "button",
                "question-send",
                text = Town.t("sendTo", mapOf("name" to current.authorName)),
            )
            send.setAttribute("type", "button")
            fun submit() {
                val text = (input.asDynamic().value as? String).orEmpty().trim()
                if (text.isEmpty()) return
                if (Town.me == null) {
                    showAuthDialog(host, onSuccess = { submit() })
                    return
                }
                send.setAttribute("disabled", "disabled")
                val payload = js("({})")
                payload.text = text
                http("POST", "/api/posts/${current.id}/answers", jsonStringify(payload)) { status, body ->
                    send.removeAttribute("disabled")
                    if (status != 200) {
                        toast(host, errorText(body, status))
                        return@http
                    }
                    val next = postFrom(parseJson(body))
                    onUpdate(next)
                    paint(next)
                }
            }
            send.onClick { submit() }
            input.asDynamic().addEventListener("keydown", { event: dynamic ->
                if (event.key == "Enter") {
                    event.preventDefault()
                    submit()
                }
            })
            input.asDynamic().addEventListener("focus", {
                if (Town.me == null) {
                    input.asDynamic().blur()
                    showAuthDialog(host, onSuccess = { input.asDynamic().focus() })
                }
            })
            card.appendChild(input)
            card.appendChild(send)
        }
    }
    paint(post)
    return card
}

private fun openQuestionAnswers(post: PostCard, host: HTMLElement, router: HistoryRouter) {
    if (host.asDynamic().querySelector(".question-sheet") != null) return
    val chrome = SheetChrome("comment-sheet question-sheet", Town.t("questionAnswers"))
    val body = el("div", "question-sheet-body")
    body.appendChild(el("p", "hint", text = Town.t("questionPrivateHint")))
    val list = el("div", "member-list")
    body.appendChild(list)
    chrome.sheet.appendChild(body)
    chrome.open(host)
    http("GET", "/api/posts/${post.id}/answers") { status, text ->
        if (status != 200) {
            list.appendChild(el("p", "hint", text = errorText(text, status)))
            return@http
        }
        val answers = parseJson(text).answers
        val length = answers?.length as? Int ?: 0
        if (length == 0) {
            list.appendChild(el("p", "hint", text = Town.t("noAnswers")))
            return@http
        }
        for (i in 0 until length) list.appendChild(answerRow(answers[i], host, router))
    }
}

private fun answerRow(node: dynamic, host: HTMLElement, router: HistoryRouter): HTMLElement {
    val userId = (node.userId as? String).orEmpty()
    val name = (node.name as? String).orEmpty()
    val photoUrl = (node.photoUrl as? String)?.let(::prefixServerOrigin)
    val location = (node.location as? String).orEmpty()
    val intro = (node.intro as? String).orEmpty()
    val text = (node.text as? String).orEmpty()
    val row = el("div", "answer-row")
    val profile = el("button", "attendee-profile")
    profile.setAttribute("type", "button")
    if (!photoUrl.isNullOrBlank()) {
        val face = el("img", "face")
        face.setAttribute("alt", "")
        face.setAttribute("src", photoUrl)
        profile.appendChild(face)
    } else {
        profile.appendChild(el("span", "face initial", text = name.take(1).uppercase()))
    }
    val copy = el("span", "answer-copy")
    copy.appendChild(el("strong", text = name))
    if (text.isNotBlank()) copy.appendChild(el("p", "answer-text", text = text))
    profile.appendChild(copy)
    profile.onClick {
        openMiniProfile(host, name, photoUrl, location, intro, userId, router)
    }
    row.appendChild(profile)
    if (userId.isNotBlank() && userId != Town.me?.id) {
        val message = el("button", "primary", text = Town.t("message"))
        message.setAttribute("type", "button")
        message.onClick { openDirectChat(host, router, userId, name) }
        row.appendChild(message)
    }
    return row
}

private fun answerField(disabled: Boolean = false): HTMLElement {
    val input = el("input", "question-field")
    input.setAttribute("type", "text")
    input.setAttribute("maxlength", Limits.QUESTION_ANSWER_MAX.toString())
    input.setAttribute("placeholder", Town.t("questionAnswerHint"))
    if (disabled) input.setAttribute("disabled", "disabled")
    return input
}
