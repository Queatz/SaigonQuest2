package quest.saigon.android.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import quest.saigon.Copy
import quest.saigon.android.theme.LocalSaigonTokens
import quest.saigon.android.ui.components.SaigonCard
import quest.saigon.android.ui.components.SaigonPageShell
import quest.saigon.android.ui.components.SaigonPillChip
import quest.saigon.android.ui.components.SaigonSpinner
import quest.saigon.android.ui.components.SaigonTextButton
import quest.saigon.android.ui.components.SaigonTinyPill
import quest.saigon.android.ui.components.UserAvatar
import quest.saigon.shared.TownSharedClient

private enum class AdminTab { USERS, POSTS, COMMENTS }

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

    LaunchedEffect(Unit) {
        if (shared.admin.verifyAdmin()) {
            shared.admin.loadUsers()
            shared.admin.loadPosts()
            shared.admin.loadComments()
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
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                ) {
                    SaigonPillChip(selected = tab == AdminTab.USERS, onClick = { tab = AdminTab.USERS }, label = Copy.text(lang, "adminUsers"))
                    SaigonPillChip(selected = tab == AdminTab.POSTS, onClick = { tab = AdminTab.POSTS }, label = Copy.text(lang, "adminPosts"))
                    SaigonPillChip(selected = tab == AdminTab.COMMENTS, onClick = { tab = AdminTab.COMMENTS }, label = Copy.text(lang, "adminComments"))
                }

                when (tab) {
                    AdminTab.USERS -> LazyColumn(
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
                                                SaigonTinyPill(label = "PAUSED", danger = true)
                                            }
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

                    AdminTab.POSTS -> LazyColumn(
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.navigationBarsPadding(),
                    ) {
                        items(state.posts, key = { it.id }) { post ->
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
                                    AsyncImage(
                                        model = post.photoUrl,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(tokens.line),
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(post.authorName, color = tokens.text, fontWeight = FontWeight.ExtraBold)
                                        Text(post.message, color = tokens.muted, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
                                    }
                                    SaigonTinyPill(
                                        label = Copy.text(lang, "delete"),
                                        danger = true,
                                        onClick = { scope.launch { shared.admin.deletePost(post.id) } },
                                    )
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

                    AdminTab.COMMENTS -> LazyColumn(
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
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                                        Text(comment.authorName, color = tokens.text, fontWeight = FontWeight.ExtraBold)
                                        Text(comment.text, color = tokens.text, fontSize = 14.sp)
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
                                                Box(Modifier.width(3.dp).height(14.dp).background(tokens.line))
                                                Spacer(Modifier.width(8.dp))
                                                Text(
                                                    postSnippet,
                                                    fontSize = 12.sp,
                                                    color = tokens.muted,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                        }
                                    }
                                    SaigonTinyPill(
                                        label = Copy.text(lang, "delete"),
                                        danger = true,
                                        onClick = { scope.launch { shared.admin.deleteComment(comment.id) } },
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
            }
        }
    }
}
