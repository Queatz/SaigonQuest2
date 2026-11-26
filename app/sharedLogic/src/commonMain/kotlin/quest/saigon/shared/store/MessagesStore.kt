package quest.saigon.shared.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import quest.saigon.Limits
import quest.saigon.shared.model.*
import quest.saigon.shared.network.ApiResult
import quest.saigon.shared.network.TownApiClient

sealed interface DirectChatCheckResult {
    data class Existing(val chat: ChatDto) : DirectChatCheckResult
    data object NeedsFirstMessage : DirectChatCheckResult
    data class Error(val message: String) : DirectChatCheckResult
}

data class MessagesUiState(
    val chats: List<ChatDto> = emptyList(),
    val nextChatsCursor: String? = null,
    val unreadCount: Long = 0L,
    val filterType: String? = null,
    val activeChat: ChatDto? = null,
    val messages: List<ChatMessageDto> = emptyList(),
    val nextMessagesCursor: String? = null,
    val members: List<ChatMemberDto> = emptyList(),
    val reactionDetails: List<ReactionDetailDto> = emptyList(),
    val peopleResults: List<EventPersonDto> = emptyList(),
    val typingUserName: String? = null,
    val streamConnected: Boolean = false,
    val isLoadingChats: Boolean = false,
    val isLoadingMessages: Boolean = false,
    val isLoadingMoreMessages: Boolean = false,
    val isSending: Boolean = false,
    val error: String? = null,
)

class MessagesStore(
    private val api: TownApiClient,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(MessagesUiState())
    val state: StateFlow<MessagesUiState> = _state.asStateFlow()

    private val _streamEvents = MutableSharedFlow<ChatStreamEventDto>(extraBufferCapacity = 64)
    val streamEvents: SharedFlow<ChatStreamEventDto> = _streamEvents.asSharedFlow()

    private var streamJob: Job? = null
    private var typingClearJob: Job? = null

    fun connectStream() {
        if (streamJob?.isActive == true) return
        scope.launch { refreshUnread() }
        streamJob = api.sse.launchStream(
            scope = scope,
            path = "/api/chats/stream",
            onConnected = { reconnected ->
                _state.update { it.copy(streamConnected = true) }
                if (reconnected) {
                    _streamEvents.tryEmit(ChatStreamEventDto(type = "reconnect"))
                    refreshChats()
                }
                _state.value.activeChat?.id?.let { activeId -> reloadActiveMessages(activeId) }
            },
            onEvent = { sse ->
                val event = try {
                    api.json.decodeFromString(ChatStreamEventDto.serializer(), sse.data)
                } catch (_: Throwable) {
                    null
                } ?: return@launchStream
                handleStreamEvent(event)
                _streamEvents.tryEmit(event)
            },
        )
    }

    fun disconnectStream() {
        streamJob?.cancel()
        streamJob = null
        typingClearJob?.cancel()
        typingClearJob = null
        _state.update { it.copy(streamConnected = false, typingUserName = null) }
    }

    fun clearState() {
        disconnectStream()
        _state.update { MessagesUiState() }
    }

    suspend fun refreshUnread() {
        when (val res = api.getChats(limit = 1)) {
            is ApiResult.Success -> _state.update { it.copy(unreadCount = res.value.unreadCount) }
            is ApiResult.Failure -> Unit
        }
    }

    fun loadChats(type: String? = null) {
        scope.launch {
            _state.update { it.copy(filterType = type, isLoadingChats = true, error = null) }
            when (val res = api.getChats(cursor = null, limit = 50, type = type)) {
                is ApiResult.Success -> {
                    _state.update {
                        it.copy(
                            chats = res.value.chats,
                            nextChatsCursor = res.value.nextCursor,
                            unreadCount = res.value.unreadCount,
                            isLoadingChats = false,
                        )
                    }
                }
                is ApiResult.Failure -> _state.update { it.copy(isLoadingChats = false, error = res.message) }
            }
        }
    }

    fun refreshChats() {
        val type = _state.value.filterType
        scope.launch {
            when (val res = api.getChats(cursor = null, limit = 50, type = type)) {
                is ApiResult.Success -> {
                    _state.update { prev ->
                        val updatedActive = prev.activeChat?.let { cur ->
                            res.value.chats.firstOrNull { it.id == cur.id }?.copy(defaultReactions = cur.defaultReactions)
                                ?: cur
                        }
                        prev.copy(
                            chats = res.value.chats,
                            nextChatsCursor = res.value.nextCursor,
                            unreadCount = res.value.unreadCount,
                            activeChat = updatedActive,
                        )
                    }
                }
                is ApiResult.Failure -> Unit
            }
        }
    }

    fun loadMoreChats() {
        val current = _state.value
        val cursor = current.nextChatsCursor ?: return
        if (current.isLoadingChats) return
        scope.launch {
            when (val res = api.getChats(cursor = cursor, limit = 50, type = current.filterType)) {
                is ApiResult.Success -> {
                    _state.update { prev ->
                        val seen = prev.chats.mapTo(HashSet()) { it.id }
                        prev.copy(
                            chats = prev.chats + res.value.chats.filter { seen.add(it.id) },
                            nextChatsCursor = res.value.nextCursor,
                            unreadCount = res.value.unreadCount,
                        )
                    }
                }
                is ApiResult.Failure -> _state.update { it.copy(error = res.message) }
            }
        }
    }

    fun markListNoticesRead() {
        scope.launch {
            when (val res = api.markChatNoticesRead()) {
                is ApiResult.Success -> _state.update { it.copy(unreadCount = res.value.unreadCount) }
                is ApiResult.Failure -> Unit
            }
        }
    }

    fun openChat(chatId: String) {
        scope.launch {
            _state.update {
                it.copy(
                    isLoadingMessages = true,
                    messages = emptyList(),
                    nextMessagesCursor = null,
                    members = emptyList(),
                    typingUserName = null,
                    error = null,
                )
            }
            val chatRes = api.getChat(chatId)
            when (chatRes) {
                is ApiResult.Success -> {
                    val chat = normalizeChat(chatRes.value)
                    _state.update { it.copy(activeChat = chat) }
                    upsertChat(chat)
                    val msgRes = api.getChatMessages(chatId, cursor = null, limit = Limits.CHAT_MESSAGE_PAGE_SIZE)
                    when (msgRes) {
                        is ApiResult.Success -> {
                            _state.update {
                                it.copy(
                                    messages = msgRes.value.messages,
                                    nextMessagesCursor = msgRes.value.nextCursor,
                                    isLoadingMessages = false,
                                )
                            }
                        }
                        is ApiResult.Failure -> {
                            _state.update { it.copy(isLoadingMessages = false, error = msgRes.message) }
                        }
                    }
                    markChatRead(chatId)
                }
                is ApiResult.Failure -> {
                    _state.update { it.copy(isLoadingMessages = false, error = chatRes.message) }
                }
            }
        }
    }

    suspend fun checkDirectChat(userId: String): DirectChatCheckResult {
        return when (val existing = api.getDirectChat(userId)) {
            is ApiResult.Success -> {
                val chat = normalizeChat(existing.value)
                upsertChat(chat)
                openChat(chat.id)
                DirectChatCheckResult.Existing(chat)
            }
            is ApiResult.Failure -> {
                if (existing.status == 404) {
                    DirectChatCheckResult.NeedsFirstMessage
                } else {
                    _state.update { it.copy(error = existing.message) }
                    DirectChatCheckResult.Error(existing.message)
                }
            }
        }
    }

    suspend fun startDirectChat(userId: String, initialText: String): ChatDto? {
        val text = initialText.trim()
        if (text.isEmpty()) return null
        return when (val created = api.createDirectChat(userId, text)) {
            is ApiResult.Success -> {
                val chat = normalizeChat(created.value)
                upsertChat(chat)
                openChat(chat.id)
                chat
            }
            is ApiResult.Failure -> {
                _state.update { it.copy(error = created.message) }
                null
            }
        }
    }

    suspend fun openDirectChat(userId: String, initialText: String = ""): ChatDto? {
        val existing = api.getDirectChat(userId)
        val chat = when (existing) {
            is ApiResult.Success -> existing.value
            is ApiResult.Failure -> {
                if (initialText.isNotBlank() || existing.status == 404) {
                    when (val created = api.createDirectChat(userId, initialText)) {
                        is ApiResult.Success -> created.value
                        is ApiResult.Failure -> {
                            _state.update { it.copy(error = created.message) }
                            return null
                        }
                    }
                } else {
                    _state.update { it.copy(error = existing.message) }
                    return null
                }
            }
        }
        upsertChat(chat)
        openChat(chat.id)
        return chat
    }

    suspend fun createGroupChat(name: String): ChatDto? {
        return when (val res = api.createGroupChat(name)) {
            is ApiResult.Success -> {
                val chat = res.value
                upsertChat(chat)
                openChat(chat.id)
                chat
            }
            is ApiResult.Failure -> {
                _state.update { it.copy(error = res.message) }
                null
            }
        }
    }

    fun loadMoreMessages() {
        val current = _state.value
        val chatId = current.activeChat?.id ?: return
        val cursor = current.nextMessagesCursor ?: return
        if (current.isLoadingMessages || current.isLoadingMoreMessages) return
        scope.launch {
            _state.update { it.copy(isLoadingMoreMessages = true) }
            when (val res = api.getChatMessages(chatId, cursor = cursor, limit = Limits.CHAT_MESSAGE_PAGE_SIZE)) {
                is ApiResult.Success -> {
                    _state.update { prev ->
                        val seen = prev.messages.mapTo(HashSet()) { it.id }
                        val older = res.value.messages.filter { seen.add(it.id) }
                        prev.copy(
                            messages = prev.messages + older,
                            nextMessagesCursor = res.value.nextCursor,
                            isLoadingMoreMessages = false,
                        )
                    }
                }
                is ApiResult.Failure -> _state.update { it.copy(isLoadingMoreMessages = false, error = res.message) }
            }
        }
    }

    suspend fun sendMessage(chatId: String, text: String, photos: List<ByteArray> = emptyList()): ChatMessageDto? {
        _state.update { it.copy(isSending = true, error = null) }
        return when (val res = api.sendChatMessage(chatId, text, photos)) {
            is ApiResult.Success -> {
                val sent = normalizeMessage(res.value)
                _state.update { prev ->
                    val nextMsgs = if (prev.activeChat?.id == chatId) {
                        if (prev.messages.any { it.id == sent.id }) prev.messages else listOf(sent) + prev.messages
                    } else {
                        prev.messages
                    }
                    prev.copy(messages = nextMsgs, isSending = false)
                }
                sent
            }
            is ApiResult.Failure -> {
                _state.update { it.copy(isSending = false, error = res.message) }
                null
            }
        }
    }

    suspend fun deleteMessage(chatId: String, messageId: String): Boolean {
        return when (val res = api.deleteChatMessage(chatId, messageId)) {
            is ApiResult.Success -> {
                _state.update { prev ->
                    prev.copy(messages = prev.messages.filterNot { it.id == messageId })
                }
                true
            }
            is ApiResult.Failure -> {
                _state.update { it.copy(error = res.message) }
                false
            }
        }
    }

    suspend fun toggleReaction(chatId: String, messageId: String, emoji: String, currentlyMine: Boolean) {
        val res = if (currentlyMine) {
            api.removeMessageReaction(chatId, messageId, emoji)
        } else {
            api.addMessageReaction(chatId, messageId, emoji)
        }
        when (res) {
            is ApiResult.Success -> updateMessageReactions(messageId, res.value.reactions)
            is ApiResult.Failure -> _state.update { it.copy(error = res.message) }
        }
    }

    fun loadReactionDetails(chatId: String, messageId: String) {
        scope.launch {
            when (val res = api.getMessageReactionDetails(chatId, messageId)) {
                is ApiResult.Success -> _state.update { it.copy(reactionDetails = res.value.reactions) }
                is ApiResult.Failure -> _state.update { it.copy(error = res.message) }
            }
        }
    }

    fun sendTyping(chatId: String) {
        scope.launch { api.sendChatTyping(chatId) }
    }

    fun markChatRead(chatId: String) {
        scope.launch {
            when (val res = api.markChatRead(chatId)) {
                is ApiResult.Success -> {
                    _state.update { prev ->
                        prev.copy(
                            unreadCount = res.value.unreadCount,
                            chats = prev.chats.map { if (it.id == chatId) it.copy(unreadCount = 0) else it },
                            activeChat = prev.activeChat?.let { if (it.id == chatId) it.copy(unreadCount = 0) else it },
                        )
                    }
                }
                is ApiResult.Failure -> Unit
            }
        }
    }

    suspend fun renameChat(chatId: String, name: String): ChatDto? {
        return when (val res = api.renameChat(chatId, name)) {
            is ApiResult.Success -> {
                val updated = normalizeChat(res.value)
                upsertChat(updated)
                updated
            }
            is ApiResult.Failure -> {
                _state.update { it.copy(error = res.message) }
                null
            }
        }
    }

    suspend fun leaveChat(chatId: String): Boolean {
        return when (val res = api.leaveChat(chatId)) {
            is ApiResult.Success -> {
                removeChat(chatId)
                true
            }
            is ApiResult.Failure -> {
                _state.update { it.copy(error = res.message) }
                false
            }
        }
    }

    suspend fun deleteChat(chatId: String): Boolean {
        return when (val res = api.deleteChat(chatId)) {
            is ApiResult.Success -> {
                removeChat(chatId)
                true
            }
            is ApiResult.Failure -> {
                _state.update { it.copy(error = res.message) }
                false
            }
        }
    }

    fun loadMembers(chatId: String) {
        scope.launch {
            when (val res = api.getChatMembers(chatId)) {
                is ApiResult.Success -> _state.update { it.copy(members = res.value.members) }
                is ApiResult.Failure -> _state.update { it.copy(error = res.message) }
            }
        }
    }

    suspend fun addMember(chatId: String, userId: String): Boolean {
        return when (val res = api.addChatMember(chatId, userId)) {
            is ApiResult.Success -> {
                upsertChat(normalizeChat(res.value))
                loadMembers(chatId)
                true
            }
            is ApiResult.Failure -> {
                _state.update { it.copy(error = res.message) }
                false
            }
        }
    }

    suspend fun removeMember(chatId: String, userId: String): Boolean {
        return when (val res = api.removeChatMember(chatId, userId)) {
            is ApiResult.Success -> {
                upsertChat(normalizeChat(res.value))
                loadMembers(chatId)
                true
            }
            is ApiResult.Failure -> {
                _state.update { it.copy(error = res.message) }
                false
            }
        }
    }

    suspend fun makeOwner(chatId: String, userId: String): Boolean {
        return when (val res = api.makeChatOwner(chatId, userId)) {
            is ApiResult.Success -> {
                upsertChat(normalizeChat(res.value))
                loadMembers(chatId)
                true
            }
            is ApiResult.Failure -> {
                _state.update { it.copy(error = res.message) }
                false
            }
        }
    }

    fun searchPeople(query: String, chatId: String? = null) {
        scope.launch {
            val res = if (chatId.isNullOrBlank()) {
                api.searchPeople(query)
            } else {
                api.searchChatPeopleToAdd(chatId, query)
            }
            when (res) {
                is ApiResult.Success -> _state.update { it.copy(peopleResults = res.value.people) }
                is ApiResult.Failure -> Unit
            }
        }
    }

    fun closeActiveChat() {
        _state.update {
            it.copy(
                activeChat = null,
                messages = emptyList(),
                nextMessagesCursor = null,
                members = emptyList(),
                typingUserName = null,
            )
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    private suspend fun reloadActiveMessages(chatId: String) {
        when (val msgRes = api.getChatMessages(chatId, cursor = null, limit = Limits.CHAT_MESSAGE_PAGE_SIZE)) {
            is ApiResult.Success -> _state.update {
                if (it.activeChat?.id == chatId) {
                    it.copy(messages = msgRes.value.messages, nextMessagesCursor = msgRes.value.nextCursor)
                } else {
                    it
                }
            }
            is ApiResult.Failure -> Unit
        }
    }

    private fun handleStreamEvent(event: ChatStreamEventDto) {
        event.unreadCount?.let { count ->
            _state.update { it.copy(unreadCount = count) }
        }
        when (event.type) {
            "chat" -> event.chat?.let { upsertChat(normalizeChat(it)) }
            "chat_deleted" -> event.chatId?.let(::removeChat)
            "message" -> {
                event.chat?.let { upsertChat(normalizeChat(it)) }
                val msg = event.message?.let(::normalizeMessage) ?: return
                val activeId = _state.value.activeChat?.id
                if (activeId == msg.chatId) {
                    _state.update { prev ->
                        val nextMsgs = if (prev.messages.any { it.id == msg.id }) {
                            prev.messages
                        } else {
                            listOf(msg) + prev.messages
                        }
                        prev.copy(messages = nextMsgs, typingUserName = null)
                    }
                    markChatRead(msg.chatId)
                }
            }
            "message_deleted" -> {
                event.chat?.let { upsertChat(normalizeChat(it)) }
                val msgId = event.messageId ?: return
                _state.update { prev ->
                    prev.copy(messages = prev.messages.filterNot { it.id == msgId })
                }
            }
            "reaction" -> {
                val msgId = event.messageId ?: return
                val reactions = event.reactions ?: return
                updateMessageReactions(msgId, reactions)
            }
            "typing" -> {
                val chatId = event.chatId ?: return
                if (_state.value.activeChat?.id == chatId) {
                    val name = event.userName?.takeIf { it.isNotBlank() } ?: return
                    _state.update { it.copy(typingUserName = name) }
                    typingClearJob?.cancel()
                    typingClearJob = scope.launch {
                        delay(3_000L)
                        _state.update { it.copy(typingUserName = null) }
                    }
                }
            }
        }
    }

    private fun upsertChat(chat: ChatDto) {
        _state.update { prev ->
            val currentActive = prev.activeChat
            val nextActive = if (currentActive?.id == chat.id) {
                chat.copy(
                    defaultReactions = chat.defaultReactions.ifEmpty { currentActive.defaultReactions },
                )
            } else {
                currentActive
            }
            val filter = prev.filterType
            val matchesFilter = filter == null || chat.type == filter
            val nextList = if (matchesFilter) {
                (listOf(chat) + prev.chats.filterNot { it.id == chat.id })
                    .sortedWith(compareByDescending<ChatDto> { it.lastMessageAt }.thenByDescending { it.id })
            } else {
                prev.chats
            }
            prev.copy(chats = nextList, activeChat = nextActive)
        }
    }

    private fun removeChat(chatId: String) {
        _state.update { prev ->
            prev.copy(
                chats = prev.chats.filterNot { it.id == chatId },
                activeChat = if (prev.activeChat?.id == chatId) null else prev.activeChat,
                messages = if (prev.activeChat?.id == chatId) emptyList() else prev.messages,
            )
        }
    }

    private fun updateMessageReactions(messageId: String, reactions: List<ReactionCountDto>) {
        _state.update { prev ->
            prev.copy(
                messages = prev.messages.map { msg ->
                    if (msg.id == messageId) msg.copy(reactions = reactions) else msg
                },
            )
        }
    }

    private fun normalizeChat(chat: ChatDto): ChatDto = chat.copy(
        photos = chat.photos.map { it.copy(photoUrl = it.photoUrl?.let(api::resolveUrl)) },
    )

    private fun normalizeMessage(msg: ChatMessageDto): ChatMessageDto = msg.copy(
        authorPhotoUrl = msg.authorPhotoUrl?.let(api::resolveUrl),
        photoUrls = msg.photoUrls.map(api::resolveUrl),
        attachment = msg.attachment?.copy(photoUrl = msg.attachment.photoUrl?.let(api::resolveUrl)),
    )
}
