package com.bgr3108.kilonom.data

import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class RecordLoadStateTest {
    @Test fun noRoomAnswerRemainsLoading() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val states = Channel<RecordLoadState<List<Int>>>(Channel.UNLIMITED)
        val job = launch {
            observeVehicleData(flowOf(ready(1)), flowOf(0)) { flow { gate.await(); emit(listOf(7)) } }
                .collect { states.send(it) }
        }
        assertEquals(RecordLoadState.Loading, withTimeout(2.seconds) { states.receive() })
        assertTrue(states.tryReceive().isFailure)
        job.cancel()
    }

    @Test fun actualFirstAnswerFindsRecord() = runBlocking {
        val result = mutableListOf<RecordLoadState<Int>>()
        observeVehicleData(flowOf(ready(1)), flowOf(0)) { flowOf(listOf(7)) }
            .collect { result += it.selectRecord { rows -> rows.find { it == 7 } } }
        assertEquals(listOf(RecordLoadState.Loading, RecordLoadState.Found(7, 1)), result)
    }

    @Test fun actualEmptyAnswerConfirmsAbsence() = runBlocking {
        val result = mutableListOf<RecordLoadState<Int>>()
        observeVehicleData(flowOf(ready(1)), flowOf(0)) { flowOf(emptyList<Int>()) }
            .collect { result += it.selectRecord { rows -> rows.firstOrNull() } }
        assertEquals(listOf(RecordLoadState.Loading, RecordLoadState.NotFound), result)
    }

    @Test fun readFailureIsErrorNotMissing() = runBlocking {
        val failure = IOException("disk")
        val result = mutableListOf<RecordLoadState<Int>>()
        observeVehicleData<Int>(flowOf(ready(1)), flowOf(0)) { flow { throw failure } }
            .collect { result += it }
        assertEquals(RecordLoadState.Error(failure, 1), result.last())
        assertFalse(result.contains(RecordLoadState.NotFound))
    }

    @Test fun retryRestartsFailedObservation() = runBlocking {
        val retry = MutableStateFlow(0)
        val states = Channel<RecordLoadState<Int>>(Channel.UNLIMITED)
        var calls = 0
        val job = launch {
            observeVehicleData(flowOf(ready(1)), retry) { flow {
                calls++
                if (calls == 1) throw IOException("disk")
                emit(7)
            } }.collect { states.send(it) }
        }
        withTimeout(2.seconds) {
            while (states.receive() !is RecordLoadState.Error) { }
            retry.value++
            while (states.receive() != RecordLoadState.Found(7, 1)) { }
        }
        assertEquals(2, calls)
        job.cancel()
    }

    @Test fun changingVehicleCancelsOldReadBeforeNewAnswer() = runBlocking {
        val vehicles = MutableStateFlow<VehicleLoadState>(ready(1))
        val oldStarted = CompletableDeferred<Unit>()
        val oldCancelled = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        val states = Channel<RecordLoadState<Int>>(Channel.UNLIMITED)
        val job = launch {
            observeVehicleData(vehicles, flowOf(0)) { vehicle -> flow {
                if (vehicle.id == 1L) {
                    oldStarted.complete(Unit)
                    try { gate.await(); emit(1) } finally { oldCancelled.complete(Unit) }
                } else emit(2)
            } }.collect { states.send(it) }
        }
        withTimeout(2.seconds) {
            oldStarted.await()
            vehicles.value = ready(2)
            oldCancelled.await()
            while (states.receive() != RecordLoadState.Found(2, 2)) { }
        }
        gate.complete(Unit)
        assertTrue(states.tryReceive().isFailure)
        job.cancel()
    }

    @Test fun cancellationIsNotConvertedToAnErrorOrAbsence() = runBlocking {
        val result = mutableListOf<RecordLoadState<Int>>()
        val started = CompletableDeferred<Unit>()
        val job = launch {
            observeVehicleData<Int>(flowOf(ready(1)), flowOf(0)) { flow {
                started.complete(Unit)
                awaitCancellation()
            } }
                .collect { result += it }
        }
        started.await()
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
        assertEquals(listOf(RecordLoadState.Loading), result)
    }

    @Test fun realDeletionTransitionsFromFoundToNotFound() = runBlocking {
        val rows = MutableStateFlow(listOf(7))
        val states = Channel<RecordLoadState<Int>>(Channel.UNLIMITED)
        val job = launch {
            observeVehicleData(flowOf(ready(1)), flowOf(0)) { rows }
                .collect { states.send(it.selectRecord { values -> values.firstOrNull() }) }
        }
        withTimeout(2.seconds) {
            while (states.receive() !is RecordLoadState.Found) { }
            rows.value = emptyList()
            assertEquals(RecordLoadState.NotFound, states.receive())
        }
        job.cancel()
    }

    @Test fun startupErrorIsNeverAConfirmedMissingRecord() = runBlocking {
        val failure = IOException("preferences")
        val result = mutableListOf<RecordLoadState<Int>>()
        observeVehicleData(flowOf(VehicleLoadState.Error(failure)), flowOf(0)) { flowOf(7) }
            .collect { result += it }
        assertEquals(RecordLoadState.Error(failure), result.last())
    }

    @Test fun emptyVehicleScopeDoesNotOpenAnyRoomRead() = runBlocking {
        var calls = 0
        val result = mutableListOf<RecordLoadState<Int>>()
        observeVehicleData(flowOf(VehicleLoadState.Empty), flowOf(0)) { calls++; flowOf(7) }
            .collect { result += it }
        assertEquals(0, calls)
        assertEquals(RecordLoadState.NotFound, result.last())
    }

    private fun ready(id: Long) = VehicleLoadState.Ready(Vehicle().toEntity(id = id, createdAt = 1))
}
