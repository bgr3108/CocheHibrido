package com.bgr3108.kilonom.ui.screens

import android.content.Intent
import android.provider.CalendarContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MaintenanceCalendarIntentInstrumentedTest {

    @Test
    fun insertIntent_usesCalendarUriAllDayFlagAndUtcBounds() {
        val event = MaintenanceCalendarEvent(
            title = "Mantenimiento · ITV",
            description = "Vehículo: SEAT León 2026\nKilonom",
            startUtcMillis = 1_824_220_800_000L,
            endUtcMillis = 1_824_307_200_000L
        )

        val intent = event.toInsertIntent()

        assertEquals(Intent.ACTION_INSERT, intent.action)
        assertEquals(CalendarContract.Events.CONTENT_URI, intent.data)
        assertEquals(event.title, intent.getStringExtra(CalendarContract.Events.TITLE))
        assertEquals(event.description, intent.getStringExtra(CalendarContract.Events.DESCRIPTION))
        assertTrue(intent.getBooleanExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, false))
        assertEquals(event.startUtcMillis, intent.getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, -1L))
        assertEquals(event.endUtcMillis, intent.getLongExtra(CalendarContract.EXTRA_EVENT_END_TIME, -1L))
    }
}
