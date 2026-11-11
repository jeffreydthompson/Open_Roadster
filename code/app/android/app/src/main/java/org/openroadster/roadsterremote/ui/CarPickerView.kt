// Scan for nearby Roadsters and pick one to drive.

package org.openroadster.roadsterremote.ui

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.SignalCellularAlt1Bar
import androidx.compose.material.icons.filled.SignalCellularAlt2Bar
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.openroadster.roadsterremote.BleController
import org.openroadster.roadsterremote.BluetoothState
import org.openroadster.roadsterremote.CentralControl
import org.openroadster.roadsterremote.ConnectionState
import org.openroadster.roadsterremote.DiscoveredCar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CarPickerView(controller: CentralControl, onDismiss: () -> Unit) {
    val connectionState by controller.connectionState.collectAsStateWithLifecycle()
    val bluetoothState by controller.bluetoothState.collectAsStateWithLifecycle()
    val cars by controller.cars.collectAsStateWithLifecycle()

    DisposableEffect(Unit) {
        controller.startScan()
        onDispose { controller.stopScan() }
    }

    LaunchedEffect(connectionState) {
        // Picked a car and it connected - get out of the way
        if (connectionState is ConnectionState.Connected) onDismiss()
    }

    BackHandler(onBack = onDismiss)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Select Your Roadster") },
                actions = { TextButton(onClick = onDismiss) { Text("Done") } },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (bluetoothState) {
                BluetoothState.Unsupported -> Unavailable(
                    Icons.Filled.BluetoothDisabled,
                    "Bluetooth Not Available",
                    "This device doesn't support Bluetooth Low Energy.",
                )
                BluetoothState.Unauthorized -> PermissionNeeded()
                BluetoothState.PoweredOff -> BluetoothOff()
                BluetoothState.LocationOff -> LocationOff()
                BluetoothState.Unknown, BluetoothState.PoweredOn ->
                    CarList(controller, connectionState, cars)
            }
        }
    }
}

@Composable
private fun CarList(controller: CentralControl, connectionState: ConnectionState, cars: List<DiscoveredCar>) {
    // The connected car stops advertising, so it's shown separately
    val nearbyCars = cars.filter { it.id != connectionState.car?.id }
    val isConnecting = connectionState is ConnectionState.Connecting

    LazyColumn(Modifier.fillMaxSize()) {
        // A lost car shows up under Nearby again once it's back in range
        connectionState.car?.let { car ->
            item { SectionHeader("Current") }
            item { CurrentCarRow(controller, car, connectionState) }
        }

        item {
            SectionHeader("Nearby") {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
            }
        }
        if (nearbyCars.isEmpty()) {
            item {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                    Text(
                        "Make sure your Roadster is powered on and nearby.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        items(nearbyCars, key = { it.id }) { car ->
            CarRow(
                car,
                onClick = if (isConnecting) null else { { controller.connect(car) } },
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.weight(1f))
        trailing()
    }
}

@Composable
private fun CurrentCarRow(controller: CentralControl, car: DiscoveredCar, state: ConnectionState) {
    val isConnecting = state is ConnectionState.Connecting
    val isDisconnecting = state is ConnectionState.Disconnecting

    CarRow(car, showSignal = false) {
        if (isConnecting) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Text("Connecting…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Icon(Icons.Filled.CheckCircle, contentDescription = "Connected", tint = Color(0xFF34C759))
        }
        OutlinedButton(
            onClick = { controller.disconnect() },
            enabled = !isDisconnecting,
            modifier = Modifier.padding(start = 8.dp),
        ) {
            Text(if (isConnecting) "Cancel" else "Disconnect", color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun CarRow(
    car: DiscoveredCar,
    showSignal: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    ListItem(
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
        leadingContent = {
            Icon(Icons.Filled.DirectionsCar, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        },
        headlineContent = { Text(car.name) },
        supportingContent = { Text("ID ${car.shortId}", fontFamily = FontFamily.Monospace) },
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                trailing()
                if (showSignal) {
                    val icon = when {
                        car.signalStrength >= 0.75 -> Icons.Filled.SignalCellularAlt
                        car.signalStrength >= 0.5 -> Icons.Filled.SignalCellularAlt2Bar
                        else -> Icons.Filled.SignalCellularAlt1Bar
                    }
                    Icon(
                        icon,
                        contentDescription = "Signal strength ${(car.signalStrength * 100).toInt()} percent",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    )
}

@Composable
private fun Unavailable(
    icon: ImageVector,
    title: String,
    description: String,
    action: @Composable () -> Unit = {},
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Icon(icon, contentDescription = null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(
            description,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(420.dp),
        )
        action()
    }
}

@Composable
private fun BluetoothOff() {
    val enableBluetooth = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}
    Unavailable(
        Icons.Filled.BluetoothDisabled,
        "Bluetooth Is Off",
        "Turn on Bluetooth to find your Roadster.",
    ) {
        Button(onClick = { enableBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }) {
            Text("Turn On Bluetooth")
        }
    }
}

@Composable
private fun LocationOff() {
    val context = LocalContext.current
    Unavailable(
        Icons.Filled.LocationOff,
        "Location Is Off",
        "This version of Android needs Location turned on to find Bluetooth devices.",
    ) {
        Button(onClick = { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }) {
            Text("Open Location Settings")
        }
    }
}

@Composable
private fun PermissionNeeded() {
    val context = LocalContext.current
    // Once the user denies twice, Android stops showing the prompt; send them to Settings instead
    var askedOnce by remember { mutableStateOf(false) }
    val requestPermissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { askedOnce = true }

    // Ask straight away the first time, like iOS does on first launch
    LaunchedEffect(Unit) { requestPermissions.launch(BleController.requiredPermissions) }

    Unavailable(
        Icons.Filled.Lock,
        "Bluetooth Access Needed",
        "Allow Roadster Remote to find and connect to nearby devices.",
    ) {
        Button(onClick = {
            if (askedOnce) {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                )
            } else {
                requestPermissions.launch(BleController.requiredPermissions)
            }
        }) {
            Icon(Icons.Filled.Bluetooth, contentDescription = null, Modifier.padding(end = 8.dp))
            Text(if (askedOnce) "Open Settings" else "Allow")
        }
    }
}
