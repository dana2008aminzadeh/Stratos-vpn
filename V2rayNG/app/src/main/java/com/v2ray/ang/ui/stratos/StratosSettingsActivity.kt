package com.v2ray.ang.ui.stratos

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.v2ray.ang.AngApplication
import com.v2ray.ang.R
import com.v2ray.ang.enums.Language
import com.v2ray.ang.extension.toast
import com.v2ray.ang.handler.AppLocaleManager
import com.v2ray.ang.stratos.StratosAdminConfig
import com.v2ray.ang.stratos.StratosSession
import com.v2ray.ang.stratos.StratosSettingsController
import com.v2ray.ang.ui.base.HelperBaseComponentActivity
import com.v2ray.ang.ui.settings.SettingsActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

// --------------------------------------------------------------------------------------
// ViewModel
// --------------------------------------------------------------------------------------

data class StratosSettingsUiState(
    val languageCode: String = "fa",
    val hasOperatorSettings: Boolean = false,
)

class StratosSettingsViewModel(app: AngApplication) : ViewModel() {

    private val _uiState = MutableStateFlow(StratosSettingsUiState())
    val uiState: StateFlow<StratosSettingsUiState> = _uiState.asStateFlow()

    private val _applied = MutableStateFlow(false)
    val applied: StateFlow<Boolean> = _applied.asStateFlow()

    init {
        val admin = StratosSession.adminState.value
        _uiState.update {
            it.copy(
                languageCode = currentLanguageCode(),
                hasOperatorSettings = admin.bestSettings.isNotEmpty() || admin.forcedSettings.isNotEmpty(),
            )
        }
    }

    private fun currentLanguageCode(): String =
        runCatching {
            androidx.appcompat.app.AppCompatDelegate.getApplicationLocales().toLanguageTags()
        }.getOrNull()?.takeIf { it.isNotBlank() }?.substringBefore('-')
            ?: com.v2ray.ang.handler.MmkvManager.decodeSettingsString(com.v2ray.ang.AppConfig.PREF_LANGUAGE)
            ?: "fa"

    fun applyBestSettings() {
        StratosSettingsController.applyBestSettings()
        _applied.value = true
    }

    fun consumeApplied() {
        _applied.value = false
    }

    fun setLanguage(code: String) {
        val language = Language.fromCode(code)
        if (language == Language.AUTO) return
        AppLocaleManager.setApplicationLanguage(language.code)
        _uiState.update { it.copy(languageCode = code) }
    }

    companion object {
        fun factory(app: AngApplication) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                StratosSettingsViewModel(app) as T
        }
    }
}

// --------------------------------------------------------------------------------------
// Activity
// --------------------------------------------------------------------------------------

class StratosSettingsActivity : HelperBaseComponentActivity() {

    private val viewModel: StratosSettingsViewModel by viewModels {
        StratosSettingsViewModel.factory(application as AngApplication)
    }

    @Composable
    override fun ScreenContent() {
        BackHandler { finish() }
        val applied by viewModel.applied.collectAsStateWithLifecycle()
        LaunchedEffect(applied) {
            if (applied) {
                toast(R.string.stratos_settings_best_applied)
                viewModel.consumeApplied()
            }
        }
        StratosSettingsScreen(
            uiState = viewModel.uiState,
            onBack = { finish() },
            onApplyBest = viewModel::applyBestSettings,
            onLanguage = viewModel::setLanguage,
            onAdvanced = { startActivity(Intent(this, SettingsActivity::class.java)) },
        )
    }
}

// --------------------------------------------------------------------------------------
// Screen
// --------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StratosSettingsScreen(
    uiState: StateFlow<StratosSettingsUiState>,
    onBack: () -> Unit,
    onApplyBest: () -> Unit,
    onLanguage: (String) -> Unit,
    onAdvanced: () -> Unit,
) {
    val state by uiState.collectAsStateWithLifecycle()

    StratosBackdrop {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                stringResource(R.string.stratos_menu_settings),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                            )
                            Text(
                                stringResource(R.string.stratos_tagline),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
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
                    .padding(innerPadding),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .widthIn(max = 680.dp)
                        .align(Alignment.TopCenter)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Spacer(Modifier.height(4.dp))

                StratosGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                ) {
                    Column(Modifier.padding(17.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = StratosColors.Cyan.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(13.dp),
                                modifier = Modifier.size(43.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        painterResource(R.drawable.ic_stratos_auto_24),
                                        contentDescription = null,
                                        tint = StratosColors.Cyan,
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                            }
                            Spacer(Modifier.width(11.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.stratos_settings_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                )
                                Text(
                                    stringResource(R.string.stratos_settings_apply_best_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        StratosPrimaryButton(
                            onClick = onApplyBest,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_stratos_check_24),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.stratos_settings_apply_best),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }

                StratosGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                ) {
                    Column(Modifier.padding(17.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painterResource(R.drawable.ic_translate_24dp),
                                contentDescription = null,
                                tint = StratosColors.SoftIndigo,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(9.dp))
                            Text(
                                stringResource(R.string.stratos_language),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        StratosLanguageRow(
                            "fa",
                            stringResource(R.string.stratos_language_persian),
                            state.languageCode,
                            onLanguage,
                        )
                        Spacer(Modifier.height(4.dp))
                        StratosLanguageRow(
                            "en",
                            stringResource(R.string.stratos_language_english),
                            state.languageCode,
                            onLanguage,
                        )
                    }
                }

                StratosGlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .clickable(role = Role.Button, onClick = onAdvanced)
                        .semantics(mergeDescendants = true) {
                            role = Role.Button
                        },
                    shape = RoundedCornerShape(24.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(17.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            color = StratosColors.SoftIndigo.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(13.dp),
                            modifier = Modifier.size(43.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painterResource(R.drawable.ic_stratos_settings_24),
                                    contentDescription = null,
                                    tint = StratosColors.SoftIndigo,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.stratos_settings_advanced),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                stringResource(R.string.stratos_settings_advanced_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    }
                    Spacer(Modifier.height(20.dp))
                }
            }
        }
    }
}

@Composable
private fun StratosLanguageRow(
    code: String,
    label: String,
    selected: String,
    onSelect: (String) -> Unit,
) {
    val isSelected = code == selected
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isSelected) StratosColors.Cyan.copy(alpha = 0.08f) else Color.Transparent,
                RoundedCornerShape(14.dp),
            )
            .clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.RadioButton) { onSelect(code) }
            .semantics(mergeDescendants = true) {
                role = Role.RadioButton
                this.selected = isSelected
            }
            .padding(horizontal = 7.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = isSelected,
            onClick = null,
            modifier = Modifier.clearAndSetSemantics { },
        )
        Spacer(Modifier.width(9.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) StratosColors.Cyan else MaterialTheme.colorScheme.onSurface,
        )
    }
}
