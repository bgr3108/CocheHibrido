package com.bgr3108.kilonom.ui.screens

import com.bgr3108.kilonom.domain.MaintenanceDueMeasure
import com.bgr3108.kilonom.domain.MaintenanceHomeInsight
import com.bgr3108.kilonom.domain.MaintenanceHomeInsightType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class MaintenanceHomeInsightPresentationTest {

    private val spanish = Locale.forLanguageTag("es-ES")

    @Test
    fun singleDueSoon_usesTheItemNameAndRelevantUnit() {
        assertEquals(
            "Aceite y filtro en 650 km.",
            insight(MaintenanceHomeInsightType.SINGLE_DUE_SOON, "Aceite y filtro", MaintenanceDueMeasure.KILOMETERS, 650)
                .homeMessage(spanish)
        )
        assertEquals(
            "ITV en 12 días.",
            insight(MaintenanceHomeInsightType.SINGLE_DUE_SOON, "ITV", MaintenanceDueMeasure.DATE, 12)
                .homeMessage(spanish)
        )
    }

    @Test
    fun overdueAndImmediateMessages_areClearAndNeverIncludeCosts() {
        assertEquals(
            "ITV: venció hace 1 día.",
            insight(MaintenanceHomeInsightType.SINGLE_OVERDUE, "ITV", MaintenanceDueMeasure.DATE, -1)
                .homeMessage(spanish)
        )
        assertEquals(
            "Aceite y filtro: venció hace 250 km.",
            insight(MaintenanceHomeInsightType.SINGLE_OVERDUE, "Aceite y filtro", MaintenanceDueMeasure.KILOMETERS, -250)
                .homeMessage(spanish)
        )
        assertEquals(
            "ITV toca hoy.",
            insight(MaintenanceHomeInsightType.SINGLE_DUE_NOW, "ITV", MaintenanceDueMeasure.DATE, 0)
                .homeMessage(spanish)
        )
        assertEquals(
            "Aceite y filtro toca ahora.",
            insight(MaintenanceHomeInsightType.SINGLE_DUE_NOW, "Aceite y filtro", MaintenanceDueMeasure.KILOMETERS, 0)
                .homeMessage(spanish)
        )
    }

    @Test
    fun aggregateAndEmptyMessages_matchTheirStates() {
        assertEquals("Mantenimiento pendiente", insight(MaintenanceHomeInsightType.MULTIPLE_OVERDUE, count = 3).homeTitle())
        assertEquals("Tienes 3 elementos pendientes.", insight(MaintenanceHomeInsightType.MULTIPLE_OVERDUE, count = 3).homeMessage(spanish))
        assertEquals("Próximos mantenimientos", insight(MaintenanceHomeInsightType.MULTIPLE_DUE_SOON, count = 3).homeTitle())
        assertEquals("Tienes 3 avisos próximos.", insight(MaintenanceHomeInsightType.MULTIPLE_DUE_SOON, count = 3).homeMessage(spanish))
        assertEquals("Aún no has añadido mantenimientos.", insight(MaintenanceHomeInsightType.NO_ITEMS).homeMessage(spanish))
        assertEquals("No hay próximos avisos configurados.", insight(MaintenanceHomeInsightType.NO_REMINDERS).homeMessage(spanish))
        assertEquals("Todo al día.", insight(MaintenanceHomeInsightType.ALL_UP_TO_DATE).homeMessage(spanish))
    }

    private fun insight(
        type: MaintenanceHomeInsightType,
        name: String? = null,
        measure: MaintenanceDueMeasure? = null,
        amount: Long? = null,
        count: Int = 0
    ) = MaintenanceHomeInsight(type, name, measure, amount, count)
}
