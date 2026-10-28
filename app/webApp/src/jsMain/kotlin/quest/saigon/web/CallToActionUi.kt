package quest.saigon.web

import quest.saigon.CallToAction
import quest.saigon.Limits
import web.html.HTMLElement
import web.window.window

internal fun composerCtaFields(host: HTMLElement): ComposerCta {
    val state = ComposerCta()
    val box = el("div", "cta-composer")
    val toggle = el("input")
    toggle.setAttribute("type", "checkbox")
    val row = el("label", "switch-row")
    row.appendChild(toggle)
    row.appendChild(el("span", "switch-ui"))
    row.appendChild(el("span", text = "📣 ${Town.t("addCallToAction")}"))
    val fields = el("div", "cta-fields")
    fields.asDynamic().hidden = true
    val title = field(Town.t("callToActionTitle"), "text", Town.t("callToActionTitleHint"))
    title.input.setAttribute("maxlength", Limits.CTA_TITLE_MAX.toString())
    title.input.setAttribute("autocomplete", "off")
    title.input.asDynamic().oninput = {
        state.title = title.value()
    }
    val text = field(Town.t("callToActionText"), "text", Town.t("callToActionTextHint"))
    text.input.setAttribute("maxlength", Limits.CTA_TEXT_MAX.toString())
    text.input.setAttribute("autocomplete", "off")
    text.input.asDynamic().oninput = {
        state.text = text.value()
    }
    val destLabel = el("span", "field-label", text = Town.t("callToActionDestination"))
    val destRow = el("div", "chip-row cta-destinations")
    val destHint = el("small", "hint")
    val url = field(Town.t("callToActionUrl"), "text", Town.t("callToActionUrlHint"))
    url.input.setAttribute("maxlength", Limits.CTA_URL_MAX.toString())
    url.input.setAttribute("autocomplete", "off")
    url.input.setAttribute("inputmode", "url")
    url.input.setAttribute("autocapitalize", "none")
    url.input.setAttribute("spellcheck", "false")
    fun rememberUrl() {
        state.url = url.value()
    }
    fun prefixUrl() {
        val cleaned = CallToAction.cleanUrl(url.value())
        if (cleaned != null) {
            url.input.asDynamic().value = cleaned
            state.url = cleaned
        } else {
            rememberUrl()
        }
    }
    url.input.asDynamic().oninput = { rememberUrl() }
    url.input.asDynamic().onblur = { prefixUrl() }
    val urlRoot = url.root
    val productRoot = productPicker(host) { productId -> state.productId = productId }
    fun paintDestination() {
        urlRoot.asDynamic().hidden = state.destination != CallToAction.WEBSITE
        productRoot.asDynamic().hidden = state.destination != CallToAction.PRODUCT
        destHint.textContent = when (state.destination) {
            CallToAction.MESSAGE -> Town.t("ctaSendMessageHint")
            CallToAction.ZALO -> Town.t("ctaZaloMessageHint")
            CallToAction.PROFILE -> Town.t("ctaShowProfileHint")
            CallToAction.PRODUCT -> Town.t("ctaShowProductHint")
            else -> ""
        }
        destHint.asDynamic().hidden = destHint.textContent == ""
        val chips = destRow.asDynamic().querySelectorAll(".chip")
        val count = chips.length as Int
        for (i in 0 until count) {
            val chip = chips[i]
            val dest = chip.getAttribute("data-destination") as? String
            chip.className = if (dest == state.destination) "chip selected" else "chip"
        }
    }
    listOf(
        CallToAction.MESSAGE to "ctaSendMessage",
        CallToAction.ZALO to "ctaZaloMessage",
        CallToAction.WEBSITE to "ctaOpenWebsite",
        CallToAction.PROFILE to "ctaShowProfile",
        CallToAction.PRODUCT to "ctaShowProduct",
    ).forEach { (dest, key) ->
        val chip = el("button", if (dest == state.destination) "chip selected" else "chip", text = Town.t(key))
        chip.setAttribute("type", "button")
        chip.setAttribute("data-destination", dest)
        chip.onClick {
            state.destination = dest
            paintDestination()
        }
        destRow.appendChild(chip)
    }
    toggle.asDynamic().onchange = {
        val on = toggle.asDynamic().checked == true
        fields.asDynamic().hidden = !on
        state.enabled = on
    }
    fields.appendChild(title.root)
    fields.appendChild(text.root)
    fields.appendChild(destLabel)
    fields.appendChild(destRow)
    fields.appendChild(destHint)
    fields.appendChild(urlRoot)
    fields.appendChild(productRoot)
    paintDestination()
    box.appendChild(row)
    box.appendChild(fields)
    state.root = box
    return state
}

internal class ComposerCta {
    var enabled: Boolean = false
    var title: String = ""
    var text: String = ""
    var destination: String = CallToAction.MESSAGE
    var url: String = ""
    var productId: String? = null
    lateinit var root: HTMLElement
}

internal fun ctaCard(post: PostCard, host: HTMLElement, router: HistoryRouter): HTMLElement {
    val card = el("div", "cta-card")
    val heading = post.ctaTitle.trim()
    if (heading.isNotBlank()) {
        card.appendChild(el("p", "cta-title", text = heading))
    } else {
        card.asDynamic().classList.add("no-title")
    }
    val action = el("button", "cta-action", text = post.ctaText)
    action.setAttribute("type", "button")
    action.onClick { activateCta(post, host, router) }
    card.appendChild(action)
    return card
}

private fun activateCta(post: PostCard, host: HTMLElement, router: HistoryRouter) {
    when (post.ctaDestination) {
        CallToAction.PROFILE -> openMiniProfile(
            host = host,
            name = post.authorName,
            photoUrl = post.authorPhotoUrl,
            location = post.authorLocation,
            intro = post.authorIntro,
            userId = post.authorId,
            router = router,
        )
        CallToAction.MESSAGE -> openDirectChat(host, router, post.authorId, post.authorName)
        CallToAction.ZALO -> {
            val url = CallToAction.httpUrl(post.authorZaloUrl)
            if (url == null) {
                toast(host, Town.t("replyFailed"))
            } else {
                window.asDynamic().open(url, "_blank")
            }
        }
        CallToAction.WEBSITE -> {
            val url = CallToAction.httpUrl(post.ctaUrl)
            if (url == null) {
                toast(host, Town.t("invalidCallToActionUrl"))
            } else {
                window.asDynamic().open(url, "_blank")
            }
        }
        CallToAction.PRODUCT -> {
            val productId = post.ctaUrl.trim()
            if (productId.isEmpty()) {
                toast(host, Town.t("unavailable"))
            } else {
                http("GET", "/api/products/${encodeQuery(productId)}") { status, body ->
                    if (status == 200) openProductDetails(host, router, parseJson(body))
                    else toast(host, Town.t("unavailable"))
                }
            }
        }
        else -> toast(host, Town.t("errorGeneric"))
    }
}
