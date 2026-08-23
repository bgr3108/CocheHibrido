package com.bgr3108.kilonom.ui.components

import com.bgr3108.kilonom.data.VehicleCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class VehicleCollectionIconTest {

    @Test
    fun onlyCars_useTheCarIcon() {
        assertEquals(
            VehicleCollectionIconType.CAR,
            vehicleCollectionIconType(setOf(VehicleCategory.COCHE))
        )
    }

    @Test
    fun onlyMotorcycles_useTheMotorcycleIcon() {
        assertEquals(
            VehicleCollectionIconType.MOTORCYCLE,
            vehicleCollectionIconType(setOf(VehicleCategory.MOTO))
        )
    }

    @Test
    fun mixedVehicleCategories_useTheCombinedIcon() {
        assertEquals(
            VehicleCollectionIconType.MIXED,
            vehicleCollectionIconType(setOf(VehicleCategory.COCHE, VehicleCategory.MOTO))
        )
    }
}
