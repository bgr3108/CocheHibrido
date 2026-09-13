package com.bgr3108.kilonom.stations

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MitecoStationMapperTest {
    @Test
    fun validStation_parsesSpanishPricesAndKeepsAllAvailableProducts() {
        val result = MitecoStationMapper.map(
            mapOf(
                "IDEESS" to "1234",
                "Rótulo" to "Kilonom Test",
                "Dirección" to "Calle Uno, 1",
                "Municipio" to "Madrid",
                "Provincia" to "MADRID",
                "Latitud" to "40,4168",
                "Longitud (WGS84)" to "-3,7038",
                "Precio Gasolina 95 E5" to "1,649",
                "Precio Gasoleo A" to "1,529"
            ),
            sourceUpdatedAtMillis = 10L
        )!!

        assertEquals("1234", result.station.externalId)
        assertEquals(40.4168, result.station.latitude!!, 0.0001)
        assertEquals(2, result.prices.size)
        assertEquals(1.649, result.prices.first { it.productCode == "gasolina_95_e5" }.price, 0.0001)
    }

    @Test
    fun optionalFieldsCanBeEmpty_withoutInventingValues() {
        val result = MitecoStationMapper.map(
            mapOf("IDEESS" to "9", "Rótulo" to "", "Latitud" to "", "Longitud (WGS84)" to ""),
            sourceUpdatedAtMillis = null
        )!!

        assertEquals("Estación de servicio", result.station.name)
        assertNull(result.station.latitude)
        assertNull(result.station.longitude)
        assertTrue(result.prices.isEmpty())
    }

    @Test
    fun invalidCoordinates_ignoreOnlyTheInvalidRow() {
        assertNull(
            MitecoStationMapper.map(
                mapOf("IDEESS" to "bad", "Latitud" to "95,0", "Longitud (WGS84)" to "0,0"),
                sourceUpdatedAtMillis = null
            )
        )
    }

    @Test
    fun cachePolicy_marksMissingAndOldCacheAsStale() {
        assertTrue(StationRepository.isStale(null, nowMillis = 100L))
        val metadata = StationCacheMetadataEntity(downloadedAtMillis = 100L, sourceUpdatedAtMillis = null, sourceUrl = "https://example.test")
        assertTrue(StationRepository.isStale(metadata, 100L + StationRepository.CACHE_STALE_AFTER_MILLIS))
    }

    @Test
    fun simplifiedFuelFilter_exposesOnlyCommonCategoriesWithExactMitecoMappings() {
        assertEquals(
            listOf("Gasolina 95", "Gasolina 98", "Diésel", "AdBlue", "GLP"),
            StationFuelType.entries.map { it.displayName }
        )
        assertEquals(listOf("gasolina_95_e5", "gasolina_95_e10", "gasolina_95_e5_premium"), StationFuelType.GASOLINE_95.productCodesByPriority)
        assertEquals(listOf("gasolina_98_e5", "gasolina_98_e10"), StationFuelType.GASOLINE_98.productCodesByPriority)
        assertEquals(listOf("gasoleo_a"), StationFuelType.DIESEL.productCodesByPriority)
        assertEquals(listOf("adblue"), StationFuelType.ADBLUE.productCodesByPriority)
        assertEquals(listOf("gases_licuados_del_petroleo"), StationFuelType.GLP.productCodesByPriority)
    }

    @Test
    fun fuelFilter_usesTheDefinedVariantPriorityAndDoesNotDuplicateStations() {
        val selected = mapStationRowsForFuelType(
            listOf(
                stationRow("one", "gasolina_95_e10", 1.50, "Gasolina 95 E10"),
                stationRow("one", "gasolina_95_e5", 1.60, "Gasolina 95 E5"),
                stationRow("two", "gasolina_95_e10", 1.40, "Gasolina 95 E10")
            ),
            StationFuelType.GASOLINE_95
        )

        assertEquals(listOf("two", "one"), selected.map { it.externalId })
        assertEquals(listOf(1.40, 1.60), selected.map { it.price })
        assertTrue(selected.all { it.productName == "Gasolina 95" })
    }

    @Test
    fun uncommonMitecoProducts_remainInTheCacheEvenWhenNotShownInTheFilter() {
        val result = MitecoStationMapper.map(
            mapOf(
                "IDEESS" to "uncommon",
                "Precio Gasoleo B" to "1,099",
                "Precio Gasoleo Premium" to "1,199",
                "Precio Biogas Natural Comprimido" to "0,899"
            ),
            sourceUpdatedAtMillis = null
        )!!

        assertEquals(
            setOf("gasoleo_b", "gasoleo_premium", "biogas_natural_comprimido"),
            result.prices.map { it.productCode }.toSet()
        )
    }

    private fun stationRow(id: String, productCode: String, price: Double, productName: String) = StationListItem(
        externalId = id,
        name = id,
        address = "",
        municipality = "",
        province = "",
        latitude = null,
        longitude = null,
        schedule = null,
        sourceUpdatedAtMillis = null,
        productCode = productCode,
        price = price,
        productName = productName,
        hasSelectedFuel = true
    )
}
