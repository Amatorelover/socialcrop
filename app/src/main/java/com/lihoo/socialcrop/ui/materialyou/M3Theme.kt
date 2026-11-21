package com.lihoo.socialcrop.ui.materialyou

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Material 3 Expressive（Google 2025 设计语言）+ Material You 动态取色。
 *
 * Android 12+ 直接用系统壁纸生成的 dynamicColorScheme：换壁纸，App 整套配色跟着变；
 * Android 11 及以下回退到 M3 官方 baseline（lightColorScheme() / darkColorScheme() 的默认紫色系）。
 * 两者都跟随系统深浅色，无需任何权限、不联网、零第三方依赖。
 *
 * blue / red / groupedBackground / card 等字段是 v2.0 iOS 皮肤遗留命名，
 * 页面仍直接引用，因此保留字段名，值已全部映射到 M3 语义色。
 */
data class M3Palette(
    val blue: Color,
    val red: Color,
    val groupedBackground: Color,
    val card: Color,
    val separator: Color,
    val label: Color,
    val secondaryLabel: Color,
    val fill: Color,
    val bar: Color,

    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val surfaceContainer: Color,
    val surfaceContainerHigh: Color,
    val surfaceContainerHighest: Color,
    val outline: Color,
    val outlineVariant: Color,
    val onSurfaceVariant: Color
)

/** M3 没有「绿 / 蓝」这类固定色 role，正向强调统一映射到 tertiary / primary */
private fun paletteOf(s: ColorScheme): M3Palette = M3Palette(
    blue = s.primary,
    red = s.error,
    groupedBackground = s.background,
    card = s.surfaceContainerLow,
    separator = s.outlineVariant,
    label = s.onSurface,
    secondaryLabel = s.onSurfaceVariant,
    fill = s.surfaceContainerHighest,
    bar = s.surface,
    primary = s.primary,
    onPrimary = s.onPrimary,
    primaryContainer = s.primaryContainer,
    onPrimaryContainer = s.onPrimaryContainer,
    secondaryContainer = s.secondaryContainer,
    onSecondaryContainer = s.onSecondaryContainer,
    surfaceContainer = s.surfaceContainer,
    surfaceContainerHigh = s.surfaceContainerHigh,
    surfaceContainerHighest = s.surfaceContainerHighest,
    outline = s.outline,
    outlineVariant = s.outlineVariant,
    onSurfaceVariant = s.onSurfaceVariant
)

private val M3LightBaseline = lightColorScheme()
private val M3DarkBaseline = darkColorScheme()

val LocalM3Palette = staticCompositionLocalOf { paletteOf(M3LightBaseline) }

object M3 {
    val palette: M3Palette
        @Composable get() = LocalM3Palette.current
}

/** M3 Expressive 形状：28dp 大圆角容器 / 56dp 触控行 / 全圆角 pill 按钮 */
object M3Dims {
    val ScreenInset = 16.dp
    val Corner = 28.dp
    val SmallCorner = 16.dp
    val Pill = 50
    val RowMinHeight = 56.dp
    val RowInset = 20.dp
    val SectionGap = 8.dp
}

/** M3 Expressive 字阶：更大的 headline、更明确的 500 字重、略紧的标题字距 */
private val M3ExpressiveTypography = Typography(
    displayLarge = TextStyle(fontSize = 57.sp, fontWeight = FontWeight.W400, lineHeight = 64.sp, letterSpacing = (-0.25).sp),
    displayMedium = TextStyle(fontSize = 45.sp, fontWeight = FontWeight.W400, lineHeight = 52.sp),
    displaySmall = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.W400, lineHeight = 44.sp),
    headlineLarge = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.W500, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.W500, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.W500, lineHeight = 32.sp),
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.W500, lineHeight = 28.sp),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.W500, lineHeight = 24.sp, letterSpacing = 0.15.sp),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.W500, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.W400, lineHeight = 24.sp, letterSpacing = 0.5.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.W400, lineHeight = 20.sp, letterSpacing = 0.25.sp),
    bodySmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.W400, lineHeight = 16.sp, letterSpacing = 0.4.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.W500, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.W500, lineHeight = 16.sp, letterSpacing = 0.5.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.W500, lineHeight = 16.sp, letterSpacing = 0.5.sp)
)

private val M3ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp)
)

@Composable
fun M3AppTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val dynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val scheme = when {
        dynamicColor && dark -> dynamicDarkColorScheme(context)
        dynamicColor -> dynamicLightColorScheme(context)
        dark -> M3DarkBaseline
        else -> M3LightBaseline
    }
    CompositionLocalProvider(LocalM3Palette provides paletteOf(scheme)) {
        MaterialTheme(
            colorScheme = scheme,
            typography = M3ExpressiveTypography,
            shapes = M3ExpressiveShapes,
            content = content
        )
    }
}
