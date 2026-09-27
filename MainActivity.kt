package com.robodrive.controller

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    private val viewModel: RoboDriveViewModel by viewModels { RoboDriveViewModelFactory(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RoboDriveTheme {
                RoboDriveApp(viewModel)
            }
        }
    }
}

private val Background = Color(0xFF070914)
private val SurfaceDeep = Color(0xFF10152A)
private val Cyan = Color(0xFF54D7FF)
private val Purple = Color(0xFFB26CFF)
private val Pink = Color(0xFFFF5EA8)
private val Green = Color(0xFF64F5B8)
private val Orange = Color(0xFFFF8A4D)
private val Red = Color(0xFFFF536B)
private val TextPrimary = Color(0xFFF5F7FF)
private val TextSecondary = Color(0xFFA9B1CC)

@Composable
private fun RoboDriveTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            background = Background,
            surface = SurfaceDeep,
            primary = Cyan,
            secondary = Purple,
            tertiary = Pink,
            onBackground = TextPrimary,
            onSurface = TextPrimary,
            onPrimary = Background
        ),
        content = content
    )
}

@Composable
private fun RoboDriveApp(vm: RoboDriveViewModel) {
    var screen by remember { mutableStateOf(AppScreen.STARTUP) }
    var selectedMode by remember { mutableStateOf(RobotMode.REMOTE_CONTROL) }
    val state by vm.connectionState.collectAsStateWithLifecycle()
    val devices by vm.devices.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var permissionRefresh by remember { mutableStateOf(0) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        permissionRefresh++
        val granted = result.values.all { it }
        if (granted) vm.scanCars()
    }

    fun requestBluetoothAndScan() {
        val permissions = if (Build.VERSION.SDK_INT >= 31) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        val missing = permissions.filter { ActivityCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) vm.scanCars() else permissionLauncher.launch(missing.toTypedArray())
    }

    fun openMode(mode: RobotMode) {
        selectedMode = mode
        screen = when (mode) {
            RobotMode.REMOTE_CONTROL -> AppScreen.REMOTE
            RobotMode.DRAW_PATH -> AppScreen.DRAW_PATH
            RobotMode.OBSTACLE_AVOIDANCE -> AppScreen.OBSTACLE
            RobotMode.LINE_FOLLOWER -> AppScreen.LINE
            RobotMode.HAND_FOLLOWING -> AppScreen.HAND
        }
    }

    LaunchedEffect(Unit) {
        delay(3000)
        screen = AppScreen.HOME
    }

    LaunchedEffect(state) {
        if (state == ConnectionState.CONNECTED && screen == AppScreen.CONNECTING) {
            delay(450)
            screen = AppScreen.MODES
        }
    }

    BackHandler(enabled = screen != AppScreen.STARTUP && screen != AppScreen.HOME) {
        screen = when (screen) {
            AppScreen.CARS, AppScreen.CONNECTING -> AppScreen.HOME
            AppScreen.MODES -> AppScreen.CARS
            AppScreen.REMOTE, AppScreen.DRAW_PATH, AppScreen.OBSTACLE, AppScreen.LINE, AppScreen.HAND -> AppScreen.MODES
            else -> AppScreen.HOME
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Background) {
        AnimatedBackground()
        Crossfade(targetState = screen, animationSpec = tween(450), label = "screen") { current ->
            when (current) {
                AppScreen.STARTUP -> StartupScreen()
                AppScreen.HOME -> HomeScreen(
                    state = state,
                    onSelectCar = { screen = AppScreen.CARS }
                )
                AppScreen.CARS -> CarsScreen(
                    state = state,
                    devices = devices,
                    message = message,
                    isBluetoothAvailable = vm.isBluetoothAvailable,
                    isBluetoothEnabled = vm.isBluetoothEnabled,
                    onBack = { screen = AppScreen.HOME },
                    onScan = { requestBluetoothAndScan() },
                    onEnableBluetooth = {
                        runCatching { context.startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }
                        permissionRefresh++
                    },
                    onConnect = { device ->
                        vm.selectDevice(device.device)
                        vm.connectSelected()
                        screen = AppScreen.CONNECTING
                    }
                )
                AppScreen.CONNECTING -> ConnectingScreen(
                    state = state,
                    message = message,
                    onBack = { screen = AppScreen.CARS },
                    onRetry = { vm.connectSelected() }
                )
                AppScreen.MODES -> ModeCarouselScreen(
                    selected = selectedMode,
                    connected = state == ConnectionState.CONNECTED,
                    onBack = { vm.disconnect(); screen = AppScreen.HOME },
                    onOpen = ::openMode
                )
                AppScreen.REMOTE -> RemoteScreen(
                    connected = state == ConnectionState.CONNECTED,
                    onBack = { vm.sendCommand(RobotCommand.STOP); screen = AppScreen.MODES },
                    onMove = vm::sendCommand
                )
                AppScreen.DRAW_PATH -> DrawPathScreen(
                    connected = state == ConnectionState.CONNECTED,
                    onBack = { vm.stopDrawPath(); screen = AppScreen.MODES },
                    onExecute = vm::executeDrawPath,
                    onStop = vm::stopDrawPath
                )
                AppScreen.OBSTACLE -> AutomaticModeScreen(
                    title = "OBSTACLE AVOIDANCE",
                    icon = Icons.Rounded.Sensors,
                    accent = Orange,
                    connected = state == ConnectionState.CONNECTED,
                    onBack = { vm.stopAutomatic(RobotMode.OBSTACLE_AVOIDANCE); screen = AppScreen.MODES },
                    onStart = { vm.startAutomatic(RobotMode.OBSTACLE_AVOIDANCE) },
                    onStop = { vm.stopAutomatic(RobotMode.OBSTACLE_AVOIDANCE) },
                    states = listOf("READY", "RUNNING", "OBSTACLE DETECTED", "TURNING", "STOPPED"),
                    sensorLabel = "SENSOR DATA UNAVAILABLE"
                )
                AppScreen.LINE -> AutomaticModeScreen(
                    title = "LINE FOLLOWER",
                    icon = Icons.Rounded.Timeline,
                    accent = Green,
                    connected = state == ConnectionState.CONNECTED,
                    onBack = { vm.stopAutomatic(RobotMode.LINE_FOLLOWER); screen = AppScreen.MODES },
                    onStart = { vm.startAutomatic(RobotMode.LINE_FOLLOWER) },
                    onStop = { vm.stopAutomatic(RobotMode.LINE_FOLLOWER) },
                    states = listOf("LINE DETECTED", "LINE LOST", "SEARCHING", "FORWARD", "LEFT", "RIGHT"),
                    sensorLabel = "WAITING FOR ROBOT DATA"
                )
                AppScreen.HAND -> AutomaticModeScreen(
                    title = "HAND FOLLOWING",
                    icon = Icons.Rounded.Handshake,
                    accent = Pink,
                    connected = state == ConnectionState.CONNECTED,
                    onBack = { vm.stopAutomatic(RobotMode.HAND_FOLLOWING); screen = AppScreen.MODES },
                    onStart = { vm.startAutomatic(RobotMode.HAND_FOLLOWING) },
                    onStop = { vm.stopAutomatic(RobotMode.HAND_FOLLOWING) },
                    states = listOf("HAND DETECTED", "NOT DETECTED", "FORWARD", "LEFT", "RIGHT", "STOP"),
                    sensorLabel = "WAITING FOR ROBOT DATA"
                )
            }
        }
    }
}

@Composable
private fun AnimatedBackground() {
    val transition = rememberInfiniteTransition(label = "bg")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Reverse),
        label = "drift"
    )
    Canvas(modifier = Modifier.fillMaxSize().alpha(0.8f)) {
        drawCircle(
            brush = Brush.radialGradient(listOf(Cyan.copy(alpha = 0.16f), Color.Transparent)),
            radius = size.minDimension * 0.55f,
            center = Offset(size.width * (0.12f + 0.04f * drift), size.height * 0.16f)
        )
        drawCircle(
            brush = Brush.radialGradient(listOf(Purple.copy(alpha = 0.14f), Color.Transparent)),
            radius = size.minDimension * 0.62f,
            center = Offset(size.width * (0.88f - 0.05f * drift), size.height * 0.76f)
        )
    }
}

@Composable
private fun StartupScreen() {
    val transition = rememberInfiniteTransition(label = "startup")
    val rotation by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "rotation")
    val pulse by transition.animateFloat(0.72f, 1.06f, infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f - 50f)
            for (i in 0 until 28) {
                val angle = (i * 360f / 28f + rotation) * Math.PI.toFloat() / 180f
                val radius = size.minDimension * (0.22f + (i % 5) * 0.018f)
                val p = Offset(center.x + cos(angle) * radius, center.y + sin(angle) * radius)
                drawCircle(Cyan.copy(alpha = 0.12f + (i % 3) * 0.04f), radius = 2.4f, center = p)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(180.dp * pulse)
                    .graphicsLayer(rotationY = rotation * 0.18f)
                    .clip(RoundedCornerShape(48.dp))
                    .background(
                        Brush.linearGradient(listOf(Cyan.copy(alpha = 0.14f), Purple.copy(alpha = 0.12f), Pink.copy(alpha = 0.14f)))
                    )
                    .border(1.dp, Cyan.copy(alpha = 0.35f), RoundedCornerShape(48.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.DirectionsCar, null, modifier = Modifier.size(86.dp), tint = Cyan)
            }
            Spacer(Modifier.height(26.dp))
            Text("ROBODRIVE", fontSize = 32.sp, fontWeight = FontWeight.Black, letterSpacing = 4.sp, color = TextPrimary)
            Text("CONTROLLER", fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 5.sp, color = TextSecondary)
            Spacer(Modifier.height(30.dp))
            LoadingRing(progress = 0.72f, color = Cyan)
            Spacer(Modifier.height(16.dp))
            Text("INITIALIZING CONTROL SYSTEM", fontSize = 11.sp, letterSpacing = 2.sp, color = TextSecondary)
        }
    }
}

@Composable
private fun LoadingRing(progress: Float, color: Color) {
    Canvas(modifier = Modifier.size(36.dp)) {
        drawArc(color = color.copy(alpha = 0.16f), startAngle = -90f, sweepAngle = 360f, useCenter = false, style = Stroke(4f))
        drawArc(color = color, startAngle = -90f, sweepAngle = 360f * progress, useCenter = false, style = Stroke(4f, cap = StrokeCap.Round))
    }
}

@Composable
private fun HomeScreen(state: ConnectionState, onSelectCar: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 22.dp)) {
        TopBrand(status = state)
        Spacer(Modifier.height(30.dp))
        Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            FloatingRobot()
        }
        DepthCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(state)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("BLUETOOTH LINK", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                    Text(stateLabel(state), color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                }
                Icon(Icons.Rounded.Bluetooth, null, tint = if (state == ConnectionState.CONNECTED) Green else Cyan, modifier = Modifier.size(30.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        GlowButton("SELECT CAR", Cyan, Icons.Rounded.DirectionsCar, onSelectCar, enabled = true, modifier = Modifier.fillMaxWidth().height(62.dp))
    }
}

@Composable
private fun TopBrand(status: ConnectionState) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.size(50.dp).clip(RoundedCornerShape(15.dp)).background(Brush.linearGradient(listOf(Cyan.copy(alpha = 0.18f), Purple.copy(alpha = 0.18f)))).border(1.dp, Cyan.copy(alpha = 0.35f), RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.DirectionsCar, null, tint = Cyan, modifier = Modifier.size(27.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("RoboDrive", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Black)
            Text("CONTROL SYSTEM", color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.3.sp)
        }
        StatusPill(status)
    }
}

@Composable
private fun StatusPill(state: ConnectionState) {
    val color = when (state) {
        ConnectionState.CONNECTED -> Green
        ConnectionState.ERROR, ConnectionState.DISCONNECTED -> Red
        ConnectionState.CONNECTING, ConnectionState.SCANNING -> Cyan
        else -> TextSecondary
    }
    Row(modifier = Modifier.clip(CircleShape).background(color.copy(alpha = 0.10f)).border(1.dp, color.copy(alpha = 0.28f), CircleShape).padding(horizontal = 11.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        StatusDot(state, color)
        Spacer(Modifier.width(7.dp))
        Text(stateLabel(state), color = color, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
    }
}

@Composable
private fun StatusDot(state: ConnectionState, colorOverride: Color? = null) {
    val transition = rememberInfiniteTransition(label = "status")
    val alpha by transition.animateFloat(0.45f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "alpha")
    val color = colorOverride ?: when (state) {
        ConnectionState.CONNECTED -> Green
        ConnectionState.ERROR, ConnectionState.DISCONNECTED -> Red
        ConnectionState.CONNECTING, ConnectionState.SCANNING -> Cyan
        else -> TextSecondary
    }
    Box(modifier = Modifier.size(10.dp).graphicsLayer(alpha = if (state == ConnectionState.CONNECTED || state == ConnectionState.CONNECTING || state == ConnectionState.SCANNING) alpha else 1f).clip(CircleShape).background(color))
}

private fun stateLabel(state: ConnectionState): String = when (state) {
    ConnectionState.NOT_CONNECTED -> "NOT CONNECTED"
    ConnectionState.SCANNING -> "SCANNING"
    ConnectionState.CONNECTING -> "CONNECTING"
    ConnectionState.CONNECTED -> "CONNECTED"
    ConnectionState.DISCONNECTED -> "DISCONNECTED"
    ConnectionState.ERROR -> "ERROR"
}

@Composable
private fun FloatingRobot() {
    val transition = rememberInfiniteTransition(label = "robot")
    val y by transition.animateFloat(-8f, 8f, infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "y")
    val glow by transition.animateFloat(0.45f, 0.9f, infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "glow")
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
        Canvas(modifier = Modifier.size(280.dp)) {
            drawCircle(Brush.radialGradient(listOf(Cyan.copy(alpha = glow * 0.14f), Color.Transparent)), radius = size.minDimension * 0.45f)
            drawOval(Cyan.copy(alpha = 0.08f), topLeft = Offset(size.width * 0.15f, size.height * 0.75f), size = Size(size.width * 0.7f, size.height * 0.12f))
        }
        Box(modifier = Modifier.graphicsLayer(translationY = y, rotationY = y * 0.25f).size(205.dp).clip(RoundedCornerShape(54.dp)).background(Brush.linearGradient(listOf(Color(0xFF15213A), Color(0xFF0C1224)))).border(1.2.dp, Brush.linearGradient(listOf(Cyan.copy(alpha = 0.6f), Purple.copy(alpha = 0.6f), Pink.copy(alpha = 0.4f))), RoundedCornerShape(54.dp)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.DirectionsCar, null, modifier = Modifier.size(110.dp), tint = Cyan)
                Text("RD", color = TextPrimary, fontWeight = FontWeight.Black, fontSize = 17.sp, letterSpacing = 3.sp)
                Text("READY", color = Green, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
            }
        }
    }
}

@Composable
private fun CarsScreen(
    state: ConnectionState,
    devices: List<DeviceItem>,
    message: String?,
    isBluetoothAvailable: Boolean,
    isBluetoothEnabled: Boolean,
    onBack: () -> Unit,
    onScan: () -> Unit,
    onEnableBluetooth: () -> Unit,
    onConnect: (DeviceItem) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Header("SELECT YOUR ROBODRIVE", onBack)
        Spacer(Modifier.height(18.dp))
        DepthCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Text("NEARBY CONTROL DEVICES", color = TextPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
                Spacer(Modifier.height(7.dp))
                Text("Choose the real Bluetooth device used by your RoboDrive car.", color = TextSecondary, fontSize = 12.sp, lineHeight = 18.sp)
                Spacer(Modifier.height(17.dp))
                if (!isBluetoothAvailable) {
                    Text("Bluetooth is unavailable on this phone.", color = Red, fontWeight = FontWeight.Bold)
                } else if (!isBluetoothEnabled) {
                    GlowButton("ENABLE BLUETOOTH", Purple, Icons.Rounded.Bluetooth, onEnableBluetooth, true, Modifier.fillMaxWidth().height(56.dp))
                } else {
                    GlowButton(if (state == ConnectionState.SCANNING) "SCANNING..." else "SCAN FOR CARS", Cyan, if (state == ConnectionState.SCANNING) Icons.Rounded.Refresh else Icons.Rounded.Bluetooth, onScan, state != ConnectionState.CONNECTING, Modifier.fillMaxWidth().height(56.dp))
                }
                if (state == ConnectionState.SCANNING) {
                    Spacer(Modifier.height(13.dp))
                    AnimatedScanLine()
                }
                if (!message.isNullOrBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(message, color = if (state == ConnectionState.ERROR) Red else TextSecondary, fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("DISCOVERED DEVICES", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
        Spacer(Modifier.height(8.dp))
        if (devices.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.Bluetooth, null, tint = TextSecondary, modifier = Modifier.size(46.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("NO DEVICES FOUND YET", color = TextPrimary, fontWeight = FontWeight.ExtraBold)
                    Text("Scan to discover real Bluetooth devices.", color = TextSecondary, fontSize = 12.sp)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                items(devices, key = { it.address }) { device ->
                    DeviceCard(device, onClick = { onConnect(device) })
                }
            }
        }
    }
}

@Composable
private fun AnimatedScanLine() {
    val transition = rememberInfiniteTransition(label = "scan")
    val x by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "x")
    Box(modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.05f))) {
        Box(modifier = Modifier.fillMaxWidth(0.28f).fillMaxHeight().graphicsLayer(translationX = x * 850f - 130f).clip(CircleShape).background(Brush.horizontalGradient(listOf(Color.Transparent, Cyan, Color.Transparent))))
    }
}

@Composable
private fun DeviceCard(device: DeviceItem, onClick: () -> Unit) {
    DepthCard(modifier = Modifier.fillMaxWidth(), accent = if (device.name.contains("HC-05", true)) Green else Cyan) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(54.dp).clip(RoundedCornerShape(17.dp)).background(Cyan.copy(alpha = 0.10f)).border(1.dp, Cyan.copy(alpha = 0.22f), RoundedCornerShape(17.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Bluetooth, null, tint = Cyan, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(13.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(device.name, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                    if (device.name.contains("HC-05", true)) {
                        Spacer(Modifier.width(8.dp))
                        Text("HC-05", color = Green, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                    }
                }
                Spacer(Modifier.height(5.dp))
                Text(device.address, color = TextSecondary, fontSize = 10.sp)
            }
            GlowButton("CONNECT", Cyan, Icons.Rounded.CheckCircle, onClick, true, Modifier.width(126.dp).height(48.dp))
        }
    }
}

@Composable
private fun ConnectingScreen(state: ConnectionState, message: String?, onBack: () -> Unit, onRetry: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Header("BLUETOOTH LINK", onBack)
        Spacer(Modifier.height(40.dp))
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            ConnectingVisual(state)
        }
        AnimatedContent(targetState = state, label = "connectText") { s ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    when (s) {
                        ConnectionState.CONNECTING -> "CONNECTING..."
                        ConnectionState.CONNECTED -> "CONNECTED"
                        ConnectionState.ERROR -> "CONNECTION FAILED"
                        else -> stateLabel(s)
                    },
                    color = if (s == ConnectionState.ERROR) Red else if (s == ConnectionState.CONNECTED) Green else TextPrimary,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
                if (!message.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(message, color = TextSecondary, textAlign = TextAlign.Center, fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        if (state == ConnectionState.ERROR) {
            GlowButton("TRY AGAIN", Purple, Icons.Rounded.Refresh, onRetry, true, Modifier.fillMaxWidth().height(58.dp))
        }
    }
}

@Composable
private fun ConnectingVisual(state: ConnectionState) {
    val transition = rememberInfiniteTransition(label = "connectVisual")
    val pulse by transition.animateFloat(0.86f, 1.13f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "pulse")
    val travel by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "travel")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        VisualNode(Icons.Rounded.DirectionsCar, Cyan, pulse)
        Spacer(Modifier.width(20.dp))
        Box(modifier = Modifier.width(110.dp).height(3.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.10f))) {
            Box(modifier = Modifier.size(11.dp).graphicsLayer(translationX = travel * 95f).clip(CircleShape).background(if (state == ConnectionState.CONNECTED) Green else Cyan))
        }
        Spacer(Modifier.width(20.dp))
        VisualNode(Icons.Rounded.Bluetooth, if (state == ConnectionState.CONNECTED) Green else Purple, pulse)
    }
}

@Composable
private fun VisualNode(icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, scale: Float) {
    Box(modifier = Modifier.size(96.dp * scale).clip(RoundedCornerShape(30.dp)).background(color.copy(alpha = 0.10f)).border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(30.dp)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = color, modifier = Modifier.size(46.dp))
    }
}

@Composable
private fun ModeCarouselScreen(selected: RobotMode, connected: Boolean, onBack: () -> Unit, onOpen: (RobotMode) -> Unit) {
    val modes = remember { RobotMode.entries.toList() }
    val pagerState = rememberPagerState(initialPage = selected.number - 1, pageCount = { modes.size })
    val scope = rememberCoroutineScope()
    Column(modifier = Modifier.fillMaxSize().padding(top = 20.dp, bottom = 20.dp)) {
        Header("SELECT CONTROL MODE", onBack, horizontalPadding = 20.dp)
        Spacer(Modifier.height(8.dp))
        Text("EXACTLY FIVE ROBOT MODES", color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp, modifier = Modifier.padding(horizontal = 20.dp))
        Spacer(Modifier.height(18.dp))
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 46.dp),
            pageSpacing = 14.dp,
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) { page ->
            val mode = modes[page]
            val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
            val distance = abs(pageOffset).coerceIn(0f, 1.8f)
            val scale = 1f - distance * 0.12f
            val alpha = 1f - distance * 0.25f
            val rotation = pageOffset * -11f
            val isCenter = page == pagerState.currentPage
            ModeCard(
                mode = mode,
                selected = isCenter,
                connected = connected,
                modifier = Modifier.graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                    rotationY = rotation
                    cameraDistance = 24f * density
                },
                onClick = { scope.launch { pagerState.animateScrollToPage(page) } }
            )
        }
        Spacer(Modifier.height(12.dp))
        val currentMode = modes[pagerState.currentPage]
        Text("${currentMode.number} / ${modes.size}", color = TextSecondary, fontWeight = FontWeight.ExtraBold, fontSize = 10.sp, letterSpacing = 2.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        GlowButton("OPEN ${currentMode.title}", currentMode.accentColor(), modeIcon(currentMode), { onOpen(currentMode) }, connected, Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(60.dp))
    }
}

private fun RobotMode.accentColor(): Color = when (this) {
    RobotMode.REMOTE_CONTROL -> Cyan
    RobotMode.DRAW_PATH -> Purple
    RobotMode.OBSTACLE_AVOIDANCE -> Orange
    RobotMode.LINE_FOLLOWER -> Green
    RobotMode.HAND_FOLLOWING -> Pink
}

private fun modeIcon(mode: RobotMode) = when (mode) {
    RobotMode.REMOTE_CONTROL -> Icons.Rounded.DirectionsCar
    RobotMode.DRAW_PATH -> Icons.Rounded.Draw
    RobotMode.OBSTACLE_AVOIDANCE -> Icons.Rounded.Sensors
    RobotMode.LINE_FOLLOWER -> Icons.Rounded.Timeline
    RobotMode.HAND_FOLLOWING -> Icons.Rounded.Handshake
}

@Composable
private fun ModeCard(mode: RobotMode, selected: Boolean, connected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val accent = mode.accentColor()
    DepthCard(modifier = modifier.fillMaxHeight().padding(vertical = 8.dp), accent = accent, selected = selected, onClick = onClick) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("MODE ${mode.number.toString().padStart(2, '0')}", color = accent, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                Spacer(Modifier.weight(1f))
                Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(accent.copy(alpha = 0.10f)).border(1.dp, accent.copy(alpha = 0.30f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(modeIcon(mode), null, tint = accent, modifier = Modifier.size(19.dp))
                }
            }
            Spacer(Modifier.height(36.dp))
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Icon(modeIcon(mode), null, tint = accent, modifier = Modifier.size(96.dp).graphicsLayer(alpha = if (selected) 1f else 0.72f, rotationY = if (selected) 0f else 18f))
            }
            Text(mode.title, color = TextPrimary, fontWeight = FontWeight.Black, fontSize = 24.sp, lineHeight = 29.sp)
            Spacer(Modifier.height(7.dp))
            Text(mode.subtitle, color = TextSecondary, fontSize = 12.sp, lineHeight = 18.sp)
            Spacer(Modifier.height(14.dp))
            StatusPill(if (connected) ConnectionState.CONNECTED else ConnectionState.DISCONNECTED)
        }
    }
}

@Composable
private fun RemoteScreen(connected: Boolean, onBack: () -> Unit, onMove: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Header("REMOTE CONTROL", onBack)
        Spacer(Modifier.height(10.dp))
        StatusPill(if (connected) ConnectionState.CONNECTED else ConnectionState.DISCONNECTED)
        Spacer(Modifier.height(22.dp))
        Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            DirectionPad(connected, onMove)
        }
        Text("PRESS AND HOLD TO DRIVE • RELEASE TO STOP", color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    }
}

@Composable
private fun DirectionPad(connected: Boolean, onMove: (String) -> Unit) {
    Box(modifier = Modifier.size(300.dp), contentAlignment = Alignment.Center) {
        Box(modifier = Modifier.size(298.dp).clip(CircleShape).background(Brush.radialGradient(listOf(Cyan.copy(alpha = 0.07f), Color.Transparent))).border(1.dp, Cyan.copy(alpha = 0.18f), CircleShape))
        ControlButton(Icons.Rounded.KeyboardArrowUp, "FORWARD", Cyan, connected, Modifier.align(Alignment.TopCenter).padding(top = 12.dp), { onMove(RobotCommand.FORWARD) }, { onMove(RobotCommand.STOP) })
        ControlButton(Icons.Rounded.KeyboardArrowDown, "BACKWARD", Purple, connected, Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp), { onMove(RobotCommand.BACKWARD) }, { onMove(RobotCommand.STOP) })
        ControlButton(Icons.Rounded.KeyboardArrowLeft, "LEFT", Pink, connected, Modifier.align(Alignment.CenterStart).padding(start = 12.dp), { onMove(RobotCommand.LEFT) }, { onMove(RobotCommand.STOP) })
        ControlButton(Icons.Rounded.KeyboardArrowRight, "RIGHT", Orange, connected, Modifier.align(Alignment.CenterEnd).padding(end = 12.dp), { onMove(RobotCommand.RIGHT) }, { onMove(RobotCommand.STOP) })
        StopButton(connected, Modifier.align(Alignment.Center)) { onMove(RobotCommand.STOP) }
    }
}

@Composable
private fun ControlButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, accent: Color, enabled: Boolean, modifier: Modifier, onPressed: () -> Unit, onReleased: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.90f else 1f, tween(110), label = "controlScale")
    Box(
        modifier = modifier
            .size(94.dp)
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.24f), Color(0xFF11172B))))
            .border(1.4.dp, accent.copy(alpha = if (enabled) 0.65f else 0.16f), CircleShape)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        pressed = true
                        onPressed()
                        tryAwaitRelease()
                        pressed = false
                        onReleased()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = accent.copy(alpha = if (enabled) 1f else 0.30f), modifier = Modifier.size(39.dp))
            Text(label, color = TextSecondary.copy(alpha = if (enabled) 1f else 0.30f), fontSize = 7.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        }
    }
}

@Composable
private fun StopButton(enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.91f else 1f, tween(110), label = "stopScale")
    Box(modifier = modifier.size(104.dp).graphicsLayer(scaleX = scale, scaleY = scale).clip(CircleShape).background(Red.copy(alpha = 0.18f)).border(2.dp, Red.copy(alpha = if (enabled) 0.90f else 0.25f), CircleShape).pointerInput(enabled) { if (enabled) detectTapGestures(onPress = { pressed = true; onClick(); tryAwaitRelease(); pressed = false }) }, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.StopCircle, null, tint = Red.copy(alpha = if (enabled) 1f else 0.28f), modifier = Modifier.size(43.dp))
            Text("STOP", color = TextPrimary.copy(alpha = if (enabled) 1f else 0.30f), fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp)
        }
    }
}

@Composable
private fun DrawPathScreen(connected: Boolean, onBack: () -> Unit, onExecute: (List<OffsetPoint>) -> Unit, onStop: () -> Unit) {
    val points = remember { mutableStateListOf<OffsetPoint>() }
    var drawing by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Header("DRAW PATH", onBack)
        Spacer(Modifier.height(12.dp))
        Text("SKETCH A ROUTE FOR ROBODRIVE", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.8.sp)
        Spacer(Modifier.height(13.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(30.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF10182E), Color(0xFF0B1020))))
                .border(1.dp, Purple.copy(alpha = 0.34f), RoundedCornerShape(30.dp))
                .pointerInput(connected) {
                    detectDragGestures(
                        onDragStart = { start -> if (connected) { points.clear(); drawing = true; points += OffsetPoint(start.x, start.y) } },
                        onDrag = { change, _ -> if (connected && drawing) { change.consume(); points += OffsetPoint(change.position.x, change.position.y) } },
                        onDragEnd = { drawing = false }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                for (x in 1..8) drawLine(Color.White.copy(alpha = 0.025f), Offset(size.width * x / 9f, 0f), Offset(size.width * x / 9f, size.height), 1f)
                for (y in 1..7) drawLine(Color.White.copy(alpha = 0.025f), Offset(0f, size.height * y / 8f), Offset(size.width, size.height * y / 8f), 1f)
                if (points.size > 1) {
                    val path = Path().apply {
                        moveTo(points.first().x, points.first().y)
                        points.drop(1).forEach { lineTo(it.x, it.y) }
                    }
                    drawPath(path, Purple.copy(alpha = 0.18f), style = Stroke(width = 18f, cap = StrokeCap.Round))
                    drawPath(path, Purple, style = Stroke(width = 6f, cap = StrokeCap.Round))
                }
            }
            if (points.isEmpty()) {
                Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.Draw, null, tint = Purple, modifier = Modifier.size(44.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("DRAW YOUR ROUTE", color = TextPrimary, fontWeight = FontWeight.Black, fontSize = 18.sp)
                    Text("Touch and drag to create turns and curves.", color = TextSecondary, fontSize = 12.sp)
                }
            }
            if (!connected) {
                Box(modifier = Modifier.align(Alignment.TopCenter).padding(16.dp).clip(CircleShape).background(Red.copy(alpha = 0.12f)).border(1.dp, Red.copy(alpha = 0.30f), CircleShape).padding(horizontal = 13.dp, vertical = 7.dp)) {
                    Text("ROBOT DISCONNECTED", color = Red, fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.3.sp)
                }
            }
        }
        Spacer(Modifier.height(13.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            GlowButton("CLEAR", Pink, Icons.Rounded.Close, { points.clear() }, true, Modifier.weight(1f).height(56.dp))
            GlowButton("START", Green, Icons.Rounded.PlayArrow, { onExecute(points.toList()) }, connected && points.size > 1, Modifier.weight(1f).height(56.dp))
            GlowButton("STOP", Red, Icons.Rounded.StopCircle, onStop, connected, Modifier.weight(1f).height(56.dp))
        }
    }
}

@Composable
private fun AutomaticModeScreen(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    connected: Boolean,
    onBack: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    states: List<String>,
    sensorLabel: String
) {
    var running by remember { mutableStateOf(false) }
    LaunchedEffect(running) { if (!running) onStop() }
    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Header(title, onBack)
        Spacer(Modifier.height(8.dp))
        StatusPill(if (connected) ConnectionState.CONNECTED else ConnectionState.DISCONNECTED)
        Spacer(Modifier.height(18.dp))
        DepthCard(modifier = Modifier.fillMaxWidth().weight(1f), accent = accent) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.size(132.dp).clip(CircleShape).background(accent.copy(alpha = 0.10f)).border(1.5.dp, accent.copy(alpha = 0.35f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = accent, modifier = Modifier.size(70.dp))
                }
                Spacer(Modifier.height(18.dp))
                Text(if (running && connected) states.firstOrNull() ?: "RUNNING" else if (connected) "READY" else "WAITING", color = if (running && connected) accent else TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(9.dp))
                Text(sensorLabel, color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(26.dp))
                StatusMatrix(states = states, accent = accent, active = running && connected)
                Spacer(Modifier.weight(1f))
                AnimatedRobotPulse(accent = accent, active = running && connected)
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            GlowButton("START", accent, Icons.Rounded.PlayArrow, { running = true; onStart() }, connected && !running, Modifier.weight(1f).height(58.dp))
            GlowButton("STOP", Red, Icons.Rounded.StopCircle, { running = false; onStop() }, connected, Modifier.weight(1f).height(58.dp))
        }
    }
}

@Composable
private fun StatusMatrix(states: List<String>, accent: Color, active: Boolean) {
    val scroll = rememberScrollState()
    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(scroll), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        states.forEachIndexed { index, text ->
            val activeThis = active && index == 0
            Box(modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(if (activeThis) accent.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.035f)).border(1.dp, if (activeThis) accent.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp)).padding(horizontal = 11.dp, vertical = 9.dp)) {
                Text(text, color = if (activeThis) accent else TextSecondary, fontSize = 8.sp, fontWeight = FontWeight.Black, letterSpacing = 0.7.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun AnimatedRobotPulse(accent: Color, active: Boolean) {
    val transition = rememberInfiniteTransition(label = "autoPulse")
    val scale by transition.animateFloat(0.92f, 1.06f, infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "scale")
    Box(modifier = Modifier.size(70.dp * scale).clip(CircleShape).background(accent.copy(alpha = if (active) 0.10f else 0.05f)).border(1.dp, accent.copy(alpha = if (active) 0.42f else 0.15f), CircleShape), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.DirectionsCar, null, tint = accent.copy(alpha = if (active) 1f else 0.5f), modifier = Modifier.size(35.dp))
    }
}

@Composable
private fun Header(title: String, onBack: () -> Unit, horizontalPadding: androidx.compose.ui.unit.Dp = 0.dp) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = horizontalPadding), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.05f)).border(1.dp, Color.White.copy(alpha = 0.07f), CircleShape)) {
            Icon(Icons.Rounded.ArrowBack, null, tint = TextPrimary)
        }
        Spacer(Modifier.width(13.dp))
        Text(title, color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
    }
}

@Composable
private fun DepthCard(modifier: Modifier = Modifier, accent: Color = Cyan, selected: Boolean = false, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val clickable = onClick != null
    val borderAlpha by animateFloatAsState(if (selected) 0.55f else 0.16f, tween(260), label = "cardBorder")
    val gradient = Brush.linearGradient(listOf(Color(0xFF141C33), Color(0xFF0B1121)))
    val base = modifier
        .clip(RoundedCornerShape(30.dp))
        .background(gradient)
        .border(1.dp, accent.copy(alpha = borderAlpha), RoundedCornerShape(30.dp))
        .then(if (clickable) Modifier.pointerInput(Unit) { detectTapGestures(onTap = { onClick?.invoke() }) } else Modifier)
        .padding(20.dp)
    Column(modifier = base, content = content)
}

@Composable
private fun GlowButton(
    label: String,
    accent: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, tween(110), label = "buttonScale")
    Box(modifier = modifier.graphicsLayer(scaleX = scale, scaleY = scale).clip(RoundedCornerShape(20.dp)).background(Brush.linearGradient(listOf(accent.copy(alpha = if (enabled) 0.28f else 0.08f), Color(0xFF11172A)))).border(1.dp, accent.copy(alpha = if (enabled) 0.48f else 0.14f), RoundedCornerShape(20.dp)).pointerInput(enabled) { if (enabled) detectTapGestures(onPress = { pressed = true; val released = tryAwaitRelease(); pressed = false; if (released) onClick() }) }, contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(icon, null, tint = accent.copy(alpha = if (enabled) 1f else 0.30f), modifier = Modifier.size(21.dp))
            Spacer(Modifier.width(9.dp))
            Text(label, color = TextPrimary.copy(alpha = if (enabled) 1f else 0.30f), fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.1.sp)
        }
    }
}
