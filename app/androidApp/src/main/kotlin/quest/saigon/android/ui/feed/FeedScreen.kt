package quest.saigon.android.ui.feed

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.ChatBubble
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import quest.saigon.android.media.AudioPreviewPlayer
import quest.saigon.android.theme.LivvicFontFamily
import quest.saigon.android.theme.LocalSaigonTokens
import quest.saigon.android.theme.SaigonBrushes
import quest.saigon.android.theme.SaigonColors
import quest.saigon.android.ui.components.AndroidIntents
import quest.saigon.android.ui.components.CheckInBubbleItem
import quest.saigon.android.ui.components.CheckInDetailDialog
import quest.saigon.android.ui.components.FirstPostBubble
import quest.saigon.android.ui.components.LinkifiedText
import quest.saigon.android.ui.components.MiniProfileDialog
import quest.saigon.android.ui.components.NotificationBellButton
import quest.saigon.android.ui.components.NotificationsSheet
import quest.saigon.android.ui.components.PhotoViewerDialog
import quest.saigon.android.ui.components.SaigonBottomSheet
import quest.saigon.android.ui.components.SaigonIconButton
import quest.saigon.android.ui.components.SaigonPrimaryButton
import quest.saigon.android.ui.components.SaigonSpinner
import quest.saigon.android.ui.components.SaigonTextField
import quest.saigon.android.ui.components.UserAvatar
import quest.saigon.android.ui.components.ViewerButton
import quest.saigon.android.ui.components.formatVnd
import quest.saigon.android.ui.components.openLocationDestination
import quest.saigon.android.ui.components.rememberDirectMessageOpener
import quest.saigon.feedMotion
import quest.saigon.postMessageNeedsMore
import quest.saigon.previewPostMessage
import quest.saigon.shared.TownSharedClient
import quest.saigon.shared.model.CommunityDto
import quest.saigon.shared.model.CheckInDto
import quest.saigon.shared.model.PostCardDto
import quest.saigon.shared.platform.currentTimeMillis
import quest.saigon.shared.store.FeedFilter
import town.saigon.BuildConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.ceil

private data class SelectedPersonModal(
    val id: String,
    val name: String,
    val photoUrl: String?,
    val location: String,
    val intro: String,
)

private data class CommentsSheetRequest(
    val post: PostCardDto,
    val initialDraft: String = "",
    val focusInput: Boolean = false,
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
    var commentsRequest by remember { mutableStateOf<CommentsSheetRequest?>(null) }
    var openedFocusCommentsPostId by remember(focusPostId) { mutableStateOf<String?>(null) }
    var attendeesPostId by remember { mutableStateOf<String?>(null) }
    var pollVotersPostId by remember { mutableStateOf<String?>(null) }
    var questionPost by remember { mutableStateOf<PostCardDto?>(null) }
    var checkInPostId by remember { mutableStateOf<String?>(null) }
    var activeCheckIn by remember { mutableStateOf<CheckInDto?>(null) }
    var showCommunityMembers by remember { mutableStateOf(false) }
    var fullMessagePost by remember { mutableStateOf<PostCardDto?>(null) }
    var zoomPhotoUrl by remember { mutableStateOf<String?>(null) }
    var selectedPerson by remember { mutableStateOf<SelectedPersonModal?>(null) }
    var miniProfileCommunities by remember { mutableStateOf<List<CommunityDto>>(emptyList()) }
    var activeCommunity by remember { mutableStateOf<quest.saigon.shared.model.CommunityDto?>(null) }
    var peeking by remember { mutableStateOf(false) }
    var feedMusicEnabled by remember { mutableStateOf(false) }
    var feedScrolling by remember { mutableStateOf(false) }
    var checkInBubblesHidden by remember { mutableStateOf(false) }
    val openDirectMessage = rememberDirectMessageOpener(
        shared = shared,
        onNavigate = onNavigate,
        onRequireAuth = onRequireAuth,
    )

    LaunchedEffect(feedScrolling) {
        if (feedScrolling) {
            checkInBubblesHidden = true
        } else if (checkInBubblesHidden) {
            delay(5_000L)
            checkInBubblesHidden = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            audioPlayer.stop()
        }
    }

    val filter = remember(communityId) {
        if (!communityId.isNullOrBlank()) FeedFilter.Community(communityId) else FeedFilter.Home
    }

    LaunchedEffect(filter, focusPostId, sessionState.me?.id) {
        shared.feed.loadInitial(filter, focusPostId)
        if (!communityId.isNullOrBlank()) {
            val res = shared.api.getCommunity(communityId)
            if (res is quest.saigon.shared.network.ApiResult.Success) {
                activeCommunity = res.value
            }
        } else {
            activeCommunity = null
            shared.discover.refreshCheckIns()
            shared.discover.loadSummary()
        }
    }

    LaunchedEffect(focusPostId, feedState.posts) {
        if (focusPostId.isNullOrBlank() || openedFocusCommentsPostId == focusPostId) return@LaunchedEffect
        val post = feedState.posts.firstOrNull { it.id == focusPostId } ?: return@LaunchedEffect
        openedFocusCommentsPostId = focusPostId
        commentsRequest = CommentsSheetRequest(post)
    }

    LaunchedEffect(selectedPerson?.id) {
        miniProfileCommunities = emptyList()
        val userId = selectedPerson?.id?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        val result = shared.api.getUserCommunities(userId)
        if (result is quest.saigon.shared.network.ApiResult.Success) {
            miniProfileCommunities = result.value.communities
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

                val isNotAtTopOfFeed = pagerState.currentPage > 0 ||
                    pagerState.settledPage > 0 ||
                    abs(pagerState.currentPageOffsetFraction) > 0.01f
                BackHandler(enabled = isNotAtTopOfFeed) {
                    scope.launch {
                        pagerState.animateScrollToPage(0)
                    }
                    shared.feed.refresh()
                    if (communityId.isNullOrBlank()) {
                        shared.discover.refreshCheckIns()
                    }
                }

                LaunchedEffect(pagerState.isScrollInProgress) {
                    feedScrolling = pagerState.isScrollInProgress
                }
                DisposableEffect(Unit) {
                    onDispose { feedScrolling = false }
                }

                LaunchedEffect(pagerState.settledPage, feedState.posts) {
                    val post = feedState.posts.getOrNull(pagerState.settledPage)
                    if (post != null) {
                        shared.feed.selectPost(post.id)
                        if (feedMusicEnabled) {
                            val url = post.musicUrl?.takeIf { it.isNotBlank() }
                            if (url != null) {
                                audioPlayer.play(url)
                            } else {
                                audioPlayer.stop()
                            }
                        }
                    }
                }

                LaunchedEffect(pagerState.currentPage, pagerState.settledPage, feedState.posts.size, feedState.nextCursor) {
                    val nearEnd = maxOf(pagerState.currentPage, pagerState.settledPage) >=
                        (feedState.posts.lastIndex - 2).coerceAtLeast(0)
                    if (nearEnd && feedState.nextCursor != null) {
                        shared.feed.loadMore()
                    }
                }

                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val stageHeightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
                    VerticalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                    ) { page ->
                        val post = feedState.posts[page]
                        val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                        val motion = feedMotion(-pageOffset.toDouble() * stageHeightPx)
                        val cardParallaxPx = (motion.cardOffset - motion.photoOffset).toFloat()
                        FeedPostSlide(
                            post = post,
                            isCurrent = page == pagerState.settledPage,
                            cardParallaxPx = cardParallaxPx,
                        peeking = peeking,
                        viewerId = sessionState.me?.id,
                        language = lang,
                        audioPlaying = audioState.currentUrl == post.musicUrl && audioState.isPlaying,
                        onSetPeeking = { peeking = it },
                        onZoomPhoto = { zoomPhotoUrl = it },
                        onToggleMusic = {
                            val url = post.musicUrl?.takeIf { it.isNotBlank() }
                            if (url != null) {
                                val playingThis = audioState.currentUrl == url && audioState.isPlaying
                                if (playingThis) {
                                    feedMusicEnabled = false
                                    audioPlayer.stop()
                                } else {
                                    feedMusicEnabled = true
                                    audioPlayer.play(url)
                                }
                            }
                        },
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
                            val token = openLocationDestination(
                                shared = shared,
                                name = post.location,
                                placeId = post.placeId,
                                locationId = post.locationId,
                                lat = post.lat,
                                lng = post.lng,
                            )
                            onNavigate(AppRoute.Location(token))
                        },
                        onMessageAuthor = {
                            if (post.authorId.isNotBlank()) {
                                openDirectMessage(post.authorId, post.authorName)
                            }
                        },
                        onOpenCommunity = { cId -> onNavigate(AppRoute.Community(cId)) },
                        onExpandMessage = { fullMessagePost = post },
                        onQuickReply = { replyText ->
                            if (sessionState.me == null) {
                                onRequireAuth()
                            } else {
                                commentsRequest = CommentsSheetRequest(
                                    post = post,
                                    initialDraft = replyText,
                                    focusInput = true,
                                )
                            }
                        },
                        onOpenComments = { focusInput ->
                            if (focusInput && sessionState.me == null) {
                                onRequireAuth()
                            } else {
                                commentsRequest = CommentsSheetRequest(
                                    post = post,
                                    initialDraft = "",
                                    focusInput = focusInput,
                                )
                            }
                        },
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
                        onAnswerQuestion = { answerText ->
                            if (sessionState.me == null) {
                                onRequireAuth()
                            } else {
                                scope.launch { shared.postDetail.answerQuestion(post.id, answerText) }
                            }
                        },
                        onOpenQuestionAnswers = { questionPost = post },
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
                                    if (post.authorId.isNotBlank()) {
                                        openDirectMessage(post.authorId, post.authorName)
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
                        onSharePost = {
                            shared.postDetail.sharePost(post.id)
                            val origin = BuildConfig.SERVER_ORIGIN.trimEnd('/')
                            AndroidIntents.shareText(context, "$origin/p/${post.id}", post.authorName)
                        },
                    )
                    }
                    if (feedState.isLoadingMore && pagerState.currentPage >= feedState.posts.lastIndex) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                                .padding(bottom = 8.dp),
                        ) {
                            SaigonSpinner(size = 20.dp)
                        }
                    }
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
            val currentPost = feedState.selectedPost
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.56f), Color.Transparent),
                        ),
                    )
                    .statusBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 10.dp),
            ) {
                if (communityId != null && activeCommunity != null) {
                    val comm = activeCommunity!!
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${comm.emoji} ${comm.name}",
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
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            val token = openLocationDestination(
                                                shared = shared,
                                                name = currentPost.location,
                                                placeId = currentPost.placeId,
                                                locationId = currentPost.locationId,
                                                lat = currentPost.lat,
                                                lng = currentPost.lng,
                                            )
                                            onNavigate(AppRoute.Location(token))
                                        },
                                )
                            }
                        }
                        ViewerButton(
                            me = sessionState.me,
                            language = lang,
                            toComposer = true,
                            overlay = true,
                            showSignedOutBubble = false,
                            onClick = { onNavigate(AppRoute.NewPost) },
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    ) {
                        val membersLabel = buildString {
                            append(Copy.text(lang, "communityMembers"))
                            if (comm.memberCount > 0) append(" · ${comm.memberCount}")
                        }
                        FeedOverlayPill(
                            label = membersLabel,
                            onClick = { showCommunityMembers = true },
                            height = 40.dp,
                        )
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
                            height = 40.dp,
                        )
                    }
                } else {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        val rightReserve = if (sessionState.me != null) 156.dp else 56.dp
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = rightReserve),
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
                            Column(modifier = Modifier.weight(1f)) {
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
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                val token = openLocationDestination(
                                                    shared = shared,
                                                    name = currentPost.location,
                                                    placeId = currentPost.placeId,
                                                    locationId = currentPost.locationId,
                                                    lat = currentPost.lat,
                                                    lng = currentPost.lng,
                                                )
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
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                        }

                        Column(
                            horizontalAlignment = Alignment.End,
                            modifier = Modifier.align(Alignment.TopEnd),
                        ) {
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
                                                Icons.Outlined.ChatBubble,
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
                                                    .size(if (msgState.unreadCount > 99L) 26.dp else 22.dp)
                                                    .clip(CircleShape)
                                                    .background(SaigonColors.NotifyRed)
                                                    .border(2.dp, Color.White, CircleShape),
                                            ) {
                                                Text(
                                                    text = if (msgState.unreadCount > 99) "99+" else msgState.unreadCount.toString(),
                                                    color = Color.White,
                                                    fontSize = if (msgState.unreadCount > 99L) 9.sp else 11.sp,
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
                                ViewerButton(
                                    me = sessionState.me,
                                    language = lang,
                                    toComposer = true,
                                    overlay = true,
                                    showSignedOutBubble = false,
                                    onClick = { onNavigate(AppRoute.NewPost) },
                                )
                            }
                            if (communityId == null && sessionState.me == null) {
                                FirstPostBubble(
                                    language = lang,
                                    onClick = { onNavigate(AppRoute.NewPost) },
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                            }
                        }
                    }

                    if (communityId == null) {
                        EventsSummaryRotatingButton(
                            today = discoverState.summary.today,
                            tomorrow = discoverState.summary.tomorrow,
                            week = discoverState.summary.week,
                            language = lang,
                            onClick = { onNavigate(AppRoute.Discover) },
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(top = 10.dp),
                        )
                    }
                }

                if (communityId == null && discoverState.checkIns.isNotEmpty()) {
                    CheckInBubblesRow(
                        checkIns = discoverState.checkIns,
                        language = lang,
                        hidden = checkInBubblesHidden,
                        onClick = { checkIn -> activeCheckIn = checkIn },
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

    commentsRequest?.let { req ->
        CommentsSheet(
            post = req.post,
            detailStore = shared.postDetail,
            viewerId = sessionState.me?.id,
            isAdmin = sessionState.me?.admin == true,
            language = lang,
            initialDraft = req.initialDraft,
            focusInput = req.focusInput,
            onRequireAuth = onRequireAuth,
            onSelectPerson = { id, name, photoUrl, location, intro ->
                selectedPerson = SelectedPersonModal(id, name, photoUrl, location, intro)
            },
            onDismiss = { commentsRequest = null },
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
            onSelectPerson = { id, name, photoUrl, location, intro ->
                selectedPerson = SelectedPersonModal(id, name, photoUrl, location, intro)
            },
            onMessageUser = { userId, name ->
                questionPost = null
                openDirectMessage(userId, name)
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

    activeCheckIn?.let { checkIn ->
        CheckInDetailDialog(
            checkIn = checkIn,
            shared = shared,
            language = lang,
            onNavigate = onNavigate,
            onRequireAuth = onRequireAuth,
            onChanged = { shared.discover.refreshCheckIns() },
            onDismiss = { activeCheckIn = null },
        )
    }

    if (showCommunityMembers && activeCommunity != null) {
        CommunityMembersSheet(
            community = activeCommunity!!,
            shared = shared,
            language = lang,
            onCommunityUpdated = { activeCommunity = it },
            onSelectMember = { member ->
                showCommunityMembers = false
                if (member.id.isNotBlank()) {
                    selectedPerson = SelectedPersonModal(
                        id = member.id,
                        name = member.name,
                        photoUrl = member.photoUrl,
                        location = member.location,
                        intro = member.intro,
                    )
                }
            },
            onDismiss = { showCommunityMembers = false },
        )
    }

    fullMessagePost?.let { targetPost ->
        val post = feedState.posts.firstOrNull { it.id == targetPost.id } ?: targetPost
        SaigonBottomSheet(
            title = post.authorName,
            onDismiss = { fullMessagePost = null },
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
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
                PostAddonsColumn(
                    post = post,
                    viewerId = sessionState.me?.id,
                    language = lang,
                    onOpenLocation = {
                        fullMessagePost = null
                        val token = openLocationDestination(
                            shared = shared,
                            name = post.location,
                            placeId = post.placeId,
                            locationId = post.locationId,
                            lat = post.lat,
                            lng = post.lng,
                        )
                        onNavigate(AppRoute.Location(token))
                    },
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
                    onOpenAttendees = {
                        fullMessagePost = null
                        attendeesPostId = post.id
                    },
                    onVotePoll = { idx ->
                        if (sessionState.me == null) {
                            onRequireAuth()
                        } else {
                            scope.launch { shared.postDetail.votePoll(post.id, idx) }
                        }
                    },
                    onOpenPollVoters = {
                        fullMessagePost = null
                        pollVotersPostId = post.id
                    },
                    onAnswerQuestion = { answerText ->
                        if (sessionState.me == null) {
                            onRequireAuth()
                        } else {
                            scope.launch { shared.postDetail.answerQuestion(post.id, answerText) }
                        }
                    },
                    onOpenQuestionAnswers = {
                        fullMessagePost = null
                        questionPost = post
                    },
                    onJoinOpenChat = {
                        if (sessionState.me == null) {
                            onRequireAuth()
                        } else {
                            scope.launch {
                                val chat = shared.postDetail.joinOpenChat(post.id)
                                if (chat != null) {
                                    fullMessagePost = null
                                    onNavigate(AppRoute.Chat(chat.id))
                                }
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
                                if (post.authorId.isNotBlank()) {
                                    fullMessagePost = null
                                    openDirectMessage(post.authorId, post.authorName)
                                }
                            }
                            CallToAction.PROFILE -> if (post.authorId.isNotBlank()) {
                                fullMessagePost = null
                                onNavigate(AppRoute.User(post.authorId))
                            }
                            CallToAction.PRODUCT -> post.ctaUrl?.takeIf { it.isNotBlank() }?.let {
                                fullMessagePost = null
                                onNavigate(AppRoute.Product(it))
                            }
                        }
                    },
                )
            }
        }
    }

    zoomPhotoUrl?.let { url ->
        PhotoViewerDialog(photoUrl = url, onDismiss = { zoomPhotoUrl = null })
    }

    selectedPerson?.let { person ->
        val canMessage = person.id.isNotBlank() && person.id != sessionState.me?.id
        MiniProfileDialog(
            name = person.name,
            photoUrl = person.photoUrl,
            location = person.location,
            intro = person.intro,
            communities = miniProfileCommunities,
            language = lang,
            onDismiss = { selectedPerson = null },
            onOpenProfile = { onNavigate(AppRoute.User(person.id)) },
            onOpenCommunity = { onNavigate(AppRoute.Community(it)) },
            onMessage = if (canMessage) {
                { openDirectMessage(person.id, person.name) }
            } else {
                null
            },
        )
    }
}

@Composable
private fun EventsSummaryRotatingButton(
    today: Long,
    tomorrow: Long,
    week: Long,
    language: Language,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val eventsLabel = remember(today, tomorrow, week, language) {
        "📅 " + when {
            today == 1L -> Copy.text(language, "eventToday")
            today > 1L -> Copy.text(language, "eventsToday", mapOf("count" to today.toString()))
            tomorrow == 1L -> Copy.text(language, "eventTomorrow")
            tomorrow > 1L -> Copy.text(language, "eventsTomorrow", mapOf("count" to tomorrow.toString()))
            week == 1L -> Copy.text(language, "eventWeek")
            week > 1L -> Copy.text(language, "eventsWeek", mapOf("count" to week.toString()))
            else -> Copy.text(language, "noUpcomingEvents")
        }
    }
    val messages = remember(eventsLabel, language) {
        listOf(
            eventsLabel,
            "🛍️ ${Copy.text(language, "searchForProducts")}",
            "🫂 ${Copy.text(language, "joinACommunity")}",
            "🙆 ${Copy.text(language, "discoverNewPeople")}",
            "♥️ ${Copy.text(language, "findThingsToDo")}",
        )
    }
    var idx by remember(messages) { mutableIntStateOf(0) }
    LaunchedEffect(messages) {
        while (true) {
            delay(3000L)
            idx = (idx + 1) % messages.size
        }
    }
    Box(modifier = modifier) {
        FeedOverlayPill(
            label = messages[idx],
            onClick = onClick,
            horizontalPadding = 18.dp,
            verticalPadding = 9.dp,
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun FeedOverlayPill(
    label: String,
    onClick: () -> Unit,
    height: androidx.compose.ui.unit.Dp? = null,
    horizontalPadding: androidx.compose.ui.unit.Dp = 12.dp,
    verticalPadding: androidx.compose.ui.unit.Dp = 5.dp,
    fontSize: androidx.compose.ui.unit.TextUnit = 13.sp,
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
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontFamily = LivvicFontFamily,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = fontSize,
                shadow = OverlayShadow,
            ),
            maxLines = 1,
        )
    }
}

@Composable
private fun CheckInBubblesRow(
    checkIns: List<CheckInDto>,
    language: Language,
    hidden: Boolean,
    onClick: (CheckInDto) -> Unit,
) {
    val bubblesAlpha by animateFloatAsState(
        targetValue = if (hidden) 0f else 1f,
        animationSpec = tween(durationMillis = 250),
        label = "checkInBubblesAlpha",
    )
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
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = bubblesAlpha }
            .padding(top = 12.dp),
    ) {
        checkIns.forEach { checkIn ->
            CheckInBubbleItem(
                checkIn = checkIn,
                language = language,
                enabled = !hidden,
                onClick = { onClick(checkIn) },
                modifier = Modifier.graphicsLayer { translationY = driftY },
            )
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
    onMessageAuthor: () -> Unit,
    onOpenCommunity: (String) -> Unit,
    onExpandMessage: () -> Unit,
    onQuickReply: (String) -> Unit,
    onOpenComments: (focusInput: Boolean) -> Unit,
    onToggleEventJoin: () -> Unit,
    onOpenAttendees: () -> Unit,
    onVotePoll: (Int) -> Unit,
    onOpenPollVoters: () -> Unit,
    onAnswerQuestion: (String) -> Unit,
    onOpenQuestionAnswers: () -> Unit,
    onJoinOpenChat: () -> Unit,
    onCallToAction: () -> Unit,
    onSharePost: () -> Unit,
) {
    val tokens = LocalSaigonTokens.current
    val scope = rememberCoroutineScope()
    val photos = remember(post.id, post.photoUrl, post.photoUrls) { post.allPhotoUrls() }
    val photoPagerState = rememberPagerState(pageCount = { photos.size.coerceAtLeast(1) })
    val previewTarget = remember(post.id, post.message) { previewPostMessage(post.message) }
    var visibleChars by remember(post.id) { mutableIntStateOf(0) }

    val quickReplies = remember(post.id, post.quickReplies, language) {
        post.quickReplies.ifEmpty { Copy.quickReplies(language) }
    }
    var cycleIdx by remember(post.id) { mutableIntStateOf(0) }
    LaunchedEffect(post.id, isCurrent, post.comments.size, quickReplies.size) {
        if (!isCurrent) return@LaunchedEffect
        while (true) {
            delay(Limits.COMMENT_CYCLE_MILLIS.toLong())
            cycleIdx += 1
        }
    }

    LaunchedEffect(post.id, isCurrent) {
        if (!isCurrent) {
            visibleChars = 0
            return@LaunchedEffect
        }
        visibleChars = 0
        for (i in 1..previewTarget.length) {
            delay(Limits.TYPEWRITER_MILLIS_PER_CHAR.toLong())
            visibleChars = i
        }
    }

    val avatarEnterEasing = remember { CubicBezierEasing(0.2f, 0.7f, 0.2f, 1f) }
    val avatarAlpha by animateFloatAsState(
        targetValue = if (isCurrent && !peeking) 1f else 0f,
        animationSpec = tween(durationMillis = 480),
        label = "avatarAlpha",
    )
    val avatarEnterOffset by animateDpAsState(
        targetValue = if (isCurrent && !peeking) 0.dp else Limits.AVATAR_ENTER_PX.dp,
        animationSpec = tween(durationMillis = 520, easing = avatarEnterEasing),
        label = "avatarEnterOffset",
    )

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        HorizontalPager(
            state = photoPagerState,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(post.id, photos.size, peeking) {
                    detectTapGestures(
                        onTap = { offset ->
                            if (photos.size >= 2) {
                                val w = size.width.toFloat().coerceAtLeast(1f)
                                val xFraction = offset.x / w
                                when {
                                    xFraction < 0.25f && photoPagerState.currentPage > 0 -> {
                                        scope.launch { photoPagerState.animateScrollToPage(photoPagerState.currentPage - 1) }
                                    }
                                    xFraction > 0.75f && photoPagerState.currentPage < photos.lastIndex -> {
                                        scope.launch { photoPagerState.animateScrollToPage(photoPagerState.currentPage + 1) }
                                    }
                                    else -> onSetPeeking(!peeking)
                                }
                            } else {
                                onSetPeeking(!peeking)
                            }
                        },
                        onLongPress = { onSetPeeking(true) },
                        onPress = {
                            val wasPeeking = peeking
                            tryAwaitRelease()
                            if (!wasPeeking && peeking) onSetPeeking(false)
                        },
                    )
                },
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
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .clickable(onClick = onToggleMusic)
                                        .padding(horizontal = 4.dp, vertical = 2.dp),
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
                                        alpha = avatarAlpha
                                        translationY = -size.height * 0.80f + avatarEnterOffset.toPx()
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
                                .pointerInput(Unit) { detectTapGestures { } }
                                .zIndex(1f),
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 14.dp),
                            ) {
                                // Message on the left + Community chip on the right (.message .community-chip.in-message)
                                val hasCommunity = !post.communityId.isNullOrBlank() && !post.communityName.isNullOrBlank()
                                val hasMessage = post.message.isNotBlank()
                                if (hasMessage || hasCommunity) {
                                    Row(
                                        verticalAlignment = Alignment.Top,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        if (hasMessage) {
                                            val shownText = previewTarget.take(visibleChars.coerceIn(0, previewTarget.length))
                                            val age = PostAge.relative(post.createdAt, currentTimeMillis(), language)
                                            val needsMore = postMessageNeedsMore(post.message) && visibleChars >= previewTarget.length
                                            Column(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(8.dp))
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
                                        } else {
                                            Spacer(Modifier.weight(1f))
                                        }
                                        if (hasCommunity) {
                                            val chipBg = if (tokens.isDark) Color(0xDB161311) else Color(0xFFFFFDF8)
                                            val chipFg = if (tokens.isDark) Color(0xFFF6F1EA) else SaigonColors.Ink
                                            val chipBorder = if (tokens.isDark) Color(0x59F6F1EA) else SaigonColors.Ink.copy(alpha = 0.16f)
                                            Surface(
                                                color = chipBg,
                                                shape = RoundedCornerShape(999.dp),
                                                border = BorderStroke(1.dp, chipBorder),
                                                modifier = Modifier
                                                    .widthIn(max = 170.dp)
                                                    .clip(RoundedCornerShape(999.dp))
                                                    .clickable { onOpenCommunity(post.communityId!!) },
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
                                    }
                                }

                                PostAddonsColumn(
                                    post = post,
                                    viewerId = viewerId,
                                    language = language,
                                    onOpenLocation = onOpenLocation,
                                    onToggleEventJoin = onToggleEventJoin,
                                    onOpenAttendees = onOpenAttendees,
                                    onVotePoll = onVotePoll,
                                    onOpenPollVoters = onOpenPollVoters,
                                    onAnswerQuestion = onAnswerQuestion,
                                    onOpenQuestionAnswers = onOpenQuestionAnswers,
                                    onJoinOpenChat = onJoinOpenChat,
                                    onCallToAction = onCallToAction,
                                )

                                // .collapsed-preview (rotating comment preview or "Be the first to give {name} a comment." + commentCount pill)
                                val previewComment = if (post.comments.isNotEmpty()) {
                                    post.comments[cycleIdx % post.comments.size]
                                } else {
                                    null
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = 20.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onOpenComments(false) },
                                ) {
                                    if (previewComment != null) {
                                        UserAvatar(
                                            photoUrl = previewComment.authorPhotoUrl,
                                            name = previewComment.authorName,
                                            size = 18.dp,
                                            borderWidth = 0.dp,
                                        )
                                        Text(
                                            text = buildAnnotatedString {
                                                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = SaigonColors.Ink)) {
                                                    append(previewComment.authorName)
                                                }
                                                append(" ")
                                                withStyle(SpanStyle(fontWeight = FontWeight.Normal, color = SaigonColors.Ink)) {
                                                    append(previewComment.text)
                                                }
                                            },
                                            fontSize = 13.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                        )
                                    } else {
                                        Text(
                                            text = Copy.text(language, "beFirstComment", mapOf("name" to post.authorName)),
                                            color = SaigonColors.Ink,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Normal,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                    if (post.commentCount > 0) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
                                                .clip(RoundedCornerShape(999.dp))
                                                .background(SaigonColors.Accent)
                                                .padding(horizontal = 6.dp),
                                        ) {
                                            Text(
                                                text = "${post.commentCount}",
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                            )
                                        }
                                    }
                                }

                                // Quick replies (.quick-replies -> .quick-chip)
                                if (quickReplies.isNotEmpty()) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                    ) {
                                        quickReplies.forEach { reply ->
                                            Surface(
                                                color = Color.White,
                                                shape = RoundedCornerShape(999.dp),
                                                border = BorderStroke(1.dp, SaigonColors.LightLine),
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(999.dp))
                                                    .clickable { onQuickReply(reply) },
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

                                // .card-actions row (.comment-input with rotating placeholder, NO count; Message author + Share)
                                val commentPlaceholder = quickReplies.getOrElse(cycleIdx % quickReplies.size) {
                                    Copy.text(language, "comments")
                                }
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
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { onOpenComments(true) },
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 14.dp, vertical = 10.dp),
                                        ) {
                                            Text(
                                                text = commentPlaceholder,
                                                color = SaigonColors.PlaceholderInk,
                                                fontSize = 15.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }

                                    if (post.authorId.isNotBlank() && post.authorId != viewerId) {
                                        CardSquareActionButton(
                                            onClick = onMessageAuthor,
                                            contentDescription = Copy.text(language, "messageUser", mapOf("name" to post.authorName)),
                                            icon = Icons.Outlined.ChatBubble,
                                        )
                                    }
                                    CardSquareActionButton(
                                        onClick = onSharePost,
                                        contentDescription = Copy.text(language, "share"),
                                        icon = Icons.Default.Share,
                                    )
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
private fun PostAddonsColumn(
    post: PostCardDto,
    viewerId: String?,
    language: Language,
    onOpenLocation: () -> Unit,
    onToggleEventJoin: () -> Unit,
    onOpenAttendees: () -> Unit,
    onVotePoll: (Int) -> Unit,
    onOpenPollVoters: () -> Unit,
    onAnswerQuestion: (String) -> Unit,
    onOpenQuestionAnswers: () -> Unit,
    onJoinOpenChat: () -> Unit,
    onCallToAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!post.hasEvent && !post.hasPoll && !post.hasQuestion && !post.hasCta && !post.hasOpenChat) return
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        if (post.hasEvent) {
            val eventStart = post.eventStartAt ?: 0L
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
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("📅", fontSize = 16.sp)
                            Column {
                                Text(
                                    text = formatFeedEventSchedule(eventStart, post.eventEndAt, language),
                                    color = SaigonColors.Ink,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                )
                                feedEventRelativeLabel(eventStart, post.eventEndAt, language)?.let { rel ->
                                    Text(
                                        text = rel,
                                        color = SaigonColors.LightMuted,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        }
                        if (post.location.isNotBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(onClick = onOpenLocation),
                            ) {
                                Text("📍", fontSize = 16.sp)
                                Text(
                                    text = post.location,
                                    color = SaigonColors.Ink,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("💰", fontSize = 15.sp)
                            Text(
                                text = post.eventPrice?.takeIf { it > 0L }?.let(::formatVnd)
                                    ?: Copy.text(language, "free"),
                                color = SaigonColors.Accent,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .clickable(onClick = onOpenAttendees),
                        ) {
                            val shownAttendees = post.attendees.take(5)
                            if (shownAttendees.isNotEmpty()) {
                                Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
                                    shownAttendees.forEach { person ->
                                        UserAvatar(
                                            photoUrl = person.photoUrl,
                                            name = person.name,
                                            size = 26.dp,
                                            borderColor = SaigonColors.Card,
                                            borderWidth = 1.5.dp,
                                        )
                                    }
                                }
                            }
                            val count = maxOf(post.attendeeCount, shownAttendees.size)
                            Text(
                                text = if (count > 0) "${Copy.text(language, "attendees")} · $count" else Copy.text(language, "attendees"),
                                color = SaigonColors.Ink,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    val isLeave = post.joined
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .defaultMinSize(minWidth = 88.dp, minHeight = 52.dp)
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
                            color = SaigonColors.LightMuted,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            Copy.text(language, "pollVoters"),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = SaigonColors.Accent,
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .clickable(onClick = onOpenPollVoters)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
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

        if (post.hasQuestion) {
            val isOwner = viewerId != null && viewerId == post.authorId
            var questionDraft by remember(post.id) { mutableStateOf("") }
            Surface(
                color = SaigonColors.EmbeddedCardBg,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, SaigonColors.Ink),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(12.dp),
                ) {
                    if (post.questionTitle.isNotBlank()) {
                        Text(
                            post.questionTitle,
                            color = SaigonColors.Ink,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                    when {
                        isOwner -> {
                            SaigonTextField(
                                value = "",
                                onValueChange = {},
                                enabled = false,
                                placeholder = Copy.text(language, "questionAnswerHint"),
                                containerColor = Color.Transparent,
                                borderColor = SaigonColors.Ink.copy(alpha = 0.55f),
                                borderWidth = 1.dp,
                                cornerRadius = 12.dp,
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 44.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .border(1.dp, SaigonColors.Ink, RoundedCornerShape(999.dp))
                                    .clickable(onClick = onOpenQuestionAnswers)
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                            ) {
                                Text(
                                    Copy.text(
                                        language,
                                        "questionResponses",
                                        mapOf("count" to post.answerCount.toString()),
                                    ),
                                    color = SaigonColors.Ink,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                )
                            }
                        }
                        post.answered -> {
                            Text(
                                Copy.text(language, "questionSent", mapOf("name" to post.authorName)),
                                color = SaigonColors.Ink,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                            )
                        }
                        else -> {
                            SaigonTextField(
                                value = questionDraft,
                                onValueChange = { if (it.length <= Limits.QUESTION_ANSWER_MAX) questionDraft = it },
                                placeholder = Copy.text(language, "questionAnswerHint"),
                                containerColor = Color.Transparent,
                                borderColor = SaigonColors.Ink,
                                borderWidth = 1.dp,
                                cornerRadius = 12.dp,
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            val canSend = questionDraft.isNotBlank()
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 44.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(
                                        if (canSend) SaigonColors.Accent else SaigonColors.Accent.copy(alpha = 0.55f),
                                    )
                                    .clickable {
                                        val trimmed = questionDraft.trim()
                                        if (trimmed.isNotEmpty()) {
                                            onAnswerQuestion(trimmed)
                                            questionDraft = ""
                                        }
                                    }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                            ) {
                                Text(
                                    Copy.text(language, "sendTo", mapOf("name" to post.authorName)),
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                )
                            }
                        }
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
                    val heading = post.ctaTitle.trim()
                    if (heading.isNotBlank()) {
                        Text(
                            text = heading,
                            color = SaigonColors.Ink,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .then(if (heading.isBlank()) Modifier.fillMaxWidth() else Modifier.defaultMinSize(minWidth = 88.dp))
                            .defaultMinSize(minHeight = 52.dp)
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
                            post.openChatName.orEmpty().ifBlank { Copy.text(language, "unnamedGroupChat") },
                            color = SaigonColors.Ink,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        post.openChatExpiresAt?.let { expiresAt ->
                            val remainingMs = expiresAt - currentTimeMillis()
                            val label = if (remainingMs <= 0L) {
                                Copy.text(language, "openChatExpired")
                            } else {
                                val hours = ceil(remainingMs / 3_600_000.0).toInt().coerceAtLeast(1)
                                val duration = Copy.text(
                                    language,
                                    if (hours == 1) "oneHour" else "hoursCount",
                                    mapOf("count" to hours.toString()),
                                )
                                "${Copy.text(language, "openChatExpiration")} $duration"
                            }
                            Text(label, color = SaigonColors.LightMuted, fontSize = 13.sp)
                        }
                        post.openChatRemaining?.let { remaining ->
                            Text(
                                Copy.text(
                                    language,
                                    "openChatSlotsRemaining",
                                    mapOf("count" to remaining.toString()),
                                ),
                                color = SaigonColors.LightMuted,
                                fontSize = 13.sp,
                            )
                        }
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
                            Copy.text(language, "openChat"),
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
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
            .clip(RoundedCornerShape(12.dp))
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

private val saigonTimeZone: TimeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")

private fun formatFeedEventSchedule(startAt: Long, endAt: Long?, language: Language): String {
    val locale = if (language == Language.VI) Locale.forLanguageTag("vi-VN") else Locale.ENGLISH
    val dateFmt = SimpleDateFormat("EEE, MMM d", locale).apply { timeZone = saigonTimeZone }
    val timeFmt = SimpleDateFormat("HH:mm", locale).apply { timeZone = saigonTimeZone }
    val dayFmt = SimpleDateFormat("yyyy-MM-dd", locale).apply { timeZone = saigonTimeZone }
    val now = currentTimeMillis()
    val oneDay = 24 * 60 * 60 * 1000L
    val dayLabel = when (dayFmt.format(Date(startAt))) {
        dayFmt.format(Date(now)) -> Copy.text(language, "today")
        dayFmt.format(Date(now + oneDay)) -> Copy.text(language, "tomorrow")
        else -> dateFmt.format(Date(startAt))
    }
    val startClock = timeFmt.format(Date(startAt))
    if (endAt == null || endAt <= startAt) return "$dayLabel, $startClock"
    val endClock = timeFmt.format(Date(endAt))
    return if (dayFmt.format(Date(startAt)) == dayFmt.format(Date(endAt))) {
        "$dayLabel, $startClock–$endClock"
    } else {
        "$dayLabel, $startClock – ${dateFmt.format(Date(endAt))}, $endClock"
    }
}

private fun feedEventRelativeLabel(startAt: Long, endAt: Long?, language: Language): String? {
    val now = currentTimeMillis()
    val diff = startAt - now
    if (diff <= 0L) {
        return if (endAt != null && endAt > now) Copy.text(language, "happeningNow") else null
    }
    val mins = (diff / 60_000L).toInt()
    if (mins < 60) {
        return Copy.text(language, "startsInMinutes", mapOf("count" to mins.coerceAtLeast(1).toString()))
    }
    val hours = (diff / 3_600_000L).toInt()
    if (hours < 24) {
        return if (hours == 1) {
            Copy.text(language, "startsInHour")
        } else {
            Copy.text(language, "startsInHours", mapOf("count" to hours.toString()))
        }
    }
    return null
}
