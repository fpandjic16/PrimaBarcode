package com.prima.barcode.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prima.barcode.data.model.DocState
import com.prima.barcode.data.model.Document
import com.prima.barcode.ui.component.PrimaTopBar
import com.prima.barcode.ui.theme.LocalTextSizeOffset
import com.prima.barcode.ui.theme.PrimaPalette
import com.prima.barcode.ui.theme.monoLabel
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.ui.res.stringResource
import com.prima.barcode.R

private val errDateFmt: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(ZoneId.systemDefault())

@Composable
fun UploadErrorScreen(
    document: Document,
    onBack: () -> Unit,
    onRetryUpload: () -> Unit,
) {
    val sizeOffset = LocalTextSizeOffset.current
    val errorReason = (document.state as? DocState.UploadFailed)?.reason ?: "Unknown error"

    Column(modifier = Modifier.fillMaxSize().background(PrimaPalette.Cream)) {
        PrimaTopBar(
            title = document.documentNo,
            subtitle = stringResource(R.string.upload_error_subtitle),
            onBack = onBack,
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Error header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0x12CE3A3A))
                    .border(1.dp, Color(0x28CE3A3A), RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Icon(
                    Icons.Outlined.CloudOff,
                    contentDescription = null,
                    tint = Color(0xFFCE3A3A),
                    modifier = Modifier.size(32.dp),
                )
                Column {
                    Text(
                        stringResource(R.string.upload_error_header),
                        style = monoLabel.copy(
                            color = Color(0xFFCE3A3A),
                            fontWeight = FontWeight.Bold,
                            fontSize = (14 + sizeOffset).sp,
                        ),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        document.documentNo,
                        style = monoLabel.copy(
                            color = Color(0x99CE3A3A),
                            fontSize = (12 + sizeOffset).sp,
                        ),
                    )
                }
            }

            // Document info card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White)
                    .border(1.dp, Color(0x14000000), RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    stringResource(R.string.upload_error_doc_section),
                    style = monoLabel.copy(color = PrimaPalette.Ink3, fontSize = (11 + sizeOffset).sp),
                )
                HorizontalDivider(color = Color(0x0F000000), thickness = 1.dp)
                ErrorInfoRow(stringResource(R.string.upload_error_row_type),   document.type.display, sizeOffset)
                ErrorInfoRow(stringResource(R.string.upload_error_row_source), document.sourceCode,   sizeOffset)
                if (document.destinationCode.isNotBlank())
                    ErrorInfoRow(stringResource(R.string.upload_error_row_destination), document.destinationCode, sizeOffset)
                if (document.rcCode.isNotBlank())
                    ErrorInfoRow(stringResource(R.string.upload_error_row_rc), document.rcCode, sizeOffset)
                ErrorInfoRow(stringResource(R.string.upload_error_row_lines), (if (document.lines.size == 1) stringResource(R.string.upload_error_line_single, document.lines.size) else stringResource(R.string.upload_error_line_plural, document.lines.size)), sizeOffset)
                document.documentDate?.let {
                    ErrorInfoRow(stringResource(R.string.upload_error_row_date), errDateFmt.format(it), sizeOffset)
                }
            }

            // Full error message card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White)
                    .border(1.dp, Color(0x14000000), RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(R.string.upload_error_details),
                    style = monoLabel.copy(color = PrimaPalette.Ink3, fontSize = (11 + sizeOffset).sp),
                )
                HorizontalDivider(color = Color(0x0F000000), thickness = 1.dp)
                Text(
                    errorReason,
                    style = monoLabel.copy(
                        color = PrimaPalette.Ink,
                        fontSize = (13 + sizeOffset).sp,
                        lineHeight = (20 + sizeOffset).sp,
                    ),
                )
            }
        }

        // Bottom action bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Button(
                onClick = onRetryUpload,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaPalette.Coral),
            ) {
                Icon(Icons.Outlined.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.btn_retry_upload), style = monoLabel.copy(color = Color.White, fontWeight = FontWeight.Medium))
            }
        }
    }
}

@Composable
private fun ErrorInfoRow(label: String, value: String, sizeOffset: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            style = monoLabel.copy(color = PrimaPalette.Ink3, fontSize = (12 + sizeOffset).sp),
            modifier = Modifier.weight(0.35f),
        )
        Text(
            value,
            style = monoLabel.copy(color = PrimaPalette.Ink, fontWeight = FontWeight.Medium, fontSize = (12 + sizeOffset).sp),
            modifier = Modifier.weight(0.65f),
        )
    }
}
