package com.bgr3108.kilonom.ads

/** UI-facing state only; it contains no consent string or user data. */
internal data class AdsUiState(
    val consentResolved: Boolean = false,
    val canRequestAds: Boolean = false,
    val mobileAdsInitialized: Boolean = false,
    val privacyOptionsRequired: Boolean = false
)

internal enum class AdBannerLoadState {
    LOADING,
    LOADED,
    FAILED
}

internal fun shouldInitializeMobileAds(state: AdsUiState): Boolean =
    state.consentResolved && state.canRequestAds && !state.mobileAdsInitialized

internal fun shouldLoadBanner(state: AdsUiState): Boolean =
    state.consentResolved && state.canRequestAds && state.mobileAdsInitialized

internal fun shouldReserveBannerSpace(
    state: AdsUiState,
    bannerLoadState: AdBannerLoadState
): Boolean = shouldLoadBanner(state) && bannerLoadState == AdBannerLoadState.LOADED
