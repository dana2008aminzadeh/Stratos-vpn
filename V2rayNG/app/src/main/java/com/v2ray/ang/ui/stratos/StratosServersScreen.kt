package com.v2ray.ang.ui.stratos

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.R
import com.v2ray.ang.stratos.StratosServersStore
import kotlinx.coroutines.flow.StateFlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StratosServersScreen(
    uiState: StateFlow<StratosServersUiState>,
    onAction: (StratosServersAction) -> Unit,
    onBack: () -> Unit,
) {
    val state by uiState.collectAsStateWithLifecycle()

    StratosBackdrop {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        StratosScreenTitle(
                            title = stringResource(R.string.stratos_servers_title),
                            subtitle = stringResource(R.string.stratos_tagline),
                        )
                    },
                    navigationIcon = {
                        StratosIconAction(
                            icon = R.drawable.ic_stratos_back_24,
                            contentDescription = stringResource(R.string.acc_back),
                            onClick = onBack,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    },
                    actions = {
                        StratosIconAction(
                            icon = R.drawable.ic_stratos_ping_24,
                            contentDescription = stringResource(R.string.stratos_test_all_ping),
                            onClick = { onAction(StratosServersAction.TestAll) },
                            enabled = !state.isTesting,
                            modifier = Modifier.padding(end = 12.dp),
                        ) {
                            if (state.isTesting && !state.isAutoConnecting) {
                                CircularProgressIndicator(
                                    Modifier.size(18.dp),
                                    color = StratosColors.Cyan,
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Icon(
                                    painterResource(R.drawable.ic_stratos_ping_24),
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
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding),
            ) {
                StratosPrimaryButton(
                    onClick = { onAction(StratosServersAction.AutoConnect) },
                    enabled = !state.isTesting && !state.isEmpty,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                ) {
                    if (state.isAutoConnecting) {
                        CircularProgressIndicator(
                            Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            stringResource(R.string.stratos_auto_connecting),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                        )
                    } else {
                        Icon(
                            painterResource(R.drawable.ic_stratos_auto_24),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            stringResource(R.string.stratos_auto_connect),
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                StratosFleetOverview(
                    state = state,
                    onRefresh = { onAction(StratosServersAction.Refresh) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                )

                Spacer(Modifier.height(14.dp))

                if (state.isEmpty && !state.isRefreshing) {
                    StratosEmptyServers(
                        onRefresh = { onAction(StratosServersAction.Refresh) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 20.dp),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(
                            items = state.groups,
                            key = { it.countryCode },
                        ) { group ->
                            StratosCountryCard(
                                group = group,
                                expanded = state.expandedCountries.contains(group.countryCode),
                                selectedGuid = state.selectedGuid,
                                isTesting = state.isTesting,
                                onToggle = { onAction(StratosServersAction.ToggleCountry(group.countryCode)) },
                                onSelect = { server -> onAction(StratosServersAction.Select(server)) },
                            )
                        }
                        item { Spacer(Modifier.height(18.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun StratosFleetOverview(
    state: StratosServersUiState,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val serverCount = state.groups.sumOf { it.servers.size }
    StratosGlassCard(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = StratosColors.Indigo.copy(alpha = 0.15f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painterResource(R.drawable.ic_stratos_globe_24),
                        contentDescription = null,
                        tint = StratosColors.Cyan,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.stratos_servers_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(R.string.stratos_server_count, serverCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.isRefreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = StratosColors.Cyan,
                    strokeWidth = 2.dp,
                )
            } else {
                StratosIconAction(
                    icon = R.drawable.ic_stratos_refresh_24,
                    contentDescription = stringResource(R.string.stratos_refresh),
                    onClick = onRefresh,
                )
            }
        }
    }
}

@Composable
private fun StratosEmptyServers(onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        StratosGlassCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 28.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(
                    color = StratosColors.Indigo.copy(alpha = 0.14f),
                    shape = CircleShape,
                    modifier = Modifier.size(58.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painterResource(R.drawable.ic_stratos_globe_24),
                            contentDescription = null,
                            tint = StratosColors.Cyan,
                            modifier = Modifier.size(27.dp),
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    stringResource(R.string.stratos_no_servers),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onRefresh) {
                    Text(stringResource(R.string.stratos_refresh), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun StratosCountryCard(
    group: StratosCountryGroup,
    expanded: Boolean,
    selectedGuid: String?,
    isTesting: Boolean,
    onToggle: () -> Unit,
    onSelect: (com.v2ray.ang.stratos.StratosServer) -> Unit,
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "countryChevron",
    )
    val containsSelection = group.servers.any { it.guid == selectedGuid }
    val expansionState = stringResource(
        if (expanded) R.string.stratos_expanded else R.string.stratos_collapsed,
    )
    val countryDescription = "${group.countryName}, " +
            stringResource(R.string.stratos_server_count, group.servers.size)

    StratosGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (containsSelection) Modifier.border(
                    1.dp,
                    StratosColors.Cyan.copy(alpha = 0.32f),
                    RoundedCornerShape(22.dp),
                ) else Modifier,
            )
            .animateContentSize(),
        shape = RoundedCornerShape(22.dp),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .clickable(role = Role.Button, onClick = onToggle)
                    .semantics(mergeDescendants = true) {
                        contentDescription = countryDescription
                        role = Role.Button
                        stateDescription = expansionState
                    }
                    .padding(horizontal = 15.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.72f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(group.flag(), fontSize = 23.sp)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        group.countryName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        stringResource(R.string.stratos_server_count, group.servers.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                group.bestDelay?.let { delay ->
                    StratosPingBadge(delayMs = delay)
                } ?: if (isTesting) {
                    CircularProgressIndicator(
                        Modifier.size(17.dp),
                        color = StratosColors.Cyan,
                        strokeWidth = 1.7.dp,
                    )
                } else Unit
                Spacer(Modifier.width(8.dp))
                Icon(
                    painterResource(R.drawable.ic_expand_more_24dp),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(20.dp)
                        .graphicsLayer { rotationZ = rotation },
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.48f),
                    )
                    group.servers.forEach { row ->
                        StratosServerRowItem(
                            row = row,
                            selected = row.guid == selectedGuid,
                            onClick = { onSelect(row.server) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StratosServerRowItem(
    row: StratosServerRow,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (selected) StratosColors.Cyan.copy(alpha = 0.08f) else Color.Transparent,
            )
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = row.remark.ifBlank { row.server.name }
                role = Role.RadioButton
                this.selected = selected
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            modifier = Modifier.clearAndSetSemantics { },
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                row.remark.ifBlank { row.server.name },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) StratosColors.Cyan else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (row.server.tags.isNotEmpty()) {
                Spacer(Modifier.height(5.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.server.tags.take(3).forEach { tag ->
                        StratosTagChip(tag)
                    }
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        when {
            row.delayMs == null -> Text(
                stringResource(R.string.stratos_ip_unavailable),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            row.delayMs <= 0 -> Text(
                stringResource(R.string.stratos_ping_timeout),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
            )

            else -> StratosPingBadge(delayMs = row.delayMs)
        }
    }
}

@Composable
private fun StratosTagChip(tag: String) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.62f),
        shape = RoundedCornerShape(7.dp),
    ) {
        Text(
            tag,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            maxLines = 1,
        )
    }
}

@Composable
private fun StratosPingBadge(delayMs: Long) {
    val color = when {
        delayMs < 120 -> StratosColors.Success
        delayMs < 300 -> StratosColors.Warning
        else -> MaterialTheme.colorScheme.error
    }
    Surface(
        color = color.copy(alpha = 0.10f),
        shape = RoundedCornerShape(50),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(color),
            )
            Spacer(Modifier.width(5.dp))
            Text(
                "$delayMs " + stringResource(R.string.stratos_ms_unit),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = color,
            )
        }
    }
}

private fun StratosCountryGroup.flag(): String =
    StratosServersStore.flagEmoji(countryCode)
