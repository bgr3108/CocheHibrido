package com.bgr3108.kilonom.domain

/** A compact, presentation-ready maintenance reminder input for the active vehicle only. */
data class MaintenanceHomeInsightItem(
    val name: String,
    val due: MaintenanceDueInfo
)

enum class MaintenanceHomeInsightType {
    NO_ITEMS,
    NO_REMINDERS,
    ALL_UP_TO_DATE,
    SINGLE_OVERDUE,
    MULTIPLE_OVERDUE,
    SINGLE_DUE_NOW,
    MULTIPLE_DUE_NOW,
    SINGLE_DUE_SOON,
    MULTIPLE_DUE_SOON
}

data class MaintenanceHomeInsight(
    val type: MaintenanceHomeInsightType,
    val itemName: String? = null,
    val dueMeasure: MaintenanceDueMeasure? = null,
    val amount: Long? = null,
    val count: Int = 0
)

/**
 * Selects exactly one message for Home. It is deliberately independent from Compose and
 * receives only maintenance already scoped to the active vehicle.
 */
fun selectMaintenanceHomeInsight(items: List<MaintenanceHomeInsightItem>): MaintenanceHomeInsight {
    if (items.isEmpty()) return MaintenanceHomeInsight(MaintenanceHomeInsightType.NO_ITEMS)

    val configuredItems = items.filter { it.due.status != MaintenanceDueStatus.NO_DUE_CONFIGURED }
    if (configuredItems.isEmpty()) return MaintenanceHomeInsight(MaintenanceHomeInsightType.NO_REMINDERS)

    val overdue = configuredItems.filter { it.due.status == MaintenanceDueStatus.OVERDUE }
    if (overdue.size > 1) {
        return MaintenanceHomeInsight(MaintenanceHomeInsightType.MULTIPLE_OVERDUE, count = overdue.size)
    }
    overdue.singleOrNull()?.let { return it.toSingleInsight(MaintenanceHomeInsightType.SINGLE_OVERDUE) }

    val dueNow = configuredItems.filter { it.due.isDueNowOrToday() }
    if (dueNow.size > 1) {
        return MaintenanceHomeInsight(MaintenanceHomeInsightType.MULTIPLE_DUE_NOW, count = dueNow.size)
    }
    dueNow.singleOrNull()?.let { return it.toSingleInsight(MaintenanceHomeInsightType.SINGLE_DUE_NOW) }

    val dueSoon = configuredItems.filter { it.due.status == MaintenanceDueStatus.DUE_SOON }
    if (dueSoon.size > 1) {
        return MaintenanceHomeInsight(MaintenanceHomeInsightType.MULTIPLE_DUE_SOON, count = dueSoon.size)
    }
    dueSoon.singleOrNull()?.let { return it.toSingleInsight(MaintenanceHomeInsightType.SINGLE_DUE_SOON) }

    return MaintenanceHomeInsight(MaintenanceHomeInsightType.ALL_UP_TO_DATE)
}

private fun MaintenanceHomeInsightItem.toSingleInsight(type: MaintenanceHomeInsightType): MaintenanceHomeInsight {
    val measure = due.primaryDueMeasure()
    val amount = when (measure) {
        MaintenanceDueMeasure.KILOMETERS -> due.remainingKm
        MaintenanceDueMeasure.DATE -> due.remainingDays
        null -> null
    }
    return MaintenanceHomeInsight(type, itemName = name, dueMeasure = measure, amount = amount)
}

private fun MaintenanceDueInfo.isDueNowOrToday(): Boolean = status == MaintenanceDueStatus.DUE_SOON &&
    primaryDueMeasure()?.let { measure ->
        when (measure) {
            MaintenanceDueMeasure.KILOMETERS -> remainingKm == 0L
            MaintenanceDueMeasure.DATE -> remainingDays == 0L
        }
    } == true
