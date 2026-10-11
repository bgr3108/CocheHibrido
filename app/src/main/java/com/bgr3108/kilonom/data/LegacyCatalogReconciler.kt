package com.bgr3108.kilonom.data

import java.text.Normalizer
import java.util.Locale

/**
 * Result of an opt-in attempt to attach an old personal snapshot to catalog provenance.
 *
 * The resolver is deliberately conservative: a legacy key alone is not enough.  The personal
 * snapshot must still agree with the catalog data that Kilonom uses for calculations.
 */
sealed interface LegacyCatalogReconciliation {
    data class ExactMatch(val catalogId: String) : LegacyCatalogReconciliation
    data object Conflict : LegacyCatalogReconciliation
    data object NoMatch : LegacyCatalogReconciliation
}

internal fun RuntimeVehicleCatalog.reconcileLegacyVehicle(
    vehicle: VehicleEntity
): LegacyCatalogReconciliation {
    if (vehicle.catalogId != null || vehicle.year == null || vehicle.type == null) {
        return LegacyCatalogReconciliation.NoMatch
    }

    val matchingLegacyKey = RuntimeLegacyKey(
        category = vehicle.category,
        brand = vehicle.brand,
        model = vehicle.model,
        year = vehicle.year
    )
    val candidates = vehicles.filter { runtimeVehicle ->
        runtimeVehicle.legacyKeys.any { it.normalizedKey() == matchingLegacyKey.normalizedKey() }
    }
    if (candidates.isEmpty()) return LegacyCatalogReconciliation.NoMatch

    val compatibleIds = candidates
        .filter { it.isFunctionallyCompatibleWith(vehicle) }
        .map { it.catalogId }
        .distinct()

    return when (compatibleIds.size) {
        1 -> LegacyCatalogReconciliation.ExactMatch(compatibleIds.single())
        else -> LegacyCatalogReconciliation.Conflict
    }
}

private fun RuntimeVehicle.isFunctionallyCompatibleWith(vehicle: VehicleEntity): Boolean {
    if (powertrain.toVehicleType(catalogId) != vehicle.type) return false
    if (fuelTankLitres.orZero() != vehicle.fuelTankCapacity) return false

    // A gross or unclassified capacity is intentionally not an operational value.  It therefore
    // cannot safely be compared with the historic snapshot's single battery field.
    return battery.usableKwh == null || battery.usableKwh == vehicle.batteryCapacity
}

private fun Double?.orZero(): Double = this ?: 0.0

private fun RuntimeLegacyKey.normalizedKey(): String = listOf(
    category.name,
    brand.normalizeLegacyText(),
    model.normalizeLegacyText(),
    year.toString()
).joinToString("|")

private fun String.normalizeLegacyText(): String = Normalizer.normalize(this, Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "")
    .trim()
    .replace(Regex("\\s+"), " ")
    .lowercase(Locale.ROOT)

/** Small editorial-only graph model, shared by validator tests without leaking it into runtime. */
internal data class EditorialCatalogTraceRecord(
    val catalogId: String,
    val active: Boolean,
    val replacedByCatalogId: String?,
    val legacyKeys: List<RuntimeLegacyKey>
)

internal sealed interface EditorialCatalogTraceResolution {
    data class Active(val catalogId: String) : EditorialCatalogTraceResolution
    data object BrokenReference : EditorialCatalogTraceResolution
    data object Cycle : EditorialCatalogTraceResolution
}

/** Follows an inactive editorial record until its unique active replacement. */
internal fun resolveEditorialCanonicalCatalogId(
    startCatalogId: String,
    recordsByCatalogId: Map<String, EditorialCatalogTraceRecord>
): EditorialCatalogTraceResolution {
    val visited = mutableSetOf<String>()
    var currentId = startCatalogId
    while (true) {
        if (!visited.add(currentId)) return EditorialCatalogTraceResolution.Cycle
        val current = recordsByCatalogId[currentId] ?: return EditorialCatalogTraceResolution.BrokenReference
        if (current.active) return EditorialCatalogTraceResolution.Active(current.catalogId)
        currentId = current.replacedByCatalogId ?: return EditorialCatalogTraceResolution.BrokenReference
    }
}
