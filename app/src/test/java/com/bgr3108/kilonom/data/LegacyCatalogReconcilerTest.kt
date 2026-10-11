package com.bgr3108.kilonom.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LegacyCatalogReconcilerTest {

    private val runtime by lazy { parseRuntimeVehicleCatalog(findAsset(RUNTIME_CATALOG_ASSET).readText()) }

    @Test
    fun representativeLegacyVehiclesResolveToOneCompatibleCanonicalActiveRecord() {
        listOf(
            LegacySelection(VehicleCategory.COCHE, "Opel", "Corsa", 2020),
            LegacySelection(VehicleCategory.COCHE, "Citroën", "Berlingo", 2010),
            LegacySelection(VehicleCategory.COCHE, "Nissan", "Qashqai", 2015),
            LegacySelection(VehicleCategory.COCHE, "SEAT", "León e-Hybrid", 2021),
            LegacySelection(VehicleCategory.COCHE, "Toyota", "RAV4 Plug-in Hybrid", 2022),
            LegacySelection(VehicleCategory.MOTO, "Yamaha", "MT-07", 2022),
            LegacySelection(VehicleCategory.MOTO, "Zero", "SR/F", 2023)
        ).forEach { (category, brand, model, year) ->
            val entity = legacySnapshot(category, brand, model, year, prefix = true)
            assertTrue(runtime.reconcileLegacyVehicle(entity) is LegacyCatalogReconciliation.ExactMatch)
        }
    }

    @Test
    fun incompatibleSnapshotOrExistingCatalogReferenceIsNeverSuggested() {
        val entity = legacySnapshot(VehicleCategory.COCHE, "Opel", "Corsa 1.2", 2020)
        assertEquals(
            LegacyCatalogReconciliation.Conflict,
            runtime.reconcileLegacyVehicle(entity.copy(fuelTankCapacity = entity.fuelTankCapacity + 1.0))
        )
        assertEquals(
            LegacyCatalogReconciliation.Conflict,
            runtime.reconcileLegacyVehicle(entity.copy(type = VehicleType.DIESEL))
        )
        assertEquals(
            LegacyCatalogReconciliation.NoMatch,
            runtime.reconcileLegacyVehicle(entity.copy(catalogId = "already-linked"))
        )
        assertEquals(
            LegacyCatalogReconciliation.NoMatch,
            runtime.reconcileLegacyVehicle(entity.copy(model = "No existe"))
        )
    }

    @Test
    fun usableBatteryIsComparedButUnclassifiedBatteryIsNotPretendedToBeOperational() {
        val usable = runtime.vehicles.first { it.battery.usableKwh != null && it.legacyKeys.isNotEmpty() }
        val usableKey = usable.legacyKeys.first()
        val usableEntity = entityFor(usable, usableKey)
        assertEquals(
            LegacyCatalogReconciliation.Conflict,
            runtime.reconcileLegacyVehicle(usableEntity.copy(batteryCapacity = usableEntity.batteryCapacity + 0.1))
        )

        val unclassified = runtime.vehicles.first { it.battery.usableKwh == null && it.battery.hasDeclaredCapacity() && it.legacyKeys.isNotEmpty() }
        val unclassifiedEntity = entityFor(unclassified, unclassified.legacyKeys.first())
        assertTrue(
            runtime.reconcileLegacyVehicle(unclassifiedEntity.copy(batteryCapacity = 999.0)) is
                LegacyCatalogReconciliation.ExactMatch
        )
    }

    @Test
    fun editorialReplacementChainsReachExactlyOneActiveRecordAndRejectBrokenGraphs() {
        val active = EditorialCatalogTraceRecord("active", true, null, emptyList())
        val inactive = EditorialCatalogTraceRecord("old", false, "middle", emptyList())
        val middle = EditorialCatalogTraceRecord("middle", false, "active", emptyList())
        assertEquals(
            EditorialCatalogTraceResolution.Active("active"),
            resolveEditorialCanonicalCatalogId("old", mapOf("old" to inactive, "middle" to middle, "active" to active))
        )
        assertEquals(
            EditorialCatalogTraceResolution.BrokenReference,
            resolveEditorialCanonicalCatalogId("old", mapOf("old" to inactive))
        )
        assertEquals(
            EditorialCatalogTraceResolution.Cycle,
            resolveEditorialCanonicalCatalogId(
                "old",
                mapOf(
                    "old" to inactive.copy(replacedByCatalogId = "middle"),
                    "middle" to middle.copy(replacedByCatalogId = "old")
                )
            )
        )
    }

    @Test
    fun all565LegacyKeysHaveOneUnbrokenEditorialTraceToAnActiveCanonicalRecord() {
        val records = readEditorialRecords()
        val recordsById = records.associateBy { it.catalogId }
        assertEquals(records.size, recordsById.size)
        records.filter { !it.active && it.replacedByCatalogId != null }.forEach { record ->
            assertTrue(resolveEditorialCanonicalCatalogId(record.catalogId, recordsById) is EditorialCatalogTraceResolution.Active)
        }

        val legacyKeys = readLegacyKeys("vehicles.json", VehicleCategory.COCHE) +
            readLegacyKeys("motorcycles.json", VehicleCategory.MOTO)
        assertEquals(565, legacyKeys.size)
        legacyKeys.forEach { key ->
            val finals = records.asSequence()
                .filter { it.legacyKeys.contains(key) }
                .map { resolveEditorialCanonicalCatalogId(it.catalogId, recordsById) }
                .map { resolution -> (resolution as? EditorialCatalogTraceResolution.Active)?.catalogId }
                .filterNotNull()
                .toSet()
            assertEquals("legacy key without one canonical ACTIVE: $key", 1, finals.size)
        }
    }

    private fun legacySnapshot(
        category: VehicleCategory,
        brand: String,
        model: String,
        year: Int,
        prefix: Boolean = false
    ): VehicleEntity {
        val vehicle = runtime.vehicles.first { candidate ->
            candidate.legacyKeys.any { key ->
                key.category == category && key.brand == brand &&
                    (key.model == model || prefix && key.model.startsWith(model)) && key.year == year
            }
        }
        val key = vehicle.legacyKeys.first {
            it.category == category && it.brand == brand &&
                (it.model == model || prefix && it.model.startsWith(model)) && it.year == year
        }
        return entityFor(vehicle, key)
    }

    private fun entityFor(vehicle: RuntimeVehicle, key: RuntimeLegacyKey): VehicleEntity {
        val snapshot = vehicle.toVehicleInfo(key.year)
        return VehicleEntity(
            id = 10,
            category = key.category,
            brand = key.brand,
            model = key.model,
            year = key.year,
            type = snapshot.type,
            fuelTankCapacity = snapshot.fuelTankCapacity,
            batteryCapacity = snapshot.batteryCapacity,
            initialKm = 12_000.0,
            createdAt = 1
        )
    }

    private fun readEditorialRecords(): List<EditorialCatalogTraceRecord> =
        Json.parseToJsonElement(findEditorial().readText())
            .jsonObject.getValue("vehicles").jsonArray.map { element ->
                val vehicle = element.jsonObject
                EditorialCatalogTraceRecord(
                    catalogId = vehicle.getValue("catalogId").jsonPrimitive.content,
                    active = vehicle.getValue("editorialStatus").jsonPrimitive.content == "ACTIVE",
                    replacedByCatalogId = vehicle["replacedByCatalogId"]?.jsonPrimitive?.contentOrNull,
                    legacyKeys = vehicle.getValue("legacyKeys").jsonArray.map { legacyElement ->
                        val key = legacyElement.jsonObject
                        RuntimeLegacyKey(
                            category = VehicleCategory.valueOf(key.getValue("category").jsonPrimitive.content),
                            brand = key.getValue("brand").jsonPrimitive.content,
                            model = key.getValue("model").jsonPrimitive.content,
                            year = key.getValue("year").jsonPrimitive.content.toInt()
                        )
                    }
                )
            }

    private fun readLegacyKeys(name: String, category: VehicleCategory): List<RuntimeLegacyKey> =
        Json.parseToJsonElement(findAsset(name).readText()).jsonArray.map { element ->
            val legacy = element.jsonObject
            RuntimeLegacyKey(
                category = category,
                brand = legacy.getValue("brand").jsonPrimitive.content,
                model = legacy.getValue("model").jsonPrimitive.content,
                year = legacy.getValue("year").jsonPrimitive.content.toInt()
            )
        }

    private fun findAsset(name: String): File = sequenceOf(
        File("app/src/main/assets", name),
        File("src/main/assets", name),
        File("../app/src/main/assets", name)
    ).firstOrNull(File::isFile) ?: error("Asset not found: $name")

    private fun findEditorial(): File = sequenceOf(
        File("catalog/editorial/catalog-editorial.json"),
        File("../catalog/editorial/catalog-editorial.json"),
        File("../../catalog/editorial/catalog-editorial.json")
    ).firstOrNull(File::isFile) ?: error("Editorial catalog not found")

    private data class LegacySelection(
        val category: VehicleCategory,
        val brand: String,
        val model: String,
        val year: Int
    )
}
