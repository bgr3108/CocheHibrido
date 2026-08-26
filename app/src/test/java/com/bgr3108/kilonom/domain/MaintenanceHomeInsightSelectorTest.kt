package com.bgr3108.kilonom.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class MaintenanceHomeInsightSelectorTest {

    @Test
    fun noItems_andItemsWithoutReminders_areDistinct() {
        assertEquals(
            MaintenanceHomeInsightType.NO_ITEMS,
            selectMaintenanceHomeInsight(emptyList()).type
        )
        assertEquals(
            MaintenanceHomeInsightType.NO_REMINDERS,
            selectMaintenanceHomeInsight(listOf(item("Aceite", MaintenanceDueStatus.NO_DUE_CONFIGURED))).type
        )
    }

    @Test
    fun oneOrMoreUpToDateReminders_reportEverythingUpToDate() {
        assertEquals(
            MaintenanceHomeInsightType.ALL_UP_TO_DATE,
            selectMaintenanceHomeInsight(listOf(item("ITV", MaintenanceDueStatus.UP_TO_DATE, days = 31))).type
        )
        assertEquals(
            MaintenanceHomeInsightType.ALL_UP_TO_DATE,
            selectMaintenanceHomeInsight(
                listOf(
                    item("ITV", MaintenanceDueStatus.UP_TO_DATE, days = 31),
                    item("Seguro", MaintenanceDueStatus.UP_TO_DATE, km = 1_001),
                    item("Frenos", MaintenanceDueStatus.UP_TO_DATE, km = 2_000)
                )
            ).type
        )
    }

    @Test
    fun oneDueSoonReminder_canBeKilometresOrDate() {
        val byKm = selectMaintenanceHomeInsight(
            listOf(item("Aceite y filtro", MaintenanceDueStatus.DUE_SOON, km = 650))
        )
        assertEquals(MaintenanceHomeInsightType.SINGLE_DUE_SOON, byKm.type)
        assertEquals(MaintenanceDueMeasure.KILOMETERS, byKm.dueMeasure)
        assertEquals(650L, byKm.amount)

        val byDate = selectMaintenanceHomeInsight(
            listOf(item("ITV", MaintenanceDueStatus.DUE_SOON, days = 12))
        )
        assertEquals(MaintenanceHomeInsightType.SINGLE_DUE_SOON, byDate.type)
        assertEquals(MaintenanceDueMeasure.DATE, byDate.dueMeasure)
        assertEquals(12L, byDate.amount)
    }

    @Test
    fun multipleDueSoonReminders_showACount() {
        val result = selectMaintenanceHomeInsight(
            listOf(
                item("Aceite", MaintenanceDueStatus.DUE_SOON, km = 650),
                item("ITV", MaintenanceDueStatus.DUE_SOON, days = 12)
            )
        )
        assertEquals(MaintenanceHomeInsightType.MULTIPLE_DUE_SOON, result.type)
        assertEquals(2, result.count)
    }

    @Test
    fun oneOverdueReminder_canBeKilometresOrDate() {
        val byKm = selectMaintenanceHomeInsight(
            listOf(item("Aceite", MaintenanceDueStatus.OVERDUE, km = -250))
        )
        assertEquals(MaintenanceHomeInsightType.SINGLE_OVERDUE, byKm.type)
        assertEquals(MaintenanceDueMeasure.KILOMETERS, byKm.dueMeasure)
        assertEquals(-250L, byKm.amount)

        val byDate = selectMaintenanceHomeInsight(
            listOf(item("ITV", MaintenanceDueStatus.OVERDUE, days = -1))
        )
        assertEquals(MaintenanceHomeInsightType.SINGLE_OVERDUE, byDate.type)
        assertEquals(MaintenanceDueMeasure.DATE, byDate.dueMeasure)
        assertEquals(-1L, byDate.amount)
    }

    @Test
    fun multipleOverdueReminders_showACount_andDominateDueSoon() {
        val result = selectMaintenanceHomeInsight(
            listOf(
                item("Aceite", MaintenanceDueStatus.OVERDUE, km = -250),
                item("ITV", MaintenanceDueStatus.OVERDUE, days = -1),
                item("Seguro", MaintenanceDueStatus.DUE_SOON, days = 3)
            )
        )
        assertEquals(MaintenanceHomeInsightType.MULTIPLE_OVERDUE, result.type)
        assertEquals(2, result.count)
    }

    @Test
    fun dueTodayAndDueNow_areNotOverdue_andTakePriorityOverNormalDueSoon() {
        val dueToday = selectMaintenanceHomeInsight(
            listOf(
                item("ITV", MaintenanceDueStatus.DUE_SOON, days = 0),
                item("Seguro", MaintenanceDueStatus.DUE_SOON, days = 3)
            )
        )
        assertEquals(MaintenanceHomeInsightType.SINGLE_DUE_NOW, dueToday.type)
        assertEquals(MaintenanceDueMeasure.DATE, dueToday.dueMeasure)
        assertEquals(0L, dueToday.amount)

        val dueNow = selectMaintenanceHomeInsight(
            listOf(
                item("Aceite", MaintenanceDueStatus.DUE_SOON, km = 0),
                item("ITV", MaintenanceDueStatus.DUE_SOON, days = 12)
            )
        )
        assertEquals(MaintenanceHomeInsightType.SINGLE_DUE_NOW, dueNow.type)
        assertEquals(MaintenanceDueMeasure.KILOMETERS, dueNow.dueMeasure)
        assertEquals(0L, dueNow.amount)
    }

    @Test
    fun severalImmediateReminders_showACount() {
        val result = selectMaintenanceHomeInsight(
            listOf(
                item("Aceite", MaintenanceDueStatus.DUE_SOON, km = 0),
                item("ITV", MaintenanceDueStatus.DUE_SOON, days = 0)
            )
        )
        assertEquals(MaintenanceHomeInsightType.MULTIPLE_DUE_NOW, result.type)
        assertEquals(2, result.count)
    }

    @Test
    fun theMoreUrgentMeasure_controlsAnItemWithDateAndKilometres() {
        val kilometreFirst = selectMaintenanceHomeInsight(
            listOf(item("Aceite", MaintenanceDueStatus.DUE_SOON, km = 100, days = 20))
        )
        assertEquals(MaintenanceDueMeasure.KILOMETERS, kilometreFirst.dueMeasure)

        val dateFirst = selectMaintenanceHomeInsight(
            listOf(item("Aceite", MaintenanceDueStatus.DUE_SOON, km = 900, days = 2))
        )
        assertEquals(MaintenanceDueMeasure.DATE, dateFirst.dueMeasure)
    }

    @Test
    fun customAndTyreLabels_areRetainedWithoutCostData() {
        val custom = selectMaintenanceHomeInsight(
            listOf(item("Bujías", MaintenanceDueStatus.DUE_SOON, km = 650))
        )
        val tyres = selectMaintenanceHomeInsight(
            listOf(item("Neumáticos delanteros", MaintenanceDueStatus.DUE_SOON, km = 650))
        )
        assertEquals("Bujías", custom.itemName)
        assertEquals("Neumáticos delanteros", tyres.itemName)
    }

    @Test
    fun eachActiveVehicleList_isEvaluatedIndependently() {
        val vehicleA = selectMaintenanceHomeInsight(
            listOf(item("ITV", MaintenanceDueStatus.OVERDUE, days = -2))
        )
        val vehicleB = selectMaintenanceHomeInsight(
            listOf(item("Seguro", MaintenanceDueStatus.UP_TO_DATE, days = 31))
        )
        assertEquals(MaintenanceHomeInsightType.SINGLE_OVERDUE, vehicleA.type)
        assertEquals(MaintenanceHomeInsightType.ALL_UP_TO_DATE, vehicleB.type)
    }

    private fun item(
        name: String,
        status: MaintenanceDueStatus,
        km: Long? = null,
        days: Long? = null
    ) = MaintenanceHomeInsightItem(
        name = name,
        due = MaintenanceDueInfo(
            status = status,
            dueKm = km?.let { 50_000L + it },
            dueDate = days?.let { 1_000L + it },
            remainingKm = km,
            remainingDays = days
        )
    )
}
