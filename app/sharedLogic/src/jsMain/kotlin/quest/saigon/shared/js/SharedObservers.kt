package quest.saigon.shared.js

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import quest.saigon.shared.TownSharedClient

fun <T> observeState(
    scope: CoroutineScope,
    flow: StateFlow<T>,
    onValue: (T) -> Unit,
): () -> Unit {
    val job: Job = scope.launch {
        flow.collect { value ->
            onValue(value)
        }
    }
    return { job.cancel() }
}

fun launchAction(
    scope: CoroutineScope,
    block: suspend () -> Unit,
): () -> Unit {
    val job = scope.launch { block() }
    return { job.cancel() }
}

object JsSharedHolder {
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val client: TownSharedClient by lazy { TownSharedClient(scope = scope) }
}
