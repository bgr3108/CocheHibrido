package com.bgr3108.kilonom.domain

import com.bgr3108.kilonom.data.FuelEntry
import com.bgr3108.kilonom.data.FuelType
import com.bgr3108.kilonom.data.isSupportedFuelLevelAfter

fun calculateAverageFuelConsumption(entries: List<FuelEntry>): Double {
    return calculateAverageFuelConsumptionFromSegments(calculateFuelSegments(entries))
}

fun calculateAverageFuelConsumptionFromSegments(
    segments: List<FuelConsumptionSegment>
): Double {

    var totalFuel = 0.0
    var totalDistance = 0.0

    segments.forEach { segment ->
        val nextFuel = totalFuel + segment.fuelUsed
        val nextDistance = totalDistance + segment.distance

        if (nextFuel.isFinite() && nextDistance.isFinite()) {
            totalFuel = nextFuel
            totalDistance = nextDistance
        }
    }

    return if (totalDistance > 0.0) {
        ((totalFuel / totalDistance) * 100.0)
            .takeIf { it.isFinite() } ?: 0.0
    } else {
        0.0
    }
}

fun calculateAverageElectricConsumption(
    entries: List<FuelEntry>
): Double {

    val segments = calculateElectricSegments(entries)

    var totalEnergy = 0.0
    var totalDistance = 0.0

    segments.forEach { segment ->
        val nextEnergy = totalEnergy + segment.energyUsed
        val nextDistance = totalDistance + segment.distance

        if (nextEnergy.isFinite() && nextDistance.isFinite()) {
            totalEnergy = nextEnergy
            totalDistance = nextDistance
        }
    }

    return if (totalDistance > 0.0) {
        ((totalEnergy / totalDistance) * 100.0)
            .takeIf { it.isFinite() } ?: 0.0
    } else {
        0.0
    }
}
fun calculateFuelSegments(
    entries: List<FuelEntry>
): List<FuelConsumptionSegment> {

    val fuelEntries = entries
        .filter {
            it.tipo == FuelType.GASOLINA &&
                    it.km.isFinite() &&
                    it.km >= 0.0 &&
                    it.cantidad.isFinite() &&
                    it.cantidad > 0.0
        }
        .sortedWith(
            compareBy<FuelEntry> { it.km }
                .thenBy { it.fecha }
                .thenBy { it.id }
        )

    val result = mutableListOf<FuelConsumptionSegment>()

    var ultimoLleno: FuelEntry? = null
    var litrosAcumulados = 0.0

    fuelEntries.forEach { entry ->

        if (ultimoLleno == null) {

            if (entry.fullTank) {
                ultimoLleno = entry
            }

        } else {

            val nextLitrosAcumulados = litrosAcumulados + entry.cantidad
            litrosAcumulados =
                if (nextLitrosAcumulados.isFinite()) {
                    nextLitrosAcumulados
                } else {
                    Double.NaN
                }

            if (entry.fullTank) {

                val distancia =
                    entry.km - ultimoLleno.km

                val consumo =
                    if (
                        distancia.isFinite() &&
                        distancia > 0.0 &&
                        litrosAcumulados.isFinite() &&
                        litrosAcumulados > 0.0
                    ) {
                        (litrosAcumulados / distancia) * 100.0
                    } else {
                        Double.NaN
                    }

                if (consumo.isFinite()) {

                    result += FuelConsumptionSegment(

                        startKm = ultimoLleno.km,

                        endKm = entry.km,

                        distance = distancia,

                        fuelUsed = litrosAcumulados,

                        consumption = consumo

                    )
                }

                // Un lleno a igual kilometraje no tiene distancia calculable. Aun así,
                // se convierte en la nueva referencia y descarta ese cierre para que sus
                // litros no se arrastren al siguiente tramo con distancia positiva.
                ultimoLleno = entry
                litrosAcumulados = 0.0
            }
        }
    }

    return result
}

/**
 * Estimates consumption accumulated since the latest confirmed full tank. Its selected tank
 * level is approximate, so this result is intentionally separate from full-to-full history.
 */
fun calculateCurrentEstimatedFuelConsumption(
    entries: List<FuelEntry>,
    tankCapacity: Double
): CurrentFuelConsumptionEstimate? {
    if (!tankCapacity.isFinite() || tankCapacity <= 0.0) return null

    val fuelEntries = entries
        .filter {
            it.tipo == FuelType.GASOLINA &&
                it.km.isFinite() &&
                it.km >= 0.0 &&
                it.cantidad.isFinite() &&
                it.cantidad > 0.0
        }
        .sortedWith(compareBy<FuelEntry> { it.km }.thenBy { it.fecha }.thenBy { it.id })

    val lastFullIndex = fuelEntries.indexOfLast { it.fullTank }
    if (lastFullIndex < 0 || lastFullIndex == fuelEntries.lastIndex) return null

    val lastFull = fuelEntries[lastFullIndex]
    val partialEntries = fuelEntries.drop(lastFullIndex + 1)
    val latestPartial = partialEntries.last()
    val latestLevel = latestPartial.fuelLevelAfter

    if (
        latestPartial.fullTank ||
        latestLevel == null ||
        !latestLevel.isFinite() ||
        !isSupportedFuelLevelAfter(latestLevel)
    ) return null

    val distance = latestPartial.km - lastFull.km
    val addedFuel = partialEntries.sumOf { it.cantidad }
    val fuelAfterLatestPartial = tankCapacity * latestLevel
    val fuelBeforeLatestPartial = fuelAfterLatestPartial - latestPartial.cantidad
    val fuelConsumed = tankCapacity + addedFuel - fuelAfterLatestPartial

    if (
        !distance.isFinite() || distance <= 0.0 ||
        !addedFuel.isFinite() || addedFuel <= 0.0 ||
        !fuelAfterLatestPartial.isFinite() ||
        !fuelBeforeLatestPartial.isFinite() || fuelBeforeLatestPartial !in 0.0..tankCapacity ||
        !fuelConsumed.isFinite() || fuelConsumed <= 0.0
    ) return null

    val consumption = (fuelConsumed / distance) * 100.0
    if (!consumption.isFinite() || consumption <= 0.0) return null

    return CurrentFuelConsumptionEstimate(
        startKm = lastFull.km,
        endKm = latestPartial.km,
        distance = distance,
        fuelUsed = fuelConsumed,
        consumption = consumption
    )
}
fun calculateBestFuelConsumption(
    entries: List<FuelEntry>
): Double {

    return calculateFuelSegments(entries)
        .minOfOrNull { it.consumption } ?: 0.0
}

fun calculateBestFuelConsumptionFromSegments(
    segments: List<FuelConsumptionSegment>
): Double = segments.minOfOrNull { it.consumption } ?: 0.0

fun calculateWorstFuelConsumption(
    entries: List<FuelEntry>
): Double {

    return calculateFuelSegments(entries)
        .maxOfOrNull { it.consumption } ?: 0.0
}

fun calculateWorstFuelConsumptionFromSegments(
    segments: List<FuelConsumptionSegment>
): Double = segments.maxOfOrNull { it.consumption } ?: 0.0

fun calculateFuelSegmentCount(
    entries: List<FuelEntry>
): Int {

    return calculateFuelSegments(entries).size
}

fun calculateFuelSegmentCountFromSegments(
    segments: List<FuelConsumptionSegment>
): Int = segments.size
data class ElectricConsumptionSegment(

    val startKm: Double,

    val endKm: Double,

    val distance: Double,

    val energyUsed: Double,

    val consumption: Double

)
fun calculateElectricSegments(
    entries: List<FuelEntry>
): List<ElectricConsumptionSegment> {

    val electricEntries = entries
        .filter {
            it.tipo == FuelType.ELECTRICO &&
                    it.km.isFinite() &&
                    it.km >= 0.0 &&
                    it.cantidad.isFinite() &&
                    it.cantidad > 0.0
        }
        .sortedWith(
            compareBy<FuelEntry> { it.km }
                .thenBy { it.fecha }
                .thenBy { it.id }
        )

    if (electricEntries.size < 2) {
        return emptyList()
    }

    val result = mutableListOf<ElectricConsumptionSegment>()

    for (i in 1 until electricEntries.size) {

        val previous = electricEntries[i - 1]
        val current = electricEntries[i]

        val distance = current.km - previous.km

        if (distance <= 0.0 || !distance.isFinite()) {
            continue
        }

        val consumption = (current.cantidad / distance) * 100.0

        if (consumption.isFinite()) {

            result += ElectricConsumptionSegment(

                startKm = previous.km,

                endKm = current.km,

                distance = distance,

                energyUsed = current.cantidad,

                consumption = consumption

            )
        }
    }

    return result
}
fun calculateBestElectricConsumption(
    entries: List<FuelEntry>
): Double {

    return calculateElectricSegments(entries)
        .minOfOrNull { it.consumption } ?: 0.0
}
fun calculateWorstElectricConsumption(
    entries: List<FuelEntry>
): Double {

    return calculateElectricSegments(entries)
        .maxOfOrNull { it.consumption } ?: 0.0
}
fun calculateElectricSegmentCount(
    entries: List<FuelEntry>
): Int {

    return calculateElectricSegments(entries).size
}
