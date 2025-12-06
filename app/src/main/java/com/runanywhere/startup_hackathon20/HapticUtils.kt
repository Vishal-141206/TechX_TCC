package com.runanywhere.startup_hackathon20

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Utility for haptic feedback throughout the app
 */
object HapticFeedback {

    /**
     * Light tap feedback for button presses
     */
    fun lightTap(context: Context) {
        vibrate(context, 10, VibrationEffect.DEFAULT_AMPLITUDE)
    }

    /**
     * Medium feedback for successful actions
     */
    fun success(context: Context) {
        vibrate(context, 50, VibrationEffect.DEFAULT_AMPLITUDE)
    }

    /**
     * Warning feedback for errors or warnings
     */
    fun warning(context: Context) {
        vibratePattern(context, longArrayOf(0, 100, 50, 100))
    }

    /**
     * Error feedback for critical errors
     */
    fun error(context: Context) {
        vibratePattern(context, longArrayOf(0, 200, 100, 200, 100, 200))
    }

    /**
     * Tick feedback for incremental actions (like scrolling through options)
     */
    fun tick(context: Context) {
        vibrate(context, 5, VibrationEffect.DEFAULT_AMPLITUDE)
    }

    private fun vibrate(context: Context, duration: Long, amplitude: Int) {
        try {
            val vibrator = getVibrator(context)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = VibrationEffect.createOneShot(duration, amplitude)
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(duration)
            }
        } catch (e: Exception) {
            // Vibration not available on this device
        }
    }

    private fun vibratePattern(context: Context, pattern: LongArray) {
        try {
            val vibrator = getVibrator(context)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = VibrationEffect.createWaveform(pattern, -1)
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, -1)
            }
        } catch (e: Exception) {
            // Vibration not available on this device
        }
    }

    @Suppress("DEPRECATION")
    private fun getVibrator(context: Context): Vibrator {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager =
                context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }
}

/**
 * Composable helper to remember vibrator in composables
 */
@Composable
fun rememberHaptic(): HapticHelper {
    val context = LocalContext.current
    return remember { HapticHelper(context) }
}

/**
 * Helper class for easier usage in composables
 */
class HapticHelper(private val context: Context) {
    fun lightTap() = HapticFeedback.lightTap(context)
    fun success() = HapticFeedback.success(context)
    fun warning() = HapticFeedback.warning(context)
    fun error() = HapticFeedback.error(context)
    fun tick() = HapticFeedback.tick(context)
}
