package com.bgr3108.kilonom

import com.bgr3108.kilonom.data.Vehicle
import com.bgr3108.kilonom.data.VehicleType
import com.bgr3108.kilonom.viewmodel.ResetState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppContentStateTest {

    @Test
    fun resetErrorWithNoActiveVehicle_keepsTheApplicationShellVisible() {
        assertFalse(shouldShowSetup(Vehicle(), ResetState.ERROR))
    }

    @Test
    fun resetLoadingWithNoActiveVehicle_keepsTheApplicationShellVisible() {
        assertFalse(shouldShowSetup(Vehicle(), ResetState.LOADING))
    }

    @Test
    fun completedResetWithNoActiveVehicle_showsSetup() {
        assertTrue(shouldShowSetup(Vehicle(), ResetState.IDLE))
    }

    @Test
    fun configuredVehicle_keepsTheApplicationShellVisible() {
        assertFalse(shouldShowSetup(Vehicle(type = VehicleType.GASOLINA), ResetState.IDLE))
    }
}
