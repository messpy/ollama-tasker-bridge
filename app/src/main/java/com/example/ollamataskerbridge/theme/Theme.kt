package com.example.ollamataskerbridge.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
  primary = Color(0xFFB8C9DC), onPrimary = Color(0xFF182027),
  secondary = Color(0xFFB7C0CA), onSecondary = Color(0xFF1A2026),
  tertiary = Color(0xFFD0B7C6), onTertiary = Color(0xFF291D25),
  background = Color(0xFF151719), onBackground = Color(0xFFE8EAED),
  surface = Color(0xFF1D2023), onSurface = Color(0xFFE8EAED),
  surfaceVariant = Color(0xFF2A2E33), onSurfaceVariant = Color(0xFFC2C7CD),
  outline = Color(0xFF747B84), outlineVariant = Color(0xFF41464C)
)

private val LightColorScheme =
  lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40,

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
  )

@Composable
fun MyApplicationTheme(
  // Keep the main app and the Tasker configuration screen visually consistent.
  darkTheme: Boolean = true,
  // Dynamic color is available on Android 12+
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
