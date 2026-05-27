package com.prima.barcode.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.prima.barcode.data.model.SyncState
import com.prima.barcode.ui.theme.PrimaPalette
import com.prima.barcode.ui.theme.PrimaStatus
import com.prima.barcode.ui.theme.monoLabel

@Composable
fun SyncPip(state: SyncState, modifier: Modifier = Modifier) {
    val (label, fg, bg) = when (state) {
        is SyncState.Offline    -> Triple("offline",                PrimaPalette.Ink3,     Color(0x0D000000))
        is SyncState.Idle       -> Triple("synced",                 PrimaStatus.Exact,     PrimaStatus.ExactBg)
        is SyncState.Pending    -> Triple("pending",  PrimaStatus.Partial, PrimaStatus.PartialBg)
        is SyncState.Syncing    -> Triple("syncing",  PrimaStatus.Over,    PrimaStatus.OverBg)
        is SyncState.Error      -> Triple("error",    PrimaStatus.Empty,   PrimaStatus.EmptyBg)
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(fg))
        Text(text = label, style = monoLabel.copy(color = fg))
    }
}
