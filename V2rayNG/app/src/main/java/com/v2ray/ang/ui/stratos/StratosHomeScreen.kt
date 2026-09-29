package com.v2ray.ang.ui.stratos

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.R
import com.v2ray.ang.extension.toSpeedString
import com.v2ray.ang.extension.toTrafficString
import com.v2ray.ang.stratos.StratosSession
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// ======================================================================================
// Home screen
// ======================================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StratosHomeScreen(
    uiState: StateFlow<StratosHomeUiState>,
    onAction: (StratosHomeAction) -> Unit,
    onOpenServers: () -> Unit,
    onOpenDns: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenRenew: () -> Unit,
    onOpenWebsite: () -> Unit,
    onOpenTelegram: () -> Unit,
) {
    val state by uiState.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            StratosDrawer(
                state = state,
                onRenew = { scope.launch { drawerState.close() }; onOpenRenew() },
                onChangePassword = {
                    scope.launch { drawerState.close() }
                    onAction(StratosHomeAction.ShowChangePassword)
                },
                onSettings = { scope.launch { drawerState.close() }; onOpenSettings() },
                onWebsite = { scope.launch { drawerState.close() }; onOpenWebsite() },
                onTelegram = { scope.launch { drawerState.close() }; onOpenTelegram() },
                onAbout = { scope.launch { drawerState.close() }; onOpenAbout() },
                onLogout = {
                    scope.launch { drawerState.close() }
                    onAction(StratosHomeAction.ShowLogoutConfirm)
                },
            )
        },
    ) {
        StratosBackdrop {
            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    TopAppBar(
                        title = {
                            StratosBrandLockup(
                                title = stringResource(R.string.app_name),
                                subtitle = stringResource(R.string.stratos_tagline),
                                compact = true,
                            )
                        },
                        navigationIcon = {
                            StratosIconAction(
                                icon = R.drawable.ic_stratos_menu_24,
                                contentDescription = stringResource(R.string.acc_open_menu),
                                onClick = { scope.launch { drawerState.open() } },
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        },
                        actions = {
                            StratosIconAction(
                                icon = R.drawable.ic_stratos_refresh_24,
                                contentDescription = stringResource(R.string.stratos_refresh),
                                onClick = { onAction(StratosHomeAction.Refresh) },
                                enabled = !state.isSyncing,
                                modifier = Modifier.padding(end = 12.dp),
                            ) {
                                if (state.isSyncing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        color = StratosColors.Cyan,
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    Icon(
                                        painterResource(R.drawable.ic_stratos_refresh_24),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    )
                },
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 680.dp)
                            .align(Alignment.TopCenter)
                            .consumeWindowInsets(innerPadding)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Spacer(Modifier.height(4.dp))
                        StratosConnectionStatusPill(state)
                        Spacer(Modifier.height(5.dp))

                        StratosConnectRing(
                            state = state,
                            onToggle = { onAction(StratosHomeAction.ToggleConnect) },
                        )

                        Spacer(Modifier.height(18.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            StratosSelectorChip(
                                icon = R.drawable.ic_stratos_globe_24,
                                label = state.selectedServerRemark.ifBlank {
                                    stringResource(R.string.stratos_servers_title)
                                },
                                subtitle = state.selectedServerCountry.uppercase()
                                    .ifBlank { stringResource(R.string.stratos_ip_unavailable) },
                                onClick = onOpenServers,
                                modifier = Modifier.weight(1.35f),
                                contentDescription = stringResource(R.string.stratos_servers_title),
                            )
                            StratosSelectorChip(
                                icon = R.drawable.ic_stratos_shield_24,
                                label = dnsLabel(state.dnsPresetId),
                                subtitle = stringResource(R.string.stratos_dns_title),
                                onClick = onOpenDns,
                                modifier = Modifier.weight(1f),
                                contentDescription = stringResource(R.string.stratos_dns_title),
                            )
                        }

                        Spacer(Modifier.height(14.dp))

                        StratosGlassCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                StratosInfoCard(
                                    title = stringResource(R.string.stratos_ip_current),
                                    value = when {
                                        state.ipLocalLoading -> stringResource(R.string.stratos_ip_loading)
                                        state.ipLocal.isBlank() -> stringResource(R.string.stratos_ip_unavailable)
                                        else -> state.ipLocal
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                                Box(
                                    modifier = Modifier
                                        .padding(top = 7.dp)
                                        .width(1.dp)
                                        .height(36.dp)
                                        .background(
                                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                                            RoundedCornerShape(50),
                                        ),
                                )
                                StratosInfoCard(
                                    title = stringResource(R.string.stratos_ip_after),
                                    value = when {
                                        state.connectState == StratosConnectState.Connected ->
                                            state.ipVpn.ifBlank { stringResource(R.string.stratos_ip_loading) }

                                        else -> stringResource(R.string.stratos_ip_unavailable)
                                    },
                                    modifier = Modifier.weight(1f),
                                    accent = state.connectState == StratosConnectState.Connected,
                                )
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        when {
                            state.isExpired -> StratosExpiredPanel(
                                reason = state.blockedReason,
                                isSyncing = state.isSyncing,
                                onRefresh = { onAction(StratosHomeAction.Refresh) },
                                onRenew = onOpenRenew,
                            )

                            state.connectState == StratosConnectState.Connected -> StratosLivePanel(state = state)

                            else -> StratosPlanPanel(state = state)
                        }

                        Spacer(Modifier.height(34.dp))
                    }
                }
            }
        }
    }

    if (state.showChangePassword) {
        StratosChangePasswordDialog(
            busy = state.passwordChangeBusy,
            onDismiss = { onAction(StratosHomeAction.HideChangePassword) },
            onSubmit = { current, new ->
                onAction(StratosHomeAction.SubmitChangePassword(current, new))
            },
        )
    }

    if (state.showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { onAction(StratosHomeAction.HideLogoutConfirm) },
            title = { Text(stringResource(R.string.stratos_logout_confirm_title)) },
            text = { Text(stringResource(R.string.stratos_logout_confirm_message)) },
            confirmButton = {
                TextButton(onClick = { onAction(StratosHomeAction.ConfirmLogout) }) {
                    Text(
                        stringResource(R.string.stratos_menu_logout),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { onAction(StratosHomeAction.HideLogoutConfirm) }) {
                    Text(stringResource(R.string.stratos_cancel))
                }
            },
        )
    }

    if (state.showKickedDialog) {
        AlertDialog(
            onDismissRequest = { onAction(StratosHomeAction.DismissKick) },
            title = { Text(stringResource(R.string.stratos_kicked_title)) },
            text = { Text(stringResource(R.string.stratos_kicked_message)) },
            confirmButton = {
                TextButton(onClick = { onAction(StratosHomeAction.DismissKick) }) {
                    Text(stringResource(R.string.stratos_confirm))
                }
            },
        )
    }
}

@Composable
private fun dnsLabel(presetId: String): String = when (presetId) {
    "ads" -> stringResource(R.string.stratos_dns_ads)
    "family" -> stringResource(R.string.stratos_dns_family)
    "gaming" -> stringResource(R.string.stratos_dns_gaming)
    else -> stringResource(R.string.stratos_dns_default)
}

// ======================================================================================
// Connect ring
// ======================================================================================

@Composable
private fun StratosConnectionStatusPill(state: StratosHomeUiState) {
    val (label, color) = when {
        state.isExpired -> stringResource(R.string.stratos_expired_title) to StratosColors.Danger
        state.connectState == StratosConnectState.Connected ->
            stringResource(R.string.stratos_connected) to StratosColors.Success

        state.connectState == StratosConnectState.Connecting ->
            stringResource(R.string.stratos_connecting) to StratosColors.Cyan

        state.connectState == StratosConnectState.Stopping ->
            stringResource(R.string.stratos_disconnecting) to StratosColors.Warning

        else -> stringResource(R.string.stratos_disconnected) to MaterialTheme.colorScheme.onSurfaceVariant
    }
    StratosStatusBadge(label = label, color = color)
}

@Composable
private fun StratosConnectRing(state: StratosHomeUiState, onToggle: () -> Unit) {
    val connected = state.connectState == StratosConnectState.Connected
    val transitioning = state.connectState == StratosConnectState.Connecting ||
            state.connectState == StratosConnectState.Stopping

    val animatedFraction by animateFloatAsState(
        targetValue = state.remainingFraction.coerceIn(0f, 1f),
        animationSpec = tween(900),
        label = "ring",
    )
    val transition = rememberInfiniteTransition(label = "connection-orbit")
    val glowPulse by transition.animateFloat(
        initialValue = 0.72f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Reverse),
        label = "glow",
    )
    val orbitTurn by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(7600, easing = LinearEasing)),
        label = "orbit-turn",
    )
    val connectDescription = stringResource(
        if (connected) R.string.stratos_disconnect else R.string.stratos_connect,
    )
    val ringTrackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier.size(282.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val aura = when {
                    connected -> StratosColors.Cyan
                    transitioning -> StratosColors.Aurora
                    else -> StratosColors.Indigo
                }
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            aura.copy(alpha = (if (connected) 0.24f else 0.13f) * glowPulse),
                            Color.Transparent,
                        ),
                        center = center,
                        radius = size.minDimension * 0.52f,
                    ),
                    center = center,
                    radius = size.minDimension * 0.52f,
                )

                val outerInset = 5.dp.toPx()
                drawArc(
                    color = StratosColors.SoftIndigo.copy(alpha = 0.20f),
                    startAngle = 202f,
                    sweepAngle = 124f,
                    useCenter = false,
                    topLeft = Offset(outerInset, outerInset),
                    size = Size(size.width - outerInset * 2, size.height - outerInset * 2),
                    style = Stroke(width = 1.dp.toPx()),
                )
                drawArc(
                    color = StratosColors.Gold.copy(alpha = 0.16f),
                    startAngle = 25f,
                    sweepAngle = 58f,
                    useCenter = false,
                    topLeft = Offset(outerInset, outerInset),
                    size = Size(size.width - outerInset * 2, size.height - outerInset * 2),
                    style = Stroke(width = 1.dp.toPx()),
                )

                val stroke = 10.dp.toPx()
                val inset = 22.dp.toPx()
                val diameter = size.minDimension - inset * 2
                val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
                val arcSize = Size(diameter, diameter)

                drawArc(
                    color = ringTrackColor,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                drawArc(
                    brush = Brush.sweepGradient(
                        listOf(
                            StratosColors.Cyan,
                            StratosColors.Sky,
                            StratosColors.Indigo,
                            StratosColors.Cyan,
                        ),
                        center = center,
                    ),
                    startAngle = -90f,
                    sweepAngle = 360f * animatedFraction,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                    alpha = if (connected) glowPulse else 0.92f,
                )

                val satelliteAngle = Math.toRadians((if (connected || transitioning) orbitTurn else 324f).toDouble())
                val orbitRadius = size.minDimension / 2f - outerInset
                val satelliteCenter = Offset(
                    center.x + kotlin.math.cos(satelliteAngle).toFloat() * orbitRadius,
                    center.y + kotlin.math.sin(satelliteAngle).toFloat() * orbitRadius,
                )
                drawCircle(
                    color = aura.copy(alpha = 0.22f),
                    radius = 7.dp.toPx(),
                    center = satelliteCenter,
                )
                drawCircle(
                    color = aura.copy(alpha = 0.94f),
                    radius = 2.8.dp.toPx(),
                    center = satelliteCenter,
                )
            }

            val buttonBrush = when {
                connected -> Brush.linearGradient(
                    listOf(StratosColors.Cyan, StratosColors.Sky, StratosColors.Indigo),
                )

                transitioning -> Brush.linearGradient(
                    listOf(StratosColors.Indigo, StratosColors.Aurora),
                )

                else -> Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.97f),
                        MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.94f),
                    ),
                )
            }
            Box(
                modifier = Modifier
                    .size(198.dp)
                    .background(buttonBrush, CircleShape)
                    .border(
                        1.dp,
                        Color.White.copy(alpha = if (connected || transitioning) 0.36f else 0.13f),
                        CircleShape,
                    )
                    .clickable(
                        enabled = !transitioning,
                        role = Role.Button,
                        onClick = onToggle,
                    )
                    .semantics(mergeDescendants = true) {
                        contentDescription = connectDescription
                        role = Role.Button
                    },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(Color.White.copy(alpha = if (connected) 0.23f else 0.08f), Color.Transparent),
                            center = Offset(size.width * 0.30f, size.height * 0.18f),
                            radius = size.width * 0.78f,
                        ),
                        radius = size.width * 0.78f,
                        center = Offset(size.width * 0.30f, size.height * 0.18f),
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (transitioning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(37.dp),
                            color = Color.White,
                            strokeWidth = 2.5.dp,
                        )
                    } else {
                        Surface(
                            color = if (connected) Color.White.copy(alpha = 0.16f)
                            else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.78f),
                            shape = CircleShape,
                            modifier = Modifier.size(63.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painterResource(R.drawable.ic_stratos_power_24),
                                    contentDescription = null,
                                    tint = if (connected) Color.White else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(31.dp),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = stringResource(statusLabel(state.connectState)),
                        color = if (connected || transitioning) Color.White
                        else MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
        }

        Text(
            text = when {
                state.isExpired -> stringResource(R.string.stratos_expired_title)
                connected -> state.selectedServerRemark.ifBlank { stringResource(R.string.stratos_connected) }
                transitioning -> stringResource(statusLabel(state.connectState))
                else -> stringResource(R.string.stratos_tap_to_connect)
            },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (connected) StratosColors.Cyan else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun statusLabel(state: StratosConnectState): Int = when (state) {
    StratosConnectState.Connected -> R.string.stratos_connected
    StratosConnectState.Connecting -> R.string.stratos_connecting
    StratosConnectState.Stopping -> R.string.stratos_disconnecting
    StratosConnectState.Disconnected -> R.string.stratos_disconnected
}

// ======================================================================================
// Selector chip + info card
// ======================================================================================

@Composable
private fun StratosSelectorChip(
    icon: Int,
    label: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String,
) {
    StratosGlassCard(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {
                this.contentDescription = contentDescription
                role = Role.Button
            },
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = StratosColors.Cyan.copy(alpha = 0.11f),
                modifier = Modifier.size(38.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painterResource(icon),
                        contentDescription = null,
                        tint = StratosColors.Cyan,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(1.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun StratosInfoCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
) {
    Column(
        modifier = modifier.padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = if (accent) StratosColors.Cyan else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ======================================================================================
// Bottom panels
// ======================================================================================

@Composable
private fun StratosPlanPanel(state: StratosHomeUiState) {
    StratosGlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(Modifier.padding(17.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = StratosColors.Cyan.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(11.dp),
                    modifier = Modifier.size(36.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painterResource(R.drawable.ic_stratos_data_24),
                            contentDescription = null,
                            tint = StratosColors.Cyan,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.stratos_data_remaining),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (state.isUnlimitedData) stringResource(R.string.stratos_unlimited)
                    else state.remainingBytes.toTrafficString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = StratosColors.Cyan,
                )
            }
            Spacer(Modifier.height(13.dp))
            StratosGradientProgress(state.remainingFraction)
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f))
            Spacer(Modifier.height(13.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(R.drawable.ic_stratos_clock_24),
                    contentDescription = null,
                    tint = StratosColors.SoftIndigo,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.stratos_time_remaining),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (state.remainingDays == Long.MAX_VALUE) stringResource(R.string.stratos_unlimited)
                    else "${state.remainingDays} " + stringResource(R.string.stratos_days),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = StratosColors.SoftIndigo,
                )
            }
        }
    }
}

@Composable
private fun StratosGradientProgress(progress: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(7.dp)
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                RoundedCornerShape(50),
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(7.dp)
                .background(StratosColors.PremiumGradient, RoundedCornerShape(50)),
        )
    }
}

@Composable
private fun StratosExpiredPanel(
    reason: String,
    isSyncing: Boolean,
    onRefresh: () -> Unit,
    onRenew: () -> Unit,
) {
    StratosGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                MaterialTheme.colorScheme.error.copy(alpha = 0.24f),
                RoundedCornerShape(24.dp),
            ),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(Modifier.padding(17.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                    shape = CircleShape,
                    modifier = Modifier.size(36.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painterResource(R.drawable.ic_stratos_info_24),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(
                        if (reason == StratosSession.BLOCKED_NO_DATA) R.string.stratos_expired_data
                        else R.string.stratos_expired_title,
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.stratos_expired_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(15.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onRefresh,
                    enabled = !isSyncing,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(
                        painterResource(R.drawable.ic_stratos_refresh_24),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.stratos_refresh))
                }
                FilledTonalButton(
                    onClick = onRenew,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(
                        painterResource(R.drawable.ic_stratos_renew_24),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.stratos_renew))
                }
            }
        }
    }
}

@Composable
private fun StratosLivePanel(state: StratosHomeUiState) {
    StratosGlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(Modifier.padding(17.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StratosSpeedItem(
                    icon = R.drawable.ic_stratos_download_24,
                    label = stringResource(R.string.stratos_download),
                    value = speedText(state.traffic?.downSpeedBytesPerSec),
                    tint = StratosColors.Cyan,
                    modifier = Modifier.weight(1f),
                )
                StratosSpeedItem(
                    icon = R.drawable.ic_stratos_upload_24,
                    label = stringResource(R.string.stratos_upload),
                    value = speedText(state.traffic?.upSpeedBytesPerSec),
                    tint = StratosColors.SoftIndigo,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(12.dp))

            // live sparkline chart of recent download/upload speed
            StratosSparkline(
                down = state.downSpeedHistory,
                up = state.upSpeedHistory,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
            )

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(12.dp))

            Row {
                StratosMiniStat(
                    label = stringResource(R.string.stratos_total_used),
                    value = state.usedBytes.toTrafficString(),
                    modifier = Modifier.weight(1f),
                )
                StratosMiniStat(
                    label = stringResource(R.string.stratos_session_traffic),
                    value = (state.traffic?.sessionTotalBytes ?: 0L).toTrafficString(),
                    modifier = Modifier.weight(1f),
                )
                StratosMiniStat(
                    label = stringResource(R.string.stratos_session_time),
                    value = sessionClock(state.connectedSinceMillis),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun StratosSpeedItem(
    icon: Int,
    label: String,
    value: String,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = CircleShape,
            color = tint.copy(alpha = 0.15f),
            modifier = Modifier.size(34.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painterResource(icon),
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun StratosMiniStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

// ======================================================================================
// Sparkline
// ======================================================================================

@Composable
private fun StratosSparkline(down: List<Float>, up: List<Float>, modifier: Modifier = Modifier) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    Canvas(modifier) {
        val w = size.width
        val h = size.height

        // grid
        val gridLines = 3
        for (i in 1..gridLines) {
            val y = h * i / (gridLines + 1)
            drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1f)
        }

        fun drawSeries(values: List<Float>, color: Color) {
            if (values.size < 2) return
            val maxV = (values.maxOrNull() ?: 1f).coerceAtLeast(1f)
            val stepX = w / (values.size - 1)
            // area fill
            val path = androidx.compose.ui.graphics.Path()
            values.forEachIndexed { index, v ->
                val x = index * stepX
                val y = h - (v / maxV) * (h * 0.9f) - h * 0.05f
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            val fillPath = androidx.compose.ui.graphics.Path().apply {
                addPath(path)
                lineTo((values.size - 1) * stepX, h)
                lineTo(0f, h)
                close()
            }
            drawPath(fillPath, brush = Brush.verticalGradient(listOf(color.copy(alpha = 0.25f), Color.Transparent)))
            drawPath(path, color = color, style = Stroke(width = 2.5f, cap = StrokeCap.Round))
        }

        drawSeries(up, StratosColors.SoftIndigo)
        drawSeries(down, StratosColors.Cyan)
    }
}

// ======================================================================================
// Drawer
// ======================================================================================

@Composable
private fun StratosDrawer(
    state: StratosHomeUiState,
    onRenew: () -> Unit,
    onChangePassword: () -> Unit,
    onSettings: () -> Unit,
    onWebsite: () -> Unit,
    onTelegram: () -> Unit,
    onAbout: () -> Unit,
    onLogout: () -> Unit,
) {
    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            Modifier
                .width(310.dp)
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            StratosColors.Indigo.copy(alpha = 0.10f),
                            Color.Transparent,
                            StratosColors.Cyan.copy(alpha = 0.05f),
                        ),
                    ),
                )
                .padding(horizontal = 14.dp, vertical = 20.dp),
        ) {
            StratosGlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StratosLogo(46.dp, contentDescription = null)
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        Text(
                            stringResource(R.string.stratos_signed_in_as, state.username),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            StratosDrawerItem(R.drawable.ic_stratos_renew_24, R.string.stratos_menu_renew, onRenew)
            StratosDrawerItem(R.drawable.ic_stratos_key_24, R.string.stratos_menu_change_password, onChangePassword)
            StratosDrawerItem(R.drawable.ic_stratos_settings_24, R.string.stratos_menu_settings, onSettings)
            StratosDrawerItem(R.drawable.ic_stratos_web_24, R.string.stratos_menu_website, onWebsite)
            StratosDrawerItem(R.drawable.ic_stratos_telegram_24, R.string.stratos_menu_telegram, onTelegram)
            StratosDrawerItem(R.drawable.ic_stratos_info_24, R.string.stratos_menu_about, onAbout)

            Spacer(Modifier.weight(1f))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
            Spacer(Modifier.height(8.dp))
            StratosDrawerItem(
                R.drawable.ic_stratos_logout_24,
                R.string.stratos_menu_logout,
                onLogout,
                tint = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.stratos_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.68f),
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
    }
}

@Composable
private fun StratosDrawerItem(
    icon: Int,
    labelRes: Int,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    NavigationDrawerItem(
        icon = {
            Icon(
                painterResource(icon),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp),
            )
        },
        label = { Text(stringResource(labelRes), color = tint) },
        selected = false,
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
    )
}

// ======================================================================================
// Change password dialog
// ======================================================================================

@Composable
private fun StratosChangePasswordDialog(
    busy: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String, String) -> Unit,
) {
    var current by rememberSaveable { mutableStateOf("") }
    var next by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.stratos_change_password_title)) },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = current,
                    onValueChange = { current = it },
                    label = { Text(stringResource(R.string.stratos_current_password)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = next,
                    onValueChange = { next = it },
                    label = { Text(stringResource(R.string.stratos_new_password)) },
                    singleLine = true,
                    supportingText = {
                        Text(
                            stringResource(R.string.stratos_error_password_format),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(current, next) },
                enabled = !busy && current.length in 5..10 && next.length in 5..10,
            ) {
                Text(stringResource(R.string.stratos_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text(stringResource(R.string.stratos_cancel))
            }
        },
    )
}

// ======================================================================================
// helpers
// ======================================================================================

private fun speedText(bytesPerSec: Long?): String =
    if (bytesPerSec == null) "0 B/s" else bytesPerSec.toSpeedString()

@Composable
private fun sessionClock(sinceMillis: Long): String {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(sinceMillis) {
        while (sinceMillis > 0L) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }
    if (sinceMillis <= 0L) return "00:00"
    val totalSec = ((now - sinceMillis) / 1000).coerceAtLeast(0)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
