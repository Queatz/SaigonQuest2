package quest.saigon.shared.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import quest.saigon.Language
import quest.saigon.shared.model.*
import quest.saigon.shared.network.ApiResult
import quest.saigon.shared.network.TownApiClient

data class ActivityUiState(
    val activities: List<ActivityDto> = emptyList(),
    val nextCursor: String? = null,
    val selectedActivity: ActivityDto? = null,
    val photos: List<UserPhotoDto> = emptyList(),
    val suggestedEmoji: String? = null,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isMutating: Boolean = false,
    val error: String? = null,
)

class ActivityStore(
    private val api: TownApiClient,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(ActivityUiState())
    val state: StateFlow<ActivityUiState> = _state.asStateFlow()

    fun loadActivities(
        name: String? = null,
        placeId: String? = null,
        locationId: String? = null,
    ) {
        scope.launch {
            _state.update { it.copy(isLoading = true, isLoadingMore = false, error = null) }
            when (val res = api.getActivities(name, placeId, locationId, cursor = null, limit = 30)) {
                is ApiResult.Success -> {
                    _state.update {
                        it.copy(
                            activities = res.value.activities,
                            nextCursor = res.value.nextCursor,
                            isLoading = false,
                        )
                    }
                }
                is ApiResult.Failure -> {
                    _state.update { it.copy(isLoading = false, error = res.message) }
                }
            }
        }
    }

    fun loadMore(
        name: String? = null,
        placeId: String? = null,
        locationId: String? = null,
    ) {
        val current = _state.value
        val cursor = current.nextCursor ?: return
        if (current.isLoading || current.isLoadingMore) return
        scope.launch {
            _state.update { it.copy(isLoadingMore = true, error = null) }
            when (val res = api.getActivities(name, placeId, locationId, cursor = cursor, limit = 30)) {
                is ApiResult.Success -> {
                    _state.update { prev ->
                        val seen = prev.activities.mapTo(HashSet()) { it.id }
                        prev.copy(
                            activities = prev.activities + res.value.activities.filter { seen.add(it.id) },
                            nextCursor = res.value.nextCursor,
                            isLoadingMore = false,
                        )
                    }
                }
                is ApiResult.Failure -> _state.update { it.copy(isLoadingMore = false, error = res.message) }
            }
        }
    }

    fun openActivity(id: String) {
        scope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val activityDef = scope.async { api.getActivity(id) }
            val photosDef = scope.async { api.getActivityPhotos(id) }
            val actRes = activityDef.await()
            val photosRes = photosDef.await()
            when (actRes) {
                is ApiResult.Success -> {
                    _state.update {
                        it.copy(
                            selectedActivity = actRes.value,
                            photos = (photosRes as? ApiResult.Success)?.value?.photos.orEmpty(),
                            isLoading = false,
                        )
                    }
                }
                is ApiResult.Failure -> {
                    _state.update { it.copy(isLoading = false, error = actRes.message) }
                }
            }
        }
    }

    suspend fun createActivity(request: CreateActivityRequest): ActivityDto? {
        _state.update { it.copy(isMutating = true, error = null) }
        return when (val res = api.createActivity(request)) {
            is ApiResult.Success -> {
                val created = res.value
                _state.update { prev ->
                    prev.copy(
                        activities = listOf(created) + prev.activities,
                        selectedActivity = created,
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

    suspend fun updateActivity(id: String, request: UpdateActivityRequest): ActivityDto? {
        _state.update { it.copy(isMutating = true, error = null) }
        return when (val res = api.updateActivity(id, request)) {
            is ApiResult.Success -> {
                val updated = res.value
                _state.update { prev ->
                    prev.copy(
                        activities = prev.activities.map { if (it.id == updated.id) updated else it },
                        selectedActivity = if (prev.selectedActivity?.id == updated.id) updated else prev.selectedActivity,
                        isMutating = false,
                    )
                }
                updated
            }
            is ApiResult.Failure -> {
                _state.update { it.copy(isMutating = false, error = res.message) }
                null
            }
        }
    }

    suspend fun deleteActivity(id: String): Boolean {
        _state.update { it.copy(isMutating = true, error = null) }
        return when (val res = api.deleteActivity(id)) {
            is ApiResult.Success -> {
                _state.update { prev ->
                    prev.copy(
                        activities = prev.activities.filterNot { it.id == id },
                        selectedActivity = if (prev.selectedActivity?.id == id) null else prev.selectedActivity,
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

    suspend fun toggleInterest(id: String, currentlyInterested: Boolean): ActivityDto? {
        _state.update { it.copy(isMutating = true, error = null) }
        val res = if (currentlyInterested) api.leaveActivity(id) else api.joinActivity(id)
        return when (res) {
            is ApiResult.Success -> {
                val updated = res.value
                _state.update { prev ->
                    prev.copy(
                        activities = prev.activities.map { if (it.id == updated.id) updated else it },
                        selectedActivity = if (prev.selectedActivity?.id == updated.id) updated else prev.selectedActivity,
                        isMutating = false,
                    )
                }
                updated
            }
            is ApiResult.Failure -> {
                _state.update { it.copy(isMutating = false, error = res.message) }
                null
            }
        }
    }

    suspend fun startActivityChat(id: String, userIds: List<String>): ChatDto? {
        _state.update { it.copy(isMutating = true, error = null) }
        return when (val res = api.startActivityChat(id, userIds)) {
            is ApiResult.Success -> {
                _state.update { it.copy(isMutating = false) }
                res.value
            }
            is ApiResult.Failure -> {
                _state.update { it.copy(isMutating = false, error = res.message) }
                null
            }
        }
    }

    suspend fun suggestEmoji(name: String): String? {
        return when (val res = api.suggestActivityEmoji(name)) {
            is ApiResult.Success -> {
                _state.update { it.copy(suggestedEmoji = res.value.emoji) }
                res.value.emoji
            }
            is ApiResult.Failure -> null
        }
    }

    suspend fun uploadPhotos(activityId: String, photos: List<ByteArray>): Boolean {
        _state.update { it.copy(isMutating = true, error = null) }
        return when (val res = api.uploadActivityPhotos(activityId, photos)) {
            is ApiResult.Success -> {
                _state.update { prev ->
                    prev.copy(photos = res.value.photos + prev.photos, isMutating = false)
                }
                true
            }
            is ApiResult.Failure -> {
                _state.update { it.copy(isMutating = false, error = res.message) }
                false
            }
        }
    }

    suspend fun deletePhoto(activityId: String, photoId: String): Boolean {
        return when (val res = api.deleteActivityPhoto(activityId, photoId)) {
            is ApiResult.Success -> {
                _state.update { prev -> prev.copy(photos = prev.photos.filterNot { it.id == photoId }) }
                true
            }
            is ApiResult.Failure -> {
                _state.update { it.copy(error = res.message) }
                false
            }
        }
    }

    fun closeActivity() {
        _state.update { it.copy(selectedActivity = null, photos = emptyList()) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    companion object {
        fun localizedName(activity: ActivityDto, language: Language): String {
            val map = if (language == Language.VI) activity.translations?.vi else activity.translations?.en
            return map?.get("name")?.takeIf { it.isNotBlank() } ?: activity.name
        }

        fun localizedDescription(activity: ActivityDto, language: Language): String {
            val map = if (language == Language.VI) activity.translations?.vi else activity.translations?.en
            return map?.get("description")?.takeIf { it.isNotBlank() } ?: activity.description
        }
    }
}
