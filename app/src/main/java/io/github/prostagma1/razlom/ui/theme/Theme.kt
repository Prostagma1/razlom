package io.github.prostagma1.razlom.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val GameColorScheme = darkColorScheme(
    primary = Ember,
    onPrimary = Ink,
    secondary = Steel,
    onSecondary = Ink,
    tertiary = Moss,
    background = Ink,
    onBackground = Bone,
    surface = InkRaised,
    onSurface = Bone,
    surfaceVariant = InkLine,
    onSurfaceVariant = Steel,
    error = Blood,
    outline = EmberDim,
)

/** Игра всегда тёмная: динамические цвета системы здесь только мешают. */
@Composable
fun RazlomTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = GameColorScheme,
        typography = Typography,
        content = content,
    )
}
