package com.bgr3108.kilonom.ui.screens

import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.mapSaver
import com.bgr3108.kilonom.data.VehicleIdentificationField
import com.bgr3108.kilonom.data.VehicleType
import com.bgr3108.kilonom.data.VehicleVersionAssistant
import com.bgr3108.kilonom.data.VehicleVersionAssistantResolution

/** Bundle values contain only stable field names and the user's small sets of answer strings. */
internal val AssistantAnswersSaver = mapSaver<Map<VehicleIdentificationField, Set<String>>>(
    save = { answers -> answers.entries.associate { it.key.name to ArrayList(it.value) } },
    restore = { saved ->
        saved.entries.mapNotNull { (name, value) ->
            val field = VehicleIdentificationField.entries.firstOrNull { it.name == name }
            val values = (value as? List<*>)?.filterIsInstance<String>()?.toSet()
            if (field != null && !values.isNullOrEmpty()) field to values else null
        }.toMap()
    }
)

internal val SkippedAssistantHintsSaver = listSaver<Set<VehicleIdentificationField>, String>(
    save = { fields -> fields.map { it.name } },
    restore = { names -> VehicleIdentificationField.entries.filter { it.name in names }.toSet() }
)

internal data class ValidAssistantProgress(
    val energy: VehicleType?,
    val answers: Map<VehicleIdentificationField, Set<String>> = emptyMap(),
    val skipped: Set<VehicleIdentificationField> = emptySet()
)

/** Replay the existing assistant, retaining only answers/omissions reachable in this catalog. */
internal fun validateAssistantProgress(
    assistant: VehicleVersionAssistant,
    energy: VehicleType?,
    answers: Map<VehicleIdentificationField, Set<String>>,
    skipped: Set<VehicleIdentificationField>
): ValidAssistantProgress {
    if (energy == null || assistant.energyOptions.none { it.type == energy }) return ValidAssistantProgress(null)
    val accepted = linkedMapOf<VehicleIdentificationField, Set<String>>()
    val omitted = linkedSetOf<VehicleIdentificationField>()
    repeat(VehicleIdentificationField.entries.size) {
        val hint = (assistant.resolve(energy, accepted, omitted) as? VehicleVersionAssistantResolution.AskIdentification)?.hint
            ?: return ValidAssistantProgress(energy, accepted, omitted)
        val answer = answers[hint.field]
        when {
            answer != null && hint.options.any { it.values == answer } -> accepted[hint.field] = answer
            hint.field in skipped -> omitted += hint.field
            else -> return ValidAssistantProgress(energy, accepted, omitted)
        }
    }
    return ValidAssistantProgress(energy, accepted, omitted)
}
