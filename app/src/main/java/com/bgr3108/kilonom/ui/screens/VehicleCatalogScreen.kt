package com.bgr3108.kilonom.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bgr3108.kilonom.R
import com.bgr3108.kilonom.viewmodel.VehicleCatalogUpdateErrorKind
import com.bgr3108.kilonom.viewmodel.VehicleCatalogUpdateStatus
import com.bgr3108.kilonom.viewmodel.VehicleCatalogUpdateViewModel

@Composable
internal fun VehicleCatalogScreen(
    innerPadding: PaddingValues,
    viewModel: VehicleCatalogUpdateViewModel
) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
    val checking = uiState.status is VehicleCatalogUpdateStatus.Checking

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.catalog_update_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = uiState.activeCatalogVersion?.let {
                        stringResource(R.string.catalog_update_version, it)
                    } ?: stringResource(R.string.catalog_update_version_loading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = viewModel::checkForUpdate,
                    enabled = !checking,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (checking) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = 8.dp),
                            strokeWidth = 2.dp
                        )
                    }
                    Text(
                        text = if (checking) {
                            stringResource(R.string.catalog_update_checking)
                        } else {
                            stringResource(R.string.catalog_update_check)
                        }
                    )
                }
                CatalogUpdateFeedback(uiState.status)
            }
        }
    }
}

@Composable
private fun CatalogUpdateFeedback(status: VehicleCatalogUpdateStatus) {
    val text = when (status) {
        VehicleCatalogUpdateStatus.Idle -> null
        VehicleCatalogUpdateStatus.Checking -> stringResource(R.string.catalog_update_checking)
        VehicleCatalogUpdateStatus.UpToDate -> stringResource(R.string.catalog_update_up_to_date)
        VehicleCatalogUpdateStatus.Updated -> stringResource(R.string.catalog_update_updated)
        is VehicleCatalogUpdateStatus.Error -> when (status.kind) {
            VehicleCatalogUpdateErrorKind.CONNECTION -> stringResource(R.string.catalog_update_connection_error)
            VehicleCatalogUpdateErrorKind.CATALOG -> stringResource(R.string.catalog_update_catalog_error)
        }
    }
    if (text != null) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (status is VehicleCatalogUpdateStatus.Error) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}
