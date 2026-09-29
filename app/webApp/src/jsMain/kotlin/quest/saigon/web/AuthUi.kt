package quest.saigon.web

import web.html.HTMLElement
import web.window.window

internal fun showAuthDialog(host: HTMLElement, onSuccess: () -> Unit, onClose: () -> Unit = {}) {
    if (host.asDynamic().querySelector(".dialog-backdrop") != null) return
    val backdrop = el("div", "dialog-backdrop")
    backdrop.appendChild(authDialog(onSuccess = {
        backdrop.asDynamic().remove()
        onSuccess()
    }, onClose = {
        backdrop.asDynamic().remove()
        onClose()
    }))
    host.appendChild(backdrop)
}

private fun authDialog(onSuccess: () -> Unit, onClose: () -> Unit): HTMLElement {
    var creating = true
    val card = el("form", "dialog")
    card.setAttribute("aria-label", Town.t("signIn"))
    val title = el("h2", text = Town.t("signIn"))
    val note = el("p", "hint", text = Town.t("needSignInToPost"))
    val error = el("p", "form-error")
    error.asDynamic().hidden = true
    val name = field(Town.t("name"), "text", "", "name")
    val phone = field(Town.t("phone"), "tel", Town.t("phoneHint"), "tel")
    val password = field(Town.t("password"), "password", Town.t("passwordHint"), "current-password")
    val confirm = field(Town.t("confirmPassword"), "password", "", "new-password")
    val submit = el("button", "primary", text = Town.t("signIn"))
    submit.setAttribute("type", "submit")
    val switch = el("button", "secondary", text = Town.t("switchToCreate"))
    switch.setAttribute("type", "button")

    fun showSignupFields(show: Boolean) {
        if (show) {
            if (name.root.parentNode == null) card.insertBefore(name.root, phone.root)
            if (confirm.root.parentNode == null) card.insertBefore(confirm.root, error)
        } else {
            name.root.asDynamic().remove()
            confirm.root.asDynamic().remove()
            name.input.asDynamic().value = ""
            confirm.input.asDynamic().value = ""
        }
    }

    fun paint() {
        title.textContent = Town.t(if (creating) "createAccount" else "signIn")
        card.setAttribute("aria-label", title.textContent ?: "")
        submit.textContent = title.textContent
        switch.textContent = Town.t(if (creating) "switchToSignIn" else "switchToCreate")
        showSignupFields(creating)
        password.input.setAttribute("autocomplete", if (creating) "new-password" else "current-password")
        error.asDynamic().hidden = true
    }

    switch.onClick {
        creating = !creating
        paint()
    }
    card.asDynamic().onsubmit = { event: dynamic ->
        event.preventDefault()
        if (creating && password.value() != confirm.value()) {
            error.textContent = Town.t("passwordMismatch")
            error.asDynamic().hidden = false
        } else {
            submit.setAttribute("disabled", "disabled")
            val payload = js("({})")
            payload.phone = phone.value()
            payload.password = password.value()
            if (creating) payload.name = name.value()
            val path = if (creating) "/api/auth/register" else "/api/auth/login"
            http("POST", path, jsonStringify(payload)) { status, body ->
                submit.removeAttribute("disabled")
                if (status == 200) {
                    Town.me = meFrom(parseJson(body))
                    Town.rememberLanguage(Town.me?.language ?: "system")
                    Town.applyLanguage()
                    recordUserVisit()
                    onSuccess()
                } else {
                    error.textContent = errorText(body, status)
                    error.asDynamic().hidden = false
                }
            }
        }
    }
    card.appendChild(title)
    card.appendChild(note)
    card.appendChild(phone.root)
    card.appendChild(password.root)
    card.appendChild(error)
    card.appendChild(submit)
    card.appendChild(switch)
    card.appendChild(el("button", "text-button", text = Town.t("close")) { button ->
        button.setAttribute("type", "button")
        button.onClick { onClose() }
    })
    window.asDynamic().setTimeout({ phone.input.asDynamic().focus() }, 30)
    paint()
    return card
}

internal class Field(val root: HTMLElement, val input: HTMLElement) {
    fun value(): String = input.asDynamic().value as String
}

internal fun field(label: String, type: String, hint: String, autocomplete: String? = null): Field {
    val root = el("label", "field")
    root.appendChild(el("span", text = label))
    val input = el("input")
    input.setAttribute("type", type)
    if (type == "tel") input.setAttribute("inputmode", "tel")
    val complete = autocomplete ?: if (type == "password") "current-password" else null
    if (complete != null) input.setAttribute("autocomplete", complete)
    root.appendChild(input)
    if (hint.isNotBlank()) root.appendChild(el("small", text = hint))
    return Field(root, input)
}

internal fun area(label: String, hint: String, maxLength: Int): Field {
    val root = el("label", "field")
    root.appendChild(el("span", text = label))
    val input = el("textarea")
    input.setAttribute("maxlength", maxLength.toString())
    root.appendChild(input)
    if (hint.isNotBlank()) root.appendChild(el("small", text = hint))
    return Field(root, input)
}
