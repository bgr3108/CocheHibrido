package com.bgr3108.kilonom.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class VehicleVersionAssistantTest {
    private val cars by lazy { parseRuntimeVehicleCatalog(readRuntime()).selectionFor(VehicleCategory.COCHE) }
    private val motorcycles by lazy { parseRuntimeVehicleCatalog(readRuntime()).selectionFor(VehicleCategory.MOTO) }

    @Test
    fun corsa2020OffersOnlyCatalogEnergyChoicesAndResolvesEachOne() {
        val assistant = cars.assistantFor("Opel", "Corsa", 2020)

        assertEquals(listOf("Gasolina", "Diésel", "Eléctrico"), assistant.energyOptions.map { it.displayName })
        assertTrue(assistant.resolve(VehicleType.GASOLINA) is VehicleVersionAssistantResolution.Match)
        assertTrue(assistant.resolve(VehicleType.DIESEL) is VehicleVersionAssistantResolution.Match)
        assertTrue(assistant.resolve(VehicleType.ELECTRICO) is VehicleVersionAssistantResolution.Match)
    }

    @Test
    fun corsa2024AndNiroUseOnlyTheirAvailableEnergyChoices() {
        assertEquals(
            listOf("Gasolina", "Híbrido", "Eléctrico"),
            cars.assistantFor("Opel", "Corsa", 2024).energyOptions.map { it.displayName }
        )
        assertEquals(
            listOf("Híbrido", "Híbrido enchufable", "Eléctrico"),
            cars.assistantFor("Kia", "Niro", 2025).energyOptions.map { it.displayName }
        )
    }

    @Test
    fun qashqai2024DoesNotGuessWhenCatalogHasNoIdentificationHint() {
        val assistant = cars.assistantFor("Nissan", "Qashqai", 2024)

        assertEquals(listOf("Híbrido"), assistant.energyOptions.map { it.displayName })
        val result = assistant.resolve(VehicleType.HIBRIDO)
        assertEquals(VehicleVersionAssistantResolution.Unresolved, result)
    }

    @Test
    fun leon2025UsesTheSameCatalogDrivenEnergyFilter() {
        val assistant = cars.assistantFor("SEAT", "León", 2025)

        assertEquals(
            listOf("Gasolina", "Diésel", "Híbrido enchufable"),
            assistant.energyOptions.map { it.displayName }
        )
        assertTrue(assistant.resolve(VehicleType.HIBRIDO_ENCHUFABLE) is VehicleVersionAssistantResolution.Match)
    }

    @Test
    fun assistantIsHiddenWhenThereIsOnlyOneFunctionalOption() {
        assertEquals(null, cars.assistantOrNull("Toyota", "Yaris", 2024))
        assertEquals(null, motorcycles.assistantOrNull("Honda", "CB500F", 2020))
    }

    @Test
    fun structuredFutureVehicleWorksWithoutVehicleFamilyMappings() {
        val astra = fakeVariant(
            id = "future-astra",
            displayName = "1.2 Turbo gasolina",
            type = VehicleType.GASOLINA,
            brand = "Opel",
            model = "Astra"
        )
        val electricAstra = fakeVariant(
            id = "future-astra-electric",
            displayName = "Eléctrico",
            type = VehicleType.ELECTRICO,
            brand = "Opel",
            model = "Astra"
        )

        val assistant = VehicleVersionAssistant(listOf(astra, electricAstra))
        assertEquals(listOf("Gasolina", "Eléctrico"), assistant.energyOptions.map { it.displayName })
        assertEquals(
            astra,
            (assistant.resolve(VehicleType.GASOLINA) as VehicleVersionAssistantResolution.Match).variant
        )
    }

    @Test
    fun indistinguishableHumanLabelsLeaveTheDecisionUnresolvedInsteadOfGuessing() {
        val first = fakeVariant("one", "Gasolina", VehicleType.GASOLINA, "Marca", "Modelo", 40.0)
        val second = fakeVariant("two", "Gasolina", VehicleType.GASOLINA, "Marca", "Modelo", 45.0)

        assertEquals(
            VehicleVersionAssistantResolution.Unresolved,
            VehicleVersionAssistant(listOf(first, second)).resolve(VehicleType.GASOLINA)
        )
    }

    @Test
    fun corsa2019GasolineUsesP1DisplacementFromCatalogInsteadOfGenerationNames() {
        val assistant = cars.assistantFor("Opel", "Corsa", 2019)

        val result = assistant.resolve(VehicleType.GASOLINA) as VehicleVersionAssistantResolution.AskIdentification
        assertEquals(VehicleIdentificationField.DISPLACEMENT_CC, result.hint.field)
        assertEquals("Busca el campo P.1 en tu ficha técnica o permiso de circulación.", result.hint.instruction)
        assertEquals(listOf("1.199 cm³", "1.364 o 1.398 cm³"), result.hint.options.map { it.displayName })

        val match = assistant.resolve(
            VehicleType.GASOLINA,
            mapOf(result.hint.field to result.hint.options.first().values)
        ) as VehicleVersionAssistantResolution.Match
        assertEquals(44.0, match.variant.vehicle.fuelTankCapacity, 0.0)
        assertEquals(
            VehicleVersionAssistantResolution.Unresolved,
            assistant.resolve(VehicleType.GASOLINA, skippedFields = setOf(result.hint.field))
        )
    }

    @Test
    fun corsa2019DieselUsesP1AndKeepsTheCorrectTankSnapshot() {
        val assistant = cars.assistantFor("Opel", "Corsa", 2019)
        val result = assistant.resolve(VehicleType.DIESEL) as VehicleVersionAssistantResolution.AskIdentification

        assertEquals(VehicleIdentificationField.DISPLACEMENT_CC, result.hint.field)
        assertEquals(listOf("1.248 cm³", "1.499 cm³"), result.hint.options.map { it.displayName })
        val modern = assistant.resolve(
            VehicleType.DIESEL,
            mapOf(result.hint.field to result.hint.options.single { it.displayName == "1.499 cm³" }.values)
        ) as VehicleVersionAssistantResolution.Match
        assertEquals(41.0, modern.variant.vehicle.fuelTankCapacity, 0.0)
        assertEquals(
            VehicleVersionAssistantResolution.Unresolved,
            assistant.resolve(VehicleType.DIESEL, skippedFields = setOf(result.hint.field))
        )
    }

    @Test
    fun claseB2011DieselUsesP1AndKeepsTheCorrectTankSnapshot() {
        val assistant = cars.assistantFor("Mercedes-Benz", "Clase B", 2011)
        val result = assistant.resolve(VehicleType.DIESEL) as VehicleVersionAssistantResolution.AskIdentification

        assertEquals(VehicleIdentificationField.DISPLACEMENT_CC, result.hint.field)
        assertEquals(listOf("1.796 cm³", "1.991 cm³"), result.hint.options.map { it.displayName })
        val newer = assistant.resolve(
            VehicleType.DIESEL,
            mapOf(result.hint.field to result.hint.options.single { it.displayName == "1.796 cm³" }.values)
        ) as VehicleVersionAssistantResolution.Match
        assertEquals(50.0, newer.variant.vehicle.fuelTankCapacity, 0.0)
    }

    @Test
    fun unresolvedTransitionsDoNotOfferANonDiscriminatingDocumentField() {
        assertEquals(
            VehicleVersionAssistantResolution.Unresolved,
            cars.assistantFor("Opel", "Corsa", 2006).resolve(VehicleType.GASOLINA)
        )
        assertEquals(
            VehicleVersionAssistantResolution.Unresolved,
            cars.assistantFor("Opel", "Corsa", 2006).resolve(VehicleType.DIESEL)
        )
        assertEquals(
            VehicleVersionAssistantResolution.Unresolved,
            cars.assistantFor("SEAT", "Ibiza", 2017).resolve(VehicleType.GASOLINA)
        )
    }

    @Test
    fun powerHintResolvesAStructuredFutureVehicleWithoutFamilyMappings() {
        val first = fakeVariant("one", "Gasolina", VehicleType.GASOLINA, "Marca", "Modelo", 40.0, powerKw = listOf(55))
        val second = fakeVariant("two", "Gasolina", VehicleType.GASOLINA, "Marca", "Modelo", 45.0, powerKw = listOf(74))
        val assistant = VehicleVersionAssistant(listOf(first, second))

        val result = assistant.resolve(VehicleType.GASOLINA) as VehicleVersionAssistantResolution.AskIdentification
        assertEquals(VehicleIdentificationField.POWER_KW, result.hint.field)
        val match = assistant.resolve(VehicleType.GASOLINA, mapOf(result.hint.field to setOf("74")))
        assertEquals(second, (match as VehicleVersionAssistantResolution.Match).variant)
    }

    @Test
    fun notFoundSkipsToTheNextCatalogHintWithoutGuessing() {
        val first = fakeVariant(
            "future-astra-12", "Gasolina", VehicleType.GASOLINA, "Opel", "Astra", 45.0,
            displacementCc = listOf(1199), powerKw = listOf(96)
        )
        val second = fakeVariant(
            "future-astra-15", "Gasolina", VehicleType.GASOLINA, "Opel", "Astra", 50.0,
            displacementCc = listOf(1499), powerKw = listOf(110)
        )
        val assistant = VehicleVersionAssistant(listOf(first, second))

        val firstHint = assistant.resolve(VehicleType.GASOLINA) as VehicleVersionAssistantResolution.AskIdentification
        assertEquals(VehicleIdentificationField.DISPLACEMENT_CC, firstHint.hint.field)
        val secondHint = assistant.resolve(
            VehicleType.GASOLINA,
            skippedFields = setOf(VehicleIdentificationField.DISPLACEMENT_CC)
        ) as VehicleVersionAssistantResolution.AskIdentification
        assertEquals(VehicleIdentificationField.POWER_KW, secondHint.hint.field)
    }

    @Test
    fun partiallyDiscriminatingHintCanLeadToTheNextCatalogHint() {
        val first = fakeVariant(
            "future-one", "Gasolina", VehicleType.GASOLINA, "Marca", "Modelo", 40.0,
            displacementCc = listOf(1200, 1400), powerKw = listOf(55)
        )
        val second = fakeVariant(
            "future-two", "Gasolina", VehicleType.GASOLINA, "Marca", "Modelo", 45.0,
            displacementCc = listOf(1400, 1600), powerKw = listOf(74)
        )
        val assistant = VehicleVersionAssistant(listOf(first, second))

        val displacement = assistant.resolve(VehicleType.GASOLINA) as VehicleVersionAssistantResolution.AskIdentification
        val sharedValue = displacement.hint.options.single { it.displayName == "1.400 cm³" }.values
        val power = assistant.resolve(
            VehicleType.GASOLINA,
            answers = mapOf(VehicleIdentificationField.DISPLACEMENT_CC to sharedValue)
        ) as VehicleVersionAssistantResolution.AskIdentification
        assertEquals(VehicleIdentificationField.POWER_KW, power.hint.field)
    }

    private fun VehicleSelectionCatalog.assistantFor(brand: String, model: String, year: Int): VehicleVersionAssistant {
        return assistantOrNull(brand, model, year) ?: error("Expected assistant for $brand $model $year")
    }

    private fun VehicleSelectionCatalog.assistantOrNull(brand: String, model: String, year: Int): VehicleVersionAssistant? {
        val brandId = brands().single { it.displayName == brand }.id
        val modelId = modelsFor(brandId).single { it.displayName == model }.id
        return versionAssistantFor(brandId, modelId, year)
    }

    private fun fakeVariant(
        id: String,
        displayName: String,
        type: VehicleType,
        brand: String,
        model: String,
        tank: Double = 45.0,
        displacementCc: List<Int> = emptyList(),
        powerKw: List<Int> = emptyList()
    ) = VehicleSelectionVariant(
        id = id,
        catalogId = id,
        displayName = displayName,
        automaticDisplayName = displayName,
        energyType = type,
        identification = RuntimeVehicleIdentification(displacementCc, powerKw, emptyList(), emptyList()),
        bodyStyle = null,
        vehicle = VehicleInfo(brand, model, 2026, VehicleCategory.COCHE, type, 0.0, tank)
    )

    private fun readRuntime(): String = sequenceOf(
        File("app/src/main/assets", RUNTIME_CATALOG_ASSET),
        File("src/main/assets", RUNTIME_CATALOG_ASSET),
        File("../app/src/main/assets", RUNTIME_CATALOG_ASSET)
    ).firstOrNull(File::isFile)?.readText() ?: error("Asset not found")
}
