package com.example.btl_nhung.ui.map

data class MapUiState(
    val mapWidthCm: Double,
    val mapHeightCm: Double,
    val robotPose: PoseCm,
    val target: PointCm?,
    val isRobotRunning: Boolean,
    val trail: List<PointCm>,
    val lastControlJson: String?,
    val lastTargetJson: String?,
)

data class PoseCm(
    val x: Double,
    val y: Double,
    val t: Double,
)

data class PointCm(
    val x: Double,
    val y: Double,
)
