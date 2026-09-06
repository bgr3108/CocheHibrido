package com.bgr3108.kilonom.domain

import com.bgr3108.kilonom.data.MaintenanceItemEntity
import com.bgr3108.kilonom.data.MaintenanceType
import org.junit.Assert.assertEquals
import org.junit.Test

class MaintenanceDueStatusTest {

    @Test
    fun kilometreBoundaries_distinguishDueNowFromOverdue() {
        assertEquals(MaintenanceDueStatus.UP_TO_DATE, status(currentKm = 48_999, dueKm = 50_000))
        assertEquals(MaintenanceDueStatus.DUE_SOON, status(currentKm = 49_000, dueKm = 50_000))
        assertEquals(MaintenanceDueStatus.DUE_SOON, status(currentKm = 49_999, dueKm = 50_000))
        assertEquals(MaintenanceDueStatus.DUE_SOON, status(currentKm = 50_000, dueKm = 50_000))
        assertEquals(MaintenanceDueStatus.OVERDUE, status(currentKm = 50_001, dueKm = 50_000))
    }

    @Test
    fun dateBoundaries_distinguishTodayFromOverdue() {
        val today = 1_000L
        assertEquals(MaintenanceDueStatus.UP_TO_DATE, status(today = today, dueDate = today + 31))
        assertEquals(MaintenanceDueStatus.DUE_SOON, status(today = today, dueDate = today + 30))
        assertEquals(MaintenanceDueStatus.DUE_SOON, status(today = today, dueDate = today + 1))
        assertEquals(MaintenanceDueStatus.DUE_SOON, status(today = today, dueDate = today))
        assertEquals(MaintenanceDueStatus.OVERDUE, status(today = today, dueDate = today - 1))
    }

    @Test
    fun configuredKilometreLeadControlsTheExactDueSoonBoundary() {
        assertEquals(MaintenanceDueStatus.UP_TO_DATE, status(currentKm = 48_499, dueKm = 50_000, leadKm = 1_500))
        assertEquals(MaintenanceDueStatus.DUE_SOON, status(currentKm = 48_500, dueKm = 50_000, leadKm = 1_500))
        assertEquals(MaintenanceDueStatus.DUE_SOON, status(currentKm = 50_000, dueKm = 50_000, leadKm = 1_500))
        assertEquals(MaintenanceDueStatus.OVERDUE, status(currentKm = 50_001, dueKm = 50_000, leadKm = 1_500))
    }

    @Test
    fun zeroLeadsOnlyReportTheExactDueMomentBeforeOverdue() {
        assertEquals(MaintenanceDueStatus.UP_TO_DATE, status(currentKm = 49_999, dueKm = 50_000, leadKm = 0))
        assertEquals(MaintenanceDueStatus.DUE_SOON, status(currentKm = 50_000, dueKm = 50_000, leadKm = 0))
        assertEquals(MaintenanceDueStatus.UP_TO_DATE, status(today = 1_000, dueDate = 1_001, leadDays = 0))
        assertEquals(MaintenanceDueStatus.DUE_SOON, status(today = 1_000, dueDate = 1_000, leadDays = 0))
    }

    @Test
    fun configuredDateLeadControlsTheExactDueSoonBoundary() {
        val today = 1_000L
        assertEquals(MaintenanceDueStatus.UP_TO_DATE, status(today = today, dueDate = today + 15, leadDays = 14))
        assertEquals(MaintenanceDueStatus.DUE_SOON, status(today = today, dueDate = today + 14, leadDays = 14))
    }

    @Test
    fun mostUrgentLimitControlsTheStatus() {
        assertEquals(
            MaintenanceDueStatus.OVERDUE,
            status(currentKm = 50_001, dueKm = 50_000, dueDate = 2_000)
        )
        assertEquals(
            MaintenanceDueStatus.OVERDUE,
            status(currentKm = 40_000, dueKm = 50_000, dueDate = 999)
        )
        assertEquals(
            MaintenanceDueStatus.DUE_SOON,
            status(currentKm = 49_500, dueKm = 50_000, dueDate = 2_000)
        )
    }

    @Test
    fun noDueConfiguration_isNotTheSameAsUpToDate() {
        assertEquals(MaintenanceDueStatus.NO_DUE_CONFIGURED, status())
    }

    private fun status(
        currentKm: Long = 40_000,
        today: Long = 1_000,
        dueKm: Long? = null,
        dueDate: Long? = null,
        leadKm: Long = 1_000,
        leadDays: Long = 30
    ): MaintenanceDueStatus = calculateMaintenanceDueStatus(
        item = MaintenanceItemEntity(
            vehicleId = 1,
            type = MaintenanceType.BRAKES,
            trackingKey = "BRAKES",
            nextDueKm = dueKm,
            nextDueDate = dueDate,
            reminderLeadKm = leadKm,
            reminderLeadDays = leadDays,
            createdAt = 0,
            updatedAt = 0
        ),
        currentKm = currentKm,
        today = today,
        daysBetween = { from, to -> to - from }
    )
}
