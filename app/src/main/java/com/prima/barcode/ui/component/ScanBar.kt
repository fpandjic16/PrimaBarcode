package com.prima.barcode.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.prima.barcode.data.model.formatQty
import com.prima.barcode.ui.theme.PrimaPalette
import com.prima.barcode.ui.theme.monoLabel

/**
 * Always-docked slate bar at the bottom of the Recording screen.
 * Houses the ScanField and the multiplier control.
 */
@Composable
fun ScanBar(
    multiplier: Double,
    onMultiplierClick: () -> Unit,
    onScan: (String) -> Unit,
    onCameraTap: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = PrimaPalette.Slate,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(containerColor)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "SCAN INPUT",
                style = monoLabel.copy(color = Color(0x88FFFFFF)),
            )
            Spacer(Modifier.weight(1f))
            // Multiplier chip — tap to change
            Row(
                modifier = Modifier
                    .background(
                        color = Color(0x14FFFFFF),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
                    )
                    .clickable(onClick = onMultiplierClick)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("× ${multiplier.formatQty()}", style = monoLabel.copy(color = Color.White))
            }
        }
        ScanField(
            placeholder = "Scan item · or press trigger",
            onScan = onScan,
            onCameraTap = onCameraTap,
            dark = true,
        )
    }
}
