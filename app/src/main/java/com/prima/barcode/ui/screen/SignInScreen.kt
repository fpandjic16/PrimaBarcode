package com.prima.barcode.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.prima.barcode.R
import com.prima.barcode.data.auth.UserProfile
import com.prima.barcode.data.auth.UserProfileStore
import com.prima.barcode.ui.component.PrimaTopBar
import com.prima.barcode.ui.theme.PrimaPalette
import com.prima.barcode.ui.theme.monoLabel

/**
 * The way into the app. Nothing else is reachable until somebody is signed in.
 *
 * This is not only a gate. Every recording stamps the operator who made it at the moment it is
 * written, so the app has to know who that is *before* the first scan — filling it in afterwards
 * is not possible, because by then the scan is already on disk and there is nothing to say who
 * made it. That is what the empty `userId` used to be.
 *
 * Known operators are listed so the common case is a tap and a password rather than typing a
 * username on a device with no comfortable keyboard.
 *
 * The one thing reachable from here without signing in is the external-system configuration — see
 * [onOpenConfig].
 */
@Composable
fun SignInScreen(
    profiles: List<UserProfile>,
    onSignIn: (username: String, password: String) -> Unit,
    busy: Boolean = false,
    error: String? = null,
    onErrorDismiss: () -> Unit = {},
    onBack: (() -> Unit)? = null,
    // Opens the external-system configuration without signing in first; null hides the button.
    // The chicken and egg it breaks: signing in asks the server whether the password is good,
    // and a device out of the box has no server URL to ask, so without a way through from here
    // a fresh install could never be configured and so could never be signed into either.
    onOpenConfig: (() -> Unit)? = null,
) {
    var username by remember { mutableStateOf(profiles.firstOrNull()?.displayName.orEmpty()) }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    val tooLong = username.trim().length > UserProfileStore.MAX_NAME_LENGTH
    val canSubmit = !busy && username.isNotBlank() && password.isNotEmpty() && !tooLong

    Column(modifier = Modifier.fillMaxSize().background(PrimaPalette.Cream)) {
        PrimaTopBar(title = stringResource(R.string.signin_title), onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (profiles.isNotEmpty()) {
                Text(
                    stringResource(R.string.signin_known_operators),
                    style = monoLabel.copy(color = PrimaPalette.Ink3),
                )
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(profiles, key = { it.id }) { profile ->
                        ProfileRow(
                            profile = profile,
                            selected = UserProfileStore.normalise(username) == profile.id,
                            onClick = { username = profile.displayName; password = "" },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text(stringResource(R.string.login_username)) },
                singleLine = true,
                isError = tooLong,
                modifier = Modifier.fillMaxWidth(),
            )
            if (tooLong) {
                Text(
                    stringResource(R.string.signin_name_too_long, UserProfileStore.MAX_NAME_LENGTH),
                    style = monoLabel.copy(color = Color(0xFFCE3A3A)),
                )
            }

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.login_password)) },
                singleLine = true,
                visualTransformation =
                    if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            if (showPassword) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = null,
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                stringResource(R.string.signin_first_time_note),
                style = monoLabel.copy(color = PrimaPalette.Ink3),
            )

            onOpenConfig?.let { open ->
                OutlinedButton(
                    onClick = open,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Icon(
                        Icons.Outlined.Settings,
                        contentDescription = null,
                        tint = PrimaPalette.Ink3,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(R.string.signin_open_config),
                        style = monoLabel.copy(color = PrimaPalette.Ink),
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .height(64.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (canSubmit) PrimaPalette.Coral else PrimaPalette.Coral.copy(alpha = 0.35f))
                .clickable(enabled = canSubmit) { onSignIn(username.trim(), password) },
            contentAlignment = Alignment.Center,
        ) {
            if (busy) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
            } else {
                Text(
                    stringResource(R.string.signin_action),
                    style = monoLabel.copy(color = Color.White, fontWeight = FontWeight.Medium),
                )
            }
        }
    }

    error?.let { message ->
        AlertDialog(
            onDismissRequest = onErrorDismiss,
            title = { Text(stringResource(R.string.signin_failed_title), fontWeight = FontWeight.Bold) },
            text = { Text(message) },
            confirmButton = {
                Button(onClick = onErrorDismiss) { Text(stringResource(R.string.btn_ok)) }
            },
        )
    }
}

@Composable
private fun ProfileRow(profile: UserProfile, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) PrimaPalette.CreamAlt else Color.White)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(32.dp).clip(CircleShape).background(PrimaPalette.Coral),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                profile.displayName.take(2).uppercase(),
                style = monoLabel.copy(color = Color.White, fontWeight = FontWeight.Bold),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(profile.displayName, style = monoLabel.copy(color = PrimaPalette.Ink))
    }
}
