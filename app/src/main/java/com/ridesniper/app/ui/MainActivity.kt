package com.ridesniper.app.ui

import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ridesniper.app.RideSniperApp
import com.ridesniper.app.service.RideSniperForegroundService
import com.ridesniper.app.ui.screens.*
import com.ridesniper.app.ui.theme.RideSniperTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: RideSniperViewModel by viewModels {
        RideSniperViewModel.Factory(application as RideSniperApp)
    }

    // Re-checked in onResume, because the driver grants this in Android's Settings app
    // and comes back; checking only once left the "permission needed" card stuck.
    private val overlayGranted = mutableStateOf(false)

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* onResume re-checks */ }

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val serviceIntent = Intent(this, RideSniperForegroundService::class.java).apply {
                action = RideSniperForegroundService.ACTION_START
                putExtra(RideSniperForegroundService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(RideSniperForegroundService.EXTRA_RESULT_DATA, result.data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            viewModel.notify("Ride Sniper is on. Open Uber Driver and tap the bubble on an offer.")
        } else {
            viewModel.notify("Screen reading wasn't allowed, so Ride Sniper can't read offers.")
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* proceed regardless; notification is best-effort on Android 13+ */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Hold the splash until saved settings are loaded, so the first frame is real content.
        splash.setKeepOnScreenCondition { !viewModel.isReady.value }

        RideSniperForegroundService.onNeedsManualCorrection = { parsed ->
            runOnUiThread { viewModel.requestManualCorrection(parsed) }
        }

        setContent {
            RideSniperTheme {
                RideSniperRoot(
                    viewModel = viewModel,
                    hasOverlayPermission = overlayGranted.value,
                    onRequestOverlayPermission = { requestOverlayPermission() },
                    onStartAnalyzerService = { requestNotificationPermissionThenProjection() },
                    onStopAnalyzerService = { stopAnalyzerService() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val granted = Settings.canDrawOverlays(this)
        if (granted && !overlayGranted.value) {
            // Only announce a change, not every resume.
            if (overlayPermissionRequested) viewModel.notify("Bubble allowed. You're ready to start.")
        }
        overlayGranted.value = granted
    }

    private var overlayPermissionRequested = false

    private fun requestOverlayPermission() {
        overlayPermissionRequested = true
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        overlayPermissionLauncher.launch(intent)
    }

    private fun requestNotificationPermissionThenProjection() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ActivityCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
            if (granted != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projectionLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    private fun stopAnalyzerService() {
        val intent = Intent(this, RideSniperForegroundService::class.java).apply {
            action = RideSniperForegroundService.ACTION_STOP
        }
        startService(intent)
        viewModel.notify("Ride Sniper stopped")
    }
}

private const val NAV_IN_MILLIS = 240
private const val NAV_OUT_MILLIS = 140

@Composable
fun RideSniperRoot(
    viewModel: RideSniperViewModel,
    hasOverlayPermission: Boolean,
    onRequestOverlayPermission: () -> Unit,
    onStartAnalyzerService: () -> Unit,
    onStopAnalyzerService: () -> Unit
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            // Newest message wins; don't queue a backlog of old confirmations.
            snackbarHostState.currentSnackbarData?.dismiss()
            launch { snackbarHostState.showSnackbar(message) }
        }
    }

    // The whole app eases in once after the splash screen, instead of appearing all at once.
    val appear = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(
        visibleState = appear,
        enter = fadeIn(tween(420)) + slideInVertically(tween(420)) { it / 30 }
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = {
                SnackbarHost(snackbarHostState) { data ->
                    Snackbar(
                        snackbarData = data,
                        shape = MaterialTheme.shapes.medium,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            bottomBar = { RideSniperBottomBar(navController) }
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = "home",
                modifier = Modifier.padding(padding),
                enterTransition = { fadeIn(tween(NAV_IN_MILLIS)) + slideInVertically(tween(NAV_IN_MILLIS)) { it / 40 } },
                exitTransition = { fadeOut(tween(NAV_OUT_MILLIS)) },
                popEnterTransition = { fadeIn(tween(NAV_IN_MILLIS)) },
                popExitTransition = { fadeOut(tween(NAV_OUT_MILLIS)) }
            ) {
                composable("home") {
                    HomeScreen(
                        viewModel = viewModel,
                        hasOverlayPermission = hasOverlayPermission,
                        onRequestOverlayPermission = onRequestOverlayPermission,
                        onStartAnalyzer = onStartAnalyzerService,
                        onStopAnalyzer = onStopAnalyzerService
                    )
                }
                composable("history") { HistoryScreen(viewModel) }
                composable("stats") { StatsScreen(viewModel) }
                composable("settings") {
                    SettingsScreen(viewModel, onOpenDebug = { navController.navigate("debug") })
                }
                composable("debug") { DebugScreen(onBack = { navController.popBackStack() }) }
            }
        }
    }

    val pendingCorrection by viewModel.pendingCorrection.collectAsState()
    pendingCorrection?.let { parsed ->
        CorrectionSheet(
            parsed = parsed,
            onDismiss = { viewModel.dismissManualCorrection() },
            onCalculate = { input -> viewModel.submitManualCorrection(input) }
        )
    }
}

@Composable
private fun RideSniperBottomBar(navController: NavController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val current = backStackEntry?.destination?.route

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        val items = listOf(
            Triple("home", "Home", Icons.Filled.Home),
            Triple("history", "History", Icons.Filled.History),
            Triple("stats", "Stats", Icons.Filled.BarChart),
            Triple("settings", "Settings", Icons.Filled.Settings)
        )
        items.forEach { (route, label, icon) ->
            NavigationBarItem(
                // Debug is reached from Settings, so keep the Settings tab lit while on it.
                selected = current == route || (route == "settings" && current == "debug"),
                onClick = {
                    navController.navigate(route) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(icon, contentDescription = label) },
                label = { Text(label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}
