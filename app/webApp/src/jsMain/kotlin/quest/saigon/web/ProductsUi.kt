package quest.saigon.web

import quest.saigon.Limits
import quest.saigon.ProductContactMethod
import web.html.HTMLElement
import web.window.window
import kotlin.js.unsafeCast

internal fun learnCatalog(host: HTMLElement, router: HistoryRouter): HTMLElement {
    val catalog = el("div", "learn-catalog")
    val search = el("input", "shop-search-input")
    search.setAttribute("type", "search")
    search.setAttribute("placeholder", Town.t("searchShopProducts"))
    search.setAttribute("aria-label", Town.t("search"))
    search.setAttribute("autocomplete", "off")
    val grid = el("div", "shop-product-grid learn-product-grid")
    catalog.appendChild(search)
    catalog.appendChild(grid)

    var cursor: String? = null
    var activeQuery = ""
    var loading = false
    var done = false
    var request = 0
    var debounceTimer: dynamic = null

    fun load(reset: Boolean = false) {
        if (reset) {
            request += 1
            loading = false
            done = false
            cursor = null
            activeQuery = (search.asDynamic().value as? String).orEmpty().trim()
            grid.clear()
        }
        if (loading || done) return
        loading = true
        val token = ++request
        val next = cursor
        val more = if (next.isNullOrBlank()) "" else "&cursor=${encodeQuery(next)}"
        http("GET", "/api/products/search?q=${encodeQuery(activeQuery)}&limit=50$more") { status, text ->
            if (token != request) return@http
            loading = false
            if (status != 200) {
                if (grid.childElementCount == 0) grid.appendChild(el("p", "hint", text = Town.t("unavailable")))
                done = true
                return@http
            }
            val parsed = parseJson(text)
            val products = parsed.products
            val count = productCount(products)
            if (count == 0 && grid.childElementCount == 0) {
                val key = if (activeQuery.isBlank()) "noProducts" else "noProductMatches"
                grid.appendChild(el("p", "hint", text = Town.t(key)))
            } else {
                for (i in 0 until count) grid.appendChild(discoverProductCard(products[i], host, router))
            }
            cursor = parsed.nextCursor as? String
            if (cursor.isNullOrBlank()) done = true
        }
    }

    grid.asDynamic().addEventListener("scroll", {
        val top = num(grid.asDynamic().scrollTop)
        val height = num(grid.asDynamic().clientHeight)
        val full = num(grid.asDynamic().scrollHeight)
        if (top + height > full - 80) load()
    })
    search.asDynamic().oninput = {
        if (debounceTimer != null && debounceTimer != undefined) window.asDynamic().clearTimeout(debounceTimer)
        debounceTimer = window.asDynamic().setTimeout({
            debounceTimer = null
            load(reset = true)
        }, 220)
    }
    load()
    return catalog
}

internal fun myProductsScreen(router: HistoryRouter): HTMLElement {
    val shell = el("section", "shell page-shell")
    shell.appendChild(pageHead(router, Town.t("yourLessons"), showProfile = false))
    val body = el("div", "page-body")
    body.appendChild(productEditorForm(shell, router))
    val list = el("div", "product-list")
    body.appendChild(list)
    shell.appendChild(body)
    loadMyProducts(list, shell, router, null)
    return shell
}

internal fun myOrdersScreen(router: HistoryRouter): HTMLElement {
    val shell = el("section", "shell page-shell")
    shell.appendChild(pageHead(router, Town.t("productOrders"), showProfile = false))
    val body = el("div", "page-body")
    val list = el("div", "product-order-list")
    body.appendChild(list)
    shell.appendChild(body)
    loadProductOrders(list, shell, router, null)
    return shell
}

internal fun productsPreview(shell: HTMLElement, router: HistoryRouter): HTMLElement {
    val section = el("section", "products-preview")
    val head = el("div", "section-head")
    head.appendChild(el("h3", text = Town.t("yourLessons")))
    val seeAll = el("button", "see-all", text = Town.t("seeAll"))
    seeAll.setAttribute("type", "button")
    seeAll.onClick { go(router, "/profile/products") }
    head.appendChild(seeAll)
    val orders = el("button", "see-all", text = Town.t("productOrders"))
    orders.setAttribute("type", "button")
    orders.onClick { go(router, "/profile/orders") }
    head.appendChild(orders)
    http("GET", "/api/me/orders?limit=1") { status, text ->
        if (status == 200 && orders.asDynamic().isConnected != false) {
            val openCount = num(parseJson(text).openCount).toLong()
            if (openCount > 0) {
                orders.textContent = Town.t("productOrdersCount", mapOf("count" to openCount.toString()))
            }
        }
    }
    section.appendChild(head)
    http("GET", "/api/me/products?limit=3") { status, text ->
        if (status != 200) return@http
        val parsed = parseJson(text)
        val products = parsed.products
        val count = productCount(products)
        if (count == 0) {
            section.appendChild(el("p", "hint", text = Town.t("noProducts")))
        } else {
            for (i in 0 until count) section.appendChild(productManageCard(products[i], shell, router))
        }
    }
    val create = el("button", "primary", text = Town.t("createProduct"))
    create.setAttribute("type", "button")
    val form = productEditorForm(shell, router)
    form.asDynamic().hidden = true
    create.onClick {
        val show = form.asDynamic().hidden == true
        form.asDynamic().hidden = !show
        create.textContent = Town.t(if (show) "cancel" else "createProduct")
        create.asDynamic().className = if (show) "text-button" else "primary"
    }
    section.appendChild(create)
    section.appendChild(form)
    return section
}

internal fun miniProfileProducts(userId: String, name: String, router: HistoryRouter, host: HTMLElement): HTMLElement {
    val section = el("section", "mini-products")
    section.asDynamic().hidden = true
    http("GET", "/api/users/${encodeQuery(userId)}/products?limit=3") { status, text ->
        if (status != 200 || section.asDynamic().isConnected == false) return@http
        val parsed = parseJson(text)
        val products = parsed.products
        val count = productCount(products)
        if (count == 0) return@http
        section.asDynamic().hidden = false
        section.appendChild(
            el(
                "h3",
                "profile-sheet-count",
                text = Town.t("lessonsOffered", mapOf("name" to name)),
            ),
        )
        val row = el("div", "mini-product-row")
        for (i in 0 until count) row.appendChild(productCard(products[i], host, router))
        section.appendChild(row)
    }
    return section
}

internal fun productPicker(host: HTMLElement, onPick: (String) -> Unit): HTMLElement {
    var selectedId: String? = null
    val button = el("button", "community-open", text = Town.t("chooseProduct"))
    button.setAttribute("type", "button")
    button.setAttribute("aria-haspopup", "dialog")
    button.onClick {
        openProductChooser(host, selectedId) { id, name ->
            selectedId = id
            button.textContent = name
            onPick(id)
        }
    }
    return button
}

private fun openProductChooser(host: HTMLElement, currentId: String?, onPick: (String, String) -> Unit) {
    if (host.asDynamic().querySelector(".product-chooser") != null) return
    val backdrop = el("div", "dialog-backdrop product-chooser")
    val card = el("div", "dialog community-dialog")
    card.setAttribute("role", "dialog")
    card.setAttribute("aria-modal", "true")
    card.appendChild(el("h2", text = Town.t("chooseProduct")))
    val search = el("input")
    search.setAttribute("type", "search")
    search.setAttribute("placeholder", Town.t("searchProducts"))
    val results = el("div", "community-list chooser-list")
    val products = mutableListOf<dynamic>()
    var nextCursor: String? = null
    var query = ""
    var loading = false
    lateinit var load: (String?) -> Unit

    fun close() {
        backdrop.asDynamic().remove()
    }

    fun choose(product: dynamic) {
        val id = product.id as? String ?: return
        val name = product.name as? String ?: ""
        close()
        onPick(id, name)
    }

    fun paint() {
        results.clear()
        val matches = products.filter { product ->
            val name = (product.name as? String).orEmpty()
            name.contains(query, ignoreCase = true)
        }
        if (matches.isEmpty()) {
            val key = if (products.isEmpty()) "noProducts" else "noProductMatches"
            results.appendChild(el("p", "hint", text = Town.t(key)))
        } else {
            matches.forEach { product ->
                val id = product.id as? String ?: return@forEach
                val name = product.name as? String ?: ""
                val row = el("button", if (id == currentId) "chooser-row selected" else "chooser-row")
                row.setAttribute("type", "button")
                val copy = el("div", "chooser-copy")
                copy.appendChild(el("span", text = name))
                copy.appendChild(el("small", text = productPrice(product.price)))
                row.appendChild(copy)
                row.onClick { choose(product) }
                results.appendChild(row)
            }
        }
        val cursor = nextCursor
        if (!cursor.isNullOrBlank()) {
            results.appendChild(el("button", "text-button", text = Town.t("loadMore")) { more ->
                more.setAttribute("type", "button")
                more.onClick {
                    more.setAttribute("disabled", "disabled")
                    load(cursor)
                }
            })
        }
    }

    load = load@{ cursor ->
        if (loading) return@load
        loading = true
        if (cursor.isNullOrBlank()) {
            products.clear()
            results.clear()
            results.appendChild(el("p", "hint", text = Town.t("loading")))
        }
        val suffix = if (cursor.isNullOrBlank()) "" else "&cursor=${encodeQuery(cursor)}"
        http("GET", "/api/me/products?limit=50$suffix") { status, body ->
            loading = false
            if (status != 200) {
                if (products.isEmpty()) {
                    results.clear()
                    results.appendChild(el("p", "hint", text = Town.t("unavailable")))
                } else {
                    paint()
                    toast(host, Town.t("unavailable"))
                }
                return@http
            }
            val parsed = parseJson(body)
            val items = parsed.products
            val count = productCount(items)
            for (i in 0 until count) products.add(items[i])
            nextCursor = parsed.nextCursor as? String
            paint()
        }
    }

    search.asDynamic().oninput = {
        query = (search.asDynamic().value as? String).orEmpty().trim()
        paint()
    }
    card.appendChild(search)
    card.appendChild(results)
    card.appendChild(el("button", "text-button", text = Town.t("close")) { button ->
        button.setAttribute("type", "button")
        button.onClick { close() }
    })
    backdrop.appendChild(card)
    backdrop.closeOnOutsideClick { close() }
    host.appendChild(backdrop)
    load(null)
}

private fun loadMyProducts(list: HTMLElement, shell: HTMLElement, router: HistoryRouter, cursor: String?) {
    val suffix = if (cursor.isNullOrBlank()) "" else "&cursor=${encodeQuery(cursor)}"
    http("GET", "/api/me/products?limit=20$suffix") { status, text ->
        if (status != 200) {
            if (list.childNodes.length == 0) list.appendChild(el("p", "hint", text = Town.t("unavailable")))
            return@http
        }
        val parsed = parseJson(text)
        val products = parsed.products
        val count = productCount(products)
        if (count == 0 && list.childNodes.length == 0) {
            list.appendChild(el("p", "hint", text = Town.t("noProducts")))
        }
        for (i in 0 until count) list.appendChild(productManageCard(products[i], shell, router))
        val next = parsed.nextCursor as? String
        if (!next.isNullOrBlank()) {
            list.appendChild(el("button", "text-button", text = Town.t("loadMore")) { button ->
                button.setAttribute("type", "button")
                button.onClick {
                    button.asDynamic().remove()
                    loadMyProducts(list, shell, router, next)
                }
            })
        }
    }
}

private fun productManageCard(product: dynamic, shell: HTMLElement, router: HistoryRouter): HTMLElement {
    val card = productCard(product, shell, router)
    card.asDynamic().classList.add("product-manage-card")
    val actions = el("div", "product-actions")
    actions.appendChild(el("button", "tiny", text = Town.t("edit")) { button ->
        button.setAttribute("type", "button")
        button.onClick { productEditorDialog(shell, router, product) }
    })
    actions.appendChild(el("button", "tiny danger", text = Town.t("delete")) { button ->
        button.setAttribute("type", "button")
        button.onClick {
            confirmDialog(shell, Town.t("deleteProductAsk"), Town.t("deleteProductHint")) {
                http("DELETE", "/api/products/${product.id}") { status, body ->
                    if (status == 200) {
                        card.asDynamic().remove()
                        if (shell.asDynamic().querySelector(".product-manage-card") == null) refresh(router)
                    } else toast(shell, errorText(body, status))
                }
            }
        }
    })
    card.appendChild(actions)
    return card
}

private fun productCard(product: dynamic, host: HTMLElement, router: HistoryRouter, vertical: Boolean = false): HTMLElement {
    val card = el("article", if (vertical) "product-card shop-product-card" else "product-card")
    val photos = product.photoUrls
    val photoUrl = if (photos != null && photos != undefined && (photos.length as Int) > 0) photos.unsafeCast<Array<String>>()[0] else null
    if (!photoUrl.isNullOrBlank()) {
        val image = el("img", "product-card-photo")
        image.setAttribute("src", prefixServerOrigin(photoUrl))
        image.setAttribute("alt", product.name as? String ?: "")
        card.appendChild(image)
    } else {
        card.appendChild(el("div", "product-card-photo product-photo-placeholder", text = "🛍️"))
    }
    val copy = el("div", "product-card-copy")
    copy.appendChild(el("strong", "product-name", text = product.name as? String ?: ""))
    copy.appendChild(el("span", "product-price", text = productPrice(product.price)))
    copy.appendChild(el("p", "product-description-preview", text = product.description as? String ?: ""))
    card.appendChild(copy)
    card.asDynamic().addEventListener("click", { event: dynamic ->
        val tag = (event.target.tagName as? String)?.lowercase()
        if (tag == "button") return@addEventListener
        openProductDetails(host, router, product)
    })
    return card
}

private fun discoverProductCard(product: dynamic, host: HTMLElement, router: HistoryRouter): HTMLElement {
    val photos = product.photoUrls
    val photoUrl = if (photos != null && photos != undefined && (photos.length as Int) > 0) {
        photos.unsafeCast<Array<String>>()[0]
    } else {
        null
    }
    val sellerName = (product.sellerName as? String).orEmpty()
    val action = el("button", "primary discover-card-action", text = Town.t("viewProduct"))
    action.setAttribute("type", "button")
    action.onClick { openProductDetails(host, router, product) }
    return discoverBannerCard(
        photoUrl = photoUrl,
        fallback = "🛍️",
        title = product.name as? String ?: "",
        gradientKey = (product.id as? String).orEmpty(),
        details = listOf(
            DiscoverCardDetail(productPrice(product.price), emphasis = true),
            DiscoverCardDetail(product.description as? String ?: ""),
        ),
        people = listOfNotNull(sellerName.takeIf { it.isNotBlank() }?.let {
            DiscoverCardPerson(it, product.sellerPhotoUrl as? String)
        }),
        action = action,
        onClick = { openProductDetails(host, router, product) },
    )
}

private fun productEditorForm(host: HTMLElement, router: HistoryRouter, initial: dynamic = null): HTMLElement {
    val form = el("form", "product-form")
    val editing = initial != null && initial != undefined
    val title = el("h3", text = Town.t(if (editing) "editProduct" else "createProduct"))
    form.appendChild(title)
    val name = field(Town.t("productName"), "text", "")
    name.input.setAttribute("maxlength", Limits.PRODUCT_NAME_MAX.toString())
    val price = field(Town.t("productPrice"), "text", "")
    price.input.setAttribute("inputmode", "numeric")
    val category = field(Town.t("productCategory"), "text", "")
    category.input.setAttribute("maxlength", Limits.PRODUCT_CATEGORY_MAX.toString())
    val description = area(Town.t("productDescription"), "", Limits.PRODUCT_DESCRIPTION_MAX)
    if (editing) {
        name.input.asDynamic().value = initial.name as? String ?: ""
        price.input.asDynamic().value = num(initial.price).toLong().toString()
        category.input.asDynamic().value = initial.category as? String ?: ""
        description.input.asDynamic().value = initial.description as? String ?: ""
    }
    form.appendChild(name.root)
    form.appendChild(price.root)
    form.appendChild(category.root)
    form.appendChild(description.root)
    val photoLabel = el("div", "field product-photo-input")
    photoLabel.appendChild(el("span", text = Town.t("productPhotos")))
    val initialPhotos = mutableListOf<ComposerPhoto>()
    if (editing) {
        val existing = initial.photoUrls
        if (existing != null && existing != undefined) {
            val existingCount = existing.length as Int
            for (index in 0 until existingCount) {
                val path = existing[index] as? String ?: continue
                initialPhotos += ComposerPhoto(null, prefixServerOrigin(path), path)
            }
        }
    }
    val photoPicker = PhotoPicker(
        crop916 = false,
        cropSquare = true,
        initialPhotos = initialPhotos,
        wrapperClass = "composer-photos product-photo-picker",
        uploadPrompt = Town.t("addPhoto"),
        onError = { key -> toast(host, Town.t(key)) },
    )
    val photos = photoPicker.photos
    photoLabel.appendChild(photoPicker.root)
    photoLabel.appendChild(el("small", text = Town.t("productPhotosHint")))
    form.appendChild(photoLabel)
    val submit = el("button", "primary", text = Town.t(if (editing) "save" else "createProduct"))
    submit.setAttribute("type", "submit")
    form.appendChild(submit)
    form.asDynamic().addEventListener("submit", { event: dynamic ->
        event.preventDefault()
        val cleanName = name.value().trim()
        val cleanPrice = price.value().trim()
        if (cleanName.isEmpty() || cleanPrice.isEmpty()) {
            toast(host, Town.t("invalidProduct"))
            return@addEventListener
        }
        submit.setAttribute("disabled", "disabled")
        submit.textContent = Town.t("saving")
        val blobs = mutableListOf<dynamic>()
        val photoOrder = js("[]")
        photos.forEach { photo ->
            val existingPath = photo.existingPath
            if (existingPath != null) {
                photoOrder.push(existingPath)
            } else if (photo.blob != null) {
                photoOrder.push("new:${blobs.size}")
                blobs.add(photo.blob)
            }
        }
        val payload = js("new FormData()")
        payload.append("name", cleanName)
        payload.append("price", cleanPrice)
        payload.append("category", category.value().trim())
        payload.append("description", description.value())
        payload.append("photoOrder", jsonStringify(photoOrder))
        blobs.forEachIndexed { index, blob -> payload.append("photos", blob, "product-$index.jpg") }
        val url = if (editing) "/api/products/${initial.id}" else "/api/products"
        http(if (editing) "PATCH" else "POST", url, form = payload) { status, body ->
            if (status == 200 || status == 201) {
                if (host.asDynamic().querySelector(".product-editor-backdrop") != null) {
                    host.asDynamic().querySelector(".product-editor-backdrop").remove()
                }
                toast(host, Town.t("productSaved"))
                refresh(router)
            } else {
                submit.removeAttribute("disabled")
                submit.textContent = Town.t(if (editing) "save" else "createProduct")
                toast(host, errorText(body, status))
            }
        }
    })
    return form
}

private fun productEditorDialog(host: HTMLElement, router: HistoryRouter, product: dynamic) {
    if (host.asDynamic().querySelector(".product-editor-backdrop") != null) return
    val backdrop = el("div", "dialog-backdrop product-editor-backdrop")
    val card = el("div", "dialog product-editor-dialog")
    card.appendChild(productEditorForm(host, router, product))
    val cancel = el("button", "text-button", text = Town.t("cancel"))
    cancel.setAttribute("type", "button")
    cancel.onClick { backdrop.asDynamic().remove() }
    card.appendChild(cancel)
    backdrop.appendChild(card)
    backdrop.closeOnOutsideClick { backdrop.asDynamic().remove() }
    host.appendChild(backdrop)
}

internal fun openProductDetails(host: HTMLElement, router: HistoryRouter, product: dynamic) {
    if (host.asDynamic().querySelector(".product-sheet") != null) return
    val name = product.name as? String ?: ""
    val productId = product.id as? String ?: ""
    val ownerId = product.userId as? String ?: ""
    val sellerName = product.sellerName as? String ?: ""
    val sellerPhotoUrl = product.sellerPhotoUrl as? String
    val chrome = SheetChrome("comment-sheet product-sheet", name)
    val photos = el("div", "product-detail-photos")
    val urls = product.photoUrls
    val length = if (urls == null || urls == undefined) 0 else urls.length as Int
    if (length == 0) photos.appendChild(el("div", "product-detail-placeholder", text = "🛍️"))
    for (i in 0 until length) {
        val image = el("img", "product-detail-photo")
        image.setAttribute("src", prefixServerOrigin(urls.unsafeCast<Array<String>>()[i]))
        image.setAttribute("alt", name)
        photos.appendChild(image)
    }
    val body = el("div", "product-sheet-body")
    body.appendChild(photos)
    val title = el("div", "product-detail-title-row")
    title.appendChild(el("h2", "product-detail-name", text = name))
    if (productId.isNotBlank()) {
        title.appendChild(copyLinkIconButton(host, "/product/$productId", "product-link-copy"))
    }
    body.appendChild(title)
    val category = product.category as? String
    if (!category.isNullOrBlank()) body.appendChild(el("p", "product-detail-category", text = category))
    body.appendChild(el("p", "product-detail-price", text = productPrice(product.price)))
    if (ownerId.isNotBlank()) {
        val seller = el("button", "attendee-profile product-sheet-seller")
        seller.setAttribute("type", "button")
        if (!sellerPhotoUrl.isNullOrBlank()) {
            val photo = el("img", "face")
            photo.setAttribute("src", prefixServerOrigin(sellerPhotoUrl))
            photo.setAttribute("alt", "")
            seller.appendChild(photo)
        } else {
            seller.appendChild(el("span", "face initial", text = sellerName.take(1).uppercase()))
        }
        val sellerCopy = el("span", "member-copy")
        sellerCopy.appendChild(el("strong", text = sellerName))
        val sellerLocation = product.sellerLocation as? String ?: ""
        if (sellerLocation.isNotBlank()) sellerCopy.appendChild(el("small", text = sellerLocation))
        seller.appendChild(sellerCopy)
        seller.onClick {
            openMiniProfile(
                host,
                sellerName,
                sellerPhotoUrl?.takeIf { it.isNotBlank() }?.let { prefixServerOrigin(it) },
                product.sellerLocation as? String ?: "",
                product.sellerIntro as? String ?: "",
                ownerId,
                router,
            )
        }
        body.appendChild(seller)
    }
    body.appendChild(linkedParagraph(product.description as? String ?: "", "product-detail-description"))
    chrome.sheet.appendChild(body)
    if (ownerId != Town.me?.id) {
        val dock = el("div", "product-buy-dock")
        dock.appendChild(el("button", "primary", text = Town.t("buy")) { button ->
            button.setAttribute("type", "button")
            button.onClick {
                if (Town.me == null) {
                    showAuthDialog(host, onSuccess = { openProductDetails(host, router, product) })
                } else {
                    productBuyDialog(host, product)
                }
            }
        })
        chrome.sheet.appendChild(dock)
    }
    chrome.open(host)
}

private fun productBuyDialog(host: HTMLElement, product: dynamic) {
    if (host.asDynamic().querySelector(".product-buy-backdrop") != null) return
    val backdrop = el("div", "dialog-backdrop product-buy-backdrop")
    val card = el("div", "dialog product-buy-dialog")
    card.setAttribute("role", "dialog")
    card.setAttribute("aria-modal", "true")
    card.appendChild(el("h2", text = Town.t("howToContactSeller")))
    val choices = el("div", "field product-contact-options")
    var method = ProductContactMethod.ZALO
    choices.appendChild(el("span", text = Town.t("contactMethod")))
    val selector = el("select", "product-contact-select")
    listOf(ProductContactMethod.ZALO to Town.t("zaloMessages"), ProductContactMethod.MESSAGES to Town.t("saigonMessages")).forEach { (value, label) ->
        val option = el("option", text = label)
        option.setAttribute("value", value)
        selector.appendChild(option)
    }
    selector.asDynamic().value = method
    selector.asDynamic().addEventListener("change", { method = selector.asDynamic().value as String })
    choices.appendChild(selector)
    card.appendChild(choices)
    val message = area(Town.t("optionalMessage"), "", Limits.CHAT_MESSAGE_MAX)
    card.appendChild(message.root)
    val actions = el("div", "dialog-actions")
    actions.appendChild(el("button", "text-button", text = Town.t("cancel")) { button ->
        button.setAttribute("type", "button")
        button.onClick { backdrop.asDynamic().remove() }
    })
    actions.appendChild(el("button", "primary", text = Town.t("confirmPurchase")) { button ->
        button.setAttribute("type", "button")
        button.onClick {
            button.setAttribute("disabled", "disabled")
            val payload = js("({})")
            payload.contactMethod = method
            payload.message = message.value()
            http("POST", "/api/products/${product.id}/orders", jsonStringify(payload)) { status, body ->
                if (status == 201 || status == 200) {
                    backdrop.asDynamic().remove()
                    toast(host, Town.t("purchaseConfirmed"))
                } else {
                    button.removeAttribute("disabled")
                    toast(host, errorText(body, status))
                }
            }
        }
    })
    card.appendChild(actions)
    backdrop.appendChild(card)
    backdrop.closeOnOutsideClick { backdrop.asDynamic().remove() }
    host.appendChild(backdrop)
}

private fun loadProductOrders(list: HTMLElement, host: HTMLElement, router: HistoryRouter, cursor: String?) {
    val suffix = if (cursor.isNullOrBlank()) "" else "&cursor=${encodeQuery(cursor)}"
    http("GET", "/api/me/orders?limit=20$suffix") { status, text ->
        if (status != 200) {
            if (list.childNodes.length == 0) list.appendChild(el("p", "hint", text = Town.t("unavailable")))
            return@http
        }
        val parsed = parseJson(text)
        val orders = parsed.orders
        val count = productCount(orders)
        if (count == 0 && list.childNodes.length == 0) list.appendChild(el("p", "hint", text = Town.t("noProductOrders")))
        for (i in 0 until count) list.appendChild(productOrderCard(orders[i], host, router))
        val next = parsed.nextCursor as? String
        if (!next.isNullOrBlank()) {
            list.appendChild(el("button", "text-button product-order-load-more", text = Town.t("loadMore")) { button ->
                button.setAttribute("type", "button")
                button.onClick {
                    button.asDynamic().remove()
                    loadProductOrders(list, host, router, next)
                }
            })
        }
    }
}

private fun productOrderCard(order: dynamic, host: HTMLElement, router: HistoryRouter): HTMLElement {
    val card = el("article", if (order.resolved == true) "product-order resolved" else "product-order")
    val productName = order.productName as? String ?: ""
    val productTitle = el("h3")
    productTitle.appendChild(el("button", "product-order-product", text = productName) { button ->
        button.setAttribute("type", "button")
        button.onClick {
            http("GET", "/api/products/${order.productId}") { status, body ->
                if (status == 200) openProductDetails(host, router, parseJson(body))
                else toast(host, Town.t("unavailable"))
            }
        }
    })
    card.appendChild(productTitle)
    card.appendChild(el("p", "product-order-price", text = productPrice(order.price)))
    val buyerName = order.buyerName as? String ?: ""
    val buyerId = order.buyerId as? String ?: ""
    val buyerPhotoUrl = order.buyerPhotoUrl as? String
    val buyer = el("button", "attendee-profile")
    buyer.setAttribute("type", "button")
    if (!buyerPhotoUrl.isNullOrBlank()) {
        val photo = el("img", "face")
        photo.setAttribute("src", prefixServerOrigin(buyerPhotoUrl))
        photo.setAttribute("alt", "")
        buyer.appendChild(photo)
    } else {
        buyer.appendChild(el("span", "face initial", text = buyerName.take(1).uppercase()))
    }
    val buyerCopy = el("span", "member-copy")
    buyerCopy.appendChild(el("strong", text = buyerName))
    val createdAt = num(order.createdAt).toLong()
    if (createdAt > 0) buyerCopy.appendChild(el("small", text = shortPostTime(createdAt)))
    buyer.appendChild(buyerCopy)
    buyer.onClick {
        openMiniProfile(
            host,
            buyerName,
            buyerPhotoUrl?.takeIf { it.isNotBlank() }?.let { prefixServerOrigin(it) },
            order.buyerLocation as? String ?: "",
            order.buyerIntro as? String ?: "",
            buyerId,
            router,
        )
    }
    card.appendChild(buyer)
    val message = order.message as? String
    if (!message.isNullOrBlank()) card.appendChild(linkedParagraph(message, "product-order-message"))
    card.appendChild(el("span", "product-order-status", text = Town.t(if (order.resolved == true) "resolved" else "unresolved")))
    val actions = el("div", "product-actions")
    val resolved = order.resolved == true
    if (!resolved) {
        val method = order.contactMethod as? String
        if (method == ProductContactMethod.ZALO) {
            val link = el("a", "primary", text = Town.t("contactOnZalo"))
            link.setAttribute("href", "https://zalo.me/${encodeQuery(order.buyerPhone as? String ?: "")}")
            link.setAttribute("target", "_blank")
            link.setAttribute("rel", "noopener noreferrer")
            actions.appendChild(link)
        } else {
            actions.appendChild(el("button", "primary", text = Town.t("contactInMessages")) { button ->
                button.setAttribute("type", "button")
                button.onClick {
                    openDirectChat(host, router, buyerId, buyerName)
                }
            })
        }
    }
    actions.appendChild(el("button", "secondary", text = Town.t(if (resolved) "markUnresolved" else "markResolved")) { button ->
        button.setAttribute("type", "button")
        button.onClick {
            val payload = js("({})")
            payload.resolved = !resolved
            http("PATCH", "/api/orders/${order.id}", jsonStringify(payload)) { status, body ->
                if (status == 200) {
                    val updatedCard = productOrderCard(parseJson(body), host, router)
                    card.asDynamic().replaceWith(updatedCard)
                    repositionProductOrderCard(updatedCard, unresolved = !resolved)
                } else toast(host, errorText(body, status))
            }
        }
    })
    card.appendChild(actions)
    return card
}

private fun repositionProductOrderCard(card: HTMLElement, unresolved: Boolean) {
    val list = card.parentElement ?: return
    val loadMore = list.asDynamic().querySelector(".product-order-load-more")
    if (unresolved) {
        val firstResolved = list.asDynamic().querySelector(".product-order.resolved")
        val before = if (firstResolved != null) firstResolved else loadMore
        if (before != null) list.asDynamic().insertBefore(card, before) else list.appendChild(card)
    } else {
        val openCards = list.asDynamic().querySelectorAll(".product-order:not(.resolved)")
        val lastOpen = if (openCards.length > 0) openCards.item(openCards.length - 1) else null
        val before = if (lastOpen != null) lastOpen.nextSibling else loadMore
        if (before != null) list.asDynamic().insertBefore(card, before) else list.appendChild(card)
    }
}

internal fun productPrice(value: dynamic): String = formatVnd(num(value).toLong())

private fun productCount(items: dynamic): Int = if (items == null || items == undefined) 0 else items.length as Int
