package com.bgr3108.kilonom.ui.screens

import com.bgr3108.kilonom.data.MaintenanceItemEntity
import com.bgr3108.kilonom.data.MaintenanceType
import com.bgr3108.kilonom.data.VehicleCategory
import com.bgr3108.kilonom.data.VehicleEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

class MaintenanceCalendarIntentTest {

    private val madrid = ZoneId.of("Europe/Madrid")
    private val spanish = Locale.forLanguageTag("es-ES")

    @Test
    fun datedItem_buildsAnAllDayEventWithTheCorrectVehicleAndKilometres() {
        val event = item(
            type = MaintenanceType.ITV,
            nextDueDate = storedDate(LocalDate.of(2027, 10, 31)),
            nextDueKm = 58_500L
        ).toMaintenanceCalendarEvent(vehicle("SEAT", "León", 2026), madrid, spanish)!!

        assertEquals("Mantenimiento · ITV", event.title)
        assertEquals("Vehículo: SEAT León 2026\nPróximo mantenimiento: 58.500 km\nKilonom", event.description)
        assertEquals(utcDateMillis(LocalDate.of(2027, 10, 31)), event.startUtcMillis)
        assertEquals(utcDateMillis(LocalDate.of(2027, 11, 1)), event.endUtcMillis)
    }

    @Test
    fun itemWithoutConcreteDate_cannotBeExported() {
        assertNull(item(nextDueDate = null).toMaintenanceCalendarEvent(vehicle(), madrid, spanish))
    }

    @Test
    fun customItem_usesItsVisibleNameAndOmitsAnAbsentKilometre() {
        val event = item(
            type = MaintenanceType.OTHER,
            customName = "Bujías",
            nextDueDate = storedDate(LocalDate.of(2028, 9, 4)),
            nextDueKm = null
        ).toMaintenanceCalendarEvent(vehicle("Yamaha", "XMAX", null), madrid, spanish)!!

        assertEquals("Mantenimiento · Bujías", event.title)
        assertEquals("Vehículo: Yamaha XMAX\nKilonom", event.description)
        assertFalse(event.description.contains("Próximo mantenimiento:"))
    }

    @Test
    fun editingTheDateOrUsingAnotherVehicle_buildsANewIndependentEvent() {
        val original = item(nextDueDate = storedDate(LocalDate.of(2028, 9, 4)))
        val edited = original.copy(nextDueDate = storedDate(LocalDate.of(2028, 9, 15)))

        val first = original.toMaintenanceCalendarEvent(vehicle("SEAT", "León", 2026), madrid, spanish)!!
        val second = edited.toMaintenanceCalendarEvent(vehicle("Honda", "CB500X", 2025), madrid, spanish)!!

        assertEquals(utcDateMillis(LocalDate.of(2028, 9, 4)), first.startUtcMillis)
        assertEquals(utcDateMillis(LocalDate.of(2028, 9, 15)), second.startUtcMillis)
        assertTrue(second.description.contains("Vehículo: Honda CB500X 2025"))
        assertFalse(second.description.contains("SEAT León"))
    }

    private fun item(
        type: MaintenanceType = MaintenanceType.GENERAL_SERVICE,
        customName: String? = null,
        nextDueKm: Long? = null,
        nextDueDate: Long?
    ) = MaintenanceItemEntity(
        vehicleId = 1L,
        type = type,
        customName = customName,
        trackingKey = type.name,
        nextDueKm = nextDueKm,
        nextDueDate = nextDueDate,
        createdAt = 1L,
        updatedAt = 1L
    )

    private fun vehicle(brand: String = "SEAT", model: String = "León", year: Int? = 2026) = VehicleEntity(
        id = 1L,
        category = VehicleCategory.COCHE,
        brand = brand,
        model = model,
        year = year,
        type = null,
        fuelTankCapacity = 0.0,
        batteryCapacity = 0.0,
        initialKm = 0.0,
        createdAt = 1L
    )

    private fun storedDate(date: LocalDate): Long = date.atStartOfDay(madrid).toInstant().toEpochMilli()

    private fun utcDateMillis(date: LocalDate): Long = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
}
