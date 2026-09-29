package com.v2ray.ang.ui.stratos

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.LocalDarkTheme

/** Stratos brand accents shared by all branded screens. */
object StratosColors {
    val Cyan = Color(0xFF22D3EE)
    val Sky = Color(0xFF38BDF8)
    val Indigo = Color(0xFF6366F1)
    val SoftIndigo = Color(0xFF818CF8)
    val Success = Color(0xFF34D399)
    val Warning = Color(0xFFFBBF24)
    val Danger = Color(0xFFF87171)
    val SpaceDeep = Color(0xFF060818)
    val Mist = Color(0xFF9AA7C7)

    val BrandGradient = listOf(Cyan, Sky, Indigo)
}

/**
 * Subtle "deep space" backdrop: a vertical gradient plus two soft radial glows.
 * Pure decoration — content is drawn by the caller on top.
 */
@Composable
fun StratosBackdrop(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val dark = LocalDarkTheme.current
    val base = MaterialTheme.colorScheme.background
    val glowA = if (dark) StratosColors.Indigo.copy(alpha = 0.10f) else StratosColors.Sky.copy(alpha = 0.12f)
    val glowB = if (dark) StratosColors.Cyan.copy(alpha = 0.07f) else StratosColors.Indigo.copy(alpha = 0.06f)
    Box(
        modifier
            .fillMaxSize()
            .background(base),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(glowA, Color.Transparent),
                        center = Offset(160f, 140f),
                        radius = 900f,
                    ),
                ),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(glowB, Color.Transparent),
                        center = Offset(900f, 1500f),
                        radius = 1100f,
                    ),
                ),
        )
        content()
    }
}

/** The Stratos planet-and-orbit mark. */
@Composable
fun StratosLogo(size: Dp, modifier: Modifier = Modifier, contentDescription: String? = null) {
    Image(
        painter = painterResource(R.drawable.ic_stratos_logo),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
    )
}
