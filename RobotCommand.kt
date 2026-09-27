package com.robodrive.controller

/**
 * Centralized robot protocol. Change only this file if the Arduino firmware uses
 * different command values. The default values are newline-terminated single-letter
 * movement commands plus dedicated mode commands.
 */
object RobotCommand {
    const val FORWARD = "F\n"
    const val BACKWARD = "B\n"
    const val LEFT = "L\n"
    const val RIGHT = "R\n"
    const val STOP = "S\n"
    const val OBSTACLE_START = "OA\n"
    const val OBSTACLE_STOP = "OAS\n"
    const val LINE_START = "LF\n"
    const val LINE_STOP = "LFS\n"
    const val HAND_START = "HF\n"
    const val HAND_STOP = "HFS\n"
}
