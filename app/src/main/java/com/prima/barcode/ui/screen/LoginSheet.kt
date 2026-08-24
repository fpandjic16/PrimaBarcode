package com.prima.barcode.ui.screen

import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.prima.barcode.ui.component.PrimaTopBar
import com.prima.barcode.ui.theme.PrimaPalette
import com.prima.barcode.ui.theme.monoLabel
import androidx.compose.ui.res.stringResource
import com.prima.barcode.R

@Composable
fun LoginSheet(
    credentialTtlHours: Int = 24,
    onSubmit: (username: String, password: String) -> Unit,
    onDismiss: () -> Unit,
    ctaLabel: String = stringResource(R.string.btn_sign_in),
    initialUsername: String = "",
    initialPassword: String = "",
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
    var testError by remember { mutableStateOf<String?>(null) }

    fun submit() {
        val test = onTestConnection
        if (test == null) {
            onSubmit(username.trim(), password)
            return
        }
        testError = null
        testing = true
        test(username.trim(), password) { success, error ->
            testing = false
            if (success) onSubmit(username.trim(), password)
            else testError = error ?: "Could not connect. Check your username and password and try again."
        }
    }

    val ttlLabel = if (credentialTtlHours == 168) stringResource(R.string.login_ttl_days, 7)
        else stringResource(R.string.login_ttl_hours, credentialTtlHours)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        (LocalView.current.parent as? DialogWindowProvider)?.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
        )
        Column(modifier = Modifier.fillMaxSize().background(PrimaPalette.Cream)) {
            PrimaTopBar(
                title = stringResource(R.string.login_title),
                onBack = onDismiss,
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
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
                testError?.let {
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
    }
}
