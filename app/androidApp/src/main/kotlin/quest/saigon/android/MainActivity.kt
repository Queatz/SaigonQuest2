package quest.saigon.android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import quest.saigon.android.theme.SaigonTheme

class MainActivity : ComponentActivity() {
    private var pendingRoutePath by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingRoutePath = RoutePaths.resolveRouteFromIntent(intent)?.let(RoutePaths::toPath)

        val app = application as SaigonApplication
        setContent {
            val sessionState by app.shared.session.state.collectAsState()
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { granted ->
                if (granted) app.refreshFcmTokenSafely()
            }

            LaunchedEffect(sessionState.isSignedIn) {
                if (sessionState.isSignedIn && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val granted = ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.POST_NOTIFICATIONS,
                    ) == PackageManager.PERMISSION_GRANTED
                    if (!granted) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        app.refreshFcmTokenSafely()
                    }
                }
            }

            SaigonTheme {
                SaigonApp(
                    shared = app.shared,
                    audioPlayer = app.audioPlayer,
                    pendingRoutePath = pendingRoutePath,
                    onConsumePendingRoutePath = { pendingRoutePath = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        RoutePaths.resolveRouteFromIntent(intent)?.let { route ->
            pendingRoutePath = RoutePaths.toPath(route)
        }
    }
}
