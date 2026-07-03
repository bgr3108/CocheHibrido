package com.example.cochehibrido.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.cochehibrido.viewmodel.HomeViewModel
import com.example.cochehibrido.util.toSpanishDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsumptionDetailScreen(
    viewModel: HomeViewModel,
    onBack: () -> Unit
) {

    val consumoGasolina by viewModel
        .consumoGasolina
        .collectAsStateWithLifecycle()

    val consumoElectrico by viewModel
        .consumoElectrico
        .collectAsStateWithLifecycle()

    Scaffold(

        topBar = {

            CenterAlignedTopAppBar(

                title = {
                    Text("Consumos")
                },

                navigationIcon = {

                    IconButton(
                        onClick = onBack
                    ) {

                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver"
                        )
                    }
                },

                colors = TopAppBarDefaults.centerAlignedTopAppBarColors()
            )
        }

    ) { innerPadding ->

        ConsumptionContent(
            innerPadding,
            consumoGasolina,
            consumoElectrico
        )

    }
}

@Composable
private fun ConsumptionContent(

    innerPadding: PaddingValues,

    consumoGasolina: Double,

    consumoElectrico: Double

) {

    Column(

        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(20.dp),

        verticalArrangement = Arrangement.spacedBy(20.dp)

    ) {

        Text(
            "Gasolina",
            style = MaterialTheme.typography.titleLarge
        )

        Text(
            "${consumoGasolina.toSpanishDecimal()} L/100 km",
            style = MaterialTheme.typography.headlineMedium
        )

        Text(
            "Gráfico próximamente",
            color = MaterialTheme.colorScheme.primary
        )

        Text(
            "Electricidad",
            style = MaterialTheme.typography.titleLarge
        )

        Text(
            "${consumoElectrico.toSpanishDecimal()} kWh/100 km",
            style = MaterialTheme.typography.headlineMedium
        )

        Text(
            "Gráfico próximamente",
            color = MaterialTheme.colorScheme.primary
        )
    }
}