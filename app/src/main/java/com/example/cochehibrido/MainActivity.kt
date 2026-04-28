package com.example.cochehibrido

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.compose.rememberNavController

import com.example.cochehibrido.ui.navigation.HybridCarNavHost
import com.example.cochehibrido.viewmodel.AppViewModelProvider
import com.example.cochehibrido.viewmodel.FuelEntryViewModel
import com.example.cochehibrido.viewmodel.HomeViewModel

class MainActivity : ComponentActivity() {

    private val fuelViewModel: FuelEntryViewModel by viewModels {
        AppViewModelProvider.Factory
    }

    private val homeViewModel: HomeViewModel by viewModels {
        AppViewModelProvider.Factory
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AppContent(
                fuelViewModel = fuelViewModel,
                homeViewModel = homeViewModel
            )
        }
    }
}

@Composable
fun AppContent(
    fuelViewModel: FuelEntryViewModel,
    homeViewModel: HomeViewModel
) {
    val navController = rememberNavController()

    Scaffold { innerPadding ->

        HybridCarNavHost(
            navController = navController,
            innerPadding = innerPadding,
            fuelViewModel = fuelViewModel,
            homeViewModel = homeViewModel
        )
    }
}