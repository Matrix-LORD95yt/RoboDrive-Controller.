package com.robodrive.controller

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream
import java.util.UUID

class BluetoothController(private val context: Context) {
    companion object {
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    private val adapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var socket: BluetoothSocket? = null
    private var output: OutputStream? = null
    private var receiverRegistered = false

    private val _connectionState = MutableStateFlow(ConnectionState.NOT_CONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _devices = MutableStateFlow<List<DeviceItem>>(emptyList())
    val devices: StateFlow<List<DeviceItem>> = _devices.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val isBluetoothAvailable: Boolean get() = adapter != null
    val isBluetoothEnabled: Boolean get() = adapter?.isEnabled == true

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    @Suppress("DEPRECATION")
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    if (device != null) addDevice(device)
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    if (_connectionState.value == ConnectionState.SCANNING) {
                        _connectionState.value = ConnectionState.NOT_CONNECTED
                    }
                }
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    if (state == BluetoothAdapter.STATE_OFF) {
                        disconnect()
                        _message.value = "Bluetooth is turned off."
                    }
                }
            }
        }
    }

    init {
        registerReceiverSafely()
    }

    private fun registerReceiverSafely() {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        receiverRegistered = true
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun canScan(): Boolean =
        android.os.Build.VERSION.SDK_INT < 31 || hasPermission(android.Manifest.permission.BLUETOOTH_SCAN)

    private fun canConnect(): Boolean =
        android.os.Build.VERSION.SDK_INT < 31 || hasPermission(android.Manifest.permission.BLUETOOTH_CONNECT)

    @SuppressLint("MissingPermission")
    fun scan() {
        if (!isBluetoothAvailable) {
            _connectionState.value = ConnectionState.ERROR
            _message.value = "This phone does not support Bluetooth."
            return
        }
        if (!isBluetoothEnabled) {
            _connectionState.value = ConnectionState.ERROR
            _message.value = "Bluetooth is turned off. Enable Bluetooth and try again."
            return
        }
        if (!canScan()) {
            _connectionState.value = ConnectionState.ERROR
            _message.value = "Nearby devices permission is required to scan."
            return
        }

        runCatching { adapter?.cancelDiscovery() }
        _devices.value = emptyList()
        loadPairedDevices()
        _connectionState.value = ConnectionState.SCANNING
        _message.value = null
        val started = runCatching { adapter?.startDiscovery() == true }.getOrDefault(false)
        if (!started) {
            _connectionState.value = ConnectionState.ERROR
            _message.value = "Bluetooth scanning could not be started."
        }
    }

    @SuppressLint("MissingPermission")
    private fun loadPairedDevices() {
        if (!canConnect()) return
        val bonded = runCatching { adapter?.bondedDevices.orEmpty() }.getOrDefault(emptySet())
        bonded.forEach(::addDevice)
    }

    private fun addDevice(device: BluetoothDevice) {
        val current = _devices.value
        if (current.none { it.address == device.address }) {
            _devices.value = current + DeviceItem(device)
        }
    }

    suspend fun connect(device: BluetoothDevice): Boolean = withContext(Dispatchers.IO) {
        if (!canConnect()) {
            _connectionState.value = ConnectionState.ERROR
            _message.value = "Nearby devices permission is required to connect."
            return@withContext false
        }

        _connectionState.value = ConnectionState.CONNECTING
        _message.value = null
        runCatching { adapter?.cancelDiscovery() }
        closeSocketOnly()

        val result = runCatching {
            val classicSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            classicSocket.connect()
            socket = classicSocket
            output = classicSocket.outputStream
            true
        }.recoverCatching {
            val insecure = device.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
            insecure.connect()
            socket = insecure
            output = insecure.outputStream
            true
        }.getOrElse { error ->
            closeSocketOnly()
            _message.value = "Connection failed: ${error.message ?: "Unable to connect"}"
            false
        }

        _connectionState.value = if (result) ConnectionState.CONNECTED else ConnectionState.ERROR
        result
    }

    suspend fun send(command: String): Boolean = withContext(Dispatchers.IO) {
        val out = output
        if (_connectionState.value != ConnectionState.CONNECTED || out == null) return@withContext false
        runCatching {
            out.write(command.toByteArray(Charsets.UTF_8))
            out.flush()
            true
        }.getOrElse {
            _connectionState.value = ConnectionState.DISCONNECTED
            _message.value = "Bluetooth connection was lost."
            closeSocketOnly()
            false
        }
    }

    fun disconnect() {
        runCatching {
            output?.write(RobotCommand.STOP.toByteArray(Charsets.UTF_8))
            output?.flush()
        }
        closeSocketOnly()
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    private fun closeSocketOnly() {
        runCatching { output?.close() }
        runCatching { socket?.close() }
        output = null
        socket = null
    }

    fun clearMessage() { _message.value = null }

    fun close() {
        disconnect()
        if (receiverRegistered) {
            runCatching { context.unregisterReceiver(receiver) }
            receiverRegistered = false
        }
    }
}
