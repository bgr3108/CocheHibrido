package com.bgr3108.kilonom

import android.app.Application
import com.bgr3108.kilonom.data.AppContainer

class HybridCarApplication : Application() {

    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
