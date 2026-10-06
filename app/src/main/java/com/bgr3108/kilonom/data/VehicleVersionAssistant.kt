package com.bgr3108.kilonom.data

/** Catalog-only aid. Documentary hints come from the structured runtime catalog, never model rules. */
data class VehicleVersionAssistant(
    private val variants: List<VehicleSelectionVariant>
) {
    val energyOptions: List<VehicleVersionEnergyOption> = variants
        .groupBy { it.energyType }
        .keys
        .sortedBy { it.assistantOrder() }
        .map { VehicleVersionEnergyOption(it, it.assistantLabel()) }

    fun resolve(
        energyType: VehicleType,
        answers: Map<VehicleIdentificationField, Set<String>> = emptyMap(),
        skippedFields: Set<VehicleIdentificationField> = emptySet()
    ): VehicleVersionAssistantResolution {
        val matches = variants.filter { variant ->
            variant.energyType == energyType && answers.all { (field, values) ->
                variant.identificationValues(field).intersect(values).isNotEmpty()
            }
        }
        if (matches.size == 1) return VehicleVersionAssistantResolution.Match(matches.single())
        if (matches.isEmpty()) return VehicleVersionAssistantResolution.Unresolved
        nextHint(matches, answers.keys + skippedFields)?.let { return VehicleVersionAssistantResolution.AskIdentification(it) }
        return VehicleVersionAssistantResolution.Unresolved
    }

    private fun nextHint(
        matches: List<VehicleSelectionVariant>,
        unavailableFields: Set<VehicleIdentificationField>
    ): VehicleIdentificationHint? = VehicleIdentificationField.entries.asSequence()
        .filterNot { it in unavailableFields }
        .mapNotNull { field -> field.toHintOrNull(matches) }
        .firstOrNull()
}

data class VehicleVersionEnergyOption(val type: VehicleType, val displayName: String)

enum class VehicleIdentificationField { DISPLACEMENT_CC, POWER_KW, COMMERCIAL_VERSION, ENGINE_CODE, BODY_STYLE }

data class VehicleIdentificationHint(
    val field: VehicleIdentificationField,
    val instruction: String,
    val question: String,
    val helperText: String,
    val options: List<VehicleIdentificationOption>
)

data class VehicleIdentificationOption(val values: Set<String>, val displayName: String)

sealed interface VehicleVersionAssistantResolution {
    data class Match(val variant: VehicleSelectionVariant) : VehicleVersionAssistantResolution
    data class AskIdentification(val hint: VehicleIdentificationHint) : VehicleVersionAssistantResolution
    data object Unresolved : VehicleVersionAssistantResolution
}

fun VehicleSelectionCatalog.versionAssistantFor(
    brandId: String,
    modelId: String,
    year: Int
): VehicleVersionAssistant? = variantsFor(brandId, modelId, year)
    .takeIf { it.size > 1 }
    ?.let(::VehicleVersionAssistant)

private fun VehicleIdentificationField.toHintOrNull(
    variants: List<VehicleSelectionVariant>
): VehicleIdentificationHint? {
    val valuesByVariant = variants.associateWith { it.identificationValues(this) }
    if (valuesByVariant.values.any { it.isEmpty() }) return null
    val variantsByValue = valuesByVariant
        .flatMap { (variant, values) -> values.map { value -> value to variant } }
        .groupBy({ it.first }, { it.second })
    if (variantsByValue.size < 2 || variantsByValue.values.none { it.size < variants.size }) return null
    val groups = variantsByValue.entries.groupBy(
        keySelector = { (_, matched) -> matched.map { it.id }.sorted() },
        valueTransform = { (value, _) -> value }
    )
    return VehicleIdentificationHint(
        field = this,
        instruction = instruction(),
        question = question(),
        helperText = helperText(),
        options = groups.values.map { values ->
            VehicleIdentificationOption(values.toSet(), values.toSet().toDisplayName(this))
        }.sortedBy { it.displayName }
    )
}

private fun VehicleSelectionVariant.identificationValues(field: VehicleIdentificationField): Set<String> = when (field) {
    VehicleIdentificationField.DISPLACEMENT_CC -> identification.displacementCc.map(Int::toString).toSet()
    VehicleIdentificationField.POWER_KW -> identification.powerKw.map(Int::toString).toSet()
    VehicleIdentificationField.COMMERCIAL_VERSION -> identification.commercialVersions.toSet()
    VehicleIdentificationField.ENGINE_CODE -> identification.engineCodes.toSet()
    VehicleIdentificationField.BODY_STYLE -> bodyStyle?.takeIf(String::isNotBlank)?.let(::setOf).orEmpty()
}

private fun Set<String>.toDisplayName(field: VehicleIdentificationField): String = when (field) {
    VehicleIdentificationField.DISPLACEMENT_CC -> "${sortedBy(String::toInt).joinToString(" o ") { it.toInt().toSpanishNumber() }} cm³"
    VehicleIdentificationField.POWER_KW -> sortedBy(String::toInt).joinToString(" o ") { "$it kW" }
    VehicleIdentificationField.BODY_STYLE -> singleOrNull()?.toBodyStyleLabel().orEmpty()
    else -> sorted().joinToString(" o ")
}

private fun Int.toSpanishNumber(): String = toString().reversed().chunked(3).joinToString(".").reversed()

private fun String.toBodyStyleLabel(): String = when (this) {
    "HATCHBACK" -> "Turismo"
    "MPV" -> "Monovolumen"
    else -> replace('_', ' ').lowercase().replaceFirstChar(Char::titlecase)
}

private fun VehicleIdentificationField.instruction(): String = when (this) {
    VehicleIdentificationField.DISPLACEMENT_CC -> "Busca el campo P.1 en tu ficha técnica o permiso de circulación."
    VehicleIdentificationField.POWER_KW -> "Busca el campo P.2 en tu ficha técnica o permiso de circulación."
    VehicleIdentificationField.COMMERCIAL_VERSION, VehicleIdentificationField.ENGINE_CODE -> "Consulta los campos D.2 o D.3 en tu documentación."
    VehicleIdentificationField.BODY_STYLE -> "Comprueba la carrocería indicada en la documentación del vehículo."
}

private fun VehicleIdentificationField.question(): String = when (this) {
    VehicleIdentificationField.DISPLACEMENT_CC -> "¿Qué cilindrada aparece?"
    VehicleIdentificationField.POWER_KW -> "¿Qué potencia aparece?"
    VehicleIdentificationField.COMMERCIAL_VERSION -> "¿Qué denominación comercial aparece?"
    VehicleIdentificationField.ENGINE_CODE -> "¿Qué variante o código de motor aparece?"
    VehicleIdentificationField.BODY_STYLE -> "¿Qué carrocería tiene?"
}

private fun VehicleIdentificationField.helperText(): String = when (this) {
    VehicleIdentificationField.DISPLACEMENT_CC -> "P.1 corresponde a la cilindrada del motor."
    VehicleIdentificationField.POWER_KW -> "P.2 corresponde a la potencia máxima expresada en kW."
    VehicleIdentificationField.COMMERCIAL_VERSION -> "D.3 suele recoger la denominación comercial del vehículo."
    VehicleIdentificationField.ENGINE_CODE -> "D.2 puede contener datos de tipo, variante o versión."
    VehicleIdentificationField.BODY_STYLE -> "Solo se usa cuando la carrocería cambia los datos relevantes del vehículo."
}

internal fun VehicleType.assistantLabel(): String = when (this) {
    VehicleType.GASOLINA -> "Gasolina"
    VehicleType.DIESEL -> "Diésel"
    VehicleType.HIBRIDO -> "Híbrido"
    VehicleType.HIBRIDO_ENCHUFABLE -> "Híbrido enchufable"
    VehicleType.ELECTRICO -> "Eléctrico"
}

private fun VehicleType.assistantOrder(): Int = when (this) {
    VehicleType.GASOLINA -> 0
    VehicleType.DIESEL -> 1
    VehicleType.HIBRIDO -> 2
    VehicleType.HIBRIDO_ENCHUFABLE -> 3
    VehicleType.ELECTRICO -> 4
}
