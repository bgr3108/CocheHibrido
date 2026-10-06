package com.bgr3108.kilonom.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

/** Strict reader for the compact, bundled vehicle-catalog runtime contract (schema v1). */
internal data class RuntimeVehicleCatalog(
    val schemaVersion: Int,
    val catalogVersion: Int,
    val generatedAt: String,
    val vehicles: List<RuntimeVehicle>
) {
    val selectionYearUpperBound: Int = generatedAt.take(4).toInt()
    private val vehiclesByCategory: Map<VehicleCategory, List<RuntimeVehicle>> = vehicles.groupBy { it.category }
    private val vehiclesByCatalogId: Map<String, RuntimeVehicle> = vehicles.associateBy { it.catalogId }
    private val brandNamesByCategory: Map<VehicleCategory, List<String>> = vehiclesByCategory.mapValues { (_, entries) ->
        entries.map { it.brand.displayName }.distinct().sorted()
    }
    private val modelNamesByCategoryAndBrand: Map<Pair<VehicleCategory, String>, List<String>> =
        vehicles.groupBy { it.category to it.brand.id }.mapValues { (_, entries) ->
            entries.map { it.model.displayName }.distinct().sorted()
        }

    fun vehiclesFor(category: VehicleCategory): List<VehicleInfo> =
        vehiclesByCategory[category].orEmpty().asSequence()
            .flatMap { vehicle -> vehicle.years(selectionYearUpperBound).asSequence().map(vehicle::toVehicleInfo) }
            .sortedWith(compareBy<VehicleInfo> { it.brand }.thenBy { it.model }.thenBy { it.year })
            .toList()

    internal fun findByCatalogId(catalogId: String): RuntimeVehicle? = vehiclesByCatalogId[catalogId]

    internal fun brandsFor(category: VehicleCategory): List<String> = brandNamesByCategory[category].orEmpty()

    internal fun modelsFor(category: VehicleCategory, brandId: String): List<String> =
        modelNamesByCategoryAndBrand[category to brandId].orEmpty()
}

internal data class RuntimeVehicle(
    val catalogId: String,
    val category: VehicleCategory,
    val brand: RuntimeIdentity,
    val model: RuntimeIdentity,
    val generation: RuntimeIdentity?,
    val variant: RuntimeIdentity?,
    val yearFrom: Int,
    val yearTo: Int?,
    val powertrain: RuntimePowertrain,
    val fuelTankLitres: Double?,
    val battery: RuntimeBattery,
    val legacyKeys: List<RuntimeLegacyKey>
) {
    fun years(selectionYearUpperBound: Int): IntRange = yearFrom..(yearTo ?: selectionYearUpperBound)

    fun toVehicleInfo(year: Int): VehicleInfo = VehicleInfo(
        brand = brand.displayName,
        model = buildString {
            append(model.displayName)
            variant?.displayName?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
        },
        year = year,
        category = category,
        type = powertrain.toVehicleType(catalogId),
        // Only a verified usable capacity may drive percentage-to-kWh estimates.
        batteryCapacity = battery.usableKwh ?: 0.0,
        fuelTankCapacity = fuelTankLitres ?: 0.0
    )
}

internal data class RuntimeIdentity(val id: String, val displayName: String)
internal data class RuntimeLegacyKey(
    val category: VehicleCategory,
    val brand: String,
    val model: String,
    val year: Int
)

internal data class RuntimePowertrain(
    val kind: RuntimePowertrainKind,
    val primaryFuel: RuntimeFuel?,
    val hybridSystem: RuntimeHybridSystem?
) {
    fun toVehicleType(catalogId: String): VehicleType = when (kind) {
        RuntimePowertrainKind.ICE -> when (primaryFuel) {
            RuntimeFuel.GASOLINA -> VehicleType.GASOLINA
            RuntimeFuel.DIESEL -> VehicleType.DIESEL
            null -> error("$catalogId.powertrain.primaryFuel is required for ICE")
        }
        RuntimePowertrainKind.HEV -> VehicleType.HIBRIDO
        RuntimePowertrainKind.PHEV -> VehicleType.HIBRIDO_ENCHUFABLE
        RuntimePowertrainKind.BEV -> VehicleType.ELECTRICO
    }
}

internal enum class RuntimePowertrainKind { ICE, HEV, PHEV, BEV }
internal enum class RuntimeFuel { GASOLINA, DIESEL }
internal enum class RuntimeHybridSystem { NONE, MILD, FULL, PLUG_IN, BATTERY_ELECTRIC }
internal enum class RuntimeDeclaredCapacityType { UNKNOWN }

internal data class RuntimeBattery(
    val grossKwh: Double?,
    val usableKwh: Double?,
    val declaredKwh: Double?,
    val declaredCapacityType: RuntimeDeclaredCapacityType?
) {
    fun hasDeclaredCapacity(): Boolean =
        grossKwh != null || usableKwh != null || declaredKwh != null
}

internal const val RUNTIME_CATALOG_ASSET = "catalog/catalog-runtime.json"
private const val RUNTIME_SCHEMA_VERSION = 1
private val runtimeIdRegex = Regex("^[a-z0-9]+(?:-[a-z0-9]+)*$")

internal fun parseRuntimeVehicleCatalog(json: String): RuntimeVehicleCatalog {
    val root = Json.parseToJsonElement(json).jsonObject
    val schemaVersion = root.requiredInt("schemaVersion")
    require(schemaVersion == RUNTIME_SCHEMA_VERSION) {
        "catalog.schemaVersion: expected $RUNTIME_SCHEMA_VERSION, was $schemaVersion"
    }
    val catalogVersion = root.requiredInt("catalogVersion")
    require(catalogVersion > 0) { "catalog.catalogVersion: must be positive" }
    val generatedAt = root.requiredString("generatedAt")
    require(generatedAt.matches(Regex("^\\d{4}-\\d{2}-\\d{2}T.*Z$"))) {
        "catalog.generatedAt: expected UTC ISO-8601 timestamp"
    }
    val selectionYearUpperBound = generatedAt.take(4).toInt()

    val catalogIds = mutableSetOf<String>()
    val legacyKeys = mutableSetOf<String>()
    val vehicles = root.requiredArray("vehicles", "catalog").mapObjects("catalog.vehicles") { index, item ->
        parseRuntimeVehicle(item, "catalog.vehicles[$index]", selectionYearUpperBound).also { vehicle ->
            require(catalogIds.add(vehicle.catalogId)) { "${vehicle.catalogId}: duplicate catalogId" }
            vehicle.legacyKeys.forEach { key ->
                require(legacyKeys.add(key.toStableKey())) { "${vehicle.catalogId}.legacyKeys: duplicate ${key.toStableKey()}" }
            }
        }
    }
    return RuntimeVehicleCatalog(schemaVersion, catalogVersion, generatedAt, vehicles)
}

private fun parseRuntimeVehicle(
    obj: JsonObject,
    context: String,
    selectionYearUpperBound: Int
): RuntimeVehicle {
    val catalogId = obj.requiredId("catalogId", context)
    val category = obj.requiredEnum<VehicleCategory>("category", catalogId)
    val brand = obj.requiredIdentity("brand", catalogId)
    val model = obj.requiredIdentity("model", catalogId)
    val generation = obj.optionalIdentity("generation", catalogId)
    val variant = obj.optionalIdentity("variant", catalogId)
    val yearFrom = obj.requiredInt("yearFrom", catalogId)
    val yearTo = obj.optionalInt("yearTo", catalogId)
    require(yearFrom in 1886..selectionYearUpperBound) { "$catalogId.yearFrom: invalid year" }
    require(yearTo == null || yearTo >= yearFrom) { "$catalogId.yearTo: before yearFrom" }

    val powertrainObject = obj.requiredObject("powertrain", catalogId)
    val powertrain = RuntimePowertrain(
        kind = powertrainObject.requiredEnum("kind", "$catalogId.powertrain"),
        primaryFuel = powertrainObject.optionalEnum("primaryFuel", "$catalogId.powertrain"),
        hybridSystem = powertrainObject.optionalEnum("hybridSystem", "$catalogId.powertrain")
    )
    val fuelTankLitres = obj.optionalPositiveDouble("fuelTankLitres", catalogId)
    val batteryObject = obj.requiredObject("battery", catalogId)
    val battery = RuntimeBattery(
        grossKwh = batteryObject.optionalPositiveDouble("grossKwh", "$catalogId.battery"),
        usableKwh = batteryObject.optionalPositiveDouble("usableKwh", "$catalogId.battery"),
        declaredKwh = batteryObject.optionalPositiveDouble("declaredKwh", "$catalogId.battery"),
        declaredCapacityType = batteryObject.optionalEnum("declaredCapacityType", "$catalogId.battery")
    )
    val legacyKeys = obj.requiredArray("legacyKeys", catalogId).mapObjects("$catalogId.legacyKeys") { _, key ->
        RuntimeLegacyKey(
            category = key.requiredEnum("category", "$catalogId.legacyKeys"),
            brand = key.requiredString("brand", "$catalogId.legacyKeys"),
            model = key.requiredString("model", "$catalogId.legacyKeys"),
            year = key.requiredInt("year", "$catalogId.legacyKeys")
        )
    }
    validateRuntimeVehicle(catalogId, powertrain, fuelTankLitres, battery)
    return RuntimeVehicle(
        catalogId, category, brand, model, generation, variant, yearFrom, yearTo,
        powertrain, fuelTankLitres, battery, legacyKeys
    )
}

private fun validateRuntimeVehicle(
    catalogId: String,
    powertrain: RuntimePowertrain,
    fuelTankLitres: Double?,
    battery: RuntimeBattery
) {
    require(battery.usableKwh == null || battery.grossKwh == null || battery.usableKwh <= battery.grossKwh) {
        "$catalogId.battery.usableKwh: cannot exceed grossKwh"
    }
    require((battery.declaredKwh == null) == (battery.declaredCapacityType == null)) {
        "$catalogId.battery.declaredKwh and declaredCapacityType must be supplied together"
    }
    when (battery.declaredCapacityType) {
        null -> Unit
        RuntimeDeclaredCapacityType.UNKNOWN -> require(battery.declaredKwh != null) {
            "$catalogId.battery.declaredKwh is required when its capacity type is UNKNOWN"
        }
    }
    when (powertrain.kind) {
        RuntimePowertrainKind.BEV -> {
            require(fuelTankLitres == null) { "$catalogId.fuelTankLitres: BEV must not have a tank" }
            require(battery.hasDeclaredCapacity()) { "$catalogId.battery: BEV requires a battery" }
            require(powertrain.primaryFuel == null) { "$catalogId.powertrain.primaryFuel: BEV must be null" }
            require(powertrain.hybridSystem == RuntimeHybridSystem.BATTERY_ELECTRIC) {
                "$catalogId.powertrain.hybridSystem: BEV requires BATTERY_ELECTRIC"
            }
        }
        RuntimePowertrainKind.PHEV -> {
            require(fuelTankLitres != null) { "$catalogId.fuelTankLitres: PHEV requires a tank" }
            require(battery.hasDeclaredCapacity()) { "$catalogId.battery: PHEV requires a battery" }
            require(powertrain.hybridSystem == RuntimeHybridSystem.PLUG_IN) {
                "$catalogId.powertrain.hybridSystem: PHEV requires PLUG_IN"
            }
        }
        RuntimePowertrainKind.HEV -> {
            require(fuelTankLitres != null) { "$catalogId.fuelTankLitres: ${powertrain.kind} requires a tank" }
            require(!battery.hasDeclaredCapacity()) { "$catalogId.battery: ${powertrain.kind} must not declare a traction battery" }
            require(powertrain.hybridSystem in setOf(RuntimeHybridSystem.MILD, RuntimeHybridSystem.FULL)) {
                "$catalogId.powertrain.hybridSystem: HEV requires MILD or FULL"
            }
        }
        RuntimePowertrainKind.ICE -> {
            require(fuelTankLitres != null) { "$catalogId.fuelTankLitres: ICE requires a tank" }
            require(!battery.hasDeclaredCapacity()) { "$catalogId.battery: ICE must not declare a traction battery" }
            require(powertrain.primaryFuel != null) { "$catalogId.powertrain.primaryFuel: ICE requires fuel" }
            require(powertrain.hybridSystem == RuntimeHybridSystem.NONE) {
                "$catalogId.powertrain.hybridSystem: ICE requires NONE"
            }
        }
    }
}

private fun RuntimeLegacyKey.toStableKey(): String = "$category|$brand|$model|$year"

private fun JsonObject.requiredIdentity(name: String, context: String): RuntimeIdentity {
    val obj = requiredObject(name, context)
    return RuntimeIdentity(obj.requiredId("id", "$context.$name"), obj.requiredString("displayName", "$context.$name"))
}

private fun JsonObject.optionalIdentity(name: String, context: String): RuntimeIdentity? =
    nullableElement(name, context)?.let { requiredIdentity(name, context) }

private fun JsonObject.requiredObject(name: String, context: String): JsonObject {
    val value = requiredElement(name, context)
    require(value is JsonObject) { "$context.$name: expected object" }
    return value
}

private fun JsonObject.requiredArray(name: String, context: String): JsonArray {
    val value = requiredElement(name, context)
    require(value is JsonArray) { "$context.$name: expected array" }
    return value
}

private fun JsonObject.requiredString(name: String, context: String = "catalog"): String {
    val value = requiredPrimitive(name, context)
    val content = value.contentOrNull
    require(value.isString && !content.isNullOrBlank()) { "$context.$name: expected non-empty string" }
    return content
}

private fun JsonObject.requiredId(name: String, context: String): String =
    requiredString(name, context).also { require(runtimeIdRegex.matches(it)) { "$context.$name: invalid id '$it'" } }

private fun JsonObject.requiredInt(name: String, context: String = "catalog"): Int =
    requiredPrimitive(name, context).intOrNull ?: error("$context.$name: expected integer")

private fun JsonObject.optionalInt(name: String, context: String): Int? =
    nullableElement(name, context)?.let { requiredInt(name, context) }

private fun JsonObject.optionalPositiveDouble(name: String, context: String): Double? {
    if (nullableElement(name, context) == null) return null
    val value = requiredPrimitive(name, context).doubleOrNull
    require(value != null && value.isFinite() && value > 0.0) {
        "$context.$name: must be a finite number greater than zero"
    }
    return value
}

private inline fun <reified T : Enum<T>> JsonObject.requiredEnum(name: String, context: String): T =
    enumValueOfOrError(requiredString(name, context), "$context.$name")

private inline fun <reified T : Enum<T>> JsonObject.optionalEnum(name: String, context: String): T? =
    nullableElement(name, context)?.let { requiredEnum(name, context) }

private inline fun <reified T : Enum<T>> enumValueOfOrError(value: String, context: String): T =
    try { enumValueOf(value) } catch (_: IllegalArgumentException) { error("$context: unknown enum '$value'") }

private fun JsonObject.requiredElement(name: String, context: String): JsonElement {
    val value = this[name]
    require(value != null && value !is JsonNull) { "$context.$name: required value is missing" }
    return value
}

private fun JsonObject.nullableElement(name: String, context: String): JsonElement? {
    require(containsKey(name)) { "$context.$name: required nullable value is missing" }
    return this[name]?.takeUnless { it is JsonNull }
}

private fun JsonObject.requiredPrimitive(name: String, context: String): JsonPrimitive {
    val value = requiredElement(name, context)
    require(value is JsonPrimitive) { "$context.$name: expected primitive" }
    return value
}

private inline fun <T> JsonArray.mapObjects(
    context: String,
    transform: (Int, JsonObject) -> T
): List<T> = mapIndexed { index, value ->
    require(value is JsonObject) { "$context[$index]: expected object" }
    transform(index, value)
}
