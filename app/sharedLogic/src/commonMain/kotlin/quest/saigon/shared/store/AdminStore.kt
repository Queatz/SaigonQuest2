package quest.saigon.shared.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import quest.saigon.shared.model.AdminPostDto
import quest.saigon.shared.model.AdminUserDto
import quest.saigon.shared.model.CommentCardDto
import quest.saigon.shared.network.ApiResult
import quest.saigon.shared.network.TownApiClient

data class AdminUiState(
    val isAdmin: Boolean = false,
    val checked: Boolean = false,
    val users: List<AdminUserDto> = emptyList(),
    val usersNextCursor: String? = null,
    val posts: List<AdminPostDto> = emptyList(),
    val postsNextCursor: String? = null,
    val comments: List<CommentCardDto> = emptyList(),
    val commentsNextCursor: String? = null,
    val isLoading: Boolean = false,
    val isMutating: Boolean = false,
    val error: String? = null,
)

class AdminStore(
    private val api: TownApiClient,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(AdminUiState())
    val state: StateFlow<AdminUiState> = _state.asStateFlow()

    suspend fun verifyAdmin(): Boolean {
        _state.update { it.copy(isLoading = true, error = null) }
        return when (val res = api.claimOrCheckAdmin()) {
            is ApiResult.Success -> {
                val ok = res.value.admin
                _state.update { it.copy(isAdmin = ok, checked = true, isLoading = false) }
                ok
            }
            is ApiResult.Failure -> {
                _state.update { it.copy(isAdmin = false, checked = true, isLoading = false, error = res.message) }
                false
            }
        }
    }

    fun loadUsers(loadMore: Boolean = false) {
        val current = _state.value
        val cursor = if (loadMore) current.usersNextCursor ?: return else null
        scope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            when (val res = api.getAdminUsers(cursor = cursor)) {
                is ApiResult.Success -> _state.update { prev ->
                    val items = if (loadMore) {
                        val seen = prev.users.mapTo(HashSet()) { it.id }
                        prev.users + res.value.users.filter { seen.add(it.id) }
                    } else {
                        res.value.users
                    }
                    prev.copy(users = items, usersNextCursor = res.value.nextCursor, isLoading = false)
                }
                is ApiResult.Failure -> _state.update { it.copy(isLoading = false, error = res.message) }
            }
        }
    }

    suspend fun setUserPaused(userId: String, paused: Boolean): Boolean {
        _state.update { it.copy(isMutating = true, error = null) }
        val res = if (paused) api.pauseUser(userId) else api.unpauseUser(userId)
        return when (res) {
            is ApiResult.Success -> {
                _state.update { prev ->
                    prev.copy(
                        users = prev.users.map { if (it.id == userId) it.copy(paused = res.value.paused) else it },
                        isMutating = false,
                    )
                }
                true
            }
            is ApiResult.Failure -> {
                _state.update { it.copy(isMutating = false, error = res.message) }
                false
            }
        }
    }

    fun loadPosts(loadMore: Boolean = false) {
        val current = _state.value
        val cursor = if (loadMore) current.postsNextCursor ?: return else null
        scope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            when (val res = api.getAdminPosts(cursor = cursor)) {
                is ApiResult.Success -> _state.update { prev ->
                    val items = if (loadMore) {
                        val seen = prev.posts.mapTo(HashSet()) { it.id }
                        prev.posts + res.value.posts.filter { seen.add(it.id) }
                    } else {
                        res.value.posts
                    }
                    prev.copy(posts = items, postsNextCursor = res.value.nextCursor, isLoading = false)
                }
                is ApiResult.Failure -> _state.update { it.copy(isLoading = false, error = res.message) }
            }
        }
    }

    suspend fun deletePost(postId: String): Boolean {
        _state.update { it.copy(isMutating = true, error = null) }
        return when (val res = api.deleteAdminPost(postId)) {
            is ApiResult.Success -> {
                _state.update { prev ->
                    prev.copy(posts = prev.posts.filterNot { it.id == postId }, isMutating = false)
                }
                true
            }
            is ApiResult.Failure -> {
                _state.update { it.copy(isMutating = false, error = res.message) }
                false
            }
        }
    }

    fun loadComments(loadMore: Boolean = false) {
        val current = _state.value
        val cursor = if (loadMore) current.commentsNextCursor ?: return else null
        scope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            when (val res = api.getAdminComments(cursor = cursor)) {
                is ApiResult.Success -> _state.update { prev ->
                    val items = if (loadMore) {
                        val seen = prev.comments.mapTo(HashSet()) { it.id }
                        prev.comments + res.value.comments.filter { seen.add(it.id) }
                    } else {
                        res.value.comments
                    }
                    prev.copy(comments = items, commentsNextCursor = res.value.nextCursor, isLoading = false)
                }
                is ApiResult.Failure -> _state.update { it.copy(isLoading = false, error = res.message) }
            }
        }
    }

    suspend fun deleteComment(commentId: String): Boolean {
        _state.update { it.copy(isMutating = true, error = null) }
        return when (val res = api.deleteAdminComment(commentId)) {
            is ApiResult.Success -> {
                _state.update { prev ->
                    prev.copy(comments = prev.comments.filterNot { it.id == commentId }, isMutating = false)
                }
                true
            }
            is ApiResult.Failure -> {
                _state.update { it.copy(isMutating = false, error = res.message) }
                false
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
