# RoboDrive Controller

Production-style Android Studio project for a 3D, colorful and animated HC-05 RoboDrive controller.

## Exact five modes
1. Remote Control
2. Draw Path
3. Obstacle Avoidance
4. Line Follower
5. Hand Following

## Bluetooth protocol
The communication protocol is centralized in `app/src/main/java/com/robodrive/controller/RobotCommand.kt` so it can be adjusted to match the Arduino firmware. The default movement protocol is newline-terminated `F`, `B`, `L`, `R`, `S`; the autonomous modes use `OA`, `OAS`, `LF`, `LFS`, `HF`, `HFS`.

## Build
Open the `RoboDriveController` folder in Android Studio and let Gradle sync. The project targets compile/target SDK 37 and uses Android Gradle Plugin 9.2.0, Kotlin 2.2.10, and Compose BOM 2026.09.00.

A physical HC-05 / RoboDrive robot is required to verify hardware communication and sensor-dependent status. The app never fabricates successful connections or sensor readings.
