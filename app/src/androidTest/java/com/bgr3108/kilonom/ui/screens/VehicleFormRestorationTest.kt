package com.bgr3108.kilonom.ui.screens

import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.inspector.WindowInspector
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.ui.node.RootForTest
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.bgr3108.kilonom.data.*
import com.bgr3108.kilonom.testing.OperationTestActivity
import com.bgr3108.kilonom.viewmodel.VehicleSelectionLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Production form/loader, real Activity saved state and semantic actions; no personal persistence. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class VehicleFormRestorationTest {
    @Test fun openAssistantRestoresMultipleAnswersOmissionsAndEditingFields() = runBlocking {
        fixture { f, scenario ->
            f.fillAssistant()
            f.setKm("4321")
            val saved = f.capture()
            assertTrue(flatten(saved).containsAll(listOf("DISPLACEMENT_CC", "POWER_KW", "COMMERCIAL_VERSION", "GASOLINA")))
            scenario.recreate()
            f.awaitText(ENGINE_QUESTION)
            f.awaitText("4321")
            f.awaitText("MarcaCoche")
            assertEquals(1, f.source.calls.get())
            assertEquals(0, f.submissions.get())
        }
    }

    @Test fun skippedQuestionRemainsSkippedAfterActivityRecreation() = runBlocking {
        fixture { f, scenario ->
            f.click(OPEN_ASSISTANT)
            f.click("Gasolina")
            f.awaitText(DISPLACEMENT_QUESTION)
            f.click("No lo encuentro")
            f.awaitText(POWER_QUESTION)
            scenario.recreate()
            f.awaitText(POWER_QUESTION)
            assertFalse(f.hasText(DISPLACEMENT_QUESTION))
            assertEquals(0, f.submissions.get())
        }
    }

    @Test fun motorcycleCategorySelectionVariantAndOtherFieldsSurviveRecreation() = runBlocking {
        fixture { f, scenario ->
            f.selectMotorcycle()
            f.fillAssistant(motorcycle = true)
            scenario.recreate()
            f.awaitText(ENGINE_QUESTION)
            f.click("E0")
            f.awaitText("Hemos encontrado una coincidencia")
            f.click("Usar esta versión")
            f.awaitText("MarcaMoto")
            f.setKm("5678")
            scenario.recreate()
            f.awaitText("5678")
            assertTrue(f.isSelected("Moto"))
            f.awaitText("Versión 0 gasolina")
            assertEquals(0, f.submissions.get())
            f.click("Guardar") // Captured by a fake callback, never by a repository or Room.
            assertEquals(1, f.submissions.get())
            assertEquals(VehicleCategory.MOTO, f.submitted?.category)
            assertEquals(5678.0, f.submitted?.initialKm)
            assertEquals("MarcaMoto", f.submitted?.brand)
            assertEquals("ModeloMoto", f.submitted?.model)
        }
    }

    @Test fun recreationDuringInitialDeferredLoadDoesNotRestartItOrReadOnMain() = runBlocking {
        val source = Catalog().apply { gate = CountDownLatch(1) }
        fixture(source, waitForForm = false) { f, scenario ->
            f.awaitText(LOADING)
            scenario.recreate()
            f.awaitText(LOADING)
            assertEquals(1, source.calls.get())
            source.gate?.countDown()
            f.awaitText("MarcaCoche")
            assertEquals(1, source.calls.get())
            assertEquals(0, source.mainCalls.get())
            assertEquals(0, f.submissions.get())
        }
    }

    @Test fun recoverableCatalogErrorPreservesTheRemovedFormAndAssistantState() = runBlocking {
        fixture { f, scenario ->
            f.fillAssistant()
            f.setKm("2468")
            f.source.failNext = true
            withContext(Dispatchers.Main.immediate) { f.model.loader.retry() }
            f.awaitText(FAILED)
            scenario.recreate()
            f.awaitText(FAILED)
            f.click("Reintentar")
            f.awaitText(ENGINE_QUESTION)
            f.awaitText("2468")
            assertEquals(3, f.source.calls.get())
            assertEquals(0, f.submissions.get())
        }
    }

    @Test fun freshRegistryAndNewViewModelRestoreMotorcycleProgressBeforeDeferredCatalogLoads() = runBlocking {
        fixture { f, scenario ->
            f.selectMotorcycle()
            f.fillAssistant(motorcycle = true)
            f.setKm("1357")
            val saved = f.capture()
            val oldModel = f.model
            scenario.close()
            f.restoredValues = saved
            f.source.gate = CountDownLatch(1)
            // A fresh Activity/ViewModel and saveable registry, not an actual killed process.
            ActivityScenario.launch(OperationTestActivity::class.java).use {
                f.awaitText(LOADING)
                assertNotSame(oldModel, f.model)
                f.source.gate?.countDown()
                f.awaitText(ENGINE_QUESTION)
                f.awaitText("1357")
                assertTrue(f.isSelected("Moto"))
                assertEquals(0, f.source.mainCalls.get())
                assertEquals(0, f.submissions.get())
            }
        }
    }

    @Test fun categoryChangeDoesNotReuseIncompatibleAssistantAnswers() = runBlocking {
        fixture { f, _ ->
            f.fillAssistant()
            f.click("Seguir eligiendo manualmente")
            f.selectMotorcycle()
            f.click(OPEN_ASSISTANT)
            f.click("Gasolina")
            f.awaitText(DISPLACEMENT_QUESTION)
            f.awaitText("600 cm³")
            assertFalse(f.hasText(ENGINE_QUESTION))
            assertEquals(0, f.submissions.get())
        }
    }

    @Test fun emptyCatalogAfterRecreationDoesNotOpenAssistantOrSaveAnything() = runBlocking {
        fixture(Catalog(empty = true), waitForForm = false) { f, scenario ->
            f.awaitText("Formulario de producción")
            scenario.recreate()
            f.awaitText("Formulario de producción")
            assertFalse(f.hasText(OPEN_ASSISTANT))
            assertEquals(0, f.submissions.get())
            assertEquals(0, f.source.mainCalls.get())
        }
    }

    private suspend fun fixture(
        source: Catalog = Catalog(),
        waitForForm: Boolean = true,
        test: suspend (Fixture, ActivityScenario<OperationTestActivity>) -> Unit
    ) {
        val f = Fixture(source)
        OperationTestActivity.content = { f.Content(it) }
        try {
            ActivityScenario.launch(OperationTestActivity::class.java).use { scenario ->
                withTimeout(40.seconds) {
                    if (waitForForm) f.awaitText("MarcaCoche")
                    test(f, scenario)
                }
            }
        } finally {
            source.gate?.countDown()
            OperationTestActivity.content = null
        }
    }

    private class SelectorModel(source: VehicleCatalog) : ViewModel() {
        val category = MutableStateFlow(VehicleCategory.COCHE)
        val loader = VehicleSelectionLoader(viewModelScope, source, category)
    }

    private class Fixture(val source: Catalog) {
        lateinit var model: SelectorModel
        private lateinit var registry: SaveableStateRegistry
        var restoredValues: Map<String, List<Any?>>? = null
        val submissions = AtomicInteger()
        var submitted: Vehicle? = null

        @Composable fun Content(activity: OperationTestActivity) {
            model = ViewModelProvider(activity, viewModelFactory { initializer { SelectorModel(source) } })[SelectorModel::class.java]
            val state by model.loader.state.collectAsStateWithLifecycle()
            val category by model.category.collectAsStateWithLifecycle()
            val platformRegistry = checkNotNull(LocalSaveableStateRegistry.current)
            val restoredRegistry = remember { restoredValues?.let { SaveableStateRegistry(it) { value -> isSmall(value) } } }
            SideEffect { registry = restoredRegistry ?: platformRegistry }
            CompositionLocalProvider(LocalSaveableStateRegistry provides (restoredRegistry ?: platformRegistry)) {
                MaterialTheme {
                    VehicleSelectionContent(state, model.loader::retry) { catalog ->
                        VehicleForm(
                            title = "Formulario de producción", initialVehicle = INITIAL,
                            selectionCatalog = catalog, selectedCategory = category,
                            onCategoryChanged = { model.category.value = it }, catalogEditable = true,
                            isSaving = false, errorMessage = null,
                            onSubmit = { submitted = it; submissions.incrementAndGet() }
                        )
                    }
                }
            }
        }

        suspend fun capture(): Map<String, List<Any?>> = withContext(Dispatchers.Main.immediate) {
            registry.performSave().also { assertTrue("Unexpected saved types: ${unexpectedTypes(it)}", isSmall(it)) }
        }
        suspend fun fillAssistant(motorcycle: Boolean = false) {
            click(OPEN_ASSISTANT)
            click("Gasolina")
            click(if (motorcycle) "600 cm³" else "1.200 cm³")
            click(if (motorcycle) "35 kW" else "55 kW")
            awaitText("¿Qué denominación comercial aparece?")
            click("No lo encuentro")
            awaitText(ENGINE_QUESTION)
        }
        suspend fun selectMotorcycle() {
            click("Moto")
            click("Marca")
            click("MarcaMoto")
            click("Modelo")
            click("ModeloMoto")
            click("Año")
            click("2026")
            awaitText(OPEN_ASSISTANT)
        }
        suspend fun awaitText(text: String) {
            try { withTimeout(10.seconds) {
                while (!hasText(text)) delay(16.milliseconds)
            } } catch (error: kotlinx.coroutines.TimeoutCancellationException) {
                throw AssertionError("Missing text '$text'; ${texts()}", error)
            }
        }
        private suspend fun texts() = withContext(Dispatchers.Main.immediate) {
            nodes().flatMap { it.config.getOrNull(SemanticsProperties.Text).orEmpty().map(AnnotatedString::text) }
        }
        suspend fun hasText(text: String) = withContext(Dispatchers.Main.immediate) { nodes().any { matches(it, text) } }
        suspend fun isSelected(text: String) = withContext(Dispatchers.Main.immediate) {
            nodes().any { it.config.getOrNull(SemanticsProperties.Selected) == true && descendants(it).any { child -> matches(child, text) } }
        }
        suspend fun click(text: String) {
            try { withTimeout(10.seconds) {
                while (true) {
                    val clicked = withContext(Dispatchers.Main.immediate) {
                        val target = nodes().firstOrNull { node ->
                            node.config.getOrNull(SemanticsActions.OnClick)?.action != null && descendants(node).any { matches(it, text) }
                        }
                        target?.config?.getOrNull(SemanticsActions.OnClick)?.action?.invoke() == true
                    }
                    if (clicked) break
                    delay(16.milliseconds)
                }
            } } catch (error: kotlinx.coroutines.TimeoutCancellationException) {
                throw AssertionError("Missing clickable '$text'; ${texts()}", error)
            }
        }
        suspend fun setKm(value: String) = withContext(Dispatchers.Main.immediate) {
            val field = nodes().first { node -> node.config.getOrNull(SemanticsActions.SetText) != null && descendants(node).any { matches(it, "Kilometraje inicial") } }
            assertTrue(field.config[SemanticsActions.SetText].action?.invoke(AnnotatedString(value)) == true)
        }
    }

    private class Catalog(private val empty: Boolean = false) : VehicleCatalog {
        val calls = AtomicInteger()
        val mainCalls = AtomicInteger()
        @Volatile var gate: CountDownLatch? = null
        @Volatile var failNext = false
        private val catalogs = VehicleCategory.entries.associateWith(::catalog)
        override fun loadVehicles(category: VehicleCategory): List<VehicleInfo> = emptyList()
        override fun loadSelectionCatalog(category: VehicleCategory): VehicleSelectionCatalog {
            calls.incrementAndGet()
            if (Looper.myLooper() == Looper.getMainLooper()) mainCalls.incrementAndGet()
            gate?.let { check(it.await(15, TimeUnit.SECONDS)) }
            if (failNext) { failNext = false; throw IOException("simulated catalog error") }
            return if (empty) VehicleSelectionCatalog(category, emptyList()) else catalogs.getValue(category)
        }
    }

    private companion object {
        const val OPEN_ASSISTANT = "¿No sabes cuál es tu versión?"
        const val DISPLACEMENT_QUESTION = "¿Qué cilindrada aparece?"
        const val POWER_QUESTION = "¿Qué potencia aparece?"
        const val ENGINE_QUESTION = "¿Qué variante o código de motor aparece?"
        const val LOADING = "Preparando catálogo de vehículos…"
        const val FAILED = "No se pudo cargar el catálogo de vehículos."
        val INITIAL = Vehicle(id = 7, catalogId = "COCHE-0", brand = "MarcaCoche", model = "ModeloCoche", year = 2026,
            category = VehicleCategory.COCHE, type = VehicleType.GASOLINA, fuelTankCapacity = 40.0, initialKm = 123.0)

        fun catalog(category: VehicleCategory): VehicleSelectionCatalog {
            val suffix = if (category == VehicleCategory.COCHE) "Coche" else "Moto"
            return VehicleSelectionCatalog(category, (0..7).map { index ->
                VehicleSelectionCandidate(
                    catalogId = "${category.name}-$index", category = category,
                    brandId = "brand-$suffix", brandDisplayName = "Marca$suffix", modelId = "model-$suffix", modelDisplayName = "Modelo$suffix",
                    year = 2026, rangeStartYear = 2026, generationId = null, variantDisplayName = "Versión $index", variantId = "$index",
                    generationDisplayName = null, powertrain = RuntimePowertrain(RuntimePowertrainKind.ICE, RuntimeFuel.GASOLINA, null),
                    fuelTankLitres = 40.0 + index, operationalBatteryKwh = null, declaredBatteryKwh = null, bodyStyle = null, drivetrain = null,
                    identification = RuntimeVehicleIdentification(
                        listOf(if (category == VehicleCategory.MOTO) { if (index < 4) 600 else 1000 } else { if (index < 4) 1200 else 1600 }),
                        listOf(if (category == VehicleCategory.MOTO) { if (index % 4 < 2) 35 else 50 } else { if (index % 4 < 2) 55 else 74 }),
                        listOf(if (index % 2 == 0) "Base" else "Plus"), listOf("E$index")
                    ), vehicleInfo = VehicleInfo("Marca$suffix", "Modelo$suffix", 2026, category, VehicleType.GASOLINA, 0.0, 40.0 + index),
                    legacyKeys = emptyList()
                )
            })
        }

        fun nodes(): List<SemanticsNode> = WindowInspector.getGlobalWindowViews().flatMap(::roots).flatMap { descendants(it.semanticsOwner.unmergedRootSemanticsNode).toList() }
        fun roots(view: View): List<RootForTest> = (if (view is RootForTest) listOf(view) else emptyList()) +
            (if (view is ViewGroup) (0 until view.childCount).flatMap { roots(view.getChildAt(it)) } else emptyList())
        fun descendants(node: SemanticsNode): Sequence<SemanticsNode> = sequence {
            yield(node)
            node.children.forEach { yieldAll(descendants(it)) }
        }
        fun matches(node: SemanticsNode, text: String): Boolean = node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == text } == true ||
            node.config.getOrNull(SemanticsProperties.EditableText)?.text == text
        fun flatten(value: Any?): List<String> = when (value) {
            is State<*> -> flatten(value.value)
            is Map<*, *> -> value.entries.flatMap { flatten(it.key) + flatten(it.value) }
            is List<*> -> value.flatMap(::flatten)
            is String -> listOf(value)
            else -> emptyList()
        }
        fun isSmall(value: Any?): Boolean = when (value) {
            null, is String, is Number, is Boolean, is Enum<*>, is UUID -> true
            is State<*> -> isSmall(value.value)
            is Map<*, *> -> value.size < 100 && value.all { isSmall(it.key) && isSmall(it.value) }
            is List<*> -> value.size < 100 && value.all(::isSmall)
            else -> false
        }
        fun unexpectedTypes(value: Any?): List<String> = when (value) {
            is State<*> -> unexpectedTypes(value.value)
            is Map<*, *> -> value.entries.flatMap { unexpectedTypes(it.key) + unexpectedTypes(it.value) }
            is List<*> -> value.flatMap(::unexpectedTypes)
            else -> if (isSmall(value)) emptyList() else listOf(value?.javaClass?.name.orEmpty())
        }
    }
}
