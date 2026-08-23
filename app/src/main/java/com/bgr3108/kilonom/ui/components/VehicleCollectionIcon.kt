package com.bgr3108.kilonom.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bgr3108.kilonom.data.VehicleCategory

internal enum class VehicleCollectionIconType { CAR, MOTORCYCLE, MIXED }

internal fun vehicleCollectionIconType(categories: Set<VehicleCategory>): VehicleCollectionIconType =
    when {
        categories.contains(VehicleCategory.COCHE) && categories.contains(VehicleCategory.MOTO) -> VehicleCollectionIconType.MIXED
        categories.contains(VehicleCategory.MOTO) -> VehicleCollectionIconType.MOTORCYCLE
        else -> VehicleCollectionIconType.CAR
    }

@Composable
internal fun VehicleCollectionIcon(categories: Set<VehicleCategory>) {
    when (vehicleCollectionIconType(categories)) {
        VehicleCollectionIconType.CAR -> Icon(Icons.Default.DirectionsCar, contentDescription = null)
        VehicleCollectionIconType.MOTORCYCLE -> Icon(Icons.Default.TwoWheeler, contentDescription = null)
        VehicleCollectionIconType.MIXED -> Row {
            Icon(Icons.Default.DirectionsCar, contentDescription = null, modifier = Modifier.size(20.dp))
            Icon(Icons.Default.TwoWheeler, contentDescription = null, modifier = Modifier.size(16.dp))
        }
    }
}
