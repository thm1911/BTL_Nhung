package com.example.btl_nhung.data.repository

import com.example.btl_nhung.data.remote.dto.PoseStateDto
import kotlinx.coroutines.flow.StateFlow

interface RobotMapRepository {

    /** Pose gần nhất app nhận từ robot (đơn vị cm, rad). */
    val pose: StateFlow<PoseStateDto>

    /** JSON stop gần nhất trên topic robot2wd/cmd. */
    val lastControlJson: StateFlow<String?>

    /** JSON move gần nhất trên topic robot2wd/cmd. */
    val lastTargetJson: StateFlow<String?>

    /** Nhận payload robot2wd/status/pose. */
    fun applyPoseFromDevice(dto: PoseStateDto)

    /** Gửi {"type":"stop"} lên robot2wd/cmd. */
    suspend fun sendSetOrigin(): Result<Unit>

    /** Gửi {"type":"move","x":..,"y":..} lên robot2wd/cmd. */
    suspend fun sendTarget(xCm: Double, yCm: Double): Result<Unit>
}
