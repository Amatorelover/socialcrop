package com.lihoo.socialcrop.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.view.Choreographer
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import com.lihoo.socialcrop.R
import com.lihoo.socialcrop.data.FrameSpec
import com.lihoo.socialcrop.data.WatermarkSpec
import com.lihoo.socialcrop.domain.FrameRenderer
import com.lihoo.socialcrop.domain.ImageProcessor
import com.lihoo.socialcrop.domain.WatermarkRenderer
import com.lihoo.socialcrop.util.ExifInfo
import com.lihoo.socialcrop.util.decodeLogo
import com.lihoo.socialcrop.util.recycleDerived
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.roundToInt

private const val PREVIEW_WIDTH = 720
/** 放大预览时按缩放级别重新渲染，但限制上限，避免 5× 时一次分配过大位图 */
private const val MAX_PREVIEW_WIDTH = 2880

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
    cropCenterY: Float = 0.5f,
    exif: ExifInfo? = null
) {
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var failed by remember { mutableStateOf(false) }

    // 双指捏合缩放 / 拖动平移：只作用于显示的位图，不参与渲染，也不影响导出。
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    // 换图时把缩放复位，避免沿用上一张图的放大倍数
    LaunchedEffect(src) {
        zoom = 1f
        pan = Offset.Zero
    }

    // 放大后只拉伸 720px 预览会糊；按缩放档位重渲染更高分辨率的预览，
    // 档位量化避免双指过程中每一帧都触发一次重渲染。
    val renderZoom = when {
        zoom <= 1.01f -> 1
        zoom <= 2f -> 2
        zoom <= 3f -> 3
        else -> 5
    }
    val renderWidth = (PREVIEW_WIDTH * renderZoom)
        .coerceAtMost(src.width)
        .coerceAtMost(MAX_PREVIEW_WIDTH)

    // key 里带上水印对象本身：WatermarkSpec 是 data class，任何字段变化都会重渲染。
    // 注意：不能在这里把 preview 置空。滑块连续拖动时每次都会重启渲染，
    // 置空会让界面立刻切成转圈，新图渲染完再切回来，表现为"滑一下闪一下"。
    // 改成旧图一直显示到新图就绪，中间最多是画面略有延迟，不再闪屏。
    LaunchedEffect(
        src, watermark, frame, targetRatio,
        slice?.first, slice?.second, cropCenterX, cropCenterY, exif, renderWidth
    ) {
        val bmp = withContext(Dispatchers.Default) {
            if (!isActive) return@withContext null
            renderPreview(
                src, watermark, frame, targetRatio,
                slice, cropCenterX, cropCenterY, exif, renderWidth
            ) ?: fallbackPreview(src, renderWidth)
        }
        // 旧协程被新参数取消时不会走到这里，避免过期结果覆盖最新预览
        if (!isActive) return@LaunchedEffect
        val previous = preview
        preview = bmp
        failed = bmp == null
        // 旧位图已经不在新 composition 里，等两帧确保 Image 不再引用后释放，
        // 避免连续拖动时短时间内堆积几十张 720px 位图。
        // 关键：src 绝不能被回收——某些路径下派生位图会与 src 引用同一对象。
        if (previous != null && previous !== src && previous !== bmp) recycleAfterDraw(previous)
    }

    // 离开页面时释放最后一张预览，避免 ViewModel 存活期间一直占用 native 内存
    DisposableEffect(Unit) {
        onDispose { preview?.let { if (it !== src) recycleAfterDraw(it) } }
    }

    // 不加深色幕布：调用方按目标比例给尺寸，预览本身就是成品画面，边缘不该出现黑边。
    // clipToBounds 防止放大后画面溢出卡片；缩放/平移只改显示矩阵，不重渲染、不写入水印配置。
    Box(
        modifier.clipToBounds().pointerInput(Unit) {
            detectTransformGestures(
                panZoomLock = true,   // 缩放时锁定平移，避免双指操作时画面乱跑
                onGesture = { _, panChange, zoomChange, _ ->
                    val next = (zoom * zoomChange).coerceIn(1f, 5f)
                    // 缩到 1x 时把偏移归零，避免缩回后画面停在边上
                    if (next <= 1f) {
                        zoom = 1f
                        pan = Offset.Zero
                    } else {
                        zoom = next
                        pan = pan + panChange
                    }
                }
            )
        },
        contentAlignment = Alignment.Center
    ) {
        val bmp = preview
        when {
            bmp != null -> Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = stringResource(R.string.cd_preview),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = zoom
                        scaleY = zoom
                        translationX = pan.x
                        translationY = pan.y
                    }
            )

            failed -> Text(
                stringResource(R.string.preview_unavailable),
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )

            else -> CircularProgressIndicator()
        }
    }
}

/**
 * 延后两帧再回收位图：Compose 状态写入后重组/绘制发生在后续帧，
 * 立即 recycle 可能让仍在绘制的 Image 拿到已回收的 Bitmap。
 */
private fun recycleAfterDraw(bitmap: Bitmap) {
    val choreographer = Choreographer.getInstance()
    choreographer.postFrameCallback {
        choreographer.postFrameCallback {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }
}

private fun renderPreview(
    src: Bitmap,
    wm: WatermarkSpec,
    frame: FrameSpec?,
    targetRatio: Float,
    slice: Pair<Int, Int>?,
    cropCenterX: Float,
    cropCenterY: Float,
    exif: ExifInfo? = null,
    previewWidth: Int = PREVIEW_WIDTH
): Bitmap? = runCatching {
    val logo = if (wm.logoPath.isNotBlank()) {
        decodeLogo(wm.logoPath, tint = wm.logoTint)
    } else null

    val out = when {
        // ① 带边框：FrameRenderer 末尾自己叠水印，这里不能重复画
        frame != null -> FrameRenderer.render(src, frame, targetRatio, previewWidth, wm, logo, exif)

        // ② 切格：与 sliceGrid 一致，取第一格原像素块后叠水印
        slice != null -> {
            val plan = ImageProcessor.planGrid(src.width, src.height, slice.first, slice.second)
            val tile = Bitmap.createBitmap(src, plan.cropLeft, plan.cropTop, plan.cellW, plan.cellH)
            val scale = min(1f, previewWidth.toFloat() / tile.width.coerceAtLeast(1))
            val w = (tile.width * scale).roundToInt().coerceAtLeast(1)
            val h = (tile.height * scale).roundToInt().coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            c.drawBitmap(tile, null, RectF(0f, 0f, w.toFloat(), h.toFloat()), null)
            WatermarkRenderer.drawOnPlainImage(c, wm, w, h, logo, exif)
            recycleDerived(tile, src)
            bmp
        }

        // ③ 纯裁剪：与 CropView 拖动后的取景框一致，同一块画面上叠水印
        else -> {
            val ratio = targetRatio.coerceIn(0.05f, 20f)
            val w = previewWidth
            val h = (w / ratio).roundToInt().coerceAtLeast(1)
            val bmp = ImageProcessor.cropRegion(
                src, cropRect(src, ratio, cropCenterX, cropCenterY), w, h
            )
            // cropRegion 可能按 4096 上限收敛尺寸，必须按实际位图尺寸摆水印，
            // 否则极端比例下水印落在画布外，看起来像「没有水印」。
            WatermarkRenderer.drawOnPlainImage(
                Canvas(bmp), wm, bmp.width, bmp.height, logo, exif
            )
            bmp
        }
    }
    logo?.recycle()
    out
}.getOrNull()

/**
 * 渲染链路任何一步失败时的兜底：至少把原图缩略显示出来，绝不让页面永久转圈。
 * 上限 4096 防止极端长图在兜底路径上再触发 OOM。
 */
private fun fallbackPreview(src: Bitmap, previewWidth: Int = PREVIEW_WIDTH): Bitmap? = runCatching {
    val scale = minOf(
        1f,
        previewWidth.toFloat() / src.width.coerceAtLeast(1),
        4096f / src.height.coerceAtLeast(1)
    )
    val w = (src.width * scale).roundToInt().coerceAtLeast(1)
    val h = (src.height * scale).roundToInt().coerceAtLeast(1)
    // 必须新建独立位图：createScaledBitmap 在尺寸一致时会直接返回 src，
    // 一旦它被当作 preview 回收，就会连带销毁 ViewModel 的源图。
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = Canvas(out)
    c.drawBitmap(src, null, RectF(0f, 0f, w.toFloat(), h.toFloat()), null)
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
