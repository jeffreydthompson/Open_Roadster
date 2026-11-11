package org.openroadster.roadsterremote.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription

private fun white(level: Float) = Color(level, level, level)

private val trackGradient = Brush.verticalGradient(
    listOf(white(0.7f), white(0.8f), white(0.7f), white(0.5f))
)

/**
 * A spring-loaded slider: drag the knob up for 0...100 throttle, and it
 * snaps back to 0 when you let go.
 */
@Composable
fun ThrottleView(throttle: Int, onThrottleChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val onChange by rememberUpdatedState(onThrottleChange)

    Canvas(
        modifier
            .semantics {
                contentDescription = "Throttle"
                stateDescription = "$throttle percent"
            }
            .pointerInput(Unit) {
                val knobRadius = size.width / 2f - size.width / 10f
                val travel = size.height - knobRadius * 2

                fun throttleAt(y: Float): Int {
                    val fromBottom = (size.height - y).coerceIn(knobRadius, size.height - knobRadius)
                    return ((fromBottom - knobRadius) / travel * 100).toInt()
                }

                awaitEachGesture {
                    val down = awaitFirstDown()
                    onChange(throttleAt(down.position.y))
                    while (true) {
                        val event = awaitPointerEvent()
                        val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!pointer.pressed) break
                        pointer.consume()
                        onChange(throttleAt(pointer.position.y))
                    }
                    onChange(0)
                }
            }
    ) {
        val w = size.width
        val h = size.height
        val pad = w / 10
        val knobRadius = w / 2 - pad

        drawRect(trackGradient)

        // Slot the knob runs in
        val slot = Size(w * 0.15f, h * 0.9f)
        drawRect(white(0.15f), Offset((w - slot.width) / 2, (h - slot.height) / 2), slot)

        // Knob
        val center = Offset(w / 2, h - knobRadius - throttle / 100f * (h - knobRadius * 2))
        drawCircle(
            Brush.radialGradient(listOf(Color.Black, white(0.9f)), center, w),
            knobRadius,
            center,
        )
        drawCircle(
            Brush.radialGradient(listOf(Color.Gray, white(0.9f)), center, w),
            knobRadius - pad,
            center,
        )
    }
}
