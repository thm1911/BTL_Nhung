package com.example.btl_nhung.data.remote.dto

import com.google.gson.annotations.SerializedName

// `robot2wd/status/pose`
// Lấy vị trí hiện tại của robot
data class PoseStateDto(
    val x: Double,
    val y: Double,
    val t: Double,
    val ts: Long = 0L,
)

// `robot2wd/cmd`
// Cho robot dừng -> set vị trí hiện tại là origin
data class SetOriginCommandDto(
    val type: String = "stop",
)

// `robot2wd/cmd`
// Set vị trí đích
data class TargetCommandDto(
    val type: String = "move",
    @SerializedName("x") val targetX: Double,
    @SerializedName("y") val targetY: Double,
)
