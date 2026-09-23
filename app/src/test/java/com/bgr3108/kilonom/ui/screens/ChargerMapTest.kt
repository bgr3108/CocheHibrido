package com.bgr3108.kilonom.ui.screens

import com.bgr3108.kilonom.chargers.ChargerListItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChargerMapTest {
    @Test
    fun geoJson_usesOneMinimalFeaturePerInstallationWithValidCoordinates() {
        val geoJson = listOf(
            charger("installation-a", latitude = 40.0, longitude = -3.0),
            // Multiple connector rows for the same physical installation still produce one point.
            charger("installation-a", latitude = 40.0, longitude = -3.0),
            charger("without-coordinates", latitude = null, longitude = null)
        ).toChargerGeoJson()

        assertEquals(1, geoJson.featureCount())
        assertTrue(geoJson.contains("\"charger_id\":\"installation-a\""))
        assertFalse(geoJson.contains("connectorTypes"))
        assertFalse(geoJson.contains("operatorName"))
    }

    @Test
    fun geoJson_escapesStableInstallationIdWithoutGrowingTheBuilder() {
        val geoJson = listOf(charger("station-\"north\\west", latitude = 40.0, longitude = -3.0))
            .toChargerGeoJson()

        assertTrue(geoJson.contains("station-\\\"north\\\\west"))
        assertEquals(1, geoJson.featureCount())
    }

    @Test
    fun preparation_keepsFeatureLookupByStableInstallationId() {
        val item = charger("installation-a", latitude = 40.0, longitude = -3.0)

        val preparation = prepareChargerMap(listOf(item)) as ChargerMapPreparation.Ready

        assertEquals(item, preparation.chargersById["installation-a"])
    }

    @Test
    fun preparationFailure_returnsSafeStateInsteadOfThrowingIntoCompose() {
        val preparation = prepareChargerMap(listOf(charger("installation-a", 40.0, -3.0))) {
            error("Synthetic serialization failure")
        }

        assertEquals(ChargerMapPreparation.Failed, preparation)
    }

    @Test
    fun largeInstallationSet_buildsOneFeatureForEveryUniqueValidInstallation() {
        val chargers = List(12_500) { index ->
            charger("installation-$index", latitude = 40.0 + index / 100_000.0, longitude = -3.0)
        }

        val geoJson = chargers.toChargerGeoJson()

        assertEquals(12_500, geoJson.featureCount())
    }

    private fun charger(id: String, latitude: Double?, longitude: Double?) = ChargerListItem(
        externalId = id,
        name = "Installation $id",
        operatorName = "Operator",
        address = "Address",
        municipality = "Municipality",
        province = "Province",
        schedule = null,
        latitude = latitude,
        longitude = longitude,
        sourceUpdatedAtMillis = null,
        connectorTypes = listOf("IEC_62196_T2"),
        maxPowerKw = 22.0
    )

    private fun String.featureCount(): Int = "\"type\":\"Feature\"".toRegex().findAll(this).count()
}
