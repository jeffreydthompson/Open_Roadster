package org.openroadster.roadsterremote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.openroadster.roadsterremote.CentralControl
import org.openroadster.roadsterremote.ConnectionState

private enum class Screen { CarPicker, Driving }

private val backgroundGradient = Brush.verticalGradient(listOf(Color(0xFF666666), Color(0xFF333333)))

@Composable
fun ContentView(controller: CentralControl) {
    var screen by rememberSaveable { mutableStateOf(Screen.CarPicker) }

    val connectionState by controller.connectionState.collectAsStateWithLifecycle()
    val batteryLevel by controller.batteryLevel.collectAsStateWithLifecycle()
    val isOnUsbPower by controller.isOnUsbPower.collectAsStateWithLifecycle()
    val isConnected = connectionState is ConnectionState.Connected

    LaunchedEffect(connectionState) {
        // Lost the car mid-drive: go straight back to the search list
        if (connectionState is ConnectionState.Lost) screen = Screen.CarPicker
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(backgroundGradient)
    ) {
        Row(
            Modifier
                .safeDrawingPadding()
                .padding(15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GearView(gear = controller.gear, onGearChange = { controller.gear = it })

            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(15.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                FuelGaugeView(
                    batteryLevel = batteryLevel,
                    isCharging = isOnUsbPower,
                    modifier = Modifier.weight(1f),
                )

                Row(
                    Modifier
                        .height(110.dp)
                        .padding(15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HeadlightControlView(
                        isOn = controller.headlight,
                        onToggle = { controller.headlight = !controller.headlight },
                        modifier = Modifier.padding(16.dp),
                    )
                    ConnectionView(
                        connected = isConnected,
                        onClick = { screen = Screen.CarPicker },
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }

            ThrottleView(
                throttle = controller.throttle,
                onThrottleChange = { controller.throttle = it },
                modifier = Modifier
                    .width(165.dp)
                    .fillMaxHeight()
                    .shadow(4.dp, RoundedCornerShape(25.dp))
                    .clip(RoundedCornerShape(25.dp)),
            )
        }

        if (screen == Screen.CarPicker) {
            CarPickerView(controller, onDismiss = { screen = Screen.Driving })
        }
    }
}
