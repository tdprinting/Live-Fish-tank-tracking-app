package com.reeflive.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val AbyssColorScheme = darkColorScheme(
    primary = Abyss.lagoon,
    onPrimary = Abyss.abyss0,
    primaryContainer = Abyss.reef,
    onPrimaryContainer = Abyss.foam,
    secondary = Abyss.lagoonBright,
    onSecondary = Abyss.abyss0,
    tertiary = Abyss.coral,
    onTertiary = Abyss.abyss0,
    background = Abyss.abyss1,
    onBackground = Abyss.foam,
    surface = Abyss.abyss1,
    onSurface = Abyss.foam,
    surfaceVariant = Abyss.abyss2,
    onSurfaceVariant = Abyss.mist,
    outline = Abyss.glassBorder,
    outlineVariant = Abyss.kelp,
    error = StatusColors.danger,
    onError = Abyss.abyss0,
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
    surface = Lagoon.bg1,
    onSurface = Lagoon.foam,
    surfaceVariant = Lagoon.bg2,
    onSurfaceVariant = Lagoon.mist,
    outline = Lagoon.glassBorder,
    outlineVariant = Lagoon.kelp,
)

/**
 * ReefLive theme. Abyss (dark) is primary; Lagoon (light) mirrors it.
 * Dark is the default regardless of the system setting; a Settings toggle arrives later.
 * M0 uses the default font family; Outfit/Inter are bundled in a later milestone.
 */
@Composable
fun ReefLiveTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) AbyssColorScheme else LagoonColorScheme,
        content = content,
    )
}
