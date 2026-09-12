package com.bgr3108.kilonom.ui.components

import com.bgr3108.kilonom.ads.AdBannerLoadState
import com.bgr3108.kilonom.ads.AdsUiState
import com.bgr3108.kilonom.ads.shouldLoadBanner
import com.bgr3108.kilonom.ads.shouldReserveBannerSpace
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdBannerSlotTest {

    @Test
    fun mainRoutes_useTheReservedBannerSlot() {
        listOf("home", "consumption", "stats", "stats/trends", "maintenance", "stations").forEach { route ->
            assertTrue(routeUsesAdBannerSlot(route))
        }
    }

    @Test
    fun secondaryRoutes_doNotUseTheReservedBannerSlot() {
        listOf("my_vehicles", "maintenance_detail/3", "usage_guide", null).forEach { route ->
            assertFalse(routeUsesAdBannerSlot(route))
        }
    }

    @Test
    fun banner_reservesHeightOnlyAfterItIsLoaded() {
        val readyState = AdsUiState(
            consentResolved = true,
            canRequestAds = true,
            mobileAdsInitialized = true
        )

        assertFalse(shouldReserveBannerSpace(readyState, AdBannerLoadState.LOADING))
        assertTrue(shouldReserveBannerSpace(readyState, AdBannerLoadState.LOADED))
        assertFalse(shouldReserveBannerSpace(readyState, AdBannerLoadState.FAILED))
        assertTrue(shouldApplyBannerNavigationInsets(readyState, AdBannerLoadState.LOADED))
        assertFalse(shouldApplyBannerNavigationInsets(readyState, AdBannerLoadState.LOADING))
    }

    @Test
    fun banner_isNotRequestedBeforeConsentAllowsAds() {
        assertFalse(shouldLoadBanner(AdsUiState()))
        assertFalse(
            shouldLoadBanner(
                AdsUiState(consentResolved = true, canRequestAds = false, mobileAdsInitialized = true)
            )
        )
        assertTrue(
            shouldLoadBanner(
                AdsUiState(consentResolved = true, canRequestAds = true, mobileAdsInitialized = true)
            )
        )
    }
}
