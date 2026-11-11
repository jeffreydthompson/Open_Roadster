package org.openroadster.roadsterremote.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/** A headlight with high-beam rays; filled and yellow when on */
@Composable
fun HeadlightControlView(isOn: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val color = if (isOn) Color.Yellow else Color.Gray

    Canvas(
        modifier
            .fillMaxHeight()
            .aspectRatio(1.3f)
            .semantics { contentDescription = if (isOn) "Headlights on" else "Headlights off" }
            .clickable(role = Role.Switch, onClick = onToggle)
    ) {
        val w = size.width
        val h = size.height
        val line = h / 10

        // Lamp: flat face on the left, rounded back on the right
        val lamp = Path().apply {
            moveTo(w * 0.55f, h * 0.15f)
            cubicTo(w * 1.0f, h * 0.15f, w * 1.0f, h * 0.85f, w * 0.55f, h * 0.85f)
            close()
        }
        drawPath(lamp, color, style = if (isOn) Fill else Stroke(line))
        if (isOn) drawPath(lamp, color, style = Stroke(line))

        // Rays
        for (y in listOf(0.22f, 0.4f, 0.6f, 0.78f)) {
            drawLine(color, Offset(w * 0.05f, h * y), Offset(w * 0.4f, h * y), line, StrokeCap.Round)
        }
    }
}
