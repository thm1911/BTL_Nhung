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

    val uiState: StateFlow<MapUiState> = combine(
        mapWidthCm,
        mapHeightCm,
        repository.pose,
        selectedTarget,
        trail,
    ) { width, height, pose, target, path ->
        val poseCm = PoseCm(x = pose.x, y = pose.y, t = pose.t)
        val running = target?.let { distanceCm(PointCm(poseCm.x, poseCm.y), it) > RUNNING_DISTANCE_EPS_CM } ?: false
        BaseUiData(
            width = width,
            height = height,
            pose = poseCm,
            target = target,
            isRobotRunning = running,
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
        selectedTarget.value = PointCm(targetX, targetY)
    }

    fun sendSetOrigin() {
        viewModelScope.launch {
            repository.sendSetOrigin().fold(
                onSuccess = {
                    selectedTarget.value = null
                    trail.value = listOf(PointCm(0.0, 0.0))
                    _userMessages.emit("Đã gửi set_origin, gốc tọa độ reset về (0,0).")
                },
                onFailure = { e ->
                    _userMessages.emit(e.message ?: "Gửi set_origin thất bại.")
                },
            )
        }
    }

    fun sendTargetToDevice() {
        val target = selectedTarget.value
        if (target == null) {
            _userMessages.tryEmit("Chưa chọn đích trên map.")
            return
        }
        viewModelScope.launch {
            val result = repository.sendTarget(target.x, target.y)
            result.fold(
                onSuccess = {
                    _userMessages.emit("Đã gửi target_x/target_y theo tọa độ cm.")
                },
                onFailure = { e ->
                    _userMessages.emit(e.message ?: "Gửi đích thất bại.")
                },
            )
        }
    }

    private fun distanceCm(a: PointCm, b: PointCm): Double {
        return hypot(a.x - b.x, a.y - b.y)
    }
}
