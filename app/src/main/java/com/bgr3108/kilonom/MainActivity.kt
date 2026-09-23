package com.bgr3108.kilonom

// 🔥 ICONOS (esto es lo que te faltaba)


import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.material3.rememberDrawerState
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
import com.bgr3108.kilonom.ads.AdsManager
import com.bgr3108.kilonom.ui.components.AdBannerSlot
import com.bgr3108.kilonom.ui.components.routeUsesAdBannerSlot
import com.bgr3108.kilonom.ui.navigation.DrawerAction
import com.bgr3108.kilonom.ui.navigation.HybridCarNavHost
import com.bgr3108.kilonom.ui.navigation.KilonomNavigationDrawerContent
import com.bgr3108.kilonom.ui.navigation.drawerGesturesEnabled
import com.bgr3108.kilonom.ui.navigation.drawerRouteTitle
import com.bgr3108.kilonom.ui.navigation.shouldCloseDrawerOnBack
import com.bgr3108.kilonom.ui.navigation.shouldNavigateToDrawerRoute
import com.bgr3108.kilonom.ui.navigation.showsVehicleTopOverflow
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
import com.bgr3108.kilonom.viewmodel.StationsViewModel
import com.bgr3108.kilonom.viewmodel.ResetState
import kotlinx.coroutines.launch

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

    private val stationsViewModel: StationsViewModel by viewModels {
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
                        maintenanceViewModel = maintenanceViewModel,
                        stationsViewModel = stationsViewModel,
                        adsManager = (application as HybridCarApplication).adsManager
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun AppContent(
    fuelViewModel: FuelEntryViewModel,
    homeViewModel: HomeViewModel,
    periodSummaryViewModel: PeriodSummaryViewModel,
    myVehiclesViewModel: MyVehiclesViewModel,
    maintenanceViewModel: MaintenanceViewModel,
    stationsViewModel: StationsViewModel,
    adsManager: AdsManager
) {
    val navController = rememberNavController()
    val isVehicleLoading by homeViewModel
        .isVehicleLoading
        .collectAsStateWithLifecycle()
    val vehicle by homeViewModel.vehicle.collectAsStateWithLifecycle()
    val resetState by homeViewModel.resetState.collectAsStateWithLifecycle()
    val showReleaseNotes by homeViewModel.showReleaseNotes.collectAsStateWithLifecycle()
    val adsUiState by adsManager.uiState.collectAsStateWithLifecycle()
    val activity = LocalActivity.current

    LaunchedEffect(activity) {
        activity?.let(adsManager::requestConsent)
    }

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
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        val context = LocalContext.current
        val snackbarHostState = remember { SnackbarHostState() }
        val showVehicleOverflow = remember { mutableStateOf(false) }
        val showResetDialog = remember { mutableStateOf(false) }
        val resetRequested = remember { mutableStateOf(false) }
        val stationsUiState by stationsViewModel.uiState.collectAsStateWithLifecycle()
        val stationsRefreshing by stationsViewModel.activeContentRefreshing.collectAsStateWithLifecycle()
        LaunchedEffect(resetRequested.value, resetState) {
            if (resetRequested.value && resetState == ResetState.IDLE) {
                showResetDialog.value = false
                resetRequested.value = false
            }
        }
        val navigateToTopLevel: (String) -> Unit = { route ->
            if (shouldNavigateToDrawerRoute(currentRoute, route)) {
                navController.navigate(route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }
        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = drawerGesturesEnabled(currentRoute, stationsUiState.viewMode),
            drawerContent = {
                KilonomNavigationDrawerContent(
                    currentRoute = currentRoute,
                    showAdPrivacyOptions = adsUiState.privacyOptionsRequired,
                    onItemSelected = { item ->
                        scope.launch {
                            drawerState.close()
                            when (item.action) {
                                DrawerAction.NAVIGATE -> item.route?.let(navigateToTopLevel)
                                DrawerAction.AD_PRIVACY_OPTIONS -> activity?.let(adsManager::showPrivacyOptions)
                                DrawerAction.INSTAGRAM -> {
                                    if (!context.openExternalUrl(ExternalLinks.INSTAGRAM_PROFILE_URL)) {
                                        snackbarHostState.showSnackbar(
                                            "No se pudo abrir Instagram. Inténtalo de nuevo cuando tengas un navegador disponible."
                                        )
                                    }
                                }
                            }
                        }
                    }
                )
            }
        ) {
            Scaffold(
                topBar = {
                    drawerRouteTitle(currentRoute)?.let { title ->
                        TopAppBar(
                            title = { Text(title) },
                            navigationIcon = {
                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                    Icon(Icons.Default.Menu, contentDescription = "Abrir menú de navegación")
                                }
                            },
                            actions = {
                                if (showsVehicleTopOverflow(currentRoute)) {
                                    Box {
                                        IconButton(onClick = { showVehicleOverflow.value = true }) {
                                            Icon(Icons.Default.MoreVert, contentDescription = "Acciones de datos")
                                        }
                                        DropdownMenu(
                                            expanded = showVehicleOverflow.value,
                                            onDismissRequest = { showVehicleOverflow.value = false }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("Borrar todos los datos") },
                                                onClick = {
                                                    showVehicleOverflow.value = false
                                                    showResetDialog.value = true
                                                }
                                            )
                                        }
                                    }
                                }
                                if (currentRoute == "stations") {
                                    IconButton(
                                        onClick = stationsViewModel::refreshActiveContent,
                                        enabled = !stationsRefreshing
                                    ) {
                                        if (stationsRefreshing) {
                                            CircularProgressIndicator(modifier = Modifier.height(24.dp), strokeWidth = 2.dp)
                                        } else {
                                            Icon(Icons.Default.Refresh, contentDescription = "Actualizar estaciones")
                                        }
                                    }
                                }
                            }
                        )
                    }
                },
                bottomBar = {
                    if (routeUsesAdBannerSlot(currentRoute)) {
                        AdBannerSlot(adsUiState = adsUiState)
                    }
                },
                snackbarHost = { SnackbarHost(snackbarHostState) }
            ) { innerPadding ->
                HybridCarNavHost(
                    navController = navController,
                    innerPadding = innerPadding,
                    fuelViewModel = fuelViewModel,
                    homeViewModel = homeViewModel,
                    periodSummaryViewModel = periodSummaryViewModel,
                    myVehiclesViewModel = myVehiclesViewModel,
                    maintenanceViewModel = maintenanceViewModel,
                    stationsViewModel = stationsViewModel,
                    onNavigateTopLevel = navigateToTopLevel
                )
                // Scaffold content is subcomposed. Register after the NavHost in that same
                // composition so an open drawer consumes Back before navigation can change route.
                BackHandler(enabled = shouldCloseDrawerOnBack(drawerState.isOpen)) {
                    scope.launch { drawerState.close() }
                }
            }
        }

        if (showResetDialog.value) {
            ResetAllDataDialog(
                resetState = resetState,
                onReset = {
                    resetRequested.value = true
                    homeViewModel.resetApplication()
                },
                onDismiss = {
                    homeViewModel.dismissResetError()
                    showResetDialog.value = false
                }
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
private fun ResetAllDataDialog(
    resetState: ResetState,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (resetState != ResetState.LOADING) onDismiss() },
        title = { Text("Borrar todos los datos") },
        text = {
            if (resetState == ResetState.ERROR) {
                Text("No se pudieron borrar todos los datos. Inténtalo de nuevo.")
            } else {
                Text("Se eliminarán todos los vehículos y consumos guardados en Kilonom. Esta acción no se puede deshacer.")
            }
        },
        confirmButton = {
            TextButton(enabled = resetState != ResetState.LOADING, onClick = onReset) {
                if (resetState == ResetState.LOADING) {
                    CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Borrar todos")
                }
            }
        },
        dismissButton = {
            TextButton(enabled = resetState != ResetState.LOADING, onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

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
