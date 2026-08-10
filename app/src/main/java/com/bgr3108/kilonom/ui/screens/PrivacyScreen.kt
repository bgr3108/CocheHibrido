package com.bgr3108.kilonom.ui.screens

import android.content.Intent
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.compose.ui.unit.dp
import com.bgr3108.kilonom.ui.theme.CardBlueDark
import com.bgr3108.kilonom.ui.theme.CardBlueLight

private const val PRIVACY_POLICY_URL =
    "https://bgr3108.github.io/CocheHibrido/privacy/"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val policyOpenError = remember { mutableStateOf(false) }

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

                    PrivacyPolicySection(
                        onOpenPolicy = {
                            policyOpenError.value = runCatching {
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
          PRIVACY_POLICY_URL.toUri()
                                    )
                                )
                            }.isFailure
                        },
                        showOpenError = policyOpenError.value
                    )
                }
            }
        }
    }
}

@Composable
private fun PrivacyPolicySection(
    onOpenPolicy: () -> Unit,
    showOpenError: Boolean
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "Política de privacidad",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        TextButton(
            onClick = onOpenPolicy,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.textButtonColors(
                contentColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text("Ver política de privacidad completa")
        }
        if (showOpenError) {
            Text(
                text = "No se pudo abrir la política de privacidad. Inténtalo de nuevo cuando tengas un navegador disponible.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
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
