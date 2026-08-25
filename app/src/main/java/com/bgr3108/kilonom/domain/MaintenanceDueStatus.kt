package com.bgr3108.kilonom.domain

import com.bgr3108.kilonom.data.MaintenanceItemEntity

enum class MaintenanceDueStatus {
    OVERDUE,
    DUE_SOON,
    UP_TO_DATE,
    NO_DUE_CONFIGURED
}

object MaintenanceReminderPolicy {
    const val DUE_SOON_KILOMETERS: Long = 1_000
    const val DUE_SOON_DAYS: Long = 30
}

fun calculateMaintenanceDueStatus(
    item: MaintenanceItemEntity,
    currentKm: Long,
    today: Long,
    daysBetween: (from: Long, to: Long) -> Long
): MaintenanceDueStatus {
    val dueKm = item.nextDueKm
    val dueDate = item.nextDueDate
    if (dueKm == null && dueDate == null) return MaintenanceDueStatus.NO_DUE_CONFIGURED

    val overdueByKm = dueKm != null && currentKm > dueKm
    val overdueByDate = dueDate != null && today > dueDate
    if (overdueByKm || overdueByDate) return MaintenanceDueStatus.OVERDUE

    // The overdue branch above guarantees non-negative remaining values here, including zero
    // for "toca ahora" and "toca hoy".
    val dueSoonByKm = dueKm?.minus(currentKm)?.let { remainingKm ->
        remainingKm <= MaintenanceReminderPolicy.DUE_SOON_KILOMETERS
    } ?: false
    val dueSoonByDate = dueDate?.let { due ->
        daysBetween(today, due) <= MaintenanceReminderPolicy.DUE_SOON_DAYS
    } ?: false
    return if (dueSoonByKm || dueSoonByDate) {
        MaintenanceDueStatus.DUE_SOON
    } else {
        MaintenanceDueStatus.UP_TO_DATE
    }
}
