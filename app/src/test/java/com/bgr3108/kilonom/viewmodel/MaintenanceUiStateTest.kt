package com.bgr3108.kilonom.viewmodel

import com.bgr3108.kilonom.data.MaintenanceItemEntity
import com.bgr3108.kilonom.data.MaintenanceType
import com.bgr3108.kilonom.domain.MaintenanceDueInfo
import com.bgr3108.kilonom.domain.MaintenanceDueStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MaintenanceUiStateTest {

    @Test
    fun itemWithoutReminderOrHistory_remainsInTheVisibleFollowUpsCollection() {
        val item = MaintenanceItemUiModel(
            item = MaintenanceItemEntity(
                id = 7,
                vehicleId = 3,
                type = MaintenanceType.BATTERY_12V,
                trackingKey = "BATTERY_12V",
                createdAt = 10,
                updatedAt = 10
            ),
            name = "Batería 12 V",
            due = MaintenanceDueInfo(
                status = MaintenanceDueStatus.NO_DUE_CONFIGURED,
                dueKm = null,
                dueDate = null,
                remainingKm = null,
                remainingDays = null
            )
        )

        val state = MaintenanceUiState(items = listOf(item))

        assertTrue(state.hasItems)
        assertTrue(state.upcomingItems.isEmpty())
        assertEquals(listOf(item), state.itemsWithoutReminder)
    }

    @Test
    fun remindersAndItemsWithoutReminder_stayInTheirOwnPresentationCollections() {
        val dueItem = item(id = 1, status = MaintenanceDueStatus.UP_TO_DATE)
        val unconfiguredItem = item(id = 2, status = MaintenanceDueStatus.NO_DUE_CONFIGURED)

        val state = MaintenanceUiState(items = listOf(dueItem, unconfiguredItem))

        assertEquals(listOf(dueItem), state.upcomingItems)
        assertEquals(listOf(unconfiguredItem), state.itemsWithoutReminder)
    }

    private fun item(id: Long, status: MaintenanceDueStatus) = MaintenanceItemUiModel(
        item = MaintenanceItemEntity(
            id = id,
            vehicleId = 1,
            type = MaintenanceType.BATTERY_12V,
            trackingKey = "BATTERY_12V_$id",
            createdAt = id,
            updatedAt = id
        ),
        name = "Batería 12 V",
        due = MaintenanceDueInfo(status, null, null, null, null)
    )
}
