package com.bgr3108.kilonom.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.bgr3108.kilonom.viewmodel.MaintenanceViewModel

/** Refreshes date-based reminder states whenever a maintenance surface becomes visible again. */
@Composable
internal fun RefreshMaintenanceForCurrentDayOnResume(viewModel: MaintenanceViewModel) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        viewModel.refreshForCurrentDay()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshForCurrentDay()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}
