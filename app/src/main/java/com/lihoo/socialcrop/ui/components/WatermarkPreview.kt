package com.lihoo.socialcrop.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import com.lihoo.socialcrop.R
import com.lihoo.socialcrop.data.FrameSpec
import com.lihoo.socialcrop.data.WatermarkSpec
import com.lihoo.socialcrop.domain.FrameRenderer
import com.lihoo.socialcrop.domain.ImageProcessor
import com.lihoo.socialcrop.domain.WatermarkRenderer
import com.lihoo.socialcrop.util.decodeLogo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.roundToInt

private const val PREVIEW_WIDTH = 720

/** 水印是否配置了可见内容；未配置时预览用示例文字占位，但导出仍严格不加任何东西 */
fun WatermarkSpec.hasPreviewContent(): Boolean =
    (showText && text.isNotBlank()) || (showLogo && logoPath.isNotBlank())

/** 仅预览用：开启却没内容时补一段示例文字，让位置 / 大小 / 透明度等调节依然可见 */
private fun WatermarkSpec.previewSpec(sample: String): WatermarkSpec =
    if (!enabled || hasPreviewContent()) this else copy(text = sample, showText = true)

/**
 * 水印实时预览：与三条导出路径共用同一套渲染器，改任何参数（位置、大小、透明度、
 * 旋转、边距、文字、Logo、开关）都会触发重渲染，所见即所得。
 *
 * @param frame   非 null 表示带边框（走 FrameRenderer）；为 null 表示纯裁剪 / 切格
 * @param slice   非 null (列, 行) 表示切格，预览第一格（每格都套水印，与导出一致）
 * @param cropCenterX/Y 取景框中心（归一化），纯裁剪预览与 CropView 导出用同一块画面
 */
@Composable
fun WatermarkPreview(
    src: Bitmap,
    watermark: WatermarkSpec,
    modifier: Modifier = Modifier,
    frame: FrameSpec? = null,
    targetRatio: Float = 1f,
    slice: Pair<Int, Int>? = null,
    cropCenterX: Float = 0.5f,
    cropCenterY: Float = 0.5f
) {
    val sample = stringResource(R.string.wm_preview_sample)
    var preview by remember { mutableStateOf<Bitmap?>(null) }

    // key 里带上水印对象本身：WatermarkSpec 是 data class，任何字段变化都会重渲染
    LaunchedEffect(
        src, watermark, frame, targetRatio,
        slice?.first, slice?.second, cropCenterX, cropCenterY, sample
    ) {
        val bmp = withContext(Dispatchers.Default) {
            if (!isActive) return@withContext null
            renderPreview(
                src, watermark.previewSpec(sample), frame, targetRatio,
                slice, cropCenterX, cropCenterY
            )
        }
        // 旧协程被新参数取消时不会走到这里，避免过期结果覆盖最新预览
        if (bmp != null) preview = bmp
    }

    // 不加深色幕布：调用方按目标比例给尺寸，预览本身就是成品画面，边缘不该出现黑边
    Box(modifier, contentAlignment = Alignment.Center) {
        preview?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = stringResource(R.string.cd_preview),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        } ?: CircularProgressIndicator()
    }
}

private fun renderPreview(
    src: Bitmap,
    wm: WatermarkSpec,
    frame: FrameSpec?,
    targetRatio: Float,
    slice: Pair<Int, Int>?,
    cropCenterX: Float,
    cropCenterY: Float
): Bitmap? = runCatching {
    val logo = if (wm.showLogo && wm.logoPath.isNotBlank()) decodeLogo(wm.logoPath) else null

    val out = when {
        // ① 带边框：FrameRenderer 末尾自己叠水印，这里不能重复画
        frame != null -> FrameRenderer.render(src, frame, targetRatio, PREVIEW_WIDTH, wm, logo)

        // ② 切格：与 sliceGrid 一致，取第一格原像素块后叠水印
        slice != null -> {
            val plan = ImageProcessor.planGrid(src.width, src.height, slice.first, slice.second)
            val tile = Bitmap.createBitmap(src, plan.cropLeft, plan.cropTop, plan.cellW, plan.cellH)
            val scale = min(1f, PREVIEW_WIDTH.toFloat() / tile.width.coerceAtLeast(1))
            val w = (tile.width * scale).roundToInt().coerceAtLeast(1)
            val h = (tile.height * scale).roundToInt().coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            c.drawBitmap(tile, null, RectF(0f, 0f, w.toFloat(), h.toFloat()), null)
            WatermarkRenderer.draw(c, wm, w, h, logo)
            tile.recycle()
            bmp
        }

        // ③ 纯裁剪：与 CropView 拖动后的取景框一致，同一块画面上叠水印
        else -> {
            val ratio = targetRatio.coerceIn(0.05f, 20f)
            val w = PREVIEW_WIDTH
            val h = (w / ratio).roundToInt().coerceAtLeast(1)
            val bmp = ImageProcessor.cropRegion(
                src, cropRect(src, ratio, cropCenterX, cropCenterY), w, h
            )
            WatermarkRenderer.draw(Canvas(bmp), wm, w, h, logo)
            bmp
        }
    }
    logo?.recycle()
    out
}.getOrNull()

/** 与 CropView 完全相同的取景矩形（源图坐标系），保证拖动后预览与导出是同一块画面 */
private fun cropRect(src: Bitmap, ratio: Float, centerX: Float, centerY: Float): RectF {
    val srcRatio = src.width.toFloat() / src.height.coerceAtLeast(1)
    val (cw, ch) = if (srcRatio > ratio) {
        src.height * ratio to src.height.toFloat()
    } else {
        src.width.toFloat() to (src.width / ratio)
    }
    val cx = centerX.coerceIn(0f, 1f)
    val cy = centerY.coerceIn(0f, 1f)
    val left = (cx * src.width - cw / 2f)
        .coerceIn(0f, (src.width - cw).coerceAtLeast(0f))
    val top = (cy * src.height - ch / 2f)
        .coerceIn(0f, (src.height - ch).coerceAtLeast(0f))
    return RectF(left, top, left + cw, top + ch)
}
