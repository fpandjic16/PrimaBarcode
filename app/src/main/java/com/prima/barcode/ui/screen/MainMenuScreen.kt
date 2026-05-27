package com.prima.barcode.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.MoveToInbox
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Store
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.prima.barcode.data.model.DocumentType
import com.prima.barcode.data.model.LineStatus
import com.prima.barcode.data.model.Location
import com.prima.barcode.data.model.ResponsibilityCenter
import com.prima.barcode.data.model.User
import com.prima.barcode.ui.component.DocumentStatsDashboard
import com.prima.barcode.ui.component.PrimaTopBar
import com.prima.barcode.ui.component.verticalScrollbar
import com.prima.barcode.ui.component.StatusProgressBar
import com.prima.barcode.ui.theme.PrimaPalette
import com.prima.barcode.ui.theme.monoLabel
import com.prima.barcode.ui.theme.uppercased
import androidx.compose.ui.res.stringResource
import com.prima.barcode.R

data class DocTypeSummary(
    val type: DocumentType,
    val short: String,
    val count: Int,
    val statusMini: List<LineStatus>,
)

@Composable
fun MainMenuScreen(
    user: User,
    location: Location?,
    rc: ResponsibilityCenter,
    docTypes: List<DocTypeSummary>,
    shiftScans: Int = 0,
    shiftErrors: Int = 0,
    shiftReady: Int = 0,
    shiftPartial: Int = 0,
    onChangeLocationRc: () -> Unit,
    onOpenSettings: () -> Unit,
    onTypeTap: (DocumentType) -> Unit,
    onDocumentOverview: () -> Unit,
    onShowErrors: () -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxSize().background(PrimaPalette.Cream)) {
        PrimaTopBar(
            title = "Prima Barcode",
            subtitle = user.displayName,
            actions = {
                IconButton(onClick = onOpenSettings, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Outlined.Settings, "Settings", tint = Color.White, modifier = Modifier.size(28.dp))
                }
            }
        )

        val listState = rememberLazyListState()
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScrollbar(listState),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            state = listState,
        ) {
            item {
                DocumentStatsDashboard(
                    totalScans = shiftScans,
                    errors = shiftErrors,
                    readyForUpload = shiftReady,
                    partial = shiftPartial,
                    onDocumentOverview = onDocumentOverview,
                )
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                        .clip(RoundedCornerShape(14.dp))
                        .background(PrimaPalette.SlateAlt)
                        .border(1.dp, Color(0x18FFFFFF), RoundedCornerShape(14.dp)),
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(onClick = onChangeLocationRc)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            rc.code,
                            style = monoLabel.copy(color = Color.White, fontWeight = FontWeight.Medium),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(Color(0x33FFFFFF)),
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(onClick = onChangeLocationRc)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            location?.code ?: "—",
                            style = monoLabel.copy(
                                color = if (location != null) Color.White else Color(0x66FFFFFF),
                                fontWeight = FontWeight.Medium,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            item {
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.main_documents_header), style = monoLabel.copy(color = PrimaPalette.Ink3))
            }
            items(docTypes) { dt ->
                DocumentTypeList(summary = dt, onClick = { onTypeTap(dt.type) })
            }
        }
    }
}

@Composable
private fun DocumentTypeList(summary: DocTypeSummary, onClick: () -> Unit) {
    val icon: ImageVector = when (summary.type) {
        DocumentType.WAREHOUSE_SHIPMENT, DocumentType.RETAIL_SHIPMENT -> Icons.Outlined.LocalShipping
        DocumentType.WAREHOUSE_RECEIPT                                -> Icons.Outlined.MoveToInbox
        DocumentType.RETAIL_RECEIPT                                   -> Icons.Outlined.Store
        DocumentType.TRANSPORT_SHEET                                  -> Icons.Outlined.Description
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .border(1.dp, Color(0x18000000), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(PrimaPalette.CreamAlt),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = PrimaPalette.Slate) }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(summary.type.display.uppercased, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium))
            if (summary.statusMini.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                StatusProgressBar(segments = summary.statusMini, height = 4.dp, gap = 2.dp)
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            summary.count.toString(),
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = com.prima.barcode.ui.theme.GeistMono,
                color = if (summary.count > 0) PrimaPalette.Ink else PrimaPalette.Ink4,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}
