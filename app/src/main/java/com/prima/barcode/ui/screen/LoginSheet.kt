package com.prima.barcode.ui.screen

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.view.Window
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.prima.barcode.data.auth.parseLoginQr
import com.prima.barcode.data.barcode.DataWedgeManager
import com.prima.barcode.ui.component.CameraPreview
import com.prima.barcode.ui.component.PrimaTopBar
import com.prima.barcode.ui.component.verticalScrollbar
import com.prima.barcode.ui.theme.PrimaPalette
import com.prima.barcode.ui.theme.monoLabel
import androidx.compose.ui.res.stringResource
import com.prima.barcode.R

/**
 * Puts the window in resize-on-keyboard mode.
 *
 * `SOFT_INPUT_ADJUST_RESIZE` is deprecated as of API 30, and its documented replacement is the
 * `setDecorFitsSystemWindows(false)` + inset handling the caller already does. Below API 30 that
 * replacement is not enough on its own: `WindowInsets.ime` — and so `Modifier.imePadding()` —
 * only sees the keyboard while the window is in adjust-resize mode. The MC3300 runs API 27, so
 * dropping the flag would quietly bring back the bug this whole window setup exists to fix, with
 * the password field sitting behind the keyboard. It stays until minSdk reaches 30.
 *
 * Suppressed in its own function rather than at the call site, so the suppression covers this one
 * line and doesn't hide some later deprecation inside [LoginSheet].
 */
@Suppress("DEPRECATION")
private fun Window.resizeForSoftInput() =
    setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

@Composable
fun LoginSheet(
    credentialTtlHours: Int = 24,
    onSubmit: (username: String, password: String) -> Unit,
    onDismiss: () -> Unit,
    ctaLabel: String = stringResource(R.string.btn_sign_in),
    initialUsername: String = "",
    initialPassword: String = "",
    // ExtSystemConfig.loginQrKey. Blank (an install that hasn't loaded a configuration carrying
    // one) leaves the QR button working but every code refused, which the error line reports.
    loginQrKey: String = "",
    // When set, the entered credentials are verified against the NAV server (the same
    // check as ExtSystemConfigScreen's "Test connection") before onSubmit is called, so
    // signing in actually confirms the server accepted them rather than just capturing
    // whatever was typed. Left null for flows that already do their own testing (e.g.
    // ExtSystemConfigScreen's own "Test connection" button reuses this sheet directly).
    onTestConnection: ((username: String, password: String, onResult: (success: Boolean, error: String?) -> Unit) -> Unit)? = null,
) {
    var username by remember { mutableStateOf(initialUsername) }
    var password by remember { mutableStateOf(initialPassword) }
    var visible  by remember { mutableStateOf(false) }
    var testing  by remember { mutableStateOf(false) }
    // One error line for both failure modes — a rejected sign-in and an unusable QR code.
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var cameraOpen by remember { mutableStateOf(false) }

    val context = LocalContext.current
    // Resolved here because the camera callback below isn't a composable scope.
    val connectFailedMessage = stringResource(R.string.login_connect_failed)
    val invalidQrMessage = stringResource(R.string.login_qr_invalid)

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) cameraOpen = true }

    // The MC3300 ships in configurations with no camera at all, where the button used to open a
    // black overlay with nothing behind it. Hardware scanning below still covers those devices.
    val hasCamera = remember {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    }

    /** One path for both scanners: the camera and the hardware trigger read the same code. */
    fun applyScannedQr(raw: String) {
        // Any symbology can land here — an ordinary product barcode pulled on the trigger, or a
        // code made under a different key. Say so rather than leaving the fields unchanged.
        val scanned = parseLoginQr(raw, loginQrKey)
        if (scanned != null) {
            username = scanned.username
            password = scanned.password
            errorMessage = null
        } else {
            errorMessage = invalidQrMessage
        }
    }

    // Registered for the sheet's lifetime rather than behind a button: on a barcode terminal the
    // trigger is the natural gesture, and DataWedge already routes scans to the whole app
    // (ACTIVITY_LIST "*"), so nothing but a listener was missing. No other screen that hosts this
    // sheet registers the same receiver, so there is no double delivery.
    val latestScan = rememberUpdatedState(::applyScannedQr)
    DisposableEffect(Unit) {
        val receiver = DataWedgeManager.createReceiver { raw -> latestScan.value(raw) }
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, DataWedgeManager.intentFilter(), Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, DataWedgeManager.intentFilter())
        }
        onDispose { context.unregisterReceiver(receiver) }
    }

    fun submit() {
        val test = onTestConnection
        if (test == null) {
            onSubmit(username.trim(), password)
            return
        }
        errorMessage = null
        testing = true
        test(username.trim(), password) { success, error ->
            testing = false
            if (success) onSubmit(username.trim(), password)
            else errorMessage = error ?: connectFailedMessage
        }
    }

    val ttlLabel = if (credentialTtlHours == 168) stringResource(R.string.login_ttl_days, 7)
        else stringResource(R.string.login_ttl_hours, credentialTtlHours)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        (LocalView.current.parent as? DialogWindowProvider)?.window?.let { window ->
            window.setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
            )
            // This dialog has its own window, separate from the Activity's — enableEdgeToEdge()
            // in MainActivity.onCreate() never reaches it, so without these two calls Compose's
            // WindowInsets/imePadding() below can't see this window's own keyboard state, and the
            // password field ends up hidden behind the IME on small screens (e.g. the MC3300).
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.resizeForSoftInput()
        }
        // Box so the camera overlay below can sit on top of the form rather than beside it.
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().background(PrimaPalette.Cream)) {
                PrimaTopBar(
                    title = stringResource(R.string.login_title),
                    onBack = onDismiss,
                )
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding()
                        .verticalScrollbar(scrollState)
                        .verticalScroll(scrollState)
                        .padding(horizontal = 22.dp)
                        .padding(top = 24.dp, bottom = 32.dp),
                ) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text(stringResource(R.string.login_username)) },
                        placeholder = { Text(stringResource(R.string.login_username_hint)) },
                        singleLine = true,
                        enabled = !testing,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(R.string.login_password)) },
                        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { visible = !visible }) {
                                Icon(
                                    if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = if (visible) stringResource(R.string.login_hide_password) else stringResource(R.string.login_show_password),
                                )
                            }
                        },
                        singleLine = true,
                        enabled = !testing,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (hasCamera) {
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = {
                                errorMessage = null
                                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                                    PackageManager.PERMISSION_GRANTED
                                if (granted) cameraOpen = true
                                else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            },
                            enabled = !testing,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                        ) {
                            Icon(
                                Icons.Outlined.QrCodeScanner,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.login_scan_qr), fontWeight = FontWeight.Medium)
                        }
                    }

                    errorMessage?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, style = monoLabel, color = Color(0xFFCE3A3A))
                    }

                    Spacer(Modifier.height(20.dp))

                    Button(
                        onClick = ::submit,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        enabled = username.isNotBlank() && password.isNotBlank() && !testing,
                    ) {
                        if (testing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp,
                            )
                            return@Button
                        }
                        Text(ctaLabel, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        stringResource(R.string.login_footer, ttlLabel),
                        style = monoLabel,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }
            }

            // Drawn last so it covers the form. One read closes it.
            if (cameraOpen) {
                CameraPreview(
                    onBarcode = { raw ->
                        applyScannedQr(raw)
                        cameraOpen = false
                    },
                    onClose = { cameraOpen = false },
                )
            }
        }
    }
}
