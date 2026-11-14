package com.speedbump.core.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val SpeedbumpDarkScheme = darkColorScheme(
    primary = SpeedbumpPalette.Amber,
    onPrimary = SpeedbumpPalette.Ink,
    primaryContainer = SpeedbumpPalette.AmberContainer,
    onPrimaryContainer = SpeedbumpPalette.Amber,
    secondary = SpeedbumpPalette.Sage,
    onSecondary = SpeedbumpPalette.Ink,
    secondaryContainer = SpeedbumpPalette.SageContainer,
    onSecondaryContainer = SpeedbumpPalette.Sage,
    tertiary = SpeedbumpPalette.Rust,
    onTertiary = SpeedbumpPalette.Ink,
    tertiaryContainer = SpeedbumpPalette.RustContainer,
    onTertiaryContainer = SpeedbumpPalette.Rust,
    error = SpeedbumpPalette.Rust,
    onError = SpeedbumpPalette.Ink,
    background = SpeedbumpPalette.Ink,
    onBackground = SpeedbumpPalette.Bone,
    surface = SpeedbumpPalette.Ink,
    onSurface = SpeedbumpPalette.Bone,
    surfaceVariant = SpeedbumpPalette.InkCard,
    onSurfaceVariant = SpeedbumpPalette.BoneDim,
    surfaceContainerLowest = SpeedbumpPalette.Ink,
    surfaceContainerLow = SpeedbumpPalette.InkElevated,
    surfaceContainer = SpeedbumpPalette.InkCard,
    surfaceContainerHigh = SpeedbumpPalette.InkCard,
    surfaceContainerHighest = SpeedbumpPalette.InkOutline,
    outline = SpeedbumpPalette.InkOutline,
    outlineVariant = SpeedbumpPalette.InkElevated,
)

private val SpeedbumpLightScheme = lightColorScheme(
    primary = SpeedbumpPalette.AmberDim,
    onPrimary = SpeedbumpPalette.PaperWhite,
    primaryContainer = SpeedbumpPalette.Amber,
    onPrimaryContainer = SpeedbumpPalette.PaperInk,
    secondary = SpeedbumpPalette.PaperSage,
    onSecondary = SpeedbumpPalette.PaperWhite,
    background = SpeedbumpPalette.PaperWhite,
    onBackground = SpeedbumpPalette.PaperInk,
    surface = SpeedbumpPalette.PaperWhite,
    onSurface = SpeedbumpPalette.PaperInk,
    surfaceVariant = SpeedbumpPalette.PaperCard,
    outline = SpeedbumpPalette.PaperOutline,
)

/**
 * @param dynamicColor Material You wallpaper colours. Off by default: the
 *   interception screen needs a predictable, low-arousal palette, and a user's
 *   wallpaper can easily supply a bright, exciting one.
 */
@Composable
fun SpeedbumpTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> SpeedbumpDarkScheme
        else -> SpeedbumpLightScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            (view.context as? Activity)?.window?.let { window ->
                WindowCompat.getInsetsController(window, view)
                    .isAppearanceLightStatusBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SpeedbumpTypography,
        shapes = SpeedbumpShapes,
        content = content,
    )
}
