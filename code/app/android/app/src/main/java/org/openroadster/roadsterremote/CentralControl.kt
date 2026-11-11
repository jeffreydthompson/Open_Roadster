package org.openroadster.roadsterremote

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/*
 Control packets go out continuously at this rate. The car stops its motors if they stop arriving for 500 ms, so keep this well above 2 Hz.
 */
private const val COMMAND_INTERVAL_MS = 1_000L / 20

enum class Gear { Park, Reverse, Drive }

class CentralControl(application: Application) : AndroidViewModel(application) {

    val bleController = BleController(application)
    private val tiltSensor = TiltSensor(application) { pitch = it }

    // state
    var gear by mutableStateOf(Gear.Park)

    val connectionState: StateFlow<ConnectionState> = bleController.connectionState
    val batteryLevel: StateFlow<Int> = bleController.batteryLevel
    val isOnUsbPower: StateFlow<Boolean> = bleController.onUsbPower
    val cars: StateFlow<List<DiscoveredCar>> = bleController.cars
    val bluetoothState: StateFlow<BluetoothState> = bleController.bluetoothState
    val isConnected: Boolean
        get() = connectionState.value is ConnectionState.Connected

    // UI Controls
    private var pitch = 0.0
        set(value) {
            field = value
            steering = (value * -100).toInt().toShort()
        }
    var throttle by mutableIntStateOf(0)
    // map 0-100 UI value to 0-255 microcontroller value
    private val motorThrottle: Int
        get() = (throttle * 255) / 100
    private var steering: Short = 0
    var headlight by mutableStateOf(false)

    init {
        viewModelScope.launch {
            connectionState.collect { state ->
                // Safety: never resume driving on reconnect
                if (state !is ConnectionState.Connected) gear = Gear.Park
            }
        }

        tiltSensor.beginUpdates()

        viewModelScope.launch {
            while (isActive) {
                sendCommand()
                delay(COMMAND_INTERVAL_MS)
            }
        }
    }

    fun startScan() = bleController.startScan()

    fun stopScan() = bleController.stopScan()

    fun connect(car: DiscoveredCar) = bleController.connect(car)

    fun disconnect() = bleController.disconnect()

    /** The app left the screen: stop the car rather than keep its last throttle */
    fun pause() {
        gear = Gear.Park
        throttle = 0
    }

    private fun sendCommand() {
        if (!isConnected) return

        val cmdThrottle = when (gear) {
            // Keep sending in Park: it's what stops the car, and headlights still work
            Gear.Park -> 0
            Gear.Reverse -> -motorThrottle
            Gear.Drive -> motorThrottle
        }

        val cmd = BleCommand(
            throttle = cmdThrottle.toShort(),
            steering = steering,
            headlight = if (headlight) 1 else 0,
        )
        bleController.send(cmd)
    }

    override fun onCleared() {
        tiltSensor.endUpdates()
        bleController.close()
    }
}
