package com.prima.barcode.ui.component

import android.content.Context
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.TypedValue
import android.view.KeyEvent
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.widget.addTextChangedListener
import com.prima.barcode.ui.theme.PrimaPalette

/**
 * Scan input that captures the hardware scanner without ever auto-opening the
 * on-screen keyboard.
 *
 * Backed by a native [EditText] with `showSoftInputOnFocus = false`: it auto-focuses
 * and reliably receives Zebra/Honeywell wedge keystrokes, but the soft keyboard
 * stays hidden. The whole barcode arrives as a rapid burst, so input is auto-
 * submitted once it settles (and immediately on an Enter/Tab terminator if one is
 * sent). Tapping the keyboard icon shows the soft keyboard for manual entry; the
 * camera icon opens the CameraX fallback.
 */
@Composable
fun ScanField(
    placeholder: String,
    onScan: (String) -> Unit,
    onCameraTap: () -> Unit,
    modifier: Modifier = Modifier,
    dark: Boolean = false,
) {
    val context = LocalContext.current
    val imm = remember { context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager }
    val currentOnScan by rememberUpdatedState(onScan)
    val handler = remember { Handler(Looper.getMainLooper()) }
    // Distinguishes deliberate manual typing (keyboard shown) from a scanner burst.
    val manualTyping = remember { mutableStateOf(false) }

    val textColor = (if (dark) Color.White else PrimaPalette.Ink).toArgb()
    val hintColor = (if (dark) Color(0x66FFFFFF) else PrimaPalette.Ink4).toArgb()

    val editText = remember {
        EditText(context).apply {
            isSingleLine = true
            background = null
            setPadding(0, 0, 0, 0)
            gravity = Gravity.CENTER_VERTICAL
            typeface = Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = EditorInfo.IME_ACTION_DONE
            showSoftInputOnFocus = false
            isFocusableInTouchMode = true
        }
    }

    DisposableEffect(editText) {
        fun submit() {
            val code = editText.text.toString().trim()
            editText.text?.clear()
            handler.removeCallbacksAndMessages(null)
            if (manualTyping.value) {
                manualTyping.value = false
                editText.showSoftInputOnFocus = false
                imm.hideSoftInputFromWindow(editText.windowToken, 0)
            }
            if (code.isNotEmpty()) currentOnScan(code)
        }
        val idle = Runnable { if (!manualTyping.value && !editText.text.isNullOrEmpty()) submit() }

        editText.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN &&
                (keyCode == KeyEvent.KEYCODE_ENTER ||
                 keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER ||
                 keyCode == KeyEvent.KEYCODE_TAB)
            ) { submit(); true } else false
        }
        editText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { submit(); true } else false
        }
        val watcher = editText.addTextChangedListener(afterTextChanged = {
            handler.removeCallbacks(idle)
            // Auto-accept a scanner burst once it goes idle; manual typing waits for Done.
            if (!manualTyping.value && !editText.text.isNullOrEmpty()) {
                handler.postDelayed(idle, 200)
            }
        })
        editText.requestFocus()

        onDispose {
            handler.removeCallbacksAndMessages(null)
            editText.removeTextChangedListener(watcher)
            editText.setOnKeyListener(null)
            editText.setOnEditorActionListener(null)
        }
    }

    // Re-assert focus whenever the field (re)enters composition so the scanner works
    // without the user tapping first.
    LaunchedEffect(Unit) { editText.requestFocus() }

    fun showKeyboard() {
        manualTyping.value = true
        editText.showSoftInputOnFocus = true
        editText.requestFocus()
        imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
    }

    val bg = if (dark) Color(0x14FFFFFF) else PrimaPalette.Cream
    val borderColor = if (dark) Color(0x1AFFFFFF) else Color(0x18000000)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AndroidView(
            factory = { editText },
            modifier = Modifier.weight(1f),
            update = { field ->
                field.setTextColor(textColor)
                field.setHintTextColor(hintColor)
                field.hint = placeholder
            },
        )
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (dark) Color(0x22FFFFFF) else PrimaPalette.CreamAlt)
                .clickable { showKeyboard() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Keyboard,
                contentDescription = "Show keyboard",
                tint = if (dark) Color.White else PrimaPalette.Slate,
            )
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
