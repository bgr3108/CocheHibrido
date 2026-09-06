package com.bgr3108.kilonom.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import com.bgr3108.kilonom.data.MaintenanceItemEntity
import com.bgr3108.kilonom.data.VehicleEntity
import com.bgr3108.kilonom.domain.displayMaintenanceName
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

/** A calendar event prepared locally; the calendar app remains responsible for saving it. */
internal data class MaintenanceCalendarEvent(
    val title: String,
    val description: String,
    val startUtcMillis: Long,
    val endUtcMillis: Long
)

/**
 * Builds an all-day event only when the item has a concrete next date. Maintenance dates are
 * stored as a local calendar day, then expressed as UTC midnight as CalendarContract requires
 * for all-day events.
 */
internal fun MaintenanceItemEntity.toMaintenanceCalendarEvent(
    vehicle: VehicleEntity,
    zoneId: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault()
): MaintenanceCalendarEvent? {
    val dueDate = nextDueDate ?: return null
    val date = Instant.ofEpochMilli(dueDate).atZone(zoneId).toLocalDate()
    val startUtcMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val endUtcMillis = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val vehicleName = listOfNotNull(
        vehicle.brand.takeIf { it.isNotBlank() },
        vehicle.model.takeIf { it.isNotBlank() },
        vehicle.year?.toString()
    ).joinToString(" ")
    val description = buildList {
        add("Vehículo: $vehicleName")
        nextDueKm?.let { add("Próximo mantenimiento: ${it.formatKilometers(locale)} km") }
        add("Kilonom")
    }.joinToString("\n")
    return MaintenanceCalendarEvent(
        title = "Mantenimiento · ${displayMaintenanceName()}",
        description = description,
        startUtcMillis = startUtcMillis,
        endUtcMillis = endUtcMillis
    )
}

internal fun MaintenanceCalendarEvent.toInsertIntent(): Intent = Intent(Intent.ACTION_INSERT).apply {
    data = CalendarContract.Events.CONTENT_URI
    putExtra(CalendarContract.Events.TITLE, title)
    putExtra(CalendarContract.Events.DESCRIPTION, description)
    putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startUtcMillis)
    putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endUtcMillis)
    putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
    putExtra(CalendarContract.Events.EVENT_TIMEZONE, ZoneOffset.UTC.id)
    putExtra(CalendarContract.Events.EVENT_END_TIMEZONE, ZoneOffset.UTC.id)
}

/** Returns false if there is no compatible calendar activity or it disappears before launch. */
internal fun Context.openMaintenanceCalendar(event: MaintenanceCalendarEvent): Boolean {
    val intent = event.toInsertIntent()
    if (intent.resolveActivity(packageManager) == null) return false
    return try {
        startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

private fun Long.formatKilometers(locale: Locale): String =
    java.text.NumberFormat.getIntegerInstance(locale).format(this)
