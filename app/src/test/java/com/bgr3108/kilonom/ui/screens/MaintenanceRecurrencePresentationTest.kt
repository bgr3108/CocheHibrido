package com.bgr3108.kilonom.ui.screens

import com.bgr3108.kilonom.data.MaintenanceItemEntity
import com.bgr3108.kilonom.data.MaintenanceTimeUnit
import com.bgr3108.kilonom.data.MaintenanceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MaintenanceRecurrencePresentationTest {

    @Test
    fun recurrenceSummary_formatsKilometresAndTimeWithNaturalGrammar() {
        assertEquals(
            "Cada 30.000 km o 2 años",
            item(intervalKm = 30_000L, intervalTimeValue = 2, intervalTimeUnit = MaintenanceTimeUnit.YEARS).recurrenceSummary()
        )
        assertEquals(
            "Cada 1 mes",
            item(intervalTimeValue = 1, intervalTimeUnit = MaintenanceTimeUnit.MONTHS).recurrenceSummary()
        )
    }

    @Test
    fun recurrenceDetailOmitsEmptyValues_andShowsOnlyRelevantReminderLeads() {
        assertNull(item().recurrenceSummary())
        assertEquals(emptyList<String>(), item().reminderLeadSummaries())
        assertEquals(
            listOf("Aviso: 1.500 km antes", "Aviso: 45 días antes"),
            item(
                intervalKm = 30_000L,
                intervalTimeValue = 2,
                intervalTimeUnit = MaintenanceTimeUnit.YEARS,
                reminderLeadKm = 1_500L,
                reminderLeadDays = 45L
            ).reminderLeadSummaries()
        )
    }

    private fun item(
        intervalKm: Long? = null,
        intervalTimeValue: Int? = null,
        intervalTimeUnit: MaintenanceTimeUnit? = null,
        reminderLeadKm: Long = 1_000L,
        reminderLeadDays: Long = 30L
    ) = MaintenanceItemEntity(
        vehicleId = 1,
        type = MaintenanceType.GENERAL_SERVICE,
        trackingKey = "GENERAL_SERVICE",
        createdAt = 1,
        updatedAt = 1,
        intervalKm = intervalKm,
        intervalTimeValue = intervalTimeValue,
        intervalTimeUnit = intervalTimeUnit,
        reminderLeadKm = reminderLeadKm,
        reminderLeadDays = reminderLeadDays
    )
}
