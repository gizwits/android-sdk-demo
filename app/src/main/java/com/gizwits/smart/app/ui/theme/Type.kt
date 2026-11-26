package com.gizwits.smart.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// guided.html hierarchy, retaining native font scaling and legible controls.
private fun style(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = FontFamily.Default, fontSize = size.sp, lineHeight = line.sp,
    fontWeight = weight, letterSpacing = 0.sp)

val Typography = Typography(
    headlineLarge = style(30, 38, FontWeight.SemiBold),
    headlineMedium = style(26, 34, FontWeight.SemiBold),
    headlineSmall = style(23, 30, FontWeight.SemiBold),
    titleLarge = style(22, 28, FontWeight.SemiBold),
    titleMedium = style(16, 23, FontWeight.SemiBold),
    titleSmall = style(14, 20, FontWeight.SemiBold),
    bodyLarge = style(14, 22),
    bodyMedium = style(13, 21),
    bodySmall = style(12, 18),
    labelLarge = style(13, 20, FontWeight.SemiBold),
    labelMedium = style(12, 17, FontWeight.Medium),
    labelSmall = style(11, 16)
)
