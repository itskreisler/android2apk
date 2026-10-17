package com.klei.neko.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = NekoPurple,
    onPrimary = Color.White,
    primaryContainer = NekoLavender,
    onPrimaryContainer = Color(0xFF3B2A55),
    secondary = NekoPink,
    onSecondary = Color.White,
    secondaryContainer = NekoPinkPastel,
    onSecondaryContainer = Color(0xFF5A2440),
    tertiary = NekoPinkTertiary,
    onTertiary = Color(0xFF4A1D33),
    tertiaryContainer = NekoMintPastel,
    onTertiaryContainer = Color(0xFF12432F),
    background = NekoBackground,
    onBackground = NekoText,
    surface = NekoSurface,
    onSurface = NekoText,
    surfaceVariant = NekoLavender,
    onSurfaceVariant = Color(0xFF5B5268),
    outline = NekoOutline,
    outlineVariant = Color(0xFFE5DAF0),
)

private val DarkColors = darkColorScheme(
    primary = NekoPurpleDark,
    onPrimary = Color(0xFF2A1740),
    primaryContainer = Color(0xFF43296B),
    onPrimaryContainer = Color(0xFFEEDBFF),
    secondary = NekoPinkDark,
    onSecondary = Color(0xFF3F1229),
    secondaryContainer = Color(0xFF5A2440),
    onSecondaryContainer = Color(0xFFFFD9E8),
    tertiary = Color(0xFFF9C0D8),
    onTertiary = Color(0xFF43142B),
    tertiaryContainer = Color(0xFF14432F),
    onTertiaryContainer = Color(0xFFBFEFD8),
    background = NekoBackgroundDark,
    onBackground = NekoTextDark,
    surface = NekoSurfaceDark,
    onSurface = NekoTextDark,
    surfaceVariant = NekoSurfaceVariantDark,
    onSurfaceVariant = Color(0xFFDCCFE9),
    outline = NekoOutlineDark,
    outlineVariant = Color(0xFF43374F),
)

@Composable
fun NekoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialExpressiveTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}

/** Preferencia de tema del usuario (pantalla Perfil). */
enum class ThemeMode { SYSTEM, LIGHT, DARK }
