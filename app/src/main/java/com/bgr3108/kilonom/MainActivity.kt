package com.bgr3108.kilonom

// 🔥 ICONOS (esto es lo que te faltaba)


import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.bgr3108.kilonom.data.Vehicle
import com.bgr3108.kilonom.ui.navigation.HybridCarNavHost
import com.bgr3108.kilonom.ui.screens.SetupScreen
import com.bgr3108.kilonom.ui.theme.CocheHibridoTheme
import com.bgr3108.kilonom.util.ExternalLinks
import com.bgr3108.kilonom.util.openExternalUrl
import com.bgr3108.kilonom.viewmodel.AppViewModelProvider
import com.bgr3108.kilonom.viewmodel.FuelEntryViewModel
import com.bgr3108.kilonom.viewmodel.HomeViewModel
import com.bgr3108.kilonom.viewmodel.PeriodSummaryViewModel
import com.bgr3108.kilonom.viewmodel.MyVehiclesViewModel
import com.bgr3108.kilonom.viewmodel.MaintenanceViewModel
import com.bgr3108.kilonom.viewmodel.ResetState

class MainActivity : ComponentActivity() {

    private val fuelViewModel: FuelEntryViewModel by viewModels {
        AppViewModelProvider.Factory
    }

    private val homeViewModel: HomeViewModel by viewModels {
        AppViewModelProvider.Factory
    }

    private val periodSummaryViewModel: PeriodSummaryViewModel by viewModels {
        AppViewModelProvider.Factory
    }

    private val myVehiclesViewModel: MyVehiclesViewModel by viewModels {
        AppViewModelProvider.Factory
    }

    private val maintenanceViewModel: MaintenanceViewModel by viewModels {
        AppViewModelProvider.Factory
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            CocheHibridoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppContent(
                        fuelViewModel = fuelViewModel,
                        homeViewModel = homeViewModel,
                        periodSummaryViewModel = periodSummaryViewModel,
                        myVehiclesViewModel = myVehiclesViewModel,
                        maintenanceViewModel = maintenanceViewModel
                    )
                }
            }
        }
    }
}

@Composable
fun AppContent(
    fuelViewModel: FuelEntryViewModel,
    homeViewModel: HomeViewModel,
    periodSummaryViewModel: PeriodSummaryViewModel,
    myVehiclesViewModel: MyVehiclesViewModel,
    maintenanceViewModel: MaintenanceViewModel
) {
    val navController = rememberNavController()
    val isVehicleLoading by homeViewModel
        .isVehicleLoading
        .collectAsStateWithLifecycle()
    val vehicle by homeViewModel.vehicle.collectAsStateWithLifecycle()
    val resetState by homeViewModel.resetState.collectAsStateWithLifecycle()
    val showReleaseNotes by homeViewModel.showReleaseNotes.collectAsStateWithLifecycle()

    if (isVehicleLoading) {

        CircularProgressIndicator()

    } else if (shouldShowSetup(vehicle, resetState)) {

        SetupScreen(
            homeViewModel = homeViewModel,

            onDone = {
            }
        )

    } else {

        val navBackStackEntry by navController.currentBackStackEntryAsState()

        val currentRoute = navBackStackEntry?.destination?.route

        val showBottomBar = currentRoute in listOf(
            "home",
            "consumption",
            "stats",
            "stats/trends",
            "maintenance"
        )
        val navigateToTopLevel: (String) -> Unit = { route ->
            navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
        Scaffold(
            bottomBar = {
                if (showBottomBar) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        NavigationBarItem(
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            ),
                            selected = currentRoute == "home",
                            onClick = { navigateToTopLevel("home") },
                            icon = { Icon(Icons.Default.Home, contentDescription = null) },
                            label = { Text("Inicio") }
                        )

                        NavigationBarItem(
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            ),
                            selected = currentRoute == "consumption",
                            onClick = { navigateToTopLevel("consumption") },
                            icon = {
                                Icon(
                                    Icons.Default.LocalGasStation,
                                    contentDescription = null
                                )
                            },
                            label = { Text("Consumos") }
                        )
                        NavigationBarItem(
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            ),
                            selected = currentRoute == "stats" || currentRoute == "stats/trends",
                            onClick = { navigateToTopLevel("stats") },
                            icon = { Icon(Icons.Default.BarChart, contentDescription = null) },
                            label = { Text("Estadísticas") }
                        )
                        NavigationBarItem(
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            ),
                            selected = currentRoute == "maintenance",
                            onClick = { navigateToTopLevel("maintenance") },
                            icon = { Icon(Icons.Default.Build, contentDescription = null) },
                            label = { Text("Mantenimiento") }
                        )
                    }
                }
            }
        ) { innerPadding ->

            HybridCarNavHost(
                navController = navController,
                innerPadding = innerPadding,
                fuelViewModel = fuelViewModel,
                homeViewModel = homeViewModel,
                periodSummaryViewModel = periodSummaryViewModel,
                myVehiclesViewModel = myVehiclesViewModel,
                maintenanceViewModel = maintenanceViewModel
            )
        }

        if (showReleaseNotes) {
            ReleaseNotesDialog(onDismiss = homeViewModel::dismissReleaseNotes)
        }
    }
}

/** Keeps the current shell visible until a reset has completed successfully. */
internal fun shouldShowSetup(vehicle: Vehicle, resetState: ResetState): Boolean =
    vehicle.type == null && resetState == ResetState.IDLE

@Composable
private fun ReleaseNotesDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val instagramOpenError = remember { mutableStateOf(false) }
    val contentScrollState = rememberScrollState()
    val maxContentHeight = with(LocalDensity.current) {
        LocalWindowInfo.current.containerSize.height.toDp() * 0.45f
    }

    AlertDialog(
        onDismissRequest = {},
        title = {
            Text(stringResource(R.string.whats_new_title))
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier
                        .heightIn(max = maxContentHeight)
                        .verticalScroll(contentScrollState)
                ) {
                    Text(stringResource(R.string.whats_new_message))
                    if (instagramOpenError.value) {
                        Text(
                            text = "No se pudo abrir Instagram. Inténtalo de nuevo cuando tengas un navegador disponible.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                if (contentScrollState.maxValue > 0) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Desliza para ver más",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.whats_new_confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    instagramOpenError.value = !context.openExternalUrl(
                        ExternalLinks.INSTAGRAM_PROFILE_URL
                    )
                }
            ) {
                Text(stringResource(R.string.whats_new_instagram))
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
