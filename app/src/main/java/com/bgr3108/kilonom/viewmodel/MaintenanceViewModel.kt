package com.bgr3108.kilonom.viewmodel

import android.database.sqlite.SQLiteConstraintException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bgr3108.kilonom.data.MaintenanceItemEntity
import com.bgr3108.kilonom.data.MaintenanceRecordEntity
import com.bgr3108.kilonom.data.MaintenanceRecordWithItem
import com.bgr3108.kilonom.data.MaintenanceRepository
import com.bgr3108.kilonom.data.MaintenanceTimeUnit
import com.bgr3108.kilonom.data.MaintenanceType
import com.bgr3108.kilonom.data.TyrePosition
import com.bgr3108.kilonom.data.VehicleEntity
import com.bgr3108.kilonom.data.VehicleRepository
import com.bgr3108.kilonom.data.DEFAULT_REMINDER_LEAD_DAYS
import com.bgr3108.kilonom.data.DEFAULT_REMINDER_LEAD_KM
import com.bgr3108.kilonom.domain.MaintenanceDueInfo
import com.bgr3108.kilonom.domain.MaintenanceHomeInsight
import com.bgr3108.kilonom.domain.MaintenanceHomeInsightItem
import com.bgr3108.kilonom.domain.MaintenanceNextDueUpdate
import com.bgr3108.kilonom.domain.availableMaintenanceTypes
import com.bgr3108.kilonom.domain.createMaintenanceDueInfo
import com.bgr3108.kilonom.domain.displayMaintenanceName
import com.bgr3108.kilonom.domain.maintenanceUrgencySortValue
import com.bgr3108.kilonom.domain.selectMaintenanceHomeInsight
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlin.math.roundToLong

private data class MaintenanceSourceSnapshot(
    val vehicle: VehicleEntity?,
    val summaries: List<VehicleRepository.VehicleSummary>,
    val items: List<MaintenanceItemEntity>,
    val records: List<MaintenanceRecordWithItem>
)

data class MaintenanceItemUiModel(
    val item: MaintenanceItemEntity,
    val name: String,
    val due: MaintenanceDueInfo
)

data class MaintenanceRecordUiModel(
    val record: MaintenanceRecordEntity,
    val item: MaintenanceItemEntity,
    val itemName: String
)

data class MaintenanceUiState(
    val vehicle: VehicleEntity? = null,
    val currentKm: Long = 0,
    val items: List<MaintenanceItemUiModel> = emptyList(),
    val records: List<MaintenanceRecordUiModel> = emptyList(),
    val availableTypes: Set<MaintenanceType> = emptySet(),
    val isWorking: Boolean = false,
    val errorMessage: String? = null
) {
    val hasItems: Boolean get() = items.isNotEmpty()
    val upcomingItems: List<MaintenanceItemUiModel>
        get() = items.filter { it.due.status != com.bgr3108.kilonom.domain.MaintenanceDueStatus.NO_DUE_CONFIGURED }
}

data class MaintenanceItemDraft(
    val type: MaintenanceType,
    val tyrePosition: TyrePosition? = null,
    val customName: String? = null,
    val nextDueKm: Long? = null,
    val nextDueDate: Long? = null,
    val intervalKm: Long? = null,
    val intervalTimeValue: Int? = null,
    val intervalTimeUnit: MaintenanceTimeUnit? = null,
    val reminderLeadKm: Long = DEFAULT_REMINDER_LEAD_KM,
    val reminderLeadDays: Long = DEFAULT_REMINDER_LEAD_DAYS
)

data class MaintenanceRecordDraft(
    val performedDate: Long?,
    val odometerKm: Long?,
    val cost: Double?,
    val notes: String?
)

/** UI boundary for the active vehicle's maintenance only. */
class MaintenanceViewModel(
    private val maintenanceRepository: MaintenanceRepository,
    vehicleRepository: VehicleRepository
) : ViewModel() {

    private val selectedItemId = MutableStateFlow<Long?>(null)
    private val actionMutex = Mutex()
    private val isWorking = MutableStateFlow(false)
    private val errorMessage = MutableStateFlow<String?>(null)
    private val currentDay = MutableStateFlow(todayAtStartOfDay())

    private val activeItems = maintenanceRepository.observeActiveItems()
    private val activeRecords = maintenanceRepository.observeActiveRecords()

    private val sourceSnapshot = combine(
        vehicleRepository.activeVehicle,
        vehicleRepository.vehicleSummaries,
        activeItems,
        activeRecords
    ) { vehicle, summaries, items, records ->
        MaintenanceSourceSnapshot(vehicle, summaries, items, records)
    }

    val state: StateFlow<MaintenanceUiState> = combine(
        sourceSnapshot,
        currentDay,
        isWorking,
        errorMessage
    ) { source, today, working, error ->
        val vehicle = source.vehicle
        val summaries = source.summaries
        val items = source.items
        val records = source.records
        val summary = vehicle?.let { active -> summaries.firstOrNull { it.vehicle.id == active.id } }
        val currentKm = summary?.currentKm?.roundToLong()?.coerceAtLeast(0L) ?: 0L
        val activeVehicleId = vehicle?.id
        val scopedItems = items.filter { it.vehicleId == activeVehicleId }
        val itemModels = scopedItems.map { item ->
            MaintenanceItemUiModel(
                item = item,
                name = item.displayMaintenanceName(),
                due = createMaintenanceDueInfo(item, currentKm, today, ::daysBetween)
            )
        }.sortedWith(compareBy<MaintenanceItemUiModel> { maintenanceUrgencySortValue(it.due).first }
            .thenBy { maintenanceUrgencySortValue(it.due).second }
            .thenBy { it.name })
        val recordModels = records
            .filter { it.item.vehicleId == activeVehicleId }
            .map { it.toUiModel() }
        MaintenanceUiState(
            vehicle = vehicle,
            currentKm = currentKm,
            items = itemModels,
            records = recordModels,
            availableTypes = vehicle?.let { availableMaintenanceTypes(it.category, it.type) }.orEmpty(),
            isWorking = working,
            errorMessage = error
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MaintenanceUiState())

    val homeInsight: StateFlow<MaintenanceHomeInsight> = state
        .map { currentState ->
            selectMaintenanceHomeInsight(
                currentState.items.map { MaintenanceHomeInsightItem(it.name, it.due) }
            )
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            MaintenanceHomeInsight(com.bgr3108.kilonom.domain.MaintenanceHomeInsightType.NO_ITEMS)
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val detailRecords: StateFlow<List<MaintenanceRecordEntity>> = selectedItemId
        .flatMapLatest { itemId -> itemId?.let(maintenanceRepository::observeRecordsForActiveItem) ?: flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectDetailItem(itemId: Long?) {
        selectedItemId.value = itemId
    }

    /** Refreshes date-based reminders when a maintenance surface becomes visible again. */
    fun refreshForCurrentDay() {
        currentDay.value = todayAtStartOfDay()
    }

    fun createItem(
        draft: MaintenanceItemDraft,
        record: MaintenanceRecordDraft?,
        nextDueUpdate: MaintenanceNextDueUpdate = MaintenanceNextDueUpdate.Manual(
            draft.nextDueKm,
            draft.nextDueDate
        ),
        onComplete: () -> Unit
    ) = runAction(onComplete) {
        val now = System.currentTimeMillis()
        maintenanceRepository.createItemForActiveVehicle(
            item = MaintenanceItemEntity(
                vehicleId = 0,
                type = draft.type,
                tyrePosition = draft.tyrePosition,
                customName = draft.customName,
                trackingKey = "",
                nextDueKm = draft.nextDueKm,
                nextDueDate = draft.nextDueDate,
                createdAt = now,
                updatedAt = now,
                intervalKm = draft.intervalKm,
                intervalTimeValue = draft.intervalTimeValue,
                intervalTimeUnit = draft.intervalTimeUnit,
                reminderLeadKm = draft.reminderLeadKm,
                reminderLeadDays = draft.reminderLeadDays
            ),
            record = record?.toEntity(itemId = 0, now = now),
            nextDueUpdate = nextDueUpdate
        )
    }

    fun registerRecord(
        item: MaintenanceItemEntity,
        record: MaintenanceRecordDraft,
        nextDueUpdate: MaintenanceNextDueUpdate,
        onComplete: () -> Unit
    ) = runAction(onComplete) {
        val now = System.currentTimeMillis()
        maintenanceRepository.registerRecordForActiveVehicle(
            item = item.copy(
                updatedAt = now
            ),
            record = record.toEntity(item.id, now),
            nextDueUpdate = nextDueUpdate
        )
    }

    fun updateItem(
        existing: MaintenanceItemEntity,
        draft: MaintenanceItemDraft,
        onComplete: () -> Unit
    ) = runAction(onComplete) {
        maintenanceRepository.updateItemForActiveVehicle(
            existing.copy(
                customName = draft.customName,
                nextDueKm = draft.nextDueKm,
                nextDueDate = draft.nextDueDate,
                intervalKm = draft.intervalKm,
                intervalTimeValue = draft.intervalTimeValue,
                intervalTimeUnit = draft.intervalTimeUnit,
                reminderLeadKm = draft.reminderLeadKm,
                reminderLeadDays = draft.reminderLeadDays,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    fun updateRecord(record: MaintenanceRecordEntity, draft: MaintenanceRecordDraft, onComplete: () -> Unit) =
        runAction(onComplete) {
            maintenanceRepository.updateRecordForActiveVehicle(
                record.copy(
                    performedDate = draft.performedDate,
                    odometerKm = draft.odometerKm,
                    cost = draft.cost,
                    notes = draft.notes?.trim()?.takeIf(String::isNotEmpty),
                    updatedAt = System.currentTimeMillis()
                )
            )
        }

    fun deleteItem(itemId: Long, onComplete: () -> Unit) = runAction(onComplete) {
        maintenanceRepository.deleteItemForActiveVehicle(itemId)
    }

    fun deleteRecord(recordId: Long, onComplete: () -> Unit) = runAction(onComplete) {
        maintenanceRepository.deleteRecordForActiveVehicle(recordId)
    }

    private fun runAction(onComplete: () -> Unit, action: suspend () -> Unit) {
        if (!actionMutex.tryLock()) return
        isWorking.value = true
        errorMessage.value = null
        viewModelScope.launch {
            try {
                action()
                onComplete()
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                errorMessage.value = when (error) {
                    is SQLiteConstraintException -> "Ya estás siguiendo este mantenimiento para este vehículo."
                    else -> error.message ?: "No se pudo guardar el mantenimiento. Inténtalo de nuevo."
                }
            } finally {
                isWorking.value = false
                actionMutex.unlock()
            }
        }
    }
}

private fun MaintenanceRecordWithItem.toUiModel() = MaintenanceRecordUiModel(
    record = record,
    item = item,
    itemName = item.displayMaintenanceName()
)

private fun MaintenanceRecordDraft.toEntity(itemId: Long, now: Long) = MaintenanceRecordEntity(
    itemId = itemId,
    performedDate = performedDate,
    odometerKm = odometerKm,
    cost = cost,
    notes = notes?.trim()?.takeIf(String::isNotEmpty),
    createdAt = now,
    updatedAt = now
)

private fun todayAtStartOfDay(): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun daysBetween(from: Long, to: Long): Long =
    TimeUnit.MILLISECONDS.toDays(to - from)
