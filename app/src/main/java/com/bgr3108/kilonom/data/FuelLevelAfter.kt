package com.bgr3108.kilonom.data

/** The nine fixed fuel-level positions exposed by the fuel-entry form. */
val fuelLevelAfterSteps = listOf(
    0.0,
    0.125,
    0.25,
    0.375,
    0.5,
    0.625,
    0.75,
    0.875,
    1.0
)

fun isSupportedFuelLevelAfter(level: Double): Boolean =
    fuelLevelAfterSteps.any { it == level }

fun initialFuelLevelAfter(entry: FuelEntry?): Double? = when {
    // New fuel entries keep the previous default behaviour: a refuel is full unless the
    // user explicitly selects a lower level.
    entry == null -> 1.0
    entry.fuelLevelAfter != null && isSupportedFuelLevelAfter(entry.fuelLevelAfter) -> entry.fuelLevelAfter
    // A legacy entry may only have the boolean flag. Do not synthesize a new percentage
    // while editing it: opening and saving without changes must preserve the stored record.
    else -> null
}

fun isFullTankLevel(level: Double?): Boolean = level == 1.0

fun fuelLevelAfterPercentageText(level: Double): String =
    if (level * 100.0 % 1.0 == 0.0) {
        "${(level * 100.0).toInt()} %"
    } else {
        "${(level * 100.0).toString().replace('.', ',')} %"
    }
