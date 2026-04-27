package com.example.cochehibrido.ui.navigation

sealed class AppDestination(val route: String) {

    object Home : AppDestination("home")

    object Car : AppDestination("car")

    object Refuels : AppDestination("refuels")

    object Trips : AppDestination("trips")

    object AddTrip : AppDestination("add_trip")

    object AddRefuel : AppDestination("add_refuel")
}