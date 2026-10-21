package com.bgr3108.kilonom.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.database.sqlite.SQLiteException
import java.io.IOException
import com.bgr3108.kilonom.data.FuelEntry
import com.bgr3108.kilonom.data.FuelRepository
import com.bgr3108.kilonom.data.FuelType
import com.bgr3108.kilonom.data.VehicleRepository
import com.bgr3108.kilonom.data.observeActiveVehicleEntries
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex

data class FuelEntryDraft(
    val id: Int,
    val fecha: Long,
    val cantidad: Double,
    val precio: Double,
    val tipo: FuelType,
    val km: Double,
    val fullTank: Boolean,
    val fuelLevelAfter: Double?,
    val electricChargeStartPercentage: Double?,
    val electricChargeEndPercentage: Double?,
    val originalVehicleId: Long?
)

internal fun FuelEntryDraft.toFuelEntry(activeVehicleId: Long): FuelEntry {
    val vehicleId = if (id == 0) {
        activeVehicleId
    } else {
        require(originalVehicleId == activeVehicleId) {
            "La entrada ya no pertenece al vehículo activo"
        }
        activeVehicleId
    }

    return FuelEntry(
        id = id,
        fecha = fecha,
        cantidad = cantidad,
        precio = precio,
        tipo = tipo,
        km = km,
        fullTank = fullTank,
        fuelLevelAfter = fuelLevelAfter,
        electricChargeStartPercentage = electricChargeStartPercentage,
        electricChargeEndPercentage = electricChargeEndPercentage,
        vehicleId = vehicleId
    )
}

class FuelEntryViewModel(
    private val repository: FuelRepository,
    private val vehicleRepository: VehicleRepository
) : ViewModel() {

    private val activeContext = repository
        .observeActiveVehicleEntries(vehicleRepository)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = com.bgr3108.kilonom.data.ActiveVehicleEntries(
                vehicle = com.bgr3108.kilonom.data.Vehicle(),
                entries = emptyList()
            )
        )

    val entries: StateFlow<List<FuelEntry>> =
        activeContext
            .map { it.entries }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList()
            )

    private val _filterState = MutableStateFlow(ConsumptionFilterState())
    val filterState: StateFlow<ConsumptionFilterState> = _filterState.asStateFlow()

    private val _dateFilterRefresh = MutableStateFlow(0)

    private val saveMutex = Mutex()
    private val operations = ScreenOperationResults()
    val operationResults = operations.results
    fun consumeOperation(origin: String, id: Long) = operations.consume(origin, id)
    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()
    private val _isDeleting = MutableStateFlow(false)
    val isDeleting: StateFlow<Boolean> = _isDeleting.asStateFlow()
    private val _deleteError = MutableStateFlow<String?>(null)
    val deleteError: StateFlow<String?> = _deleteError.asStateFlow()

    val filteredEntries: StateFlow<List<FuelEntry>> =
        combine(entries, filterState, _dateFilterRefresh) { entries, filters, _ ->
            filterConsumptionEntries(entries, filters)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    fun setEnergyFilter(filter: EnergyFilter) {
        _filterState.value = _filterState.value.copy(energyFilter = filter)
    }

    fun setDateFilter(filter: DateFilter) {
        _filterState.value = _filterState.value.copy(dateFilter = filter)
    }

    fun clearFilters() {
        _filterState.value = ConsumptionFilterState()
    }

    fun refreshDateFilters() {
        _dateFilterRefresh.value += 1
    }

    // 🔥 GUARDAR (CORREGIDO)
    fun saveEntry(
        draft: FuelEntryDraft,
        origin: String
    ) {
        if (!saveMutex.tryLock()) return
        val operation = operations.begin(origin, ScreenOperationKind.SAVE) ?: run {
            saveMutex.unlock()
            return
        }

        _isSaving.value = true

        viewModelScope.launch {
            try {
                val activeVehicleId = vehicleRepository.activeVehicleId.value
                    ?: error("No hay un vehículo activo")
                val entry = draft.toFuelEntry(activeVehicleId)
                if (draft.id == 0) {
                    repository.addEntryForVehicle(entry, activeVehicleId)
                } else {
                    repository.updateEntryForVehicle(entry, activeVehicleId)
                }
                operations.finish(operation, ScreenOperationStatus.SUCCEEDED)
            } catch (error: Throwable) {
                if (error is CancellationException) {
                    operations.finish(operation, ScreenOperationStatus.CANCELLED)
                    throw error
                }
                operations.finish(operation, ScreenOperationStatus.FAILED,
                    "No se pudo guardar el registro. Inténtalo de nuevo.")
            } finally {
                _isSaving.value = false
                saveMutex.unlock()
            }
        }
    }

    // 🔥 BORRAR
    fun clearDeleteError() { _deleteError.value = null }

    fun deleteEntry(entry: FuelEntry, origin: String) {
        if (!saveMutex.tryLock()) return
        val operation = operations.begin(origin, ScreenOperationKind.DELETE_ENTRY, entry.id.toLong()) ?: run {
            saveMutex.unlock()
            return
        }
        _isDeleting.value = true
        _deleteError.value = null
        viewModelScope.launch {
            try {
                val activeVehicleId = vehicleRepository.activeVehicleId.value
                if (activeVehicleId == null || activeVehicleId != entry.vehicleId) {
                    _deleteError.value = "El consumo ya no pertenece al vehículo seleccionado."
                    operations.finish(operation, ScreenOperationStatus.FAILED, _deleteError.value)
                    return@launch
                }
                repository.deleteEntryForVehicle(entry, activeVehicleId)
                operations.finish(operation, ScreenOperationStatus.SUCCEEDED)
            } catch (error: CancellationException) {
                operations.finish(operation, ScreenOperationStatus.CANCELLED)
                throw error
            } catch (_: IOException) {
                showDeleteError()
                operations.finish(operation, ScreenOperationStatus.FAILED, _deleteError.value)
            } catch (_: SQLiteException) {
                showDeleteError()
                operations.finish(operation, ScreenOperationStatus.FAILED, _deleteError.value)
            } finally {
                _isDeleting.value = false
                saveMutex.unlock()
            }
        }
    }

    private fun showDeleteError() {
        _deleteError.value = "No se pudo eliminar el consumo. Inténtalo de nuevo."
    }
}
