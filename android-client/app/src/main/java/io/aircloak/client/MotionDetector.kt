package io.aircloak.client

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import kotlin.math.sqrt

/**
 * MotionDetector
 * Hardware IMU accelerometer listener configured for two-axis rapid jerk gestures.
 * When triggered, it executes an immediate Zeroize/Purge of the Android clipboard and memory registers.
 */
class MotionDetector(
    private val context: Context,
    private val onEmergencyPurgeTriggered: () -> Unit
) : SensorEventListener {

    companion object {
        private const val TAG = "AirCloakMotion"
        // Acceleration threshold in m/s^2 above standard gravity (1G ≈ 9.81)
        private const val SHAKE_THRESHOLD_G_FORCE = 2.7f
        private const val SHAKE_SLOP_TIME_MS = 500
        private const val SHAKE_COUNT_RESET_TIME_MS = 3000
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    private var shakeTimestamp: Long = 0
    private var shakeCount: Int = 0

    fun startListening() {
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            Log.i(TAG, "MotionDetector active. Listening for emergency shake purge.")
        } ?: Log.e(TAG, "Accelerometer hardware sensor not available on device.")
    }

    fun stopListening() {
        sensorManager.unregisterListener(this)
        Log.i(TAG, "MotionDetector paused.")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        val gX = x / SensorManager.GRAVITY_EARTH
        val gY = y / SensorManager.GRAVITY_EARTH
        val gZ = z / SensorManager.GRAVITY_EARTH

        // Calculate gForce scalar vector (focusing on X & Y rapid lateral shake)
        val gForce = sqrt((gX * gX + gY * gY + gZ * gZ).toDouble()).toFloat()

        if (gForce > SHAKE_THRESHOLD_G_FORCE) {
            val now = System.currentTimeMillis()

            if (shakeTimestamp + SHAKE_SLOP_TIME_MS > now) {
                return
            }

            if (shakeTimestamp + SHAKE_COUNT_RESET_TIME_MS < now) {
                shakeCount = 0
            }

            shakeTimestamp = now
            shakeCount++

            Log.d(TAG, "Shake detected! Count: $shakeCount, Force: $gForce")

            // Trigger emergency purge on double rapid shake
            if (shakeCount >= 2) {
                shakeCount = 0
                executeEmergencyPurge()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    /**
     * Instantly zeros out system clipboard and gives high-priority haptic pulse.
     */
    private fun executeEmergencyPurge() {
        Log.w(TAG, "EMERGENCY SHAKE TRIGGERED: Zeroizing clipboard & local memory.")

        try {
            // Overwrite clipboard with blank data
            val emptyClip = ClipData.newPlainText("", "")
            clipboardManager.setPrimaryClip(emptyClip)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                clipboardManager.clearPrimaryClip()
            }

            // Haptic Feedback Alert
            triggerHapticAlert()

            // Invoke Callback
            onEmergencyPurgeTriggered()
        } catch (e: Exception) {
            Log.error(TAG, "Error executing emergency clipboard purge", e)
        }
    }

    private fun triggerHapticAlert() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            val vibrator = vibratorManager.defaultVibrator
            vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 150, 80, 250), -1))
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(300)
            }
        }
    }
}

private fun Log.Companion.error(tag: String, msg: String, tr: Throwable) {
    Log.e(tag, msg, tr)
}
