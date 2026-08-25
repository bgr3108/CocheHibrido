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

    @Test
    fun instagramReelUrl_isTheKilonomOnePointOneReel() {
        assertEquals(
            "https://www.instagram.com/p/DcdvMd4qsnt/",
            ExternalLinks.INSTAGRAM_REEL_1_1_URL
        )
    }
}
