package quest.saigon.android.ui.messages

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import quest.saigon.AppRoute
import quest.saigon.Copy
import quest.saigon.Language
import quest.saigon.Limits
import quest.saigon.PostAge
import quest.saigon.android.media.ImagePreparer
import quest.saigon.android.theme.LocalSaigonTokens
import quest.saigon.android.theme.SaigonBrushes
import quest.saigon.android.theme.SaigonColors
import quest.saigon.android.ui.components.LinkifiedText
import quest.saigon.android.ui.components.PhotoViewerDialog
import quest.saigon.android.ui.components.SaigonBottomSheet
import quest.saigon.android.ui.components.SaigonConfirmDialog
import quest.saigon.android.ui.components.SaigonDialog
import quest.saigon.android.ui.components.SaigonIconButton
import quest.saigon.android.ui.components.SaigonPageShell
import quest.saigon.android.ui.components.SaigonPrimaryButton
import quest.saigon.android.ui.components.SaigonSpinner
import quest.saigon.android.ui.components.SaigonTextButton
import quest.saigon.android.ui.components.SaigonTextField
import quest.saigon.android.ui.components.SaigonTinyPill
import quest.saigon.android.ui.components.UserAvatar
import quest.saigon.android.ui.components.formatVnd
import quest.saigon.shared.TownSharedClient
import quest.saigon.shared.model.ChatDto
import quest.saigon.shared.model.ChatMessageDto
import quest.saigon.shared.platform.currentTimeMillis

@Composable
fun MessagesListScreen(
    shared: TownSharedClient,
    onNavigate: (AppRoute) -> Unit,
    onBack: () -> Unit,
) {
    val sessionState by shared.session.state.collectAsState()
    val msgState by shared.messages.state.collectAsState()
    val lang = sessionState.language
    val tokens = LocalSaigonTokens.current
    val scope = rememberCoroutineScope()
    var showNewGroup by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        shared.messages.loadChats()
        shared.messages.markListNoticesRead()
    }

    SaigonPageShell(
        title = Copy.text(lang, "messages"),
        onBack = onBack,
        backContentDescription = Copy.text(lang, "back"),
        topActions = {
            SaigonIconButton(onClick = { showNewGroup = true }, overlay = false) {
                Icon(Icons.Default.Add, contentDescription = Copy.text(lang, "newGroup"), modifier = Modifier.size(20.dp))
            }
        },
    ) {
        when {
            msgState.isLoadingChats && msgState.chats.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    SaigonSpinner()
                }
            }
            msgState.chats.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(Copy.text(lang, "noChats"), color = tokens.muted)
                }
            }
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxSize().navigationBarsPadding(),
                ) {
                    items(msgState.chats, key = { it.id }) { chat ->
                        ChatRowCard(chat = chat, language = lang, onClick = { onNavigate(AppRoute.Chat(chat.id)) })
                    }
                    if (msgState.nextChatsCursor != null) {
                        item {
                            SaigonTextButton(
                                text = Copy.text(lang, "loadMore"),
                                onClick = { shared.messages.loadMoreChats() },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }

    if (showNewGroup) {
        var groupName by remember { mutableStateOf("") }
        SaigonDialog(onDismiss = { showNewGroup = false }) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(Copy.text(lang, "newGroup"), color = tokens.text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(end = 36.dp))
                SaigonTextField(
                    value = groupName,
                    onValueChange = { if (it.length <= Limits.CHAT_NAME_MAX) groupName = it },
                    label = Copy.text(lang, "groupName"),
                    placeholder = Copy.text(lang, "groupNameHint"),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                SaigonPrimaryButton(
                    text = Copy.text(lang, "createGroup"),
                    onClick = {
                        scope.launch {
                            val created = shared.messages.createGroupChat(groupName.trim())
                            if (created != null) {
                                showNewGroup = false
                                onNavigate(AppRoute.Chat(created.id))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                SaigonTextButton(text = Copy.text(lang, "cancel"), onClick = { showNewGroup = false }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun ChatRowCard(
    chat: ChatDto,
    language: Language,
    onClick: () -> Unit,
) {
    val tokens = LocalSaigonTokens.current
    val firstFace = chat.photos.firstOrNull()
    val secondFace = chat.photos.getOrNull(1)
    val unread = chat.unreadCount > 0L

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .then(
                if (unread) {
                    Modifier
                        .background(tokens.accent.copy(alpha = 0.08f))
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
    ) {
        if (secondFace != null && firstFace != null) {
            Box(modifier = Modifier.size(48.dp)) {
                UserAvatar(
                    photoUrl = firstFace.photoUrl,
                    name = firstFace.name,
                    size = 32.dp,
                    borderWidth = 0.dp,
                    modifier = Modifier.align(Alignment.TopStart),
                )
                UserAvatar(
                    photoUrl = secondFace.photoUrl,
                    name = secondFace.name,
                    size = 32.dp,
                    borderColor = tokens.bg,
                    borderWidth = 2.dp,
                    modifier = Modifier.align(Alignment.BottomEnd),
                )
            }
        } else {
            UserAvatar(
                photoUrl = firstFace?.photoUrl,
                name = chat.name.ifBlank { firstFace?.name ?: "?" },
                size = 48.dp,
                borderWidth = 0.dp,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
            Text(
                chat.name.ifBlank { Copy.text(language, "unnamedGroupChat") },
                color = tokens.text,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val preview = listOfNotNull(
                chat.lastMessageSenderName.takeIf { it.isNotBlank() },
                chat.lastMessageText.takeIf { it.isNotBlank() },
            ).joinToString(": ")
            if (preview.isNotBlank()) {
                Text(
                    preview,
                    fontSize = 14.sp,
                    color = tokens.text.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val age = PostAge.relative(chat.lastMessageAt, currentTimeMillis(), language)
            if (!age.isNullOrBlank()) {
                Text(
                    age,
                    fontSize = 12.sp,
                    fontWeight = if (unread) FontWeight.ExtraBold else FontWeight.Normal,
                    color = if (unread) tokens.accent else tokens.muted,
                )
            }
            if (unread) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .defaultMinSize(minWidth = 20.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(tokens.accent)
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                ) {
                    Text(
                        chat.unreadCount.toString(),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
        }
    }
}

@Composable
fun ChatScreen(
    chatId: String,
    shared: TownSharedClient,
    onNavigate: (AppRoute) -> Unit,
    onBack: () -> Unit,
) {
    val sessionState by shared.session.state.collectAsState()
    val msgState by shared.messages.state.collectAsState()
    val lang = sessionState.language
    val tokens = LocalSaigonTokens.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var inputText by remember { mutableStateOf("") }
    var pendingPhotos by remember { mutableStateOf<List<ByteArray>>(emptyList()) }
    var showMembersSheet by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var confirmDeleteGroup by remember { mutableStateOf(false) }
    var pendingDeleteMessage by remember { mutableStateOf<ChatMessageDto?>(null) }
    var reactTargetMessage by remember { mutableStateOf<ChatMessageDto?>(null) }
    var zoomUrl by remember { mutableStateOf<String?>(null) }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(Limits.PHOTOS_MAX),
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            pendingPhotos = uris.mapNotNull { ImagePreparer.prepareSquareOrOriginal(context, it) }
        }
    }

    LaunchedEffect(chatId) {
        shared.messages.openChat(chatId)
    }

    DisposableEffect(chatId) {
        onDispose { shared.messages.closeActiveChat() }
    }

    val activeChat = msgState.activeChat
    val meId = sessionState.me?.id
    val isGroup = activeChat?.type == "group" || activeChat?.type == "open"
    val headerTitle = activeChat?.name?.ifBlank { Copy.text(lang, "unnamedGroupChat") }
        ?: Copy.text(lang, "messages")

    SaigonPageShell(
        title = headerTitle,
        onBack = onBack,
        backContentDescription = Copy.text(lang, "back"),
        topActions = {
            if (isGroup) {
                SaigonIconButton(
                    onClick = {
                        shared.messages.loadMembers(chatId)
                        showMembersSheet = true
                    },
                    overlay = false,
                ) {
                    Icon(Icons.Default.Group, contentDescription = Copy.text(lang, "chatMembers"), modifier = Modifier.size(20.dp))
                }
            }
            Box {
                SaigonIconButton(onClick = { showMenu = true }, overlay = false) {
                    Icon(Icons.Default.MoreVert, contentDescription = Copy.text(lang, "chatMenu"), modifier = Modifier.size(20.dp))
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    containerColor = tokens.surface,
                ) {
                    if (isGroup && activeChat.owner) {
                        DropdownMenuItem(
                            text = { Text(Copy.text(lang, "renameGroup"), color = tokens.text) },
                            onClick = {
                                showMenu = false
                                showRenameDialog = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(Copy.text(lang, "deleteGroup"), color = tokens.danger) },
                            onClick = {
                                showMenu = false
                                confirmDeleteGroup = true
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(Copy.text(lang, "leaveChat"), color = tokens.text) },
                        onClick = {
                            showMenu = false
                            confirmLeave = true
                        },
                    )
                }
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(tokens.bg),
            ) {
                HorizontalDivider(color = tokens.line, thickness = 1.dp)
                if (pendingPhotos.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                    ) {
                        Text("${pendingPhotos.size} ${Copy.text(lang, "photos")}", fontSize = 12.sp, color = tokens.muted)
                        SaigonTextButton(text = Copy.text(lang, "cancel"), onClick = { pendingPhotos = emptyList() })
                    }
                }
                msgState.typingUserName?.let { typing ->
                    Text(
                        "$typing…",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = tokens.muted,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                // .chat-composer
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    SaigonIconButton(
                        onClick = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        overlay = false,
                        size = 40.dp,
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = Copy.text(lang, "sendPhoto"), modifier = Modifier.size(18.dp))
                    }
                    SaigonTextField(
                        value = inputText,
                        onValueChange = { next ->
                            if (next.length <= Limits.CHAT_MESSAGE_MAX) {
                                inputText = next
                                shared.messages.sendTyping(chatId)
                            }
                        },
                        placeholder = Copy.text(lang, "messagePlaceholder"),
                        containerColor = tokens.surface,
                        borderWidth = 1.dp,
                        cornerRadius = 16.dp,
                        singleLine = false,
                        minLines = 1,
                        maxLines = 5,
                        modifier = Modifier.weight(1f),
                    )
                    val sendEnabled = (inputText.isNotBlank() || pendingPhotos.isNotEmpty()) && !msgState.isSending
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .height(40.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(SaigonBrushes.primaryGradient, RoundedCornerShape(999.dp))
                            .clickable(enabled = sendEnabled) {
                                val text = inputText.trim()
                                val photos = pendingPhotos
                                scope.launch {
                                    val sent = shared.messages.sendMessage(chatId, text, photos)
                                    if (sent != null) {
                                        inputText = ""
                                        pendingPhotos = emptyList()
                                    }
                                }
                            }
                            .alpha(if (sendEnabled) 1f else 0.45f)
                            .padding(horizontal = 16.dp),
                    ) {
                        Text(
                            Copy.text(lang, "send"),
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                        )
                    }
                }
            }
        },
    ) {
        LazyColumn(
            reverseLayout = true,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(msgState.messages, key = { it.id }) { msg ->
                ChatMessageBubble(
                    message = msg,
                    isMine = meId != null && msg.userId == meId,
                    language = lang,
                    onOpenProduct = { id -> onNavigate(AppRoute.Product(id)) },
                    onOpenActivity = { id -> onNavigate(AppRoute.Activity(id)) },
                    onZoomPhoto = { zoomUrl = it },
                    onToggleReaction = { emoji, mine ->
                        scope.launch { shared.messages.toggleReaction(chatId, msg.id, emoji, mine) }
                    },
                    onLongPress = { reactTargetMessage = msg },
                    onDelete = { pendingDeleteMessage = msg },
                )
            }
            if (msgState.nextMessagesCursor != null) {
                item {
                    SaigonTextButton(
                        text = Copy.text(lang, "loadMore"),
                        onClick = { shared.messages.loadMoreMessages() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

    if (showMembersSheet) {
        ChatMembersSheet(
            chatId = chatId,
            shared = shared,
            isOwner = activeChat?.owner == true,
            language = lang,
            onOpenUser = { userId ->
                showMembersSheet = false
                onNavigate(AppRoute.User(userId))
            },
            onDismiss = { showMembersSheet = false },
        )
    }

    if (showRenameDialog) {
        var nextName by remember { mutableStateOf(activeChat?.name.orEmpty()) }
        SaigonDialog(onDismiss = { showRenameDialog = false }) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(Copy.text(lang, "renameGroup"), color = tokens.text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(end = 36.dp))
                SaigonTextField(
                    value = nextName,
                    onValueChange = { if (it.length <= Limits.CHAT_NAME_MAX) nextName = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                SaigonPrimaryButton(
                    text = Copy.text(lang, "save"),
                    enabled = nextName.isNotBlank(),
                    onClick = {
                        scope.launch {
                            if (shared.messages.renameChat(chatId, nextName.trim()) != null) showRenameDialog = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                SaigonTextButton(text = Copy.text(lang, "cancel"), onClick = { showRenameDialog = false }, modifier = Modifier.fillMaxWidth())
            }
        }
    }

    if (confirmLeave) {
        SaigonConfirmDialog(
            title = Copy.text(lang, "confirmLeaveChat"),
            hint = "",
            confirmLabel = Copy.text(lang, "leaveChat"),
            cancelLabel = Copy.text(lang, "cancel"),
            onConfirm = {
                scope.launch {
                    if (shared.messages.leaveChat(chatId)) {
                        confirmLeave = false
                        onBack()
                    }
                }
            },
            onDismiss = { confirmLeave = false },
        )
    }

    if (confirmDeleteGroup) {
        SaigonConfirmDialog(
            title = Copy.text(lang, "confirmDeleteGroup"),
            hint = "",
            confirmLabel = Copy.text(lang, "deleteGroup"),
            cancelLabel = Copy.text(lang, "cancel"),
            onConfirm = {
                scope.launch {
                    if (shared.messages.deleteChat(chatId)) {
                        confirmDeleteGroup = false
                        onBack()
                    }
                }
            },
            onDismiss = { confirmDeleteGroup = false },
        )
    }

    pendingDeleteMessage?.let { target ->
        SaigonConfirmDialog(
            title = Copy.text(lang, "confirmRemoveMessage"),
            hint = "",
            confirmLabel = Copy.text(lang, "delete"),
            cancelLabel = Copy.text(lang, "cancel"),
            onConfirm = {
                scope.launch {
                    if (shared.messages.deleteMessage(chatId, target.id)) {
                        pendingDeleteMessage = null
                    }
                }
            },
            onDismiss = { pendingDeleteMessage = null },
        )
    }

    reactTargetMessage?.let { msg ->
        var customEmoji by remember { mutableStateOf("") }
        val defaults = activeChat?.defaultReactions?.ifEmpty { listOf("👍", "❤️", "😂", "🙌", "🔥") }
            ?: listOf("👍", "❤️", "😂", "🙌", "🔥")
        SaigonDialog(onDismiss = { reactTargetMessage = null }) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(Copy.text(lang, "reactions"), color = tokens.text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(end = 36.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    defaults.forEach { emoji ->
                        val mine = msg.reactions.any { it.emoji == emoji && it.mine }
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(tokens.bg)
                                .border(1.dp, if (mine) tokens.accent else tokens.line, RoundedCornerShape(999.dp))
                                .clickable {
                                    scope.launch { shared.messages.toggleReaction(chatId, msg.id, emoji, mine) }
                                    reactTargetMessage = null
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Text(emoji, fontSize = 18.sp)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    SaigonTextField(
                        value = customEmoji,
                        onValueChange = { if (it.length <= Limits.CHAT_REACTION_MAX) customEmoji = it },
                        placeholder = Copy.text(lang, "customReaction"),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    SaigonPrimaryButton(
                        text = Copy.text(lang, "send"),
                        enabled = customEmoji.isNotBlank(),
                        onClick = {
                            val e = customEmoji.trim()
                            scope.launch { shared.messages.toggleReaction(chatId, msg.id, e, false) }
                            reactTargetMessage = null
                        },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }

    zoomUrl?.let { PhotoViewerDialog(photoUrl = it, onDismiss = { zoomUrl = null }) }
}

@Composable
private fun ChatMessageBubble(
    message: ChatMessageDto,
    isMine: Boolean,
    language: Language,
    onOpenProduct: (String) -> Unit,
    onOpenActivity: (String) -> Unit,
    onZoomPhoto: (String) -> Unit,
    onToggleReaction: (String, Boolean) -> Unit,
    onLongPress: () -> Unit,
    onDelete: () -> Unit,
) {
    val tokens = LocalSaigonTokens.current
    val bubbleShape = if (isMine) {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 6.dp, bottomStart = 18.dp)
    } else {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 6.dp)
    }
    val bubbleBg = if (isMine) SaigonColors.Card else tokens.surface
    val bubbleFg = if (isMine) SaigonColors.Ink else tokens.text

    Column(
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (!isMine && message.authorName.isNotBlank()) {
            Text(
                message.authorName,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = tokens.muted,
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }
        Surface(
            color = bubbleBg,
            shape = bubbleShape,
            border = if (isMine) null else BorderStroke(1.dp, tokens.line),
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(bubbleShape)
                .clickable(onClick = onLongPress),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(10.dp)) {
                message.attachment?.let { att ->
                    if (att.type == "product" && !att.productId.isNullOrBlank()) {
                        Surface(
                            color = tokens.surface,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, tokens.line),
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onOpenProduct(att.productId!!) },
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(att.productName.orEmpty(), color = tokens.text, fontWeight = FontWeight.ExtraBold)
                                att.price?.let { Text(formatVnd(it), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = tokens.accent) }
                            }
                        }
                    } else if (att.type == "activity" && !att.activityId.isNullOrBlank()) {
                        Surface(
                            color = tokens.surface,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, tokens.line),
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onOpenActivity(att.activityId!!) },
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("${att.activityEmoji.orEmpty()} ${att.activityName.orEmpty()}".trim(), color = tokens.text, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                }

                message.photoUrls.forEach { url ->
                    AsyncImage(
                        model = url,
                        contentDescription = Copy.text(language, "photoMessage"),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onZoomPhoto(url) },
                    )
                }

                if (message.text.isNotBlank()) {
                    LinkifiedText(text = message.text, color = bubbleFg, linkColor = SaigonColors.Accent)
                }

                if (message.reactions.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        message.reactions.forEach { r ->
                            Surface(
                                color = tokens.bg,
                                shape = RoundedCornerShape(999.dp),
                                border = BorderStroke(1.dp, if (r.mine) tokens.accent else tokens.line),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .clickable { onToggleReaction(r.emoji, r.mine) },
                            ) {
                                Text(
                                    "${r.emoji} ${r.count}",
                                    color = tokens.text,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
        if (isMine) {
            IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Delete, contentDescription = Copy.text(language, "delete"), tint = tokens.muted, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun ChatMembersSheet(
    chatId: String,
    shared: TownSharedClient,
    isOwner: Boolean,
    language: Language,
    onOpenUser: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val msgState by shared.messages.state.collectAsState()
    val tokens = LocalSaigonTokens.current
    val scope = rememberCoroutineScope()
    var addQuery by remember { mutableStateOf("") }

    SaigonBottomSheet(
        title = Copy.text(language, "chatMembers"),
        onDismiss = onDismiss,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            if (isOwner) {
                SaigonTextField(
                    value = addQuery,
                    onValueChange = {
                        addQuery = it
                        shared.messages.searchPeople(it, chatId)
                    },
                    placeholder = Copy.text(language, "searchPeople"),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (addQuery.isNotBlank()) {
                    msgState.peopleResults.take(5).forEach { person ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(tokens.bg)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Text(person.name, color = tokens.text, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            SaigonTinyPill(
                                label = Copy.text(language, "addMember"),
                                selected = true,
                                onClick = {
                                    scope.launch {
                                        if (shared.messages.addMember(chatId, person.id)) addQuery = ""
                                    }
                                },
                            )
                        }
                    }
                }
            }
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.heightIn(max = 320.dp),
            ) {
                items(msgState.members, key = { it.id }) { member ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(999.dp))
                            .clickable { onOpenUser(member.id) }
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                    ) {
                        UserAvatar(photoUrl = member.photoUrl, name = member.name, size = 36.dp, borderWidth = 0.dp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(member.name, color = tokens.text, fontWeight = FontWeight.Bold)
                            if (member.owner) Text(Copy.text(language, "owner"), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = tokens.accent)
                        }
                        if (isOwner && !member.owner) {
                            SaigonTinyPill(
                                label = Copy.text(language, "makeOwner"),
                                onClick = { scope.launch { shared.messages.makeOwner(chatId, member.id) } },
                            )
                            SaigonTinyPill(
                                label = Copy.text(language, "removeMember"),
                                danger = true,
                                onClick = { scope.launch { shared.messages.removeMember(chatId, member.id) } },
                            )
                        }
                    }
                }
            }
        }
    }
}
