package com.kargroup.planner.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kargroup.planner.ui.theme.KarColors
import com.kargroup.planner.ui.theme.KarPalette

/** Brand gradients used to give surfaces a lit, three-dimensional feel. */
object KarBrush {
    val SkyHeader: Brush get() = if (KarPalette.dark) Brush.linearGradient(listOf(Color(0xFF075985), Color(0xFF0369A1), Color(0xFF0284C7)))
        else Brush.linearGradient(listOf(Color(0xFF0284C7), Color(0xFF0EA5E9), Color(0xFF38BDF8)))
    val Sidebar: Brush get() = if (KarPalette.dark) Brush.verticalGradient(listOf(Color(0xFF111926), Color(0xFF0D1520)))
        else Brush.verticalGradient(listOf(Color(0xFFF3F9FF), Color(0xFFE2F0FC)))
    val NavyVertical = Brush.verticalGradient(listOf(Color(0xFF1E3A6E), Color(0xFF0B1E3F)))
    val Gold = Brush.verticalGradient(listOf(Color(0xFF38BDF8), Color(0xFF0EA5E9), Color(0xFF0284C7)))
    val SelectedNav = Brush.horizontalGradient(listOf(Color(0xFF0EA5E9), Color(0xFF38BDF8)))
    val Backdrop: Brush get() = if (KarPalette.dark) Brush.verticalGradient(listOf(Color(0xFF0E141D), Color(0xFF0B1119)))
        else Brush.verticalGradient(listOf(Color(0xFFF5FAFF), Color(0xFFE6F2FC)))
    val CardSheen: Brush get() = if (KarPalette.dark) Brush.verticalGradient(listOf(Color(0xFF161E2B), Color(0xFF141B27)))
        else Brush.verticalGradient(listOf(Color.White, Color(0xFFFCFDFE)))
    fun tile(color: Color) = Brush.linearGradient(listOf(color.copy(alpha = 0.95f), color.copy(alpha = 0.70f)))
}

private val ShadowAmbient = Color(0xFF0369A1).copy(alpha = 0.10f)
private val ShadowSpot = Color(0xFF0369A1).copy(alpha = 0.20f)

/** Soft navy-tinted layered shadow. */
fun Modifier.depthShadow(elevation: Dp, shape: Shape): Modifier =
    this.shadow(elevation * KarPalette.depth, shape, clip = false, ambientColor = ShadowAmbient, spotColor = ShadowSpot)

/**
 * Spring-loaded press effect: the element sinks, scales down slightly and tilts
 * back in 3D, then springs back when released.
 */
@Composable
fun Modifier.pressDepth(interaction: MutableInteractionSource, tiltDegrees: Float = 5f): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.965f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "pressScale",
    )
    val tilt by animateFloatAsState(
        targetValue = if (pressed) tiltDegrees else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "pressTilt",
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
        rotationX = tilt
        cameraDistance = 14f * density
    }
}

/**
 * Elevated white card with a navy-tinted drop shadow, a hairline border and a 3D press
 * response. Pass [onClick] = null for a static card.
 */
@Composable
fun DepthCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(18.dp),
    elevation: Dp = 8.dp,
    onClick: (() -> Unit)? = null,
    background: Brush = KarBrush.CardSheen,
    border: BorderStroke? = BorderStroke(1.dp, KarColors.Border),
    content: @Composable BoxScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    var m = modifier
    if (onClick != null) m = m.pressDepth(interaction)
    m = m.depthShadow(elevation, shape).clip(shape).background(background)
    if (border != null) m = m.border(border, shape)
    if (onClick != null) m = m.clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick)
    Box(m, content = content)
}

/** Glossy gold gradient call-to-action button with depth and press animation. */
@Composable
fun GoldButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    height: Dp = 46.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier
            .pressDepth(interaction, 8f)
            .height(height)
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .depthShadow(if (enabled) 8.dp else 0.dp, shape)
            .clip(shape)
            .background(KarBrush.Gold)
            .border(1.dp, Color.White.copy(alpha = 0.35f), shape)
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClick = onClick,
            )
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        GoldButtonContent(text, icon)
    }
}

@Composable
private fun RowScope.GoldButtonContent(text: String, icon: ImageVector?) {
    if (icon != null) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
    }
    Text(text, color = Color.White, fontWeight = FontWeight.Bold)
}

/** Soft tinted tile with a coloured icon (used on stat cards). */
@Composable
fun SoftIconTile(icon: ImageVector, color: Color, size: Dp = 42.dp) {
    val shape = RoundedCornerShape(size * 0.28f)
    Box(
        Modifier
            .size(size)
            .clip(shape)
            .background(color.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(size * 0.52f))
    }
}

/** Rounded gradient tile holding an icon - gives icons a raised, lit appearance. */
@Composable
fun IconTile(icon: ImageVector, color: Color, size: Dp = 44.dp) {
    val shape = RoundedCornerShape(size * 0.3f)
    Box(
        Modifier
            .size(size)
            .depthShadow(6.dp, shape)
            .clip(shape)
            .background(KarBrush.tile(color))
            .border(1.dp, Color.White.copy(alpha = 0.35f), shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(size * 0.5f))
    }
}
