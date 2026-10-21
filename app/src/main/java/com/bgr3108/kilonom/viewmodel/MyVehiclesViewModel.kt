package com.bgr3108.kilonom.viewmodel

import android.database.sqlite.SQLiteException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bgr3108.kilonom.data.Vehicle
import com.bgr3108.kilonom.data.VehicleCategory
import com.bgr3108.kilonom.data.VehicleEntity
import com.bgr3108.kilonom.data.LegacyCatalogReconciliation
import com.bgr3108.kilonom.data.VehicleRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import java.io.IOException

sealed interface VehicleCreationState {
    data object Editing : VehicleCreationState
    data class Saved(
        val vehicleId: Long,
        val isActive: Boolean = false,
        val acknowledged: Boolean = false
    ) : VehicleCreationState
}

/** UI boundary for the vehicle management flow; Compose never reaches the DAOs directly. */
class MyVehiclesViewModel(
    private val vehicleRepository: VehicleRepository
) : ViewModel() {

    val vehicleSummaries = vehicleRepository.vehicleSummaries
    val activeVehicleId = vehicleRepository.activeVehicleId

    private val actionMutex = Mutex()
    private val _isWorking = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)
    val isWorking: StateFlow<Boolean> = _isWorking
    val error: StateFlow<String?> = _error
    private val _switchingVehicleId = MutableStateFlow<Long?>(null)
    val switchingVehicleId: StateFlow<Long?> = _switchingVehicleId
    private val _creationState = MutableStateFlow<VehicleCreationState>(VehicleCreationState.Editing)
    val creationState: StateFlow<VehicleCreationState> = _creationState

    private val _selectedCategory = MutableStateFlow(VehicleCategory.COCHE)
    val selectedCategory: StateFlow<VehicleCategory> = _selectedCategory
    private val selectionLoader = VehicleSelectionLoader(
        viewModelScope, vehicleRepository.vehicleDataSource, _selectedCategory
    )
    val selectionCatalog = selectionLoader.state

    fun retrySelectionCatalog() = selectionLoader.retry()

    fun selectCategory(category: VehicleCategory) {
        if (_selectedCategory.value == category) return
        _selectedCategory.value = category
    }

    fun selectVehicle(vehicleId: Long) = runAction(
        action = { vehicleRepository.selectActiveVehicle(vehicleId) },
        onStarted = { _switchingVehicleId.value = vehicleId },
        onFinished = { _switchingVehicleId.value = null }
    )

    /** Only an explicit new-form action can begin another creation after a completed one. */
    fun beginVehicleCreation(): Boolean {
        if (_isWorking.value) return false
        val saved = _creationState.value as? VehicleCreationState.Saved
        if (saved == null || saved.isActive || saved.acknowledged || activeVehicleId.value == saved.vehicleId) {
            _creationState.value = VehicleCreationState.Editing
        }
        _error.value = null
        return true
    }

    fun createVehicle(vehicle: Vehicle) {
        if (_creationState.value != VehicleCreationState.Editing) return
        runCreationAction {
            val created = vehicleRepository.createVehicle(vehicle) { stored ->
                _creationState.value = VehicleCreationState.Saved(stored.id)
            }
            _creationState.value = VehicleCreationState.Saved(created.id, isActive = true)
        }
    }

    fun retryCreatedVehicleActivation() {
        val saved = _creationState.value as? VehicleCreationState.Saved ?: return
        if (saved.isActive || saved.acknowledged) return
        runCreationAction {
            if (vehicleRepository.activateCreatedVehicle(saved.vehicleId)) {
                _creationState.value = saved.copy(isActive = true)
            } else {
                _error.value = "El vehículo guardado ya no está disponible. Revisa Mis vehículos."
            }
        }
    }

    fun acknowledgeCreatedVehicle() {
        if (_isWorking.value) return
        val saved = _creationState.value as? VehicleCreationState.Saved ?: return
        _creationState.value = saved.copy(acknowledged = true)
    }

    private fun runCreationAction(action: suspend () -> Unit) {
        if (!actionMutex.tryLock()) return
        _isWorking.value = true
        _error.value = null
        viewModelScope.launch {
            try {
                action()
            } catch (_: IOException) {
                showCreationError()
            } catch (_: SQLiteException) {
                showCreationError()
            } finally {
                _isWorking.value = false
                actionMutex.unlock()
            }
        }
    }

    private fun showCreationError() {
        _error.value = if (_creationState.value is VehicleCreationState.Saved) {
            "El vehículo está guardado, pero no pudo activarse automáticamente. Puedes reintentar la activación."
        } else {
            "No se pudo guardar el vehículo. Inténtalo de nuevo."
        }
    }

    fun updateVehicle(vehicleId: Long, vehicle: Vehicle, onUpdated: () -> Unit) = runAction(
        action = {
            vehicleRepository.updateVehicle(vehicleId, vehicle)
            onUpdated()
        }
    )

    fun deleteVehicle(vehicleId: Long, onDeleted: () -> Unit) = runAction(
        action = {
            vehicleRepository.deleteVehicle(vehicleId)
            onDeleted()
        }
    )

    fun reconcileLegacyVehicle(vehicle: VehicleEntity): LegacyCatalogReconciliation =
        vehicleRepository.reconcileLegacyVehicle(vehicle)

    fun linkLegacyVehicleToCatalog(vehicleId: Long, catalogId: String) = runAction(
        action = { vehicleRepository.linkLegacyVehicleToCatalog(vehicleId, catalogId) }
    )

    private fun runAction(
        action: suspend () -> Unit,
        onStarted: () -> Unit = {},
        onFinished: () -> Unit = {}
    ) {
        if (!actionMutex.tryLock()) return
        _isWorking.value = true
        _error.value = null
        onStarted()
        viewModelScope.launch {
            try {
                action()
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                _error.value = error.message ?: "No se pudo completar la operación. Inténtalo de nuevo."
            } finally {
                onFinished()
                _isWorking.value = false
                actionMutex.unlock()
            }
        }
    }

}
