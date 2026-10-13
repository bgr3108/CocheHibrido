package com.bgr3108.kilonom

import com.bgr3108.kilonom.data.Vehicle
import com.bgr3108.kilonom.data.VehicleType
import com.bgr3108.kilonom.data.VehicleLoadState
import com.bgr3108.kilonom.data.toEntity
import com.bgr3108.kilonom.viewmodel.ResetState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class AppContentStateTest {

    @Test
    fun resetErrorWithNoActiveVehicle_keepsTheApplicationShellVisible() {
        assertFalse(shouldShowSetup(VehicleLoadState.Empty, ResetState.ERROR))
    }

    @Test
    fun resetLoadingWithNoActiveVehicle_keepsTheApplicationShellVisible() {
        assertFalse(shouldShowSetup(VehicleLoadState.Empty, ResetState.LOADING))
    }

    @Test
    fun completedResetWithNoActiveVehicle_showsSetup() {
        assertTrue(shouldShowSetup(VehicleLoadState.Empty, ResetState.IDLE))
    }

    @Test
    fun configuredVehicle_keepsTheApplicationShellVisible() {
        assertFalse(shouldShowSetup(VehicleLoadState.Ready(Vehicle(type = VehicleType.GASOLINA).toEntity(createdAt = 0)), ResetState.IDLE))
    }

    @Test
    fun storageFailure_neverShowsOnboarding() {
        assertFalse(shouldShowSetup(VehicleLoadState.Error(IOException("storage unavailable")), ResetState.IDLE))
    }

    @Test
    fun loadingDoesNotShowOnboardingBeforeRoomHasAnswered() {
        assertFalse(shouldShowSetup(VehicleLoadState.Loading, ResetState.IDLE))
    }

    @Test
    fun successfullyReadLegacyRecoveryAnchor_keepsItsSetupFlow() {
        assertTrue(shouldShowSetup(VehicleLoadState.Ready(Vehicle().toEntity(createdAt = 0)), ResetState.IDLE))
    }
}
