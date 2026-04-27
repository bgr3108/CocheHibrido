package com.example.cochehibrido

import android.app.Application
import com.example.cochehibrido.data.AppContainer

class HybridCarApplication : Application() {

    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}