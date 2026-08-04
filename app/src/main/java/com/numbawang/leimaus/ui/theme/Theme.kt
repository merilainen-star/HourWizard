package com.numbawang.leimaus.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = PurplePrimary,
    onPrimary = PurpleOnPrimary,
    primaryContainer = PurpleContainer,
    onPrimaryContainer = PurpleOnContainer,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = OnSecondaryContainer,
    background = PolishBackground,
    surface = PolishSurface,
    surfaceVariant = PolishSurfaceVariant,
    onBackground = PolishOnSurface,
    onSurface = PolishOnSurface,
    onSurfaceVariant = PolishOnSurfaceVariant
)

private val DarkColorScheme = darkColorScheme(
    primary = PurpleContainer,
    onPrimary = PurpleOnContainer,
    primaryContainer = PurplePrimary,
    onPrimaryContainer = PurpleOnPrimary,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = OnSecondaryContainer,
    background = Color(0xFF1C1B1F),
    surface = Color(0xFF2B2930),
    surfaceVariant = Color(0xFF49454F),
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = Color(0xFFCAC4D0)
)

@Composable
fun MyApplicationTheme(
    appTheme: String = "system",
    darkTheme: Boolean = when (appTheme) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    },
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
  val isDark = when (appTheme) {
      "light" -> false
      "dark" -> true
      else -> darkTheme
  }

  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      isDark -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
