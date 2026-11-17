package quest.saigon.shared.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import quest.saigon.shared.model.StatsResponse
import quest.saigon.shared.network.ApiResult
import quest.saigon.shared.network.TownApiClient

data class StatsUiState(
    val stats: StatsResponse? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
)

class StatsStore(
    private val api: TownApiClient,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(StatsUiState())
    val state: StateFlow<StatsUiState> = _state.asStateFlow()

    fun load() {
        scope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            when (val res = api.getStats()) {
                is ApiResult.Success -> _state.update { it.copy(stats = res.value, isLoading = false) }
                is ApiResult.Failure -> _state.update { it.copy(isLoading = false, error = res.message) }
            }
        }
    }
}
