package com.prima.barcode.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.prima.barcode.ui.theme.GeistMono
import com.prima.barcode.ui.theme.PrimaPalette

/**
 * Always-focused scan input. Accepts:
 *  - Hardware scanner keystrokes ending in newline or tab (Zebra/Honeywell wedge mode)
 *  - On-screen keyboard input
 *  - Camera fallback when the camera icon is tapped
 *
 * @param onScan Fired when a complete scan terminator is received.
 * @param onCameraTap Fired when user taps the camera icon — open CameraX preview.
 */
@Composable
fun ScanField(
    placeholder: String,
    onScan: (String) -> Unit,
    onCameraTap: () -> Unit,
    modifier: Modifier = Modifier,
    dark: Boolean = false,
) {
    var buffer by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) { focus.requestFocus() }

    fun submit() {
        val code = buffer.trim()
        if (code.isNotEmpty()) onScan(code)
        buffer = ""
    }

    val bg = if (dark) Color(0x14FFFFFF) else PrimaPalette.Cream
    val borderColor = if (dark) Color(0x1AFFFFFF) else Color(0x18000000)
    val placeholderColor = if (dark) Color(0x66FFFFFF) else PrimaPalette.Ink4
    val textColor = if (dark) Color.White else PrimaPalette.Ink

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f)) {
            BasicTextField(
                value = buffer,
                onValueChange = { next ->
                    if (next.endsWith('\n') || next.endsWith('\t')) {
                        buffer = next.trimEnd()
                        submit()
                    } else {
                        buffer = next
                    }
                },
                singleLine = true,
                textStyle = TextStyle(
                    fontFamily = GeistMono,
                    fontWeight = FontWeight.Normal,
                    fontSize   = androidx.compose.ui.unit.TextUnit.Unspecified,
                    color = textColor,
                ),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrect = false,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.focusRequester(focus).fillMaxWidth(),
            )
            if (buffer.isEmpty()) {
                Text(text = placeholder, color = placeholderColor)
            }
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (dark) PrimaPalette.Coral else PrimaPalette.Slate)
                .clickable(onClick = onCameraTap),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.PhotoCamera,
                contentDescription = "Open camera",
                tint = Color.White,
            )
        }
    }
}
