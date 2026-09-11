package com.bgr3108.kilonom

import android.app.Application
import com.bgr3108.kilonom.ads.AdsManager
import com.bgr3108.kilonom.data.AppContainer

class HybridCarApplication : Application() {

    lateinit var container: AppContainer
    internal lateinit var adsManager: AdsManager

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        adsManager = AdsManager(this)
        if (BuildConfig.DEBUG && BuildConfig.RESET_UMP_CONSENT_FOR_DEBUG) {
            adsManager.resetConsentForDebugTesting()
        }
    }
}
