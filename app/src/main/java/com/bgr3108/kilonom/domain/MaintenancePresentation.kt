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
    val remainingDays: Long?,
    val reminderLeadKm: Long = MaintenanceReminderPolicy.DEFAULT_DUE_SOON_KILOMETERS,
    val reminderLeadDays: Long = MaintenanceReminderPolicy.DEFAULT_DUE_SOON_DAYS
)

enum class MaintenanceDueMeasure {
    KILOMETERS,
    DATE
}

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
    remainingDays = item.nextDueDate?.let { daysBetween(today, it) },
    reminderLeadKm = item.reminderLeadKm,
    reminderLeadDays = item.reminderLeadDays
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
        info.remainingKm?.toDouble()?.div(info.reminderLeadKm.coerceAtLeast(1L)),
        info.remainingDays?.toDouble()?.div(info.reminderLeadDays.coerceAtLeast(1L))
    ).minOrNull() ?: Double.MAX_VALUE
    val group = when (info.status) {
        MaintenanceDueStatus.OVERDUE -> 0
        MaintenanceDueStatus.DUE_SOON -> 1
        MaintenanceDueStatus.UP_TO_DATE -> 2
        MaintenanceDueStatus.NO_DUE_CONFIGURED -> 3
    }
    return group to normalizedRemaining
}

/**
 * Chooses the same due criterion that orders reminders in the maintenance screen.
 * Kilometres and dates are normalised by the existing due-soon policy only to rank
 * urgency; they are never converted into one another.
 */
fun MaintenanceDueInfo.primaryDueMeasure(): MaintenanceDueMeasure? {
    val candidates = buildList {
        remainingKm?.let { remaining ->
            if (isRelevantForStatus(remaining, MaintenanceDueMeasure.KILOMETERS)) {
                add(MaintenanceDueMeasure.KILOMETERS to normalizedRemaining(remaining, reminderLeadKm))
            }
        }
        remainingDays?.let { remaining ->
            if (isRelevantForStatus(remaining, MaintenanceDueMeasure.DATE)) {
                add(MaintenanceDueMeasure.DATE to normalizedRemaining(remaining, reminderLeadDays))
            }
        }
    }
    return candidates.minWithOrNull(compareBy<Pair<MaintenanceDueMeasure, Double>> { it.second }
        .thenBy { it.first.ordinal })?.first
}

private fun MaintenanceDueInfo.isRelevantForStatus(
    remaining: Long,
    measure: MaintenanceDueMeasure
): Boolean = when (status) {
    MaintenanceDueStatus.OVERDUE -> remaining < 0
    MaintenanceDueStatus.DUE_SOON -> remaining in 0..dueSoonLimitFor(measure)
    MaintenanceDueStatus.UP_TO_DATE -> remaining > dueSoonLimitFor(measure)
    MaintenanceDueStatus.NO_DUE_CONFIGURED -> false
}

private fun MaintenanceDueInfo.dueSoonLimitFor(measure: MaintenanceDueMeasure): Long = when (measure) {
    MaintenanceDueMeasure.KILOMETERS -> reminderLeadKm
    MaintenanceDueMeasure.DATE -> reminderLeadDays
}

private fun normalizedRemaining(remaining: Long, threshold: Long): Double =
    remaining.toDouble() / threshold.coerceAtLeast(1L)
