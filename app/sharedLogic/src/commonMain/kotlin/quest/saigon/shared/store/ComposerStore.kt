package quest.saigon.shared.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import quest.saigon.shared.model.*
import quest.saigon.shared.network.ApiResult
import quest.saigon.shared.network.TownApiClient

data class ComposerUiState(
    val avatars: List<AvatarDto> = emptyList(),
    val selectedAvatarId: String? = null,
    val myMusic: List<MusicDto> = emptyList(),
    val searchedMusic: List<MusicDto> = emptyList(),
    val selectedMusic: MusicDto? = null,
    val communities: List<CommunityDto> = emptyList(),
    val communitySuggestion: CommunitySuggestResponse? = null,
    val recentPlaces: List<PlaceSuggestionDto> = emptyList(),
    val placeResults: List<PlaceSuggestionDto> = emptyList(),
    val myProducts: List<ProductDto> = emptyList(),
    val isLoadingProducts: Boolean = false,
    val myGroupChats: List<ChatDto> = emptyList(),
    val isLoadingGroupChats: Boolean = false,
    val isLoadingAvatars: Boolean = false,
    val isLoadingMusic: Boolean = false,
    val isSubmittingPost: Boolean = false,
    val createdPost: PostCardDto? = null,
    val error: String? = null,
)

class ComposerStore(
    private val api: TownApiClient,
    private val feedStore: FeedStore,
    private val scope: CoroutineScope,
    private val pollIntervalMs: Long = 1_500L,
) {
    private val _state = MutableStateFlow(ComposerUiState())
    val state: StateFlow<ComposerUiState> = _state.asStateFlow()

    private var avatarPollJob: Job? = null
    private var musicPollJob: Job? = null

    fun loadInitial() {
        _state.update { it.copy(selectedAvatarId = null, selectedMusic = null, communitySuggestion = null, error = null) }
        loadAvatars()
        loadMyMusic()
        loadCommunities()
        searchPlaces("")
    }

    fun loadAvatars() {
        scope.launch {
            _state.update { it.copy(isLoadingAvatars = true) }
            when (val res = api.getAvatars()) {
                is ApiResult.Success -> {
                    val list = res.value.avatars
                    _state.update { prev ->
                        val selected = prev.selectedAvatarId?.takeIf { id -> list.any { it.id == id } }
                        prev.copy(avatars = list, selectedAvatarId = selected, isLoadingAvatars = false)
                    }
                    list.firstOrNull { it.status == "generating" }?.let { startAvatarPolling(it.id) }
                }
                is ApiResult.Failure -> _state.update { it.copy(isLoadingAvatars = false, error = res.message) }
            }
        }
    }

    suspend fun createAvatar(prompt: String): AvatarDto? {
        return when (val res = api.createAvatar(prompt)) {
            is ApiResult.Success -> {
                val created = res.value
                _state.update { prev ->
                    prev.copy(
                        avatars = listOf(created) + prev.avatars.filterNot { it.id == created.id },
                        error = null,
                    )
                }
                if (created.status == "generating") startAvatarPolling(created.id)
                created
            }
            is ApiResult.Failure -> {
                _state.update { it.copy(error = res.message) }
                null
            }
        }
    }

    suspend fun retryAvatar(id: String): AvatarDto? {
        return when (val res = api.retryAvatar(id)) {
            is ApiResult.Success -> {
                val updated = res.value
                upsertAvatar(updated)
                if (updated.status == "generating") startAvatarPolling(updated.id)
                updated
            }
            is ApiResult.Failure -> {
                _state.update { it.copy(error = res.message) }
                null
            }
        }
    }

    suspend fun deleteAvatar(id: String): Boolean {
        return when (val res = api.deleteAvatar(id)) {
            is ApiResult.Success -> {
                _state.update { prev ->
                    prev.copy(
                        avatars = prev.avatars.filterNot { it.id == id },
                        selectedAvatarId = if (prev.selectedAvatarId == id) null else prev.selectedAvatarId,
                    )
                }
                true
            }
            is ApiResult.Failure -> {
                _state.update { it.copy(error = res.message) }
                false
            }
        }
    }

    fun selectAvatar(id: String?) {
        _state.update { it.copy(selectedAvatarId = id) }
    }

    fun loadMyMusic() {
        scope.launch {
            _state.update { it.copy(isLoadingMusic = true) }
            when (val res = api.getMyMusic()) {
                is ApiResult.Success -> {
                    val list = res.value.music
                    _state.update { it.copy(myMusic = list, isLoadingMusic = false) }
                    list.firstOrNull { it.status == "generating" }?.let { startMusicPolling(it.id) }
                }
                is ApiResult.Failure -> _state.update { it.copy(isLoadingMusic = false, error = res.message) }
            }
        }
    }

    fun searchMusic(query: String) {
        scope.launch {
            when (val res = api.searchMusic(query = query)) {
                is ApiResult.Success -> _state.update { it.copy(searchedMusic = res.value.music) }
                is ApiResult.Failure -> _state.update { it.copy(error = res.message) }
            }
        }
    }

    suspend fun createMusic(prompt: String, gender: String? = null, instrumental: Boolean = false): MusicDto? {
        return when (val res = api.createMusic(prompt, gender, instrumental)) {
            is ApiResult.Success -> {
                val created = res.value
                upsertMusic(created)
                if (created.status == "generating") startMusicPolling(created.id)
                created
            }
            is ApiResult.Failure -> {
                _state.update { it.copy(error = res.message) }
                null
            }
        }
    }

    suspend fun retryMusic(id: String): MusicDto? {
        return when (val res = api.retryMusic(id)) {
            is ApiResult.Success -> {
                val updated = res.value
                upsertMusic(updated)
                if (updated.status == "generating") startMusicPolling(updated.id)
                updated
            }
            is ApiResult.Failure -> {
                _state.update { it.copy(error = res.message) }
                null
            }
        }
    }

    suspend fun renameMusic(id: String, name: String): MusicDto? {
        return when (val res = api.renameMusic(id, name)) {
            is ApiResult.Success -> {
                upsertMusic(res.value)
                res.value
            }
            is ApiResult.Failure -> {
                _state.update { it.copy(error = res.message) }
                null
            }
        }
    }

    suspend fun deleteMusic(id: String): Boolean {
        return when (val res = api.deleteMusic(id)) {
            is ApiResult.Success -> {
                _state.update { prev ->
                    prev.copy(
                        myMusic = prev.myMusic.filterNot { it.id == id },
                        searchedMusic = prev.searchedMusic.filterNot { it.id == id },
                        selectedMusic = if (prev.selectedMusic?.id == id) null else prev.selectedMusic,
                    )
                }
                true
            }
            is ApiResult.Failure -> {
                _state.update { it.copy(error = res.message) }
                false
            }
        }
    }

    fun selectMusic(music: MusicDto?) {
        _state.update { it.copy(selectedMusic = music) }
    }

    fun loadCommunities(query: String = "") {
        scope.launch {
            when (val res = api.getCommunities(query = query)) {
                is ApiResult.Success -> _state.update { it.copy(communities = res.value.communities) }
                is ApiResult.Failure -> Unit
            }
        }
    }

    fun clearCommunitySuggestion() {
        _state.update { it.copy(communitySuggestion = null) }
    }

    suspend fun suggestCommunity(text: String): CommunitySuggestResponse? {
        return when (val res = api.suggestCommunity(text)) {
            is ApiResult.Success -> {
                _state.update { it.copy(communitySuggestion = res.value) }
                res.value
            }
            is ApiResult.Failure -> null
        }
    }

    suspend fun createCommunity(name: String, emoji: String): CommunityDto? {
        return when (val res = api.createCommunity(name, emoji)) {
            is ApiResult.Success -> {
                val created = res.value
                _state.update { prev ->
                    val updatedSuggestion = prev.communitySuggestion?.let { suggestion ->
                        if (suggestion.communityId == null && suggestion.name.equals(created.name, ignoreCase = true)) {
                            suggestion.copy(communityId = created.id)
                        } else {
                            suggestion
                        }
                    }
                    prev.copy(
                        communities = listOf(created) + prev.communities.filterNot { it.id == created.id },
                        communitySuggestion = updatedSuggestion,
                    )
                }
                created
            }
            is ApiResult.Failure -> {
                _state.update { it.copy(error = res.message) }
                null
            }
        }
    }

    fun loadMyProducts() {
        scope.launch {
            _state.update { it.copy(isLoadingProducts = true) }
            val all = mutableListOf<ProductDto>()
            var cursor: String? = null
            do {
                when (val res = api.getMyProducts(cursor = cursor, limit = 50)) {
                    is ApiResult.Success -> {
                        all += res.value.products
                        cursor = res.value.nextCursor
                    }
                    is ApiResult.Failure -> {
                        cursor = null
                    }
                }
            } while (!cursor.isNullOrBlank())
            _state.update { it.copy(myProducts = all.distinctBy { p -> p.id }, isLoadingProducts = false) }
        }
    }

    fun loadGroupChats() {
        scope.launch {
            _state.update { it.copy(isLoadingGroupChats = true) }
            val all = mutableListOf<ChatDto>()
            var cursor: String? = null
            do {
                when (val res = api.getChats(cursor = cursor, limit = 50, type = "group")) {
                    is ApiResult.Success -> {
                        all += res.value.chats
                        cursor = res.value.nextCursor
                    }
                    is ApiResult.Failure -> {
                        cursor = null
                    }
                }
            } while (!cursor.isNullOrBlank())
            _state.update { it.copy(myGroupChats = all.distinctBy { c -> c.id }, isLoadingGroupChats = false) }
        }
    }

    suspend fun resolvePlaceDetails(placeId: String): PlaceSuggestionDto? {
        return when (val res = api.getPlaceDetails(placeId)) {
            is ApiResult.Success -> res.value
            is ApiResult.Failure -> null
        }
    }

    fun searchPlaces(query: String, communityId: String? = null) {
        scope.launch {
            when (val res = api.searchPlaces(query, communityId)) {
                is ApiResult.Success -> _state.update {
                    it.copy(recentPlaces = res.value.recent, placeResults = res.value.places)
                }
                is ApiResult.Failure -> Unit
            }
        }
    }

    suspend fun createCustomPlace(name: String, lat: Double, lng: Double): PlaceSuggestionDto? {
        return when (val res = api.createPlace(name, lat, lng)) {
            is ApiResult.Success -> {
                val created = res.value
                _state.update { prev -> prev.copy(placeResults = listOf(created) + prev.placeResults) }
                created
            }
            is ApiResult.Failure -> {
                _state.update { it.copy(error = res.message) }
                null
            }
        }
    }

    suspend fun submitPost(fields: Map<String, String>, photos: List<ByteArray>): PostCardDto? {
        _state.update { it.copy(isSubmittingPost = true, error = null, createdPost = null) }
        return when (val res = api.createPost(fields, photos)) {
            is ApiResult.Success -> {
                val created = res.value
                feedStore.prependPost(created)
                _state.update { it.copy(isSubmittingPost = false, createdPost = created, error = null) }
                created
            }
            is ApiResult.Failure -> {
                _state.update { it.copy(isSubmittingPost = false, error = res.message) }
                null
            }
        }
    }

    fun clearCreatedPost() {
        _state.update { it.copy(createdPost = null) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun stopPolling() {
        avatarPollJob?.cancel()
        avatarPollJob = null
        musicPollJob?.cancel()
        musicPollJob = null
    }

    private fun startAvatarPolling(id: String) {
        avatarPollJob?.cancel()
        avatarPollJob = scope.launch {
            while (isActive) {
                delay(pollIntervalMs)
                val res = api.getAvatar(id)
                if (res is ApiResult.Success) {
                    upsertAvatar(res.value)
                    if (res.value.status != "generating") break
                } else {
                    break
                }
            }
        }
    }

    private fun startMusicPolling(id: String) {
        musicPollJob?.cancel()
        musicPollJob = scope.launch {
            while (isActive) {
                delay(pollIntervalMs)
                val res = api.getMusic(id)
                if (res is ApiResult.Success) {
                    upsertMusic(res.value)
                    if (res.value.status != "generating") break
                } else {
                    break
                }
            }
        }
    }

    private fun upsertAvatar(avatar: AvatarDto) {
        _state.update { prev ->
            val previous = prev.avatars.firstOrNull { it.id == avatar.id }
            val wasGenerating = previous?.status == "generating"
            val exists = previous != null
            val list = if (exists) prev.avatars.map { if (it.id == avatar.id) avatar else it } else listOf(avatar) + prev.avatars
            val selected = if (wasGenerating && avatar.status == "ready" && prev.selectedAvatarId == null) avatar.id else prev.selectedAvatarId
            prev.copy(avatars = list, selectedAvatarId = selected)
        }
    }

    private fun upsertMusic(music: MusicDto) {
        _state.update { prev ->
            val exists = prev.myMusic.any { it.id == music.id }
            val list = if (exists) prev.myMusic.map { if (it.id == music.id) music else it } else listOf(music) + prev.myMusic
            val selected = if (prev.selectedMusic?.id == music.id) music else prev.selectedMusic
            prev.copy(myMusic = list, selectedMusic = selected)
        }
    }
}
