package com.bgr3108.kilonom.ui.screens

import com.bgr3108.kilonom.data.MaintenanceItemEntity
import com.bgr3108.kilonom.data.MaintenanceType
import com.bgr3108.kilonom.domain.MaintenanceDueInfo
import com.bgr3108.kilonom.domain.MaintenanceDueStatus
import com.bgr3108.kilonom.viewmodel.MaintenanceItemUiModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.Locale

class MaintenanceDetailPresentationTest {

    private lateinit var previousLocale: Locale

    @Before
    fun useSpanishFormatting() {
        previousLocale = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("es-ES"))
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(previousLocale)
    }

    @Test
    fun onlyDate_isShownOnce() {
        assertEquals(listOf("05/11/2026"), item(nextDueDate = novemberFifth()).nextDueDetailLines())
    }

    @Test
    fun onlyKilometres_areShownOnce() {
        assertEquals(listOf("58.500 km"), item(nextDueKm = 58_500L).nextDueDetailLines())
    }

    @Test
    fun dateAndKilometres_areShownAsTwoDistinctLines() {
        assertEquals(
            listOf("05/11/2026", "58.500 km"),
            item(nextDueDate = novemberFifth(), nextDueKm = 58_500L).nextDueDetailLines()
        )
    }

    @Test
    fun noDue_showsTheConfiguredNoReminderMessage() {
        assertEquals(listOf("Sin próximo aviso configurado"), item().nextDueDetailLines())
    }

    private fun item(nextDueDate: Long? = null, nextDueKm: Long? = null) = MaintenanceItemUiModel(
        item = MaintenanceItemEntity(
            id = 1,
            vehicleId = 1,
            type = MaintenanceType.BATTERY_12V,
            trackingKey = "BATTERY_12V",
            nextDueKm = nextDueKm,
            nextDueDate = nextDueDate,
            createdAt = 1,
            updatedAt = 1
        ),
        name = "Batería 12 V",
        due = MaintenanceDueInfo(
            status = if (nextDueDate == null && nextDueKm == null) {
                MaintenanceDueStatus.NO_DUE_CONFIGURED
            } else {
                MaintenanceDueStatus.UP_TO_DATE
            },
            dueKm = nextDueKm,
            dueDate = nextDueDate,
            remainingKm = null,
            remainingDays = null
        )
    )

    private fun novemberFifth(): Long = Calendar.getInstance().apply {
        clear()
        set(2026, Calendar.NOVEMBER, 5)
    }.timeInMillis
}
