package com.v2ray.ang.ui.stratos

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
    val SpaceDeep = Color(0xFF030511)
    val Space = Color(0xFF090D24)
    val SpaceRaised = Color(0xFF11183A)
    val Mist = Color(0xFF9AA7C7)
    val Platinum = Color(0xFFEAF2FF)

    val BrandGradient = listOf(Cyan, Sky, Indigo)
}

private val StratosDarkScheme = darkColorScheme(
    primary = StratosColors.Sky,
    onPrimary = Color(0xFF031B2A),
    primaryContainer = Color(0xFF102B4A),
    onPrimaryContainer = Color(0xFFCBEAFF),
    secondary = StratosColors.Cyan,
    onSecondary = Color(0xFF002B32),
    secondaryContainer = Color(0xFF103D48),
    onSecondaryContainer = Color(0xFFB8F5FF),
    tertiary = StratosColors.SoftIndigo,
    onTertiary = Color(0xFF10143D),
    background = StratosColors.SpaceDeep,
    onBackground = StratosColors.Platinum,
    surface = StratosColors.Space,
    onSurface = StratosColors.Platinum,
    surfaceVariant = StratosColors.SpaceRaised,
    onSurfaceVariant = StratosColors.Mist,
    outline = Color(0xFF46547D),
    outlineVariant = Color(0xFF263158),
    error = StratosColors.Danger,
)

private val StratosLightScheme = lightColorScheme(
    primary = Color(0xFF3155C6),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE6FF),
    onPrimaryContainer = Color(0xFF10235C),
    secondary = Color(0xFF007D92),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC9F3FC),
    onSecondaryContainer = Color(0xFF00363F),
    tertiary = Color(0xFF514BC1),
    background = Color(0xFFF5F8FF),
    onBackground = Color(0xFF11172A),
    surface = Color.White,
    onSurface = Color(0xFF11172A),
    surfaceVariant = Color(0xFFE7ECF8),
    onSurfaceVariant = Color(0xFF505970),
    outline = Color(0xFF7F89A3),
    outlineVariant = Color(0xFFD5DCEA),
    error = Color(0xFFBA1A1A),
)

/**
 * Layered atmospheric background used by the Stratos experience. The faint stars,
 * horizon glow and orbital paths add depth without competing with interactive content.
 */
@Composable
fun StratosBackdrop(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val dark = LocalDarkTheme.current
    val upperGlow = if (dark) StratosColors.Indigo.copy(alpha = 0.24f)
    else StratosColors.Sky.copy(alpha = 0.18f)
    val lowerGlow = if (dark) StratosColors.Cyan.copy(alpha = 0.11f)
    else StratosColors.Indigo.copy(alpha = 0.08f)
    val starColor = if (dark) StratosColors.Platinum else StratosColors.Indigo
    val orbitColor = if (dark) StratosColors.Sky else StratosColors.Indigo

    MaterialTheme(colorScheme = if (dark) StratosDarkScheme else StratosLightScheme) {
        Box(
            modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        if (dark) {
                            listOf(StratosColors.SpaceDeep, StratosColors.Space, Color(0xFF080B20))
                        } else {
                            listOf(Color(0xFFF9FBFF), Color(0xFFF2F6FF), Color(0xFFEDF4FF))
                        },
                    ),
                ),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(upperGlow, Color.Transparent),
                        center = Offset(size.width * 0.16f, size.height * 0.04f),
                        radius = size.width * 1.05f,
                    ),
                    radius = size.width * 1.05f,
                    center = Offset(size.width * 0.16f, size.height * 0.04f),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(lowerGlow, Color.Transparent),
                        center = Offset(size.width * 0.94f, size.height * 0.82f),
                        radius = size.width * 0.88f,
                    ),
                    radius = size.width * 0.88f,
                    center = Offset(size.width * 0.94f, size.height * 0.82f),
                )

                val orbitStroke = Stroke(width = 1.2f, cap = StrokeCap.Round)
                drawOval(
                    color = orbitColor.copy(alpha = if (dark) 0.10f else 0.07f),
                    topLeft = Offset(-size.width * 0.38f, size.height * 0.08f),
                    size = androidx.compose.ui.geometry.Size(size.width * 1.55f, size.height * 0.23f),
                    style = orbitStroke,
                )
                drawOval(
                    color = orbitColor.copy(alpha = if (dark) 0.07f else 0.05f),
                    topLeft = Offset(size.width * 0.22f, size.height * 0.68f),
                    size = androidx.compose.ui.geometry.Size(size.width * 1.18f, size.height * 0.19f),
                    style = orbitStroke,
                )

                val stars = listOf(
                    0.08f to 0.14f, 0.25f to 0.09f, 0.72f to 0.12f, 0.90f to 0.20f,
                    0.14f to 0.38f, 0.84f to 0.43f, 0.06f to 0.66f, 0.93f to 0.62f,
                    0.18f to 0.84f, 0.67f to 0.91f, 0.88f to 0.79f,
                )
                stars.forEachIndexed { index, point ->
                    drawCircle(
                        color = starColor.copy(
                            alpha = if (dark) 0.16f + (index % 3) * 0.05f else 0.08f,
                        ),
                        radius = if (index % 4 == 0) 2.2f else 1.4f,
                        center = Offset(size.width * point.first, size.height * point.second),
                    )
                }
            }
            content()
        }
    }
}

/** A restrained glass-and-metal surface for primary Stratos content. */
@Composable
fun StratosGlassSurface(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val dark = LocalDarkTheme.current
    val shape = RoundedCornerShape(cornerRadius)
    val fill = if (dark) {
        listOf(Color(0xE6101735), Color(0xD90A0F27))
    } else {
        listOf(Color(0xF7FFFFFF), Color(0xEAF4F7FF))
    }
    val edge = if (dark) {
        listOf(Color.White.copy(alpha = 0.18f), StratosColors.Sky.copy(alpha = 0.24f), Color.White.copy(alpha = 0.05f))
    } else {
        listOf(Color.White, StratosColors.Sky.copy(alpha = 0.35f), StratosColors.Indigo.copy(alpha = 0.12f))
    }
    Box(
        modifier = modifier
            .background(Brush.linearGradient(fill), shape)
            .border(1.dp, Brush.linearGradient(edge), shape),
        content = content,
    )
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
