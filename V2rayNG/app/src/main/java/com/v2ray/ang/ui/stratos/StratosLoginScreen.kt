package com.v2ray.ang.ui.stratos

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import kotlinx.coroutines.flow.StateFlow

@Composable
fun StratosLoginScreen(
    uiState: StateFlow<StratosLoginUiState>,
    onAction: (StratosLoginAction) -> Unit,
    onScanQr: () -> Unit,
) {
    val state by uiState.collectAsStateWithLifecycle()

    StratosBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(58.dp))

            StratosLoginMark()

            Spacer(Modifier.height(22.dp))

            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.2.sp,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.stratos_tagline).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                letterSpacing = 3.2.sp,
                fontWeight = FontWeight.SemiBold,
                color = StratosColors.Cyan,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.stratos_login_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(28.dp))

            StratosGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp),
                shape = RoundedCornerShape(26.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            ) {
                Column {
                    OutlinedTextField(
                        value = state.username,
                        onValueChange = { onAction(StratosLoginAction.UsernameChanged(it)) },
                        label = { Text(stringResource(R.string.stratos_username)) },
                        leadingIcon = {
                            Icon(
                                painterResource(R.drawable.ic_stratos_user_24),
                                contentDescription = null,
                                tint = StratosColors.Cyan,
                            )
                        },
                        singleLine = true,
                        enabled = !state.isLoading,
                        isError = state.usernameErrorRes != null,
                        supportingText = state.usernameErrorRes?.let {
                            { Text(stringResource(it)) }
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Ascii,
                            imeAction = ImeAction.Next,
                        ),
                        shape = RoundedCornerShape(16.dp),
                        colors = stratosFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = state.password,
                        onValueChange = { onAction(StratosLoginAction.PasswordChanged(it)) },
                        label = { Text(stringResource(R.string.stratos_password)) },
                        leadingIcon = {
                            Icon(
                                painterResource(R.drawable.ic_stratos_lock_24),
                                contentDescription = null,
                                tint = StratosColors.SoftIndigo,
                            )
                        },
                        trailingIcon = {
                            IconButton(onClick = { onAction(StratosLoginAction.TogglePasswordVisibility) }) {
                                Icon(
                                    painterResource(
                                        if (state.passwordVisible) R.drawable.ic_stratos_eye_off_24
                                        else R.drawable.ic_stratos_eye_24,
                                    ),
                                    contentDescription = stringResource(
                                        if (state.passwordVisible) R.string.stratos_hide_password
                                        else R.string.stratos_show_password,
                                    ),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        singleLine = true,
                        enabled = !state.isLoading,
                        isError = state.passwordErrorRes != null,
                        supportingText = state.passwordErrorRes?.let {
                            { Text(stringResource(it)) }
                        },
                        visualTransformation = if (state.passwordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { onAction(StratosLoginAction.Submit) },
                        ),
                        shape = RoundedCornerShape(16.dp),
                        colors = stratosFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    state.generalErrorRes?.let {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = stringResource(it),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    Spacer(Modifier.height(20.dp))

                    StratosPrimaryButton(
                        onClick = { onAction(StratosLoginAction.Submit) },
                        enabled = !state.isLoading,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                stringResource(R.string.stratos_logging_in),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                            )
                        } else {
                            Icon(
                                painterResource(R.drawable.ic_stratos_power_24),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(19.dp),
                            )
                            Spacer(Modifier.width(9.dp))
                            Text(
                                stringResource(R.string.stratos_login),
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.6.sp,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            // QR login
            val qrDescription = stringResource(R.string.stratos_login_with_qr)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f),
                        RoundedCornerShape(18.dp),
                    )
                    .border(
                        1.dp,
                        StratosColors.SoftIndigo.copy(alpha = 0.35f),
                        RoundedCornerShape(18.dp),
                    )
                    .clickable(enabled = !state.isLoading, role = Role.Button, onClick = onScanQr)
                    .semantics(mergeDescendants = true) {
                        contentDescription = qrDescription
                        role = Role.Button
                    }
                    .padding(horizontal = 18.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_stratos_qr_24),
                    contentDescription = null,
                    tint = StratosColors.SoftIndigo,
                    modifier = Modifier.size(21.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        stringResource(R.string.stratos_login_with_qr),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        stringResource(R.string.stratos_qr_login_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(36.dp))
        }
    }
}

@Composable
private fun StratosLoginMark() {
    Box(
        modifier = Modifier.size(148.dp),
        contentAlignment = Alignment.Center,
    ) {
        // soft halo rings behind the logo
        Box(
            Modifier
                .size(148.dp)
                .background(
                    Brush.radialGradient(
                        listOf(
                            StratosColors.Indigo.copy(alpha = 0.32f),
                            StratosColors.Cyan.copy(alpha = 0.10f),
                            Color.Transparent,
                        ),
                    ),
                    CircleShape,
                ),
        )
        Box(
            Modifier
                .size(126.dp)
                .border(
                    1.2.dp,
                    Brush.sweepGradient(
                        listOf(
                            StratosColors.Cyan.copy(alpha = 0.55f),
                            StratosColors.SoftIndigo.copy(alpha = 0.30f),
                            Color.Transparent,
                            StratosColors.Cyan.copy(alpha = 0.55f),
                        ),
                    ),
                    CircleShape,
                ),
        )
        StratosLogo(96.dp, contentDescription = stringResource(R.string.app_name))
    }
}

private fun stratosFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = StratosColors.Cyan,
    focusedLabelColor = StratosColors.Cyan,
    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f),
    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.35f),
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.25f),
    errorBorderColor = MaterialTheme.colorScheme.error,
)
