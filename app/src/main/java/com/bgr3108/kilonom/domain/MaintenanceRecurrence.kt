package com.bgr3108.kilonom.domain

import com.bgr3108.kilonom.data.MaintenanceTimeUnit
import java.time.Instant
import java.time.DateTimeException
import java.time.ZoneId

/** Concrete next limits derived from one completed maintenance event and its recurrence rule. */
data class MaintenanceNextDue(
    val nextDueKm: Long?,
    val nextDueDate: Long?
)

/** Invalid persisted/user recurrence data, distinct from unexpected programming failures. */
class MaintenanceRecurrenceException(message: String) : IllegalArgumentException(message)

/** Explicit choice for a completed maintenance: calculate, manually replace, or clear reminders. */
sealed interface MaintenanceNextDueUpdate {
    data object AutomaticFromInterval : MaintenanceNextDueUpdate
    data class Manual(val nextDueKm: Long?, val nextDueDate: Long?) : MaintenanceNextDueUpdate
    data object Clear : MaintenanceNextDueUpdate
}

/**
 * Calculates only limits for which a trustworthy base reading exists. Dates use LocalDate calendar
 * semantics, so leap years and varying month lengths are delegated to java.time.
 */
fun calculateNextMaintenanceDue(
    performedDate: Long?,
    performedKm: Long?,
    intervalKm: Long?,
    intervalTimeValue: Int?,
    intervalTimeUnit: MaintenanceTimeUnit?,
    zoneId: ZoneId = ZoneId.systemDefault()
): MaintenanceNextDue {
    if (intervalKm != null && intervalKm <= 0L) {
        throw MaintenanceRecurrenceException("El intervalo de kilometraje debe ser positivo.")
    }
    if ((intervalTimeValue == null) != (intervalTimeUnit == null) || intervalTimeValue != null && intervalTimeValue <= 0) {
        throw MaintenanceRecurrenceException("Introduce un intervalo de tiempo y una unidad válidos.")
    }
    if (performedKm != null && performedKm < 0L) {
        throw MaintenanceRecurrenceException("El kilometraje realizado no es válido.")
    }

    val dueKm = if (performedKm != null && intervalKm != null) {
        try {
            Math.addExact(performedKm, intervalKm)
        } catch (_: ArithmeticException) {
            throw MaintenanceRecurrenceException("El intervalo genera un kilometraje fuera del rango admitido.")
        }
    } else null
    val dueDate = if (performedDate != null && intervalTimeValue != null && intervalTimeUnit != null) {
        try {
            val performedLocalDate = Instant.ofEpochMilli(performedDate).atZone(zoneId).toLocalDate()
            val next = when (intervalTimeUnit) {
                MaintenanceTimeUnit.DAYS -> performedLocalDate.plusDays(intervalTimeValue.toLong())
                MaintenanceTimeUnit.MONTHS -> performedLocalDate.plusMonths(intervalTimeValue.toLong())
                MaintenanceTimeUnit.YEARS -> performedLocalDate.plusYears(intervalTimeValue.toLong())
            }
            next.atStartOfDay(zoneId).toInstant().toEpochMilli()
        } catch (_: DateTimeException) {
            throw MaintenanceRecurrenceException("El intervalo genera una fecha fuera del rango admitido.")
        } catch (_: ArithmeticException) {
            throw MaintenanceRecurrenceException("El intervalo genera una fecha fuera del rango admitido.")
        }
    } else {
        null
    }
    return MaintenanceNextDue(dueKm, dueDate)
}

/** Resolves the explicit choice made when a completed record starts its next cycle. */
fun resolveNextMaintenanceDueUpdate(
    update: MaintenanceNextDueUpdate,
    performedDate: Long?,
    performedKm: Long?,
    intervalKm: Long?,
    intervalTimeValue: Int?,
    intervalTimeUnit: MaintenanceTimeUnit?,
    zoneId: ZoneId = ZoneId.systemDefault()
): MaintenanceNextDue = when (update) {
    MaintenanceNextDueUpdate.AutomaticFromInterval -> calculateNextMaintenanceDue(
        performedDate = performedDate,
        performedKm = performedKm,
        intervalKm = intervalKm,
        intervalTimeValue = intervalTimeValue,
        intervalTimeUnit = intervalTimeUnit,
        zoneId = zoneId
    )

    is MaintenanceNextDueUpdate.Manual -> MaintenanceNextDue(update.nextDueKm, update.nextDueDate)
    MaintenanceNextDueUpdate.Clear -> MaintenanceNextDue(null, null)
}
