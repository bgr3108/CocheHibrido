package com.bgr3108.kilonom.domain

import com.bgr3108.kilonom.data.MaintenanceItemEntity
import com.bgr3108.kilonom.data.DEFAULT_REMINDER_LEAD_DAYS
import com.bgr3108.kilonom.data.DEFAULT_REMINDER_LEAD_KM

enum class MaintenanceDueStatus {
    OVERDUE,
    DUE_SOON,
    UP_TO_DATE,
    NO_DUE_CONFIGURED
}

object MaintenanceReminderPolicy {
    const val DEFAULT_DUE_SOON_KILOMETERS: Long = DEFAULT_REMINDER_LEAD_KM
    const val DEFAULT_DUE_SOON_DAYS: Long = DEFAULT_REMINDER_LEAD_DAYS
}

fun calculateMaintenanceDueStatus(
    item: MaintenanceItemEntity,
    currentKm: Long,
    today: Long,
    daysBetween: (from: Long, to: Long) -> Long
): MaintenanceDueStatus {
    val dueKm = item.nextDueKm
    val dueDate = item.nextDueDate
    val leadKm = item.reminderLeadKm
    val leadDays = item.reminderLeadDays
    require(leadKm >= 0L) { "El aviso previo por kilometraje no es válido" }
    require(leadDays >= 0L) { "El aviso previo por fecha no es válido" }
    if (dueKm == null && dueDate == null) return MaintenanceDueStatus.NO_DUE_CONFIGURED

    val overdueByKm = dueKm != null && currentKm > dueKm
    val overdueByDate = dueDate != null && today > dueDate
    if (overdueByKm || overdueByDate) return MaintenanceDueStatus.OVERDUE

    // The overdue branch above guarantees non-negative remaining values here, including zero
    // for "toca ahora" and "toca hoy".
    val dueSoonByKm = dueKm?.minus(currentKm)?.let { remainingKm ->
        remainingKm <= leadKm
    } ?: false
    val dueSoonByDate = dueDate?.let { due ->
        daysBetween(today, due) <= leadDays
    } ?: false
    return if (dueSoonByKm || dueSoonByDate) {
        MaintenanceDueStatus.DUE_SOON
    } else {
        MaintenanceDueStatus.UP_TO_DATE
    }
}
