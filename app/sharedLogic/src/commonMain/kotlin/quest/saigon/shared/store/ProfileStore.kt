package quest.saigon.shared.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import quest.saigon.shared.model.*
import quest.saigon.shared.network.ApiResult
import quest.saigon.shared.network.TownApiClient

data class ProfileUiState(
    val myPosts: List<OwnedPostDto> = emptyList(),
    val myPostsNextCursor: String? = null,
    val myCommunities: List<CommunityDto> = emptyList(),
    val myMusic: List<MusicDto> = emptyList(),
    val myProducts: List<ProductDto> = emptyList(),
    val myProductsNextCursor: String? = null,
    val myActivities: List<ActivityDto> = emptyList(),
    val myActivitiesNextCursor: String? = null,
    val myOrders: List<ProductOrderDto> = emptyList(),
    val myOrdersNextCursor: String? = null,
    val openOrdersCount: Long = 0L,
    val features: List<FeatureSuggestionDto> = emptyList(),
    val viewedUser: UserProfileDto? = null,
    val viewedUserPosts: List<PostCardDto> = emptyList(),
    val viewedUserPostsNextCursor: String? = null,
    val viewedUserPostsCount: Long = 0L,
    val viewedUserCommunities: List<CommunityDto> = emptyList(),
    val viewedUserActivities: List<ActivityDto> = emptyList(),
    val viewedUserProducts: List<ProductDto> = emptyList(),
    val isLoading: Boolean = false,
    val isMutating: Boolean = false,
    val error: String? = null,
)

class ProfileStore(
    private val api: TownApiClient,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    fun loadMyPosts(loadMore: Boolean = false) {
        val current = _state.value
        val cursor = if (loadMore) current.myPostsNextCursor ?: return else null
        scope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            when (val res = api.getMyPosts(cursor = cursor)) {
                is ApiResult.Success -> _state.update { prev ->
                    val items = if (loadMore) {
                        val seen = prev.myPosts.mapTo(HashSet()) { it.id }
                        prev.myPosts + res.value.posts.filter { seen.add(it.id) }
                    } else {
                        res.value.posts
                    }
                    prev.copy(myPosts = items, myPostsNextCursor = res.value.nextCursor, isLoading = false)
                }
                is ApiResult.Failure -> _state.update { it.copy(isLoading = false, error = res.message) }
            }
        }
    }

    suspend fun editMyPost(postId: String, message: String): Boolean {
        _state.update { it.copy(isMutating = true, error = null) }
        return when (val res = api.patchPost(postId, message)) {
            is ApiResult.Success -> {
                _state.update { prev ->
                    prev.copy(
                        myPosts = prev.myPosts.map { if (it.id == postId) it.copy(message = res.value.message) else it },
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

    suspend fun deleteMyPost(postId: String): Boolean {
        _state.update { it.copy(isMutating = true, error = null) }
        return when (val res = api.deletePost(postId)) {
            is ApiResult.Success -> {
                _state.update { prev ->
                    prev.copy(myPosts = prev.myPosts.filterNot { it.id == postId }, isMutating = false)
                }
                true
            }
            is ApiResult.Failure -> {
                _state.update { it.copy(isMutating = false, error = res.message) }
                false
            }
        }
    }

    fun loadMyCommunities() {
        scope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            when (val res = api.getMyCommunities()) {
                is ApiResult.Success -> _state.update { it.copy(myCommunities = res.value.communities, isLoading = false) }
                is ApiResult.Failure -> _state.update { it.copy(isLoading = false, error = res.message) }
            }
        }
    }

    suspend fun leaveMyCommunity(communityId: String): Boolean {
        return when (val res = api.leaveCommunity(communityId)) {
            is ApiResult.Success -> {
                _state.update { prev ->
                    prev.copy(myCommunities = prev.myCommunities.filterNot { it.id == communityId })
                }
                true
            }
            is ApiResult.Failure -> {
                _state.update { it.copy(error = res.message) }
                false
            }
        }
    }

    fun loadMyMusic() {
        scope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            when (val res = api.getMyMusic()) {
                is ApiResult.Success -> _state.update { it.copy(myMusic = res.value.music, isLoading = false) }
                is ApiResult.Failure -> _state.update { it.copy(isLoading = false, error = res.message) }
            }
        }
    }

    fun loadMyProducts(loadMore: Boolean = false) {
        val current = _state.value
        val cursor = if (loadMore) current.myProductsNextCursor ?: return else null
        scope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            when (val res = api.getMyProducts(cursor = cursor)) {
                is ApiResult.Success -> _state.update { prev ->
                    val items = if (loadMore) {
                        val seen = prev.myProducts.mapTo(HashSet()) { it.id }
                        prev.myProducts + res.value.products.filter { seen.add(it.id) }
                    } else {
                        res.value.products
                    }
                    prev.copy(myProducts = items, myProductsNextCursor = res.value.nextCursor, isLoading = false)
                }
                is ApiResult.Failure -> _state.update { it.copy(isLoading = false, error = res.message) }
            }
        }
    }

    fun loadMyActivities(loadMore: Boolean = false) {
        val current = _state.value
        val cursor = if (loadMore) current.myActivitiesNextCursor ?: return else null
        scope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            when (val res = api.getMyActivities(cursor = cursor)) {
                is ApiResult.Success -> _state.update { prev ->
                    val items = if (loadMore) {
                        val seen = prev.myActivities.mapTo(HashSet()) { it.id }
                        prev.myActivities + res.value.activities.filter { seen.add(it.id) }
                    } else {
                        res.value.activities
                    }
                    prev.copy(myActivities = items, myActivitiesNextCursor = res.value.nextCursor, isLoading = false)
                }
                is ApiResult.Failure -> _state.update { it.copy(isLoading = false, error = res.message) }
            }
        }
    }

    fun loadMyOrders(loadMore: Boolean = false) {
        val current = _state.value
        val cursor = if (loadMore) current.myOrdersNextCursor ?: return else null
        scope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            when (val res = api.getMyOrders(cursor = cursor)) {
                is ApiResult.Success -> _state.update { prev ->
                    val items = if (loadMore) {
                        val seen = prev.myOrders.mapTo(HashSet()) { it.id }
                        prev.myOrders + res.value.orders.filter { seen.add(it.id) }
                    } else {
                        res.value.orders
                    }
                    prev.copy(
                        myOrders = items,
                        myOrdersNextCursor = res.value.nextCursor,
                        openOrdersCount = res.value.openCount,
                        isLoading = false,
                    )
                }
                is ApiResult.Failure -> _state.update { it.copy(isLoading = false, error = res.message) }
            }
        }
    }

    suspend fun resolveOrder(orderId: String, resolved: Boolean): Boolean {
        _state.update { it.copy(isMutating = true, error = null) }
        return when (val res = api.resolveOrder(orderId, resolved)) {
            is ApiResult.Success -> {
                val updated = res.value
                _state.update { prev ->
                    val nextOrders = prev.myOrders.map { if (it.id == updated.id) updated else it }
                    val openCount = nextOrders.count { !it.resolved }.toLong()
                    prev.copy(myOrders = nextOrders, openOrdersCount = openCount, isMutating = false)
                }
                true
            }
            is ApiResult.Failure -> {
                _state.update { it.copy(isMutating = false, error = res.message) }
                false
            }
        }
    }

    fun loadFeatures(openOnly: Boolean = false) {
        scope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            when (val res = api.getFeatures(limit = 200, openOnly = openOnly)) {
                is ApiResult.Success -> _state.update { it.copy(features = res.value.features, isLoading = false) }
                is ApiResult.Failure -> _state.update { it.copy(isLoading = false, error = res.message) }
            }
        }
    }

    suspend fun createFeature(text: String): FeatureSuggestionDto? {
        _state.update { it.copy(isMutating = true, error = null) }
        return when (val res = api.createFeature(text)) {
            is ApiResult.Success -> {
                val created = res.value
                _state.update { prev ->
                    prev.copy(features = listOf(created) + prev.features, isMutating = false)
                }
                created
            }
            is ApiResult.Failure -> {
                _state.update { it.copy(isMutating = false, error = res.message) }
                null
            }
        }
    }

    suspend fun voteFeature(id: String): FeatureSuggestionDto? {
        return when (val res = api.voteFeature(id)) {
            is ApiResult.Success -> {
                val updated = res.value
                _state.update { prev ->
                    prev.copy(features = prev.features.map { if (it.id == updated.id) updated else it })
                }
                updated
            }
            is ApiResult.Failure -> {
                _state.update { it.copy(error = res.message) }
                null
            }
        }
    }

    suspend fun resolveFeature(id: String): FeatureSuggestionDto? {
        return when (val res = api.resolveFeature(id)) {
            is ApiResult.Success -> {
                val updated = res.value
                _state.update { prev ->
                    prev.copy(features = prev.features.map { if (it.id == updated.id) updated else it })
                }
                updated
            }
            is ApiResult.Failure -> {
                _state.update { it.copy(error = res.message) }
                null
            }
        }
    }

    suspend fun deleteFeature(id: String): Boolean {
        return when (val res = api.deleteFeature(id)) {
            is ApiResult.Success -> {
                _state.update { prev -> prev.copy(features = prev.features.filterNot { it.id == id }) }
                true
            }
            is ApiResult.Failure -> {
                _state.update { it.copy(error = res.message) }
                false
            }
        }
    }

    fun loadUserProfile(userId: String) {
        scope.launch {
            _state.update {
                it.copy(
                    isLoading = true,
                    viewedUser = null,
                    viewedUserPosts = emptyList(),
                    viewedUserPostsNextCursor = null,
                    viewedUserCommunities = emptyList(),
                    viewedUserActivities = emptyList(),
                    viewedUserProducts = emptyList(),
                    error = null,
                )
            }
            val userDef = scope.async { api.getUserProfile(userId) }
            val postsDef = scope.async { api.getUserPosts(userId) }
            val communitiesDef = scope.async { api.getUserCommunities(userId) }
            val activitiesDef = scope.async { api.getUserActivities(userId) }
            val productsDef = scope.async { api.getUserProducts(userId) }

            val userRes = userDef.await()
            val postsRes = postsDef.await()
            val communitiesRes = communitiesDef.await()
            val activitiesRes = activitiesDef.await()
            val productsRes = productsDef.await()

            when (userRes) {
                is ApiResult.Success -> {
                    val page = (postsRes as? ApiResult.Success)?.value
                    _state.update {
                        it.copy(
                            viewedUser = userRes.value,
                            viewedUserPosts = page?.posts.orEmpty(),
                            viewedUserPostsNextCursor = page?.nextCursor,
                            viewedUserPostsCount = page?.count ?: 0L,
                            viewedUserCommunities = (communitiesRes as? ApiResult.Success)?.value?.communities.orEmpty(),
                            viewedUserActivities = (activitiesRes as? ApiResult.Success)?.value?.activities.orEmpty(),
                            viewedUserProducts = (productsRes as? ApiResult.Success)?.value?.products.orEmpty(),
                            isLoading = false,
                        )
                    }
                }
                is ApiResult.Failure -> {
                    _state.update { it.copy(isLoading = false, error = userRes.message) }
                }
            }
        }
    }

    fun loadMoreUserPosts(userId: String) {
        val current = _state.value
        val cursor = current.viewedUserPostsNextCursor ?: return
        if (current.isLoading) return
        scope.launch {
            when (val res = api.getUserPosts(userId, cursor = cursor)) {
                is ApiResult.Success -> _state.update { prev ->
                    val seen = prev.viewedUserPosts.mapTo(HashSet()) { it.id }
                    prev.copy(
                        viewedUserPosts = prev.viewedUserPosts + res.value.posts.filter { seen.add(it.id) },
                        viewedUserPostsNextCursor = res.value.nextCursor,
                    )
                }
                is ApiResult.Failure -> _state.update { it.copy(error = res.message) }
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
