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

/** Stratos brand accents shared by all branded screens. */
object StratosColors {
    val Cyan = Color(0xFF27D7F2)
    val Sky = Color(0xFF49BFFB)
    val Indigo = Color(0xFF6D67F6)
    val SoftIndigo = Color(0xFF938BFF)
    val Aurora = Color(0xFF8B5CF6)
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
        end = Offset(900f, 700f),
    )
}

private val starPositions = listOf(
    0.08f to 0.12f,
    0.19f to 0.31f,
    0.31f to 0.08f,
    0.46f to 0.20f,
    0.64f to 0.11f,
    0.83f to 0.25f,
    0.93f to 0.07f,
    0.11f to 0.57f,
    0.28f to 0.71f,
    0.72f to 0.61f,
    0.89f to 0.78f,
    0.53f to 0.88f,
    0.16f to 0.92f,
)

/**
 * Branded atmospheric background used by all Stratos screens. The orbital lines and stars are
 * intentionally low contrast so text and controls remain the visual focus in both themes.
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
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(StratosColors.Indigo.copy(alpha = if (dark) 0.24f else 0.13f), Color.Transparent),
                    center = Offset(size.width * 0.08f, size.height * 0.05f),
                    radius = size.width * 0.92f,
                ),
                radius = size.width * 0.92f,
                center = Offset(size.width * 0.08f, size.height * 0.05f),
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(StratosColors.Cyan.copy(alpha = if (dark) 0.15f else 0.10f), Color.Transparent),
                    center = Offset(size.width * 0.95f, size.height * 0.68f),
                    radius = size.width * 0.78f,
                ),
                radius = size.width * 0.78f,
                center = Offset(size.width * 0.95f, size.height * 0.68f),
            )

            val orbitSize = Size(size.width * 1.30f, size.width * 0.52f)
            drawArc(
                color = orbitColor.copy(alpha = if (dark) 0.10f else 0.07f),
                startAngle = 192f,
                sweepAngle = 205f,
                useCenter = false,
                topLeft = Offset(-size.width * 0.15f, size.height * 0.17f),
                size = orbitSize,
                style = Stroke(width = 1.dp.toPx()),
            )
            drawArc(
                color = StratosColors.Cyan.copy(alpha = if (dark) 0.08f else 0.06f),
                startAngle = 12f,
                sweepAngle = 190f,
                useCenter = false,
                topLeft = Offset(-size.width * 0.04f, size.height * 0.64f),
                size = orbitSize,
                style = Stroke(width = 1.dp.toPx()),
            )

            starPositions.forEachIndexed { index, (x, y) ->
                drawCircle(
                    color = starColor.copy(alpha = if (dark) 0.16f + (index % 3) * 0.05f else 0.10f),
                    radius = (if (index % 4 == 0) 1.3.dp else 0.75.dp).toPx(),
                    center = Offset(size.width * x, size.height * y),
                )
            }
        }
        content()
    }
}

/** A premium translucent surface with a restrained spectral edge. */
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
                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.88f),
                MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.78f),
            ),
        )
    } else {
        Brush.linearGradient(
            listOf(
                MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.88f),
            ),
        )
    }
    val edge = Brush.linearGradient(
        listOf(
            Color.White.copy(alpha = if (dark) 0.16f else 0.82f),
            StratosColors.Cyan.copy(alpha = if (dark) 0.24f else 0.18f),
            StratosColors.Indigo.copy(alpha = if (dark) 0.28f else 0.16f),
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
            listOf(
                MaterialTheme.colorScheme.surfaceVariant,
                MaterialTheme.colorScheme.surfaceVariant,
            ),
        )
    }
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 54.dp)
            .background(brush, shape)
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = if (enabled) 0.24f else 0.08f),
                shape = shape,
            )
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { }
            .padding(contentPadding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
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
                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = if (enabled) 0.72f else 0.42f),
                CircleShape,
            )
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f), CircleShape)
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

/** The Stratos planet-and-orbit mark. */
@Composable
fun StratosLogo(size: Dp, modifier: Modifier = Modifier, contentDescription: String? = null) {
    Image(
        painter = painterResource(R.drawable.ic_stratos_logo),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
    )
}
