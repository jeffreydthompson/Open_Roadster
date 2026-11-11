package org.openroadster.roadsterremote.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp

private val ledOff = Color.hsv(0.33f * 360, 0.5f, 0.3f)
private val ledOn = Color.hsv(0.33f * 360, 1.0f, 1.0f)

@Composable
fun ConnectionView(connected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxHeight()
            .clearAndSetSemantics {
                contentDescription = if (connected) "Connected. Change car" else "Not connected. Select car"
                role = Role.Button
            }
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.Filled.Sensors,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier
                .fillMaxHeight()
                .aspectRatio(1f),
        )
        Canvas(
            Modifier
                .fillMaxHeight()
                .aspectRatio(1f)
                .shadow(1.dp, CircleShape)
        ) {
            drawCircle(Color.Gray)
            drawCircle(if (connected) ledOn else ledOff, radius = size.minDimension / 2 * 0.8f)
        }
    }
}
