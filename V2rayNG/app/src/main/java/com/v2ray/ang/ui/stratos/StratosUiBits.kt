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
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.R
import com.v2ray.ang.ui.compose.LocalDarkTheme

/** Visual tokens for the Stratos midnight-luxe design system. */
object StratosColors {
    val Cyan = Color(0xFF27D7F2)
    val Sky = Color(0xFF49BFFB)
    val Indigo = Color(0xFF6D67F6)
    val SoftIndigo = Color(0xFF938BFF)
    val Aurora = Color(0xFFB17BFF)
    val Gold = Color(0xFFFFD28A)
    val Success = Color(0xFF42E8B4)
    val Warning = Color(0xFFFFC857)
    val Danger = Color(0xFFFF758C)
    val SpaceDeep = Color(0xFF040615)
    val Space = Color(0xFF090D21)
    val SpaceRaised = Color(0xFF111833)
    val Mist = Color(0xFFA8B4D4)
    val Pearl = Color(0xFFF5F7FF)

    val BrandGradient = listOf(Cyan, Sky, Indigo, Aurora)
    val PremiumGradient = Brush.linearGradient(
        colors = BrandGradient,
        start = Offset.Zero,
        end = Offset(1100f, 740f),
    )
}

private val starPositions = listOf(
    0.06f to 0.11f,
    0.18f to 0.34f,
    0.31f to 0.08f,
    0.45f to 0.22f,
    0.63f to 0.10f,
    0.82f to 0.26f,
    0.94f to 0.07f,
    0.10f to 0.56f,
    0.27f to 0.72f,
    0.43f to 0.59f,
    0.71f to 0.61f,
    0.88f to 0.77f,
    0.54f to 0.89f,
    0.16f to 0.92f,
    0.96f to 0.47f,
)

/**
 * A layered night-sky canvas shared by branded screens. Its contrast is deliberately restrained:
 * it gives every page depth without competing with controls or readable text.
 */
@Composable
fun StratosBackdrop(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val dark = LocalDarkTheme.current
    val base = MaterialTheme.colorScheme.background
    val lower = if (dark) StratosColors.SpaceDeep else MaterialTheme.colorScheme.surfaceContainerLow
    val starColor = if (dark) StratosColors.Pearl else StratosColors.Indigo
    val orbitColor = if (dark) StratosColors.SoftIndigo else StratosColors.Indigo

    Box(
        modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(base, lower, base),
                    startY = 0f,
                    endY = 2200f,
                ),
            ),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val darkGlow = if (dark) 1f else 0.56f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        StratosColors.Indigo.copy(alpha = 0.27f * darkGlow),
                        StratosColors.Aurora.copy(alpha = 0.09f * darkGlow),
                        Color.Transparent,
                    ),
                    center = Offset(size.width * 0.03f, size.height * 0.03f),
                    radius = size.width * 0.96f,
                ),
                radius = size.width * 0.96f,
                center = Offset(size.width * 0.03f, size.height * 0.03f),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        StratosColors.Cyan.copy(alpha = 0.17f * darkGlow),
                        Color.Transparent,
                    ),
                    center = Offset(size.width * 1.02f, size.height * 0.62f),
                    radius = size.width * 0.82f,
                ),
                radius = size.width * 0.82f,
                center = Offset(size.width * 1.02f, size.height * 0.62f),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(StratosColors.Gold.copy(alpha = 0.055f * darkGlow), Color.Transparent),
                    center = Offset(size.width * 0.52f, size.height * 0.98f),
                    radius = size.width * 0.66f,
                ),
                radius = size.width * 0.66f,
                center = Offset(size.width * 0.52f, size.height * 0.98f),
            )

            val primaryOrbit = Size(size.width * 1.38f, size.width * 0.56f)
            drawArc(
                color = orbitColor.copy(alpha = if (dark) 0.13f else 0.075f),
                startAngle = 192f,
                sweepAngle = 205f,
                useCenter = false,
                topLeft = Offset(-size.width * 0.18f, size.height * 0.15f),
                size = primaryOrbit,
                style = Stroke(width = 1.dp.toPx()),
            )
            drawArc(
                color = StratosColors.Cyan.copy(alpha = if (dark) 0.10f else 0.065f),
                startAngle = 14f,
                sweepAngle = 190f,
                useCenter = false,
                topLeft = Offset(-size.width * 0.08f, size.height * 0.62f),
                size = primaryOrbit,
                style = Stroke(width = 1.dp.toPx()),
            )
            drawArc(
                color = StratosColors.Gold.copy(alpha = if (dark) 0.05f else 0.04f),
                startAngle = 214f,
                sweepAngle = 134f,
                useCenter = false,
                topLeft = Offset(size.width * 0.32f, size.height * 0.36f),
                size = Size(size.width * 1.04f, size.width * 0.43f),
                style = Stroke(width = 1.dp.toPx()),
            )

            starPositions.forEachIndexed { index, (x, y) ->
                val radius = if (index % 5 == 0) 1.45.dp else 0.75.dp
                val center = Offset(size.width * x, size.height * y)
                val alpha = if (dark) 0.13f + (index % 3) * 0.055f else 0.09f
                drawCircle(color = starColor.copy(alpha = alpha), radius = radius.toPx(), center = center)
                if (index % 5 == 0) {
                    drawLine(
                        color = starColor.copy(alpha = alpha * 0.58f),
                        start = Offset(center.x - 3.dp.toPx(), center.y),
                        end = Offset(center.x + 3.dp.toPx(), center.y),
                        strokeWidth = 0.55.dp.toPx(),
                    )
                    drawLine(
                        color = starColor.copy(alpha = alpha * 0.58f),
                        start = Offset(center.x, center.y - 3.dp.toPx()),
                        end = Offset(center.x, center.y + 3.dp.toPx()),
                        strokeWidth = 0.55.dp.toPx(),
                    )
                }
            }
        }
        content()
    }
}

/** A tactile, translucent surface with a subtle spectral rim and elevated shadow. */
@Composable
fun StratosGlassCard(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(24.dp),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    val dark = LocalDarkTheme.current
    val fill = if (dark) {
        Brush.linearGradient(
            listOf(
                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.90f),
                MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.79f),
                MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.84f),
            ),
            start = Offset(0f, 0f),
            end = Offset(900f, 1100f),
        )
    } else {
        Brush.linearGradient(
            listOf(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.90f),
            ),
            start = Offset(0f, 0f),
            end = Offset(900f, 1100f),
        )
    }
    val edge = Brush.linearGradient(
        listOf(
            Color.White.copy(alpha = if (dark) 0.19f else 0.86f),
            StratosColors.Cyan.copy(alpha = if (dark) 0.28f else 0.18f),
            StratosColors.Indigo.copy(alpha = if (dark) 0.22f else 0.16f),
            Color.Transparent,
        ),
    )

    Box(
        modifier = modifier
            .shadow(14.dp, shape, clip = false)
            .clip(shape)
            .background(fill)
            .border(1.dp, edge, shape)
            .padding(contentPadding),
    ) {
        Canvas(Modifier.matchParentSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = if (dark) 0.045f else 0.24f), Color.Transparent),
                    center = Offset(size.width * 0.10f, -size.height * 0.05f),
                    radius = size.width * 0.90f,
                ),
                radius = size.width * 0.90f,
                center = Offset(size.width * 0.10f, -size.height * 0.05f),
            )
        }
        content()
    }
}

/** Main call-to-action treatment used on login and server-selection screens. */
@Composable
fun StratosPrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 15.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    val brush = if (enabled) {
        StratosColors.PremiumGradient
    } else {
        Brush.linearGradient(
            listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surfaceVariant),
        )
    }
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 54.dp)
            .shadow(if (enabled) 12.dp else 0.dp, shape, clip = false)
            .clip(shape)
            .background(brush)
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = if (enabled) 0.32f else 0.08f),
                shape = shape,
            )
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { }
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.matchParentSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White.copy(alpha = if (enabled) 0.28f else 0f), Color.Transparent),
                    center = Offset(size.width * 0.15f, -size.height * 0.1f),
                    radius = size.width * 0.75f,
                ),
                radius = size.width * 0.75f,
                center = Offset(size.width * 0.15f, -size.height * 0.1f),
            )
        }
        Row(
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
            .shadow(if (enabled) 8.dp else 0.dp, CircleShape, clip = false)
            .background(
                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = if (enabled) 0.78f else 0.42f),
                CircleShape,
            )
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.62f),
                CircleShape,
            )
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {
                this.contentDescription = contentDescription
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
        StratosLogo(if (compact) 40.dp else 50.dp, contentDescription = null)
        Spacer(Modifier.width(if (compact) 9.dp else 12.dp))
        Column {
            Text(
                text = title,
                style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.45.sp,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 1.35.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Page title with an editorial hierarchy that works in LTR and RTL locales. */
@Composable
fun StratosScreenTitle(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.35.sp,
            color = StratosColors.Cyan,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.size(2.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = (-0.25).sp,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Small non-interactive state marker for high-value status or selected states. */
@Composable
fun StratosStatusBadge(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.13f),
        shape = RoundedCornerShape(50),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(6.dp)
                    .background(color, CircleShape),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = color,
                maxLines = 1,
            )
        }
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
