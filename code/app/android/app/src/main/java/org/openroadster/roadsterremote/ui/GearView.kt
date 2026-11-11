package org.openroadster.roadsterremote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.openroadster.roadsterremote.Gear

@Composable
fun GearView(gear: Gear, onGearChange: (Gear) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        GearButton("P", gear == Gear.Park) { onGearChange(Gear.Park) }
        GearButton("R", gear == Gear.Reverse) { onGearChange(Gear.Reverse) }
        GearButton("D", gear == Gear.Drive) { onGearChange(Gear.Drive) }
    }
}

@Composable
private fun ColumnScope.GearButton(label: String, isSelected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier
            .weight(1f, fill = false)
            .aspectRatio(1f)
            .shadow(3.dp, shape)
            .background(if (isSelected) Color.White else Color.Gray, shape)
            .border(4.dp, if (isSelected) Color.Blue else Color.Gray, shape)
            .semantics { selected = isSelected }
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 60.sp, color = Color.Black)
    }
}
