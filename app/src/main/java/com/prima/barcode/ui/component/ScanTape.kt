package com.prima.barcode.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.prima.barcode.data.model.TapeEntry
import com.prima.barcode.data.model.color
import com.prima.barcode.data.model.formatQty
import com.prima.barcode.ui.theme.PrimaPalette
import com.prima.barcode.ui.theme.monoLabel
import kotlinx.coroutines.delay
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    .withZone(ZoneId.systemDefault())

/**
 * Collapsible strip showing the last [maxLines] scans.
 * If [maxLines] is 0, this composable emits nothing.
 * Auto-collapses after 20 s of no new entries.
 */
@Composable
fun ScanTape(
    tape: List<TapeEntry>,
    maxLines: Int = 5,
    modifier: Modifier = Modifier,
) {
    if (maxLines == 0 || tape.isEmpty()) return

    var expanded by remember { mutableStateOf(true) }

    LaunchedEffect(tape) {
        expanded = true
        delay(20_000)
        expanded = false
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(PrimaPalette.SlateAlt),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "LAST SCANS",
                style = monoLabel.copy(color = Color(0x88FFFFFF)),
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = Color(0x88FFFFFF),
                modifier = Modifier.size(16.dp),
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column {
                tape.take(maxLines).forEach { entry ->
                    TapeRow(entry = entry)
                }
            }
        }
    }
}

@Composable
private fun TapeRow(entry: TapeEntry) {
    val stripe = if (entry.isError) Color(0xFFCE3A3A) else (entry.lineStatus?.color ?: Color.Transparent)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0x0AFFFFFF))
            .padding(start = 0.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(44.dp)
                .background(stripe),
        )
        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(
                entry.barcode,
                style = monoLabel.copy(color = Color.White, fontWeight = FontWeight.Medium),
            )
            Text(
                entry.itemName ?: if (entry.isError) "Not found in document" else "Unknown item",
                style = monoLabel.copy(
                    color = if (entry.isError) Color(0xFFCE3A3A) else Color(0xAAFFFFFF),
                ),
            )
        }

        Text(
            "×${entry.quantity.formatQty()}",
            style = monoLabel.copy(color = Color(0xCCFFFFFF), fontWeight = FontWeight.Medium),
            modifier = Modifier.padding(horizontal = 8.dp),
        )

        Text(
            timeFmt.format(entry.at),
            style = monoLabel.copy(color = Color(0x66FFFFFF)),
        )

    }
}
