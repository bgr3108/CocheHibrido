package com.example.cochehibrido.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class BaselineRepository(context: Context) {

    private val prefs = context.getSharedPreferences("baseline", Context.MODE_PRIVATE)

    private val _baseline = MutableStateFlow(
        VehicleBaseline(
            kmInicial = prefs.getFloat("kmInicial", 0f).toDouble(),
            consumoGasolinaInicial = prefs.getFloat("consumoGasolina", 0f).toDouble(),
            consumoElectricoInicial = prefs.getFloat("consumoElectrico", 0f).toDouble()
        )
    )

    val baseline: StateFlow<VehicleBaseline> = _baseline

    fun saveBaseline(km: Double, gas: Double, elec: Double) {
        prefs.edit()
            .putFloat("kmInicial", km.toFloat())
            .putFloat("consumoGasolina", gas.toFloat())
            .putFloat("consumoElectrico", elec.toFloat())
            .apply()

        _baseline.value = VehicleBaseline(km, gas, elec)
    }
}