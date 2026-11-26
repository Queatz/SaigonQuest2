package quest.saigon.android.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import quest.saigon.Copy
import quest.saigon.PostAge
import quest.saigon.android.theme.LocalSaigonTokens
import quest.saigon.android.ui.components.LinkifiedText
import quest.saigon.android.ui.components.SaigonCard
import quest.saigon.android.ui.components.SaigonConfirmDialog
import quest.saigon.android.ui.components.SaigonPageShell
import quest.saigon.android.ui.components.SaigonPillChip
import quest.saigon.android.ui.components.SaigonSpinner
import quest.saigon.android.ui.components.SaigonTextButton
import quest.saigon.android.ui.components.SaigonTinyPill
import quest.saigon.android.ui.components.UserAvatar
import quest.saigon.shared.TownSharedClient
import quest.saigon.shared.model.AdminPostDto
import quest.saigon.shared.model.CommentCardDto
import quest.saigon.shared.model.WantDto
import quest.saigon.shared.platform.currentTimeMillis

private enum class AdminTab { USERS, POSTS, COMMENTS, WANTS }

@Composable
fun AdminScreen(
    shared: TownSharedClient,
    onBack: () -> Unit,
) {
    val lang = shared.session.state.collectAsState().value.language
    val state by shared.admin.state.collectAsState()
    val tokens = LocalSaigonTokens.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(AdminTab.USERS) }
    var pendingDeletePost by remember { mutableStateOf<AdminPostDto?>(null) }
    var pendingDeleteComment by remember { mutableStateOf<CommentCardDto?>(null) }
    var pendingDeleteWant by remember { mutableStateOf<WantDto?>(null) }

    LaunchedEffect(Unit) {
        if (shared.admin.verifyAdmin()) {
            shared.admin.loadUsers()
            shared.admin.loadPosts()
            shared.admin.loadComments()
            shared.admin.loadWants()
        }
    }

    SaigonPageShell(
        title = Copy.text(lang, "admin"),
        onBack = onBack,
        backContentDescription = Copy.text(lang, "back"),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (!state.checked || (state.isLoading && !state.isAdmin)) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    SaigonSpinner()
                }
            } else if (!state.isAdmin) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(Copy.text(lang, "notFound"), color = tokens.muted)
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                ) {
                    SaigonPillChip(selected = tab == AdminTab.USERS, onClick = { tab = AdminTab.USERS }, label = Copy.text(lang, "adminUsers"))
                    SaigonPillChip(selected = tab == AdminTab.POSTS, onClick = { tab = AdminTab.POSTS }, label = Copy.text(lang, "adminPosts"))
                    SaigonPillChip(selected = tab == AdminTab.COMMENTS, onClick = { tab = AdminTab.COMMENTS }, label = Copy.text(lang, "adminComments"))
                    SaigonPillChip(selected = tab == AdminTab.WANTS, onClick = { tab = AdminTab.WANTS }, label = Copy.text(lang, "adminWants"))
                }

                when (tab) {
                    AdminTab.USERS -> if (state.isLoading && state.users.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { SaigonSpinner() }
                    } else if (state.users.isEmpty()) {
                        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text(Copy.text(lang, "noUsers"), color = tokens.muted)
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.navigationBarsPadding(),
                        ) {
                            items(state.users, key = { it.id }) { user ->
                                SaigonCard(
                                    borderWidth = 1.dp,
                                    cornerRadius = 16.dp,
                                    contentPadding = PaddingValues(10.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        UserAvatar(photoUrl = user.photoUrl, name = user.name, size = 44.dp, borderWidth = 0.dp)
                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Text(user.name, color = tokens.text, fontWeight = FontWeight.ExtraBold)
                                                if (user.paused) {
                                                    SaigonTinyPill(label = Copy.text(lang, "paused"), danger = true)
                                                }
                                            }
                                            if (user.intro.isNotBlank()) {
                                                Text(user.intro, fontSize = 13.sp, color = tokens.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                            }
                                            Text(
                                                Copy.text(lang, "postCount", mapOf("count" to user.postCount.toString())),
                                                fontSize = 13.sp,
                                                color = tokens.muted,
                                            )
                                        }
                                        SaigonTinyPill(
                                            label = Copy.text(lang, if (user.paused) "unpauseUser" else "pauseUser"),
                                            danger = !user.paused,
                                            onClick = { scope.launch { shared.admin.setUserPaused(user.id, !user.paused) } },
                                        )
                                    }
                                }
                            }
                            if (state.usersNextCursor != null) {
                                item {
                                    SaigonTextButton(
                                        text = Copy.text(lang, "loadMore"),
                                        onClick = { shared.admin.loadUsers(loadMore = true) },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }
                    }

                    AdminTab.POSTS -> if (state.isLoading && state.posts.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { SaigonSpinner() }
                    } else if (state.posts.isEmpty()) {
                        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text(Copy.text(lang, "noPosts"), color = tokens.muted)
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.navigationBarsPadding(),
                        ) {
                            items(state.posts, key = { it.id }) { post ->
                                SaigonCard(
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
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                UserAvatar(photoUrl = post.authorPhotoUrl, name = post.authorName, size = 28.dp, borderWidth = 0.dp)
                                                Text(post.authorName, color = tokens.text, fontWeight = FontWeight.ExtraBold)
                                            }
                                            Text(post.message, color = tokens.text, maxLines = 3, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                                            if (!post.musicName.isNullOrBlank()) {
                                                Text("🎵 ${post.musicName}", color = tokens.muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                                            SaigonTinyPill(
                                                label = Copy.text(lang, "delete"),
                                                danger = true,
                                                onClick = { pendingDeletePost = post },
                                            )
                                        }
                                    }
                                }
                            }
                            if (state.postsNextCursor != null) {
                                item {
                                    SaigonTextButton(
                                        text = Copy.text(lang, "loadMore"),
                                        onClick = { shared.admin.loadPosts(loadMore = true) },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }
                    }

                    AdminTab.COMMENTS -> if (state.isLoading && state.comments.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { SaigonSpinner() }
                    } else if (state.comments.isEmpty()) {
                        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text(Copy.text(lang, "noComments"), color = tokens.muted)
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.navigationBarsPadding(),
                        ) {
                            items(state.comments, key = { it.id }) { comment ->
                                SaigonCard(
                                    borderWidth = 1.dp,
                                    cornerRadius = 16.dp,
                                    contentPadding = PaddingValues(10.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.Top,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        UserAvatar(photoUrl = comment.authorPhotoUrl, name = comment.authorName, size = 44.dp, borderWidth = 0.dp)
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            ) {
                                                Text(comment.authorName, color = tokens.text, fontWeight = FontWeight.ExtraBold)
                                                if (comment.createdAt > 0L) {
                                                    val age = PostAge.relative(comment.createdAt, currentTimeMillis(), lang)
                                                    if (!age.isNullOrBlank()) {
                                                        Text(
                                                            age,
                                                            fontSize = 12.sp,
                                                            color = tokens.muted,
                                                        )
                                                    }
                                                }
                                            }
                                            val replyText = comment.replyToText.orEmpty()
                                            if (replyText.isNotBlank()) {
                                                val quoteShape = RoundedCornerShape(topStart = 3.dp, topEnd = 8.dp, bottomEnd = 8.dp, bottomStart = 3.dp)
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(quoteShape)
                                                        .background(tokens.accent.copy(alpha = 0.1f))
                                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                                ) {
                                                    Box(Modifier.width(3.dp).height(14.dp).background(tokens.accent))
                                                    Spacer(Modifier.width(8.dp))
                                                    Text(
                                                        "@${comment.replyToAuthorName.orEmpty()}: $replyText",
                                                        fontSize = 12.sp,
                                                        color = tokens.muted,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                }
                                            }
                                            LinkifiedText(text = comment.text, color = tokens.text)
                                            val postSnippet = comment.postText.orEmpty()
                                            if (postSnippet.isNotBlank()) {
                                                val quoteShape = RoundedCornerShape(topStart = 3.dp, topEnd = 8.dp, bottomEnd = 8.dp, bottomStart = 3.dp)
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(quoteShape)
                                                        .background(tokens.bg)
                                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                                ) {
                                                    Box(Modifier.width(3.dp).height(28.dp).background(tokens.line))
                                                    Spacer(Modifier.width(8.dp))
                                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                        Text(
                                                            Copy.text(lang, "onPost").uppercase(),
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.ExtraBold,
                                                            color = tokens.muted,
                                                        )
                                                        Text(
                                                            postSnippet,
                                                            fontSize = 12.sp,
                                                            color = tokens.muted,
                                                            maxLines = 2,
                                                            overflow = TextOverflow.Ellipsis,
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        SaigonTinyPill(
                                            label = Copy.text(lang, "delete"),
                                            danger = true,
                                            onClick = { pendingDeleteComment = comment },
                                        )
                                    }
                                }
                            }
                            if (state.commentsNextCursor != null) {
                                item {
                                    SaigonTextButton(
                                        text = Copy.text(lang, "loadMore"),
                                        onClick = { shared.admin.loadComments(loadMore = true) },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }
                    }

                    AdminTab.WANTS -> if (state.isLoading && state.wants.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { SaigonSpinner() }
                    } else if (state.wants.isEmpty()) {
                        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text(Copy.text(lang, "noWants"), color = tokens.muted)
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.navigationBarsPadding(),
                        ) {
                            items(state.wants, key = { it.id }) { want ->
                                SaigonCard(
                                    borderWidth = 1.dp,
                                    cornerRadius = 16.dp,
                                    contentPadding = PaddingValues(10.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        if (!want.photoUrl.isNullOrBlank()) {
                                            AsyncImage(
                                                model = want.photoUrl,
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(width = 64.dp, height = 44.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(tokens.line),
                                            )
                                        }
                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                                            Text(want.name, color = tokens.text, fontWeight = FontWeight.ExtraBold)
                                            Text(
                                                Copy.text(lang, "wantMembersCount", mapOf("count" to want.memberCount.toString())),
                                                fontSize = 13.sp,
                                                color = tokens.muted,
                                            )
                                        }
                                        SaigonTinyPill(
                                            label = Copy.text(lang, "delete"),
                                            danger = true,
                                            onClick = { pendingDeleteWant = want },
                                        )
                                    }
                                }
                            }
                            if (state.wantsNextCursor != null) {
                                item {
                                    SaigonTextButton(
                                        text = Copy.text(lang, "loadMore"),
                                        onClick = { shared.admin.loadWants(loadMore = true) },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }
                    }
                }
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
                    if (shared.admin.deletePost(target.id)) {
                        pendingDeletePost = null
                    }
                }
            },
            onDismiss = { pendingDeletePost = null },
        )
    }

    pendingDeleteComment?.let { target ->
        SaigonConfirmDialog(
            title = Copy.text(lang, "deleteCommentAsk"),
            hint = Copy.text(lang, "deleteCommentHint"),
            confirmLabel = Copy.text(lang, "delete"),
            cancelLabel = Copy.text(lang, "cancel"),
            loading = state.isMutating,
            onConfirm = {
                scope.launch {
                    if (shared.admin.deleteComment(target.id)) {
                        pendingDeleteComment = null
                    }
                }
            },
            onDismiss = { pendingDeleteComment = null },
        )
    }

    pendingDeleteWant?.let { target ->
        SaigonConfirmDialog(
            title = Copy.text(lang, "deleteWantAsk"),
            hint = Copy.text(lang, "deleteWantHint"),
            confirmLabel = Copy.text(lang, "delete"),
            cancelLabel = Copy.text(lang, "cancel"),
            loading = state.isMutating,
            onConfirm = {
                scope.launch {
                    if (shared.admin.deleteWant(target.id)) {
                        pendingDeleteWant = null
                    }
                }
            },
            onDismiss = { pendingDeleteWant = null },
        )
    }
}
