package com.bgr3108.kilonom.ui.components

import android.util.Log
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import com.bgr3108.kilonom.BuildConfig
import com.bgr3108.kilonom.ads.AdBannerLoadState
import com.bgr3108.kilonom.ads.AdsUiState
import com.bgr3108.kilonom.ads.shouldLoadBanner
import com.bgr3108.kilonom.ads.shouldReserveBannerSpace
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

/** Routes hosted by the main scaffold, where a future banner can be displayed safely. */
internal fun routeUsesAdBannerSlot(route: String?): Boolean = route in setOf(
    "home",
    "consumption",
    "stats",
    "stats/trends",
    "maintenance"
)

/**
 * Displays an anchored adaptive test banner only after UMP authorizes ad requests.
 *
 * It is a sibling of the bottom navigation in the app scaffold, so visible banner height is
 * included in the content insets instead of overlaying scrollable content or floating actions.
 * A failed or unavailable request collapses to zero height without user-facing errors.
 */
@Composable
internal fun AdBannerSlot(
    adsUiState: AdsUiState,
    modifier: Modifier = Modifier
) {
    if (!BuildConfig.ADS_ENABLED || !shouldLoadBanner(adsUiState)) return

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val context = LocalContext.current
        val density = LocalDensity.current
        val adWidthDp = maxWidth.value.toInt()
        if (adWidthDp <= 0) return@BoxWithConstraints

        key(adWidthDp) {
            val adSize = remember(adWidthDp) {
                AdSize.getLargeAnchoredAdaptiveBannerAdSize(context, adWidthDp)
            }
            val visibleHeight = with(density) {
                adSize.getHeightInPixels(context).toDp()
            }
            var loadState by remember { mutableStateOf(AdBannerLoadState.LOADING) }
            val adView = remember(adSize) {
                AdView(context).apply {
                    adUnitId = BuildConfig.ADMOB_BANNER_AD_UNIT_ID
                    setAdSize(adSize)
                    adListener = object : AdListener() {
                        override fun onAdLoaded() {
                            loadState = AdBannerLoadState.LOADED
                            Log.d(TAG, "Test banner loaded")
                        }

                        override fun onAdFailedToLoad(error: com.google.android.gms.ads.LoadAdError) {
                            loadState = AdBannerLoadState.FAILED
                            Log.d(TAG, "Test banner unavailable: ${error.code}")
                        }
                    }
                    loadAd(AdRequest.Builder().build())
                }
            }

            DisposableEffect(adView) {
                onDispose { adView.destroy() }
            }

            if (loadState != AdBannerLoadState.FAILED) {
                AndroidView(
                    factory = { adView },
                    modifier = Modifier
                        .fillMaxWidth()
                        // While loading, retain only an imperceptible attachment point. The
                        // full adaptive height is reserved exclusively after an ad is visible.
                        .height(
                            if (shouldReserveBannerSpace(adsUiState, loadState)) {
                                visibleHeight
                            } else {
                                androidx.compose.ui.unit.Dp.Hairline
                            }
                        )
                )
            }
        }
    }
}

private const val TAG = "KilonomAds"
