package com.bgr3108.kilonom.util

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Test

class SupportContactTest {
    @Test
    fun `support email uses a mailto send-to request`() {
        val request = supportEmailIntentRequest()

        assertEquals(Intent.ACTION_SENDTO, request.action)
        assertEquals("mailto:kilonom.app@gmail.com", request.uri)
    }

    @Test
    fun `instagram uses an action-view request for the official profile`() {
        val request = externalUrlIntentRequest(ExternalLinks.INSTAGRAM_PROFILE_URL)

        assertEquals(Intent.ACTION_VIEW, request.action)
        assertEquals("https://www.instagram.com/kilonom.app/", request.uri)
    }
}
