package pl.watershed.septictank

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import pl.watershed.septictank.ui.AppNavHost
import pl.watershed.septictank.ui.Routes

class MainActivity : ComponentActivity() {
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* FR-013: reminders still work even if denied -- they just won't show a system notification. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val container = (application as SepticTankApplication).container
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier) {
                    AppRoot(container)
                }
            }
        }
    }
}

/**
 * Determines the start screen: initial setup (Onboarding) if the tank capacity hasn't been
 * provided yet (US4, Acceptance Scenario 1), otherwise the home screen.
 */
@Composable
private fun AppRoot(container: AppContainer) {
    var startDestination by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val configuration = container.tankConfigurationRepository.get()
        startDestination = if (configuration.capacityLiters == null) Routes.ONBOARDING else Routes.HOME
    }

    startDestination?.let { destination ->
        Box {
            AppNavHost(startDestination = destination)
        }
    }
}
