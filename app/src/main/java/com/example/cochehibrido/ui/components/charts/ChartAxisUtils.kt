package com.example.cochehibrido.ui.components.charts

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

fun calculateAxisStep(
    min: Double,
    max: Double,
    targetTicks: Int = 5
): Double {

    val range = max - min

    if (range <= 0.0) return 1.0

    val rawStep = range / targetTicks

    val magnitude = 10.0.pow(floor(log10(rawStep)))

    val normalized = rawStep / magnitude

    val niceNormalized = when {
        normalized <= 1 -> 1.0
        normalized <= 2 -> 2.0
        normalized <= 5 -> 5.0
        else -> 10.0
    }

    return niceNormalized * magnitude
}

fun calculateAxisTicks(
    min: Double,
    max: Double,
    step: Double
): List<Double> {

    val firstTick = floor(min / step) * step
    val lastTick = ceil(max / step) * step

    val ticks = mutableListOf<Double>()

    var current = firstTick

    while (current <= lastTick + 0.0001) {
        ticks.add(current)
        current += step
    }

    return ticks
}
fun buildAxis(
    min: Double,
    max: Double,
    targetTicks: Int = 5
): Axis {

    val step = calculateAxisStep(min, max, targetTicks)

    val ticks = calculateAxisTicks(min, max, step)

    return Axis(
        min = ticks.first(),
        max = ticks.last(),
        step = step,
        ticks = ticks
    )
}