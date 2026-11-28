package com.lihoo.socialcrop.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.view.Choreographer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.lihoo.socialcrop.data.WatermarkSpec
import com.lihoo.socialcrop.domain.ImageProcessor
import com.lihoo.socialcrop.domain.WatermarkRenderer
import com.lihoo.socialcrop.util.ExifInfo
import com.lihoo.socialcrop.util.decodeLogo
import kotlin.math.min
import kotlin.math.roundToInt

/** 降采样成极小图；绘制时双线性放大即得到柔和模糊，仅用于弱化「被裁掉区域」。 */
private fun makeBlurThumbnail(src: Bitmap): Bitmap {
    val w = 32
    val h = (src.height.toLong() * w / src.width.coerceAtLeast(1)).toInt().coerceAtLeast(1)
    return try {
        Bitmap.createScaledBitmap(src, w, h, true)
    } catch (t: Throwable) {
        // HARDWARE 等特殊配置兜底：先复制成软件位图；仍失败则退化为纯色底，不让预览崩溃
        try {
            val soft = src.copy(Bitmap.Config.ARGB_8888, false)
            Bitmap.createScaledBitmap(soft, w, h, true).also { soft.recycle() }
        } catch (t2: Throwable) {
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        }
    }
}

/**
 * 按比例裁剪预览：亮框内是保留区域（清晰原图），框外是被裁掉区域（模糊 + 压暗）。
 * 亮框可拖动，位置由外部以归一化中心点持有；生成输出复用同一个矩形。
 *
 * @param centerX / centerY 裁剪框中心在源图上的归一化位置（0.5 = 居中）
 * @param onCenterChange     拖动时回调，由 ViewModel 保存
 */
@Composable
fun CropView(
    bitmap: Bitmap,
    targetRatio: Float,
    modifier: Modifier = Modifier,
    centerX: Float = 0.5f,
    centerY: Float = 0.5f,
    onCenterChange: (Float, Float) -> Unit = { _, _ -> },
    cropTrigger: Int,
    onCropped: (Bitmap) -> Unit,
    /** 关闭边框时的全局水印：画在取景框内的图片底部，和导出结果一致 */
    watermark: WatermarkSpec? = null,
    exif: ExifInfo? = null
) {
    // 与 FrameRenderer 保持同一区间，极端比例下 1080/safeTarget 才不会算出 10 万像素的高度
    val safeTarget = targetRatio.coerceIn(0.05f, 20f)
    val srcRatio = bitmap.width.toFloat() / bitmap.height.toFloat().coerceAtLeast(1f)
    val (cropW, cropH) = if (srcRatio > safeTarget) {
        bitmap.height * safeTarget to bitmap.height.toFloat()
    } else {
        bitmap.width.toFloat() to (bitmap.width / safeTarget)
    }
    val cx = centerX.coerceIn(0f, 1f)
    val cy = centerY.coerceIn(0f, 1f)
    val cropLeft = (cx * bitmap.width - cropW / 2f)
        .coerceIn(0f, (bitmap.width - cropW).coerceAtLeast(0f))
    val cropTop = (cy * bitmap.height - cropH / 2f)
        .coerceIn(0f, (bitmap.height - cropH).coerceAtLeast(0f))

    val clearImage = remember(bitmap) { bitmap.asImageBitmap() }
    val blurImage = remember(bitmap) { makeBlurThumbnail(bitmap).asImageBitmap() }

    // 取景框内的成品预览要带水印：用与导出相同的 WatermarkRenderer 画一张透明小图，
    // 再按取景框缩放绘制，保证「关闭边框也能在编辑页看到底部水印」。
    val wmOverlay = remember(bitmap, watermark, exif, cropW, cropH) {
        val wm = watermark
        if (wm == null || !wm.enabled) null else runCatching {
            // 极端比例（如 1:20）时按 720 宽会算出上万像素高，一次分配几十 MB 直接 OOM；
            // 最长边钳到 2048，覆盖层只是缩放绘制，分辨率足够且不会拖垮预览。
            val rawW = 720f
            val rawH = rawW * cropH / cropW.coerceAtLeast(1f)
            val cap = if (maxOf(rawW, rawH) > 2048f) 2048f / maxOf(rawW, rawH) else 1f
            val ow = (rawW * cap).roundToInt().coerceAtLeast(1)
            val oh = (rawH * cap).roundToInt().coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(ow, oh, Bitmap.Config.ARGB_8888)
            val logo = if (wm.logoPath.isNotBlank()) decodeLogo(wm.logoPath, tint = wm.logoTint) else null
            WatermarkRenderer.drawOnPlainImage(Canvas(bmp), wm, ow, oh, logo, exif)
            logo?.recycle()
            bmp.asImageBitmap()
        }.getOrNull()
    }
    // remember 的旧 ImageBitmap 被替换时不会自动释放底层位图；延后两帧回收，避免仍在绘制时 recycle
    DisposableEffect(wmOverlay) {
        onDispose {
            val bmp = wmOverlay?.asAndroidBitmap()
            if (bmp != null && !bmp.isRecycled) {
                val ch = Choreographer.getInstance()
                ch.postFrameCallback { ch.postFrameCallback { if (!bmp.isRecycled) bmp.recycle() } }
            }
        }
    }

    // pointerInput 的 key 不含 center，需用 updated state 读取最新值，否则拖动只会基于首次组合的位置累加
    val latestCenter by rememberUpdatedState(cx to cy)
    val latestOnChange by rememberUpdatedState(onCenterChange)

    // 不铺黑幕：调用方按原图比例给尺寸，画面铺满预览区，不留黑色letterbox
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(
            Modifier.fillMaxSize().pointerInput(bitmap, targetRatio) {
                val scale = minOf(
                    size.width.toFloat() / bitmap.width.coerceAtLeast(1),
                    size.height.toFloat() / bitmap.height.coerceAtLeast(1)
                ).coerceAtLeast(0.0001f)
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    val nx = latestCenter.first + dragAmount.x / scale / bitmap.width.coerceAtLeast(1)
                    val ny = latestCenter.second + dragAmount.y / scale / bitmap.height.coerceAtLeast(1)
                    latestOnChange(nx.coerceIn(0f, 1f), ny.coerceIn(0f, 1f))
                }
            }
        ) {
            // 与 ContentScale.Fit 一致的变换，保证预览和裁切矩形对齐
            val scale = minOf(
                size.width / bitmap.width.coerceAtLeast(1),
                size.height / bitmap.height.coerceAtLeast(1)
            )
            val dispW = bitmap.width * scale
            val dispH = bitmap.height * scale
            val offX = (size.width - dispW) / 2f
            val offY = (size.height - dispH) / 2f
            val dstOffset = IntOffset(offX.toInt(), offY.toInt())
            val dstSize = IntSize(
                dispW.toInt().coerceAtLeast(1),
                dispH.toInt().coerceAtLeast(1)
            )

            // 保留区域在画布上的矩形
            val left = offX + cropLeft * scale
            val top = offY + cropTop * scale
            val right = left + cropW * scale
            val bottom = top + cropH * scale

            // ① 整幅先铺模糊底图（对应「不要的部分」）
            drawImage(
                blurImage,
                dstOffset = dstOffset,
                dstSize = dstSize,
                filterQuality = FilterQuality.High
            )
            // ② 框外再压暗一层，进一步弱化
            val shade = Color.Black.copy(alpha = 0.42f)
            drawRect(shade, Offset.Zero, Size(size.width, top.coerceAtLeast(0f)))
            drawRect(shade, Offset(0f, bottom), Size(size.width, (size.height - bottom).coerceAtLeast(0f)))
            drawRect(shade, Offset.Zero, Size(left.coerceAtLeast(0f), size.height))
            drawRect(shade, Offset(right, 0f), Size((size.width - right).coerceAtLeast(0f), size.height))
            val frameW = right - left
            val frameH = bottom - top
            // ③ 亮框内还原清晰原图，并叠上与导出一致的底部水印
            clipRect(left, top, right, bottom) {
                drawImage(
                    clearImage,
                    dstOffset = dstOffset,
                    dstSize = dstSize,
                    filterQuality = FilterQuality.High
                )
                wmOverlay?.let { overlay ->
                    drawImage(
                        overlay,
                        dstOffset = IntOffset(left.toInt(), top.toInt()),
                        dstSize = IntSize(
                            frameW.toInt().coerceAtLeast(1),
                            frameH.toInt().coerceAtLeast(1)
                        ),
                        filterQuality = FilterQuality.High
                    )
                }
            }
            // ④ 取景框描边（Stroke，不能实心填充）
            drawRect(
                color = Color.White.copy(alpha = 0.9f),
                topLeft = Offset(left, top),
                size = Size(frameW, frameH),
                style = Stroke(width = 1.5f.dp.toPx())
            )
            // ⑤ 四角 L 形标记，提示取景框可拖动
            val mark = Color.White.copy(alpha = 0.95f)
            val arm = min(frameW, frameH) * 0.12f
            val lw = 2f.dp.toPx()
            drawLine(mark, Offset(left, top + arm), Offset(left, top), strokeWidth = lw)
            drawLine(mark, Offset(left, top), Offset(left + arm, top), strokeWidth = lw)
            drawLine(mark, Offset(right - arm, top), Offset(right, top), strokeWidth = lw)
            drawLine(mark, Offset(right, top), Offset(right, top + arm), strokeWidth = lw)
            drawLine(mark, Offset(left, bottom - arm), Offset(left, bottom), strokeWidth = lw)
            drawLine(mark, Offset(left, bottom), Offset(left + arm, bottom), strokeWidth = lw)
            drawLine(mark, Offset(right - arm, bottom), Offset(right, bottom), strokeWidth = lw)
            drawLine(mark, Offset(right, bottom), Offset(right, bottom - arm), strokeWidth = lw)
        }
    }

    var lastHandled by remember { mutableStateOf(cropTrigger) }
    LaunchedEffect(cropTrigger) {
        if (cropTrigger <= lastHandled) return@LaunchedEffect
        lastHandled = cropTrigger
        val rect = RectF(cropLeft, cropTop, cropLeft + cropW, cropTop + cropH)
        onCropped(ImageProcessor.cropRegion(bitmap, rect, 1080, kotlin.math.round(1080 / safeTarget).toInt().coerceAtLeast(1)))
    }
}

/** 长图切格预览：源图 + 网格叠加。 */
@Composable
fun SlicePreview(
    src: Bitmap,
    cols: Int,
    rows: Int,
    modifier: Modifier = Modifier
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        BoxWithConstraintsFixed(
            src = src,
            cols = cols,
            rows = rows,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun BoxWithConstraintsFixed(
    src: Bitmap,
    cols: Int,
    rows: Int,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    BoxWithConstraints(modifier, propagateMinConstraints = true) {
        val maxW = constraints.maxWidth.toFloat()
        val maxH = constraints.maxHeight.toFloat()
        val dispW = maxW.coerceAtMost(maxH * (src.width.toFloat() / src.height))
        val dispH = dispW * src.height / src.width

        Box(
            Modifier
                .width(with(density) { dispW.toDp() })
                .height(with(density) { dispH.toDp() })
                .clip(RectangleShape)
        ) {
            Image(
                bitmap = src.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
            Canvas(Modifier.fillMaxSize()) {
                val plan = ImageProcessor.planGrid(src.width, src.height, cols, rows)
                val sx = size.width / src.width.toFloat()
                val sy = size.height / src.height.toFloat()
                val left = plan.cropLeft * sx
                val top = plan.cropTop * sy
                val right = left + plan.gridW * sx
                val bottom = top + plan.gridH * sy

                // 被裁掉的边缘压暗，让「不足一格的轻微裁切」在预览里可见
                val shade = Color.Black.copy(alpha = 0.6f)
                drawRect(shade, topLeft = Offset(0f, 0f), size = Size(size.width, top))
                drawRect(shade, topLeft = Offset(0f, bottom), size = Size(size.width, size.height - bottom))
                drawRect(shade, topLeft = Offset(0f, top), size = Size(left, bottom - top))
                drawRect(shade, topLeft = Offset(right, top), size = Size(size.width - right, bottom - top))

                for (i in 1 until plan.cols) {
                    val x = left + i * plan.cellW * sx
                    drawLine(
                        color = Color.White,
                        start = Offset(x, top),
                        end = Offset(x, bottom),
                        strokeWidth = 2f,
                        alpha = 0.9f
                    )
                }
                for (j in 1 until plan.rows) {
                    val y = top + j * plan.cellH * sy
                    drawLine(
                        color = Color.White,
                        start = Offset(left, y),
                        end = Offset(right, y),
                        strokeWidth = 2f,
                        alpha = 0.9f
                    )
                }
                drawRect(
                    color = Color.White,
                    topLeft = Offset(left, top),
                    size = Size(right - left, bottom - top),
                    style = Stroke(2f),
                    alpha = 0.9f
                )
            }
        }
    }
}
