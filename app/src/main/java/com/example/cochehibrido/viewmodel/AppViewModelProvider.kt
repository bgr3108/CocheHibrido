package com.example.cochehibrido.viewmodel

import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory

object AppViewModelProvider {

    val Factory = viewModelFactory {

        initializer {
            CarViewModel(
                hybridCarApplication().container.carRepository
            )
        }

        initializer {
            FuelEntryViewModel(
                hybridCarApplication().container.fuelRepository
            )
        }

        initializer {
            HomeViewModel(
                hybridCarApplication().container.fuelRepository,
                hybridCarApplication().container.carRepository,
                hybridCarApplication().container.vehicleRepository
            )
        }

    }
}
