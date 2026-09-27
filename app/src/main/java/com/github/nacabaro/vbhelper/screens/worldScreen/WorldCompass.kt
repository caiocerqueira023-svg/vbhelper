package com.github.nacabaro.vbhelper.screens.worldScreen

import android.content.Context
import android.content.pm.ApplicationInfo
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.sqrt

internal enum class CompassStatus { WAITING, CALIBRATE, TRACKING, APPROXIMATE, UNAVAILABLE }

internal data class CompassReading(
    val heading: Float? = null,
    val status: CompassStatus = CompassStatus.WAITING,
    val diagnostic: String = "Compass C3: waiting"
)

@Composable
internal fun rememberWorldCompass(
    location: Location?,
    enabled: Boolean = true
): CompassReading {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val currentLocation by rememberUpdatedState(location)
    var reading by remember { mutableStateOf(CompassReading()) }

    DisposableEffect(context, view, lifecycle, enabled) {
        if (!enabled) {
            reading = CompassReading()
            return@DisposableEffect onDispose { }
        }
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val handler = Handler(Looper.getMainLooper())
        val debug = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        val fieldSensor = manager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        val rotationSensor = manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val rotationMatrix = FloatArray(9)
        val field = FloatArray(3)
        var filter = CompassHeading()
        var fieldTime = 0L
        var poseTime = 0L
        var fieldAccuracy = SensorManager.SENSOR_STATUS_UNRELIABLE
        var fieldStrength = 0f
        var lastFix: Location? = null
        var expectedField: Float? = null
        var declination = 0f
        var goodSince: Long? = null
        var active = false
        var events = 0L
        var lastDiagnosticTime = 0L
        var diagnostic = "Compass C3: waiting"

        fun fieldTrusted() = trustworthyMagneticField(fieldAccuracy, fieldStrength, expectedField)

        fun publish(timestamp: Long) {
            if (fieldTime == 0L) return
            val fix = currentLocation
            if (fix != null && fix !== lastFix) {
                val model = GeomagneticField(
                    fix.latitude.toFloat(), fix.longitude.toFloat(),
                    fix.altitude.toFloat(), System.currentTimeMillis()
                )
                declination = model.declination
                expectedField = model.fieldStrength / 1000f // Android model returns nT.
                lastFix = fix
            }
            // The rotation-vector sensor can provide a useful heading immediately.
            // Magnetometer quality still controls whether we label it as fully
            // tracked, but it must not gate the first visible compass reading.
            val poseFresh = poseTime != 0L &&
                kotlin.math.abs(timestamp - poseTime) <= 500_000_000L
            val fieldFresh = fieldTime == 0L ||
                kotlin.math.abs(timestamp - fieldTime) <= 500_000_000L
            val fresh = poseFresh && fieldFresh
            val trusted = fresh && fieldTrusted()
            if (!trusted) goodSince = null else if (goodSince == null) goodSince = timestamp
            // Require a short run of healthy samples before acquiring/reacquiring north.
            val calibrated = trusted && timestamp - (goodSince ?: timestamp) >= 500_000_000L
            val displayRotation = view.display?.rotation ?: 0
            val magnetic = compassBearing(rotationMatrix, displayRotation)
            val heading = filter.update(
                magnetic.takeIf { poseFresh && it.isFinite() },
                declination
            )
            val status = when {
                calibrated && heading != null -> CompassStatus.TRACKING
                heading != null -> CompassStatus.APPROXIMATE
                poseFresh -> CompassStatus.CALIBRATE
                else -> CompassStatus.WAITING
            }
            if (timestamp - lastDiagnosticTime >= 500_000_000L) {
                lastDiagnosticTime = timestamp
                diagnostic = "Compass C3 #$events: $status, magnetic=$magnetic, " +
                    "output=$heading, accuracy=$fieldAccuracy, " +
                    "field=$fieldStrength µT, expected=$expectedField µT, declination=$declination"
                if (debug) Log.d("WorldCompass", diagnostic)
            }
            reading = CompassReading(heading, status, diagnostic)
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                events++
                when (event.sensor.type) {
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        event.values.copyInto(field, endIndex = 3)
                        fieldTime = event.timestamp
                        fieldAccuracy = event.accuracy
                        fieldStrength = sqrt(field.sumOf { (it * it).toDouble() }).toFloat()
                        if (!fieldTrusted()) goodSince = null
                    }
                    Sensor.TYPE_ROTATION_VECTOR -> {
                        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                        poseTime = event.timestamp
                        publish(event.timestamp)
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                if (sensor?.type == Sensor.TYPE_MAGNETIC_FIELD) {
                    fieldAccuracy = accuracy
                    if (!fieldTrusted()) goodSince = null
                }
            }
        }

        fun stop() {
            active = false
            manager.unregisterListener(listener)
        }

        fun start() {
            if (active) return
            filter = CompassHeading()
            fieldTime = 0L
            poseTime = 0L
            goodSince = null
            fieldAccuracy = SensorManager.SENSOR_STATUS_UNRELIABLE
            reading = CompassReading()
            val poseActive = rotationSensor != null && manager.registerListener(
                listener, rotationSensor, SensorManager.SENSOR_DELAY_GAME, handler
            )
            val magneticActive = fieldSensor != null && manager.registerListener(
                listener, fieldSensor, SensorManager.SENSOR_DELAY_GAME, handler
            )
            // Rotation-vector readings can still provide an immediately useful
            // approximate heading when the device has no magnetometer (or its
            // magnetometer is temporarily reporting unreliable accuracy).
            active = poseActive
            if (!active) {
                manager.unregisterListener(listener)
                reading = CompassReading(status = CompassStatus.UNAVAILABLE,
                    diagnostic = "Compass C3: pose=$poseActive magnetic=$magneticActive")
            }
        }

        // A missing/stopped stream must not silently present the last angle as reliable.
        val watchdog = object : Runnable {
            override fun run() {
                if (active) {
                    val now = SystemClock.elapsedRealtimeNanos()
                    if (now - poseTime > 2_000_000_000L || now - fieldTime > 2_000_000_000L) {
                        goodSince = null
                        reading = reading.copy(status = if (reading.heading == null) {
                            CompassStatus.WAITING
                        } else CompassStatus.APPROXIMATE)
                    }
                }
                handler.postDelayed(this, 1000L)
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> start()
                Lifecycle.Event.ON_PAUSE -> stop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) start()
        handler.postDelayed(watchdog, 1000L)
        onDispose {
            lifecycle.removeObserver(observer)
            handler.removeCallbacks(watchdog)
            stop()
        }
    }
    return reading
}
