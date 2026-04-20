package com.example.btl_nhung.ui.map

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import androidx.appcompat.content.res.AppCompatResources
import com.example.btl_nhung.R
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
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#263238")
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val axisTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#263238")
        textSize = 28f
    }
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1E88E5")
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }
    private val robotHeadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2E7D32")
        style = Paint.Style.FILL
    }
    private val targetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E53935")
        style = Paint.Style.FILL
    }
    private val robotDrawable = AppCompatResources.getDrawable(context, R.drawable.ic_cart)

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
        val spanX = mapWidthCm.coerceAtLeast(1.0)
        val spanY = mapHeightCm.coerceAtLeast(1.0)
        val scaleX = width.toDouble() / spanX
        val scaleY = height.toDouble() / spanY
        val halfX = spanX / 2.0
        val halfY = spanY / 2.0
        val screenX = ((xCm + halfX) * scaleX).toFloat()
        val screenY = ((halfY - yCm) * scaleY).toFloat()
        return PointF(screenX, screenY)
    }

    /** Converts canvas pixels into cm coordinates. */
    fun screenToReal(px: Float, py: Float): PointCm {
        val spanX = mapWidthCm.coerceAtLeast(1.0)
        val spanY = mapHeightCm.coerceAtLeast(1.0)
        val scaleX = width.toDouble() / spanX
        val scaleY = height.toDouble() / spanY
        val halfX = spanX / 2.0
        val halfY = spanY / 2.0
        val x = (px / scaleX - halfX).coerceIn(-halfX, halfX)
        val y = (halfY - py / scaleY).coerceIn(-halfY, halfY)
        return PointCm(x, y)
    }

    /**
     * Returns how many real-map cm correspond to 1cm on the device screen.
     * First value is along X axis, second value is along Y axis.
     */
    fun getRealCmPerScreenCm(): Pair<Double, Double>? {
        if (width <= 0 || height <= 0) return null
        val xdpi = resources.displayMetrics.xdpi.toDouble().coerceAtLeast(1.0)
        val ydpi = resources.displayMetrics.ydpi.toDouble().coerceAtLeast(1.0)
        val screenWidthCm = width * 2.54 / xdpi
        val screenHeightCm = height * 2.54 / ydpi
        if (screenWidthCm <= 0.0 || screenHeightCm <= 0.0) return null
        return (mapWidthCm / screenWidthCm) to (mapHeightCm / screenHeightCm)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawGrid(canvas)
        drawAxes(canvas)
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
        drawRobot(canvas, robot.x, robot.y, robotPose.t)
    }

    private fun drawGrid(canvas: Canvas) {
        val stepCm = pickGridStepCm()
        val halfX = mapWidthCm / 2.0
        val halfY = mapHeightCm / 2.0
        var x = -halfX
        while (x <= halfX + 0.0001) {
            val p = realToScreen(x, 0.0)
            val major = isMajorLine(x)
            canvas.drawLine(p.x, 0f, p.x, height.toFloat(), if (major) gridMajorPaint else gridMinorPaint)
            x += stepCm
        }
        var y = -halfY
        while (y <= halfY + 0.0001) {
            val p = realToScreen(0.0, y)
            val major = isMajorLine(y)
            canvas.drawLine(0f, p.y, width.toFloat(), p.y, if (major) gridMajorPaint else gridMinorPaint)
            y += stepCm
        }
    }

    private fun drawAxes(canvas: Canvas) {
        val origin = realToScreen(0.0, 0.0)
        canvas.drawLine(0f, origin.y, width.toFloat(), origin.y, axisPaint)
        canvas.drawLine(origin.x, 0f, origin.x, height.toFloat(), axisPaint)
        canvas.drawText("O(0,0)", origin.x + 8f, origin.y - 8f, axisTextPaint)
        canvas.drawText("+X", width - 52f, origin.y - 10f, axisTextPaint)
        canvas.drawText("+Y", origin.x + 10f, 30f, axisTextPaint)
    }

    private fun drawRobot(canvas: Canvas, centerX: Float, centerY: Float, headingDeg: Double) {
        val iconHalf = 40
        canvas.save()
        canvas.translate(centerX, centerY)
        // Canvas rotate positive is clockwise (Y-down), inverse of mathematical CCW heading.
        canvas.rotate((-headingDeg).toFloat())
        robotDrawable?.setBounds(-iconHalf, -iconHalf, iconHalf, iconHalf)
        robotDrawable?.draw(canvas)
        canvas.restore()

        // Green nose marker at front side (+X in robot local frame).
        val noseOffset = 20f
        val tRad = Math.toRadians(headingDeg)
        val hx = centerX + cos(tRad).toFloat() * noseOffset
        val hy = centerY - sin(tRad).toFloat() * noseOffset
        canvas.drawCircle(hx, hy, 5.5f, robotHeadPaint)
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
