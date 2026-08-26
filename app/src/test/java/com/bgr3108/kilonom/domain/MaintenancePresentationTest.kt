package com.bgr3108.kilonom.domain

import com.bgr3108.kilonom.data.MaintenanceItemEntity
import com.bgr3108.kilonom.data.MaintenanceRecordEntity
import com.bgr3108.kilonom.data.MaintenanceType
import com.bgr3108.kilonom.data.TyrePosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MaintenancePresentationTest {

    @Test
    fun dueInfo_preservesBothLimitsAndUsesTheMostUrgentStatus() {
        val info = createMaintenanceDueInfo(
            item = item(nextDueKm = 50_000, nextDueDate = 20),
            currentKm = 49_500,
            today = 21,
            daysBetween = { from, to -> to - from }
        )

        assertEquals(MaintenanceDueStatus.OVERDUE, info.status)
        assertEquals(500L, info.remainingKm)
        assertEquals(-1L, info.remainingDays)
    }

    @Test
    fun urgencySort_ordersOverdueBeforeDueSoonAndUpToDate() {
        val overdue = MaintenanceDueInfo(MaintenanceDueStatus.OVERDUE, 50_000, null, -1, null)
        val dueSoon = MaintenanceDueInfo(MaintenanceDueStatus.DUE_SOON, 50_000, null, 500, null)
        val upToDate = MaintenanceDueInfo(MaintenanceDueStatus.UP_TO_DATE, 60_000, null, 10_000, null)

        assertTrue(maintenanceUrgencySortValue(overdue).first < maintenanceUrgencySortValue(dueSoon).first)
        assertTrue(maintenanceUrgencySortValue(dueSoon).first < maintenanceUrgencySortValue(upToDate).first)
    }

    @Test
    fun displayName_usesTyrePositionAndCustomName() {
        assertEquals("Neumáticos delanteros", item(type = MaintenanceType.TYRES, position = TyrePosition.FRONT).displayMaintenanceName())
        assertEquals("Bujías", item(type = MaintenanceType.OTHER, customName = "Bujías").displayMaintenanceName())
    }

    @Test
    fun persistedOilAndFilterItemWithRecord_remainsPresentableAfterReload() {
        val oil = item(type = MaintenanceType.OIL_AND_FILTER, nextDueKm = 20_000, nextDueDate = 1_850_857_200_000)
        val record = MaintenanceRecordEntity(
            itemId = oil.id,
            performedDate = 1_785_711_600_000,
            odometerKm = 10_000,
            cost = 120.0,
            notes = null,
            createdAt = 1,
            updatedAt = 1
        )

        val due = createMaintenanceDueInfo(oil, currentKm = 10_000, today = 1, daysBetween = { from, to -> to - from })

        assertEquals("Aceite y filtro", oil.displayMaintenanceName())
        assertEquals(MaintenanceDueStatus.UP_TO_DATE, due.status)
        assertEquals(10_000L, due.remainingKm)
        assertEquals(10_000L, record.odometerKm)
        assertEquals(120.0, record.cost ?: error("Cost must remain present"), 0.0)
    }

    private fun item(
        type: MaintenanceType = MaintenanceType.BRAKES,
        position: TyrePosition? = null,
        customName: String? = null,
        nextDueKm: Long? = null,
        nextDueDate: Long? = null
    ) = MaintenanceItemEntity(
        vehicleId = 1,
        type = type,
        tyrePosition = position,
        customName = customName,
        trackingKey = "TEST",
        nextDueKm = nextDueKm,
        nextDueDate = nextDueDate,
        createdAt = 0,
        updatedAt = 0
    )
}
