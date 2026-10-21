package com.bgr3108.kilonom.ui.screens

import androidx.compose.runtime.saveable.SaverScope
import com.bgr3108.kilonom.data.*
import org.junit.Assert.*
import org.junit.Test

class VehicleAssistantSavedStateTest {
    private val answers = mapOf(
        VehicleIdentificationField.DISPLACEMENT_CC to setOf("1200"),
        VehicleIdentificationField.POWER_KW to setOf("55")
    )
    private val skipped = setOf(VehicleIdentificationField.COMMERCIAL_VERSION)

    @Test fun saversRoundTripAnswersAndOmissionsUsingOnlySmallValues() {
        val scope = SaverScope { value -> value is String || value is ArrayList<*> }
        val savedAnswers = with(AssistantAnswersSaver) { requireNotNull(scope.save(answers)) }
        val savedSkipped = with(SkippedAssistantHintsSaver) { requireNotNull(scope.save(skipped)) }
        assertEquals(answers, AssistantAnswersSaver.restore(savedAnswers))
        assertEquals(skipped, SkippedAssistantHintsSaver.restore(savedSkipped))
    }

    @Test fun validProgressRestoresTheSameQuestionWithoutSavingAnExtraStep() {
        val helper = assistant()
        val restored = validateAssistantProgress(helper, VehicleType.GASOLINA, answers, skipped)
        assertEquals(answers, restored.answers)
        assertEquals(skipped, restored.skipped)
        val next = helper.resolve(requireNotNull(restored.energy), restored.answers, restored.skipped)
        assertEquals(VehicleIdentificationField.ENGINE_CODE,
            (next as VehicleVersionAssistantResolution.AskIdentification).hint.field)
    }

    @Test fun unavailableEnergyDropsIncompatibleAnswersAndOmissions() {
        assertEquals(ValidAssistantProgress(null), validateAssistantProgress(assistant(), VehicleType.ELECTRICO, answers, skipped))
    }

    @Test fun invalidAnswerDropsItAndAllDependentProgress() {
        val restored = validateAssistantProgress(assistant(), VehicleType.GASOLINA,
            answers + (VehicleIdentificationField.DISPLACEMENT_CC to setOf("9999")), skipped)
        assertTrue(restored.answers.isEmpty())
        assertTrue(restored.skipped.isEmpty())
    }

    @Test fun changedCatalogRetainsOnlyTheStillValidPrefix() {
        val restored = validateAssistantProgress(assistant(powerOffset = 100), VehicleType.GASOLINA, answers, skipped)
        assertEquals(mapOf(VehicleIdentificationField.DISPLACEMENT_CC to setOf("1200")), restored.answers)
        assertTrue(restored.skipped.isEmpty())
    }

    @Test fun unvisitedOrUnknownFutureQuestionsAreNotRestoredAsOmissions() {
        val restored = validateAssistantProgress(assistant(), VehicleType.GASOLINA, emptyMap(), setOf(VehicleIdentificationField.ENGINE_CODE))
        assertTrue(restored.answers.isEmpty())
        assertTrue(restored.skipped.isEmpty())
    }

    private fun assistant(powerOffset: Int = 0) = VehicleVersionAssistant((0..7).map { index ->
        VehicleSelectionVariant(
            id = "$index", catalogId = "$index", displayName = "Versión $index", automaticDisplayName = "Versión $index",
            energyType = VehicleType.GASOLINA,
            identification = RuntimeVehicleIdentification(
                listOf(if (index < 4) 1200 else 1600),
                listOf((if (index % 4 < 2) 55 else 74) + powerOffset),
                listOf(if (index % 2 == 0) "Base" else "Plus"), listOf("E$index")
            ), bodyStyle = null,
            vehicle = VehicleInfo("Marca", "Modelo", 2026, VehicleCategory.COCHE, VehicleType.GASOLINA, 0.0, 40.0 + index)
        )
    })
}
