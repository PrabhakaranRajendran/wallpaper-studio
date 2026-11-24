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
import androidx.compose.ui.platform.LocalContext
import com.prabhu.wallpaperstudio.data.Keys
import com.prabhu.wallpaperstudio.data.Store

/** The bold "hero" colour of a theme (the aqua card on Home, for Aqua). */
data class Brand(val color: Color, val onColor: Color)

val LocalBrand = staticCompositionLocalOf { Brand(Color(0xFF2AAAE0), Color(0xFF0D1B24)) }

/** A theme the user can pick in Settings. */
class Palette(
    val id: String,
    val name: String,
    val swatch: List<Color>,
    val primaryL: Color, val onPrimaryL: Color, val containerL: Color, val onContainerL: Color, val bgL: Color,
    val primaryD: Color, val onPrimaryD: Color, val containerD: Color, val onContainerD: Color, val bgD: Color, val surfaceD: Color,
    val brandL: Brand, val brandD: Brand,
)

val Palettes = listOf(
    Palette(
        "aqua", "Aqua", listOf(Color(0xFF2AAAE0), Color(0xFF0D1B24), Color.White),
        Color(0xFF0879A8), Color.White, Color(0xFFC9EEFC), Color(0xFF00344A), Color(0xFFEEF8FC),
        Color(0xFF5CCDF5), Color(0xFF00344A), Color(0xFF0B5675), Color(0xFFCFF1FF), Color(0xFF08131B), Color(0xFF10202B),
        Brand(Color(0xFF2AAAE0), Color(0xFF0D1B24)), Brand(Color(0xFF2AAAE0), Color(0xFF0D1B24)),
    ),
    Palette(
        "ocean", "Ocean", listOf(Color(0xFF1E4FA8), Color(0xFF0A1A3A), Color(0xFF9CC3FF)),
        Color(0xFF2F57B0), Color.White, Color(0xFFDCE5FF), Color(0xFF001A43), Color(0xFFF3F6FD),
        Color(0xFFA9C4FF), Color(0xFF002E6B), Color(0xFF1E3F86), Color(0xFFDCE5FF), Color(0xFF0A1222), Color(0xFF121B2E),
        Brand(Color(0xFF1E4FA8), Color.White), Brand(Color(0xFF2B5CC0), Color.White),
    ),
    Palette(
        "mint", "Mint", listOf(Color(0xFF2BB89A), Color(0xFF0F2A25), Color(0xFFD6F5EC)),
        Color(0xFF00796B), Color.White, Color(0xFFC8F2E7), Color(0xFF00201B), Color(0xFFF0FAF7),
        Color(0xFF5FD6BD), Color(0xFF003731), Color(0xFF005047), Color(0xFFC8F2E7), Color(0xFF08150F), Color(0xFF10211B),
        Brand(Color(0xFF2BB89A), Color(0xFF0F2A25)), Brand(Color(0xFF2BB89A), Color(0xFF0F2A25)),
    ),
    Palette(
        "sunset", "Sunset", listOf(Color(0xFFF2784B), Color(0xFF2A1410), Color(0xFFFFD7B8)),
        Color(0xFFB4471E), Color.White, Color(0xFFFFDBCC), Color(0xFF3A0B00), Color(0xFFFFF6F1),
        Color(0xFFFFB599), Color(0xFF5A1B00), Color(0xFF8A3412), Color(0xFFFFDBCC), Color(0xFF1A110D), Color(0xFF261A15),
        Brand(Color(0xFFF2784B), Color(0xFF2A1410)), Brand(Color(0xFFF2784B), Color(0xFF2A1410)),
    ),
    Palette(
        "mono", "Mono", listOf(Color(0xFF111111), Color(0xFF6B6B6B), Color.White),
        Color(0xFF1F1F1F), Color.White, Color(0xFFE4E4E4), Color(0xFF111111), Color(0xFFF7F7F7),
        Color(0xFFE6E6E6), Color(0xFF111111), Color(0xFF3A3A3A), Color(0xFFEEEEEE), Color(0xFF0E0E0E), Color(0xFF1A1A1A),
        Brand(Color(0xFF111111), Color.White), Brand(Color(0xFFEDEDED), Color(0xFF111111)),
    ),
)

/** Material You is listed as a seventh choice on Android 12+. */
const val MATERIAL_YOU = "you"

private fun lightScheme(p: Palette): ColorScheme = lightColorScheme(
    primary = p.primaryL, onPrimary = p.onPrimaryL,
    primaryContainer = p.containerL, onPrimaryContainer = p.onContainerL,
    secondary = p.primaryL, onSecondary = p.onPrimaryL,
    secondaryContainer = p.containerL, onSecondaryContainer = p.onContainerL,
    tertiary = Color(0xFF875300), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDCBE), onTertiaryContainer = Color(0xFF2C1600),
    background = p.bgL, onBackground = Color(0xFF12181C),
    surface = p.bgL, onSurface = Color(0xFF12181C),
    surfaceVariant = Color(0xFFDDE3E8), onSurfaceVariant = Color(0xFF46525C),
    outline = Color(0xFF8A949C), outlineVariant = Color(0xFFD5DDE2),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color.White,
    surfaceContainer = Color.White, surfaceContainerHigh = Color(0xFFF1F4F6),
    surfaceContainerHighest = Color(0xFFE6EBEE),
)

private fun darkScheme(p: Palette): ColorScheme = darkColorScheme(
    primary = p.primaryD, onPrimary = p.onPrimaryD,
    primaryContainer = p.containerD, onPrimaryContainer = p.onContainerD,
    secondary = p.primaryD, onSecondary = p.onPrimaryD,
    secondaryContainer = p.containerD, onSecondaryContainer = p.onContainerD,
    tertiary = Color(0xFFFFB873), onTertiary = Color(0xFF4A2800),
    tertiaryContainer = Color(0xFF6A3B00), onTertiaryContainer = Color(0xFFFFDCBE),
    background = p.bgD, onBackground = Color(0xFFE4ECF0),
    surface = p.bgD, onSurface = Color(0xFFE4ECF0),
    surfaceVariant = Color(0xFF2A3640), onSurfaceVariant = Color(0xFFAAB6BE),
    outline = Color(0xFF75818A), outlineVariant = Color(0xFF2A3640),
    surfaceContainerLowest = p.bgD, surfaceContainerLow = p.surfaceD,
    surfaceContainer = p.surfaceD, surfaceContainerHigh = Color(0xFF1E2A33),
    surfaceContainerHighest = Color(0xFF28343D),
)

@Composable
fun isAppInDarkTheme(): Boolean {
    Store.tick.intValue
    return when (Store.string(Keys.THEME, "system")) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
}

@Composable
fun StudioTheme(dark: Boolean, content: @Composable () -> Unit) {
    Store.tick.intValue
    val id = Store.palette()
    val context = LocalContext.current
    val useYou = id == MATERIAL_YOU && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val palette = Palettes.firstOrNull { it.id == id } ?: Palettes.first()
    val scheme = when {
        useYou && dark -> dynamicDarkColorScheme(context)
        useYou -> dynamicLightColorScheme(context)
        dark -> darkScheme(palette)
        else -> lightScheme(palette)
    }
    val brand = when {
        useYou -> Brand(scheme.primary, scheme.onPrimary)
        dark -> palette.brandD
        else -> palette.brandL
    }
    CompositionLocalProvider(LocalBrand provides brand) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
