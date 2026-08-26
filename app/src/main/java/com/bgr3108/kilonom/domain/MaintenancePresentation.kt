package com.bgr3108.kilonom.domain

import com.bgr3108.kilonom.data.MaintenanceItemEntity
import com.bgr3108.kilonom.data.MaintenanceType
import com.bgr3108.kilonom.data.TyrePosition

/** Presentation-neutral result prepared outside Compose for one maintenance reminder. */
data class MaintenanceDueInfo(
    val status: MaintenanceDueStatus,
    val dueKm: Long?,
    val dueDate: Long?,
    val remainingKm: Long?,
    val remainingDays: Long?
)

fun createMaintenanceDueInfo(
    item: MaintenanceItemEntity,
    currentKm: Long,
    today: Long,
    daysBetween: (Long, Long) -> Long
): MaintenanceDueInfo = MaintenanceDueInfo(
    status = calculateMaintenanceDueStatus(item, currentKm, today, daysBetween),
    dueKm = item.nextDueKm,
    dueDate = item.nextDueDate,
    remainingKm = item.nextDueKm?.minus(currentKm),
    remainingDays = item.nextDueDate?.let { daysBetween(today, it) }
)

fun MaintenanceItemEntity.displayMaintenanceName(): String = when (type) {
    MaintenanceType.OIL_AND_FILTER -> "Aceite y filtro"
    MaintenanceType.BRAKES -> "Frenos"
    MaintenanceType.TYRES -> when (tyrePosition) {
        TyrePosition.ALL -> "Neumáticos"
        TyrePosition.FRONT -> "Neumáticos delanteros"
        TyrePosition.REAR -> "Neumáticos traseros"
        null -> "Neumáticos"
    }
    MaintenanceType.BATTERY_12V -> "Batería 12 V"
    MaintenanceType.GENERAL_SERVICE -> "Revisión general"
    MaintenanceType.CHAIN_AND_DRIVETRAIN -> "Cadena / transmisión"
    MaintenanceType.ITV -> "ITV"
    MaintenanceType.INSURANCE -> "Seguro"
    MaintenanceType.CIRCULATION_TAX -> "Impuesto de circulación"
    MaintenanceType.OTHER -> customName.orEmpty()
}

fun MaintenanceType.isDocumentMaintenance(): Boolean = this in setOf(
    MaintenanceType.ITV,
    MaintenanceType.INSURANCE,
    MaintenanceType.CIRCULATION_TAX
)

/** Stable urgency ranking: overdue, due soon and then up-to-date reminders. */
fun maintenanceUrgencySortValue(info: MaintenanceDueInfo): Pair<Int, Double> {
    val normalizedRemaining = listOfNotNull(
        info.remainingKm?.toDouble()?.div(MaintenanceReminderPolicy.DUE_SOON_KILOMETERS),
        info.remainingDays?.toDouble()?.div(MaintenanceReminderPolicy.DUE_SOON_DAYS)
    ).minOrNull() ?: Double.MAX_VALUE
    val group = when (info.status) {
        MaintenanceDueStatus.OVERDUE -> 0
        MaintenanceDueStatus.DUE_SOON -> 1
        MaintenanceDueStatus.UP_TO_DATE -> 2
        MaintenanceDueStatus.NO_DUE_CONFIGURED -> 3
    }
    return group to normalizedRemaining
}
