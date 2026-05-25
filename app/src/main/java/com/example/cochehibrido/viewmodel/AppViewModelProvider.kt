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
                hybridCarApplication().container.fuelRepository,
                hybridCarApplication().container.carRepository
            )
        }

        initializer {
            HomeViewModel(
                hybridCarApplication().container.fuelRepository,
                hybridCarApplication().container.tripRepository,
                hybridCarApplication().container.baselineRepository,
                hybridCarApplication().container.vehicleRepository
            )
        }

        initializer {
            TripViewModel(
                hybridCarApplication().container.tripRepository
            )
        }
    }
}