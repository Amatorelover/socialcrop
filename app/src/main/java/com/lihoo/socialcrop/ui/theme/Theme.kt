package com.lihoo.socialcrop.ui.theme

import androidx.compose.runtime.Composable
import com.lihoo.socialcrop.ui.materialyou.M3AppTheme

/**
 * 全局主题入口：Material 3 Expressive + Material You 动态取色。
 * 浅色 / 深色跟随系统；Android 12+ 从壁纸取色，低版本回退 M3 官方 baseline。
 * 具体实现见 ui/materialyou/M3Theme.kt。
 */
@Composable
fun SocialCropTheme(content: @Composable () -> Unit) {
    M3AppTheme(content = content)
}
