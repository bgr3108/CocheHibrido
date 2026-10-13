package com.bgr3108.kilonom.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.database.sqlite.SQLiteException
import com.bgr3108.kilonom.data.VehicleCatalogUpdateChecker
import com.bgr3108.kilonom.data.VehicleCatalogUpdateResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

internal data class VehicleCatalogUpdateUiState(
    val activeCatalogVersion: Int? = null,
    val status: VehicleCatalogUpdateStatus = VehicleCatalogUpdateStatus.Idle
)

internal sealed interface VehicleCatalogUpdateStatus {
    data object Idle : VehicleCatalogUpdateStatus
    data object Checking : VehicleCatalogUpdateStatus
    data object UpToDate : VehicleCatalogUpdateStatus
    data object Updated : VehicleCatalogUpdateStatus
    data class Error(val kind: VehicleCatalogUpdateErrorKind) : VehicleCatalogUpdateStatus
}

internal enum class VehicleCatalogUpdateErrorKind {
    CONNECTION,
    CATALOG
}

/** Lifecycle-aware state holder for an explicit, user-initiated catalog update only. */
internal class VehicleCatalogUpdateViewModel(
    private val updateChecker: VehicleCatalogUpdateChecker
) : ViewModel() {
    private val _uiState = MutableStateFlow(VehicleCatalogUpdateUiState())
    val uiState: StateFlow<VehicleCatalogUpdateUiState> = _uiState

    init {
        refreshActiveCatalogVersion()
    }

    fun checkForUpdate() {
        if (!canStartCatalogUpdate(_uiState.value)) return
        _uiState.value = catalogUpdateCheckingUiState(_uiState.value.activeCatalogVersion)
        viewModelScope.launch {
            try {
                val result = updateChecker.checkForUpdate()
                val version = when (result) {
                    is VehicleCatalogUpdateResult.Updated -> activeCatalogVersionOrNull() ?: result.catalogVersion
                    is VehicleCatalogUpdateResult.UpToDate -> result.activeCatalogVersion
                    else -> _uiState.value.activeCatalogVersion
                }
                _uiState.value = catalogUpdateUiStateForResult(version, result)
            } catch (_: IOException) {
                showPersistenceError()
            } catch (_: SQLiteException) {
                showPersistenceError()
            } finally {
                if (_uiState.value.status == VehicleCatalogUpdateStatus.Checking) {
                    _uiState.update { it.copy(status = VehicleCatalogUpdateStatus.Idle) }
                }
            }
        }
    }

    private fun showPersistenceError() {
        _uiState.update { it.copy(status = VehicleCatalogUpdateStatus.Error(VehicleCatalogUpdateErrorKind.CATALOG)) }
    }

    private fun refreshActiveCatalogVersion() {
        viewModelScope.launch {
            val version = activeCatalogVersionOrNull()
            _uiState.update {
                if (it.status != VehicleCatalogUpdateStatus.Idle) it
                else it.copy(
                    activeCatalogVersion = version,
                    status = if (version == null) VehicleCatalogUpdateStatus.Error(VehicleCatalogUpdateErrorKind.CATALOG)
                        else it.status
                )
            }
        }
    }

    private suspend fun activeCatalogVersionOrNull(): Int? = withContext(Dispatchers.IO) {
        try {
            updateChecker.activeCatalogVersion()
        } catch (_: IOException) {
            null
        } catch (_: SQLiteException) {
            null
        }
    }
}

internal fun catalogUpdateUiStateForResult(
    activeCatalogVersion: Int?,
    result: VehicleCatalogUpdateResult
): VehicleCatalogUpdateUiState = VehicleCatalogUpdateUiState(
    activeCatalogVersion = activeCatalogVersion,
    status = when (result) {
        is VehicleCatalogUpdateResult.Updated -> VehicleCatalogUpdateStatus.Updated
        is VehicleCatalogUpdateResult.UpToDate -> VehicleCatalogUpdateStatus.UpToDate
        VehicleCatalogUpdateResult.ManifestDownloadFailed,
        VehicleCatalogUpdateResult.CatalogDownloadFailed ->
            VehicleCatalogUpdateStatus.Error(VehicleCatalogUpdateErrorKind.CONNECTION)
        VehicleCatalogUpdateResult.InvalidManifest,
        is VehicleCatalogUpdateResult.InstallFailed ->
            VehicleCatalogUpdateStatus.Error(VehicleCatalogUpdateErrorKind.CATALOG)
    }
)

internal fun catalogUpdateCheckingUiState(activeCatalogVersion: Int?): VehicleCatalogUpdateUiState =
    VehicleCatalogUpdateUiState(
        activeCatalogVersion = activeCatalogVersion,
        status = VehicleCatalogUpdateStatus.Checking
    )

internal fun canStartCatalogUpdate(state: VehicleCatalogUpdateUiState): Boolean =
    state.status !is VehicleCatalogUpdateStatus.Checking
