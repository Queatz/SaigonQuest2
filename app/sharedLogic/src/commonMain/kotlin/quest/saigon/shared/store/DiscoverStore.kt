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

data class LocationDetailState(
    val name: String = "",
    val placeId: String? = null,
    val locationId: String? = null,
    val place: PlaceSuggestionDto? = null,
    val description: String = "",
    val photos: List<UserPhotoDto> = emptyList(),
    val activities: List<ActivityDto> = emptyList(),
    val posts: List<PostCardDto> = emptyList(),
    val postsNextCursor: String? = null,
    val postsCount: Long = 0L,
    val isLoading: Boolean = false,
    val isMutating: Boolean = false,
    val error: String? = null,
)

data class DiscoverUiState(
    val summary: EventsSummaryDto = EventsSummaryDto(),
    val events: List<PostCardDto> = emptyList(),
    val locations: List<PlaceSuggestionDto> = emptyList(),
    val checkIns: List<CheckInDto> = emptyList(),
    val people: List<CommunityMemberDto> = emptyList(),
    val nextPeopleCursor: String? = null,
    val communities: List<CommunityDto> = emptyList(),
    val communityQuery: String = "",
    val searchRecentPlaces: List<PlaceSuggestionDto> = emptyList(),
    val searchPlaces: List<PlaceSuggestionDto> = emptyList(),
    val locationDetail: LocationDetailState? = null,
    val isLoading: Boolean = false,
    val isLoadingCommunities: Boolean = false,
    val isMutating: Boolean = false,
    val error: String? = null,
)

class DiscoverStore(
    private val api: TownApiClient,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(DiscoverUiState())
    val state: StateFlow<DiscoverUiState> = _state.asStateFlow()

    fun loadAll() {
        scope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val summaryDef = scope.async { api.getEventsSummary() }
            val discoveryDef = scope.async { api.getDiscovery() }
            val checkInsDef = scope.async { api.getCheckIns() }
            val communitiesDef = scope.async { api.getCommunities(_state.value.communityQuery) }

            val summary = (summaryDef.await() as? ApiResult.Success)?.value
            val discovery = discoveryDef.await()
            val checkIns = (checkInsDef.await() as? ApiResult.Success)?.value?.checkIns
            val communities = (communitiesDef.await() as? ApiResult.Success)?.value?.communities

            when (discovery) {
                is ApiResult.Success -> {
                    val d = discovery.value
                    _state.update { prev ->
                        prev.copy(
                            summary = summary ?: prev.summary,
                            events = d.events,
                            locations = d.locations,
                            people = d.people,
                            nextPeopleCursor = d.nextPeopleCursor,
                            checkIns = checkIns ?: prev.checkIns,
                            communities = communities ?: prev.communities,
                            isLoading = false,
                            error = null,
                        )
                    }
                }
                is ApiResult.Failure -> {
                    _state.update { prev ->
                        prev.copy(
                            summary = summary ?: prev.summary,
                            checkIns = checkIns ?: prev.checkIns,
                            communities = communities ?: prev.communities,
                            isLoading = false,
                            error = discovery.message,
                        )
                    }
                }
            }
        }
    }

    fun refreshCheckIns() {
        scope.launch {
            when (val res = api.getCheckIns()) {
                is ApiResult.Success -> _state.update { it.copy(checkIns = res.value.checkIns) }
                is ApiResult.Failure -> Unit
            }
        }
    }

    fun searchCommunities(query: String) {
        scope.launch {
            _state.update { it.copy(communityQuery = query, isLoadingCommunities = true, error = null) }
            when (val res = api.getCommunities(query = query)) {
                is ApiResult.Success -> _state.update {
                    it.copy(communities = res.value.communities, isLoadingCommunities = false)
                }
                is ApiResult.Failure -> _state.update {
                    it.copy(isLoadingCommunities = false, error = res.message)
                }
            }
        }
    }

    suspend fun toggleCommunityMembership(communityId: String, currentlyJoined: Boolean): CommunityDto? {
        _state.update { it.copy(isMutating = true, error = null) }
        val result = if (currentlyJoined) api.leaveCommunity(communityId) else api.joinCommunity(communityId)
        return when (result) {
            is ApiResult.Success -> {
                val updated = result.value
                _state.update { prev ->
                    prev.copy(
                        communities = prev.communities.map { if (it.id == updated.id) updated else it },
                        isMutating = false,
                    )
                }
                updated
            }
            is ApiResult.Failure -> {
                _state.update { it.copy(isMutating = false, error = result.message) }
                null
            }
        }
    }

    suspend fun createCommunity(name: String, emoji: String = ""): CommunityDto? {
        _state.update { it.copy(isMutating = true, error = null) }
        return when (val res = api.createCommunity(name, emoji)) {
            is ApiResult.Success -> {
                val created = res.value
                _state.update { prev ->
                    prev.copy(
                        communities = listOf(created) + prev.communities.filterNot { it.id == created.id },
                        isMutating = false,
                    )
                }
                created
            }
            is ApiResult.Failure -> {
                _state.update { it.copy(isMutating = false, error = res.message) }
                null
            }
        }
    }

    fun searchPlaces(query: String, communityId: String? = null) {
        scope.launch {
            when (val res = api.searchPlaces(query, communityId)) {
                is ApiResult.Success -> {
                    _state.update {
                        it.copy(
                            searchRecentPlaces = res.value.recent,
                            searchPlaces = res.value.places,
                        )
                    }
                }
                is ApiResult.Failure -> _state.update { it.copy(error = res.message) }
            }
        }
    }

    suspend fun createCustomPlace(name: String, lat: Double, lng: Double): PlaceSuggestionDto? {
        _state.update { it.copy(isMutating = true, error = null) }
        return when (val res = api.createPlace(name, lat, lng)) {
            is ApiResult.Success -> {
                val created = res.value
                _state.update { prev ->
                    prev.copy(
                        locations = listOf(created) + prev.locations,
                        isMutating = false,
                    )
                }
                created
            }
            is ApiResult.Failure -> {
                _state.update { it.copy(isMutating = false, error = res.message) }
                null
            }
        }
    }

    fun openLocationByIdOrToken(idOrToken: String) {
        scope.launch {
            _state.update { it.copy(locationDetail = LocationDetailState(isLoading = true)) }
            when (val res = api.getPlace(idOrToken)) {
                is ApiResult.Success -> {
                    val place = res.value
                    openLocation(
                        name = place.name,
                        placeId = place.placeId,
                        locationId = if (place.custom) place.id else null,
                        initialPlace = place,
                    )
                }
                is ApiResult.Failure -> {
                    _state.update { it.copy(locationDetail = LocationDetailState(isLoading = false, error = res.message)) }
                }
            }
        }
    }

    fun openLocation(
        name: String,
        placeId: String? = null,
        locationId: String? = null,
        initialPlace: PlaceSuggestionDto? = null,
    ) {
        scope.launch {
            _state.update {
                it.copy(
                    locationDetail = LocationDetailState(
                        name = name,
                        placeId = placeId,
                        locationId = locationId,
                        place = initialPlace,
                        isLoading = true,
                    ),
                )
            }
            val descDef = scope.async { api.getPlaceDescription(name, placeId, locationId) }
            val photosDef = scope.async { api.getPlacePhotos(name, placeId, locationId) }
            val activitiesDef = scope.async { api.getActivities(name, placeId, locationId) }
            val postsDef = scope.async { api.getPlacePosts(name, placeId, locationId) }
            val placeDef = if (initialPlace == null && !placeId.isNullOrBlank()) {
                scope.async { api.getPlaceDetails(placeId) }
            } else {
                null
            }

            val desc = (descDef.await() as? ApiResult.Success)?.value?.description.orEmpty()
            val photos = (photosDef.await() as? ApiResult.Success)?.value?.photos.orEmpty()
            val activities = (activitiesDef.await() as? ApiResult.Success)?.value?.activities.orEmpty()
            val postsRes = postsDef.await()
            val resolvedPlace = initialPlace ?: (placeDef?.await() as? ApiResult.Success)?.value

            _state.update { prev ->
                val page = (postsRes as? ApiResult.Success)?.value
                prev.copy(
                    locationDetail = LocationDetailState(
                        name = name,
                        placeId = placeId,
                        locationId = locationId,
                        place = resolvedPlace,
                        description = desc,
                        photos = photos,
                        activities = activities,
                        posts = page?.posts.orEmpty(),
                        postsNextCursor = page?.nextCursor,
                        postsCount = page?.count ?: 0L,
                        isLoading = false,
                        error = (postsRes as? ApiResult.Failure)?.message,
                    ),
                )
            }
        }
    }

    fun loadMoreLocationPosts() {
        val detail = _state.value.locationDetail ?: return
        val cursor = detail.postsNextCursor ?: return
        if (detail.isLoading) return
        scope.launch {
            when (val res = api.getPlacePosts(detail.name, detail.placeId, detail.locationId, cursor = cursor)) {
                is ApiResult.Success -> {
                    val page = res.value
                    _state.update { prev ->
                        val cur = prev.locationDetail ?: return@update prev
                        val seen = cur.posts.mapTo(HashSet()) { it.id }
                        prev.copy(
                            locationDetail = cur.copy(
                                posts = cur.posts + page.posts.filter { seen.add(it.id) },
                                postsNextCursor = page.nextCursor,
                            ),
                        )
                    }
                }
                is ApiResult.Failure -> _state.update { it.copy(error = res.message) }
            }
        }
    }

    suspend fun updateLocationDescription(description: String): Boolean {
        val detail = _state.value.locationDetail ?: return false
        _state.update { it.copy(locationDetail = detail.copy(isMutating = true, error = null)) }
        return when (val res = api.updatePlaceDescription(detail.name, detail.placeId, detail.locationId, description)) {
            is ApiResult.Success -> {
                _state.update { prev ->
                    prev.copy(
                        locationDetail = prev.locationDetail?.copy(
                            description = res.value.description,
                            isMutating = false,
                        ),
                    )
                }
                true
            }
            is ApiResult.Failure -> {
                _state.update { prev ->
                    prev.copy(locationDetail = prev.locationDetail?.copy(isMutating = false, error = res.message))
                }
                false
            }
        }
    }

    suspend fun uploadLocationPhotos(photos: List<ByteArray>): Boolean {
        val detail = _state.value.locationDetail ?: return false
        _state.update { it.copy(locationDetail = detail.copy(isMutating = true, error = null)) }
        return when (val res = api.uploadPlacePhotos(detail.name, detail.placeId, detail.locationId, photos)) {
            is ApiResult.Success -> {
                _state.update { prev ->
                    val cur = prev.locationDetail ?: return@update prev
                    prev.copy(locationDetail = cur.copy(photos = res.value.photos + cur.photos, isMutating = false))
                }
                true
            }
            is ApiResult.Failure -> {
                _state.update { prev ->
                    prev.copy(locationDetail = prev.locationDetail?.copy(isMutating = false, error = res.message))
                }
                false
            }
        }
    }

    suspend fun deleteLocationPhoto(photoId: String): Boolean {
        val detail = _state.value.locationDetail ?: return false
        return when (val res = api.deletePlacePhoto(photoId)) {
            is ApiResult.Success -> {
                _state.update { prev ->
                    val cur = prev.locationDetail ?: return@update prev
                    prev.copy(locationDetail = cur.copy(photos = cur.photos.filterNot { it.id == photoId }))
                }
                true
            }
            is ApiResult.Failure -> {
                _state.update { prev ->
                    prev.copy(locationDetail = detail.copy(error = res.message))
                }
                false
            }
        }
    }

    fun closeLocation() {
        _state.update { it.copy(locationDetail = null) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
