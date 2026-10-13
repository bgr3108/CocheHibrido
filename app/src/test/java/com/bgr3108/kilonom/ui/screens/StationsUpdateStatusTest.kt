package com.bgr3108.kilonom.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StationsUpdateStatusTest {
    @Test
    fun errorWithCacheExplainsThatSavedDataRemainsAvailable() {
        assertEquals("Sin conexión. Se seguirán mostrando los datos guardados.", stationUpdateStatusMessage(true, true, false, "Sin conexión.", false))
    }

    @Test
    fun errorWithoutCacheExplainsFirstDownloadIsRequired() {
        assertTrue(stationUpdateStatusMessage(false, true, false, "Sin conexión.", false)!!.contains("por primera vez"))
    }

    @Test
    fun successAndProgressHaveExplicitFeedback() {
        assertEquals("Datos actualizados correctamente.", stationUpdateStatusMessage(true, false, false, null, true))
        assertEquals("Actualizando datos…", stationUpdateStatusMessage(true, true, true, null, false))
    }
}
