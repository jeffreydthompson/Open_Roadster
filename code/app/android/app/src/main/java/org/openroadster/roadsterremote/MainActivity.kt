package org.openroadster.roadsterremote

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import org.openroadster.roadsterremote.ui.ContentView
import org.openroadster.roadsterremote.ui.RoadsterTheme

class MainActivity : ComponentActivity() {

    private val controller: CentralControl by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Full screen, like a game; swipe from an edge to bring the system bars back
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        setContent {
            RoadsterTheme {
                ContentView(controller)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Picks up permissions granted, or Bluetooth turned on, while the app was away
        controller.bleController.refreshState()
    }

    override fun onStop() {
        super.onStop()
        controller.pause()
    }
}
