package com.example.cochehibrido.domain

import com.example.cochehibrido.data.FuelEntry
import com.example.cochehibrido.data.FuelType

fun calculateAverageFuelConsumption(entries: List<FuelEntry>): Double {

    val fuelEntries = entries
        .filter { it.tipo == FuelType.GASOLINA }
        .sortedBy { it.km }

    var litrosConsumidos = 0.0
    var kmRecorridos = 0.0

    var ultimoLleno: FuelEntry? = null
    var litrosAcumulados = 0.0

    fuelEntries.forEach { entry ->

        if (ultimoLleno == null) {

            if (entry.fullTank) {
                ultimoLleno = entry
            }

        } else {

            litrosAcumulados += entry.cantidad

            if (entry.fullTank) {

                kmRecorridos += entry.km - ultimoLleno!!.km
                litrosConsumidos += litrosAcumulados

                ultimoLleno = entry
                litrosAcumulados = 0.0
            }
        }
    }

    return if (kmRecorridos > 0)
        litrosConsumidos / kmRecorridos * 100
    else
        0.0
}

fun calculateAverageElectricConsumption(
    entries: List<FuelEntry>
): Double {

    val electricEntries = entries
        .filter { it.tipo == FuelType.ELECTRICO }
        .sortedBy { it.km }

    if (electricEntries.size < 2)
        return 0.0

    val kmRecorridos =
        electricEntries.last().km -
                electricEntries.first().km

    val kwhConsumidos =
        electricEntries.sumOf { it.cantidad }

    return if (kmRecorridos > 0)
        kwhConsumidos / kmRecorridos * 100
    else
        0.0
}
fun calculateFuelSegments(
    entries: List<FuelEntry>
): List<FuelConsumptionSegment> {

    val fuelEntries = entries
        .filter { it.tipo == FuelType.GASOLINA }
        .sortedBy { it.km }

    val result = mutableListOf<FuelConsumptionSegment>()

    var ultimoLleno: FuelEntry? = null
    var litrosAcumulados = 0.0

    fuelEntries.forEach { entry ->

        if (ultimoLleno == null) {

            if (entry.fullTank) {
                ultimoLleno = entry
            }

        } else {

            litrosAcumulados += entry.cantidad

            if (entry.fullTank) {

                val distancia =
                    entry.km - ultimoLleno!!.km

                if (distancia > 0) {

                    result += FuelConsumptionSegment(

                        startKm = ultimoLleno!!.km,

                        endKm = entry.km,

                        distance = distancia,

                        fuelUsed = litrosAcumulados,

                        consumption =
                            litrosAcumulados /
                                    distancia *
                                    100

                    )
                }

                ultimoLleno = entry
                litrosAcumulados = 0.0
            }
        }
    }

    return result
}
fun calculateBestFuelConsumption(
    entries: List<FuelEntry>
): Double {

    return calculateFuelSegments(entries)
        .minOfOrNull { it.consumption } ?: 0.0
}

fun calculateWorstFuelConsumption(
    entries: List<FuelEntry>
): Double {

    return calculateFuelSegments(entries)
        .maxOfOrNull { it.consumption } ?: 0.0
}

fun calculateFuelSegmentCount(
    entries: List<FuelEntry>
): Int {

    return calculateFuelSegments(entries).size
}
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
        .filter { it.tipo == FuelType.ELECTRICO }
        .sortedBy { it.km }

    if (electricEntries.size < 2) {
        return emptyList()
    }

    val result = mutableListOf<ElectricConsumptionSegment>()

    for (i in 1 until electricEntries.size) {

        val previous = electricEntries[i - 1]
        val current = electricEntries[i]

        val distance = current.km - previous.km

        if (distance > 0) {

            result += ElectricConsumptionSegment(

                startKm = previous.km,

                endKm = current.km,

                distance = distance,

                energyUsed = current.cantidad,

                consumption = current.cantidad / distance * 100

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