package quest.saigon.shared.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import quest.saigon.shared.model.WantDto
import quest.saigon.shared.network.ApiResult
import quest.saigon.shared.network.TownApiClient

data class WantsUiState(
    val wants: List<WantDto> = emptyList(),
    val isLoading: Boolean = false,
    val mutatingWantIds: Set<String> = emptySet(),
    val loadGeneration: Long = 0L,
    val error: String? = null,
)

class WantsStore(
    private val api: TownApiClient,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(WantsUiState())
    val state: StateFlow<WantsUiState> = _state.asStateFlow()

    fun loadAll() {
        scope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            when (val res = api.getAllWants()) {
                is ApiResult.Success -> _state.update { prev ->
                    prev.copy(
                        wants = res.value.wants,
                        isLoading = false,
                        loadGeneration = prev.loadGeneration + 1L,
                        error = null,
                    )
                }
                is ApiResult.Failure -> _state.update { it.copy(isLoading = false, error = res.message) }
            }
        }
    }

    suspend fun toggleJoin(wantId: String, currentlyJoined: Boolean): WantDto? {
        _state.update { prev ->
            prev.copy(mutatingWantIds = prev.mutatingWantIds + wantId, error = null)
        }
        val result = if (currentlyJoined) api.leaveWant(wantId) else api.joinWant(wantId)
        return when (result) {
            is ApiResult.Success -> {
                val updated = result.value
                _state.update { prev ->
                    prev.copy(
                        wants = prev.wants.map { if (it.id == wantId) updated else it },
                        mutatingWantIds = prev.mutatingWantIds - wantId,
                    )
                }
                updated
            }
            is ApiResult.Failure -> {
                _state.update { prev ->
                    prev.copy(
                        mutatingWantIds = prev.mutatingWantIds - wantId,
                        error = result.message,
                    )
                }
                null
            }
        }
    }

    fun updateWant(want: WantDto) {
        _state.update { prev ->
            prev.copy(wants = prev.wants.map { if (it.id == want.id) want else it })
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
