package com.bgr3108.kilonom.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.bgr3108.kilonom.viewmodel.ScreenOperationResult
import com.bgr3108.kilonom.viewmodel.ScreenOperationStatus
import kotlinx.coroutines.flow.StateFlow

/** Only the current, resumed entry may consume its own completion. Persistence outlives this effect. */
@Composable
fun ScreenOperationEffect(
    origin: String,
    results: StateFlow<Map<String, ScreenOperationResult>>,
    consume: (String, Long) -> Boolean,
    isCurrentEntry: () -> Boolean,
    onSuccess: (ScreenOperationResult) -> Unit
) {
    val operations by results.collectAsStateWithLifecycle()
    val result = operations[origin]
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentCheck by rememberUpdatedState(isCurrentEntry)
    val currentConsume by rememberUpdatedState(consume)
    val currentSuccess by rememberUpdatedState(onSuccess)
    LaunchedEffect(lifecycle, result) {
        if (result?.status == ScreenOperationStatus.SUCCEEDED && !result.consumed) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                if (currentCheck() && currentConsume(origin, result.id)) currentSuccess(result)
            }
        }
    }
}
