package com.example.cochehibrido.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.compose.foundation.isSystemInDarkTheme
import com.example.cochehibrido.ui.theme.CardBlueLight
import com.example.cochehibrido.ui.theme.CardBlueDark

import com.example.cochehibrido.data.Trip
import com.example.cochehibrido.viewmodel.TripViewModel
import com.example.cochehibrido.viewmodel.HomeViewModel
import com.example.cochehibrido.util.toDateTimeString
import com.example.cochehibrido.util.toSpanishDecimal



@Composable
fun TripListScreen(
    innerPadding: PaddingValues,
    viewModel: TripViewModel,
    homeViewModel: HomeViewModel,
    navController: NavController, // 🔥 ESTE ES EL CAMBIO CLAVE
    onAddClick: () -> Unit
) {
    val trips by viewModel.trips.collectAsStateWithLifecycle()
    val isDark = isSystemInDarkTheme()
    val precioGasolina by homeViewModel.precioGasolina.collectAsStateWithLifecycle()
    val precioElectrico by homeViewModel.precioElectrico.collectAsStateWithLifecycle()

    var tripToDelete by remember { mutableStateOf<Trip?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(16.dp)
    ) {

        Text("Viajes", style = MaterialTheme.typography.headlineSmall)

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onAddClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Añadir viaje")
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (trips.isEmpty()) {
            Text("No hay viajes")
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(trips, key = { it.id }) { trip ->

                val costeGasolina =
                    (trip.km / 100.0) * trip.consumoGasolina * precioGasolina

                val costeElectrico =
                    (trip.km / 100.0) * trip.consumoElectrico * precioElectrico

                val costeTotal = costeGasolina + costeElectrico
                val costeKm = if (trip.km > 0) costeTotal / trip.km else 0.0

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDark) CardBlueDark else CardBlueLight
                    ),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {

                        Text(trip.fecha.toDateTimeString())
                        Text("Km: ${trip.km.toSpanishDecimal()}")
                        Text("Gasolina: ${trip.consumoGasolina.toSpanishDecimal()}")
                        Text("Eléctrico: ${trip.consumoElectrico.toSpanishDecimal()}")

                        Spacer(modifier = Modifier.height(8.dp))

                        if (precioGasolina == 0.0 && precioElectrico == 0.0) {
                            Text("⚠️ Añade repostajes para calcular coste")
                        } else {
                            Text("💰 Coste: %.2f €".format(costeTotal))
                            Text("€/km: %.3f €".format(costeKm))
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {

                            // ✏️ EDITAR
                            Button(
                                onClick = {
                                    navController.navigate("edit_trip/${trip.id}")
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Editar")
                            }

                            // 🗑 ELIMINAR
                            OutlinedButton(
                                onClick = { tripToDelete = trip },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Eliminar")
                            }
                        }
                    }
                }
            }
        }
    }

    // 🔥 CONFIRMACIÓN ELIMINAR
    tripToDelete?.let { trip ->
        AlertDialog(
            onDismissRequest = { tripToDelete = null },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteTrip(trip)
                    tripToDelete = null
                }) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { tripToDelete = null }) {
                    Text("Cancelar")
                }
            },
            title = { Text("Eliminar viaje") },
            text = { Text("¿Seguro que quieres eliminar este viaje?") }
        )
    }
}