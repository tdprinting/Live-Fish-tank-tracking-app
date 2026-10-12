package com.reeflive.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.reeflive.core.status.Status

private val AbyssColorScheme = darkColorScheme(
    primary = Abyss.lagoon,
    onPrimary = Ink.onPrimary,
    primaryContainer = Abyss.reef,
    onPrimaryContainer = Abyss.foam,
    secondary = Abyss.lagoonBright,
    onSecondary = Ink.onPrimary,
    tertiary = Abyss.coral,
    onTertiary = Ink.onCoral,
    background = Abyss.abyss1,
    onBackground = Abyss.foam,
    surface = Abyss.abyss1,
    onSurface = Abyss.foam,
    surfaceVariant = Abyss.abyss2,
    onSurfaceVariant = Abyss.mist,
    surfaceContainer = Abyss.abyss2,
    surfaceContainerHigh = Abyss.reef,
    surfaceContainerHighest = Abyss.reef,
    surfaceContainerLow = Abyss.abyss1,
    outline = Abyss.glassBorder,
    outlineVariant = Abyss.kelp,
    error = StatusColors.danger,
    onError = Ink.onPrimary,
    inverseSurface = Abyss.foam,
    inverseOnSurface = Abyss.abyss1,
)

private val LagoonColorScheme = lightColorScheme(
    primary = Lagoon.lagoon,
    onPrimary = Lagoon.bg0,
    primaryContainer = Lagoon.reef,
    onPrimaryContainer = Lagoon.foam,
    secondary = Lagoon.lagoonBright,
    onSecondary = Lagoon.bg0,
    tertiary = Lagoon.coral,
    onTertiary = Lagoon.bg0,
    background = Lagoon.bg1,
    onBackground = Lagoon.foam,
    surface = Lagoon.bg0,
    onSurface = Lagoon.foam,
    surfaceVariant = Lagoon.bg2,
    onSurfaceVariant = Lagoon.mist,
    surfaceContainer = Lagoon.bg1,
    surfaceContainerHigh = Lagoon.bg2,
    surfaceContainerHighest = Lagoon.reef,
    surfaceContainerLow = Lagoon.bg0,
    outline = Lagoon.glassBorder,
    outlineVariant = Lagoon.kelp,
    error = StatusColors.dangerTextLight,
)

/**
 * ReefLive theme: Abyss (dark, primary) or Lagoon (light). Provides [ReefTheme.colors],
 * Material 3 scheme/typography/shapes, numeric styles and the reduce-motion flag.
 */
@Composable
fun ReefLiveTheme(
    darkTheme: Boolean = true,
    reduceMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) AbyssColors else LagoonColors
    CompositionLocalProvider(
        LocalReefColors provides colors,
        LocalReefNumbers provides ReefNumbers,
        LocalReduceMotion provides reduceMotion,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) AbyssColorScheme else LagoonColorScheme,
            typography = ReefTypography,
            shapes = ReefMaterialShapes,
            content = content,
        )
    }
}

object ReefTheme {
    val colors: ReefColors
        @Composable @ReadOnlyComposable get() = LocalReefColors.current
    val numbers: ReefNumberStyles
        @Composable @ReadOnlyComposable get() = LocalReefNumbers.current
    val reduceMotion: Boolean
        @Composable @ReadOnlyComposable get() = LocalReduceMotion.current
}

fun ReefColors.status(s: Status): Color = when (s) {
    Status.GOOD -> good; Status.CAUTION -> caution; Status.DANGER -> danger; Status.UNKNOWN -> unknown
}

fun ReefColors.statusText(s: Status): Color = when (s) {
    Status.GOOD -> goodText; Status.CAUTION -> cautionText; Status.DANGER -> dangerText; Status.UNKNOWN -> unknownText
}

fun ReefColors.statusFill(s: Status): Color = status(s).copy(
    alpha = when (s) {
        Status.DANGER -> statusFillAlpha + 0.04f
        Status.UNKNOWN -> statusFillAlpha - 0.08f
        else -> statusFillAlpha
    },
)
