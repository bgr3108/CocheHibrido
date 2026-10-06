package com.bgr3108.kilonom.util

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Test

class SupportContactTest {
    @Test
    fun `vehicle request email uses a mailto send-to request`() {
        val request = vehicleRequestEmailIntentRequest(
            brand = null,
            model = null,
            year = null,
            variant = null
        )

        assertEquals(Intent.ACTION_SENDTO, request.action)
        assertEquals("mailto:kilonom.app@gmail.com", request.uri)
    }

    @Test
    fun `vehicle request email includes only selected catalog context`() {
        val request = vehicleRequestEmailIntentRequest(
            brand = "Opel",
            model = "Corsa",
            year = "2012",
            variant = "Gasolina"
        )

        assertEquals(Intent.ACTION_SENDTO, request.action)
        assertEquals("mailto:kilonom.app@gmail.com", request.uri)
        assertEquals("Kilonom - Solicitud de vehículo", request.subject)
        assertEquals(
            "Hola,\n\nNo encuentro mi vehículo en Kilonom.\n\nMarca: Opel\nModelo: Corsa\nAño: 2012\nMotorización: Gasolina\n\nGracias.",
            request.body
        )
    }

    @Test
    fun `assistant help email includes the selected fuel without personal identifiers`() {
        val request = vehicleRequestEmailIntentRequest(
            brand = "Opel",
            model = "Corsa",
            year = "2019",
            variant = null,
            fuel = "Gasolina",
            note = "No sé qué versión corresponde."
        )

        assertEquals(
            "Hola,\n\nNo encuentro mi vehículo en Kilonom.\n\nMarca: Opel\nModelo: Corsa\nAño: 2019\nCombustible: Gasolina\nNo sé qué versión corresponde.\n\nGracias.",
            request.body
        )
    }

    @Test
    fun `instagram uses an action-view request for the official profile`() {
        val request = externalUrlIntentRequest(ExternalLinks.INSTAGRAM_PROFILE_URL)

        assertEquals(Intent.ACTION_VIEW, request.action)
        assertEquals("https://www.instagram.com/kilonom.app/", request.uri)
    }
}
