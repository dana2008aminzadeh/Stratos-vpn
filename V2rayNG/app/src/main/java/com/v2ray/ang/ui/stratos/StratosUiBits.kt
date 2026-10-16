package com.v2ray.ang.ui.stratos

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.LocalDarkTheme

/**
 * Stratos brand palette — aurora planet: cyan skies over indigo-violet space.
 * Shared by every branded screen so the whole app reads as one product.
 */
object StratosColors {
    val Cyan = Color(0xFF3BE3FF)
    val Sky = Color(0xFF48B8FF)
    val Indigo = Color(0xFF6C63FF)
    val SoftIndigo = Color(0xFF9A8CFF)
    val Aurora = Color(0xFFB14BFF)
    val Violet = Color(0xFF7C3AED)
    val Success = Color(0xFF3DF2B6)
    val Warning = Color(0xFFFFD166)
    val Danger = Color(0xFFFF6B8A)
    val SpaceDeep = Color(0xFF05071A)
    val Space = Color(0xFF0A0E2A)
    val SpaceRaised = Color(0xFF141A3D)
    val Mist = Color(0xFFA9B6DE)
    val Pearl = Color(0xFFF4F7FF)

    val BrandGradient = listOf(Cyan, Sky, Indigo, Aurora)
    val PremiumGradient = Brush.linearGradient(
        colors = BrandGradient,
        start = Offset.Zero,
        end = Offset(980f, 780f),
    )
    val AuroraGradient = Brush.sweepGradient(
        colors = listOf(Cyan, Sky, Indigo, Aurora, Cyan),
    )
}

private val starPositions = listOf(
    0.07f to 0.11f, 0.18f to 0.30f, 0.30f to 0.07f, 0.44f to 0.18f, 0.62f to 0.10f,
    0.81f to 0.23f, 0.92f to 0.06f, 0.10f to 0.55f, 0.26f to 0.70f, 0.70f to 0.60f,
    0.88f to 0.77f, 0.51f to 0.87f, 0.15f to 0.91f, 0.95f to 0.46f, 0.38f to 0.42f,
    0.66f to 0.33f, 0.05f to 0.78f, 0.84f to 0.92f,
)

/**
 * Atmospheric space backdrop: nebula haze, a faint orbit arc and a static star
 * field. Everything is painted once per state change — no frame-by-frame
 * animation — to keep idle rendering cost (and battery use) near zero.
 */
@Composable
fun StratosBackdrop(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val dark = LocalDarkTheme.current
    val base = MaterialTheme.colorScheme.background
    val lower = if (dark) StratosColors.SpaceDeep else Color(0xFF111637)

    Box(
        modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(base, lower, base),
                    startY = 0f,
                    endY = 2400f,
                ),
            ),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            // nebula glow — top-left aurora
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        StratosColors.Indigo.copy(alpha = if (dark) 0.30f else 0.16f),
                        StratosColors.Aurora.copy(alpha = if (dark) 0.10f else 0.05f),
                        Color.Transparent,
                    ),
                    center = Offset(size.width * 0.05f, size.height * 0.02f),
                    radius = size.width * 1.02f,
                ),
                radius = size.width * 1.02f,
                center = Offset(size.width * 0.05f, size.height * 0.02f),
            )
            // nebula glow — bottom-right cyan
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        StratosColors.Cyan.copy(alpha = if (dark) 0.16f else 0.10f),
                        Color.Transparent,
                    ),
                    center = Offset(size.width * 1.0f, size.height * 0.72f),
                    radius = size.width * 0.85f,
                ),
                radius = size.width * 0.85f,
                center = Offset(size.width * 1.0f, size.height * 0.72f),
            )
            // signature orbit arc
            val orbitSize = Size(size.width * 1.38f, size.width * 0.56f)
            drawArc(
                brush = Brush.sweepGradient(
                    listOf(
                        Color.Transparent,
                        StratosColors.Cyan.copy(alpha = if (dark) 0.16f else 0.10f),
                        StratosColors.SoftIndigo.copy(alpha = if (dark) 0.12f else 0.08f),
                        Color.Transparent,
                    ),
                ),
                startAngle = 188f,
                sweepAngle = 212f,
                useCenter = false,
                topLeft = Offset(-size.width * 0.18f, size.height * 0.15f),
                size = orbitSize,
                style = Stroke(width = 1.2.dp.toPx()),
            )
            drawArc(
                color = StratosColors.SoftIndigo.copy(alpha = if (dark) 0.10f else 0.07f),
                startAngle = 8f,
                sweepAngle = 196f,
                useCenter = false,
                topLeft = Offset(-size.width * 0.05f, size.height * 0.66f),
                size = orbitSize,
                style = Stroke(width = 1.dp.toPx()),
            )

            starPositions.forEachIndexed { index, (x, y) ->
                drawCircle(
                    color = StratosColors.Pearl.copy(alpha = if (dark) 0.18f + (index % 3) * 0.05f else 0.12f),
                    radius = (if (index % 4 == 0) 1.35.dp else 0.8.dp).toPx(),
                    center = Offset(size.width * x, size.height * y),
                )
            }
        }
        content()
    }
}

/**
 * Translucent premium surface with a spectral gradient edge and inner light.
 */
@Composable
fun StratosGlassCard(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(24.dp),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    glow: Color = StratosColors.Cyan,
    content: @Composable BoxScope.() -> Unit,
) {
    val dark = LocalDarkTheme.current
    val fill = if (dark) {
        Brush.linearGradient(
            listOf(
                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.90f),
                MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.74f),
            ),
        )
    } else {
        Brush.linearGradient(
            listOf(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.88f),
            ),
        )
    }
    val edge = Brush.linearGradient(
        listOf(
            Color.White.copy(alpha = if (dark) 0.20f else 0.85f),
            glow.copy(alpha = if (dark) 0.32f else 0.22f),
            StratosColors.Indigo.copy(alpha = if (dark) 0.30f else 0.18f),
            Color.Transparent,
        ),
    )

    Box(
        modifier = modifier
            .background(fill, shape)
            .border(1.dp, edge, shape)
            .padding(contentPadding),
        content = content,
    )
}

/** Primary call-to-action with brand gradient and a soft glow. */
@Composable
fun StratosPrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 22.dp, vertical = 15.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    val brush = if (enabled) {
        Brush.linearGradient(
            listOf(StratosColors.Cyan, StratosColors.Sky, StratosColors.Indigo),
        )
    } else {
        Brush.linearGradient(
            listOf(
                MaterialTheme.colorScheme.surfaceVariant,
                MaterialTheme.colorScheme.surfaceVariant,
            ),
        )
    }
    Box(modifier) {
        // glow layer beneath the button
        if (enabled) {
            Box(
                Modifier
                    .matchParentSize()
                    .padding(top = 6.dp, bottom = 2.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                StratosColors.Sky.copy(alpha = 0.38f),
                                Color.Transparent,
                            ),
                        ),
                        shape,
                    ),
            )
        }
        Row(
            modifier = Modifier
                .defaultMinSize(minHeight = 54.dp)
                .background(brush, shape)
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = if (enabled) 0.28f else 0.08f),
                    shape = shape,
                )
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .semantics(mergeDescendants = true) {
                    role = Role.Button
                }
                .padding(contentPadding),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/** Compact circular action used in branded top bars. */
@Composable
fun StratosIconAction(
    icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: (@Composable BoxScope.() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .background(
                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = if (enabled) 0.78f else 0.42f),
                CircleShape,
            )
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {
                this.contentDescription = contentDescription
                role = Role.Button
            },
        contentAlignment = Alignment.Center,
    ) {
        if (content != null) {
            content()
        } else {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** Branded lockup for premium headers and navigation surfaces. */
@Composable
fun StratosBrandLockup(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    compact: Boolean = false,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        StratosLogo(if (compact) 38.dp else 48.dp, contentDescription = null)
        Spacer(Modifier.width(if (compact) 9.dp else 12.dp))
        Column {
            Text(
                text = title,
                style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.4.sp,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 1.2.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The Stratos aurora-planet mark. */
@Composable
fun StratosLogo(size: Dp, modifier: Modifier = Modifier, contentDescription: String? = null) {
    Image(
        painter = painterResource(R.drawable.ic_stratos_logo),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
    )
}

/** Section heading inside premium cards. */
@Composable
fun StratosSectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** Rounded gradient progress bar. */
@Composable
fun StratosGradientProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
    gradient: Brush = StratosColors.PremiumGradient,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                RoundedCornerShape(50),
            ),
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(height)
                .background(gradient, RoundedCornerShape(50)),
        )
    }
}

/** Small label pill with brand tint. */
@Composable
fun StratosPill(
    text: String,
    modifier: Modifier = Modifier,
    tint: Color = StratosColors.Cyan,
    textColor: Color = tint,
) {
    Box(
        modifier = modifier
            .background(tint.copy(alpha = 0.13f), RoundedCornerShape(50))
            .border(1.dp, tint.copy(alpha = 0.30f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = textColor,
        )
    }
}

/** Thin divider with brand fade at the ends. */
@Composable
fun StratosDivider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(
                        Color.Transparent,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                        Color.Transparent,
                    ),
                ),
            ),
    )
}
