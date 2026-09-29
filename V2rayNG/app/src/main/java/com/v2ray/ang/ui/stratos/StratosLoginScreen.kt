package com.v2ray.ang.ui.stratos

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.BuildConfig
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
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 480.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(8.dp))
                StratosLoginMark()
                Spacer(Modifier.height(18.dp))
                Text(
                    text = stringResource(R.string.stratos_login_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    text = stringResource(R.string.stratos_login_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(28.dp))

                StratosGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(30.dp),
                ) {
                    Column(
                        Modifier.padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        OutlinedTextField(
                            value = state.username,
                            onValueChange = { onAction(StratosLoginAction.UsernameChanged(it)) },
                            label = { Text(stringResource(R.string.stratos_username)) },
                            singleLine = true,
                            isError = state.usernameErrorRes != null,
                            supportingText = state.usernameErrorRes?.let { res ->
                                { Text(stringResource(res), color = MaterialTheme.colorScheme.error) }
                            },
                            leadingIcon = {
                                Icon(
                                    painterResource(R.drawable.ic_stratos_user_24),
                                    contentDescription = null,
                                )
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Ascii,
                                imeAction = ImeAction.Next,
                            ),
                            shape = RoundedCornerShape(17.dp),
                            colors = stratosFieldColors(),
                            modifier = Modifier.fillMaxWidth(),
                        )

                        OutlinedTextField(
                            value = state.password,
                            onValueChange = { onAction(StratosLoginAction.PasswordChanged(it)) },
                            label = { Text(stringResource(R.string.stratos_password)) },
                            singleLine = true,
                            isError = state.passwordErrorRes != null,
                            supportingText = state.passwordErrorRes?.let { res ->
                                { Text(stringResource(res), color = MaterialTheme.colorScheme.error) }
                            },
                            leadingIcon = {
                                Icon(
                                    painterResource(R.drawable.ic_stratos_lock_24),
                                    contentDescription = null,
                                )
                            },
                            trailingIcon = {
                                val description = stringResource(
                                    if (state.passwordVisible) R.string.stratos_hide_password
                                    else R.string.stratos_show_password,
                                )
                                IconButton(onClick = { onAction(StratosLoginAction.TogglePasswordVisibility) }) {
                                    Icon(
                                        painterResource(
                                            if (state.passwordVisible) R.drawable.ic_stratos_eye_off_24
                                            else R.drawable.ic_stratos_eye_24,
                                        ),
                                        contentDescription = description,
                                    )
                                }
                            },
                            visualTransformation = if (state.passwordVisible) VisualTransformation.None
                            else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done,
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = { onAction(StratosLoginAction.Submit) },
                            ),
                            shape = RoundedCornerShape(17.dp),
                            colors = stratosFieldColors(),
                            modifier = Modifier.fillMaxWidth(),
                        )

                        state.generalErrorRes?.let { res ->
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.72f),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = stringResource(res),
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                                )
                            }
                        }

                        StratosPrimaryButton(
                            onClick = { onAction(StratosLoginAction.Submit) },
                            enabled = !state.isLoading,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (state.isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(21.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.size(10.dp))
                                Text(
                                    stringResource(R.string.stratos_logging_in),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                )
                            } else {
                                Text(
                                    stringResource(R.string.stratos_login),
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.2.sp,
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = onScanQr,
                            enabled = !state.isLoading,
                            shape = RoundedCornerShape(17.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_stratos_qr_24),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.size(9.dp))
                            Text(
                                stringResource(R.string.stratos_login_with_qr),
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(22.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = stringResource(R.string.stratos_tagline),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.8.sp,
                    )
                    Text(
                        text = "  •  " + stringResource(R.string.stratos_version, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.68f),
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun StratosLoginMark() {
    Box(
        modifier = Modifier.size(140.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(140.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(
                        StratosColors.Cyan.copy(alpha = 0.20f),
                        StratosColors.Indigo.copy(alpha = 0.10f),
                        Color.Transparent,
                    ),
                    center = center,
                    radius = size.minDimension * 0.50f,
                ),
                radius = size.minDimension * 0.50f,
                center = center,
            )
            val inset = 7.dp.toPx()
            drawArc(
                color = StratosColors.Cyan.copy(alpha = 0.38f),
                startAngle = 202f,
                sweepAngle = 202f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2),
                style = Stroke(width = 1.dp.toPx(), cap = StrokeCap.Round),
            )
            drawArc(
                color = StratosColors.Gold.copy(alpha = 0.28f),
                startAngle = 34f,
                sweepAngle = 72f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2),
                style = Stroke(width = 1.dp.toPx(), cap = StrokeCap.Round),
            )
            drawCircle(
                color = StratosColors.Cyan.copy(alpha = 0.90f),
                radius = 3.dp.toPx(),
                center = Offset(size.width * 0.85f, size.height * 0.25f),
            )
        }
        Box(
            Modifier
                .size(108.dp)
                .background(
                    Brush.radialGradient(
                        listOf(
                            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.88f),
                            MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.56f),
                        ),
                    ),
                    CircleShape,
                )
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.18f),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            StratosLogo(96.dp, contentDescription = null)
        }
    }
}

@Composable
private fun stratosFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = StratosColors.Cyan,
    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.78f),
    focusedLabelColor = StratosColors.Cyan,
    focusedLeadingIconColor = StratosColors.Cyan,
    cursorColor = StratosColors.Cyan,
    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.48f),
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.34f),
)
