package com.bgr3108.kilonom.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bgr3108.kilonom.data.RecordLoadState
import com.bgr3108.kilonom.viewmodel.FuelEntryViewModel
import com.bgr3108.kilonom.viewmodel.HomeViewModel

@Composable
fun EditConsumptionScreen(
    id: Int?,
    viewModel: FuelEntryViewModel,
    homeViewModel: HomeViewModel,
    innerPadding: PaddingValues,
    operationOrigin: String,
    onClose: () -> Unit
) {
    val record = remember(viewModel, id) { viewModel.entryState(id) }
    val state by record.collectAsStateWithLifecycle(initialValue = RecordLoadState.Loading)
    RecordLoadContent(state, "fuel:$id", innerPadding, viewModel::retryEntryLoad, onClose) { entry ->
        AddConsumptionScreen(viewModel, homeViewModel, innerPadding, operationOrigin, entry, onClose)
    }
}
