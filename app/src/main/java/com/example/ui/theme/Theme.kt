package com.example.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val MedColorScheme = lightColorScheme(
    primary = Color(0xFF2563EB),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEFF6FF),
    onPrimaryContainer = Color(0xFF1E40AF),
    secondary = Color(0xFF7C3AED),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF3E8FF),
    onSecondaryContainer = Color(0xFF6B21A8),
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color.White,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF64748B),
    outline = Color(0xFFE2E8F0)
)

private val NeonColorScheme = darkColorScheme(
    primary = Color(0xFF00E5FF),
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF1E293B),
    onPrimaryContainer = Color(0xFF00E5FF),
    secondary = Color(0xFFD946EF),
    onSecondary = Color.White,
    background = Color(0xFF0F172A),
    onBackground = Color.White,
    surface = Color(0xFF1E293B),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF334155),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF334155)
)

private val StudyColorScheme = lightColorScheme(
    primary = Color(0xFF78350F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFEF3C7),
    onPrimaryContainer = Color(0xFF78350F),
    secondary = Color(0xFF92400E),
    onSecondary = Color.White,
    background = Color(0xFFFEF9C3),
    onBackground = Color(0xFF422006),
    surface = Color(0xFFFFFBEB),
    onSurface = Color(0xFF422006),
    surfaceVariant = Color(0xFFFEF3C7),
    onSurfaceVariant = Color(0xFF854D0E),
    outline = Color(0xFFFDE68A)
)

private val OceanColorScheme = darkColorScheme(
    primary = Color(0xFF38BDF8),
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF0C4A6E),
    onPrimaryContainer = Color(0xFFBAE6FD),
    secondary = Color(0xFF2DD4BF),
    onSecondary = Color.Black,
    background = Color(0xFF082F49),
    onBackground = Color(0xFFF0F9FF),
    surface = Color(0xFF0C4A6E),
    onSurface = Color(0xFFF0F9FF),
    surfaceVariant = Color(0xFF075985),
    onSurfaceVariant = Color(0xFF7DD3FC),
    outline = Color(0xFF075985)
)

private val MidnightColorScheme = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF262626),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFFA3A3A3),
    onSecondary = Color.Black,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color(0xFF171717),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF262626),
    onSurfaceVariant = Color(0xFFA3A3A3),
    outline = Color(0xFF404040)
)

@Composable
fun medInputTextStyle() = TextStyle(
    color = MaterialTheme.colorScheme.onSurface,
    fontSize = 14.5.sp,
    fontWeight = FontWeight.SemiBold
)

@Composable
fun medTextFieldColors(): TextFieldColors {
    val currentScheme = MaterialTheme.colorScheme
    return OutlinedTextFieldDefaults.colors(
        focusedTextColor = currentScheme.onSurface,
        unfocusedTextColor = currentScheme.onSurface,
        focusedContainerColor = currentScheme.surface,
        unfocusedContainerColor = currentScheme.surface,
        disabledContainerColor = currentScheme.surfaceVariant,
        focusedBorderColor = currentScheme.primary,
        unfocusedBorderColor = currentScheme.outline,
        focusedLabelColor = currentScheme.primary,
        unfocusedLabelColor = currentScheme.onSurfaceVariant,
        focusedPlaceholderColor = currentScheme.onSurfaceVariant.copy(alpha = 0.6f),
        unfocusedPlaceholderColor = currentScheme.onSurfaceVariant.copy(alpha = 0.6f),
        cursorColor = currentScheme.primary,
        focusedLeadingIconColor = currentScheme.primary,
        unfocusedLeadingIconColor = currentScheme.onSurfaceVariant,
        focusedTrailingIconColor = currentScheme.primary,
        unfocusedTrailingIconColor = currentScheme.onSurfaceVariant
    )
}

@Composable
fun MyApplicationTheme(
    themeIndex: Int = 0,
    content: @Composable () -> Unit,
) {
    val colorScheme = when (themeIndex) {
        1 -> NeonColorScheme
        2 -> StudyColorScheme
        3 -> OceanColorScheme
        4 -> MidnightColorScheme
        else -> MedColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

