package com.kargroup.planner.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object KarColors {
    val Navy = Color(0xFF0B1E3F)
    val Navy2 = Color(0xFF0369A1)

    // Corporate accents (names kept for compatibility across screens)
    // Sky & pastel blue palette
    val Blue = Color(0xFF0EA5E9)
    val Sky = Color(0xFF38BDF8)
    val SkyMid = Color(0xFF0EA5E9)
    val SkyDeep = Color(0xFF0284C7)
    val SkyLight = Color(0xFFE0F2FE)
    val SkyWash = Color(0xFFF0F8FF)
    val Pastel = Color(0xFFDBEEFC)
    val PastelLabel = Color(0xFF6B9AC4)
    val SidebarText = Color(0xFF1E3A5F)
    val Gold = Color(0xFFF5A623)
    val SurfaceBg = Color(0xFFF0F7FD)
    val Card = Color(0xFFFFFFFF)
    val Border = Color(0xFFD6E8F7)
    val Red = Color(0xFFEF4444)
    val Crimson = Color(0xFF9F1239)
    val Amber = Color(0xFFF59E0B)
    val Green = Color(0xFF10B981)
    val Slate = Color(0xFF64748B)
    val SlateLight = Color(0xFF94A3B8)
    val Ink = Color(0xFF0F172A)
    val Tint = Color(0xFFF5FAFF)
    val GoldTint = Color(0xFFE6F3FD)
}

/** Colour choices offered for projects and contexts. */
val PaletteChoices = listOf(
    "#0B1E3F", "#1E3A6E", "#2563EB", "#0EA5E9", "#10B981", "#84CC16",
    "#F5A623", "#F59E0B", "#EF4444", "#EC4899", "#8B5CF6", "#64748B",
)

/** "#RRGGBB" or "#AARRGGBB" → Color (plain Kotlin, so the desktop app can share it). */
fun parseHexColor(hex: String?, fallback: Color = KarColors.Navy2): Color = runCatching {
    val h = hex!!.trim().removePrefix("#")
    when (h.length) {
        6 -> Color((0xFF000000L or h.toLong(16)).toInt())
        8 -> Color(h.toLong(16).toInt())
        else -> fallback
    }
}.getOrDefault(fallback)

private val KarScheme = lightColorScheme(
    primary = KarColors.SkyDeep,
    onPrimary = Color.White,
    primaryContainer = KarColors.SkyLight,
    onPrimaryContainer = KarColors.Navy,
    secondary = KarColors.Sky,
    onSecondary = KarColors.Navy,
    secondaryContainer = KarColors.GoldTint,
    onSecondaryContainer = KarColors.Navy,
    tertiary = KarColors.SkyMid,
    onTertiary = Color.White,
    background = KarColors.SurfaceBg,
    onBackground = KarColors.Ink,
    surface = KarColors.Card,
    onSurface = KarColors.Ink,
    surfaceVariant = KarColors.SkyWash,
    onSurfaceVariant = KarColors.Slate,
    surfaceTint = Color.White,
    outline = Color(0xFFBFDDF5),
    outlineVariant = KarColors.Border,
    error = KarColors.Red,
    onError = Color.White,
    surfaceBright = Color.White,
    surfaceDim = KarColors.SurfaceBg,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = KarColors.SkyWash,
)

private val base = Typography()
private val Sans = FontFamily.SansSerif

private val KarTypography = Typography(
    displaySmall = base.displaySmall.copy(fontFamily = Sans, fontWeight = FontWeight.Bold),
    headlineMedium = base.headlineMedium.copy(fontFamily = Sans, fontWeight = FontWeight.Bold),
    headlineSmall = base.headlineSmall.copy(fontFamily = Sans, fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontFamily = Sans, fontWeight = FontWeight.Bold),
    titleMedium = base.titleMedium.copy(fontFamily = Sans, fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontFamily = Sans, fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.copy(fontFamily = Sans),
    bodyMedium = base.bodyMedium.copy(fontFamily = Sans),
    bodySmall = base.bodySmall.copy(fontFamily = Sans),
    labelLarge = base.labelLarge.copy(fontFamily = Sans, fontWeight = FontWeight.SemiBold),
    labelMedium = base.labelMedium.copy(fontFamily = Sans, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.3.sp),
)

/** All text is drawn at 75% of the system font size for a slightly more compact, professional look. */
private const val KAR_TEXT_SCALE = 0.75f

@Composable
fun KarTheme(content: @Composable () -> Unit) {
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, density.fontScale * KAR_TEXT_SCALE)
    ) {
        MaterialTheme(colorScheme = KarScheme, typography = KarTypography, content = content)
    }
}
