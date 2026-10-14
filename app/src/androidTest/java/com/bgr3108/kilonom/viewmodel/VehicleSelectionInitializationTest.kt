package com.bgr3108.kilonom.viewmodel

import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bgr3108.kilonom.data.*
import com.bgr3108.kilonom.database.HybridCarDatabase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds

/** In-memory personal DB, fake preferences and read-only bundled seed: never the user's data. */
@RunWith(AndroidJUnit4::class)
class VehicleSelectionInitializationTest {
    @Test
    fun bothViewModelConstructors_andUnobservedCategoryChanges_doNotLoadCatalog() = runBlocking {
        fixture { f ->
            val home = f.home()
            val vehicles = f.vehicles()
            withContext(Dispatchers.Main.immediate) {
                home.selectSetupVehicleCategory(VehicleCategory.MOTO)
                vehicles.selectCategory(VehicleCategory.MOTO)
            }
            assertEquals(VehicleSelectionState.Loading, home.selectionCatalog.value)
            assertEquals(VehicleSelectionState.Loading, vehicles.selectionCatalog.value)
            assertEquals(0, f.catalog.calls.get())
            assertEquals(0, f.source.seedReads.get())
        }
    }

    @Test
    fun firstSelectorLoadsOffMain_andOtherFormsAndRecreatedViewModelsReuseIt() = runBlocking {
        fixture { f ->
            val home = f.home()
            val car = ready(home.selectionCatalog)
            val vehicles = f.vehicles()
            assertSame(car, ready(vehicles.selectionCatalog))
            withContext(Dispatchers.Main.immediate) { vehicles.selectCategory(VehicleCategory.MOTO) }
            assertFalse(ready(vehicles.selectionCatalog, VehicleCategory.MOTO).brands().isEmpty())
            assertSame(car, ready(f.home().selectionCatalog))
            assertEquals(1, f.source.seedReads.get())
            assertEquals(0, f.catalog.mainCalls.get())
            assertEquals(0, f.source.mainReads.get())
        }
    }

    @Test
    fun creatingAndEditingCarAndMotorcycle_stillPersistCorrectSnapshots() = runBlocking {
        fixture { f ->
            val vm = f.vehicles()
            for (category in VehicleCategory.entries) {
                withContext(Dispatchers.Main.immediate) { vm.selectCategory(category) }
                val selector = ready(vm.selectionCatalog, category)
                val brand = selector.brands().first()
                val model = selector.modelsFor(brand.id).first()
                val year = selector.yearsFor(brand.id, model.id).first()
                val info = selector.variantsFor(brand.id, model.id, year).first().vehicle
                val snapshot = Vehicle(
                    brand = info.brand, model = info.model, year = info.year, category = category,
                    type = info.type, fuelTankCapacity = info.fuelTankCapacity,
                    batteryCapacity = info.batteryCapacity, initialKm = 100.0
                )
                val created = CompletableDeferred<Unit>()
                withContext(Dispatchers.Main.immediate) { vm.createVehicle(snapshot) { created.complete(Unit) } }
                created.await()
                val entity = f.database.vehicleDao().observeAllVehicles().first().first { it.category == category }
                assertEquals(snapshot.copy(id = entity.id), entity.toVehicle())
                assertNotNull(selector.initialSelectionFor(entity.toVehicle()))
                val edited = CompletableDeferred<Unit>()
                withContext(Dispatchers.Main.immediate) {
                    vm.updateVehicle(entity.id, snapshot.copy(initialKm = 200.0)) { edited.complete(Unit) }
                }
                edited.await()
                assertEquals(200.0, requireNotNull(f.database.vehicleDao().getVehicle(entity.id)).initialKm, 0.0)
            }
            assertEquals(2, f.database.vehicleDao().observeAllVehicles().first().size)
            assertEquals(1, f.source.seedReads.get())
        }
    }

    private suspend fun fixture(block: suspend (Fixture) -> Unit) {
        val f = Fixture()
        try {
            withTimeout(15.seconds) { block(f) }
        } finally {
            withContext(Dispatchers.Main.immediate) { f.viewModels.clear() }
            requireNotNull(f.scope.coroutineContext[Job]).cancelAndJoin()
            f.database.close()
        }
    }

    private suspend fun ready(
        state: kotlinx.coroutines.flow.StateFlow<VehicleSelectionState>,
        category: VehicleCategory = VehicleCategory.COCHE
    ) = (state.first { it is VehicleSelectionState.Ready && it.catalog.category == category } as VehicleSelectionState.Ready).catalog

    private class Fixture {
        private val context = InstrumentationRegistry.getInstrumentation().targetContext
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val database = Room.inMemoryDatabaseBuilder(context, HybridCarDatabase::class.java).build()
        val viewModels = ViewModelStore()
        val source = CountingSource()
        val catalog = CountingCatalog(VehicleDataSource(source))
        private val preferences = object : VehiclePreferencesStore {
            override suspend fun loadVehicle() = Vehicle()
            override suspend fun saveVehicle(vehicle: Vehicle) = Unit
            override suspend fun clearVehicle() = Unit
        }
        private val repository = VehicleRepository(
            catalog, preferences, database.vehicleDao(), database.fuelEntryDao(), database.maintenanceDao(), scope
        )
        private var nextId = 0
        suspend fun home() = withContext(Dispatchers.Main.immediate) {
            HomeViewModel(FuelRepository(database.fuelEntryDao()), repository).also {
                viewModels.put("home-${nextId++}", it)
            }
        }
        suspend fun vehicles() = withContext(Dispatchers.Main.immediate) {
            MyVehiclesViewModel(repository).also { viewModels.put("vehicles-${nextId++}", it) }
        }
    }

    private class CountingSource : VehicleCatalogLocalSource {
        val seedReads = AtomicInteger()
        val mainReads = AtomicInteger()
        override fun readInstalledCatalog(): String? = null
        override fun readBundledSeed(): String {
            seedReads.incrementAndGet()
            if (Looper.myLooper() == Looper.getMainLooper()) mainReads.incrementAndGet()
            val start = SystemClock.elapsedRealtimeNanos()
            val text = InstrumentationRegistry.getInstrumentation().targetContext.assets
                .open(RUNTIME_CATALOG_ASSET).bufferedReader().use { it.readText() }
            Log.d("KilonomSelectorProbe", "seedReadMs=${(SystemClock.elapsedRealtimeNanos() - start) / 1_000_000.0}")
            return text
        }
    }

    private class CountingCatalog(private val delegate: VehicleCatalog) : VehicleCatalog by delegate {
        val calls = AtomicInteger()
        val mainCalls = AtomicInteger()
        override fun loadSelectionCatalog(category: VehicleCategory): VehicleSelectionCatalog {
            calls.incrementAndGet()
            if (Looper.myLooper() == Looper.getMainLooper()) mainCalls.incrementAndGet()
            val start = SystemClock.elapsedRealtimeNanos()
            val result = delegate.loadSelectionCatalog(category)
            Log.d("KilonomSelectorProbe", "category=$category totalMs=${(SystemClock.elapsedRealtimeNanos() - start) / 1_000_000.0} main=${Looper.myLooper() == Looper.getMainLooper()}")
            return result
        }
    }
}
