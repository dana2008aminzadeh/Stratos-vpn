package com.v2ray.ang.ui.stratos

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.R
import com.v2ray.ang.stratos.StratosSession
import com.v2ray.ang.ui.base.HelperBaseComponentActivity
import com.v2ray.ang.util.Utils

class StratosAboutActivity : HelperBaseComponentActivity() {

    @Composable
    override fun ScreenContent() {
        BackHandler { finish() }
        StratosAboutScreen(
            onBack = { finish() },
            onWebsite = {
                StratosSession.adminState.value.websiteUrl
                    .takeIf { it.isNotBlank() }
                    ?.let { Utils.openUri(this, it) }
            },
            onTelegram = {
                StratosSession.adminState.value.telegramUrl
                    .takeIf { it.isNotBlank() }
                    ?.let { Utils.openUri(this, it) }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StratosAboutScreen(
    onBack: () -> Unit,
    onWebsite: () -> Unit,
    onTelegram: () -> Unit,
) {
    StratosBackdrop {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            stringResource(R.string.stratos_menu_about),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
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
                    .padding(innerPadding),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .widthIn(max = 600.dp)
                        .align(Alignment.TopCenter)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(24.dp))
                Box(
                    modifier = Modifier
                        .size(132.dp)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    StratosColors.Cyan.copy(alpha = 0.18f),
                                    StratosColors.Indigo.copy(alpha = 0.08f),
                                    Color.Transparent,
                                ),
                            ),
                            CircleShape,
                        )
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.44f),
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    StratosLogo(104.dp, contentDescription = null)
                }
                Spacer(Modifier.height(15.dp))
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.4).sp,
                )
                Text(
                    stringResource(R.string.stratos_tagline),
                    style = MaterialTheme.typography.bodyMedium,
                    color = StratosColors.Cyan,
                    letterSpacing = 1.sp,
                )
                Spacer(Modifier.height(6.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.64f),
                    shape = RoundedCornerShape(50),
                ) {
                    Text(
                        stringResource(R.string.stratos_version, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }

                Spacer(Modifier.height(24.dp))
                StratosGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_stratos_shield_24),
                            contentDescription = null,
                            tint = StratosColors.Cyan,
                            modifier = Modifier.size(25.dp),
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            stringResource(R.string.stratos_about_text),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = onWebsite,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_stratos_web_24),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.stratos_menu_website), fontWeight = FontWeight.SemiBold)
                    }
                    FilledTonalButton(
                        onClick = onTelegram,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_stratos_telegram_24),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.stratos_menu_telegram), fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(Modifier.height(30.dp))
                Text(
                    stringResource(R.string.stratos_powered_by),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.64f),
                )
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}
