package com.bgr3108.kilonom.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.bgr3108.kilonom.BuildConfig
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Coordinates UMP and Mobile Ads without retaining an Activity or storing consent locally.
 * Ad requests only begin after UMP resolves consent and the current build enables advertising.
 */
internal class AdsManager(context: Context) {

    private val applicationContext = context.applicationContext
    // Keeping this lazy avoids touching UMP until the consent flow is actually started.
    private val consentInformation by lazy {
        UserMessagingPlatform.getConsentInformation(applicationContext)
    }
    private val consentRequested = AtomicBoolean(false)
    private val mobileAdsInitializationRequested = AtomicBoolean(false)
    private val _uiState = MutableStateFlow(AdsUiState())

    val uiState: StateFlow<AdsUiState> = _uiState.asStateFlow()

    fun requestConsent(activity: Activity) {
        if (!BuildConfig.ADS_ENABLED || !consentRequested.compareAndSet(false, true)) return

        Log.d(TAG, "Requesting UMP consent information")
        consentInformation.requestConsentInfoUpdate(
            activity,
            consentRequestParameters(),
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                    publishConsentState()
                }
            },
            {
                // A network failure never blocks Kilonom. UMP may still authorize ads using
                // previously stored consent; otherwise the slot remains collapsed.
                Log.d(TAG, "UMP update unavailable; evaluating the saved consent state")
                publishConsentState()
            }
        )
    }

    fun showPrivacyOptions(activity: Activity) {
        if (!uiState.value.privacyOptionsRequired) return

        UserMessagingPlatform.showPrivacyOptionsForm(activity) {
            publishConsentState()
        }
    }

    private fun consentRequestParameters(): ConsentRequestParameters {
        val builder = ConsentRequestParameters.Builder()
        val testDeviceHashedId = BuildConfig.UMP_TEST_DEVICE_HASHED_ID
        if (BuildConfig.DEBUG && testDeviceHashedId.isNotBlank()) {
            val debugSettings = ConsentDebugSettings.Builder(applicationContext)
                .addTestDeviceHashedId(testDeviceHashedId)
                .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
                .build()
            builder.setConsentDebugSettings(debugSettings)
        }
        return builder.build()
    }

    private fun publishConsentState() {
        val canRequestAds = consentInformation.canRequestAds()
        val privacyOptionsRequired = consentInformation.privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
        _uiState.update {
            it.copy(
                consentResolved = true,
                canRequestAds = canRequestAds,
                privacyOptionsRequired = privacyOptionsRequired
            )
        }
        Log.d(
            TAG,
            "UMP resolved: status=${consentInformation.consentStatus}, " +
                "canRequestAds=$canRequestAds, privacyOptionsRequired=$privacyOptionsRequired"
        )
        if (shouldInitializeMobileAds(_uiState.value)) initializeMobileAds()
    }

    private fun initializeMobileAds() {
        if (!mobileAdsInitializationRequested.compareAndSet(false, true)) return

        Log.d(TAG, "Initializing Mobile Ads")
        Thread {
            MobileAds.initialize(applicationContext) {
                _uiState.update { it.copy(mobileAdsInitialized = true) }
                Log.d(TAG, "Mobile Ads initialized")
            }
        }.start()
    }

    internal fun resetConsentForDebugTesting() {
        if (BuildConfig.DEBUG) {
            consentInformation.reset()
            consentRequested.set(false)
            mobileAdsInitializationRequested.set(false)
            _uiState.value = AdsUiState()
            Log.d(TAG, "UMP consent reset for debug testing")
        }
    }

    private companion object {
        const val TAG = "KilonomAds"
    }
}
