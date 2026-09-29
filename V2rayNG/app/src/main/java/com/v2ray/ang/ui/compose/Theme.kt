package com.v2ray.ang.ui.compose

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private val LightColor = lightColorScheme(
    primary = Color(0xFF0284C7), // Stratos Sky
    onPrimary = Color(0xFFFFFFFF), // White
    primaryContainer = Color(0xFFE0F2FE), // Pale Sky
    onPrimaryContainer = Color(0xFF0C4A6E), // Deep Sky
    secondary = Color(0xFF0891B2), // Stratos Cyan
    onSecondary = Color(0xFFFFFFFF), // White
    secondaryContainer = Color(0xFFCFFAFE), // Pale Cyan
    onSecondaryContainer = Color(0xFF164E63), // Deep Cyan
    tertiary = Color(0xFF4F46E5), // Stratos Indigo
    onTertiary = Color(0xFFFFFFFF), // White
    tertiaryContainer = Color(0xFFE0E7FF), // Pale Indigo
    onTertiaryContainer = Color(0xFF312E81), // Deep Indigo
    error = Color(0xFFDC2626), // Red
    errorContainer = Color(0xFFFEE2E2), // Light Red
    onError = Color(0xFFFFFFFF), // White
    onErrorContainer = Color(0xFF7F1D1D), // Dark Red
    background = Color(0xFFF8FAFF), // Ice White
    onBackground = Color(0xFF0F172A), // Space Ink
    surface = Color(0xFFFFFFFF), // White
    onSurface = Color(0xFF0F172A), // Space Ink
    surfaceVariant = Color(0xFFE8EDF8), // Silver Blue
    onSurfaceVariant = Color(0xFF475569), // Slate
    outline = Color(0xFF94A3C8), // Blue Gray
    outlineVariant = Color(0xFFD6DEEF), // Light Blue Gray
    inverseSurface = Color(0xFF11173A), // Stratos Space
    inverseOnSurface = Color(0xFFF0F4FF), // Ice White
    inversePrimary = Color(0xFF38BDF8), // Stratos Sky
    scrim = Color(0xFF000000), // Black
    surfaceTint = Color(0xFF0284C7), // Stratos Sky
    surfaceContainerLowest = Color(0xFFFFFFFF), // White
    surfaceContainerLow = Color(0xFFF4F7FE), // Ice Blue
    surfaceContainer = Color(0xFFEDF2FC), // Pale Blue
    surfaceContainerHigh = Color(0xFFE4EBF9), // Soft Blue
    surfaceContainerHighest = Color(0xFFDAE3F4), // Faded Blue
)

private val DarkColor = darkColorScheme(
    primary = Color(0xFF38BDF8), // Stratos Sky
    onPrimary = Color(0xFF06202E), // Deep Space
    primaryContainer = Color(0xFF0B2B45), // Night Sky
    onPrimaryContainer = Color(0xFFBFE4FF), // Ice Blue
    secondary = Color(0xFF22D3EE), // Stratos Cyan
    onSecondary = Color(0xFF002831), // Deep Sea
    secondaryContainer = Color(0xFF104A57), // Dark Cyan
    onSecondaryContainer = Color(0xFFA8F0FB), // Light Cyan
    tertiary = Color(0xFF818CF8), // Soft Indigo
    onTertiary = Color(0xFF161843), // Indigo Night
    tertiaryContainer = Color(0xFF2E3178), // Stratos Indigo
    onTertiaryContainer = Color(0xFFD6D8FF), // Pale Indigo
    error = Color(0xFFF87171), // Soft Red
    errorContainer = Color(0xFF7F1D1D), // Dark Red
    onError = Color(0xFF450A0A), // Deep Red
    onErrorContainer = Color(0xFFFEE2E2), // Light Red
    background = Color(0xFF060818), // Stratos Space
    onBackground = Color(0xFFF0F4FF), // Ice White
    surface = Color(0xFF0B1026), // Space Card
    onSurface = Color(0xFFF0F4FF), // Ice White
    surfaceVariant = Color(0xFF1A224C), // Space Elevated
    onSurfaceVariant = Color(0xFF9AA7C7), // Mist Blue
    outline = Color(0xFF3A4778), // Orbit Blue
    outlineVariant = Color(0xFF232C57), // Dark Orbit
    inverseSurface = Color(0xFFF0F4FF), // Ice White
    inverseOnSurface = Color(0xFF11173A), // Stratos Space
    inversePrimary = Color(0xFF0284C7), // Deep Sky
    scrim = Color(0xFF000000), // Black
    surfaceTint = Color(0xFF38BDF8), // Stratos Sky
    surfaceContainerLowest = Color(0xFF040610), // Void
    surfaceContainerLow = Color(0xFF0A0E22), // Deep Space
    surfaceContainer = Color(0xFF0E1330), // Space
    surfaceContainerHigh = Color(0xFF131A3D), // High Space
    surfaceContainerHighest = Color(0xFF18204A), // Bright Space
)

// Semantic Colors
val colorPing = Color(0xFF34D399) // Stratos Success
val colorPingRed = Color(0xFFF87171) // Stratos Error
val colorConfigType = Color(0xFF818CF8) // Soft Indigo
val colorFabActive = Color(0xFF38BDF8) // Stratos Sky
val colorFabInactiveLight = Color(0xFF9C9C9C) // Gray
val colorFabInactiveDark = Color(0xFF3A4778) // Orbit Blue
val dividerColorLight = Color(0xFFE0E0E0) // Light Gray
val dividerColorDark = Color(0xFF232C57) // Dark Orbit

// Toast Colors 85%
val toastNormalBgLight = Color(0xD9353A3E) // Dark Gray
val toastNormalBgDark = Color(0xD94A4F54) // Darker Gray
val toastSuccessBg = Color(0xD9388E3C) // Green
val toastErrorBg = Color(0xD9D50000) // Red
val toastInfoBg = Color(0xD93F51B5) // Indigo Blue
val toastIconCircleBg = Color(0x33FFFFFF) // Semi-transparent White
val toastTextColor = Color.White // White

object ThemeManager {
    private val _themeMode = MutableStateFlow(
        MmkvManager.decodeSettingsString(AppConfig.PREF_UI_MODE_NIGHT, "0") ?: "0"
    )
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _dynamicColorEnabled = MutableStateFlow(
        MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_COLOR, true)
    )
    val dynamicColorEnabled: StateFlow<Boolean> = _dynamicColorEnabled.asStateFlow()

    fun setThemeMode(mode: String) {
        MmkvManager.encodeSettings(AppConfig.PREF_UI_MODE_NIGHT, mode)
        _themeMode.value = mode
    }

    fun setDynamicColorEnabled(enabled: Boolean) {
        MmkvManager.encodeSettings(AppConfig.PREF_DYNAMIC_COLOR, enabled)
        _dynamicColorEnabled.value = enabled
    }

    fun refresh() {
        _themeMode.value =
            MmkvManager.decodeSettingsString(AppConfig.PREF_UI_MODE_NIGHT, "0") ?: "0"
        _dynamicColorEnabled.value =
            MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_COLOR, true)
    }
}

@Composable
fun resolveDarkTheme(): Boolean {
    val mode by ThemeManager.themeMode.collectAsState()
    return when (mode) {
        "1" -> false
        "2" -> true
        else -> isSystemInDarkTheme()
    }
}

val LocalDarkTheme = compositionLocalOf { false }

@Composable
fun AppTheme(
    darkTheme: Boolean = resolveDarkTheme(),
    content: @Composable () -> Unit
) {
    val dynamicColor by ThemeManager.dynamicColorEnabled.collectAsState()
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColor
        else -> LightColor
    }
    val snackbarController = rememberAppSnackbarController()

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity ?: return@SideEffect
            val window = activity.window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalDarkTheme provides darkTheme,
        LocalAppSnackbar provides snackbarController
    ) {
        MaterialTheme(
            colorScheme = colorScheme
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AppSnackbarBridge(controller = snackbarController)
                content()
                AppSnackbarHost(hostState = snackbarController.hostState)
            }
        }
    }
}
