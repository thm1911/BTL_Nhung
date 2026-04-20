package com.example.btl_nhung.ui.map

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class RobotMapCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private var mapWidthCm: Double = 500.0
    private var mapHeightCm: Double = 300.0
    private var robotPose = PoseCm(0.0, 0.0, 0.0)
    private var target: PointCm? = null
    private var trail: List<PointCm> = emptyList()
    private var targetSelectionEnabled: Boolean = true
    private var onTargetPicked: ((PointCm) -> Unit)? = null

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#455A64")
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val gridMinorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D4DFE5")
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }
    private val gridMajorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#AFC0CA")
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
    }
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1E88E5")
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }
    private val robotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2E7D32")
        style = Paint.Style.FILL
    }
    private val headingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val targetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E53935")
        style = Paint.Style.FILL
    }

    fun render(
        mapWidthCm: Double,
        mapHeightCm: Double,
        robotPose: PoseCm,
        target: PointCm?,
        trail: List<PointCm>,
    ) {
        this.mapWidthCm = mapWidthCm
        this.mapHeightCm = mapHeightCm
        this.robotPose = robotPose
        this.target = target
        this.trail = trail
        invalidate()
    }

    fun setOnTargetPickedListener(listener: (PointCm) -> Unit) {
        onTargetPicked = listener
    }

    fun setTargetSelectionEnabled(enabled: Boolean) {
        targetSelectionEnabled = enabled
    }

    /** Converts cm coordinates into canvas pixels. */
    fun realToScreen(xCm: Double, yCm: Double): PointF {
        val scaleX = width.toDouble() / mapWidthCm
        val scaleY = height.toDouble() / mapHeightCm
        val screenX = (xCm * scaleX).toFloat()
        val screenY = (height - yCm * scaleY).toFloat()
        return PointF(screenX, screenY)
    }

    /** Converts canvas pixels into cm coordinates. */
    fun screenToReal(px: Float, py: Float): PointCm {
        val scaleX = width.toDouble() / mapWidthCm
        val scaleY = height.toDouble() / mapHeightCm
        val x = (px / scaleX).coerceIn(0.0, mapWidthCm)
        val y = ((height - py) / scaleY).coerceIn(0.0, mapHeightCm)
        return PointCm(x, y)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawGrid(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), borderPaint)

        if (trail.size >= 2) {
            val path = Path()
            val start = realToScreen(trail[0].x, trail[0].y)
            path.moveTo(start.x, start.y)
            for (i in 1 until trail.size) {
                val p = realToScreen(trail[i].x, trail[i].y)
                path.lineTo(p.x, p.y)
            }
            canvas.drawPath(path, trailPaint)
        }

        target?.let {
            val p = realToScreen(it.x, it.y)
            canvas.drawCircle(p.x, p.y, 12f, targetPaint)
        }

        val robot = realToScreen(robotPose.x, robotPose.y)
        canvas.drawCircle(robot.x, robot.y, 16f, robotPaint)
        val headingLen = 26f
        val hx = (robot.x + cos(robotPose.t).toFloat() * headingLen)
        val hy = (robot.y - sin(robotPose.t).toFloat() * headingLen)
        canvas.drawLine(robot.x, robot.y, hx, hy, headingPaint)
    }

    private fun drawGrid(canvas: Canvas) {
        val stepCm = pickGridStepCm()
        var x = 0.0
        while (x <= mapWidthCm + 0.0001) {
            val p = realToScreen(x, 0.0)
            val major = isMajorLine(x)
            canvas.drawLine(p.x, 0f, p.x, height.toFloat(), if (major) gridMajorPaint else gridMinorPaint)
            x += stepCm
        }
        var y = 0.0
        while (y <= mapHeightCm + 0.0001) {
            val p = realToScreen(0.0, y)
            val major = isMajorLine(y)
            canvas.drawLine(0f, p.y, width.toFloat(), p.y, if (major) gridMajorPaint else gridMinorPaint)
            y += stepCm
        }
    }

    private fun isMajorLine(vCm: Double): Boolean {
        return abs(vCm % 100.0) < 0.001 || abs(vCm % 100.0 - 100.0) < 0.001 || vCm == 0.0
    }

    private fun pickGridStepCm(): Double {
        val maxCm = maxOf(mapWidthCm, mapHeightCm)
        return when {
            maxCm <= 120.0 -> 10.0
            maxCm <= 250.0 -> 20.0
            maxCm <= 600.0 -> 50.0
            else -> 100.0
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!targetSelectionEnabled) return false
        if (event.action == MotionEvent.ACTION_DOWN) {
            onTargetPicked?.invoke(screenToReal(event.x, event.y))
            return true
        }
        return super.onTouchEvent(event)
    }
}
