package quest.saigon.android.ui.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import quest.saigon.android.ui.components.AndroidIntents
import quest.saigon.android.ui.components.LinkifiedText
import quest.saigon.android.ui.components.PhotoViewerDialog
import quest.saigon.android.ui.components.PostGridRows
import quest.saigon.android.ui.components.SaigonCard
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
import quest.saigon.shared.TownSharedClient
import quest.saigon.shared.model.UpdateMeRequest

@Composable
fun ProfileScreen(
    shared: TownSharedClient,
    onNavigate: (AppRoute) -> Unit,
    onBack: () -> Unit,
) {
    val sessionState by shared.session.state.collectAsState()
    val me = sessionState.me
    val lang = sessionState.language
    val tokens = LocalSaigonTokens.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var name by remember(me?.id, me?.name) { mutableStateOf(me?.name.orEmpty()) }
    var location by remember(me?.id, me?.location) { mutableStateOf(me?.location.orEmpty()) }
    var intro by remember(me?.id, me?.intro) { mutableStateOf(me?.intro.orEmpty()) }
    var savedMessage by remember { mutableStateOf<String?>(null) }

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
                        .clickable {
                            avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
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
                            .size(26.dp)
                            .shadow(3.dp, CircleShape)
                            .clip(CircleShape)
                            .background(SaigonBrushes.primaryGradient),
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = Copy.text(lang, "changePhoto"), tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
                Text(me?.name.orEmpty(), color = tokens.text, style = MaterialTheme.typography.titleLarge)
                if (me != null && me.phone.isNotBlank()) {
                    Text(me.phone, fontSize = 13.sp, color = tokens.muted)
                }
            }

            SaigonTextField(
                value = name,
                onValueChange = { if (it.length <= Limits.NAME_MAX) name = it },
                label = Copy.text(lang, "name"),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            SaigonTextField(
                value = location,
                onValueChange = { if (it.length <= Limits.LOCATION_MAX) location = it },
                label = Copy.text(lang, "location"),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            SaigonTextField(
                value = intro,
                onValueChange = { if (it.length <= Limits.INTRO_MAX) intro = it },
                label = Copy.text(lang, "introduceYourself"),
                singleLine = false,
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )

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
                                location = location.trim(),
                                intro = intro.trim(),
                            ),
                        )
                        if (ok) savedMessage = Copy.text(lang, "saved")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            savedMessage?.let { Text(it, color = tokens.accent, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
            sessionState.error?.let { Text(it, color = tokens.danger, fontWeight = FontWeight.Bold, fontSize = 14.sp) }

            HorizontalDivider(color = tokens.line, thickness = 1.dp)

            val links = buildList {
                add(Copy.text(lang, "yourPosts") to AppRoute.MyPosts)
                add(Copy.text(lang, "communities") to AppRoute.MyCommunities)
                add(Copy.text(lang, "yourMusic") to AppRoute.MyMusic)
                add(Copy.text(lang, "products") to AppRoute.MyProducts)
                add(Copy.text(lang, "myActivities") to AppRoute.MyActivities)
                add(Copy.text(lang, "productOrders") to AppRoute.MyOrders)
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
                            modifier = Modifier.then(
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
                        Spacer(Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (sessionState.me?.id != user.id) {
                                SaigonPrimaryButton(
                                    text = Copy.text(lang, "messageUser", mapOf("name" to user.name)),
                                    onClick = {
                                        if (sessionState.me == null) {
                                            onRequireAuth()
                                        } else {
                                            scope.launch {
                                                val chat = shared.messages.openDirectChat(user.id)
                                                if (chat != null) onNavigate(AppRoute.Chat(chat.id))
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                                SaigonIconButton(
                                    onClick = { AndroidIntents.copyPathLink(context, "/u/${user.id}", lang) },
                                    overlay = false,
                                ) {
                                    Icon(Icons.Default.Link, contentDescription = Copy.text(lang, "copyLink"), modifier = Modifier.size(20.dp))
                                }
                            } else {
                                SaigonOutlinedPillButton(
                                    text = Copy.text(lang, "copyLink"),
                                    onClick = { AndroidIntents.copyPathLink(context, "/u/${user.id}", lang) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
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

    LaunchedEffect(Unit) { shared.profile.loadMyPosts() }

    SaigonPageShell(
        title = Copy.text(lang, "yourPosts"),
        onBack = onBack,
    ) {
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
                            Text(
                                text = buildAnnotatedString {
                                    withStyle(SpanStyle(color = tokens.text, fontWeight = FontWeight.ExtraBold)) { append("${post.views}") }
                                    append(" 👁 · ")
                                    withStyle(SpanStyle(color = tokens.text, fontWeight = FontWeight.ExtraBold)) { append("${post.replyClicks}") }
                                    append(" 💬 · ")
                                    withStyle(SpanStyle(color = tokens.text, fontWeight = FontWeight.ExtraBold)) { append("${post.shareClicks}") }
                                    append(" ↗")
                                },
                                fontSize = 13.sp,
                                color = tokens.muted,
                            )
                        }
                        IconButton(onClick = { editingId = post.id; editingText = post.message }) {
                            Icon(Icons.Default.Edit, contentDescription = null, tint = tokens.accent)
                        }
                        IconButton(onClick = { scope.launch { shared.profile.deleteMyPost(post.id) } }) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = tokens.danger)
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
                            onClick = { scope.launch { shared.profile.leaveMyCommunity(comm.id) } },
                        )
                    }
                }
            }
        }
    }
}

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

    LaunchedEffect(Unit) { shared.profile.loadMyMusic() }

    SaigonPageShell(
        title = Copy.text(lang, "yourMusic"),
        onBack = onBack,
    ) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
        ) {
            items(state.myMusic, key = { it.id }) { track ->
                SaigonCard(
                    borderWidth = 2.dp,
                    cornerRadius = 16.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (!track.audioUrl.isNullOrBlank()) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(tokens.bg)
                                    .clickable { audioPlayer.toggle(track.audioUrl) },
                            ) {
                                val playing = audioState.isPlaying && audioState.currentUrl == track.audioUrl
                                Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null, tint = tokens.text)
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(track.name.ifBlank { track.prompt }, color = tokens.text, fontWeight = FontWeight.Bold)
                            Text(track.status, fontSize = 12.sp, color = tokens.muted)
                        }
                        IconButton(onClick = {
                            scope.launch {
                                shared.composer.deleteMusic(track.id)
                                shared.profile.loadMyMusic()
                            }
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = tokens.danger)
                        }
                    }
                }
            }
        }
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
    val tokens = LocalSaigonTokens.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { shared.profile.loadMyProducts() }

    SaigonPageShell(
        title = Copy.text(lang, "products"),
        onBack = onBack,
        topActions = {
            SaigonIconButton(onClick = { onNavigate(AppRoute.Shop) }, overlay = false) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
            }
        },
    ) {
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
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AsyncImage(
                            model = product.photoUrls.firstOrNull(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(68.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(SaigonColors.Card),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(product.name, color = tokens.text, fontWeight = FontWeight.ExtraBold)
                            Text("${product.price} đ", color = tokens.accent, fontWeight = FontWeight.ExtraBold)
                        }
                        IconButton(onClick = {
                            scope.launch {
                                shared.shop.deleteProduct(product.id)
                                shared.profile.loadMyProducts()
                            }
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = tokens.danger)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MyActivitiesScreen(
    shared: TownSharedClient,
    onNavigate: (AppRoute) -> Unit,
    onBack: () -> Unit,
) {
    val lang = shared.session.state.collectAsState().value.language
    val state by shared.profile.state.collectAsState()
    val tokens = LocalSaigonTokens.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { shared.profile.loadMyActivities() }

    SaigonPageShell(
        title = Copy.text(lang, "myActivities"),
        onBack = onBack,
    ) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
        ) {
            items(state.myActivities, key = { it.id }) { act ->
                SaigonCard(
                    onClick = { onNavigate(AppRoute.Activity(act.id)) },
                    borderWidth = 2.dp,
                    cornerRadius = 16.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${act.emoji} ${act.name}", color = tokens.text, fontWeight = FontWeight.ExtraBold)
                            Text(act.locationName, fontSize = 13.sp, color = tokens.muted)
                        }
                        IconButton(onClick = {
                            scope.launch {
                                shared.activity.deleteActivity(act.id)
                                shared.profile.loadMyActivities()
                            }
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = tokens.danger)
                        }
                    }
                }
            }
        }
    }
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
                            Text("${order.price} đ", color = tokens.accent, fontWeight = FontWeight.ExtraBold)
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
        }
    }
}

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
                    placeholder = Copy.text(lang, "suggestFeature"),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                SaigonSecondaryButton(
                    text = Copy.text(lang, "send"),
                    enabled = input.isNotBlank() && !state.isMutating,
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
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                                Text(feat.text, color = tokens.text, fontWeight = FontWeight.Medium)
                                if (feat.resolved) {
                                    Text(
                                        Copy.text(lang, "resolved").uppercase(),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = tokens.accent,
                                    )
                                }
                            }
                            SaigonTinyPill(
                                label = "👍 ${feat.voteCount}",
                                selected = feat.voted,
                                onClick = { scope.launch { shared.profile.voteFeature(feat.id) } },
                            )
                            if (sessionState.me?.admin == true) {
                                IconButton(onClick = { scope.launch { shared.profile.deleteFeature(feat.id) } }) {
                                    Icon(Icons.Default.Delete, contentDescription = null, tint = tokens.danger)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
