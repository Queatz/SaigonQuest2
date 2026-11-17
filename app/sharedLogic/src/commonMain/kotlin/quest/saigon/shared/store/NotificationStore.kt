package quest.saigon.shared.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import quest.saigon.shared.model.NotificationDto
import quest.saigon.shared.network.ApiResult
import quest.saigon.shared.network.TownApiClient

data class NotificationUiState(
    val items: List<NotificationDto> = emptyList(),
    val nextCursor: String? = null,
    val unreadCount: Long = 0L,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val connected: Boolean = false,
    val latestNotification: NotificationDto? = null,
    val error: String? = null,
)

class NotificationStore(
    private val api: TownApiClient,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(NotificationUiState())
    val state: StateFlow<NotificationUiState> = _state.asStateFlow()

    private var streamJob: Job? = null

    fun connect() {
        if (streamJob?.isActive == true) return
        scope.launch { refreshUnread() }
        streamJob = api.sse.launchStream(
            scope = scope,
            path = "/api/notifications/stream",
            onConnected = { reconnected ->
                _state.update { it.copy(connected = true) }
                if (reconnected) refreshUnread()
            },
            onEvent = { event ->
                val item = try {
                    api.json.decodeFromString(NotificationDto.serializer(), event.data)
                } catch (_: Throwable) {
                    null
                }
                if (item != null && item.id.isNotBlank()) {
                    _state.update { prev ->
                        val isNew = prev.items.none { it.id == item.id }
                        val updatedItems = listOf(item) + prev.items.filterNot { it.id == item.id }
                        prev.copy(
                            items = updatedItems,
                            unreadCount = if (isNew && !item.read) prev.unreadCount + 1 else prev.unreadCount,
                            latestNotification = item,
                        )
                    }
                }
            },
        )
    }

    fun disconnect() {
        streamJob?.cancel()
        streamJob = null
        _state.update {
            NotificationUiState()
        }
    }

    suspend fun refreshUnread() {
        when (val result = api.getNotifications(cursor = null, limit = 1)) {
            is ApiResult.Success -> {
                _state.update { it.copy(unreadCount = result.value.unreadCount) }
            }
            is ApiResult.Failure -> Unit
        }
    }

    fun loadInitial() {
        scope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            when (val result = api.getNotifications(cursor = null, limit = 50)) {
                is ApiResult.Success -> {
                    val page = result.value
                    _state.update {
                        it.copy(
                            items = page.notifications,
                            nextCursor = page.nextCursor,
                            unreadCount = page.unreadCount,
                            isLoading = false,
                            error = null,
                        )
                    }
                }
                is ApiResult.Failure -> {
                    _state.update { it.copy(isLoading = false, error = result.message) }
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
            when (val result = api.getNotifications(cursor = cursor, limit = 50)) {
                is ApiResult.Success -> {
                    val page = result.value
                    _state.update { prev ->
                        val seen = prev.items.mapTo(HashSet()) { it.id }
                        prev.copy(
                            items = prev.items + page.notifications.filter { seen.add(it.id) },
                            nextCursor = page.nextCursor,
                            unreadCount = page.unreadCount,
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

    suspend fun markRead(id: String): NotificationDto? {
        return when (val result = api.markNotificationRead(id)) {
            is ApiResult.Success -> {
                val updated = result.value
                _state.update { prev ->
                    val wasUnread = prev.items.firstOrNull { it.id == id }?.read == false
                    val nextItems = prev.items.map { if (it.id == id) updated else it }
                    val nextUnread = if (wasUnread || prev.items.none { it.id == id }) {
                        (prev.unreadCount - 1).coerceAtLeast(0L)
                    } else {
                        prev.unreadCount
                    }
                    prev.copy(items = nextItems, unreadCount = nextUnread)
                }
                updated
            }
            is ApiResult.Failure -> {
                _state.update { it.copy(error = result.message) }
                null
            }
        }
    }

    suspend fun markAllRead(): Boolean {
        return when (val result = api.markAllNotificationsRead()) {
            is ApiResult.Success -> {
                _state.update { prev ->
                    prev.copy(
                        items = prev.items.map { it.copy(read = true) },
                        unreadCount = result.value.unreadCount,
                    )
                }
                true
            }
            is ApiResult.Failure -> {
                _state.update { it.copy(error = result.message) }
                false
            }
        }
    }

    fun clearLatestNotification() {
        _state.update { it.copy(latestNotification = null) }
    }
}
