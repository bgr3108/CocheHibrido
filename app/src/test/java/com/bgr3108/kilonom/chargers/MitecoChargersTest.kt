package com.bgr3108.kilonom.chargers

import com.bgr3108.kilonom.stations.StationCoordinates
import com.bgr3108.kilonom.stations.StationRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringReader

class MitecoChargersTest {
    @Test
    fun parser_readsActualUtf16CsvShape_withQuotedSemicolonsAndConnectorFields() {
        val csv = """
            COMUNIDAD AUTONOMA;PROVINCIA;MUNICIPIO;LATITUD;LONGITUD;NOMBRE INSTALACION;DIRECCIÓN;CODIGO POSTAL;LOCALIZACION;TIPO HORARIO APERTURA;HORARIO APERTURA;NOMBRE OPERADOR;COD.OPERADOR;TIPOS DE SERVICIOS;COD.INSTALACION;ID. PUNTO DE RECARGA;ACCESIBILIDAD;METODOS DE PAGOS;COD. PUNTO DE RECARGA;ID. CONECTOR;TIPO CONECTOR;TIPO DE CARGA;FORMATO;POTENCIA MAXIMA;VOLTAJE;INTENSIDAD;FECHA DE ULTIMA MODIFICACION
            "Canarias";"Las Palmas";"Las Palmas de Gran Canaria";="28.1234";="-15.4321";"Centro; Atlántico";"Calle Mayor 1";="35001";"";"Horario habitual";"L-V 08:00-20:00";"Operador Uno";"ES*001";"";"INST-1";"P-1";"";"";"CP-1";"1";"IEC_62196_T2_COMBO";"DC";"Cable";"150,00 kW";"800 V";"200,00 A";"21/11/2023 11:39:04"
        """.trimIndent()

        val parsed = MitecoChargersParser.parse(StringReader(csv))

        assertEquals(1, parsed.installations.size)
        assertEquals(1, parsed.connectors.size)
        assertEquals("Centro; Atlántico", parsed.installations.single().name)
        assertEquals(-15.4321, parsed.installations.single().longitude!!, 0.0001)
        assertEquals(150.0, parsed.connectors.single().maxPowerKw!!, 0.001)
        assertEquals("IEC_62196_T2_COMBO", parsed.connectors.single().connectorType)
    }

    @Test
    fun csvReader_keepsFormulaStyleCoordinatesAndEscapedQuotesWithinTheirRecord() {
        val csv = listOf(
            "COD.INSTALACION;LATITUD;LONGITUD;NOMBRE INSTALACION;ID. CONECTOR",
            "\"one\";=\"28.1000\";=\"-15.4000\";\"Centro \"\"Norte\"\"\";\"1\"",
            "\"two\";=\"28.2000\";=\"-15.5000\";\"Segundo centro\";\"1\""
        ).joinToString("\n")

        val parsed = MitecoChargersParser.parse(StringReader(csv))

        assertEquals(listOf("one", "two"), parsed.installations.map { it.externalId })
        assertEquals("Centro \"Norte\"", parsed.installations.first().name)
    }

    @Test
    fun parser_keepsConnectorsWithTheSameSourceNumberAtDifferentEvse() {
        val csv = listOf(
            "COD.INSTALACION;ID. PUNTO DE RECARGA;COD. PUNTO DE RECARGA;ID. CONECTOR;TIPO CONECTOR",
            "installation;ES*ONE*E001;code-1;1;IEC_62196_T2",
            "installation;ES*ONE*E002;code-2;1;IEC_62196_T2"
        ).joinToString("\n")

        val parsed = MitecoChargersParser.parse(StringReader(csv))

        assertEquals(2, parsed.connectors.size)
        assertEquals(
            listOf("installation|ES*ONE*E001|1", "installation|ES*ONE*E002|1"),
            parsed.connectors.map(ChargerConnectorEntity::externalId)
        )
    }

    @Test
    fun mapper_ignoresInvalidCoordinates_withoutBreakingOtherRows() {
        val invalid = mapOf("COD.INSTALACION" to "bad", "LATITUD" to "99", "LONGITUD" to "-3")
        assertNull(MitecoChargerMapper.map(invalid, 1))
        val valid = mapOf("COD.INSTALACION" to "ok", "LATITUD" to "28,1", "LONGITUD" to "-15,4", "ID. CONECTOR" to "1")
        assertEquals("ok", MitecoChargerMapper.map(valid, 2)?.installation?.externalId)
    }

    @Test
    fun connectorPresentation_usesOnlyActualRipreeValues() {
        assertEquals("CCS / Combo 2", listOf("IEC_62196_T2_COMBO").toPresentationConnectorNames().single())
        assertEquals("Tipo 2", listOf("IEC_62196_T2").toPresentationConnectorNames().single())
        assertEquals("CHAdeMO", listOf("CHADEMO").toPresentationConnectorNames().single())
        assertEquals("Enchufe doméstico", listOf("DOMESTIC_E").toPresentationConnectorNames().single())
        assertEquals("Tesla", listOf("TeslaConnectorEurope").toPresentationConnectorNames().single())
    }

    @Test
    fun filters_keepOriginalConnectorValues_andSortByPowerOrDistance() {
        val chargers = listOf(
            charger("canarias", -15.43, 28.12, listOf("IEC_62196_T2_COMBO"), 150.0, "Operador A"),
            charger("tipo2", -15.40, 28.10, listOf("IEC_62196_T2"), 22.0, "Operador B")
        )
        val ccs = filterAndSortChargers(chargers, ChargerFilter(connectorType = ChargerConnectorType.CCS_COMBO_2), null)
        assertEquals(listOf("canarias"), ccs.map(ChargerListItem::externalId))
        val highPower = filterAndSortChargers(chargers, ChargerFilter(minimumPowerKw = 50.0), null)
        assertEquals(listOf("canarias"), highPower.map(ChargerListItem::externalId))
        val near = filterAndSortChargers(chargers, ChargerFilter(sortOrder = ChargerSortOrder.DISTANCE), StationCoordinates(28.10, -15.40))
        assertEquals("tipo2", near.first().externalId)
        assertTrue(near.first().distanceMeters != null)
    }

    @Test
    fun noOperatorUrlIsInvented_whenRipreeDoesNotContainOne() {
        assertFalse(charger("one", -3.0, 40.0, emptyList(), null, null).operatorUrl != null)
    }

    @Test
    fun installationAndConnectorSummaries_buildOneListItemWithoutRepeatingInstallationFields() {
        val installation = ChargerInstallationEntity(
            externalId = "installation-a", name = "Installation", operatorName = "Operator", operatorCode = null,
            address = "Address", municipality = "Municipality", province = "Province", postalCode = null,
            locality = null, schedule = null, scheduleType = null, latitude = 40.0, longitude = -3.0,
            sourceUpdatedAtMillis = 1L
        )
        val chargers = listOf(installation).toChargers(
            listOf(
                ChargerConnectorSummary("installation-a", "IEC_62196_T2", 22.0),
                ChargerConnectorSummary("installation-a", "IEC_62196_T2_COMBO", 50.0)
            )
        )

        assertEquals(1, chargers.size)
        assertEquals(listOf("IEC_62196_T2", "IEC_62196_T2_COMBO"), chargers.single().connectorTypes)
        assertEquals(50.0, chargers.single().maxPowerKw!!, 0.001)
    }

    @Test
    fun cacheImport_buildsInstallationSummaryOnce_withoutDroppingOriginalConnectors() {
        val installation = ChargerInstallationEntity(
            externalId = "installation-a", name = "Installation", operatorName = null, operatorCode = null,
            address = "", municipality = "", province = "", postalCode = null, locality = null,
            schedule = null, scheduleType = null, latitude = null, longitude = null, sourceUpdatedAtMillis = null
        )
        val connectors = listOf(
            ChargerConnectorEntity("a|1", "installation-a", null, null, "IEC_62196_T2", null, null, 22.0, null, null),
            ChargerConnectorEntity("a|2", "installation-a", null, null, "IEC_62196_T2_COMBO", null, null, 50.0, null, null)
        )

        val summary = listOf(installation).withConnectorSummaries(connectors).single()

        assertEquals(50.0, summary.maxPowerKw!!, 0.001)
        assertEquals(listOf("IEC_62196_T2", "IEC_62196_T2_COMBO"), summary.connectorTypesEncoded.split(CHARGER_CONNECTOR_SEPARATOR))
        assertEquals(2, connectors.size)
    }

    @Test
    fun cacheRefreshPolicy_keepsFreshCacheVisibleAndRefreshesOnlyMissingOrStaleCache() {
        val now = 2_000_000_000L
        val fresh = ChargerCacheMetadataEntity(downloadedAtMillis = now - 1, sourceUpdatedAtMillis = null, sourceUrl = "source")
        val stale = fresh.copy(downloadedAtMillis = now - StationRepository.CACHE_STALE_AFTER_MILLIS)

        assertFalse(shouldRefreshChargerCache(fresh, now))
        assertTrue(shouldRefreshChargerCache(stale, now))
        assertTrue(shouldRefreshChargerCache(null, now))
    }

    @Test
    fun nearbyQuery_onlyUsesConnectorTableWhenAConnectorWasSelected() {
        assertFalse(requiresConnectorLookup(ChargerFilter()))
        assertTrue(requiresConnectorLookup(ChargerFilter(connectorType = ChargerConnectorType.CCS_COMBO_2)))
    }

    private fun charger(id: String, longitude: Double, latitude: Double, connectors: List<String>, power: Double?, operator: String?) =
        ChargerListItem(
            externalId = id, name = id, operatorName = operator, address = "", municipality = "", province = "",
            schedule = null, latitude = latitude, longitude = longitude, sourceUpdatedAtMillis = null,
            connectorTypes = connectors, maxPowerKw = power
        )
}
