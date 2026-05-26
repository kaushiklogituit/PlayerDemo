package com.example.exoplayerdummy.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val VaultColorScheme = darkColorScheme(
    primary          = VaultRed,
    onPrimary        = VaultWhite,
    primaryContainer = VaultRedDim,
    onPrimaryContainer = VaultWhite,

    secondary        = VaultGold,
    onSecondary      = VaultBlack,
    secondaryContainer = Color(0xFF2C2200),
    onSecondaryContainer = VaultGold,

    tertiary         = VaultGreen,
    onTertiary       = VaultBlack,

    background       = VaultBlack,
    onBackground     = VaultTextPrimary,

    surface          = VaultSurface,
    onSurface        = VaultTextPrimary,
    surfaceVariant   = VaultCard,
    onSurfaceVariant = VaultTextSecond,

    outline          = VaultDivider,
    outlineVariant   = VaultElevated,

    error            = Color(0xFFCF6679),
    onError          = VaultBlack,
)

@Composable
fun AppTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = VaultColorScheme,
        typography = AppTypography,
        content = content
    )
}
