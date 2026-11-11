package org.openroadster.roadsterremote.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer

/**
 * A car-style fuel gauge for the battery: a 100° arc from E to F. Angles are
 * degrees clockwise from 12 o'clock, so the arc runs from 310° through 0° to 50°.
 */
@Composable
fun FuelGaugeView(batteryLevel: Int, isCharging: Boolean, modifier: Modifier = Modifier) {
    val textMeasurer = rememberTextMeasurer()
    val bolt = rememberVectorPainter(Icons.Filled.Bolt)

    Canvas(
        modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "Battery $batteryLevel percent" + if (isCharging) ", charging" else ""
            }
    ) {
        val w = size.width
        val h = size.height
        val fontSize = h / 5
        val lineWeight = h / 10
        val label = TextStyle(color = Color.White, fontSize = fontSize.toSp(), fontWeight = FontWeight.Bold)

        fun drawCentered(text: String, xP: Float, yP: Float) {
            val layout = textMeasurer.measure(text, label)
            drawText(
                layout,
                topLeft = Offset(
                    w / 2 + w * xP - layout.size.width / 2,
                    h / 2 + h * yP - layout.size.height / 2,
                ),
            )
        }

        drawCentered("F", 0.43f, 0.27f)
        drawCentered("E", -0.43f, 0.27f)

        // ticks
        for ((angle, weight) in listOf(310f to 3, 334f to 2, 359f to 3, 24f to 2, 48f to 3)) {
            gaugeArc(angle, 2f, lineWeight * weight, Color.White)
        }
        gaugeArc(310f, 100f, lineWeight, Color.Gray)
        gaugeArc(310f, batteryLevel.toFloat(), lineWeight, gaugeColor(batteryLevel))

        if (isCharging) {
            val boltSize = fontSize * 1.2f
            translate(w / 2 - boltSize / 2, h / 2 + h * 0.27f - boltSize / 2) {
                with(bolt) { draw(Size(boltSize, boltSize), colorFilter = ColorFilter.tint(Color.Yellow)) }
            }
        }
    }
}

/** An arc on the gauge's circle: as wide as the view, centered below the middle */
private fun DrawScope.gaugeArc(startAngle: Float, sweep: Float, lineWeight: Float, color: Color) {
    val w = size.width
    val radius = w / 2
    val center = Offset(w / 2, size.height / 2 + (w / 2 - w / 10))
    drawArc(
        color,
        startAngle = startAngle - 90,
        sweepAngle = sweep,
        useCenter = false,
        topLeft = Offset(center.x - radius, center.y - radius),
        size = Size(radius * 2, radius * 2),
        style = Stroke(lineWeight),
    )
}

// Red when empty through to green when full
private fun gaugeColor(batteryLevel: Int) =
    Color.hsv((batteryLevel / 300f * 360).coerceIn(0f, 360f), 1f, 1f)
