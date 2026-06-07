package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.graphics.Color

private val DarkColorScheme =
  darkColorScheme(
    primary = IceBlueAccent,
    secondary = IndigoPrimary,
    tertiary = ActiveGradientEnd,
    background = ImmersiveBg,
    surface = ImmersiveSurface,
    onPrimary = Color(0xFF002D6E),
    onSecondary = Color.White,
    onBackground = TextPrimary,
    onSurface = TextPrimary
  )

private val LightColorScheme =
  darkColorScheme(
    primary = IceBlueAccent,
    secondary = IndigoPrimary,
    tertiary = ActiveGradientEnd,
    background = ImmersiveBg,
    surface = ImmersiveSurface,
    onPrimary = Color(0xFF002D6E),
    onSecondary = Color.White,
    onBackground = TextPrimary,
    onSurface = TextPrimary
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color is disabled by default to preserve Saturnium's premium cosmic theme
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
