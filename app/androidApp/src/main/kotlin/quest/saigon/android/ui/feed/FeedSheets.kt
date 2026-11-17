package quest.saigon.android.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import quest.saigon.CheckIns
import quest.saigon.Copy
import quest.saigon.Language
import quest.saigon.Limits
import quest.saigon.PostAge
import quest.saigon.android.theme.LocalSaigonTokens
import quest.saigon.android.theme.SaigonBrushes
import quest.saigon.android.ui.components.LinkifiedText
import quest.saigon.android.ui.components.SaigonBottomSheet
import quest.saigon.android.ui.components.SaigonDialog
import quest.saigon.android.ui.components.SaigonPillChip
import quest.saigon.android.ui.components.SaigonPrimaryButton
import quest.saigon.android.ui.components.SaigonSpinner
import quest.saigon.android.ui.components.SaigonTextButton
import quest.saigon.android.ui.components.SaigonTextField
import quest.saigon.android.ui.components.UserAvatar
import quest.saigon.shared.TownSharedClient
import quest.saigon.shared.model.CommentCardDto
import quest.saigon.shared.model.CommunityDto
import quest.saigon.shared.model.CommunityMemberDto
import quest.saigon.shared.model.EventPersonDto
import quest.saigon.shared.model.PostCardDto
import quest.saigon.shared.network.ApiResult
import quest.saigon.shared.platform.currentTimeMillis
import quest.saigon.shared.store.PostDetailStore

@Composable
fun CommentsSheet(
    post: PostCardDto,
    detailStore: PostDetailStore,
    viewerId: String?,
    isAdmin: Boolean = false,
    language: Language,
    initialDraft: String = "",
    focusInput: Boolean = false,
    onRequireAuth: () -> Unit,
    onSelectPerson: (id: String, name: String, photoUrl: String?, location: String, intro: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val state by detailStore.state.collectAsState()
    val tokens = LocalSaigonTokens.current
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var inputText by remember(post.id, initialDraft) { mutableStateOf(initialDraft) }
    var replyTarget by remember { mutableStateOf<CommentCardDto?>(null) }
    var pendingDeleteComment by remember { mutableStateOf<CommentCardDto?>(null) }

    LaunchedEffect(post.id, focusInput) {
        if (focusInput) {
            delay(180)
            runCatching { focusRequester.requestFocus() }
            keyboardController?.show()
        }
    }

    val replies = remember(post.id, post.quickReplies, language) {
        post.quickReplies.ifEmpty { Copy.quickReplies(language) }
    }
    var placeholderIndex by remember(post.id, replies) { mutableIntStateOf(0) }
    LaunchedEffect(post.id, replies) {
        if (replies.size > 1) {
            while (true) {
                delay(Limits.COMMENT_CYCLE_MILLIS.toLong())
                placeholderIndex = (placeholderIndex + 1) % replies.size
            }
        }
    }
    val cyclingPlaceholder = replies.getOrElse(placeholderIndex) { Copy.text(language, "emptyComments") }

    LaunchedEffect(post.id) {
        detailStore.bindPost(post)
        detailStore.openComments(post.id)
    }

    SaigonBottomSheet(
        title = Copy.text(language, "comments"),
        onDismiss = onDismiss,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            when {
                state.isLoadingComments && state.comments.isEmpty() -> {
                    Box(Modifier.fillMaxWidth().heightIn(min = 160.dp), contentAlignment = Alignment.Center) {
                        SaigonSpinner()
                    }
                }
                state.comments.isEmpty() -> {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 140.dp)
                            .padding(horizontal = 16.dp),
                    ) {
                        Text(
                            Copy.text(language, "noComments"),
                            color = tokens.muted,
                            fontSize = 14.sp,
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 140.dp, max = 360.dp),
                    ) {
                        items(state.comments, key = { it.id }) { comment ->
                            val canDelete = viewerId != null && (viewerId == comment.userId || viewerId == post.authorId || isAdmin)
                            CommentRow(
                                comment = comment,
                                canDelete = canDelete,
                                language = language,
                                onAuthorClick = {
                                    if (comment.userId.isNotBlank()) {
                                        onSelectPerson(
                                            comment.userId,
                                            comment.authorName,
                                            comment.authorPhotoUrl,
                                            comment.authorLocation,
                                            comment.authorIntro,
                                        )
                                    }
                                },
                                onReply = { replyTarget = comment },
                                onDelete = { pendingDeleteComment = comment },
                            )
                        }
                        if (state.commentsNextCursor != null) {
                            item {
                                SaigonTextButton(
                                    text = Copy.text(language, "loadMore"),
                                    onClick = { detailStore.loadMoreComments() },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = tokens.line, thickness = 1.dp)

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                replyTarget?.let { target ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(tokens.bg)
                            .border(1.dp, tokens.line, RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        Text(
                            Copy.text(language, "replyingTo", mapOf("name" to target.authorName)),
                            color = tokens.muted,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { replyTarget = null }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, contentDescription = null, tint = tokens.muted, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                if (!state.error.isNullOrBlank()) {
                    Text(
                        state.error.orEmpty(),
                        color = tokens.danger,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                    )
                }

                if (replyTarget == null && replies.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 2.dp),
                    ) {
                        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(replies) { reply ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(tokens.surface)
                                        .border(1.dp, tokens.line, RoundedCornerShape(999.dp))
                                        .clickable {
                                            inputText = reply
                                            runCatching { focusRequester.requestFocus() }
                                            keyboardController?.show()
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                ) {
                                    Text(
                                        text = reply,
                                        color = tokens.text,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    SaigonTextField(
                        value = inputText,
                        onValueChange = { if (it.length <= Limits.COMMENT_MAX) inputText = it },
                        placeholder = cyclingPlaceholder,
                        containerColor = tokens.bg,
                        borderWidth = 1.dp,
                        cornerRadius = 12.dp,
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester),
                    )
                    val sendEnabled = inputText.isNotBlank() && !state.isPostingComment
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .defaultMinSize(minHeight = 46.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(SaigonBrushes.primaryGradient, RoundedCornerShape(999.dp))
                            .border(2.dp, tokens.text.copy(alpha = 0.15f), RoundedCornerShape(999.dp))
                            .clickable(enabled = sendEnabled) {
                                if (viewerId == null) {
                                    onRequireAuth()
                                    return@clickable
                                }
                                val text = inputText.trim()
                                val replyId = replyTarget?.id
                                scope.launch {
                                    val created = detailStore.addComment(post.id, text, replyId)
                                    if (created != null) {
                                        inputText = ""
                                        replyTarget = null
                                    }
                                }
                            }
                            .alpha(if (sendEnabled) 1f else 0.45f)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Text(
                            Copy.text(language, "sendComment"),
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                        )
                    }
                }
            }
        }
    }

    pendingDeleteComment?.let { target ->
        SaigonDialog(onDismiss = { pendingDeleteComment = null }) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    Copy.text(language, "deleteCommentAsk"),
                    color = tokens.text,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(end = 36.dp),
                )
                Text(
                    Copy.text(language, "deleteCommentHint"),
                    color = tokens.muted,
                    style = MaterialTheme.typography.bodyMedium,
                )
                SaigonPrimaryButton(
                    text = Copy.text(language, "delete"),
                    danger = true,
                    loading = state.isMutating,
                    onClick = {
                        scope.launch {
                            if (detailStore.deleteComment(post.id, target.id)) {
                                pendingDeleteComment = null
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                SaigonTextButton(
                    text = Copy.text(language, "cancel"),
                    onClick = { pendingDeleteComment = null },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun CommentRow(
    comment: CommentCardDto,
    canDelete: Boolean,
    language: Language,
    onAuthorClick: () -> Unit,
    onReply: () -> Unit,
    onDelete: () -> Unit,
) {
    val tokens = LocalSaigonTokens.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(if (canDelete) Modifier.clickable(onClick = onDelete) else Modifier)
            .padding(vertical = 2.dp),
    ) {
        UserAvatar(
            photoUrl = comment.authorPhotoUrl,
            name = comment.authorName,
            size = 36.dp,
            borderWidth = 0.dp,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onAuthorClick),
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    comment.authorName,
                    color = tokens.text,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .clickable(onClick = onAuthorClick)
                        .padding(horizontal = 2.dp),
                )
                val age = PostAge.relative(comment.createdAt, currentTimeMillis(), language)
                if (!age.isNullOrBlank()) {
                    Text(age, fontSize = 12.sp, color = tokens.muted)
                }
            }
            if (!comment.replyToAuthorName.isNullOrBlank()) {
                val quoteShape = RoundedCornerShape(topStart = 3.dp, topEnd = 8.dp, bottomEnd = 8.dp, bottomStart = 3.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(quoteShape)
                        .background(tokens.accent.copy(alpha = 0.10f))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(16.dp)
                            .background(tokens.accent),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${comment.replyToAuthorName}: ${comment.replyToText.orEmpty()}",
                        fontSize = 12.sp,
                        color = tokens.muted,
                    )
                }
            }
            LinkifiedText(
                text = comment.text,
                color = tokens.text,
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    Copy.text(language, "reply"),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = tokens.accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .clickable(onClick = onReply)
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                )
                if (canDelete) {
                    Text(
                        Copy.text(language, "delete"),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = tokens.danger,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .clickable(onClick = onDelete)
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun AttendeesSheet(
    postId: String,
    detailStore: PostDetailStore,
    language: Language,
    onSelectPerson: (EventPersonDto) -> Unit,
    onDismiss: () -> Unit,
) {
    val state by detailStore.state.collectAsState()
    val tokens = LocalSaigonTokens.current
    LaunchedEffect(postId) {
        detailStore.loadAttendees(postId, loadMore = false)
    }

    SaigonBottomSheet(
        title = Copy.text(language, "attendees"),
        onDismiss = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp),
        ) {
            if (state.isLoadingAttendees && state.attendees.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    SaigonSpinner()
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.heightIn(max = 400.dp),
                ) {
                    items(state.attendees, key = { it.id }) { person ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(999.dp))
                                .clickable { onSelectPerson(person) }
                                .padding(horizontal = 6.dp, vertical = 6.dp),
                        ) {
                            UserAvatar(photoUrl = person.photoUrl, name = person.name, size = 40.dp, borderWidth = 0.dp)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(person.name, color = tokens.text, fontWeight = FontWeight.Bold)
                                if (person.location.isNotBlank()) {
                                    Text(person.location, fontSize = 12.sp, color = tokens.muted)
                                }
                            }
                        }
                    }
                    if (state.attendeesNextCursor != null) {
                        item {
                            SaigonTextButton(
                                text = Copy.text(language, "loadMore"),
                                onClick = { detailStore.loadAttendees(postId, loadMore = true) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PollVotersSheet(
    postId: String,
    detailStore: PostDetailStore,
    language: Language,
    onSelectPerson: (EventPersonDto) -> Unit,
    onDismiss: () -> Unit,
) {
    val state by detailStore.state.collectAsState()
    val tokens = LocalSaigonTokens.current
    LaunchedEffect(postId) {
        detailStore.loadPollVoters(postId)
    }

    SaigonBottomSheet(
        title = Copy.text(language, "pollVoters"),
        onDismiss = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp),
        ) {
            if (state.isLoadingPollVoters) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    SaigonSpinner()
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.heightIn(max = 400.dp),
                ) {
                    items(state.pollVoters, key = { it.index }) { option ->
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "${option.text} (${option.voteCount})",
                                color = tokens.text,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                            )
                            option.voters.forEach { voter ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(999.dp))
                                        .clickable { onSelectPerson(voter) }
                                        .padding(horizontal = 6.dp, vertical = 4.dp),
                                ) {
                                    UserAvatar(photoUrl = voter.photoUrl, name = voter.name, size = 32.dp, borderWidth = 0.dp)
                                    Text(voter.name, color = tokens.text, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QuestionAnswersSheet(
    post: PostCardDto,
    detailStore: PostDetailStore,
    viewerId: String?,
    language: Language,
    onSelectPerson: (id: String, name: String, photoUrl: String?, location: String, intro: String) -> Unit,
    onMessageUser: (userId: String, name: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val state by detailStore.state.collectAsState()
    val tokens = LocalSaigonTokens.current

    LaunchedEffect(post.id) {
        detailStore.loadQuestionAnswers(post.id)
    }

    SaigonBottomSheet(
        title = Copy.text(language, "questionAnswers"),
        onDismiss = onDismiss,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp),
        ) {
            Text(
                Copy.text(language, "questionPrivateHint"),
                color = tokens.muted,
                fontSize = 13.sp,
            )
            when {
                state.isLoadingAnswers && state.questionAnswers.isEmpty() -> {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        SaigonSpinner()
                    }
                }
                state.questionAnswers.isEmpty() -> {
                    Text(
                        Copy.text(language, "noAnswers"),
                        color = tokens.muted,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 360.dp),
                    ) {
                        items(state.questionAnswers, key = { it.id }) { item ->
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 10.dp),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable {
                                                if (item.userId.isNotBlank()) {
                                                    onSelectPerson(item.userId, item.name, item.photoUrl, item.location, item.intro)
                                                }
                                            }
                                            .padding(4.dp),
                                    ) {
                                        UserAvatar(photoUrl = item.photoUrl, name = item.name, size = 38.dp, borderWidth = 0.dp)
                                        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                                            Text(item.name, color = tokens.text, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                                            LinkifiedText(text = item.text, color = tokens.text, style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                    if (item.userId.isNotBlank() && item.userId != viewerId) {
                                        SaigonPrimaryButton(
                                            text = Copy.text(language, "message"),
                                            onClick = { onMessageUser(item.userId, item.name) },
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                        )
                                    }
                                }
                                HorizontalDivider(color = tokens.line, thickness = 1.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CommunityMembersSheet(
    community: CommunityDto,
    shared: TownSharedClient,
    language: Language,
    onCommunityUpdated: (CommunityDto) -> Unit,
    onSelectMember: (CommunityMemberDto) -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens = LocalSaigonTokens.current
    val scope = rememberCoroutineScope()
    var current by remember(community.id, community.description, community.joined) { mutableStateOf(community) }
    var editingDesc by remember { mutableStateOf(false) }
    var descInput by remember(community.id, community.description) { mutableStateOf(community.description) }
    var savingDesc by remember { mutableStateOf(false) }
    var descError by remember { mutableStateOf<String?>(null) }

    var members by remember(community.id) { mutableStateOf<List<CommunityMemberDto>>(emptyList()) }
    var nextCursor by remember(community.id) { mutableStateOf<String?>(null) }
    var loadingMembers by remember(community.id) { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }

    LaunchedEffect(community.id) {
        loadingMembers = true
        when (val res = shared.api.getCommunityMembers(community.id, cursor = null, limit = 30)) {
            is ApiResult.Success -> {
                members = res.value.members
                nextCursor = res.value.nextCursor
            }
            is ApiResult.Failure -> Unit
        }
        loadingMembers = false
    }

    SaigonBottomSheet(
        title = "${current.emoji} ${current.name}".trim(),
        onDismiss = onDismiss,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp),
        ) {
            if (!editingDesc) {
                if (current.description.isNotBlank()) {
                    LinkifiedText(
                        text = current.description,
                        color = tokens.muted,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (current.joined) {
                    Row {
                        quest.saigon.android.ui.components.SaigonTinyPill(
                            label = Copy.text(language, if (current.description.isBlank()) "addDescription" else "edit"),
                            onClick = {
                                descInput = current.description
                                descError = null
                                editingDesc = true
                            },
                        )
                    }
                }
            } else {
                SaigonTextField(
                    value = descInput,
                    onValueChange = { if (it.length <= Limits.COMMUNITY_DESCRIPTION_MAX) descInput = it },
                    placeholder = Copy.text(language, "communityDescriptionHint"),
                    singleLine = false,
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                descError?.let {
                    Text(it, color = tokens.danger, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SaigonPrimaryButton(
                        text = Copy.text(language, "save"),
                        loading = savingDesc,
                        enabled = !savingDesc,
                        onClick = {
                            scope.launch {
                                savingDesc = true
                                descError = null
                                when (val res = shared.api.updateCommunityDescription(current.id, descInput.trim())) {
                                    is ApiResult.Success -> {
                                        current = res.value
                                        onCommunityUpdated(res.value)
                                        editingDesc = false
                                    }
                                    is ApiResult.Failure -> descError = res.message
                                }
                                savingDesc = false
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    )
                    SaigonTextButton(
                        text = Copy.text(language, "cancel"),
                        onClick = { editingDesc = false },
                    )
                }
            }

            Text(
                Copy.text(language, "communityMembers"),
                color = tokens.text,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp,
            )

            when {
                loadingMembers && members.isEmpty() -> {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        SaigonSpinner()
                    }
                }
                members.isEmpty() -> {
                    Text(Copy.text(language, "noMembers"), color = tokens.muted, fontSize = 14.sp)
                }
                else -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.heightIn(max = 360.dp),
                    ) {
                        items(members, key = { it.id }) { member ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { onSelectMember(member) }
                                    .padding(horizontal = 6.dp, vertical = 6.dp),
                            ) {
                                UserAvatar(photoUrl = member.photoUrl, name = member.name, size = 44.dp, borderWidth = 0.dp)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(member.name, color = tokens.text, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                                    if (member.location.isNotBlank()) {
                                        Text(member.location, color = tokens.muted, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                        if (nextCursor != null) {
                            item {
                                SaigonTextButton(
                                    text = Copy.text(language, "loadMore"),
                                    enabled = !loadingMore,
                                    onClick = {
                                        val c = nextCursor ?: return@SaigonTextButton
                                        scope.launch {
                                            loadingMore = true
                                            when (val res = shared.api.getCommunityMembers(community.id, cursor = c, limit = 30)) {
                                                is ApiResult.Success -> {
                                                    val seen = members.mapTo(HashSet()) { it.id }
                                                    members = members + res.value.members.filter { seen.add(it.id) }
                                                    nextCursor = res.value.nextCursor
                                                }
                                                is ApiResult.Failure -> Unit
                                            }
                                            loadingMore = false
                                        }
                                    },
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

@Composable
fun CheckInSheet(
    postId: String,
    detailStore: PostDetailStore,
    language: Language,
    onUpdated: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    SaigonBottomSheet(
        title = Copy.text(language, "checkInDialogTitle"),
        onDismiss = onDismiss,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            CheckIns.durationOptionsMinutes.chunked(4).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { minutes ->
                        val label = when (minutes) {
                            30 -> Copy.text(language, "checkIn30Minutes")
                            60 -> Copy.text(language, "checkIn1Hour")
                            90 -> Copy.text(language, "checkIn1AndHalfHours")
                            120 -> Copy.text(language, "checkIn2Hours")
                            180 -> Copy.text(language, "checkIn3Hours")
                            240 -> Copy.text(language, "checkIn4Hours")
                            300 -> Copy.text(language, "checkIn5Hours")
                            360 -> Copy.text(language, "checkIn6Hours")
                            else -> if (minutes < 60) "${minutes}m" else "${minutes / 60}h"
                        }
                        SaigonPillChip(
                            label = label,
                            selected = false,
                            onClick = {
                                scope.launch {
                                    if (detailStore.updateCheckIn(postId, minutes) != null) {
                                        onUpdated()
                                        onDismiss()
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            SaigonTextButton(
                text = Copy.text(language, "checkOut"),
                danger = true,
                onClick = {
                    scope.launch {
                        if (detailStore.removeCheckIn(postId)) {
                            onUpdated()
                            onDismiss()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun EditPostDialog(
    post: PostCardDto,
    detailStore: PostDetailStore,
    language: Language,
    onDismiss: () -> Unit,
) {
    val state by detailStore.state.collectAsState()
    val tokens = LocalSaigonTokens.current
    val scope = rememberCoroutineScope()
    var message by remember(post.id) { mutableStateOf(post.message) }

    SaigonDialog(onDismiss = onDismiss) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                Copy.text(language, "edit"),
                color = tokens.text,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(end = 36.dp),
            )
            SaigonTextField(
                value = message,
                onValueChange = { if (it.length <= Limits.MESSAGE_MAX) message = it },
                singleLine = false,
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .clickable {
                        scope.launch {
                            if (detailStore.deletePost(post.id)) onDismiss()
                        }
                    }
                    .padding(vertical = 6.dp, horizontal = 4.dp),
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, tint = tokens.danger, modifier = Modifier.size(18.dp))
                Text(Copy.text(language, "delete"), color = tokens.danger, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            SaigonPrimaryButton(
                text = Copy.text(language, "save"),
                enabled = message.isNotBlank() && !state.isMutating,
                loading = state.isMutating,
                onClick = {
                    scope.launch {
                        if (detailStore.updatePostMessage(post.id, message.trim())) onDismiss()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            SaigonTextButton(
                text = Copy.text(language, "cancel"),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
