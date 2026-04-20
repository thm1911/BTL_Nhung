package com.example.btl_nhung.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.btl_nhung.data.repository.RobotMapRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.hypot
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class MapViewModel @Inject constructor(
    private val repository: RobotMapRepository,
) : ViewModel() {
    companion object {
        private const val RUNNING_DISTANCE_EPS_CM = 2.0
    }

    private val mapWidthCm = MutableStateFlow(500.0)
    private val mapHeightCm = MutableStateFlow(300.0)
    private val selectedTarget = MutableStateFlow<PointCm?>(null)
    private val movingTarget = MutableStateFlow<PointCm?>(null)
    private val trail = MutableStateFlow<List<PointCm>>(listOf(PointCm(0.0, 0.0)))

    private val _userMessages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val userMessages = _userMessages.asSharedFlow()

    init {
        viewModelScope.launch {
            repository.pose.collect { pose ->
                val p = PointCm(pose.x, pose.y)
                val last = trail.value.lastOrNull()
                if (last == null || distanceCm(last, p) >= 1.5) {
                    trail.value = trail.value + p
                }
                val active = movingTarget.value
                if (active != null && distanceCm(p, active) <= RUNNING_DISTANCE_EPS_CM) {
                    movingTarget.value = null
                    _userMessages.tryEmit("Robot đã đến đích, có thể chọn điểm mới.")
                }
            }
        }
    }

    private data class BaseUiData(
        val width: Double,
        val height: Double,
        val pose: PoseCm,
        val target: PointCm?,
        val isRobotRunning: Boolean,
        val trail: List<PointCm>,
    )

    val uiState: StateFlow<MapUiState> = mapWidthCm
        .combine(mapHeightCm) { width, height -> width to height }
        .combine(repository.pose) { wh, pose -> Triple(wh.first, wh.second, pose) }
        .combine(selectedTarget) { base, selected -> Pair(base, selected) }
        .combine(movingTarget) { withSelected, moving -> Triple(withSelected.first, withSelected.second, moving) }
        .combine(trail) { data, path ->
            val base = data.first
            val selected = data.second
            val moving = data.third
            val poseCm = PoseCm(x = base.third.x, y = base.third.y, t = base.third.t)
            val displayTarget = moving ?: selected
            BaseUiData(
                width = base.first,
                height = base.second,
                pose = poseCm,
                target = displayTarget,
                isRobotRunning = moving != null,
                trail = path,
            )
        }.combine(repository.lastControlJson) { base, controlJson ->
        base to controlJson
    }.combine(repository.lastTargetJson) { (base, controlJson), targetJson ->
        MapUiState(
            mapWidthCm = base.width,
            mapHeightCm = base.height,
            robotPose = base.pose,
            target = base.target,
            isRobotRunning = base.isRobotRunning,
            trail = base.trail,
            lastControlJson = controlJson,
            lastTargetJson = targetJson,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MapUiState(
            mapWidthCm = 500.0,
            mapHeightCm = 300.0,
            robotPose = PoseCm(0.0, 0.0, 0.0),
            target = null,
            isRobotRunning = false,
            trail = listOf(PointCm(0.0, 0.0)),
            lastControlJson = null,
            lastTargetJson = null,
        ),
    )

    fun onMapSizeChanged(widthCm: Double, heightCm: Double) {
        if (widthCm <= 0.0 || heightCm <= 0.0) return
        mapWidthCm.value = widthCm
        mapHeightCm.value = heightCm
    }

    fun onMapTapped(targetX: Double, targetY: Double) {
        if (movingTarget.value != null) {
            _userMessages.tryEmit("Robot đang chạy, chưa thể chọn đích mới.")
            return
        }
        selectedTarget.value = PointCm(targetX, targetY)
    }

    fun setManualTarget(targetX: Double, targetY: Double) {
        if (movingTarget.value != null) {
            _userMessages.tryEmit("Robot đang chạy, chưa thể đổi đích.")
            return
        }
        selectedTarget.value = PointCm(targetX, targetY)
    }

    fun sendSetOrigin() {
        viewModelScope.launch {
            repository.sendSetOrigin().fold(
                onSuccess = {
                    movingTarget.value = null
                    selectedTarget.value = null
                    _userMessages.emit("Đã gửi lệnh stop cho robot.")
                },
                onFailure = { e ->
                    _userMessages.emit(e.message ?: "Gửi lệnh stop thất bại.")
                },
            )
        }
    }

    fun sendTargetToDevice() {
        if (movingTarget.value != null) {
            _userMessages.tryEmit("Robot đang chạy, chờ đến đích rồi gửi lệnh mới.")
            return
        }
        val target = selectedTarget.value
        if (target == null) {
            _userMessages.tryEmit("Chưa chọn đích trên map.")
            return
        }
        viewModelScope.launch {
            val result = repository.sendTarget(target.x, target.y)
            result.fold(
                onSuccess = {
                    movingTarget.value = target
                    _userMessages.emit("Đã gửi lệnh move theo tọa độ x/y.")
                },
                onFailure = { e ->
                    _userMessages.emit(e.message ?: "Gửi lệnh move thất bại.")
                },
            )
        }
    }

    fun clearTrail() {
        val pose = repository.pose.value
        trail.value = listOf(PointCm(pose.x, pose.y))
        _userMessages.tryEmit("Đã xóa đường đi đã vẽ trên map.")
    }

    private fun distanceCm(a: PointCm, b: PointCm): Double {
        return hypot(a.x - b.x, a.y - b.y)
    }
}
