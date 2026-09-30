package com.vishnu.assistant.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * EnodaAI is always shown in its premium dark identity -
 * deep midnight blue with gold and teal accents.
 */
private val EnodaDarkScheme = darkColorScheme(
    primary = Gold,
    onPrimary = MidnightBase,
    primaryContainer = MidnightSurfaceHigh,
    onPrimaryContainer = GoldSoft,

    secondary = Teal,
    onSecondary = MidnightBase,
    secondaryContainer = MidnightSurfaceHigh,
    onSecondaryContainer = TealSoft,

    tertiary = ThinkingPurple,
    onTertiary = MidnightBase,

    background = MidnightBase,
    onBackground = OnDark,
    surface = MidnightSurface,
    onSurface = OnDark,
    surfaceVariant = MidnightSurfaceHigh,
    onSurfaceVariant = OnDarkMuted,

    outline = MidnightBorder,
    outlineVariant = MidnightBorder,

    error = DangerRed,
    onError = MidnightBase
)

@Composable
fun EnodaAITheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = EnodaDarkScheme,
        typography = Typography,
        content = content
    )
}
