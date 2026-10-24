package com.kargroup.planner.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kargroup.planner.ui.theme.KarColors

val LogoGold = Color(0xFFF0AA00)

/** The "Split K" emblem drawn as vectors (200 × 211 design units). */
@Composable
fun KarEmblem(
    size: Dp,
    modifier: Modifier = Modifier,
    stemColor: Color = KarColors.Navy,
    armColor: Color = LogoGold,
    legColor: Color = stemColor,
) {
    Canvas(modifier.size(width = size * (200f / 211f), height = size)) {
        val s = this.size.height / 211f
        drawRect(stemColor, topLeft = Offset(0f, 0f), size = Size(52f * s, 211f * s))
        val upper = Path().apply {
            moveTo(137f * s, 0f); lineTo(200f * s, 0f); lineTo(137f * s, 98f * s); lineTo(74f * s, 98f * s); close()
        }
        drawPath(upper, armColor)
        val lower = Path().apply {
            moveTo(74f * s, 113f * s); lineTo(137f * s, 113f * s); lineTo(200f * s, 211f * s); lineTo(137f * s, 211f * s); close()
        }
        drawPath(lower, legColor)
    }
}

/** Full company logo artwork (emblem + "KAR GROUP" wordmark). */
@Suppress("DEPRECATION")
@Composable
fun KarLogo(
    modifier: Modifier = Modifier,
    height: Dp = 48.dp,
    onDark: Boolean = false,
) {
    Image(
        painter = painterResource(if (onDark) "kar_logo_white.png" else "kar_logo.png"),
        contentDescription = "KAR Group",
        contentScale = ContentScale.Fit,
        modifier = modifier.height(height).aspectRatio(722f / 244f),
    )
}
