package com.bgr3108.kilonom.data

import android.database.sqlite.SQLiteException
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow

/** Found may contain an empty collection: only a real query emission establishes absence. */
sealed interface RecordLoadState<out T> {
    data object Loading : RecordLoadState<Nothing>
    data class Found<T>(val value: T, val vehicleId: Long) : RecordLoadState<T>
    data object NotFound : RecordLoadState<Nothing>
    data class Error(val cause: Exception, val vehicleId: Long? = null) : RecordLoadState<Nothing>
}

fun <T, R : Any> RecordLoadState<T>.selectRecord(select: (T) -> R?): RecordLoadState<R> = when (this) {
    RecordLoadState.Loading -> RecordLoadState.Loading
    RecordLoadState.NotFound -> RecordLoadState.NotFound
    is RecordLoadState.Error -> this
    is RecordLoadState.Found -> select(value)?.let { RecordLoadState.Found(it, vehicleId) }
        ?: RecordLoadState.NotFound
}

/** Ready selects the query, but never means that its first answer is already available. */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun <T> observeVehicleData(
    vehicles: Flow<VehicleLoadState>,
    retries: Flow<Int>,
    read: (VehicleEntity) -> Flow<T>
): Flow<RecordLoadState<T>> = combine(vehicles, retries) { vehicle, retry -> vehicle to retry }
    .distinctUntilChanged()
    .flatMapLatest { (state, _) ->
        flow {
            emit(RecordLoadState.Loading)
            when (state) {
                VehicleLoadState.Loading -> Unit
                VehicleLoadState.Empty -> emit(RecordLoadState.NotFound)
                is VehicleLoadState.Error -> emit(RecordLoadState.Error(state.cause))
                is VehicleLoadState.Ready -> {
                    try {
                        read(state.vehicle).collect { emit(RecordLoadState.Found(it, state.vehicle.id)) }
                    } catch (error: IOException) {
                        currentCoroutineContext().ensureActive()
                        emit(RecordLoadState.Error(error, state.vehicle.id))
                    } catch (error: SQLiteException) {
                        currentCoroutineContext().ensureActive()
                        emit(RecordLoadState.Error(error, state.vehicle.id))
                    }
                }
            }
        }
    }

/** Reject replay from a previous vehicle while the new query is being started. */
internal fun <T> Flow<RecordLoadState<T>>.forCurrentVehicle(
    vehicles: Flow<VehicleLoadState>
): Flow<RecordLoadState<T>> = combine(this, vehicles) { data, vehicle ->
    when (vehicle) {
        VehicleLoadState.Loading -> RecordLoadState.Loading
        VehicleLoadState.Empty -> RecordLoadState.NotFound
        is VehicleLoadState.Error -> RecordLoadState.Error(vehicle.cause)
        is VehicleLoadState.Ready -> when {
            data is RecordLoadState.Found && data.vehicleId != vehicle.vehicle.id -> RecordLoadState.Loading
            data is RecordLoadState.Error && data.vehicleId != vehicle.vehicle.id -> RecordLoadState.Loading
            data == RecordLoadState.NotFound -> RecordLoadState.Loading // A former Empty vehicle scope.
            else -> data
        }
    }
}.distinctUntilChanged()
