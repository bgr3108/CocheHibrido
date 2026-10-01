package com.bgr3108.kilonom.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigDecimal

enum class FuelType {
    GASOLINA,
    ELECTRICO
}

@Entity(
    tableName = "fuel_entries",
    foreignKeys = [
        ForeignKey(
            entity = VehicleEntity::class,
            parentColumns = ["id"],
            childColumns = ["vehicleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["vehicleId", "fecha"])]
)
data class FuelEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    val fecha: Long,

    val cantidad: Double,
    val precio: Double,

    val tipo: FuelType,

    val km: Double,

    val fullTank: Boolean = true,

    /** Fraction of the tank estimated after a fuel entry, or null for historical/unknown data. */
    val fuelLevelAfter: Double? = null,

    /** Battery percentages recorded for an electric charge, or null for historical/unknown data. */
    val electricChargeStartPercentage: Double? = null,
    val electricChargeEndPercentage: Double? = null,

    val vehicleId: Long
)

fun electricChargePercentageRangeText(
    startPercentage: Double?,
    endPercentage: Double?
): String? = if (
    startPercentage != null &&
    endPercentage != null &&
    startPercentage.isFinite() &&
    endPercentage.isFinite() &&
    startPercentage in 0.0..100.0 &&
    endPercentage in startPercentage..100.0
) {
    "${startPercentage.toPercentageInputText()} % → ${endPercentage.toPercentageInputText()} %"
} else {
    null
}

/**
 * Compact secondary text for an electric charge when both stored battery percentages are valid.
 * Historical entries without complete percentages deliberately have no presentation.
 */
fun electricChargePercentageSummaryText(
    startPercentage: Double?,
    endPercentage: Double?
): String? {
    val range = electricChargePercentageRangeText(startPercentage, endPercentage) ?: return null
    val variation = requireNotNull(endPercentage) - requireNotNull(startPercentage)
    return "$range (+${variation.toPercentageInputText()} %)"
}

/** Identifies which input last defined the amount of an electric charge in the form. */
enum class ElectricChargeAmountOrigin {
    STORED,
    MANUAL,
    PERCENTAGES
}

/**
 * Resolves the amount without ever recalculating a value merely because an existing entry was
 * opened. Percentages only take precedence after the user has edited one of them.
 */
fun resolveElectricChargeAmount(
    amount: Double?,
    origin: ElectricChargeAmountOrigin,
    batteryCapacity: Double,
    startPercentage: Double?,
    endPercentage: Double?
): Double? = when (origin) {
    ElectricChargeAmountOrigin.PERCENTAGES -> {
        if (
            batteryCapacity.isFinite() && batteryCapacity > 0.0 &&
            startPercentage != null && endPercentage != null &&
            startPercentage.isFinite() && endPercentage.isFinite() &&
            startPercentage in 0.0..100.0 && endPercentage in startPercentage..100.0
        ) {
            batteryCapacity * (endPercentage - startPercentage) / 100.0
        } else {
            null
        }
    }

    ElectricChargeAmountOrigin.STORED,
    ElectricChargeAmountOrigin.MANUAL -> amount
}

fun Double?.toPercentageInputText(): String = this
    ?.takeIf(Double::isFinite)
    ?.let { BigDecimal.valueOf(it).stripTrailingZeros().toPlainString().replace('.', ',') }
    .orEmpty()
