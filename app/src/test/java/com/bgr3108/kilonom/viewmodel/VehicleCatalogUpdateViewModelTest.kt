package com.bgr3108.kilonom.viewmodel

import com.bgr3108.kilonom.data.VehicleCatalogInstallResult
import com.bgr3108.kilonom.data.VehicleCatalogUpdateResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleCatalogUpdateViewModelTest {

    @Test
    fun initialState_isIdleAndDoesNotRepresentAnAutomaticCheck() {
        val state = VehicleCatalogUpdateUiState()

        assertEquals(VehicleCatalogUpdateStatus.Idle, state.status)
        assertTrue(canStartCatalogUpdate(state))
    }

    @Test
    fun explicitCheck_movesToCheckingAndPreventsAnotherUiRequest() {
        val checking = catalogUpdateCheckingUiState(activeCatalogVersion = 1)

        assertEquals(VehicleCatalogUpdateStatus.Checking, checking.status)
        assertFalse(canStartCatalogUpdate(checking))
    }

    @Test
    fun successfulResults_produceUserFacingStatesAndKeepTheActualVersion() {
        assertEquals(
            VehicleCatalogUpdateUiState(2, VehicleCatalogUpdateStatus.Updated),
            catalogUpdateUiStateForResult(2, VehicleCatalogUpdateResult.Updated(2))
        )
        assertEquals(
            VehicleCatalogUpdateUiState(1, VehicleCatalogUpdateStatus.UpToDate),
            catalogUpdateUiStateForResult(1, VehicleCatalogUpdateResult.UpToDate(1))
        )
    }

    @Test
    fun networkAndCatalogFailures_areGroupedWithoutTechnicalDetails() {
        assertEquals(
            VehicleCatalogUpdateStatus.Error(VehicleCatalogUpdateErrorKind.CONNECTION),
            catalogUpdateUiStateForResult(1, VehicleCatalogUpdateResult.ManifestDownloadFailed).status
        )
        assertEquals(
            VehicleCatalogUpdateStatus.Error(VehicleCatalogUpdateErrorKind.CONNECTION),
            catalogUpdateUiStateForResult(1, VehicleCatalogUpdateResult.CatalogDownloadFailed).status
        )
        assertEquals(
            VehicleCatalogUpdateStatus.Error(VehicleCatalogUpdateErrorKind.CATALOG),
            catalogUpdateUiStateForResult(1, VehicleCatalogUpdateResult.InvalidManifest).status
        )
        assertEquals(
            VehicleCatalogUpdateStatus.Error(VehicleCatalogUpdateErrorKind.CATALOG),
            catalogUpdateUiStateForResult(
                1,
                VehicleCatalogUpdateResult.InstallFailed(VehicleCatalogInstallResult.ChecksumMismatch)
            ).status
        )
    }
}
