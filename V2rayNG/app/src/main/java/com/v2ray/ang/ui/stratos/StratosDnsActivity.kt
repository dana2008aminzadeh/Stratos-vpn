package com.v2ray.ang.ui.stratos

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.viewModels
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.AngApplication
import com.v2ray.ang.R
import com.v2ray.ang.core.LauncherManager
import com.v2ray.ang.extension.toast
import com.v2ray.ang.stratos.StratosSettingsController
import com.v2ray.ang.ui.base.HelperBaseComponentActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

// --------------------------------------------------------------------------------------
// ViewModel
// --------------------------------------------------------------------------------------

class StratosDnsViewModel(private val app: AngApplication) : ViewModel() {

    private val _uiState = MutableStateFlow(StratosSettingsController.currentDnsPresetId())
    val uiState: StateFlow<String> = _uiState.asStateFlow()

    fun select(presetId: String): Boolean {
        if (!StratosSettingsController.applyDnsPreset(presetId)) return false
        _uiState.update { presetId }
        // DNS changes need a tunnel restart to fully apply.
        LauncherManager.restartService(app)
        return true
    }

    companion object {
        fun factory(app: AngApplication) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                StratosDnsViewModel(app) as T
        }
    }
}

// --------------------------------------------------------------------------------------
// Activity
// --------------------------------------------------------------------------------------

class StratosDnsActivity : HelperBaseComponentActivity() {

    private val viewModel: StratosDnsViewModel by viewModels {
        StratosDnsViewModel.factory(application as AngApplication)
    }

    @Composable
    override fun ScreenContent() {
        BackHandler { finish() }
        StratosDnsScreen(
            uiState = viewModel.uiState,
            onBack = { finish() },
            onSelect = { preset ->
                if (viewModel.select(preset)) {
                    toast(R.string.stratos_dns_applied)
                }
            },
        )
    }
}

// --------------------------------------------------------------------------------------
// Screen
// --------------------------------------------------------------------------------------

@Composable
private fun StratosDnsOverview(label: String, description: String) {
    StratosGlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = StratosColors.Cyan.copy(alpha = 0.14f),
                shape = RoundedCornerShape(15.dp),
                modifier = Modifier.size(48.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painterResource(R.drawable.ic_stratos_shield_24),
                        contentDescription = null,
                        tint = StratosColors.Cyan,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.stratos_dns_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StratosDnsScreen(
    uiState: StateFlow<String>,
    onBack: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val selectedId by uiState.collectAsStateWithLifecycle()
    val selectedPreset = StratosSettingsController.dnsPresets.firstOrNull { it.id == selectedId }
        ?: StratosSettingsController.dnsPresets.first()

    StratosBackdrop {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        StratosScreenTitle(
                            title = stringResource(R.string.stratos_dns_title),
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
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                )
            },
        ) { innerPadding ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .widthIn(max = 680.dp)
                        .align(Alignment.TopCenter)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(11.dp),
                ) {
                    Spacer(Modifier.height(4.dp))
                    StratosDnsOverview(
                        label = stringResource(selectedPreset.labelRes),
                        description = stringResource(selectedPreset.descRes),
                    )
                    Spacer(Modifier.height(4.dp))
                    StratosSettingsController.dnsPresets.forEach { preset ->
                    val selected = preset.id == selectedId
                    val label = stringResource(preset.labelRes)
                    val accent = when (preset.id) {
                        "ads" -> StratosColors.Aurora
                        "family" -> StratosColors.Success
                        "gaming" -> StratosColors.Gold
                        else -> StratosColors.Cyan
                    }
                    StratosGlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (selected) Modifier.border(
                                    1.dp,
                                    accent.copy(alpha = 0.48f),
                                    RoundedCornerShape(22.dp),
                                ) else Modifier,
                            )
                            .clip(RoundedCornerShape(22.dp))
                            .clickable(role = Role.RadioButton) { onSelect(preset.id) }
                            .semantics(mergeDescendants = true) {
                                contentDescription = label
                                role = Role.RadioButton
                                this.selected = selected
                            },
                        shape = RoundedCornerShape(22.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 15.dp, vertical = 15.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(
                                color = if (selected) accent.copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.58f),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.size(46.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        painterResource(R.drawable.ic_stratos_shield_24),
                                        contentDescription = null,
                                        tint = if (selected) accent
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(23.dp),
                                    )
                                }
                            }
                            Spacer(Modifier.width(13.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selected) accent
                                    else MaterialTheme.colorScheme.onSurface,
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    stringResource(preset.descRes),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            RadioButton(
                                selected = selected,
                                onClick = null,
                                modifier = Modifier.clearAndSetSemantics { },
                            )
                        }
                    }
                    }
                    Spacer(Modifier.height(18.dp))
                }
            }
        }
    }
}
