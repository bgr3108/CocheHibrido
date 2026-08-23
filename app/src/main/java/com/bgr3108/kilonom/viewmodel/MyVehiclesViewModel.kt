package com.bgr3108.kilonom.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bgr3108.kilonom.data.Vehicle
import com.bgr3108.kilonom.data.VehicleCategory
import com.bgr3108.kilonom.data.VehicleInfo
import com.bgr3108.kilonom.data.VehicleRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

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

    private val _selectedCategory = MutableStateFlow(VehicleCategory.COCHE)
    val selectedCategory: StateFlow<VehicleCategory> = _selectedCategory
    val availableVehicles = MutableStateFlow(loadCatalog(VehicleCategory.COCHE))

    fun selectCategory(category: VehicleCategory) {
        if (_selectedCategory.value == category) return
        _selectedCategory.value = category
        availableVehicles.value = loadCatalog(category)
    }

    fun selectVehicle(vehicleId: Long) = runAction(
        action = { vehicleRepository.selectActiveVehicle(vehicleId) },
        onStarted = { _switchingVehicleId.value = vehicleId },
        onFinished = { _switchingVehicleId.value = null }
    )

    fun createVehicle(vehicle: Vehicle, onCreated: () -> Unit) = runAction(
        action = {
            vehicleRepository.createVehicle(vehicle)
            onCreated()
        }
    )

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

    private fun loadCatalog(category: VehicleCategory): List<VehicleInfo> =
        vehicleRepository.vehicleDataSource.loadVehicles(category)
}
