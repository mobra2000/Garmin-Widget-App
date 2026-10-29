package de.mbraisch.garminwidget.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Garmin-Blau als Grundton, wenn keine Systemfarben genutzt werden (oder vor Android 12). */
private val GarminLight = lightColorScheme(
    primary = Color(0xFF00629E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD0E4FF),
    onPrimaryContainer = Color(0xFF001D34),
    secondary = Color(0xFF526070),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD5E4F7),
    onSecondaryContainer = Color(0xFF0F1D2A),
    tertiary = Color(0xFF6A5778),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF2DAFF),
    onTertiaryContainer = Color(0xFF251431),
    background = Color(0xFFF7F9FC),
    onBackground = Color(0xFF191C20),
    surface = Color(0xFFF7F9FC),
    onSurface = Color(0xFF191C20),
    surfaceVariant = Color(0xFFDEE3EB),
    onSurfaceVariant = Color(0xFF42474E),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF1F4F9),
    surfaceContainer = Color(0xFFEBEEF3),
    surfaceContainerHigh = Color(0xFFE5E8ED),
    surfaceContainerHighest = Color(0xFFE0E2E8),
    outline = Color(0xFF72777F),
)

/** Dunkles Schema im Stil der Garmin-Connect-App: fast schwarzer Hintergrund, helles Blau. */
private val GarminDark = darkColorScheme(
    primary = Color(0xFF11A9ED),
    onPrimary = Color(0xFF00243A),
    primaryContainer = Color(0xFF004A75),
    onPrimaryContainer = Color(0xFFD0E4FF),
    secondary = Color(0xFFB9C8DA),
    onSecondary = Color(0xFF243240),
    secondaryContainer = Color(0xFF2A3644),
    onSecondaryContainer = Color(0xFFD5E4F7),
    tertiary = Color(0xFFD6BEE4),
    onTertiary = Color(0xFF3B2948),
    tertiaryContainer = Color(0xFF3E2F4C),
    onTertiaryContainer = Color(0xFFF2DAFF),
    background = Color(0xFF0B0F14),
    onBackground = Color(0xFFE2E2E6),
    surface = Color(0xFF0B0F14),
    onSurface = Color(0xFFE2E2E6),
    surfaceVariant = Color(0xFF2A3038),
    onSurfaceVariant = Color(0xFFC2C7CF),
    surfaceContainerLowest = Color(0xFF070A0E),
    surfaceContainerLow = Color(0xFF12171D),
    surfaceContainer = Color(0xFF161C23),
    surfaceContainerHigh = Color(0xFF1D232B),
    surfaceContainerHighest = Color(0xFF262D36),
    outline = Color(0xFF8C9199),
)

private val AppTypography = Typography().run {
    copy(
        headlineLarge = headlineLarge.copy(fontWeight = FontWeight.Bold),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        displaySmall = displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    )
}

@Composable
fun GarminTheme(
    dynamicColor: Boolean = true,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> GarminDark
        else -> GarminLight
    }
    MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
}
