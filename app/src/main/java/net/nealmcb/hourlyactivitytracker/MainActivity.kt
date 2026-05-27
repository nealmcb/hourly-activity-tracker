package net.nealmcb.hourlyactivitytracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.nealmcb.hourlyactivitytracker.ui.theme.HourlyActivityTheme

class MainActivity : ComponentActivity() {

    private val viewModel: HourlyActivityViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        if (granted.containsAll(HealthConnectManager.PERMISSIONS)) {
            viewModel.onPermissionsGranted()
        } else {
            // Some permissions denied; re-check state
            viewModel.checkAvailabilityAndLoad()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        viewModel.checkAvailabilityAndLoad()

        setContent {
            HourlyActivityTheme {
                val uiState = viewModel.uiState.collectAsStateWithLifecycle()
                HourlyActivityApp(
                    uiState = uiState.value,
                    onRequestPermissions = {
                        permissionLauncher.launch(HealthConnectManager.PERMISSIONS)
                    },
                    onRefresh = { viewModel.loadTodaySteps() }
                )
            }
        }
    }
}
