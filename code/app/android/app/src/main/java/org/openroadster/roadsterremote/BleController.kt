package org.openroadster.roadsterremote

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

private val SERVICE_UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
private val RX_UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")
private val TX_UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")

private val BATTERY_SERVICE_UUID = UUID.fromString("0000180F-0000-1000-8000-00805F9B34FB")
private val BATTERY_LEVEL_CHARACTERISTIC_UUID = UUID.fromString("00002A19-0000-1000-8000-00805F9B34FB")
// Custom: 1 = car is on USB power (battery charging), 0 = on battery
private val POWER_SOURCE_CHARACTERISTIC_UUID = UUID.fromString("8FDDF80E-8D10-4B50-9466-FCE56AF3B124")

private val CLIENT_CONFIG_DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")

// A car that drops out of scan results for this long is removed from the list
private const val STALE_CAR_TIMEOUT_MS = 5_000L

class BleCommand(val throttle: Short, val steering: Short, val headlight: Byte) {
    val payload: ByteArray
        get() = byteArrayOf(
            steering.toByte(), (steering.toInt() shr 8).toByte(),
            throttle.toByte(), (throttle.toInt() shr 8).toByte(),
            headlight,
        )
}

/** A Roadster found while scanning */
data class DiscoveredCar(
    /** The car's Bluetooth address */
    val id: String,
    val name: String,
    val rssi: Int,
    val lastSeen: Long,
) {
    /** Short tag to tell identical-looking cars apart */
    val shortId: String
        get() = id.replace(":", "").takeLast(4)

    /** 0...1 for signal strength bars */
    val signalStrength: Double
        get() = when {
            rssi >= -60 -> 1.0
            rssi >= -70 -> 0.75
            rssi >= -80 -> 0.5
            else -> 0.25
        }
}

sealed class ConnectionState {
    data object Disconnected : ConnectionState()
    data class Connecting(val target: DiscoveredCar) : ConnectionState()
    data class Connected(val target: DiscoveredCar) : ConnectionState()
    /** The user asked to disconnect and it's in progress */
    data class Disconnecting(val target: DiscoveredCar) : ConnectionState()
    /** The connection dropped without the user asking (out of range, car switched off) */
    data class Lost(val target: DiscoveredCar) : ConnectionState()

    /** The car the app is linked to, or trying to be */
    val car: DiscoveredCar?
        get() = when (this) {
            is Disconnected, is Lost -> null
            is Connecting -> target
            is Connected -> target
            is Disconnecting -> target
        }
}

enum class BluetoothState {
    Unknown, Unsupported, Unauthorized, PoweredOff,
    /** Android 11 and older can't scan with location services off */
    LocationOff,
    PoweredOn,
}

/**
 * All state lives on the main thread: GATT callbacks arrive on a binder thread
 * and are posted to [main] before they touch anything.
 */
@SuppressLint("MissingPermission") // every Bluetooth call is gated on bluetoothState == PoweredOn
class BleController(private val context: Context) {

    private val main = Handler(Looper.getMainLooper())
    private val adapter: BluetoothAdapter? =
        context.getSystemService(BluetoothManager::class.java)?.adapter

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState
    private val _cars = MutableStateFlow<List<DiscoveredCar>>(emptyList())
    val cars: StateFlow<List<DiscoveredCar>> = _cars
    private val _bluetoothState = MutableStateFlow(BluetoothState.Unknown)
    val bluetoothState: StateFlow<BluetoothState> = _bluetoothState
    private val _batteryLevel = MutableStateFlow(0)
    val batteryLevel: StateFlow<Int> = _batteryLevel
    private val _onUsbPower = MutableStateFlow(false)
    val onUsbPower: StateFlow<Boolean> = _onUsbPower

    private var gatt: BluetoothGatt? = null
    private var commandChar: BluetoothGattCharacteristic? = null

    private val discoveredDevices = mutableMapOf<String, BluetoothDevice>()
    private val discoveredCars = mutableMapOf<String, DiscoveredCar>()
    private var wantsScan = false
    private var isScanning = false
    // Android runs one GATT operation at a time, so setup steps wait their turn
    private val gattQueue = ArrayDeque<(BluetoothGatt) -> Boolean>()
    private var gattOpInFlight = false
    // A command write is waiting for the stack to take it
    private var commandWriteInFlight = false

    private val pruneStaleCars = object : Runnable {
        override fun run() {
            val cutoff = SystemClock.elapsedRealtime() - STALE_CAR_TIMEOUT_MS
            if (discoveredCars.values.removeAll { it.lastSeen < cutoff }) publishCars()
            main.postDelayed(this, 1_000)
        }
    }

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = refreshState()
    }

    init {
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED).apply {
            addAction(android.location.LocationManager.PROVIDERS_CHANGED_ACTION)
        }
        ContextCompat.registerReceiver(context, stateReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        refreshState()
    }

    fun close() {
        context.unregisterReceiver(stateReceiver)
        stopScan()
        gatt?.close()
    }

    /** Re-reads the adapter and permissions, e.g. after the user grants access */
    fun refreshState() {
        val state = currentBluetoothState()
        _bluetoothState.value = state

        if (state == BluetoothState.PoweredOn) {
            beginScanIfReady()
        } else {
            isScanning = false
            if (gatt != null) resetConnection(droppedState)
        }
    }

    private fun currentBluetoothState(): BluetoothState {
        val adapter = adapter ?: return BluetoothState.Unsupported
        if (!hasPermissions(context)) return BluetoothState.Unauthorized
        if (!adapter.isEnabled) return BluetoothState.PoweredOff
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            val location = context.getSystemService(android.location.LocationManager::class.java)
            if (location == null || !LocationManagerCompat.isLocationEnabled(location)) {
                return BluetoothState.LocationOff
            }
        }
        return BluetoothState.PoweredOn
    }

    // Scanning

    fun startScan() {
        wantsScan = true
        discoveredCars.clear()
        discoveredDevices.clear()
        publishCars()
        beginScanIfReady()

        main.removeCallbacks(pruneStaleCars)
        main.postDelayed(pruneStaleCars, 1_000)
    }

    fun stopScan() {
        wantsScan = false
        main.removeCallbacks(pruneStaleCars)
        if (isScanning) {
            isScanning = false
            if (_bluetoothState.value == BluetoothState.PoweredOn) {
                adapter?.bluetoothLeScanner?.stopScan(scanCallback)
            }
        }
    }

    private fun beginScanIfReady() {
        if (!wantsScan || isScanning || _bluetoothState.value != BluetoothState.PoweredOn) return
        val scanner = adapter?.bluetoothLeScanner ?: return
        val filters = listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(SERVICE_UUID)).build())
        // Every advertisement is reported, so RSSI stays live and stale cars can be dropped
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanner.startScan(filters, settings, scanCallback)
        isScanning = true
    }

    private fun publishCars() {
        _cars.value = discoveredCars.values.sortedByDescending { it.rssi }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            main.post { didDiscover(result) }
        }

        override fun onScanFailed(errorCode: Int) {
            main.post { isScanning = false }
        }
    }

    private fun didDiscover(result: ScanResult) {
        if (!isScanning) return
        // RSSI of 127 means "unavailable"
        if (result.rssi == 127) return

        val device = result.device
        val name = result.scanRecord?.deviceName ?: device.name ?: "Unknown Roadster"

        discoveredDevices[device.address] = device
        discoveredCars[device.address] = DiscoveredCar(
            id = device.address,
            name = name,
            rssi = result.rssi,
            lastSeen = SystemClock.elapsedRealtime(),
        )
        publishCars()
    }

    // Connecting

    fun connect(car: DiscoveredCar) {
        val target = discoveredDevices[car.id] ?: return

        // Only one car at a time
        gatt?.let {
            it.disconnect()
            it.close()
            resetConnection(ConnectionState.Disconnected)
        }

        _connectionState.value = ConnectionState.Connecting(car)
        gatt = target.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    fun disconnect() {
        val gatt = gatt ?: return

        when (val state = _connectionState.value) {
            is ConnectionState.Connecting -> {
                // A pending connection has nothing to tear down, so don't wait for a callback
                gatt.disconnect()
                gatt.close()
                resetConnection(ConnectionState.Disconnected)
            }
            is ConnectionState.Connected -> {
                _connectionState.value = ConnectionState.Disconnecting(state.target)
                gatt.disconnect()
            }
            else -> {}
        }
    }

    /** Where a connection ends up when it closes without being asked to */
    private val droppedState: ConnectionState
        get() = when (val state = _connectionState.value) {
            is ConnectionState.Connected -> ConnectionState.Lost(state.target)
            else -> ConnectionState.Disconnected
        }

    private fun resetConnection(state: ConnectionState) {
        gatt = null
        commandChar = null
        gattQueue.clear()
        gattOpInFlight = false
        commandWriteInFlight = false
        _connectionState.value = state
        _onUsbPower.value = false
    }

    @Suppress("DEPRECATION") // the newer write API needs Android 13
    fun send(command: BleCommand) {
        val gatt = gatt ?: return
        val commandChar = commandChar ?: return
        if (_connectionState.value !is ConnectionState.Connected) return
        // Packets go out at 20 Hz and each one replaces the last, so if the
        // previous one hasn't gone out yet, skip this one rather than queue it.
        // Older firmware only accepts acknowledged writes, which are slower than
        // 20 Hz; this keeps them from building up a growing delay too.
        if (commandWriteInFlight || gattOpInFlight) return

        val withoutResponse = commandChar.properties and
            BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0
        commandChar.writeType = if (withoutResponse) {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        }
        commandChar.value = command.payload
        commandWriteInFlight = gatt.writeCharacteristic(commandChar)
    }

    // GATT

    private fun enqueue(op: (BluetoothGatt) -> Boolean) {
        gattQueue.addLast(op)
        runNextGattOp()
    }

    private fun runNextGattOp() {
        val gatt = gatt ?: return
        while (!gattOpInFlight && !commandWriteInFlight) {
            val op = gattQueue.removeFirstOrNull() ?: return
            // An op that fails to start gets no callback, so move on to the next
            gattOpInFlight = op(gatt)
        }
    }

    private fun gattOpFinished() {
        gattOpInFlight = false
        runNextGattOp()
    }

    @Suppress("DEPRECATION")
    private fun didDiscoverServices(gatt: BluetoothGatt) {
        gatt.getService(SERVICE_UUID)?.let { service ->
            commandChar = service.getCharacteristic(RX_UUID)
        }

        val battery = gatt.getService(BATTERY_SERVICE_UUID) ?: return
        for (uuid in listOf(BATTERY_LEVEL_CHARACTERISTIC_UUID, POWER_SOURCE_CHARACTERISTIC_UUID)) {
            val c = battery.getCharacteristic(uuid) ?: continue
            gatt.setCharacteristicNotification(c, true)
            c.getDescriptor(CLIENT_CONFIG_DESCRIPTOR_UUID)?.let { cccd ->
                enqueue {
                    cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    it.writeDescriptor(cccd)
                }
            }
            // Show the values right away instead of waiting for the next notify
            enqueue { it.readCharacteristic(c) }
        }
    }

    private fun didUpdateValue(uuid: UUID, value: ByteArray?) {
        when (uuid) {
            BATTERY_LEVEL_CHARACTERISTIC_UUID -> _batteryLevel.value = (value?.firstOrNull()?.toInt() ?: 0) and 0xFF
            POWER_SOURCE_CHARACTERISTIC_UUID -> _onUsbPower.value = value?.firstOrNull() == 1.toByte()
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            main.post {
                if (g != gatt) return@post
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        (_connectionState.value as? ConnectionState.Connecting)?.let {
                            _connectionState.value = ConnectionState.Connected(it.target)
                        }
                        g.discoverServices()
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        g.close()
                        resetConnection(droppedState)
                    }
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            main.post {
                if (g == gatt && status == BluetoothGatt.GATT_SUCCESS) didDiscoverServices(g)
            }
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            main.post {
                if (g == gatt && c.uuid == RX_UUID) {
                    commandWriteInFlight = false
                    runNextGattOp()
                }
            }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            main.post { if (g == gatt) gattOpFinished() }
        }

        // Android 12 and older call the deprecated read/changed callbacks, 13+ the ones with a value

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            @Suppress("DEPRECATION")
            didRead(g, c.uuid, c.value, status)
        }

        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
            didRead(g, c.uuid, value, status)
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            didChange(g, c.uuid, c.value)
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
            didChange(g, c.uuid, value)
        }

        // The characteristic's value can change once this thread moves on, so it's copied here
        private fun didRead(g: BluetoothGatt, uuid: UUID, value: ByteArray?, status: Int) {
            val copy = value?.copyOf()
            main.post {
                if (g != gatt) return@post
                if (status == BluetoothGatt.GATT_SUCCESS) didUpdateValue(uuid, copy)
                gattOpFinished()
            }
        }

        private fun didChange(g: BluetoothGatt, uuid: UUID, value: ByteArray?) {
            val copy = value?.copyOf()
            main.post { if (g == gatt) didUpdateValue(uuid, copy) }
        }
    }

    companion object {
        val requiredPermissions: Array<String> =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
            }

        fun hasPermissions(context: Context) = requiredPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }
}
