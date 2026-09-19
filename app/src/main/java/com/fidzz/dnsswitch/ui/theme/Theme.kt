package com.fidzz.dnsswitch.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val DnsTypography = Typography(
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 19.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp)
)

@Composable
fun DnsSwitchTheme(
    accent: Color = BrandBlue,
    content: @Composable () -> Unit
) {
    val darkTheme = isSystemInDarkTheme()
    val animatedAccent by animateColorAsState(
        targetValue = accent,
        animationSpec = tween(durationMillis = 450),
        label = "accentColor"
    )

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = animatedAccent,
            onPrimary = Color.White,
            background = Color(0xFF060B16),
            surface = Color(0xFF0E1729),
            onBackground = Color(0xFFE6EEFA),
            onSurface = Color(0xFFE6EEFA),
            surfaceVariant = Color(0xFF16233C),
            outline = Color(0xFF1F2D47)
        )
    } else {
        lightColorScheme(
            primary = animatedAccent,
            onPrimary = Color.White,
            background = Cloud,
            surface = Color.White,
            onBackground = Ink,
            onSurface = Ink,
            surfaceVariant = Mist,
            outline = Line
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = DnsTypography,
        content = content
    )
}
