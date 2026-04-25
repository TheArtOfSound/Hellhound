package com.theartofsound.hellhound.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = HoundRed,
    onPrimary = HoundCharcoal,
    secondary = HoundRedDeep,
    background = HoundCharcoal,
    onBackground = HoundOnSurface,
    surface = HoundSurface,
    onSurface = HoundOnSurface
)

private val LightColors = lightColorScheme(
    primary = HoundRedDeep,
    secondary = HoundRed
)

@Composable
fun HellhoundTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = HellhoundTypography,
        content = content
    )
}
