package com.prima.barcode.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.prima.barcode.ui.theme.monoLabel
import androidx.compose.ui.res.stringResource
import com.prima.barcode.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginSheet(
    initialDomain: String = "",
    credentialTtlHours: Int = 24,
    onSubmit: (domain: String, username: String, password: String) -> Unit,
    onDismiss: () -> Unit,
    ctaLabel: String = "Sign in",
) {
    var domain   by remember { mutableStateOf(initialDomain) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var visible  by remember { mutableStateOf(false) }

    val ttlLabel = if (credentialTtlHours == 168) stringResource(R.string.login_ttl_days, 7)
        else stringResource(R.string.login_ttl_hours, credentialTtlHours)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(modifier = Modifier.padding(horizontal = 22.dp).padding(top = 8.dp, bottom = 32.dp)) {
            Text(stringResource(R.string.login_title), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
            Text(
                stringResource(R.string.login_session_info, ttlLabel),
                style = monoLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))

            OutlinedTextField(
                value = domain,
                onValueChange = { domain = it },
                label = { Text(stringResource(R.string.login_domain)) },
                placeholder = { Text("e.g. PRIMA") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text(stringResource(R.string.login_username)) },
                singleLine = true,
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
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = { onSubmit(domain.trim(), username.trim(), password) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = domain.isNotBlank() && username.isNotBlank() && password.isNotBlank(),
            ) {
                Text(ctaLabel, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.login_footer),
                style = monoLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}
