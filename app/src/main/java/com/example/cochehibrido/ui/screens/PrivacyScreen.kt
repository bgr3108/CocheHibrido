package com.example.cochehibrido.ui.screens

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.cochehibrido.ui.theme.CardBlueDark
import com.example.cochehibrido.ui.theme.CardBlueLight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen(
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text("Privacidad")
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors()
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Kilonom almacena los datos de tus vehículos y consumos localmente en tu dispositivo.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSystemInDarkTheme()) {
                        CardBlueDark
                    } else {
                        CardBlueLight
                    }
                ),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = 4.dp
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    PrivacySection(
                        title = "Tus datos",
                        description = "Kilonom no requiere una cuenta y no envía tus datos al desarrollador ni a servidores propios. La aplicación no utiliza publicidad, analítica ni sistemas de seguimiento."
                    )

                    HorizontalDivider()

                    PrivacySection(
                        title = "Copias de seguridad",
                        description = "Android puede incluir los datos de Kilonom en las copias de seguridad o transferencias entre dispositivos asociadas a tu cuenta de Google, dependiendo de la configuración de tu dispositivo."
                    )

                    HorizontalDivider()

                    PrivacySection(
                        title = "Eliminar datos",
                        description = "Puedes eliminar los datos almacenados por Kilonom utilizando la opción «Borrar todos los datos» disponible en la aplicación."
                    )

                    HorizontalDivider()

                    PrivacySection(
                        title = "Política de privacidad",
                        description = "La política de privacidad completa estará disponible antes de la publicación de Kilonom en Google Play."
                    )
                }
            }
        }
    }
}

@Composable
private fun PrivacySection(
    title: String,
    description: String
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
