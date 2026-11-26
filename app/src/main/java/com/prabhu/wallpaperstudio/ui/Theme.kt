package com.prabhu.wallpaperstudio.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import com.prabhu.wallpaperstudio.data.Keys
import com.prabhu.wallpaperstudio.data.Store

/** The bold "hero" colour of a theme (the aqua card on Home, for Aqua). */
data class Brand(val color: Color, val onColor: Color)

val LocalBrand = staticCompositionLocalOf { Brand(Color(0xFF2AAAE0), Color(0xFF0D1B24)) }

/**
 * A theme. Everything else is worked out from three colours:
 * the hero colour, a deep "ink" colour and the light-mode accent.
 */
class Palette(
    val id: String,
    val name: String,
    val hero: Color,
    val onHero: Color,
    val accentLight: Color,
    val accentDark: Color,
    val bgTintLight: Color,
    val bgDark: Color,
) {
    val swatch get() = listOf(hero, onHero, accentDark)
}

val Palettes = listOf(
    Palette("aqua", "Aqua", Color(0xFF2AAAE0), Color(0xFF0D1B24), Color(0xFF0879A8), Color(0xFF5CCDF5), Color(0xFFEEF8FC), Color(0xFF08131B)),
    Palette("ocean", "Ocean", Color(0xFF1E4FA8), Color.White, Color(0xFF2F57B0), Color(0xFFA9C4FF), Color(0xFFF3F6FD), Color(0xFF0A1222)),
    Palette("mint", "Mint", Color(0xFF2BB89A), Color(0xFF0F2A25), Color(0xFF00796B), Color(0xFF5FD6BD), Color(0xFFF0FAF7), Color(0xFF08150F)),
    Palette("sunset", "Sunset", Color(0xFFF2784B), Color(0xFF2A1410), Color(0xFFB4471E), Color(0xFFFFB599), Color(0xFFFFF6F1), Color(0xFF1A110D)),
    Palette("grape", "Grape", Color(0xFF7B4FD6), Color.White, Color(0xFF6A3FC4), Color(0xFFCDB8FF), Color(0xFFF6F2FF), Color(0xFF120D1F)),
    Palette("rose", "Rose", Color(0xFFE0608A), Color(0xFF2A0D18), Color(0xFFB0315C), Color(0xFFFFB1C8), Color(0xFFFFF3F6), Color(0xFF1C0D12)),
    Palette("forest", "Forest", Color(0xFF2F6B3A), Color.White, Color(0xFF2E6B39), Color(0xFF9BD7A2), Color(0xFFF2F8F2), Color(0xFF0B140C)),
    Palette("gold", "Gold", Color(0xFFE8B03A), Color(0xFF2A1E05), Color(0xFF8A5A00), Color(0xFFFFD27A), Color(0xFFFFF8EC), Color(0xFF18120A)),
    Palette("slate", "Slate", Color(0xFF4B5D6E), Color.White, Color(0xFF3E5468), Color(0xFFB4C8DA), Color(0xFFF3F5F7), Color(0xFF0E1215)),
    Palette("mono", "Mono", Color(0xFF111111), Color.White, Color(0xFF1F1F1F), Color(0xFFE6E6E6), Color(0xFFF7F7F7), Color(0xFF0E0E0E)),
)

/** Material You is offered as an extra choice on Android 12+. */
const val MATERIAL_YOU = "you"

private fun lightScheme(p: Palette): ColorScheme {
    val accent = p.accentLight
    val container = lerp(accent, Color.White, 0.82f)
    val onContainer = lerp(accent, Color.Black, 0.65f)
    return lightColorScheme(
        primary = accent, onPrimary = Color.White,
        primaryContainer = container, onPrimaryContainer = onContainer,
        secondary = accent, onSecondary = Color.White,
        secondaryContainer = container, onSecondaryContainer = onContainer,
        tertiary = Color(0xFF875300), onTertiary = Color.White,
        tertiaryContainer = Color(0xFFFFDCBE), onTertiaryContainer = Color(0xFF2C1600),
        background = p.bgTintLight, onBackground = Color(0xFF12181C),
        surface = p.bgTintLight, onSurface = Color(0xFF12181C),
        surfaceVariant = Color(0xFFDDE3E8), onSurfaceVariant = Color(0xFF46525C),
        outline = Color(0xFF8A949C), outlineVariant = Color(0xFFD5DDE2),
        surfaceContainerLowest = Color.White, surfaceContainerLow = Color.White,
        surfaceContainer = Color.White, surfaceContainerHigh = lerp(p.bgTintLight, Color.Black, 0.04f),
        surfaceContainerHighest = lerp(p.bgTintLight, Color.Black, 0.08f),
        inverseSurface = Color(0xFF0D1B24), inverseOnSurface = Color(0xFFE4ECF0),
    )
}

private fun darkScheme(p: Palette): ColorScheme {
    val accent = p.accentDark
    val surface = lerp(p.bgDark, Color.White, 0.05f)
    return darkColorScheme(
        primary = accent, onPrimary = lerp(accent, Color.Black, 0.75f),
        primaryContainer = lerp(accent, Color.Black, 0.6f), onPrimaryContainer = lerp(accent, Color.White, 0.75f),
        secondary = accent, onSecondary = lerp(accent, Color.Black, 0.75f),
        secondaryContainer = lerp(accent, Color.Black, 0.6f), onSecondaryContainer = lerp(accent, Color.White, 0.75f),
        tertiary = Color(0xFFFFB873), onTertiary = Color(0xFF4A2800),
        tertiaryContainer = Color(0xFF6A3B00), onTertiaryContainer = Color(0xFFFFDCBE),
        background = p.bgDark, onBackground = Color(0xFFE4ECF0),
        surface = p.bgDark, onSurface = Color(0xFFE4ECF0),
        surfaceVariant = Color(0xFF2A3640), onSurfaceVariant = Color(0xFFAAB6BE),
        outline = Color(0xFF75818A), outlineVariant = Color(0xFF2A3640),
        surfaceContainerLowest = p.bgDark, surfaceContainerLow = surface,
        surfaceContainer = surface, surfaceContainerHigh = lerp(p.bgDark, Color.White, 0.10f),
        surfaceContainerHighest = lerp(p.bgDark, Color.White, 0.14f),
        inverseSurface = Color(0xFFE4ECF0), inverseOnSurface = Color(0xFF0D1B24),
    )
}

@Composable
fun isAppInDarkTheme(): Boolean {
    Store.tick.intValue
    return when (Store.string(Keys.THEME, "system")) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
}

/** Colours for a palette, used both for the app and for theme previews. */
fun schemeFor(id: String, dark: Boolean): Pair<ColorScheme, Brand>? {
    val p = Palettes.firstOrNull { it.id == id } ?: return null
    val brand = if (dark && p.id == "mono") Brand(Color(0xFFEDEDED), Color(0xFF111111)) else Brand(p.hero, p.onHero)
    return (if (dark) darkScheme(p) else lightScheme(p)) to brand
}

@Composable
fun StudioTheme(dark: Boolean, content: @Composable () -> Unit) {
    Store.tick.intValue
    val id = Store.palette()
    val context = LocalContext.current
    val useYou = id == MATERIAL_YOU && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val (scheme, brand) = when {
        useYou -> {
            val s = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            s to Brand(s.primary, s.onPrimary)
        }
        else -> schemeFor(id, dark) ?: schemeFor("aqua", dark)!!
    }
    CompositionLocalProvider(LocalBrand provides brand) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
