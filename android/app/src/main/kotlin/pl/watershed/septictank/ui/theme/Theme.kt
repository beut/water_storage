package pl.watershed.septictank.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * App-wide Material 3 theme. A fixed brand palette (built around the launcher's water blue,
 * #0B6E99) is used instead of dynamic color, so the fill-level status colours below always keep
 * their meaning and sufficient contrast against the surfaces they sit on (WCAG AA).
 */

private val LightColors = lightColorScheme(
    primary = Color(0xFF00658E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC7E7FF),
    onPrimaryContainer = Color(0xFF001E2E),
    secondary = Color(0xFF4F616E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD2E5F5),
    onSecondaryContainer = Color(0xFF0B1D29),
    tertiary = Color(0xFF63597C),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE9DDFF),
    onTertiaryContainer = Color(0xFF1F1635),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF6FAFE),
    onBackground = Color(0xFF181C1F),
    surface = Color(0xFFF6FAFE),
    onSurface = Color(0xFF181C1F),
    surfaceVariant = Color(0xFFDDE3EA),
    onSurfaceVariant = Color(0xFF41484D),
    outline = Color(0xFF71787E),
    outlineVariant = Color(0xFFC1C7CE),
    inverseSurface = Color(0xFF2D3135),
    inverseOnSurface = Color(0xFFEDF1F5),
    inversePrimary = Color(0xFF84CFFF),
    surfaceDim = Color(0xFFD6DADE),
    surfaceBright = Color(0xFFF6FAFE),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF0F4F8),
    surfaceContainer = Color(0xFFEAEEF2),
    surfaceContainerHigh = Color(0xFFE4E9ED),
    surfaceContainerHighest = Color(0xFFDFE3E7),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF84CFFF),
    onPrimary = Color(0xFF00344C),
    primaryContainer = Color(0xFF004C6C),
    onPrimaryContainer = Color(0xFFC7E7FF),
    secondary = Color(0xFFB6C9D8),
    onSecondary = Color(0xFF21323E),
    secondaryContainer = Color(0xFF374955),
    onSecondaryContainer = Color(0xFFD2E5F5),
    tertiary = Color(0xFFCDC0E9),
    onTertiary = Color(0xFF342B4B),
    tertiaryContainer = Color(0xFF4B4263),
    onTertiaryContainer = Color(0xFFE9DDFF),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0F1417),
    onBackground = Color(0xFFDFE3E7),
    surface = Color(0xFF0F1417),
    onSurface = Color(0xFFDFE3E7),
    surfaceVariant = Color(0xFF41484D),
    onSurfaceVariant = Color(0xFFC1C7CE),
    outline = Color(0xFF8B9198),
    outlineVariant = Color(0xFF41484D),
    inverseSurface = Color(0xFFDFE3E7),
    inverseOnSurface = Color(0xFF2D3135),
    inversePrimary = Color(0xFF00658E),
    surfaceDim = Color(0xFF0F1417),
    surfaceBright = Color(0xFF353A3D),
    surfaceContainerLowest = Color(0xFF0A0F12),
    surfaceContainerLow = Color(0xFF181C1F),
    surfaceContainer = Color(0xFF1C2024),
    surfaceContainerHigh = Color(0xFF262A2E),
    surfaceContainerHighest = Color(0xFF313539),
)

/**
 * Semantic colours for the tank state (OK / approaching / exceeded). Material's colour roles have
 * no "success" or "warning" role, and reusing `tertiary` for warnings (as before) read as purple.
 */
@Immutable
data class StatusColors(
    val good: Color,
    val goodContainer: Color,
    val onGoodContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val critical: Color,
    val criticalContainer: Color,
    val onCriticalContainer: Color,
)

private val LightStatusColors = StatusColors(
    good = Color(0xFF1B6D3A),
    goodContainer = Color(0xFFD3F0DC),
    onGoodContainer = Color(0xFF07391B),
    warning = Color(0xFF8A5300),
    warningContainer = Color(0xFFFFDDB3),
    onWarningContainer = Color(0xFF2C1700),
    critical = LightColors.error,
    criticalContainer = LightColors.errorContainer,
    onCriticalContainer = LightColors.onErrorContainer,
)

private val DarkStatusColors = StatusColors(
    good = Color(0xFF8DD8A5),
    goodContainer = Color(0xFF1F4D2F),
    onGoodContainer = Color(0xFFD3F0DC),
    warning = Color(0xFFFFB955),
    warningContainer = Color(0xFF673D00),
    onWarningContainer = Color(0xFFFFDDB3),
    critical = DarkColors.error,
    criticalContainer = DarkColors.errorContainer,
    onCriticalContainer = DarkColors.onErrorContainer,
)

private val LocalStatusColors = staticCompositionLocalOf { LightStatusColors }

/** Access point for [StatusColors], mirroring `MaterialTheme.colorScheme`. */
object AppTheme {
    val statusColors: StatusColors
        @Composable
        @ReadOnlyComposable
        get() = LocalStatusColors.current
}

private val AppTypography: Typography = Typography().let { base ->
    base.copy(
        displayMedium = base.displayMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
        displaySmall = base.displaySmall.copy(fontWeight = FontWeight.SemiBold),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/** Follows the system light/dark setting. */
@Composable
fun SepticTankTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors: ColorScheme = if (darkTheme) DarkColors else LightColors
    CompositionLocalProvider(LocalStatusColors provides if (darkTheme) DarkStatusColors else LightStatusColors) {
        MaterialTheme(colorScheme = colors, typography = AppTypography, shapes = AppShapes, content = content)
    }
}
