package com.example.btl_nhung.data.repository

import com.example.btl_nhung.data.remote.dto.PoseStateDto
import kotlinx.coroutines.flow.StateFlow

interface RobotMapRepository {

    /** Pose gần nhất app nhận từ robot (đơn vị cm, rad). */
    val pose: StateFlow<PoseStateDto>

    /** JSON gần nhất gửi lên topic robot2wd/cmd/control. */
    val lastControlJson: StateFlow<String?>

    /** JSON gần nhất gửi lên topic robot2wd/cmd/target. */
    val lastTargetJson: StateFlow<String?>

    /** Nhận payload robot2wd/state/pose. */
    fun applyPoseFromDevice(dto: PoseStateDto)

    /** Gửi {"cmd":"set_origin"} lên robot2wd/cmd/control. */
    suspend fun sendSetOrigin(): Result<Unit>

    /** Gửi {"target_x":..,"target_y":..} lên robot2wd/cmd/target. */
    suspend fun sendTarget(xCm: Double, yCm: Double): Result<Unit>
}
