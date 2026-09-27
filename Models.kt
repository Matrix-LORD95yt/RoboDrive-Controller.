package com.robodrive.controller

import android.bluetooth.BluetoothDevice

enum class ConnectionState { NOT_CONNECTED, SCANNING, CONNECTING, CONNECTED, DISCONNECTED, ERROR }

enum class AppScreen { STARTUP, HOME, CARS, CONNECTING, MODES, REMOTE, DRAW_PATH, OBSTACLE, LINE, HAND }

enum class RobotMode(val title: String, val number: Int, val subtitle: String) {
    REMOTE_CONTROL("REMOTE CONTROL", 1, "Manual precision driving"),
    DRAW_PATH("DRAW PATH", 2, "Sketch and send a route"),
    OBSTACLE_AVOIDANCE("OBSTACLE AVOIDANCE", 3, "Autonomous obstacle response"),
    LINE_FOLLOWER("LINE FOLLOWER", 4, "Track a line with the robot"),
    HAND_FOLLOWING("HAND FOLLOWING", 5, "Follow a detected hand/object")
}

data class DeviceItem(val device: BluetoothDevice) {
    val name: String get() = device.name?.takeIf { it.isNotBlank() } ?: "Unnamed Bluetooth device"
    val address: String get() = device.address
}
