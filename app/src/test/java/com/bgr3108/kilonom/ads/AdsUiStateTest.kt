package com.bgr3108.kilonom.ads

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdsUiStateTest {

    @Test
    fun unresolvedConsent_doesNotInitializeMobileAds() {
        assertFalse(
            shouldInitializeMobileAds(
                AdsUiState(consentResolved = false, canRequestAds = true)
            )
        )
    }

    @Test
    fun consentThatAllowsAds_initializesExactlyOnce() {
        assertTrue(
            shouldInitializeMobileAds(
                AdsUiState(consentResolved = true, canRequestAds = true)
            )
        )
        assertFalse(
            shouldInitializeMobileAds(
                AdsUiState(
                    consentResolved = true,
                    canRequestAds = true,
                    mobileAdsInitialized = true
                )
            )
        )
    }

    @Test
    fun privacyOptionsAreOnlyShownWhenUMPRequiresThem() {
        assertFalse(AdsUiState().privacyOptionsRequired)
        assertTrue(AdsUiState(privacyOptionsRequired = true).privacyOptionsRequired)
    }

    @Test
    fun failedBannerNeverReservesSpaceWhenOfflineOrUnavailable() {
        val state = AdsUiState(
            consentResolved = true,
            canRequestAds = true,
            mobileAdsInitialized = true
        )

        assertFalse(shouldReserveBannerSpace(state, AdBannerLoadState.FAILED))
    }
}
