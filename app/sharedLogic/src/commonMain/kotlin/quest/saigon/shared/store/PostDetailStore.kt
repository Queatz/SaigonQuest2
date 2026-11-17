package quest.saigon.shared.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import quest.saigon.Limits
import quest.saigon.shared.model.ChatDto
import quest.saigon.shared.model.CommentCardDto
import quest.saigon.shared.model.EventPersonDto
import quest.saigon.shared.model.PollChoiceDto
import quest.saigon.shared.model.PostCardDto
import quest.saigon.shared.model.QuestionAnswerDto
import quest.saigon.shared.network.ApiResult
import quest.saigon.shared.network.TownApiClient

data class PostDetailUiState(
    val postId: String? = null,
    val post: PostCardDto? = null,
    val comments: List<CommentCardDto> = emptyList(),
    val commentsNextCursor: String? = null,
    val commentsTotal: Long = 0L,
    val isLoadingComments: Boolean = false,
    val isLoadingMoreComments: Boolean = false,
    val isPostingComment: Boolean = false,
    val attendees: List<EventPersonDto> = emptyList(),
    val attendeesNextCursor: String? = null,
    val isLoadingAttendees: Boolean = false,
    val pollVoters: List<PollChoiceDto> = emptyList(),
    val isLoadingPollVoters: Boolean = false,
    val questionAnswers: List<QuestionAnswerDto> = emptyList(),
    val isLoadingAnswers: Boolean = false,
    val isMutating: Boolean = false,
    val lastZaloUrl: String? = null,
    val error: String? = null,
)

class PostDetailStore(
    private val api: TownApiClient,
    private val feedStore: FeedStore,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(PostDetailUiState())
    val state: StateFlow<PostDetailUiState> = _state.asStateFlow()

    fun bindPost(post: PostCardDto) {
        _state.update { prev ->
            if (prev.postId == post.id) {
                prev.copy(post = post, error = null)
            } else {
                PostDetailUiState(postId = post.id, post = post)
            }
        }
    }

    suspend fun loadPost(postId: String): PostCardDto? {
        return when (val result = api.getPost(postId)) {
            is ApiResult.Success -> {
                val post = result.value
                bindPost(post)
                feedStore.applyPostUpdate(post)
                post
            }
            is ApiResult.Failure -> {
                _state.update { it.copy(error = result.message) }
                null
            }
        }
    }

    fun openComments(postId: String) {
        scope.launch {
            _state.update {
                it.copy(
                    postId = postId,
                    comments = emptyList(),
                    commentsNextCursor = null,
                    commentsTotal = 0L,
                    isLoadingComments = true,
                    error = null,
                )
            }
            when (val result = api.getComments(postId, cursor = null, limit = Limits.COMMENT_PAGE_SIZE)) {
                is ApiResult.Success -> {
                    val page = result.value
                    _state.update {
                        it.copy(
                            comments = page.comments,
                            commentsNextCursor = page.nextCursor,
                            commentsTotal = page.total,
                            isLoadingComments = false,
                        )
                    }
                }
                is ApiResult.Failure -> {
                    _state.update { it.copy(isLoadingComments = false, error = result.message) }
                }
            }
        }
    }

    fun loadMoreComments() {
        val current = _state.value
        val postId = current.postId ?: return
        val cursor = current.commentsNextCursor ?: return
        if (current.isLoadingComments || current.isLoadingMoreComments) return
        scope.launch {
            _state.update { it.copy(isLoadingMoreComments = true, error = null) }
            when (val result = api.getComments(postId, cursor = cursor, limit = Limits.COMMENT_PAGE_SIZE)) {
                is ApiResult.Success -> {
                    val page = result.value
                    _state.update { prev ->
                        val seen = prev.comments.mapTo(HashSet()) { it.id }
                        prev.copy(
                            comments = prev.comments + page.comments.filter { seen.add(it.id) },
                            commentsNextCursor = page.nextCursor,
                            commentsTotal = page.total,
                            isLoadingMoreComments = false,
                        )
                    }
                }
                is ApiResult.Failure -> {
                    _state.update { it.copy(isLoadingMoreComments = false, error = result.message) }
                }
            }
        }
    }

    suspend fun addComment(postId: String, text: String, replyToId: String? = null): CommentCardDto? {
        _state.update { it.copy(isPostingComment = true, error = null) }
        return when (val result = api.addComment(postId, text, replyToId)) {
            is ApiResult.Success -> {
                val created = result.value
                _state.update { prev ->
                    val updatedComments = if (prev.postId == postId) listOf(created) + prev.comments else prev.comments
                    val nextTotal = if (prev.postId == postId) prev.commentsTotal + 1 else prev.commentsTotal
                    prev.copy(
                        comments = updatedComments,
                        commentsTotal = nextTotal,
                        isPostingComment = false,
                        error = null,
                    )
                }
                syncFeedPost(postId) { post ->
                    val previews = (listOf(created) + post.comments.filterNot { it.id == created.id }).take(Limits.COMMENT_PREVIEW)
                    post.copy(commentCount = post.commentCount + 1, comments = previews)
                }
                created
            }
            is ApiResult.Failure -> {
                _state.update { it.copy(isPostingComment = false, error = result.message) }
                null
            }
        }
    }

    suspend fun deleteComment(postId: String, commentId: String): Boolean {
        _state.update { it.copy(isMutating = true, error = null) }
        return when (val result = api.deleteComment(postId, commentId)) {
            is ApiResult.Success -> {
                _state.update { prev ->
                    prev.copy(
                        comments = prev.comments.filterNot { it.id == commentId },
                        commentsTotal = (prev.commentsTotal - 1).coerceAtLeast(0L),
                        isMutating = false,
                    )
                }
                syncFeedPost(postId) { post ->
                    post.copy(
                        commentCount = (post.commentCount - 1).coerceAtLeast(0),
                        comments = post.comments.filterNot { it.id == commentId },
                    )
                }
                true
            }
            is ApiResult.Failure -> {
                _state.update { it.copy(isMutating = false, error = result.message) }
                false
            }
        }
    }

    suspend fun joinEvent(postId: String): PostCardDto? = mutatePost { api.joinEvent(postId) }

    suspend fun leaveEvent(postId: String): PostCardDto? = mutatePost { api.leaveEvent(postId) }

    suspend fun votePoll(postId: String, optionIndex: Int): PostCardDto? = mutatePost {
        api.votePoll(postId, optionIndex)
    }

    suspend fun answerQuestion(postId: String, text: String): PostCardDto? = mutatePost {
        api.answerQuestion(postId, text)
    }

    suspend fun joinOpenChat(postId: String): ChatDto? {
        _state.update { it.copy(isMutating = true, error = null) }
        return when (val result = api.joinOpenChat(postId)) {
            is ApiResult.Success -> {
                val chat = result.value
                _state.update { it.copy(isMutating = false) }
                syncFeedPost(postId) { post ->
                    post.copy(
                        openChatJoined = true,
                        openChatRemaining = chat.openChatRemaining ?: post.openChatRemaining,
                    )
                }
                chat
            }
            is ApiResult.Failure -> {
                _state.update { it.copy(isMutating = false, error = result.message) }
                null
            }
        }
    }

    fun loadAttendees(postId: String, loadMore: Boolean = false) {
        val current = _state.value
        val cursor = if (loadMore) current.attendeesNextCursor ?: return else null
        scope.launch {
            _state.update { it.copy(isLoadingAttendees = true, error = null) }
            when (val result = api.getEventAttendees(postId, cursor = cursor, limit = 30)) {
                is ApiResult.Success -> {
                    val page = result.value
                    _state.update { prev ->
                        val items = if (loadMore) {
                            val seen = prev.attendees.mapTo(HashSet()) { it.id }
                            prev.attendees + page.attendees.filter { seen.add(it.id) }
                        } else {
                            page.attendees
                        }
                        prev.copy(
                            attendees = items,
                            attendeesNextCursor = page.nextCursor,
                            isLoadingAttendees = false,
                        )
                    }
                }
                is ApiResult.Failure -> {
                    _state.update { it.copy(isLoadingAttendees = false, error = result.message) }
                }
            }
        }
    }

    fun loadPollVoters(postId: String) {
        scope.launch {
            _state.update { it.copy(isLoadingPollVoters = true, error = null) }
            when (val result = api.getPollVoters(postId)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(pollVoters = result.value.options, isLoadingPollVoters = false) }
                }
                is ApiResult.Failure -> {
                    _state.update { it.copy(isLoadingPollVoters = false, error = result.message) }
                }
            }
        }
    }

    fun loadQuestionAnswers(postId: String) {
        scope.launch {
            _state.update { it.copy(isLoadingAnswers = true, error = null) }
            when (val result = api.getQuestionAnswers(postId)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(questionAnswers = result.value.answers, isLoadingAnswers = false) }
                }
                is ApiResult.Failure -> {
                    _state.update { it.copy(isLoadingAnswers = false, error = result.message) }
                }
            }
        }
    }

    suspend fun updateCheckIn(postId: String, minutes: Int): Long? {
        _state.update { it.copy(isMutating = true, error = null) }
        return when (val result = api.updateCheckIn(postId, minutes)) {
            is ApiResult.Success -> {
                _state.update { it.copy(isMutating = false) }
                result.value.untilAt
            }
            is ApiResult.Failure -> {
                _state.update { it.copy(isMutating = false, error = result.message) }
                null
            }
        }
    }

    suspend fun removeCheckIn(postId: String): Boolean {
        _state.update { it.copy(isMutating = true, error = null) }
        return when (val result = api.deleteCheckIn(postId)) {
            is ApiResult.Success -> {
                _state.update { it.copy(isMutating = false) }
                true
            }
            is ApiResult.Failure -> {
                _state.update { it.copy(isMutating = false, error = result.message) }
                false
            }
        }
    }

    suspend fun contactAuthor(postId: String): String? {
        return when (val result = api.contactPostAuthor(postId)) {
            is ApiResult.Success -> {
                _state.update { it.copy(lastZaloUrl = result.value.zaloUrl) }
                result.value.zaloUrl
            }
            is ApiResult.Failure -> {
                _state.update { it.copy(error = result.message) }
                null
            }
        }
    }

    fun sharePost(postId: String) {
        scope.launch { api.recordPostShare(postId) }
    }

    suspend fun updatePostMessage(postId: String, message: String): Boolean {
        _state.update { it.copy(isMutating = true, error = null) }
        return when (val result = api.patchPost(postId, message)) {
            is ApiResult.Success -> {
                val updatedMessage = result.value.message
                _state.update { prev ->
                    prev.copy(
                        post = prev.post?.takeIf { it.id == postId }?.copy(message = updatedMessage) ?: prev.post,
                        isMutating = false,
                    )
                }
                feedStore.updatePostMessage(postId, updatedMessage)
                true
            }
            is ApiResult.Failure -> {
                _state.update { it.copy(isMutating = false, error = result.message) }
                false
            }
        }
    }

    suspend fun deletePost(postId: String): Boolean {
        _state.update { it.copy(isMutating = true, error = null) }
        return when (val result = api.deletePost(postId)) {
            is ApiResult.Success -> {
                feedStore.removePost(postId)
                _state.update { prev ->
                    if (prev.postId == postId) PostDetailUiState() else prev.copy(isMutating = false)
                }
                true
            }
            is ApiResult.Failure -> {
                _state.update { it.copy(isMutating = false, error = result.message) }
                false
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun clearLastZaloUrl() {
        _state.update { it.copy(lastZaloUrl = null) }
    }

    private suspend fun mutatePost(call: suspend () -> ApiResult<PostCardDto>): PostCardDto? {
        _state.update { it.copy(isMutating = true, error = null) }
        return when (val result = call()) {
            is ApiResult.Success -> {
                val updated = result.value
                _state.update { prev ->
                    prev.copy(
                        post = if (prev.postId == updated.id) updated else prev.post,
                        isMutating = false,
                    )
                }
                feedStore.applyPostUpdate(updated)
                updated
            }
            is ApiResult.Failure -> {
                _state.update { it.copy(isMutating = false, error = result.message) }
                null
            }
        }
    }

    private fun syncFeedPost(postId: String, transform: (PostCardDto) -> PostCardDto) {
        val current = feedStore.state.value.posts.firstOrNull { it.id == postId }
            ?: _state.value.post?.takeIf { it.id == postId }
            ?: return
        val updated = transform(current)
        feedStore.applyPostUpdate(updated)
        _state.update { prev ->
            if (prev.postId == postId) prev.copy(post = updated) else prev
        }
    }
}
