package com.bgr3108.kilonom.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.bgr3108.kilonom.R
import com.bgr3108.kilonom.data.VehicleSelectionCatalog
import com.bgr3108.kilonom.viewmodel.VehicleSelectionState

@Composable
internal fun VehicleSelectionContent(
    state: VehicleSelectionState,
    onRetry: () -> Unit,
    content: @Composable (VehicleSelectionCatalog) -> Unit
) {
    when (state) {
        is VehicleSelectionState.Ready -> content(state.catalog)
        VehicleSelectionState.Loading, VehicleSelectionState.Failed -> Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (state == VehicleSelectionState.Loading) {
                CircularProgressIndicator()
                Text(stringResource(R.string.vehicle_selection_loading))
            } else {
                Text(stringResource(R.string.vehicle_selection_failed))
                TextButton(onClick = onRetry) {
                    Text(stringResource(R.string.vehicle_selection_retry))
                }
            }
        }
    }
}
