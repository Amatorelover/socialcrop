package com.lihoo.socialcrop.ui.components

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lihoo.socialcrop.data.FrameSpec
import com.lihoo.socialcrop.data.WatermarkSpec

/**
 * 边框效果预览：与导出共用 FrameRenderer，保证所见即所得。
 * v1.9.5 起统一走 [WatermarkPreview]，因此水印的开关 / 位置 / 外观改动同样实时反映，
 * 且「已开启但未填内容」时预览会显示示例水印，不会一片空白。
 */
@Composable
fun FramePreview(
    src: Bitmap,
    spec: FrameSpec,
    targetRatio: Float,
    watermark: WatermarkSpec = WatermarkSpec(),
    modifier: Modifier = Modifier
) {
    WatermarkPreview(
        src = src,
        watermark = watermark,
        modifier = modifier,
        frame = spec,
        targetRatio = targetRatio
    )
}
