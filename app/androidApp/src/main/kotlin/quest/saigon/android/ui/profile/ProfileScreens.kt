package quest.saigon.android.ui.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import quest.saigon.AppRoute
import quest.saigon.Copy
import quest.saigon.Limits
import quest.saigon.android.media.AudioPreviewPlayer
import quest.saigon.android.media.ImagePreparer
import quest.saigon.android.theme.LocalSaigonTokens
import quest.saigon.android.theme.SaigonBrushes
import quest.saigon.android.theme.SaigonColors
import quest.saigon.android.ui.components.ActivityCard
import quest.saigon.android.ui.components.ActivityEditorDialog
import quest.saigon.android.ui.components.AndroidIntents
import quest.saigon.android.ui.components.LinkifiedText
import quest.saigon.android.ui.components.LocationInputRow
import quest.saigon.android.ui.components.LocationPickerDialog
import quest.saigon.android.ui.components.PhotoViewerDialog
import quest.saigon.android.ui.components.PostGridRows
import quest.saigon.android.ui.components.SaigonCard
import quest.saigon.android.ui.components.SaigonConfirmDialog
import quest.saigon.android.ui.components.SaigonDialog
import quest.saigon.android.ui.components.SaigonIconButton
import quest.saigon.android.ui.components.SaigonOutlinedPillButton
import quest.saigon.android.ui.components.SaigonPageShell
import quest.saigon.android.ui.components.SaigonPillChip
import quest.saigon.android.ui.components.SaigonPrimaryButton
import quest.saigon.android.ui.components.SaigonSecondaryButton
import quest.saigon.android.ui.components.SaigonSpinner
import quest.saigon.android.ui.components.SaigonTextButton
import quest.saigon.android.ui.components.SaigonTextField
import quest.saigon.android.ui.components.SaigonTinyPill
import quest.saigon.android.ui.components.UserAvatar
import quest.saigon.android.ui.components.formatVnd
import quest.saigon.android.ui.components.openLocationDestination
import quest.saigon.android.ui.components.rememberDirectMessageOpener
import quest.saigon.android.ui.shop.ProductEditorDialog
import quest.saigon.shared.TownSharedClient
import quest.saigon.shared.model.ActivityDto
import quest.saigon.shared.model.FeatureSuggestionDto
import quest.saigon.shared.model.MusicDto
import quest.saigon.shared.model.OwnedPostDto
import quest.saigon.shared.model.PlaceSuggestionDto
import quest.saigon.shared.model.ProductDto
import quest.saigon.shared.model.UpdateMeRequest

@Composable
fun ProfileScreen(
    shared: TownSharedClient,
    onNavigate: (AppRoute) -> Unit,
    onBack: () -> Unit,
) {
    val sessionState by shared.session.state.collectAsState()
    val profileState by shared.profile.state.collectAsState()
    val composerState by shared.composer.state.collectAsState()
    val me = sessionState.me
    val lang = sessionState.language
    val tokens = LocalSaigonTokens.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var name by remember(me?.id, me?.name) { mutableStateOf(me?.name.orEmpty()) }
    var phone by remember(me?.id, me?.phone) { mutableStateOf(me?.phone.orEmpty()) }
    var location by remember(me?.id, me?.location) { mutableStateOf(me?.location.orEmpty()) }
    var selectedPlace by remember(me?.id, me?.location, me?.placeId, me?.locationId, me?.lat, me?.lng) {
        mutableStateOf(
            if (me != null && me.location.isNotBlank() && me.lat != null && me.lng != null) {
                PlaceSuggestionDto(
                    id = me.locationId,
                    name = me.location,
                    placeId = me.placeId,
                    lat = me.lat,
                    lng = me.lng,
                    custom = me.locationId != null,
                )
            } else {
                null
            },
        )
    }
    var intro by remember(me?.id, me?.intro) { mutableStateOf(me?.intro.orEmpty()) }
    var savedMessage by remember { mutableStateOf<String?>(null) }
    var showLocationDialog by remember { mutableStateOf(false) }

    LaunchedEffect(me?.id) {
        shared.composer.searchPlaces("")
        if (me != null) {
            shared.profile.loadMyOrders()
        }
    }

    val avatarPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            ImagePreparer.prepareAvatarPhoto(context, uri)?.let { bytes ->
                shared.session.uploadProfilePhoto(bytes)
            }
        }
    }

    SaigonPageShell(
        title = Copy.text(lang, "profile"),
        onBack = onBack,
        backContentDescription = Copy.text(lang, "back"),
        topActions = {
            if (me != null) {
                SaigonIconButton(
                    onClick = { AndroidIntents.copyPathLink(context, "/u/${me.id}", lang) },
                    overlay = false,
                ) {
                    Icon(Icons.Default.Link, contentDescription = Copy.text(lang, "copyLink"), modifier = Modifier.size(20.dp))
                }
            }
        },
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 12.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                        ),
                ) {
                    UserAvatar(
                        photoUrl = me?.photoUrl,
                        name = me?.name.orEmpty(),
                        size = 96.dp,
                        borderColor = tokens.line,
                        borderWidth = 2.dp,
                    )
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = 2.dp, y = 2.dp)
                            .size(26.dp)
                            .shadow(3.dp, CircleShape)
                            .clip(CircleShape)
                            .background(SaigonBrushes.primaryGradient),
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = Copy.text(lang, "changePhoto"), tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
                Text(me?.name.orEmpty(), color = tokens.text, style = MaterialTheme.typography.titleLarge)
            }

            val hasCoordinates = selectedPlace?.lat != null &&
                selectedPlace?.lng != null &&
                (selectedPlace?.name ?: location).isNotBlank()
            val hasEdits = me != null && (
                name.trim() != me.name.trim() ||
                    phone.trim() != me.phone.trim() ||
                    location.trim() != me.location.trim() ||
                    intro.trim() != me.intro.trim() ||
                    selectedPlace?.placeId != me.placeId ||
                    selectedPlace?.id != me.locationId ||
                    selectedPlace?.lat != me.lat ||
                    selectedPlace?.lng != me.lng
                )

            SaigonTextField(
                value = name,
                onValueChange = { if (it.length <= Limits.NAME_MAX) { name = it; savedMessage = null; shared.session.clearError() } },
                label = Copy.text(lang, "yourName"),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            SaigonTextField(
                value = phone,
                onValueChange = { phone = it; savedMessage = null; shared.session.clearError() },
                label = Copy.text(lang, "phone"),
                placeholder = Copy.text(lang, "phoneHint"),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            LocationInputRow(
                label = Copy.text(lang, "yourLocation"),
                value = location,
                onValueChange = { typed ->
                    location = typed
                    if (selectedPlace != null && !typed.trim().equals(selectedPlace?.name?.trim(), ignoreCase = false)) {
                        selectedPlace = null
                    }
                    savedMessage = null
                    shared.session.clearError()
                },
                hasCoordinates = hasCoordinates,
                language = lang,
                onOpenMapPicker = { showLocationDialog = true },
                modifier = Modifier.fillMaxWidth(),
            )
            SaigonTextField(
                value = intro,
                onValueChange = { if (it.length <= Limits.INTRO_MAX) { intro = it; savedMessage = null; shared.session.clearError() } },
                label = Copy.text(lang, "introduceYourself"),
                placeholder = Copy.text(lang, "introduceYourselfHint"),
                singleLine = false,
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )

            if (hasEdits) {
                SaigonPrimaryButton(
                    text = Copy.text(lang, "save"),
                    enabled = !sessionState.isSubmitting && name.isNotBlank(),
                    loading = sessionState.isSubmitting,
                    onClick = {
                        savedMessage = null
                        scope.launch {
                            val ok = shared.session.updateProfile(
                                UpdateMeRequest(
                                    name = name.trim(),
                                    phone = phone.trim().takeIf { it != me.phone.trim() },
                                    location = location.trim(),
                                    placeId = selectedPlace?.placeId,
                                    locationId = selectedPlace?.id,
                                    lat = selectedPlace?.lat,
                                    lng = selectedPlace?.lng,
                                    intro = intro.trim(),
                                ),
                            )
                            if (ok) savedMessage = Copy.text(lang, "saved")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Text(Copy.text(lang, "language"), color = tokens.text, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                listOf(
                    "system" to Copy.text(lang, "languageSystem"),
                    "en" to Copy.text(lang, "languageEn"),
                    "vi" to Copy.text(lang, "languageVi"),
                ).forEach { (code, label) ->
                    SaigonPillChip(
                        selected = sessionState.languagePreference == code,
                        onClick = { scope.launch { shared.session.setLanguagePreference(code) } },
                        label = label,
                    )
                }
            }

            savedMessage?.let { Text(it, color = tokens.accent, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
            sessionState.error?.let { Text(it, color = tokens.danger, fontWeight = FontWeight.Bold, fontSize = 14.sp) }

            HorizontalDivider(color = tokens.line, thickness = 1.dp)

            val ordersLabel = if (profileState.openOrdersCount > 0) {
                Copy.text(lang, "productOrdersCount", mapOf("count" to profileState.openOrdersCount.toString()))
            } else {
                Copy.text(lang, "productOrders")
            }
            val links = buildList {
                add(Copy.text(lang, "yourPosts") to AppRoute.MyPosts)
                add(Copy.text(lang, "yourCommunities") to AppRoute.MyCommunities)
                add(Copy.text(lang, "yourMusic") to AppRoute.MyMusic)
                add(Copy.text(lang, "yourLessons") to AppRoute.MyProducts)
                add(Copy.text(lang, "myActivities") to AppRoute.MyActivities)
                add(ordersLabel to AppRoute.MyOrders)
                add(Copy.text(lang, "allFeatures") to AppRoute.FeatureSuggestions)
                add(Copy.text(lang, "stats") to AppRoute.Stats)
                if (me?.admin == true) add(Copy.text(lang, "admin") to AppRoute.Admin)
            }

            links.forEach { (label, route) ->
                SaigonCard(
                    onClick = { onNavigate(route) },
                    borderWidth = 2.dp,
                    cornerRadius = 16.dp,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(label, color = tokens.text, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = tokens.muted)
                    }
                }
            }

            SaigonTextButton(
                text = Copy.text(lang, "signOut"),
                danger = true,
                onClick = {
                    scope.launch {
                        shared.session.logout()
                        shared.onSignedOut()
                        onBack()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (showLocationDialog) {
        LocationPickerDialog(
            initialQuery = "",
            currentPlace = selectedPlace,
            recent = composerState.recentPlaces,
            results = composerState.placeResults,
            mapsApiKey = sessionState.mapsKey,
            language = lang,
            onSearch = { q -> shared.composer.searchPlaces(q, null) },
            onResolvePlaceDetails = { placeId -> shared.composer.resolvePlaceDetails(placeId) },
            onCreateCustomPlace = { placeName, lat, lng -> shared.composer.createCustomPlace(placeName, lat, lng) },
            onSelectPlace = { place ->
                selectedPlace = place
                location = place.name
                savedMessage = null
                showLocationDialog = false
            },
            onDismiss = { showLocationDialog = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PublicUserProfileScreen(
    userId: String,
    shared: TownSharedClient,
    onNavigate: (AppRoute) -> Unit,
    onRequireAuth: () -> Unit,
    onBack: () -> Unit,
) {
    val sessionState by shared.session.state.collectAsState()
    val profileState by shared.profile.state.collectAsState()
    val activityState by shared.activity.state.collectAsState()
    val lang = sessionState.language
    val tokens = LocalSaigonTokens.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var zoomPhoto by remember { mutableStateOf<String?>(null) }
    val openDirectMessage = rememberDirectMessageOpener(
        shared = shared,
        onNavigate = onNavigate,
        onRequireAuth = onRequireAuth,
    )

    LaunchedEffect(userId, sessionState.me?.id) {
        shared.profile.loadUserProfile(userId)
    }

    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    SaigonPageShell(
        title = profileState.viewedUser?.name ?: Copy.text(lang, "profile"),
        onBack = onBack,
        backContentDescription = Copy.text(lang, "back"),
        topActions = {
            profileState.viewedUser?.let { u ->
                SaigonIconButton(
                    onClick = { AndroidIntents.copyPathLink(context, "/u/${u.id}", lang) },
                    overlay = false,
                ) {
                    Icon(Icons.Default.Link, contentDescription = Copy.text(lang, "copyLink"), modifier = Modifier.size(20.dp))
                }
            }
        },
    ) {
        val user = profileState.viewedUser
        if (profileState.isLoading && user == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                SaigonSpinner()
            }
        } else if (user != null) {
            LazyColumn(
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 28.dp + bottomInset),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        UserAvatar(
                            photoUrl = user.photoUrl,
                            name = user.name,
                            size = 96.dp,
                            borderWidth = 0.dp,
                            modifier = Modifier
                                .clip(CircleShape)
                                .then(
                                    if (!user.photoUrl.isNullOrBlank()) {
                                        Modifier.clickable { zoomPhoto = user.photoUrl }
                                    } else {
                                        Modifier
                                    },
                                ),
                        )
                        Text(user.name, color = tokens.text, style = MaterialTheme.typography.titleLarge)
                        if (user.location.isNotBlank()) {
                            Text(user.location, color = tokens.muted, fontWeight = FontWeight.Bold)
                        }
                        if (user.intro.isNotBlank()) {
                            LinkifiedText(text = user.intro, color = tokens.text)
                        }
                        if (profileState.viewedUserCommunities.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                            ) {
                                profileState.viewedUserCommunities.forEach { comm ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(tokens.surface)
                                            .border(1.dp, tokens.line, RoundedCornerShape(999.dp))
                                            .clickable { onNavigate(AppRoute.Community(comm.id)) }
                                            .padding(horizontal = 10.dp, vertical = 5.dp),
                                    ) {
                                        Text(
                                            text = "${comm.emoji} ${comm.name}".trim(),
                                            color = tokens.text,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                        )
                                    }
                                }
                            }
                        }
                        if (sessionState.me?.id != user.id) {
                            Spacer(Modifier.height(6.dp))
                            SaigonPrimaryButton(
                                text = Copy.text(lang, "messageUser", mapOf("name" to user.name)),
                                onClick = { openDirectMessage(user.id, user.name) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }

                // User's activities
                if (profileState.viewedUserActivities.isNotEmpty()) {
                    item {
                        Text(Copy.text(lang, "activities"), color = tokens.text, style = MaterialTheme.typography.titleMedium)
                    }
                    items(profileState.viewedUserActivities, key = { it.id }) { act ->
                        ActivityCard(
                            activity = act,
                            language = lang,
                            viewerId = sessionState.me?.id,
                            showLocation = true,
                            isMutating = activityState.isMutating,
                            onOpen = { onNavigate(AppRoute.Activity(act.id)) },
                            onToggleInterest = {
                                if (sessionState.me == null) {
                                    onRequireAuth()
                                } else {
                                    scope.launch {
                                        if (shared.activity.toggleInterest(act.id, act.interested) != null) {
                                            shared.profile.loadUserProfile(userId)
                                        }
                                    }
                                }
                            },
                            onOpenLocation = {
                                val token = openLocationDestination(
                                    shared = shared,
                                    name = act.locationName,
                                    placeId = act.placeId,
                                    locationId = act.locationId,
                                    lat = act.lat,
                                    lng = act.lng,
                                )
                                onNavigate(AppRoute.Location(token))
                            },
                            onOpenPhoto = { url -> zoomPhoto = url },
                        )
                    }
                }

                // User's products (.mini-product-row)
                if (profileState.viewedUserProducts.isNotEmpty()) {
                    item {
                        Text(Copy.text(lang, "products"), color = tokens.text, style = MaterialTheme.typography.titleMedium)
                    }
                    item {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                        ) {
                            profileState.viewedUserProducts.forEach { product ->
                                SaigonCard(
                                    onClick = { onNavigate(AppRoute.Product(product.id)) },
                                    borderWidth = 2.dp,
                                    cornerRadius = 16.dp,
                                    contentPadding = PaddingValues(10.dp),
                                    modifier = Modifier.width(230.dp),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        val photo = product.photoUrls.firstOrNull()
                                        if (!photo.isNullOrBlank()) {
                                            AsyncImage(
                                                model = photo,
                                                contentDescription = product.name,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(72.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(SaigonColors.Card),
                                            )
                                        } else {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier
                                                    .size(72.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(SaigonColors.Card),
                                            ) {
                                                Text("🛍️", fontSize = 24.sp)
                                            }
                                        }
                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(3.dp),
                                            modifier = Modifier.weight(1f),
                                        ) {
                                            Text(
                                                product.name,
                                                color = tokens.text,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 14.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                formatVnd(product.price),
                                                color = tokens.accent,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontSize = 13.sp,
                                            )
                                            if (product.description.isNotBlank()) {
                                                Text(
                                                    product.description,
                                                    color = tokens.muted,
                                                    fontSize = 12.sp,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // User's posts (3-column 9:16 grid, never "Your posts")
                if (profileState.viewedUserPosts.isNotEmpty()) {
                    item {
                        val total = maxOf(profileState.viewedUserPostsCount, profileState.viewedUserPosts.size.toLong())
                        Text(
                            Copy.text(lang, "postCount", mapOf("count" to total.toString())),
                            color = tokens.text,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    item {
                        PostGridRows(
                            posts = profileState.viewedUserPosts,
                            showAuthor = false,
                            onPostClick = { onNavigate(AppRoute.Post(it.id)) },
                        )
                    }
                    if (profileState.viewedUserPostsNextCursor != null) {
                        item {
                            SaigonTextButton(
                                text = Copy.text(lang, "loadMore"),
                                onClick = { shared.profile.loadMoreUserPosts(userId) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }

    zoomPhoto?.let { PhotoViewerDialog(photoUrl = it, onDismiss = { zoomPhoto = null }) }
}

@Composable
fun MyPostsScreen(
    shared: TownSharedClient,
    onNavigate: (AppRoute) -> Unit,
    onBack: () -> Unit,
) {
    val lang = shared.session.state.collectAsState().value.language
    val state by shared.profile.state.collectAsState()
    val tokens = LocalSaigonTokens.current
    val scope = rememberCoroutineScope()
    var editingId by remember { mutableStateOf<String?>(null) }
    var editingText by remember { mutableStateOf("") }
    var pendingDeletePost by remember { mutableStateOf<OwnedPostDto?>(null) }

    LaunchedEffect(Unit) { shared.profile.loadMyPosts() }

    SaigonPageShell(
        title = Copy.text(lang, "yourPosts"),
        onBack = onBack,
    ) {
        if (state.isLoading && state.myPosts.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                SaigonSpinner()
            }
        } else if (state.myPosts.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(Copy.text(lang, "noPosts"), color = tokens.muted)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            ) {
                items(state.myPosts, key = { it.id }) { post ->
                    SaigonCard(
                        onClick = { onNavigate(AppRoute.Post(post.id)) },
                        borderWidth = 2.dp,
                        cornerRadius = 16.dp,
                        contentPadding = PaddingValues(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            AsyncImage(
                                model = post.photoUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(width = 72.dp, height = 96.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(tokens.line),
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                                Text(post.message, color = tokens.text, fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                if (post.location.isNotBlank()) {
                                    Text("📍 ${post.location}", fontSize = 13.sp, color = tokens.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                if (!post.communityId.isNullOrBlank() && !post.communityName.isNullOrBlank()) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        SaigonTinyPill(
                                            label = "${post.communityEmoji ?: "✨"} ${post.communityName}".trim(),
                                            onClick = { onNavigate(AppRoute.Community(post.communityId!!)) },
                                        )
                                        if (post.communityOnly) {
                                            Text(Copy.text(lang, "communityOnly"), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = tokens.muted)
                                        }
                                    }
                                }
                                if (!post.musicName.isNullOrBlank()) {
                                    Text("🎵 ${post.musicName}", fontSize = 13.sp, color = tokens.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Text(
                                    text = buildAnnotatedString {
                                        withStyle(SpanStyle(color = tokens.text, fontWeight = FontWeight.ExtraBold)) { append("${post.views}") }
                                        append(" ${Copy.text(lang, "views")} · ")
                                        withStyle(SpanStyle(color = tokens.text, fontWeight = FontWeight.ExtraBold)) { append("${post.replyClicks}") }
                                        append(" ${Copy.text(lang, "repliesClicked")} · ")
                                        withStyle(SpanStyle(color = tokens.text, fontWeight = FontWeight.ExtraBold)) { append("${post.shareClicks}") }
                                        append(" ${Copy.text(lang, "shareClicked")}")
                                    },
                                    fontSize = 12.sp,
                                    color = tokens.muted,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    SaigonTinyPill(
                                        label = Copy.text(lang, "edit"),
                                        onClick = { editingId = post.id; editingText = post.message },
                                    )
                                    SaigonTinyPill(
                                        label = Copy.text(lang, "delete"),
                                        danger = true,
                                        onClick = { pendingDeletePost = post },
                                    )
                                }
                            }
                        }
                    }
                }
                if (state.myPostsNextCursor != null) {
                    item {
                        SaigonTextButton(
                            text = Copy.text(lang, "loadMore"),
                            onClick = { shared.profile.loadMyPosts(loadMore = true) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }

    editingId?.let { postId ->
        SaigonDialog(onDismiss = { editingId = null }) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(Copy.text(lang, "edit"), color = tokens.text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(end = 36.dp))
                SaigonTextField(
                    value = editingText,
                    onValueChange = { if (it.length <= Limits.MESSAGE_MAX) editingText = it },
                    singleLine = false,
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                SaigonPrimaryButton(
                    text = Copy.text(lang, "save"),
                    onClick = {
                        scope.launch {
                            if (shared.profile.editMyPost(postId, editingText.trim())) editingId = null
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                SaigonTextButton(text = Copy.text(lang, "cancel"), onClick = { editingId = null }, modifier = Modifier.fillMaxWidth())
            }
        }
    }

    pendingDeletePost?.let { target ->
        SaigonConfirmDialog(
            title = Copy.text(lang, "deletePostAsk"),
            hint = Copy.text(lang, "deletePostHint"),
            confirmLabel = Copy.text(lang, "delete"),
            cancelLabel = Copy.text(lang, "cancel"),
            loading = state.isMutating,
            onConfirm = {
                scope.launch {
                    if (shared.profile.deleteMyPost(target.id)) {
                        pendingDeletePost = null
                    }
                }
            },
            onDismiss = { pendingDeletePost = null },
        )
    }
}

@Composable
fun MyCommunitiesScreen(
    shared: TownSharedClient,
    onNavigate: (AppRoute) -> Unit,
    onBack: () -> Unit,
) {
    val lang = shared.session.state.collectAsState().value.language
    val state by shared.profile.state.collectAsState()
    val tokens = LocalSaigonTokens.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { shared.profile.loadMyCommunities() }

    SaigonPageShell(
        title = Copy.text(lang, "communities"),
        onBack = onBack,
    ) {
        if (state.isLoading && state.myCommunities.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                SaigonSpinner()
            }
        } else if (state.myCommunities.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(Copy.text(lang, "noCommunities"), color = tokens.muted)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            ) {
                items(state.myCommunities, key = { it.id }) { comm ->
                    SaigonCard(
                        onClick = { onNavigate(AppRoute.Community(comm.id)) },
                        borderWidth = 1.dp,
                        cornerRadius = 14.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${comm.emoji} ${comm.name}", color = tokens.text, fontWeight = FontWeight.ExtraBold)
                                Text("${comm.memberCount} ${Copy.text(lang, "communityMembers")}", fontSize = 12.sp, color = tokens.muted)
                            }
                            SaigonTinyPill(
                                label = Copy.text(lang, "leaveCommunity"),
                                danger = true,
                                onClick = { scope.launch { shared.profile.leaveMyCommunity(comm.id) } },
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MyMusicScreen(
    shared: TownSharedClient,
    audioPlayer: AudioPreviewPlayer,
    onBack: () -> Unit,
) {
    val lang = shared.session.state.collectAsState().value.language
    val state by shared.profile.state.collectAsState()
    val audioState by audioPlayer.state.collectAsState()
    val tokens = LocalSaigonTokens.current
    val scope = rememberCoroutineScope()
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }
    var renamingTrack by remember { mutableStateOf<MusicDto?>(null) }
    var renameText by remember { mutableStateOf("") }
    var renameError by remember { mutableStateOf<String?>(null) }
    var expandedLyricsIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(Unit) { shared.profile.loadMyMusic() }

    SaigonPageShell(
        title = Copy.text(lang, "yourMusic"),
        onBack = onBack,
    ) {
        if (state.isLoading && state.myMusic.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                SaigonSpinner()
            }
        } else if (state.myMusic.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(Copy.text(lang, "noMusicYet"), color = tokens.muted)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            ) {
                items(state.myMusic, key = { it.id }) { track ->
                    val lyricsText = track.lyrics.orEmpty()
                    val showLyrics = track.id in expandedLyricsIds
                    SaigonCard(
                        borderWidth = 2.dp,
                        cornerRadius = 16.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "🎵 ${track.name.ifBlank { track.prompt }}",
                                color = tokens.text,
                                fontWeight = FontWeight.ExtraBold,
                            )
                            when (track.status) {
                                "failed" -> {
                                    val errText = track.error?.takeIf { it.isNotBlank() }?.let {
                                        Copy.text(lang, "musicFailedDetail", mapOf("reason" to it))
                                    } ?: Copy.text(lang, "musicFailed")
                                    Text(errText, fontSize = 12.sp, color = tokens.danger)
                                }
                                "generating" -> {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        SaigonSpinner(size = 18.dp)
                                        Text(Copy.text(lang, "generatingMusic"), fontSize = 12.sp, color = tokens.muted)
                                    }
                                }
                                else -> {
                                    if (track.prompt.isNotBlank()) {
                                        Text(track.prompt, fontSize = 12.sp, color = tokens.muted)
                                    }
                                }
                            }
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                val audioUrl = track.audioUrl
                                if (track.status == "ready" && !audioUrl.isNullOrBlank()) {
                                    val playing = audioState.isPlaying && audioState.currentUrl == audioUrl
                                    SaigonTinyPill(
                                        label = Copy.text(lang, if (playing) "pause" else "preview"),
                                        selected = playing,
                                        onClick = { audioPlayer.toggle(audioUrl) },
                                    )
                                }
                                if (track.status != "generating") {
                                    SaigonTinyPill(
                                        label = Copy.text(lang, "rename"),
                                        onClick = {
                                            renamingTrack = track
                                            renameText = track.name
                                            renameError = null
                                        },
                                    )
                                }
                                if (lyricsText.isNotBlank()) {
                                    SaigonTinyPill(
                                        label = Copy.text(lang, "lyrics"),
                                        selected = showLyrics,
                                        onClick = {
                                            expandedLyricsIds = if (showLyrics) {
                                                expandedLyricsIds - track.id
                                            } else {
                                                expandedLyricsIds + track.id
                                            }
                                        },
                                    )
                                }
                                if (track.status == "failed") {
                                    SaigonTinyPill(
                                        label = Copy.text(lang, "retry"),
                                        onClick = {
                                            scope.launch {
                                                shared.profile.retryMyMusic(track.id)
                                            }
                                        },
                                    )
                                }
                                if (track.status != "generating") {
                                    SaigonTinyPill(
                                        label = Copy.text(lang, "delete"),
                                        danger = true,
                                        onClick = { pendingDeleteId = track.id },
                                    )
                                }
                            }
                            if (showLyrics && lyricsText.isNotBlank()) {
                                Text(
                                    lyricsText,
                                    color = tokens.muted,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    renamingTrack?.let { target ->
        SaigonDialog(onDismiss = { renamingTrack = null }) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    Copy.text(lang, "rename"),
                    color = tokens.text,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(end = 36.dp),
                )
                SaigonTextField(
                    value = renameText,
                    onValueChange = {
                        if (it.length <= Limits.MUSIC_NAME_MAX) {
                            renameText = it
                            renameError = null
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                (renameError ?: state.error)?.let {
                    Text(it, color = tokens.danger, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                SaigonPrimaryButton(
                    text = Copy.text(lang, "save"),
                    enabled = !state.isMutating,
                    loading = state.isMutating,
                    onClick = {
                        val clean = renameText.trim()
                        if (clean.isEmpty()) {
                            renameError = Copy.text(lang, "invalidMusicName")
                            return@SaigonPrimaryButton
                        }
                        scope.launch {
                            if (shared.profile.renameMyMusic(target.id, clean) != null) {
                                renamingTrack = null
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                SaigonTextButton(
                    text = Copy.text(lang, "cancel"),
                    onClick = { renamingTrack = null },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    pendingDeleteId?.let { targetId ->
        SaigonConfirmDialog(
            title = Copy.text(lang, "dismissMusicAsk"),
            hint = Copy.text(lang, "dismissMusicHint"),
            confirmLabel = Copy.text(lang, "delete"),
            cancelLabel = Copy.text(lang, "cancel"),
            loading = state.isMutating,
            onConfirm = {
                scope.launch {
                    if (shared.profile.deleteMyMusic(targetId)) {
                        pendingDeleteId = null
                    }
                }
            },
            onDismiss = { pendingDeleteId = null },
        )
    }
}

@Composable
fun MyProductsScreen(
    shared: TownSharedClient,
    onNavigate: (AppRoute) -> Unit,
    onBack: () -> Unit,
) {
    val lang = shared.session.state.collectAsState().value.language
    val state by shared.profile.state.collectAsState()
    val shopState by shared.shop.state.collectAsState()
    val tokens = LocalSaigonTokens.current
    val scope = rememberCoroutineScope()
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingProduct by remember { mutableStateOf<ProductDto?>(null) }
    var pendingDeleteProduct by remember { mutableStateOf<ProductDto?>(null) }

    LaunchedEffect(Unit) { shared.profile.loadMyProducts() }

    SaigonPageShell(
        title = Copy.text(lang, "yourLessons"),
        onBack = onBack,
        topActions = {
            SaigonIconButton(onClick = { showCreateDialog = true }, overlay = false) {
                Icon(Icons.Default.Add, contentDescription = Copy.text(lang, "createProduct"), modifier = Modifier.size(20.dp))
            }
        },
    ) {
        if (state.isLoading && state.myProducts.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                SaigonSpinner()
            }
        } else if (state.myProducts.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(Copy.text(lang, "noProducts"), color = tokens.muted)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            ) {
                items(state.myProducts, key = { it.id }) { product ->
                    SaigonCard(
                        onClick = { onNavigate(AppRoute.Product(product.id)) },
                        borderWidth = 2.dp,
                        cornerRadius = 16.dp,
                        contentPadding = PaddingValues(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                val photo = product.photoUrls.firstOrNull()
                                if (!photo.isNullOrBlank()) {
                                    AsyncImage(
                                        model = photo,
                                        contentDescription = product.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(84.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(SaigonColors.Card),
                                    )
                                } else {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(84.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(SaigonColors.Card),
                                    ) {
                                        Text("🛍️", fontSize = 26.sp)
                                    }
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.weight(1f)) {
                                    Text(product.name, color = tokens.text, fontWeight = FontWeight.ExtraBold)
                                    Text(formatVnd(product.price), color = tokens.accent, fontWeight = FontWeight.ExtraBold)
                                    if (product.description.isNotBlank()) {
                                        Text(
                                            product.description,
                                            color = tokens.muted,
                                            fontSize = 13.sp,
                                            lineHeight = 18.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SaigonTinyPill(
                                    label = Copy.text(lang, "edit"),
                                    onClick = { editingProduct = product },
                                )
                                SaigonTinyPill(
                                    label = Copy.text(lang, "delete"),
                                    danger = true,
                                    onClick = { pendingDeleteProduct = product },
                                )
                            }
                        }
                    }
                }
                if (state.myProductsNextCursor != null) {
                    item {
                        SaigonTextButton(
                            text = Copy.text(lang, "loadMore"),
                            onClick = { shared.profile.loadMyProducts(loadMore = true) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        ProductEditorDialog(
            existing = null,
            shared = shared,
            language = lang,
            onSaved = { shared.profile.loadMyProducts() },
            onDismiss = { showCreateDialog = false },
        )
    }

    editingProduct?.let { target ->
        ProductEditorDialog(
            existing = target,
            shared = shared,
            language = lang,
            onSaved = { shared.profile.loadMyProducts() },
            onDismiss = { editingProduct = null },
        )
    }

    pendingDeleteProduct?.let { target ->
        SaigonConfirmDialog(
            title = Copy.text(lang, "deleteProductAsk"),
            hint = Copy.text(lang, "deleteProductHint"),
            confirmLabel = Copy.text(lang, "delete"),
            cancelLabel = Copy.text(lang, "cancel"),
            loading = shopState.isSubmitting,
            onConfirm = {
                scope.launch {
                    if (shared.shop.deleteProduct(target.id)) {
                        pendingDeleteProduct = null
                        shared.profile.loadMyProducts()
                    }
                }
            },
            onDismiss = { pendingDeleteProduct = null },
        )
    }
}

@Composable
fun MyActivitiesScreen(
    shared: TownSharedClient,
    onNavigate: (AppRoute) -> Unit,
    onBack: () -> Unit,
) {
    val sessionState by shared.session.state.collectAsState()
    val lang = sessionState.language
    val state by shared.profile.state.collectAsState()
    val activityState by shared.activity.state.collectAsState()
    val tokens = LocalSaigonTokens.current
    val scope = rememberCoroutineScope()
    var editingActivity by remember { mutableStateOf<ActivityDto?>(null) }
    var deletingActivity by remember { mutableStateOf<ActivityDto?>(null) }
    var zoomPhoto by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { shared.profile.loadMyActivities() }

    SaigonPageShell(
        title = Copy.text(lang, "myActivities"),
        onBack = onBack,
    ) {
        if (state.isLoading && state.myActivities.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                SaigonSpinner()
            }
        } else if (state.myActivities.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(Copy.text(lang, "noActivities"), color = tokens.muted)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            ) {
                items(state.myActivities, key = { it.id }) { act ->
                    ActivityCard(
                        activity = act,
                        language = lang,
                        viewerId = sessionState.me?.id,
                        showLocation = true,
                        isMutating = activityState.isMutating,
                        onOpen = { onNavigate(AppRoute.Activity(act.id)) },
                        onToggleInterest = {
                            scope.launch {
                                if (shared.activity.toggleInterest(act.id, act.interested) != null) {
                                    shared.profile.loadMyActivities()
                                }
                            }
                        },
                        onOpenLocation = {
                            val token = openLocationDestination(
                                shared = shared,
                                name = act.locationName,
                                placeId = act.placeId,
                                locationId = act.locationId,
                                lat = act.lat,
                                lng = act.lng,
                            )
                            onNavigate(AppRoute.Location(token))
                        },
                        onOpenPhoto = { url -> zoomPhoto = url },
                        footer = {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                SaigonTinyPill(
                                    label = Copy.text(lang, "edit"),
                                    onClick = { editingActivity = act },
                                )
                                SaigonTinyPill(
                                    label = Copy.text(lang, "delete"),
                                    danger = true,
                                    onClick = { deletingActivity = act },
                                )
                            }
                        },
                    )
                }
            }
        }
    }

    editingActivity?.let { act ->
        ActivityEditorDialog(
            existing = act,
            locationName = act.locationName,
            locationId = act.locationId,
            placeId = act.placeId,
            lat = act.lat,
            lng = act.lng,
            shared = shared,
            language = lang,
            onSaved = {
                editingActivity = null
                shared.profile.loadMyActivities()
            },
            onDismiss = { editingActivity = null },
        )
    }

    deletingActivity?.let { act ->
        SaigonConfirmDialog(
            title = Copy.text(lang, "deleteActivityAsk"),
            hint = Copy.text(lang, "deleteActivityHint"),
            confirmLabel = Copy.text(lang, "delete"),
            cancelLabel = Copy.text(lang, "cancel"),
            loading = activityState.isMutating,
            onConfirm = {
                scope.launch {
                    if (shared.activity.deleteActivity(act.id)) {
                        deletingActivity = null
                        shared.profile.loadMyActivities()
                    }
                }
            },
            onDismiss = { deletingActivity = null },
        )
    }

    zoomPhoto?.let { PhotoViewerDialog(photoUrl = it, onDismiss = { zoomPhoto = null }) }
}

@Composable
fun MyOrdersScreen(
    shared: TownSharedClient,
    onNavigate: (AppRoute) -> Unit,
    onBack: () -> Unit,
) {
    val lang = shared.session.state.collectAsState().value.language
    val state by shared.profile.state.collectAsState()
    val tokens = LocalSaigonTokens.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { shared.profile.loadMyOrders() }

    SaigonPageShell(
        title = Copy.text(lang, "productOrdersCount", mapOf("count" to state.openOrdersCount.toString())),
        onBack = onBack,
    ) {
        if (state.isLoading && state.myOrders.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                SaigonSpinner()
            }
        } else if (state.myOrders.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(Copy.text(lang, "noProductOrders"), color = tokens.muted)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize().navigationBarsPadding(),
            ) {
                items(state.myOrders, key = { it.id }) { order ->
                    SaigonCard(
                        borderColor = if (!order.resolved) tokens.accent else tokens.line,
                        borderWidth = 2.dp,
                        cornerRadius = 16.dp,
                        contentPadding = PaddingValues(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(if (order.resolved) 0.75f else 1f),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(order.productName, color = tokens.text, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                                Text(formatVnd(order.price), color = tokens.accent, fontWeight = FontWeight.ExtraBold)
                            }
                            Text(
                                "${order.buyerName.orEmpty()} · ${order.buyerPhone.orEmpty()}".uppercase(),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = tokens.muted,
                            )
                            if (order.message.isNotBlank()) Text(order.message, color = tokens.text)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                order.contactChatId?.let { chatId ->
                                    SaigonOutlinedPillButton(
                                        text = Copy.text(lang, "openChat"),
                                        onClick = { onNavigate(AppRoute.Chat(chatId)) },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                    )
                                }
                                if (order.contactMethod == "zalo" && !order.buyerPhone.isNullOrBlank()) {
                                    SaigonOutlinedPillButton(
                                        text = "Zalo",
                                        onClick = { AndroidIntents.openExternalUrl(context, "https://zalo.me/${order.buyerPhone}") },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                    )
                                }
                                SaigonSecondaryButton(
                                    text = Copy.text(lang, if (order.resolved) "markUnresolved" else "markResolved"),
                                    onClick = { scope.launch { shared.profile.resolveOrder(order.id, !order.resolved) } },
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                )
                            }
                        }
                    }
                }
                if (state.myOrdersNextCursor != null) {
                    item {
                        SaigonTextButton(
                            text = Copy.text(lang, "loadMore"),
                            onClick = { shared.profile.loadMyOrders(loadMore = true) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FeatureSuggestionsScreen(
    shared: TownSharedClient,
    onBack: () -> Unit,
) {
    val sessionState by shared.session.state.collectAsState()
    val state by shared.profile.state.collectAsState()
    val lang = sessionState.language
    val tokens = LocalSaigonTokens.current
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var pendingDeleteFeature by remember { mutableStateOf<FeatureSuggestionDto?>(null) }

    LaunchedEffect(Unit) { shared.profile.loadFeatures() }

    SaigonPageShell(
        title = Copy.text(lang, "allFeatures"),
        onBack = onBack,
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                SaigonTextField(
                    value = input,
                    onValueChange = { if (it.length <= Limits.FEATURE_MAX) input = it },
                    placeholder = Copy.text(lang, "suggestFeatureHint"),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                SaigonPrimaryButton(
                    text = Copy.text(lang, "submitFeature"),
                    enabled = input.isNotBlank() && !state.isMutating,
                    dimWhenDisabled = false,
                    onClick = {
                        val text = input.trim()
                        scope.launch {
                            if (shared.profile.createFeature(text) != null) input = ""
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            if (state.isLoading && state.features.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    SaigonSpinner()
                }
            } else if (state.features.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(Copy.text(lang, "noFeatures"), color = tokens.muted)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.navigationBarsPadding(),
                ) {
                    items(state.features, key = { it.id }) { feat ->
                        SaigonCard(
                            borderWidth = 2.dp,
                            cornerRadius = 14.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .alpha(if (feat.resolved) 0.72f else 1f),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(feat.text, color = tokens.text, fontWeight = FontWeight.Medium)
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    itemVerticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (feat.resolved) {
                                        Text(
                                            Copy.text(lang, "featureResolved").uppercase(),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = tokens.accent,
                                        )
                                    }
                                    SaigonTinyPill(
                                        label = Copy.text(lang, if (feat.voted) "voted" else "vote"),
                                        selected = feat.voted,
                                        onClick = { scope.launch { shared.profile.voteFeature(feat.id) } },
                                    )
                                    Text(
                                        Copy.text(lang, "votes", mapOf("count" to feat.voteCount.toString())),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = tokens.muted,
                                    )
                                    if (sessionState.me?.admin == true) {
                                        if (!feat.resolved) {
                                            SaigonTinyPill(
                                                label = Copy.text(lang, "resolveFeature"),
                                                onClick = { scope.launch { shared.profile.resolveFeature(feat.id) } },
                                            )
                                        }
                                        SaigonTinyPill(
                                            label = Copy.text(lang, "delete"),
                                            danger = true,
                                            onClick = { pendingDeleteFeature = feat },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pendingDeleteFeature?.let { target ->
        SaigonConfirmDialog(
            title = Copy.text(lang, "deleteFeatureAsk"),
            hint = Copy.text(lang, "deleteFeatureHint"),
            confirmLabel = Copy.text(lang, "delete"),
            cancelLabel = Copy.text(lang, "cancel"),
            loading = state.isMutating,
            onConfirm = {
                scope.launch {
                    if (shared.profile.deleteFeature(target.id)) {
                        pendingDeleteFeature = null
                    }
                }
            },
            onDismiss = { pendingDeleteFeature = null },
        )
    }
}
