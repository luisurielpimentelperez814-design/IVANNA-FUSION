/*
 * IVANNA-FUSION TRASCENDENTAL
 * © 2025 Luis Uriel Pimentel Pérez. Todos los derechos reservados.
 */

package com.ivannafusion

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.compose.ui.geometry.Offset
import kotlin.math.atan2
import kotlin.math.sqrt

class IVANNAGestureDetector(context: Context) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var onWristTwist: ((Float) -> Unit)? = null
    private var onPinchRotate: ((Float) -> Unit)? = null
    private var onThreeFingerSwipe: (() -> Unit)? = null
    private var onDoubleTapLatency: (() -> Unit)? = null
    private var onTwoFingerCircle: ((Boolean) -> Unit)? = null

    private var lastAccelZ = 0f
    private var twistAccumulated = 0f
    private var lastTwistTime = 0L

    // Estado para gestos multitouch
    private val activePointers = mutableMapOf<Int, Offset>()
    private var initialPinchDistance = 0f
    private var initialPinchAngle = 0f
    private var isPinching = false
    private var circleStartAngle = 0f
    private var circleAccumulated = 0f
    private var isCircling = false

    fun setCallbacks(
        wristTwist: (Float) -> Unit,
        pinchRotate: (Float) -> Unit,
        threeFingerSwipe: () -> Unit,
        doubleTapLatency: () -> Unit,
        twoFingerCircle: (Boolean) -> Unit
    ) {
        onWristTwist = wristTwist
        onPinchRotate = pinchRotate
        onThreeFingerSwipe = threeFingerSwipe
        onDoubleTapLatency = doubleTapLatency
        onTwoFingerCircle = twoFingerCircle
    }

    fun register() {
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
    }

    fun unregister() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            detectWristTwist(event.values[0], event.values[1], event.values[2])
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun detectWristTwist(x: Float, y: Float, z: Float) {
        val currentTime = System.currentTimeMillis()
        val deltaZ = z - lastAccelZ
        lastAccelZ = z

        // Detectar giro de muñeca basado en cambio brusco de Z
        if (kotlin.math.abs(deltaZ) > 3.0f) {
            twistAccumulated += deltaZ
            if (currentTime - lastTwistTime > 200) {
                val normalized = (twistAccumulated / 20f).coerceIn(-1f, 1f)
                onWristTwist?.invoke(normalized)
                twistAccumulated = 0f
                lastTwistTime = currentTime
            }
        }
    }

    fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                activePointers[event.getPointerId(event.actionIndex)] = Offset(
                    event.getX(event.actionIndex),
                    event.getY(event.actionIndex)
                )
                updatePinchState()
            }

            MotionEvent.ACTION_MOVE -> {
                // Actualizar posiciones
                for (i in 0 until event.pointerCount) {
                    activePointers[event.getPointerId(i)] = Offset(event.getX(i), event.getY(i))
                }
                processMove()
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                activePointers.remove(event.getPointerId(event.actionIndex))
                if (activePointers.size < 2) {
                    isPinching = false
                    isCircling = false
                }
            }
        }
        return true
    }

    private fun updatePinchState() {
        if (activePointers.size == 2) {
            val points = activePointers.values.toList()
            val dx = points[1].x - points[0].x
            val dy = points[1].y - points[0].y
            initialPinchDistance = sqrt(dx * dx + dy * dy)
            initialPinchAngle = atan2(dy, dx)
            isPinching = true
            isCircling = true
            circleStartAngle = initialPinchAngle
            circleAccumulated = 0f
        }
    }

    private fun processMove() {
        if (activePointers.size == 2 && isPinching) {
            val points = activePointers.values.toList()
            val dx = points[1].x - points[0].x
            val dy = points[1].y - points[0].y
            val currentAngle = atan2(dy, dx)
            val angleDelta = currentAngle - initialPinchAngle

            // Pellizco rotatorio
            if (kotlin.math.abs(angleDelta) > 0.1f) {
                onPinchRotate?.invoke(angleDelta)
                initialPinchAngle = currentAngle
            }

            // Círculo con 2 dedos
            val circleDelta = currentAngle - circleStartAngle
            circleAccumulated += circleDelta
            circleStartAngle = currentAngle
            if (kotlin.math.abs(circleAccumulated) > 1.5f) {
                onTwoFingerCircle?.invoke(circleAccumulated > 0)
                circleAccumulated = 0f
            }
        }

        // 3 dedos hacia abajo
        if (activePointers.size == 3) {
            val yValues = activePointers.values.map { it.y }
            val avgY = yValues.average().toFloat()
            // Detectar swipe hacia abajo (simplificado)
            if (avgY > 1500) { // Umbral de pantalla
                onThreeFingerSwipe?.invoke()
            }
        }
    }

    fun onDoubleTap() {
        onDoubleTapLatency?.invoke()
    }
}
