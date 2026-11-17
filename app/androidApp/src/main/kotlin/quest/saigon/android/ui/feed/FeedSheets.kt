package quest.saigon.android.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import quest.saigon.shared.model.CommentCardDto
import quest.saigon.shared.model.EventPersonDto
import quest.saigon.shared.model.PostCardDto
import quest.saigon.shared.platform.currentTimeMillis
import quest.saigon.shared.store.PostDetailStore

@Composable
fun CommentsSheet(
    post: PostCardDto,
    detailStore: PostDetailStore,
    viewerId: String?,
    language: Language,
    onRequireAuth: () -> Unit,
    onSelectPerson: (id: String, name: String, photoUrl: String?, location: String, intro: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val state by detailStore.state.collectAsState()
    val tokens = LocalSaigonTokens.current
    val scope = rememberCoroutineScope()
    var inputText by remember { mutableStateOf("") }
    var replyTarget by remember { mutableStateOf<CommentCardDto?>(null) }

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
                            CommentRow(
                                comment = comment,
                                canDelete = viewerId != null && (viewerId == comment.userId || viewerId == post.authorId),
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
                                onDelete = { scope.launch { detailStore.deleteComment(post.id, comment.id) } },
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

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    SaigonTextField(
                        value = inputText,
                        onValueChange = { if (it.length <= Limits.COMMENT_MAX) inputText = it },
                        placeholder = Copy.text(language, "emptyComments"),
                        containerColor = tokens.bg,
                        borderWidth = 1.dp,
                        cornerRadius = 12.dp,
                        singleLine = true,
                        modifier = Modifier.weight(1f),
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
                            Copy.text(language, "send"),
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                        )
                    }
                }
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
        modifier = Modifier.fillMaxWidth(),
    ) {
        UserAvatar(
            photoUrl = comment.authorPhotoUrl,
            name = comment.authorName,
            size = 36.dp,
            borderWidth = 0.dp,
            modifier = Modifier.clickable(onClick = onAuthorClick),
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    comment.authorName,
                    color = tokens.text,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                    modifier = Modifier.clickable(onClick = onAuthorClick),
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
                        .clickable(onClick = onReply)
                        .padding(vertical = 4.dp),
                )
                if (canDelete) {
                    Text(
                        Copy.text(language, "delete"),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = tokens.danger,
                        modifier = Modifier
                            .clickable(onClick = onDelete)
                            .padding(vertical = 4.dp),
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
                                        .clickable { onSelectPerson(voter) }
                                        .padding(vertical = 4.dp),
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
    onRequireAuth: () -> Unit,
    onSelectPerson: (id: String, name: String, photoUrl: String?, location: String, intro: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val state by detailStore.state.collectAsState()
    val tokens = LocalSaigonTokens.current
    val scope = rememberCoroutineScope()
    var answerText by remember { mutableStateOf("") }

    LaunchedEffect(post.id) {
        detailStore.loadQuestionAnswers(post.id)
    }

    SaigonBottomSheet(
        title = post.questionTitle.ifBlank { Copy.text(language, "askQuestion") },
        onDismiss = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                SaigonTextField(
                    value = answerText,
                    onValueChange = { if (it.length <= Limits.QUESTION_ANSWER_MAX) answerText = it },
                    placeholder = Copy.text(language, "questionAnswerHint"),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                SaigonPrimaryButton(
                    text = Copy.text(language, "send"),
                    enabled = answerText.isNotBlank() && !state.isMutating,
                    onClick = {
                        if (viewerId == null) {
                            onRequireAuth()
                            return@SaigonPrimaryButton
                        }
                        scope.launch {
                            val updated = detailStore.answerQuestion(post.id, answerText.trim())
                            if (updated != null) {
                                answerText = ""
                                detailStore.loadQuestionAnswers(post.id)
                            }
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            if (state.isLoadingAnswers && state.questionAnswers.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    SaigonSpinner()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 320.dp),
                ) {
                    items(state.questionAnswers, key = { it.id }) { item ->
                        Column {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (item.userId.isNotBlank()) {
                                            onSelectPerson(item.userId, item.name, item.photoUrl, item.location, item.intro)
                                        }
                                    }
                                    .padding(vertical = 10.dp),
                            ) {
                                UserAvatar(photoUrl = item.photoUrl, name = item.name, size = 36.dp, borderWidth = 0.dp)
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(item.name, color = tokens.text, fontWeight = FontWeight.Bold)
                                    LinkifiedText(text = item.text, color = tokens.text, style = MaterialTheme.typography.bodyMedium)
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
                        val label = if (minutes < 60) "${minutes}m" else "${minutes / 60}h"
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
