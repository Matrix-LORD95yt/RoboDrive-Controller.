package com.robodrive.controller

import android.bluetooth.BluetoothDevice
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

class RoboDriveViewModel(context: Context) : ViewModel() {
    private val bluetooth = BluetoothController(context.applicationContext)

    val connectionState: StateFlow<ConnectionState> = bluetooth.connectionState
        .stateIn(viewModelScope, SharingStarted.Eagerly, ConnectionState.NOT_CONNECTED)
    val devices: StateFlow<List<DeviceItem>> = bluetooth.devices
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val message: StateFlow<String?> = bluetooth.message
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val isBluetoothAvailable: Boolean get() = bluetooth.isBluetoothAvailable
    val isBluetoothEnabled: Boolean get() = bluetooth.isBluetoothEnabled

    private var executionJob: Job? = null
    private var selectedDevice: BluetoothDevice? = null

    fun scanCars() = bluetooth.scan()

    fun selectDevice(device: BluetoothDevice) {
        selectedDevice = device
    }

    fun connectSelected(): Job? = selectedDevice?.let { device ->
        viewModelScope.launch { bluetooth.connect(device) }
    }

    fun disconnect() {
        executionJob?.cancel()
        viewModelScope.launch { bluetooth.send(RobotCommand.STOP) }
        bluetooth.disconnect()
    }

    fun sendCommand(command: String) {
        if (connectionState.value != ConnectionState.CONNECTED) return
        viewModelScope.launch { bluetooth.send(command) }
    }

    fun startAutomatic(mode: RobotMode) {
        val command = when (mode) {
            RobotMode.OBSTACLE_AVOIDANCE -> RobotCommand.OBSTACLE_START
            RobotMode.LINE_FOLLOWER -> RobotCommand.LINE_START
            RobotMode.HAND_FOLLOWING -> RobotCommand.HAND_START
            else -> return
        }
        sendCommand(command)
    }

    fun stopAutomatic(mode: RobotMode) {
        val command = when (mode) {
            RobotMode.OBSTACLE_AVOIDANCE -> RobotCommand.OBSTACLE_STOP
            RobotMode.LINE_FOLLOWER -> RobotCommand.LINE_STOP
            RobotMode.HAND_FOLLOWING -> RobotCommand.HAND_STOP
            else -> RobotCommand.STOP
        }
        sendCommand(command)
    }

    fun stopDrawPath() {
        executionJob?.cancel()
        executionJob = null
        sendCommand(RobotCommand.STOP)
    }

    fun executeDrawPath(points: List<OffsetPoint>) {
        if (points.size < 2 || connectionState.value != ConnectionState.CONNECTED) return
        executionJob?.cancel()
        executionJob = viewModelScope.launch {
            val simplified = simplify(points)
            for (i in 1 until simplified.size) {
                if (connectionState.value != ConnectionState.CONNECTED) break
                val prev = simplified[i - 1]
                val next = simplified[i]
                val dx = next.x - prev.x
                val dy = next.y - prev.y
                if (hypot(dx.toDouble(), dy.toDouble()) < 12) continue
                val angle = Math.toDegrees(atan2(-dy.toDouble(), dx.toDouble())).toFloat()
                val command = when {
                    angle in -35f..35f -> RobotCommand.RIGHT
                    angle > 35f && angle < 145f -> RobotCommand.FORWARD
                    angle < -35f && angle > -145f -> RobotCommand.BACKWARD
                    else -> RobotCommand.LEFT
                }
                bluetooth.send(command)
                delay((hypot(dx.toDouble(), dy.toDouble()) * 8).toLong().coerceIn(180, 900))
                bluetooth.send(RobotCommand.STOP)
                delay(80)
            }
            bluetooth.send(RobotCommand.STOP)
        }
    }

    private fun simplify(points: List<OffsetPoint>): List<OffsetPoint> {
        val result = mutableListOf(points.first())
        var last = points.first()
        for (p in points.drop(1)) {
            if (hypot((p.x - last.x).toDouble(), (p.y - last.y).toDouble()) >= 24) {
                result += p
                last = p
            }
        }
        if (result.lastOrNull() != points.last()) result += points.last()
        return result.take(80)
    }

    override fun onCleared() {
        executionJob?.cancel()
        bluetooth.close()
        super.onCleared()
    }
}

data class OffsetPoint(val x: Float, val y: Float)

class RoboDriveViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return RoboDriveViewModel(context) as T
    }
}
