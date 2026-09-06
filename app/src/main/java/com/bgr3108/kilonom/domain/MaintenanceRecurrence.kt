package com.bgr3108.kilonom.domain

import com.bgr3108.kilonom.data.MaintenanceTimeUnit
import java.time.Instant
import java.time.ZoneId

/** Concrete next limits derived from one completed maintenance event and its recurrence rule. */
data class MaintenanceNextDue(
    val nextDueKm: Long?,
    val nextDueDate: Long?
)

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
    require(intervalKm == null || intervalKm > 0L) { "El intervalo de kilometraje debe ser positivo" }
    require((intervalTimeValue == null) == (intervalTimeUnit == null)) {
        "El intervalo temporal requiere valor y unidad"
    }
    require(intervalTimeValue == null || intervalTimeValue > 0) {
        "El intervalo temporal debe ser positivo"
    }

    val dueKm = if (performedKm != null && intervalKm != null) performedKm + intervalKm else null
    val dueDate = if (performedDate != null && intervalTimeValue != null && intervalTimeUnit != null) {
        val performedLocalDate = Instant.ofEpochMilli(performedDate).atZone(zoneId).toLocalDate()
        val next = when (intervalTimeUnit) {
            MaintenanceTimeUnit.DAYS -> performedLocalDate.plusDays(intervalTimeValue.toLong())
            MaintenanceTimeUnit.MONTHS -> performedLocalDate.plusMonths(intervalTimeValue.toLong())
            MaintenanceTimeUnit.YEARS -> performedLocalDate.plusYears(intervalTimeValue.toLong())
        }
        next.atStartOfDay(zoneId).toInstant().toEpochMilli()
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
