package com.bgr3108.kilonom.viewmodel

import com.bgr3108.kilonom.data.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds

class VehicleSelectionLoaderTest {
    @Test
    fun constructingLoader_doesNotReadOrPrepareCatalog() = runBlocking {
        fixture { f ->
            assertEquals(VehicleSelectionState.Loading, f.loader.state.value)
            assertEquals(0, f.catalog.calls.get())
            assertEquals(0, f.source.seedReads.get())
        }
    }

    @Test
    fun subscribingObtainsRealSelector_withCurrentOrderAndVehicleTypes() = runBlocking {
        fixture { f ->
            val catalog = f.ready()
            val names = catalog.brands().map { it.displayName }
            assertEquals(names.sorted(), names)
            assertTrue(names.contains("SEAT"))
            assertEquals(VehicleType.entries.toSet(), f.catalog.delegate.loadVehicles(VehicleCategory.COCHE).map { it.type }.toSet())
            assertEquals(1, f.source.seedReads.get())
        }
    }

    @Test
    fun twoLoadersAndCategories_reuseOneDataSourceAndItsProjections() = runBlocking {
        fixture { f ->
            val car = f.ready()
            val second = VehicleSelectionLoader(f.scope, f.catalog, f.category)
            assertSame(car, (second.state.first { it is VehicleSelectionState.Ready } as VehicleSelectionState.Ready).catalog)
            val observer = f.scope.launch { f.loader.state.collect {} }
            f.category.value = VehicleCategory.MOTO
            val moto = f.ready(VehicleCategory.MOTO)
            assertFalse(moto.brands().isEmpty())
            f.category.value = VehicleCategory.COCHE
            assertSame(car, f.ready())
            assertEquals(1, f.source.seedReads.get())
            observer.cancelAndJoin()
        }
    }

    @Test
    fun choosingCategoryWithoutConsumer_doesNotLoadUntilFormOpens() = runBlocking {
        fixture { f ->
            f.category.value = VehicleCategory.MOTO
            assertEquals(0, f.catalog.calls.get())
            assertEquals(VehicleCategory.MOTO, f.ready(VehicleCategory.MOTO).category)
            assertEquals(1, f.source.seedReads.get())
        }
    }

    @Test
    fun invalidInstalledCatalog_preservesSeedFallback() = runBlocking {
        fixture(installed = "{ truncated") { f ->
            assertTrue(f.ready().brands().any { it.displayName == "SEAT" })
            assertEquals(1, f.source.seedReads.get())
        }
    }

    @Test
    fun validInstalledCatalog_hasPriorityWithoutReadingSeed() = runBlocking {
        fixture(installed = seed()) { f ->
            assertFalse(f.ready().brands().isEmpty())
            assertEquals(0, f.source.seedReads.get())
        }
    }

    @Test
    fun expectedReadFailure_isRecoverableWithoutChangingCategory() = runBlocking {
        fixture { f ->
            f.catalog.fail = true
            f.loader.state.first { it == VehicleSelectionState.Failed }
            f.catalog.fail = false
            f.loader.retry()
            assertEquals(VehicleCategory.COCHE, f.ready().category)
            assertEquals(VehicleCategory.COCHE, f.category.value)
        }
    }

    private suspend fun fixture(installed: String? = null, block: suspend (Fixture) -> Unit) {
        val f = Fixture(installed)
        try {
            withTimeout(10.seconds) { block(f) }
        } finally {
            requireNotNull(f.scope.coroutineContext[Job]).cancelAndJoin()
        }
    }

    private class Fixture(installed: String?) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val category = MutableStateFlow(VehicleCategory.COCHE)
        val source = CountingSource(installed)
        val catalog = CountingCatalog(VehicleDataSource(source))
        val loader = VehicleSelectionLoader(scope, catalog, category)
        suspend fun ready(category: VehicleCategory = VehicleCategory.COCHE) =
            (loader.state.first { it is VehicleSelectionState.Ready && it.catalog.category == category } as VehicleSelectionState.Ready).catalog
    }

    private class CountingSource(private val installed: String?) : VehicleCatalogLocalSource {
        val seedReads = AtomicInteger()
        override fun readInstalledCatalog(): String? = installed
        override fun readBundledSeed(): String {
            seedReads.incrementAndGet()
            return seed()
        }
    }

    private class CountingCatalog(val delegate: VehicleCatalog) : VehicleCatalog by delegate {
        val calls = AtomicInteger()
        @Volatile var fail = false
        override fun loadSelectionCatalog(category: VehicleCategory): VehicleSelectionCatalog {
            calls.incrementAndGet()
            if (fail) throw IOException("test read failure")
            return delegate.loadSelectionCatalog(category)
        }
    }

    companion object {
        private fun seed() = sequenceOf(
            File("app/src/main/assets", RUNTIME_CATALOG_ASSET),
            File("src/main/assets", RUNTIME_CATALOG_ASSET),
            File("../app/src/main/assets", RUNTIME_CATALOG_ASSET)
        ).first(File::isFile).readText()
    }
}
