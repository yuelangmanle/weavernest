package com.zhique.studio.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Color(0xFF8A5A00),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE5B0),
    onPrimaryContainer = Color(0xFF2B1B00),
    secondary = Color(0xFF0A7C68),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC4F0E5),
    onSecondaryContainer = Color(0xFF00382E),
    tertiary = Color(0xFF2F6BBA),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD9E6FF),
    onTertiaryContainer = Color(0xFF001B3E),
    error = Color(0xFFB42318),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD5),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF7F8FA),
    onBackground = Color(0xFF17191D),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF17191D),
    surfaceVariant = Color(0xFFF0F2F5),
    onSurfaceVariant = Color(0xFF5F6670),
    outline = Color(0xFF737982)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFE8B34E),
    onPrimary = Color(0xFF3B2700),
    primaryContainer = Color(0xFF5A3D00),
    onPrimaryContainer = Color(0xFFFFE5B0),
    secondary = Color(0xFF49BFA8),
    onSecondary = Color(0xFF00382E),
    secondaryContainer = Color(0xFF005143),
    onSecondaryContainer = Color(0xFFC4F0E5),
    tertiary = Color(0xFF72A5E8),
    onTertiary = Color(0xFF003064),
    tertiaryContainer = Color(0xFF174C88),
    onTertiaryContainer = Color(0xFFD9E6FF),
    error = Color(0xFFFFB4A9),
    onError = Color(0xFF680003),
    errorContainer = Color(0xFF8D1B14),
    onErrorContainer = Color(0xFFFFDAD5),
    background = Color(0xFF151617),
    onBackground = Color(0xFFF3F4F4),
    surface = Color(0xFF1E2022),
    onSurface = Color(0xFFF3F4F4),
    surfaceVariant = Color(0xFF292C2F),
    onSurfaceVariant = Color(0xFFA8ADB4),
    outline = Color(0xFF8C929A)
)

@Composable
fun ZhiqueTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        shapes = MaterialTheme.shapes.copy(
            extraSmall = RoundedCornerShape(4.dp),
            small = RoundedCornerShape(6.dp),
            medium = RoundedCornerShape(8.dp),
            large = RoundedCornerShape(10.dp),
            extraLarge = RoundedCornerShape(12.dp)
        ),
        content = content
    )
}
