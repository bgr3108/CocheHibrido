package com.bgr3108.kilonom.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ExternalLinksTest {

    @Test
    fun instagramProfileUrl_isTheOfficialKilonomProfile() {
        assertEquals(
            "https://www.instagram.com/kilonom.app/",
            ExternalLinks.INSTAGRAM_PROFILE_URL
        )
    }
}
