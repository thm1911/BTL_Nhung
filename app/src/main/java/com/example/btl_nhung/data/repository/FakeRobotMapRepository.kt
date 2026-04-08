package com.example.btl_nhung.data.repository

import com.example.btl_nhung.data.remote.dto.PoseStateDto
import com.example.btl_nhung.data.remote.dto.SetOriginCommandDto
import com.example.btl_nhung.data.remote.dto.TargetCommandDto
import com.google.gson.Gson
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.atan2
import kotlin.math.hypot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class FakeRobotMapRepository @Inject constructor(
    private val gson: Gson,
) : RobotMapRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val tickMs = 150L

    private val mutex = Mutex()

    private val _pose = MutableStateFlow(PoseStateDto(x = 0.0, y = 0.0, t = 0.0, ts = System.currentTimeMillis()))
    override val pose: StateFlow<PoseStateDto> = _pose.asStateFlow()

    private val _lastControlJson = MutableStateFlow<String?>(null)
    override val lastControlJson: StateFlow<String?> = _lastControlJson.asStateFlow()

    private val _lastTargetJson = MutableStateFlow<String?>(null)
    override val lastTargetJson: StateFlow<String?> = _lastTargetJson.asStateFlow()

    private var targetX: Double? = null
    private var targetY: Double? = null

    init {
        scope.launch {
            while (true) {
                stepTowardsTarget()
                delay(tickMs)
            }
        }
    }

    override fun applyPoseFromDevice(dto: PoseStateDto) {
        _pose.value = dto
    }

    override suspend fun sendSetOrigin(): Result<Unit> = mutex.withLock {
        _pose.value = PoseStateDto(
            x = 0.0,
            y = 0.0,
            t = 0.0,
            ts = System.currentTimeMillis(),
        )
        targetX = null
        targetY = null
        _lastControlJson.value = gson.toJson(SetOriginCommandDto())
        Result.success(Unit)
    }

    override suspend fun sendTarget(xCm: Double, yCm: Double): Result<Unit> = mutex.withLock {
        if (xCm < 0.0 || yCm < 0.0) {
            return@withLock Result.failure(IllegalArgumentException("target must be non-negative"))
        }
        targetX = xCm
        targetY = yCm
        _lastTargetJson.value = gson.toJson(TargetCommandDto(targetX = xCm, targetY = yCm))
        Result.success(Unit)
    }

    private fun stepTowardsTarget() {
        val tx = targetX ?: return
        val ty = targetY ?: return
        val cur = _pose.value
        val dx = tx - cur.x
        val dy = ty - cur.y
        val dist = hypot(dx, dy)
        if (dist < 0.5) {
            _pose.value = cur.copy(x = tx, y = ty, t = atan2(dy, dx), ts = System.currentTimeMillis())
            targetX = null
            targetY = null
            return
        }
        val step = minOf(2.2, dist)
        val nx = cur.x + dx / dist * step
        val ny = cur.y + dy / dist * step
        _pose.value = PoseStateDto(
            x = nx,
            y = ny,
            t = atan2(dy, dx),
            ts = System.currentTimeMillis(),
        )
    }
}
