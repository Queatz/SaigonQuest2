package quest.saigon.android.ui.shop

import android.content.ActivityNotFoundException
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import quest.saigon.AppRoute
import quest.saigon.Copy
import quest.saigon.Language
import quest.saigon.Limits
import quest.saigon.ProductContactMethod
import quest.saigon.android.media.ImagePreparer
import quest.saigon.android.theme.LocalSaigonTokens
import quest.saigon.android.theme.SaigonColors
import quest.saigon.android.ui.components.AndroidIntents
import quest.saigon.android.ui.components.LinkifiedText
import quest.saigon.android.ui.components.PhotoViewerDialog
import quest.saigon.android.ui.components.PickedPhotoItem
import quest.saigon.android.ui.components.SaigonCard
import quest.saigon.android.ui.components.SaigonConfirmDialog
import quest.saigon.android.ui.components.SaigonDialog
import quest.saigon.android.ui.components.SaigonIconButton
import quest.saigon.android.ui.components.SaigonOutlinedPillButton
import quest.saigon.android.ui.components.SaigonPageShell
import quest.saigon.android.ui.components.SaigonPhotoPicker
import quest.saigon.android.ui.components.SaigonPillChip
import quest.saigon.android.ui.components.SaigonPrimaryButton
import quest.saigon.android.ui.components.SaigonSpinner
import quest.saigon.android.ui.components.SaigonTextButton
import quest.saigon.android.ui.components.SaigonTextField
import quest.saigon.android.ui.components.SaigonTinyPill
import quest.saigon.android.ui.components.UserAvatar
import quest.saigon.android.ui.components.formatVnd
import quest.saigon.shared.TownSharedClient
import quest.saigon.shared.model.ProductDto

@Composable
fun ShopScreen(
    shared: TownSharedClient,
    onNavigate: (AppRoute) -> Unit,
    onRequireAuth: () -> Unit,
    onBack: () -> Unit,
) {
    val sessionState by shared.session.state.collectAsState()
    val shopState by shared.shop.state.collectAsState()
    val lang = sessionState.language
    val tokens = LocalSaigonTokens.current
    var query by remember { mutableStateOf("") }
    var showEditor by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        shared.shop.loadInitial()
    }

    SaigonPageShell(
        title = Copy.text(lang, "shop"),
        onBack = onBack,
        backContentDescription = Copy.text(lang, "back"),
        topActions = {
            SaigonIconButton(
                onClick = { if (sessionState.me == null) onRequireAuth() else showEditor = true },
                overlay = false,
            ) {
                Icon(Icons.Default.Add, contentDescription = Copy.text(lang, "createProduct"), modifier = Modifier.size(20.dp))
            }
        },
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
            SaigonTextField(
                value = query,
                onValueChange = {
                    query = it
                    shared.shop.loadInitial(it)
                },
                placeholder = Copy.text(lang, "searchShopProducts"),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )

            if (shopState.isLoading && shopState.products.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    SaigonSpinner()
                }
            } else if (shopState.products.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(Copy.text(lang, "noProducts"), color = tokens.muted)
                }
            } else {
                val rows = shopState.products.chunked(2)
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 28.dp),
                    modifier = Modifier.navigationBarsPadding(),
                ) {
                    items(rows) { rowProducts ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            rowProducts.forEach { product ->
                                SaigonCard(
                                    onClick = { onNavigate(AppRoute.Product(product.id)) },
                                    borderWidth = 2.dp,
                                    cornerRadius = 16.dp,
                                    contentPadding = PaddingValues(8.dp),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        val photo = product.photoUrls.firstOrNull()
                                        if (!photo.isNullOrBlank()) {
                                            AsyncImage(
                                                model = photo,
                                                contentDescription = product.name,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .aspectRatio(1f)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(SaigonColors.Card),
                                            )
                                        } else {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .aspectRatio(1f)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(SaigonColors.Card),
                                            ) {
                                                Icon(Icons.Default.ShoppingBag, contentDescription = null, tint = tokens.muted, modifier = Modifier.size(32.dp))
                                            }
                                        }
                                        Text(
                                            product.name,
                                            color = tokens.text,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 15.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            formatVnd(product.price),
                                            color = tokens.accent,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 14.sp,
                                        )
                                        if (product.description.isNotBlank()) {
                                            Text(
                                                product.description,
                                                color = tokens.muted,
                                                fontSize = 12.sp,
                                                lineHeight = 16.sp,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }
                            }
                            if (rowProducts.size == 1) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                    if (shopState.nextCursor != null) {
                        item {
                            SaigonTextButton(
                                text = Copy.text(lang, "loadMore"),
                                onClick = { shared.shop.loadMore() },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }

    if (showEditor) {
        ProductEditorDialog(
            existing = null,
            shared = shared,
            language = lang,
            onDismiss = { showEditor = false },
        )
    }
}

@Composable
fun ProductDetailScreen(
    productId: String,
    shared: TownSharedClient,
    onNavigate: (AppRoute) -> Unit,
    onRequireAuth: () -> Unit,
    onBack: () -> Unit,
) {
    val sessionState by shared.session.state.collectAsState()
    val shopState by shared.shop.state.collectAsState()
    val lang = sessionState.language
    val tokens = LocalSaigonTokens.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showOrderDialog by remember { mutableStateOf(false) }
    var showEditor by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var zoomUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(productId) {
        shared.shop.openProduct(productId)
    }

    val product = shopState.selectedProduct?.takeIf { it.id == productId }
    val isOwner = product != null && sessionState.me?.id == product.userId

    SaigonPageShell(
        title = product?.name ?: Copy.text(lang, "productAttachment"),
        onBack = onBack,
        backContentDescription = Copy.text(lang, "back"),
        topActions = {
            if (product != null) {
                SaigonIconButton(
                    onClick = { AndroidIntents.copyPathLink(context, "/product/${product.id}", lang) },
                    overlay = false,
                ) {
                    Icon(Icons.Default.Link, contentDescription = Copy.text(lang, "copyLink"), modifier = Modifier.size(18.dp))
                }
            }
            if (isOwner) {
                SaigonIconButton(onClick = { showEditor = true }, overlay = false) {
                    Icon(Icons.Default.Edit, contentDescription = Copy.text(lang, "edit"), modifier = Modifier.size(18.dp))
                }
                SaigonIconButton(
                    onClick = { confirmDelete = true },
                    overlay = false,
                ) {
                    Icon(Icons.Default.Delete, contentDescription = Copy.text(lang, "delete"), tint = tokens.danger, modifier = Modifier.size(18.dp))
                }
            }
        },
        bottomBar = {
            if (product != null && !isOwner) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(tokens.surface),
                ) {
                    HorizontalDivider(color = tokens.line, thickness = 1.dp)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        SaigonPrimaryButton(
                            text = Copy.text(lang, "buy"),
                            onClick = {
                                if (sessionState.me == null) onRequireAuth() else showOrderDialog = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
    ) {
        if (product == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                SaigonSpinner()
            }
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 12.dp),
            ) {
                if (product.photoUrls.isNotEmpty()) {
                    val pager = rememberPagerState(pageCount = { product.photoUrls.size })
                    HorizontalPager(
                        state = pager,
                        pageSpacing = 10.dp,
                        modifier = Modifier.fillMaxWidth().height(300.dp),
                    ) { idx ->
                        AsyncImage(
                            model = product.photoUrls[idx],
                            contentDescription = product.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(16.dp))
                                .background(SaigonColors.Card)
                                .clickable { zoomUrl = product.photoUrls[idx] },
                        )
                    }
                }
                Text(product.name, color = tokens.text, style = MaterialTheme.typography.headlineSmall)
                Text(
                    formatVnd(product.price),
                    color = tokens.accent,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                if (product.category.isNotBlank()) {
                    SaigonTinyPill(label = product.category)
                }
                if (product.description.isNotBlank()) {
                    LinkifiedText(text = product.description, color = tokens.text, style = MaterialTheme.typography.bodyLarge)
                }

                HorizontalDivider(color = tokens.line, thickness = 1.dp)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            if (product.userId.isNotBlank()) onNavigate(AppRoute.User(product.userId))
                        }
                        .padding(vertical = 4.dp),
                ) {
                    UserAvatar(photoUrl = product.sellerPhotoUrl, name = product.sellerName.orEmpty(), size = 44.dp, borderWidth = 0.dp)
                    Column {
                        Text(product.sellerName.orEmpty(), color = tokens.text, fontWeight = FontWeight.ExtraBold)
                        product.sellerLocation?.takeIf { it.isNotBlank() }?.let {
                            Text(it, fontSize = 12.sp, color = tokens.muted)
                        }
                    }
                }
            }
        }
    }

    if (showOrderDialog && product != null) {
        var method by remember { mutableStateOf(ProductContactMethod.ZALO) }
        var message by remember { mutableStateOf("") }
        SaigonDialog(onDismiss = { showOrderDialog = false }) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(Copy.text(lang, "howToContactSeller"), color = tokens.text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(end = 36.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SaigonPillChip(
                        selected = method == ProductContactMethod.ZALO,
                        onClick = { method = ProductContactMethod.ZALO },
                        label = Copy.text(lang, "zaloMessages"),
                    )
                    SaigonPillChip(
                        selected = method == ProductContactMethod.MESSAGES,
                        onClick = { method = ProductContactMethod.MESSAGES },
                        label = Copy.text(lang, "saigonMessages"),
                    )
                }
                SaigonTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = Copy.text(lang, "optionalMessage"),
                    singleLine = false,
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                shopState.error?.let { Text(it, color = tokens.danger, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                SaigonPrimaryButton(
                    text = Copy.text(lang, "confirmPurchase"),
                    enabled = !shopState.isSubmitting,
                    loading = shopState.isSubmitting,
                    onClick = {
                        scope.launch {
                            val order = shared.shop.placeOrder(product.id, method, message.trim())
                            if (order != null) {
                                showOrderDialog = false
                                val chatId = order.contactChatId
                                if (!chatId.isNullOrBlank()) {
                                    onNavigate(AppRoute.Chat(chatId))
                                } else {
                                    Toast.makeText(context, Copy.text(lang, "purchaseConfirmed"), Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                SaigonTextButton(text = Copy.text(lang, "cancel"), onClick = { showOrderDialog = false }, modifier = Modifier.fillMaxWidth())
            }
        }
    }

    if (confirmDelete && product != null) {
        SaigonConfirmDialog(
            title = Copy.text(lang, "deleteProductAsk"),
            hint = Copy.text(lang, "deleteProductHint"),
            confirmLabel = Copy.text(lang, "delete"),
            cancelLabel = Copy.text(lang, "cancel"),
            loading = shopState.isSubmitting,
            onConfirm = {
                scope.launch {
                    if (shared.shop.deleteProduct(product.id)) {
                        confirmDelete = false
                        onBack()
                    }
                }
            },
            onDismiss = { confirmDelete = false },
        )
    }

    if (showEditor && product != null) {
        ProductEditorDialog(
            existing = product,
            shared = shared,
            language = lang,
            onDismiss = { showEditor = false },
        )
    }

    zoomUrl?.let { PhotoViewerDialog(photoUrl = it, onDismiss = { zoomUrl = null }) }
}

@Composable
internal fun ProductEditorDialog(
    existing: ProductDto?,
    shared: TownSharedClient,
    language: Language,
    onSaved: () -> Unit = {},
    onDismiss: () -> Unit,
) {
    val shopState by shared.shop.state.collectAsState()
    val tokens = LocalSaigonTokens.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var name by remember(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var price by remember(existing?.id) { mutableStateOf(existing?.price?.toString().orEmpty()) }
    var category by remember(existing?.id) { mutableStateOf(existing?.category.orEmpty()) }
    var description by remember(existing?.id) { mutableStateOf(existing?.description.orEmpty()) }
    var photos by remember(existing?.id) {
        mutableStateOf(
            existing?.photoUrls.orEmpty().map { url ->
                val mediaIdx = url.indexOf("/media/")
                val path = if (mediaIdx >= 0) url.substring(mediaIdx) else url
                PickedPhotoItem(existingPath = path, existingUrl = url)
            },
        )
    }
    var activePhotoIdx by remember(existing?.id) { mutableIntStateOf(0) }
    var preparingPhotos by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }

    fun processPickedUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        scope.launch {
            preparingPhotos = true
            localError = null
            try {
                val initialCount = photos.size
                val next = photos.toMutableList()
                var failedCount = 0
                for (uri in uris) {
                    if (next.size >= Limits.PHOTOS_MAX) break
                    val prepared = ImagePreparer.prepareSquarePhoto(context, uri)
                    if (prepared != null && prepared.isNotEmpty()) {
                        next += PickedPhotoItem(bytes = prepared)
                    } else {
                        failedCount += 1
                    }
                }
                photos = next
                if (next.isNotEmpty()) activePhotoIdx = next.lastIndex
                if (failedCount > 0 && next.size == initialCount) {
                    localError = Copy.text(language, "invalidPhoto")
                }
            } finally {
                preparingPhotos = false
            }
        }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(Limits.PHOTOS_MAX),
    ) { uris: List<Uri> ->
        processPickedUris(uris)
    }
    val fallbackPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents(),
    ) { uris: List<Uri> ->
        processPickedUris(uris)
    }

    fun launchPhotoPicker() {
        try {
            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        } catch (_: ActivityNotFoundException) {
            runCatching { fallbackPicker.launch("image/*") }
        }
    }

    SaigonDialog(onDismiss = onDismiss) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.verticalScroll(rememberScrollState()),
        ) {
            Text(
                Copy.text(language, if (existing == null) "createProduct" else "editProduct"),
                color = tokens.text,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(end = 36.dp),
            )
            SaigonTextField(
                value = name,
                onValueChange = { if (it.length <= Limits.PRODUCT_NAME_MAX) name = it },
                label = Copy.text(language, "productName"),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            SaigonTextField(
                value = price,
                onValueChange = { price = it.filter(Char::isDigit) },
                label = Copy.text(language, "productPrice"),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            SaigonTextField(
                value = category,
                onValueChange = { if (it.length <= Limits.PRODUCT_CATEGORY_MAX) category = it },
                label = Copy.text(language, "productCategory"),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            SaigonTextField(
                value = description,
                onValueChange = { if (it.length <= Limits.PRODUCT_DESCRIPTION_MAX) description = it },
                label = Copy.text(language, "productDescription"),
                singleLine = false,
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                Text(
                    Copy.text(language, "productPhotos"),
                    color = tokens.text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                SaigonPhotoPicker(
                    photos = photos,
                    activeIndex = activePhotoIdx,
                    onActiveIndexChange = { activePhotoIdx = it },
                    onPhotosChange = { photos = it },
                    onAddPhotosClick = { launchPhotoPicker() },
                    language = language,
                    square = true,
                    rotatePrompts = false,
                    emptyPrompt = Copy.text(language, "addPhoto"),
                    isBusy = preparingPhotos,
                )
                Text(
                    Copy.text(language, "productPhotosHint"),
                    color = tokens.muted,
                    fontSize = 13.sp,
                )
            }
            (localError ?: shopState.error)?.let {
                Text(it, color = tokens.danger, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            SaigonPrimaryButton(
                text = Copy.text(language, if (existing == null) "createProduct" else "save"),
                enabled = name.isNotBlank() && price.isNotBlank() && !shopState.isSubmitting && !preparingPhotos,
                loading = shopState.isSubmitting,
                onClick = {
                    val newBytes = mutableListOf<ByteArray>()
                    val orderTokens = mutableListOf<String>()
                    photos.forEach { item ->
                        when {
                            item.existingPath != null -> orderTokens += item.existingPath
                            item.bytes != null -> {
                                orderTokens += "new:${newBytes.size}"
                                newBytes += item.bytes
                            }
                        }
                    }
                    val photoOrderJson = JsonArray(orderTokens.map(::JsonPrimitive)).toString()
                    scope.launch {
                        val saved = if (existing == null) {
                            shared.shop.createProduct(name.trim(), price.trim(), category.trim(), description.trim(), newBytes)
                        } else {
                            shared.shop.updateProduct(existing.id, name.trim(), price.trim(), category.trim(), description.trim(), photoOrderJson, newBytes)
                        }
                        if (saved != null) {
                            onSaved()
                            onDismiss()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            SaigonTextButton(text = Copy.text(language, "cancel"), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
    }
}
