package com.bgr3108.kilonom.ui.screens

import com.bgr3108.kilonom.data.MaintenanceType
import com.bgr3108.kilonom.data.MaintenanceTimeUnit
import com.bgr3108.kilonom.data.MaintenanceRecordEntity
import com.bgr3108.kilonom.domain.MaintenanceNextDueUpdate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class MaintenanceFormValidationTest {

    @Test
    fun typeMustBeExplicitlySelectedForANewMaintenance() {
        assertEquals("Selecciona un tipo de mantenimiento", validateSelectedMaintenanceType(null))
        assertNull(validateSelectedMaintenanceType(MaintenanceType.OIL_AND_FILTER))
    }

    @Test
    fun upcomingItemAndHistoryRecordWithTheSameDatabaseId_haveDistinctLazyListKeys() {
        assertNotEquals(maintenanceUpcomingItemKey(1L), maintenanceHistoryRecordKey(1L))
    }

    @Test
    fun tyresRequirePosition_andOtherRequiresName() {
        assertEquals(
            "Selecciona la posición de los neumáticos",
            validate(type = MaintenanceType.TYRES)
        )
        assertEquals(
            "El nombre personalizado es obligatorio",
            validate(type = MaintenanceType.OTHER)
        )
    }

    @Test
    fun physicalRecordRequiresDate_butKilometresAreOptional() {
        assertEquals("Introduce la fecha del mantenimiento", validate(includeRecord = true))
        assertNull(validate(includeRecord = true, recordDate = 1L))
        assertNull(validate(includeRecord = true, recordDate = 1L, recordKm = "42000"))
        assertNull(validate(type = MaintenanceType.ITV, document = true, includeRecord = true, recordDate = 1L))
    }

    @Test
    fun optionalKilometres_keepEmptyValuesNull_andRejectInvalidValues() {
        assertNull(optionalOdometerKm(""))
        assertNull(optionalOdometerKm("  "))
        assertEquals(42_000L, optionalOdometerKm("42000"))
        assertEquals("El kilometraje no es válido", validate(includeRecord = true, recordDate = 1L, recordKm = "-1"))
        assertEquals("El kilometraje no es válido", validate(includeRecord = true, recordDate = 1L, recordKm = "999999999999999999999999"))
    }

    @Test
    fun historicalRecordIsValid_andNextDueBeforeItsOdometerIsRejected() {
        assertNull(validate(includeRecord = true, recordDate = 1L, recordKm = "42000"))
        assertEquals(
            "El próximo kilometraje no puede ser anterior al mantenimiento realizado",
            validate(includeRecord = true, recordDate = 1L, recordKm = "42000", hasDueKm = true, dueKm = "41000")
        )
    }

    @Test
    fun nextDueKilometresAreValidWhenThePerformedOdometerIsMissing() {
        assertNull(validate(includeRecord = true, recordDate = 1L, hasDueKm = true, dueKm = "50000"))
    }

    @Test
    fun historyDetailsOmitKilometresWhenTheRecordHasNoOdometer() {
        val details = MaintenanceRecordEntity(
            itemId = 1,
            performedDate = 1L,
            odometerKm = null,
            cost = 95.0,
            createdAt = 1L,
            updatedAt = 1L
        ).detailsText().orEmpty()

        assertFalse(details.contains("km"))
        assertFalse(details.contains("null"))
    }

    @Test
    fun overdueReminderBelowCurrentKmRemainsValidWhenNoRecordIsBeingRegistered() {
        assertNull(validate(hasDueKm = true, dueKm = "50000"))
    }

    @Test
    fun changingReminderModeDoesNotPersistThePreviouslySelectedLimit() {
        assertEquals(
            50_000L to null,
            selectedReminderDueValues(
                hasDueKm = true,
                dueKm = "50000",
                hasDueDate = false,
                dueDate = 1L
            )
        )
        assertEquals(
            null to 1L,
            selectedReminderDueValues(
                hasDueKm = false,
                dueKm = "50000",
                hasDueDate = true,
                dueDate = 1L
            )
        )
    }

    @Test
    fun recurringIntervalsAcceptKilometresTimeOrBoth_andRejectZero() {
        assertNull(validateIntervalConfiguration(MaintenanceIntervalMode.KM, "30000", "", null))
        assertNull(validateIntervalConfiguration(MaintenanceIntervalMode.TIME, "", "2", MaintenanceTimeUnit.YEARS))
        assertNull(validateIntervalConfiguration(MaintenanceIntervalMode.BOTH, "30000", "2", MaintenanceTimeUnit.YEARS))
        assertEquals(
            "Introduce un intervalo de kilometraje válido.",
            validateIntervalConfiguration(MaintenanceIntervalMode.KM, "0", "", null)
        )
        assertEquals(
            "Introduce un intervalo de tiempo válido.",
            validateIntervalConfiguration(MaintenanceIntervalMode.TIME, "", "0", MaintenanceTimeUnit.MONTHS)
        )
    }

    @Test
    fun reminderLeadsAllowZero_andRejectInvalidValues() {
        assertNull(validateReminderLeads("0", "0", validateKm = true, validateDays = true))
        assertEquals(
            "El aviso previo por kilometraje no puede ser negativo.",
            validateReminderLeads("-1", "30", validateKm = true, validateDays = false)
        )
        assertEquals(
            "El aviso previo por fecha no puede ser negativo.",
            validateReminderLeads("1000", "-1", validateKm = false, validateDays = true)
        )
    }

    @Test
    fun nextDueChoiceMapsToAutomaticManualOrExplicitClear() {
        assertEquals(
            MaintenanceNextDueUpdate.AutomaticFromInterval,
            MaintenanceNextDueMode.AUTOMATIC.toRepositoryUpdate(null to null)
        )
        assertEquals(
            MaintenanceNextDueUpdate.Manual(50_000L, null),
            MaintenanceNextDueMode.MANUAL.toRepositoryUpdate(50_000L to null)
        )
        assertEquals(
            MaintenanceNextDueUpdate.Clear,
            MaintenanceNextDueMode.NONE.toRepositoryUpdate(50_000L to 1L)
        )
    }

    @Test
    fun unrepresentableNumericTextAndTimeIntervalsReturnFormErrors() {
        assertEquals("Introduce un intervalo de kilometraje válido.",
            validateIntervalConfiguration(MaintenanceIntervalMode.KM, "9223372036854775808", "", null))
        assertEquals("Introduce un intervalo de tiempo válido.",
            validateIntervalConfiguration(MaintenanceIntervalMode.TIME, "", "2147483648", MaintenanceTimeUnit.YEARS))
        assertEquals("El intervalo genera una fecha fuera del rango admitido.",
            validateIntervalConfiguration(MaintenanceIntervalMode.TIME, "", Int.MAX_VALUE.toString(), MaintenanceTimeUnit.YEARS,
                performedDate = formBaseDate(), zoneId = ZoneOffset.UTC))
    }

    @Test
    fun actualBaseKilometresAndDateAreValidatedBeforeSaving() {
        assertEquals("El intervalo genera un kilometraje fuera del rango admitido.",
            validateIntervalConfiguration(MaintenanceIntervalMode.KM, "1", "", null, performedKm = Long.MAX_VALUE))
        assertEquals("El intervalo genera una fecha fuera del rango admitido.",
            validateIntervalConfiguration(MaintenanceIntervalMode.TIME, "", "1", MaintenanceTimeUnit.DAYS,
                performedDate = Long.MAX_VALUE, zoneId = ZoneOffset.UTC))
        assertNull(validateIntervalConfiguration(MaintenanceIntervalMode.KM, Long.MAX_VALUE.toString(), "", null, performedKm = 0L))
    }

    @Test
    fun failedPreviewCanBeCorrectedAndRecalculatedWithoutLosingValidValues() {
        val invalid = calculateMaintenanceFormDue(formBaseDate(), 42_000, Long.MAX_VALUE, 1, MaintenanceTimeUnit.YEARS, ZoneOffset.UTC)
        assertTrue(invalid.isFailure)
        val corrected = calculateMaintenanceFormDue(formBaseDate(), 42_000, 30_000, 1, MaintenanceTimeUnit.YEARS, ZoneOffset.UTC).getOrThrow()
        assertEquals(72_000L, corrected.nextDueKm)
        assertEquals(LocalDate.of(2027, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), corrected.nextDueDate)
        assertNull(validateIntervalConfiguration(MaintenanceIntervalMode.BOTH, "30000", "1", MaintenanceTimeUnit.YEARS,
            performedKm = 42_000, performedDate = formBaseDate(), zoneId = ZoneOffset.UTC))
    }

    @Test
    fun malformedImportedRecurrenceHasAnEditablePreviewError() {
        assertTrue(calculateMaintenanceFormDue(formBaseDate(), 42_000, -1, 1, MaintenanceTimeUnit.YEARS, ZoneOffset.UTC).isFailure)
        assertTrue(calculateMaintenanceFormDue(formBaseDate(), 42_000, 30_000, 1, null, ZoneOffset.UTC).isFailure)
    }

    private fun formBaseDate() = LocalDate.of(2026, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    private fun validate(
        type: MaintenanceType = MaintenanceType.BRAKES,
        document: Boolean = false,
        tyrePosition: String? = null,
        customName: String = "",
        includeRecord: Boolean = false,
        recordDate: Long? = null,
        recordKm: String = "",
        hasDueKm: Boolean = false,
        dueKm: String = ""
    ): String? = validateMaintenanceForm(
        type = type,
        tyrePosition = tyrePosition,
        customName = customName,
        includeRecord = includeRecord,
        documentType = document,
        recordDate = recordDate,
        recordKm = recordKm,
        cost = "",
        hasDueKm = hasDueKm,
        dueKm = dueKm,
        hasDueDate = false,
        dueDate = null
    )
}
