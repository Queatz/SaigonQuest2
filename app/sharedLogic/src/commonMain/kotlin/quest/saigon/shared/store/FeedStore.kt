package quest.saigon.shared.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import quest.saigon.Limits
import quest.saigon.shared.model.FeedPageDto
import quest.saigon.shared.model.PostCardDto
import quest.saigon.shared.network.ApiResult
import quest.saigon.shared.network.TownApiClient

sealed interface FeedFilter {
    data object Home : FeedFilter
    data class Community(val id: String) : FeedFilter
    data class Search(val query: String) : FeedFilter
    data class Author(val userId: String) : FeedFilter
    data class Place(
        val name: String,
        val placeId: String? = null,
        val locationId: String? = null,
    ) : FeedFilter
}

data class FeedUiState(
    val filter: FeedFilter = FeedFilter.Home,
    val posts: List<PostCardDto> = emptyList(),
    val nextCursor: String? = null,
    val totalCount: Long? = null,
    val selectedPostId: String? = null,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: String? = null,
) {
    val selectedPost: PostCardDto?
        get() = selectedPostId?.let { id -> posts.firstOrNull { it.id == id } } ?: posts.firstOrNull()
}

class FeedStore(
    private val api: TownApiClient,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(FeedUiState())
    val state: StateFlow<FeedUiState> = _state.asStateFlow()

    private var activeLoadJob: Job? = null
    private val viewedPostIds = mutableSetOf<String>()

    fun loadInitial(
        filter: FeedFilter = FeedFilter.Home,
        focusPostId: String? = null,
    ) {
        activeLoadJob?.cancel()
        activeLoadJob = scope.launch {
            _state.update {
                it.copy(
                    filter = filter,
                    isLoading = true,
                    isLoadingMore = false,
                    error = null,
                )
            }
            val result = fetchPage(filter, cursor = null)
            when (result) {
                is ApiResult.Success -> {
                    val page = result.value
                    var posts = page.posts
                    if (!focusPostId.isNullOrBlank() && posts.none { it.id == focusPostId }) {
                        val focused = api.getPost(focusPostId)
                        if (focused is ApiResult.Success) {
                            posts = listOf(focused.value) + posts
                        }
                    }
                    val selected = when {
                        !focusPostId.isNullOrBlank() && posts.any { it.id == focusPostId } -> focusPostId
                        else -> posts.firstOrNull()?.id
                    }
                    _state.update {
                        it.copy(
                            posts = posts,
                            nextCursor = page.nextCursor,
                            totalCount = page.count,
                            selectedPostId = selected,
                            isLoading = false,
                            error = null,
                        )
                    }
                    if (selected != null) markViewed(selected)
                }
                is ApiResult.Failure -> {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            error = result.message,
                        )
                    }
                }
            }
        }
    }

    fun refresh() {
        val current = _state.value
        activeLoadJob?.cancel()
        activeLoadJob = scope.launch {
            _state.update { it.copy(isRefreshing = true, error = null) }
            when (val result = fetchPage(current.filter, cursor = null)) {
                is ApiResult.Success -> {
                    val page = result.value
                    val selected = current.selectedPostId?.takeIf { id -> page.posts.any { it.id == id } }
                        ?: page.posts.firstOrNull()?.id
                    _state.update {
                        it.copy(
                            posts = page.posts,
                            nextCursor = page.nextCursor,
                            totalCount = page.count,
                            selectedPostId = selected,
                            isRefreshing = false,
                            error = null,
                        )
                    }
                }
                is ApiResult.Failure -> {
                    _state.update { it.copy(isRefreshing = false, error = result.message) }
                }
            }
        }
    }

    fun loadMore() {
        val current = _state.value
        val cursor = current.nextCursor ?: return
        if (current.isLoading || current.isLoadingMore) return
        scope.launch {
            _state.update { it.copy(isLoadingMore = true, error = null) }
            when (val result = fetchPage(current.filter, cursor = cursor)) {
                is ApiResult.Success -> {
                    val page = result.value
                    _state.update { prev ->
                        val seen = prev.posts.mapTo(HashSet()) { it.id }
                        val merged = prev.posts + page.posts.filter { seen.add(it.id) }
                        prev.copy(
                            posts = merged,
                            nextCursor = page.nextCursor,
                            totalCount = page.count ?: prev.totalCount,
                            isLoadingMore = false,
                        )
                    }
                }
                is ApiResult.Failure -> {
                    _state.update { it.copy(isLoadingMore = false, error = result.message) }
                }
            }
        }
    }

    fun selectPost(postId: String) {
        _state.update { it.copy(selectedPostId = postId) }
        markViewed(postId)
    }

    fun applyPostUpdate(updated: PostCardDto) {
        _state.update { prev ->
            val exists = prev.posts.any { it.id == updated.id }
            val nextPosts = if (exists) {
                prev.posts.map { if (it.id == updated.id) updated else it }
            } else {
                listOf(updated) + prev.posts
            }
            prev.copy(posts = nextPosts)
        }
    }

    fun prependPost(created: PostCardDto) {
        _state.update { prev ->
            val filtered = prev.posts.filterNot { it.id == created.id }
            prev.copy(
                posts = listOf(created) + filtered,
                selectedPostId = created.id,
                totalCount = prev.totalCount?.let { it + 1 },
            )
        }
    }

    fun removePost(postId: String) {
        _state.update { prev ->
            val nextPosts = prev.posts.filterNot { it.id == postId }
            val nextSelected = if (prev.selectedPostId == postId) nextPosts.firstOrNull()?.id else prev.selectedPostId
            prev.copy(
                posts = nextPosts,
                selectedPostId = nextSelected,
                totalCount = prev.totalCount?.let { (it - 1).coerceAtLeast(0L) },
            )
        }
    }

    fun updatePostMessage(postId: String, message: String) {
        _state.update { prev ->
            prev.copy(posts = prev.posts.map { if (it.id == postId) it.copy(message = message) else it })
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    private fun markViewed(postId: String) {
        if (!viewedPostIds.add(postId)) return
        scope.launch {
            api.recordPostView(postId)
        }
    }

    private suspend fun fetchPage(filter: FeedFilter, cursor: String?): ApiResult<FeedPageDto> =
        when (filter) {
            FeedFilter.Home -> api.getFeed(cursor = cursor, limit = Limits.FEED_PAGE_SIZE)
            is FeedFilter.Community -> api.getCommunityPosts(filter.id, cursor = cursor, limit = Limits.FEED_PAGE_SIZE)
            is FeedFilter.Search -> api.searchPosts(filter.query, cursor = cursor, limit = 24)
            is FeedFilter.Author -> api.getUserPosts(filter.userId, cursor = cursor, limit = 24)
            is FeedFilter.Place -> api.getPlacePosts(
                name = filter.name,
                placeId = filter.placeId,
                locationId = filter.locationId,
                cursor = cursor,
                limit = 24,
            )
        }
}
