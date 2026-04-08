package com.example.btl_nhung.data.remote.dto

import com.google.gson.annotations.SerializedName

/** ESP32 -> App (`robot2wd/state/pose`). */
data class PoseStateDto(
    val x: Double,
    val y: Double,
    val t: Double,
    val ts: Long,
)

/** App -> ESP32 (`robot2wd/cmd/control`). */
data class SetOriginCommandDto(
    val cmd: String = "set_origin",
)

/** App -> ESP32 (`robot2wd/cmd/target`). */
data class TargetCommandDto(
    @SerializedName("target_x") val targetX: Double,
    @SerializedName("target_y") val targetY: Double,
)
