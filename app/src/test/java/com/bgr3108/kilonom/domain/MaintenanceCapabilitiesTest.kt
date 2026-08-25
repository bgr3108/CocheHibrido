package com.bgr3108.kilonom.domain

import com.bgr3108.kilonom.data.MaintenanceType
import com.bgr3108.kilonom.data.VehicleCategory
import com.bgr3108.kilonom.data.VehicleType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MaintenanceCapabilitiesTest {

    @Test
    fun car_doesNotOfferChainAndDrivetrain() {
        assertFalse(
            MaintenanceType.CHAIN_AND_DRIVETRAIN in
                availableMaintenanceTypes(VehicleCategory.COCHE, VehicleType.GASOLINA)
        )
    }

    @Test
    fun motorcycle_offersChainAndDrivetrain() {
        assertTrue(
            MaintenanceType.CHAIN_AND_DRIVETRAIN in
                availableMaintenanceTypes(VehicleCategory.MOTO, VehicleType.GASOLINA)
        )
    }

    @Test
    fun bev_doesNotOfferOilAndFilter() {
        assertFalse(
            MaintenanceType.OIL_AND_FILTER in
                availableMaintenanceTypes(VehicleCategory.COCHE, VehicleType.ELECTRICO)
        )
    }

    @Test
    fun thermalAndHybridPowertrains_offerOilAndFilter() {
        listOf(
            VehicleType.GASOLINA,
            VehicleType.DIESEL,
            VehicleType.HIBRIDO,
            VehicleType.HIBRIDO_ENCHUFABLE
        ).forEach { type ->
            assertTrue(
                MaintenanceType.OIL_AND_FILTER in availableMaintenanceTypes(VehicleCategory.COCHE, type)
            )
        }
    }
}
