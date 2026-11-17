package quest.saigon.android.ui.feed

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import quest.saigon.AppRoute
import quest.saigon.CallToAction
import quest.saigon.Copy
import quest.saigon.Language
import quest.saigon.Limits
import quest.saigon.PostAge
import quest.saigon.android.BuildConfig
import quest.saigon.android.media.AudioPreviewPlayer
import quest.saigon.android.theme.LivvicFontFamily
import quest.saigon.android.theme.LocalSaigonTokens
import quest.saigon.android.theme.SaigonBrushes
import quest.saigon.android.theme.SaigonColors
import quest.saigon.android.ui.components.AndroidIntents
import quest.saigon.android.ui.components.LinkifiedText
import quest.saigon.android.ui.components.MiniProfileDialog
import quest.saigon.android.ui.components.NotificationBellButton
import quest.saigon.android.ui.components.NotificationsSheet
import quest.saigon.android.ui.components.PhotoViewerDialog
import quest.saigon.android.ui.components.SaigonBottomSheet
import quest.saigon.android.ui.components.SaigonIconButton
import quest.saigon.android.ui.components.SaigonPrimaryButton
import quest.saigon.android.ui.components.SaigonSpinner
import quest.saigon.android.ui.components.UserAvatar
import quest.saigon.encodeLocationToken
import quest.saigon.feedMotion
import quest.saigon.postMessageNeedsMore
import quest.saigon.previewPostMessage
import quest.saigon.shared.TownSharedClient
import quest.saigon.shared.model.CheckInDto
import quest.saigon.shared.model.PostCardDto
import quest.saigon.shared.platform.currentTimeMillis
import quest.saigon.shared.store.FeedFilter

private data class SelectedPersonModal(
    val id: String,
    val name: String,
    val photoUrl: String?,
    val location: String,
    val intro: String,
)

private val OverlayShadow = Shadow(
    color = Color.Black.copy(alpha = 0.85f),
    offset = Offset(0f, 1f),
    blurRadius = 8f,
)

@Composable
fun FeedScreen(
    shared: TownSharedClient,
    audioPlayer: AudioPreviewPlayer,
    focusPostId: String? = null,
    communityId: String? = null,
    onNavigate: (AppRoute) -> Unit,
    onNavigatePath: (String) -> Unit,
    onRequireAuth: () -> Unit,
    onBack: (() -> Unit)? = null,
) {
    val sessionState by shared.session.state.collectAsState()
    val feedState by shared.feed.state.collectAsState()
    val notifState by shared.notifications.state.collectAsState()
    val msgState by shared.messages.state.collectAsState()
    val discoverState by shared.discover.state.collectAsState()
    val audioState by audioPlayer.state.collectAsState()
    val lang = sessionState.language
    val tokens = LocalSaigonTokens.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showNotifications by remember { mutableStateOf(false) }
    var commentsPost by remember { mutableStateOf<PostCardDto?>(null) }
    var attendeesPostId by remember { mutableStateOf<String?>(null) }
    var pollVotersPostId by remember { mutableStateOf<String?>(null) }
    var questionPost by remember { mutableStateOf<PostCardDto?>(null) }
    var checkInPostId by remember { mutableStateOf<String?>(null) }
    var editPost by remember { mutableStateOf<PostCardDto?>(null) }
    var fullMessagePost by remember { mutableStateOf<PostCardDto?>(null) }
    var zoomPhotoUrl by remember { mutableStateOf<String?>(null) }
    var selectedPerson by remember { mutableStateOf<SelectedPersonModal?>(null) }
    var activeCommunity by remember { mutableStateOf<quest.saigon.shared.model.CommunityDto?>(null) }
    var peeking by remember { mutableStateOf(false) }

    val filter = remember(communityId) {
        if (!communityId.isNullOrBlank()) FeedFilter.Community(communityId) else FeedFilter.Home
    }

    LaunchedEffect(filter, focusPostId) {
        shared.feed.loadInitial(filter, focusPostId)
        if (!communityId.isNullOrBlank()) {
            val res = shared.api.getCommunity(communityId)
            if (res is quest.saigon.shared.network.ApiResult.Success) {
                activeCommunity = res.value
            }
        } else {
            activeCommunity = null
            shared.discover.refreshCheckIns()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SaigonColors.StageBg),
    ) {
        when {
            feedState.isLoading && feedState.posts.isEmpty() -> {
                SaigonSpinner(modifier = Modifier.align(Alignment.Center))
            }
            feedState.posts.isEmpty() -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                ) {
                    Text(
                        Copy.text(lang, if (communityId == null) "emptyFeed" else "emptyCommunity"),
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        Copy.text(lang, if (communityId == null) "emptyFeedHint" else "emptyCommunityHint"),
                        color = Color.White.copy(alpha = 0.72f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(4.dp))
                    SaigonPrimaryButton(
                        text = Copy.text(lang, "retry"),
                        onClick = { shared.feed.refresh() },
                    )
                }
            }
            else -> {
                val initialIndex = remember(feedState.posts, focusPostId) {
                    focusPostId?.let { id -> feedState.posts.indexOfFirst { it.id == id }.takeIf { it >= 0 } } ?: 0
                }
                val pagerState = rememberPagerState(
                    initialPage = initialIndex,
                    pageCount = { feedState.posts.size },
                )

                LaunchedEffect(pagerState.settledPage, feedState.posts.size) {
                    val post = feedState.posts.getOrNull(pagerState.settledPage)
                    if (post != null) {
                        shared.feed.selectPost(post.id)
                    }
                    if (pagerState.settledPage >= feedState.posts.lastIndex - 1 && feedState.nextCursor != null) {
                        shared.feed.loadMore()
                    }
                }

                VerticalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    val post = feedState.posts[page]
                    val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                    val motion = feedMotion(pageOffset * 320.0)
                    FeedPostSlide(
                        post = post,
                        isCurrent = page == pagerState.settledPage,
                        cardParallaxPx = motion.cardOffset.toFloat(),
                        peeking = peeking,
                        viewerId = sessionState.me?.id,
                        language = lang,
                        audioPlaying = audioState.currentUrl == post.musicUrl && audioState.isPlaying,
                        onSetPeeking = { peeking = it },
                        onZoomPhoto = { zoomPhotoUrl = it },
                        onToggleMusic = { audioPlayer.toggle(post.musicUrl) },
                        onSelectAuthor = {
                            if (post.authorId.isNotBlank()) {
                                selectedPerson = SelectedPersonModal(
                                    id = post.authorId,
                                    name = post.authorName,
                                    photoUrl = post.authorPhotoUrl,
                                    location = post.authorLocation,
                                    intro = post.authorIntro,
                                )
                            }
                        },
                        onOpenLocation = {
                            val token = post.locationId ?: encodeLocationToken(post.location)
                            onNavigate(AppRoute.Location(token))
                        },
                        onOpenCommunity = { cId -> onNavigate(AppRoute.Community(cId)) },
                        onExpandMessage = { fullMessagePost = post },
                        onQuickReply = { replyText ->
                            if (sessionState.me == null) {
                                onRequireAuth()
                            } else {
                                scope.launch { shared.postDetail.addComment(post.id, replyText) }
                            }
                        },
                        onOpenComments = { commentsPost = post },
                        onToggleEventJoin = {
                            if (sessionState.me == null) {
                                onRequireAuth()
                            } else {
                                scope.launch {
                                    if (post.joined) shared.postDetail.leaveEvent(post.id)
                                    else shared.postDetail.joinEvent(post.id)
                                }
                            }
                        },
                        onOpenAttendees = { attendeesPostId = post.id },
                        onVotePoll = { idx ->
                            if (sessionState.me == null) {
                                onRequireAuth()
                            } else {
                                scope.launch { shared.postDetail.votePoll(post.id, idx) }
                            }
                        },
                        onOpenPollVoters = { pollVotersPostId = post.id },
                        onOpenQuestion = { questionPost = post },
                        onJoinOpenChat = {
                            if (sessionState.me == null) {
                                onRequireAuth()
                            } else {
                                scope.launch {
                                    val chat = shared.postDetail.joinOpenChat(post.id)
                                    if (chat != null) onNavigate(AppRoute.Chat(chat.id))
                                }
                            }
                        },
                        onCallToAction = {
                            when (post.ctaDestination) {
                                CallToAction.WEBSITE -> CallToAction.httpUrl(post.ctaUrl)?.let {
                                    AndroidIntents.openExternalUrl(context, it)
                                }
                                CallToAction.ZALO -> {
                                    scope.launch {
                                        val url = post.authorZaloUrl ?: shared.postDetail.contactAuthor(post.id)
                                        if (!url.isNullOrBlank()) AndroidIntents.openExternalUrl(context, url)
                                    }
                                }
                                CallToAction.MESSAGE -> {
                                    if (sessionState.me == null) {
                                        onRequireAuth()
                                    } else if (post.authorId.isNotBlank()) {
                                        scope.launch {
                                            val chat = shared.messages.openDirectChat(post.authorId)
                                            if (chat != null) onNavigate(AppRoute.Chat(chat.id))
                                        }
                                    }
                                }
                                CallToAction.PROFILE -> if (post.authorId.isNotBlank()) {
                                    onNavigate(AppRoute.User(post.authorId))
                                }
                                CallToAction.PRODUCT -> post.ctaUrl?.takeIf { it.isNotBlank() }?.let {
                                    onNavigate(AppRoute.Product(it))
                                }
                            }
                        },
                        onOpenCheckIn = { checkInPostId = post.id },
                        onEditPost = { editPost = post },
                        onSharePost = {
                            shared.postDetail.sharePost(post.id)
                            val origin = BuildConfig.SERVER_ORIGIN.trimEnd('/')
                            AndroidIntents.shareText(context, "$origin/p/${post.id}", post.authorName)
                        },
                    )
                }
            }
        }

        // Topbar matching .topbar + .feed-brand + .top-actions
        AnimatedVisibility(
            visible = !peeking,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.56f), Color.Transparent),
                        ),
                    )
                    .statusBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 10.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (onBack != null || communityId != null) {
                        SaigonIconButton(
                            onClick = { onBack?.invoke() ?: onNavigate(AppRoute.Home) },
                            overlay = true,
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = Copy.text(lang, "back"),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }

                    val currentPost = feedState.selectedPost
                    Column(modifier = Modifier.weight(1f)) {
                        if (communityId != null && activeCommunity != null) {
                            Text(
                                text = "${activeCommunity!!.emoji} ${activeCommunity!!.name}",
                                style = TextStyle(
                                    fontFamily = LivvicFontFamily,
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 18.sp,
                                    shadow = OverlayShadow,
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (currentPost != null && currentPost.location.isNotBlank()) {
                                Text(
                                    text = "📍 ${currentPost.location}",
                                    style = TextStyle(
                                        fontFamily = LivvicFontFamily,
                                        color = Color.White,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 14.sp,
                                        shadow = OverlayShadow,
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.clickable {
                                        val token = currentPost.locationId ?: encodeLocationToken(currentPost.location)
                                        onNavigate(AppRoute.Location(token))
                                    },
                                )
                            }
                        } else {
                            if (currentPost != null && currentPost.location.isNotBlank()) {
                                Text(
                                    text = "📍 ${currentPost.location}",
                                    style = TextStyle(
                                        fontFamily = LivvicFontFamily,
                                        color = Color.White,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 22.sp,
                                        letterSpacing = (-0.6).sp,
                                        shadow = OverlayShadow,
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.clickable {
                                        val token = currentPost.locationId ?: encodeLocationToken(currentPost.location)
                                        onNavigate(AppRoute.Location(token))
                                    },
                                )
                            } else {
                                Text(
                                    text = Copy.text(lang, "appName"),
                                    style = TextStyle(
                                        fontFamily = LivvicFontFamily,
                                        color = Color.White,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 22.sp,
                                        letterSpacing = (-0.6).sp,
                                        shadow = OverlayShadow,
                                    ),
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(top = 2.dp),
                            ) {
                                Text(
                                    text = Copy.text(lang, "appName"),
                                    style = TextStyle(
                                        fontFamily = LivvicFontFamily,
                                        color = Color.White.copy(alpha = 0.82f),
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        letterSpacing = 0.3.sp,
                                        shadow = OverlayShadow,
                                    ),
                                )
                                FeedOverlayPill(
                                    label = Copy.text(lang, "discover"),
                                    onClick = { onNavigate(AppRoute.Discover) },
                                )
                                FeedOverlayPill(
                                    label = Copy.text(lang, "shop"),
                                    onClick = { onNavigate(AppRoute.Shop) },
                                )
                            }
                        }
                    }

                    if (communityId != null && activeCommunity != null) {
                        val comm = activeCommunity!!
                        FeedOverlayPill(
                            label = Copy.text(lang, if (comm.joined) "leaveCommunity" else "joinCommunity"),
                            onClick = {
                                if (sessionState.me == null) {
                                    onRequireAuth()
                                } else {
                                    scope.launch {
                                        shared.discover.toggleCommunityMembership(comm.id, comm.joined)?.let {
                                            activeCommunity = it
                                        }
                                    }
                                }
                            },
                            height = 44.dp,
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (sessionState.me != null) {
                                Box(modifier = Modifier.size(44.dp)) {
                                    SaigonIconButton(
                                        onClick = { onNavigate(AppRoute.Messages) },
                                        overlay = true,
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.Chat,
                                            contentDescription = Copy.text(lang, "openMessages"),
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                    if (msgState.unreadCount > 0L) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .offset(x = 4.dp, y = (-4).dp)
                                                .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
                                                .clip(RoundedCornerShape(999.dp))
                                                .background(SaigonColors.NotifyRed)
                                                .border(2.dp, Color.White, RoundedCornerShape(999.dp))
                                                .padding(horizontal = 5.dp),
                                        ) {
                                            Text(
                                                text = if (msgState.unreadCount > 99) "99+" else msgState.unreadCount.toString(),
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                            )
                                        }
                                    }
                                }
                                NotificationBellButton(
                                    unreadCount = notifState.unreadCount,
                                    label = Copy.text(lang, "openNotifications"),
                                    onClick = { showNotifications = true },
                                    overlay = true,
                                )
                            }
                            SaigonIconButton(
                                onClick = { onNavigate(AppRoute.NewPost) },
                                overlay = true,
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = Copy.text(lang, "newPost"),
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clickable { onNavigate(AppRoute.Profile) },
                            ) {
                                UserAvatar(
                                    photoUrl = sessionState.me?.photoUrl,
                                    name = sessionState.me?.name ?: "?",
                                    size = 44.dp,
                                    borderColor = Color.White,
                                    borderWidth = 2.dp,
                                )
                            }
                        }
                    }
                }

                if (communityId == null && discoverState.checkIns.isNotEmpty()) {
                    CheckInBubblesRow(
                        checkIns = discoverState.checkIns,
                        onClick = { checkIn -> onNavigate(AppRoute.Post(checkIn.postId)) },
                    )
                }
            }
        }
    }

    if (showNotifications) {
        NotificationsSheet(
            store = shared.notifications,
            language = lang,
            onDismiss = { showNotifications = false },
            onNavigatePath = onNavigatePath,
        )
    }

    commentsPost?.let { post ->
        CommentsSheet(
            post = post,
            detailStore = shared.postDetail,
            viewerId = sessionState.me?.id,
            language = lang,
            onRequireAuth = onRequireAuth,
            onSelectPerson = { id, name, photoUrl, location, intro ->
                selectedPerson = SelectedPersonModal(id, name, photoUrl, location, intro)
            },
            onDismiss = { commentsPost = null },
        )
    }

    attendeesPostId?.let { postId ->
        AttendeesSheet(
            postId = postId,
            detailStore = shared.postDetail,
            language = lang,
            onSelectPerson = { person ->
                if (person.id.isNotBlank()) {
                    selectedPerson = SelectedPersonModal(
                        id = person.id,
                        name = person.name,
                        photoUrl = person.photoUrl,
                        location = person.location,
                        intro = person.intro,
                    )
                }
            },
            onDismiss = { attendeesPostId = null },
        )
    }

    pollVotersPostId?.let { postId ->
        PollVotersSheet(
            postId = postId,
            detailStore = shared.postDetail,
            language = lang,
            onSelectPerson = { person ->
                if (person.id.isNotBlank()) {
                    selectedPerson = SelectedPersonModal(
                        id = person.id,
                        name = person.name,
                        photoUrl = person.photoUrl,
                        location = person.location,
                        intro = person.intro,
                    )
                }
            },
            onDismiss = { pollVotersPostId = null },
        )
    }

    questionPost?.let { post ->
        QuestionAnswersSheet(
            post = post,
            detailStore = shared.postDetail,
            viewerId = sessionState.me?.id,
            language = lang,
            onRequireAuth = onRequireAuth,
            onSelectPerson = { id, name, photoUrl, location, intro ->
                selectedPerson = SelectedPersonModal(id, name, photoUrl, location, intro)
            },
            onDismiss = { questionPost = null },
        )
    }

    checkInPostId?.let { postId ->
        CheckInSheet(
            postId = postId,
            detailStore = shared.postDetail,
            language = lang,
            onUpdated = { shared.discover.refreshCheckIns() },
            onDismiss = { checkInPostId = null },
        )
    }

    editPost?.let { post ->
        EditPostDialog(
            post = post,
            detailStore = shared.postDetail,
            language = lang,
            onDismiss = { editPost = null },
        )
    }

    fullMessagePost?.let { post ->
        SaigonBottomSheet(
            title = post.authorName,
            onDismiss = { fullMessagePost = null },
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
            ) {
                LinkifiedText(
                    text = post.message,
                    color = tokens.text,
                    style = TextStyle(
                        fontFamily = LivvicFontFamily,
                        fontSize = 20.sp,
                        lineHeight = 27.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
            }
        }
    }

    zoomPhotoUrl?.let { url ->
        PhotoViewerDialog(photoUrl = url, onDismiss = { zoomPhotoUrl = null })
    }

    selectedPerson?.let { person ->
        MiniProfileDialog(
            name = person.name,
            photoUrl = person.photoUrl,
            location = person.location,
            intro = person.intro,
            language = lang,
            onDismiss = { selectedPerson = null },
            onOpenProfile = { onNavigate(AppRoute.User(person.id)) },
            onMessage = {
                if (sessionState.me == null) {
                    onRequireAuth()
                } else {
                    scope.launch {
                        val chat = shared.messages.openDirectChat(person.id)
                        if (chat != null) onNavigate(AppRoute.Chat(chat.id))
                    }
                }
            },
        )
    }
}

@Composable
private fun FeedOverlayPill(
    label: String,
    onClick: () -> Unit,
    height: androidx.compose.ui.unit.Dp? = null,
) {
    val shape = RoundedCornerShape(999.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .then(if (height != null) Modifier.height(height) else Modifier)
            .shadow(4.dp, shape)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.16f))
            .border(2.dp, Color.White, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp),
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontFamily = LivvicFontFamily,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 13.sp,
                shadow = OverlayShadow,
            ),
            maxLines = 1,
        )
    }
}

@Composable
private fun CheckInBubblesRow(
    checkIns: List<CheckInDto>,
    onClick: (CheckInDto) -> Unit,
) {
    val driftTransition = rememberInfiniteTransition(label = "checkInDrift")
    val driftY by driftTransition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "driftY",
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(top = 12.dp),
    ) {
        checkIns.forEach { checkIn ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .graphicsLayer { translationY = driftY }
                    .clickable { onClick(checkIn) },
            ) {
                UserAvatar(
                    photoUrl = checkIn.authorPhotoUrl,
                    name = checkIn.authorName,
                    size = 54.dp,
                    borderColor = Color.White,
                    borderWidth = 3.dp,
                    modifier = Modifier.shadow(6.dp, CircleShape),
                )
                Surface(
                    color = Color.White.copy(alpha = 0.96f),
                    shape = RoundedCornerShape(18.dp),
                    shadowElevation = 6.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 220.dp)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Text(
                            text = "${checkIn.authorName} · ${checkIn.location}",
                            color = Color(0xFF28231F),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            lineHeight = 18.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (checkIn.message.isNotBlank()) {
                            Text(
                                text = checkIn.message,
                                color = Color(0xFF28231F),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedPostSlide(
    post: PostCardDto,
    isCurrent: Boolean,
    cardParallaxPx: Float,
    peeking: Boolean,
    viewerId: String?,
    language: Language,
    audioPlaying: Boolean,
    onSetPeeking: (Boolean) -> Unit,
    onZoomPhoto: (String) -> Unit,
    onToggleMusic: () -> Unit,
    onSelectAuthor: () -> Unit,
    onOpenLocation: () -> Unit,
    onOpenCommunity: (String) -> Unit,
    onExpandMessage: () -> Unit,
    onQuickReply: (String) -> Unit,
    onOpenComments: () -> Unit,
    onToggleEventJoin: () -> Unit,
    onOpenAttendees: () -> Unit,
    onVotePoll: (Int) -> Unit,
    onOpenPollVoters: () -> Unit,
    onOpenQuestion: () -> Unit,
    onJoinOpenChat: () -> Unit,
    onCallToAction: () -> Unit,
    onOpenCheckIn: () -> Unit,
    onEditPost: () -> Unit,
    onSharePost: () -> Unit,
) {
    val tokens = LocalSaigonTokens.current
    val photos = remember(post.id, post.photoUrl, post.photoUrls) { post.allPhotoUrls() }
    val photoPagerState = rememberPagerState(pageCount = { photos.size.coerceAtLeast(1) })
    val previewTarget = remember(post.id, post.message) { previewPostMessage(post.message) }
    var visibleChars by remember(post.id, isCurrent) {
        mutableIntStateOf(if (isCurrent) 0 else previewTarget.length)
    }

    LaunchedEffect(post.id, isCurrent) {
        if (!isCurrent) {
            visibleChars = previewTarget.length
            return@LaunchedEffect
        }
        visibleChars = 0
        for (i in 1..previewTarget.length) {
            delay(Limits.TYPEWRITER_MILLIS_PER_CHAR.toLong())
            visibleChars = i
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(post.id) {
                detectTapGestures(
                    onTap = {
                        if (peeking) onSetPeeking(false)
                        else photos.getOrNull(photoPagerState.currentPage)?.let(onZoomPhoto)
                    },
                    onLongPress = { onSetPeeking(true) },
                    onPress = {
                        tryAwaitRelease()
                        if (peeking) onSetPeeking(false)
                    },
                )
            },
    ) {
        HorizontalPager(
            state = photoPagerState,
            modifier = Modifier.fillMaxSize(),
        ) { photoIdx ->
            val url = photos.getOrNull(photoIdx).orEmpty()
            AsyncImage(
                model = url,
                contentDescription = post.message,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        AnimatedVisibility(
            visible = !peeking,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .graphicsLayer { translationY = cardParallaxPx },
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(),
                ) {
                    // .slide-meta (photo-dots + music-line above card)
                    if (photos.size > 1 || !post.musicUrl.isNullOrBlank()) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 18.dp, end = 18.dp, bottom = 26.dp),
                        ) {
                            if (photos.size > 1) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.align(Alignment.CenterHorizontally),
                                ) {
                                    photos.indices.forEach { idx ->
                                        val selected = idx == photoPagerState.currentPage
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .scale(if (selected) 1.2f else 1f)
                                                .clip(CircleShape)
                                                .background(
                                                    if (selected) Color.White else Color.White.copy(alpha = 0.4f),
                                                ),
                                        )
                                    }
                                }
                            }
                            if (!post.musicUrl.isNullOrBlank()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.clickable(onClick = onToggleMusic),
                                ) {
                                    Icon(
                                        Icons.Default.MusicNote,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text(
                                        text = post.musicName.orEmpty().ifBlank { Copy.text(language, "yourMusic") },
                                        style = TextStyle(
                                            fontFamily = LivvicFontFamily,
                                            color = Color.White,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 14.sp,
                                            shadow = OverlayShadow,
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false),
                                    )
                                    Icon(
                                        if (audioPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = Copy.text(language, if (audioPlaying) "pause" else "preview"),
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                    }

                    // Cream .card with .avatar behind top-right and overlapping .name-chip at top = -16.dp
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp),
                    ) {
                        if (!post.avatarUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = post.avatarUrl,
                                contentDescription = post.authorName,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .heightIn(max = 200.dp)
                                    .widthIn(max = 200.dp)
                                    .graphicsLayer {
                                        translationY = -size.height * 0.80f
                                    }
                                    .zIndex(0f),
                            )
                        }
                        Surface(
                            color = SaigonColors.Card,
                            contentColor = SaigonColors.Ink,
                            shape = RoundedCornerShape(22.dp),
                            shadowElevation = 12.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 148.dp)
                                .zIndex(1f),
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 14.dp),
                            ) {
                                // Community chip + post message (.message)
                                if (!post.communityId.isNullOrBlank() && !post.communityName.isNullOrBlank()) {
                                    val chipBg = if (tokens.isDark) Color(0xDB161311) else Color(0xFFFFFDF8)
                                    val chipFg = if (tokens.isDark) Color(0xFFF6F1EA) else SaigonColors.Ink
                                    val chipBorder = if (tokens.isDark) Color(0x59F6F1EA) else SaigonColors.Ink.copy(alpha = 0.16f)
                                    Surface(
                                        color = chipBg,
                                        shape = RoundedCornerShape(999.dp),
                                        border = BorderStroke(1.dp, chipBorder),
                                        modifier = Modifier.clickable { onOpenCommunity(post.communityId!!) },
                                    ) {
                                        Text(
                                            text = "${post.communityEmoji.orEmpty()} ${post.communityName}".trim(),
                                            color = chipFg,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        )
                                    }
                                }

                                if (post.message.isNotBlank()) {
                                    val shownText = previewTarget.take(visibleChars.coerceIn(0, previewTarget.length))
                                    val age = PostAge.relative(post.createdAt, currentTimeMillis(), language)
                                    val needsMore = postMessageNeedsMore(post.message) && visibleChars >= previewTarget.length
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .then(if (needsMore) Modifier.clickable(onClick = onExpandMessage) else Modifier),
                                    ) {
                                        LinkifiedText(
                                            text = shownText,
                                            color = SaigonColors.Ink,
                                            linkColor = SaigonColors.Accent,
                                            style = TextStyle(
                                                fontFamily = LivvicFontFamily,
                                                fontSize = 20.sp,
                                                lineHeight = 27.sp,
                                                fontWeight = FontWeight.Bold,
                                            ),
                                        )
                                        val metaLine = buildAnnotatedString {
                                            if (needsMore) {
                                                withStyle(SpanStyle(color = tokens.muted, fontWeight = FontWeight.Bold)) {
                                                    append("… ${Copy.text(language, "seeMore")}")
                                                }
                                            }
                                            if (!age.isNullOrBlank()) {
                                                if (needsMore) append(" ")
                                                withStyle(SpanStyle(color = SaigonColors.Ink.copy(alpha = 0.5f), fontWeight = FontWeight.SemiBold)) {
                                                    append(age)
                                                }
                                            }
                                            if (post.communityOnly) {
                                                append(" ⭐")
                                            }
                                        }
                                        if (metaLine.isNotEmpty()) {
                                            Text(
                                                text = metaLine,
                                                fontSize = 14.sp,
                                                modifier = Modifier.padding(top = 2.dp),
                                            )
                                        }
                                    }
                                }

                                // Embedded Event Card (.event-card)
                                if (post.hasEvent) {
                                    Surface(
                                        color = SaigonColors.EmbeddedCardBg,
                                        shape = RoundedCornerShape(16.dp),
                                        border = BorderStroke(1.dp, SaigonColors.Ink),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            modifier = Modifier.padding(start = 14.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                                        ) {
                                            Column(
                                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clickable(onClick = onOpenAttendees),
                                            ) {
                                                Text(
                                                    "📅 ${Copy.text(language, "events")}",
                                                    color = SaigonColors.Ink,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold,
                                                )
                                                Text(
                                                    "${post.attendeeCount} ${Copy.text(language, "going")}",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = tokens.muted,
                                                )
                                            }
                                            val isLeave = post.joined
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier
                                                    .defaultMinSize(minWidth = 88.dp, minHeight = 48.dp)
                                                    .clip(RoundedCornerShape(32.dp))
                                                    .background(if (isLeave) Color.Transparent else SaigonColors.Accent)
                                                    .clickable(onClick = onToggleEventJoin)
                                                    .padding(horizontal = 18.dp, vertical = 12.dp),
                                            ) {
                                                Text(
                                                    text = Copy.text(language, if (isLeave) "leaveEvent" else "joinEvent"),
                                                    color = if (isLeave) SaigonColors.Accent else Color.White,
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                )
                                            }
                                        }
                                    }
                                }

                                // Embedded Poll Card (.poll-card)
                                if (post.hasPoll) {
                                    val totalVotes = post.pollOptions.sumOf { it.voteCount }.coerceAtLeast(1)
                                    Surface(
                                        color = SaigonColors.EmbeddedCardBg,
                                        shape = RoundedCornerShape(16.dp),
                                        border = BorderStroke(1.dp, SaigonColors.Ink),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.padding(12.dp),
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.fillMaxWidth(),
                                            ) {
                                                Text(
                                                    Copy.text(language, "poll"),
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = tokens.muted,
                                                    modifier = Modifier.weight(1f),
                                                )
                                                Text(
                                                    Copy.text(language, "pollVoters"),
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = SaigonColors.Accent,
                                                    modifier = Modifier.clickable(onClick = onOpenPollVoters),
                                                )
                                            }
                                            post.pollOptions.forEach { option ->
                                                val fraction = (option.voteCount.toFloat() / totalVotes.toFloat()).coerceIn(0f, 1f)
                                                val borderColor = if (option.voted) SaigonColors.Accent else SaigonColors.Ink
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                                                        .clickable { onVotePoll(option.index) },
                                                ) {
                                                    if (option.voteCount > 0) {
                                                        Box(
                                                            modifier = Modifier
                                                                .matchParentSize()
                                                                .fillMaxWidth(fraction)
                                                                .background(SaigonColors.PollFill),
                                                        )
                                                    }
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                                    ) {
                                                        Text(
                                                            option.text,
                                                            color = SaigonColors.Ink,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 14.sp,
                                                            modifier = Modifier.weight(1f),
                                                        )
                                                        Text(
                                                            "${option.voteCount}",
                                                            color = SaigonColors.Ink,
                                                            fontWeight = FontWeight.ExtraBold,
                                                            fontSize = 14.sp,
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // Embedded Question / Open Chat / CTA cards (.question-card, .open-chat-card, .cta-card)
                                if (post.hasQuestion) {
                                    Surface(
                                        color = SaigonColors.EmbeddedCardBg,
                                        shape = RoundedCornerShape(16.dp),
                                        border = BorderStroke(1.dp, SaigonColors.Ink),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            modifier = Modifier.padding(12.dp),
                                        ) {
                                            Text(
                                                post.questionTitle.ifBlank { Copy.text(language, "askQuestion") },
                                                color = SaigonColors.Ink,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                modifier = Modifier.weight(1f),
                                            )
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier
                                                    .defaultMinSize(minHeight = 44.dp)
                                                    .clip(RoundedCornerShape(999.dp))
                                                    .background(SaigonColors.Accent)
                                                    .clickable(onClick = onOpenQuestion)
                                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                            ) {
                                                Text(
                                                    "${Copy.text(language, "send")} (${post.answerCount})",
                                                    color = Color.White,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                )
                                            }
                                        }
                                    }
                                }

                                if (post.hasOpenChat) {
                                    Surface(
                                        color = SaigonColors.EmbeddedCardBg,
                                        shape = RoundedCornerShape(16.dp),
                                        border = BorderStroke(1.dp, SaigonColors.Ink),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            modifier = Modifier.padding(start = 14.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    post.openChatName.orEmpty().ifBlank { Copy.text(language, "openChat") },
                                                    color = SaigonColors.Ink,
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                )
                                                Text(
                                                    Copy.text(language, "openChat"),
                                                    color = tokens.muted,
                                                    fontSize = 13.sp,
                                                )
                                            }
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier
                                                    .defaultMinSize(minWidth = 88.dp, minHeight = 48.dp)
                                                    .clip(RoundedCornerShape(32.dp))
                                                    .background(SaigonColors.Accent)
                                                    .clickable(onClick = onJoinOpenChat)
                                                    .padding(horizontal = 18.dp, vertical = 12.dp),
                                            ) {
                                                Text(
                                                    Copy.text(language, "joinChat"),
                                                    color = Color.White,
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                )
                                            }
                                        }
                                    }
                                }

                                if (post.hasCta) {
                                    Surface(
                                        color = SaigonColors.EmbeddedCardBg,
                                        shape = RoundedCornerShape(16.dp),
                                        border = BorderStroke(1.dp, SaigonColors.Ink),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            modifier = Modifier.padding(start = 14.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                                        ) {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .defaultMinSize(minHeight = 48.dp)
                                                    .clip(RoundedCornerShape(32.dp))
                                                    .background(SaigonColors.Accent)
                                                    .clickable(onClick = onCallToAction)
                                                    .padding(horizontal = 20.dp, vertical = 12.dp),
                                            ) {
                                                Text(
                                                    post.ctaText,
                                                    color = Color.White,
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                )
                                            }
                                        }
                                    }
                                }

                                // Quick replies (.quick-replies -> .quick-chip)
                                if (post.quickReplies.isNotEmpty()) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                    ) {
                                        post.quickReplies.forEach { reply ->
                                            Surface(
                                                color = Color.White,
                                                shape = RoundedCornerShape(999.dp),
                                                border = BorderStroke(1.dp, SaigonColors.LightLine),
                                                modifier = Modifier.clickable { onQuickReply(reply) },
                                            ) {
                                                Text(
                                                    reply,
                                                    color = SaigonColors.Ink,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                )
                                            }
                                        }
                                    }
                                }

                                // .card-actions row (.comment-input + .comment-count + .share-btn)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Surface(
                                        color = Color.White,
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, SaigonColors.LightLine),
                                        modifier = Modifier
                                            .weight(1f)
                                            .defaultMinSize(minHeight = 44.dp)
                                            .clickable(onClick = onOpenComments),
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 14.dp, vertical = 10.dp),
                                        ) {
                                            Text(
                                                Copy.text(language, "comments"),
                                                color = SaigonColors.PlaceholderInk,
                                                fontSize = 15.sp,
                                            )
                                            if (post.commentCount > 0) {
                                                Box(
                                                    contentAlignment = Alignment.Center,
                                                    modifier = Modifier
                                                        .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
                                                        .clip(RoundedCornerShape(999.dp))
                                                        .background(SaigonColors.Accent)
                                                        .padding(horizontal = 6.dp),
                                                ) {
                                                    Text(
                                                        "${post.commentCount}",
                                                        color = Color.White,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.ExtraBold,
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    CardSquareActionButton(
                                        onClick = onOpenLocation,
                                        contentDescription = post.location,
                                        icon = Icons.Default.LocationOn,
                                    )
                                    CardSquareActionButton(
                                        onClick = onSharePost,
                                        contentDescription = Copy.text(language, "share"),
                                        icon = Icons.Default.Share,
                                    )
                                    if (viewerId != null && viewerId == post.authorId) {
                                        CardSquareActionButton(
                                            onClick = onOpenCheckIn,
                                            contentDescription = Copy.text(language, "checkInDialogTitle"),
                                            icon = Icons.Default.Schedule,
                                        )
                                        CardSquareActionButton(
                                            onClick = onEditPost,
                                            contentDescription = Copy.text(language, "edit"),
                                            icon = Icons.Default.Edit,
                                        )
                                    }
                                }
                            }
                        }

                        // Overlapping .name-chip at top = -16.dp, start = 16.dp
                        val hasFace = !post.authorPhotoUrl.isNullOrBlank()
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .zIndex(2f)
                                .padding(start = 16.dp, end = 16.dp)
                                .offset(y = (-16).dp)
                                .shadow(3.dp, RoundedCornerShape(999.dp))
                                .clip(RoundedCornerShape(999.dp))
                                .background(SaigonBrushes.primaryGradient)
                                .clickable(onClick = onSelectAuthor)
                                .padding(
                                    start = if (hasFace) 4.dp else 16.dp,
                                    end = if (hasFace) 14.dp else 16.dp,
                                    top = if (hasFace) 4.dp else 6.dp,
                                    bottom = if (hasFace) 4.dp else 6.dp,
                                ),
                        ) {
                            if (hasFace) {
                                UserAvatar(
                                    photoUrl = post.authorPhotoUrl,
                                    name = post.authorName,
                                    size = 28.dp,
                                    borderWidth = 0.dp,
                                )
                            }
                            Text(
                                text = post.authorName,
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CardSquareActionButton(
    onClick: () -> Unit,
    contentDescription: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, SaigonColors.LightLine),
        modifier = Modifier
            .size(44.dp)
            .clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = contentDescription,
                tint = SaigonColors.Accent,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
