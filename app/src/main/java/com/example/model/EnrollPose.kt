package com.example.model

/**
 * Guided enrollment poses for ArcFace multi-template enroll.
 * Yaw/pitch signs match the existing Record Presence challenges.
 */
enum class EnrollPose(
    val apiValue: String,
    val title: String,
    val instruction: String
) {
    FRONT(
        apiValue = "front",
        title = "Front",
        instruction = "Look straight at the camera"
    ),
    LEFT(
        apiValue = "left",
        title = "Left",
        instruction = "Turn your head slightly to the left"
    ),
    RIGHT(
        apiValue = "right",
        title = "Right",
        instruction = "Turn your head slightly to the right"
    ),
    UP(
        apiValue = "up",
        title = "Up",
        instruction = "Tilt your chin slightly up"
    ),
    DOWN(
        apiValue = "down",
        title = "Down",
        instruction = "Tilt your chin slightly down"
    );

    companion object {
        val ENROLL_ORDER: List<EnrollPose> = listOf(FRONT, LEFT, RIGHT, UP, DOWN)
    }
}
