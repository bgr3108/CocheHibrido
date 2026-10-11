package com.bgr3108.kilonom.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

/** Compact metadata contract for a catalog artifact supplied by a future transport layer. */
internal data class VehicleCatalogManifest(
    val schemaVersion: Int,
    val catalogVersion: Int,
    val generatedAt: String,
    val sha256: String,
    val file: String
)

internal const val VEHICLE_CATALOG_MANIFEST_SCHEMA_VERSION = 1

internal fun parseVehicleCatalogManifest(json: String): VehicleCatalogManifest {
    val root = Json.parseToJsonElement(json).jsonObject
    val schemaVersion = root.requiredManifestInt("schemaVersion")
    require(schemaVersion == VEHICLE_CATALOG_MANIFEST_SCHEMA_VERSION) {
        "manifest.schemaVersion: expected $VEHICLE_CATALOG_MANIFEST_SCHEMA_VERSION, was $schemaVersion"
    }
    val catalogVersion = root.requiredManifestInt("catalogVersion")
    require(catalogVersion > 0) { "manifest.catalogVersion: must be positive" }
    val generatedAt = root.requiredManifestString("generatedAt")
    require(generatedAt.matches(Regex("^\\d{4}-\\d{2}-\\d{2}T.*Z$"))) {
        "manifest.generatedAt: expected UTC ISO-8601 timestamp"
    }
    val sha256 = root.requiredManifestString("sha256")
    require(sha256.matches(Regex("^[0-9a-fA-F]{64}$"))) {
        "manifest.sha256: expected a 64-character hexadecimal SHA-256"
    }
    val file = root.requiredManifestString("file")
    require(file == INSTALLED_RUNTIME_CATALOG_FILE_NAME) {
        "manifest.file: expected $INSTALLED_RUNTIME_CATALOG_FILE_NAME"
    }
    return VehicleCatalogManifest(schemaVersion, catalogVersion, generatedAt, sha256.lowercase(), file)
}

private fun JsonObject.requiredManifestInt(name: String): Int =
    requiredManifestPrimitive(name).intOrNull ?: error("manifest.$name: expected integer")

private fun JsonObject.requiredManifestString(name: String): String {
    val value = requiredManifestPrimitive(name)
    val content = value.contentOrNull
    require(value.isString && !content.isNullOrBlank()) { "manifest.$name: expected non-empty string" }
    return content
}

private fun JsonObject.requiredManifestPrimitive(name: String): JsonPrimitive {
    val value = this[name]
    require(value != null && value !is JsonNull) { "manifest.$name: required value is missing" }
    require(value is JsonPrimitive) { "manifest.$name: expected primitive" }
    return value
}
