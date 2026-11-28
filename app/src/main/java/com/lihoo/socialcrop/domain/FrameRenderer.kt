package com.lihoo.socialcrop.domain

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.text.TextPaint
import com.lihoo.socialcrop.data.CinemaSubtitleAlign
import com.lihoo.socialcrop.data.CinemaTheme
import com.lihoo.socialcrop.data.FrameFit
import com.lihoo.socialcrop.data.FrameSpec
import com.lihoo.socialcrop.data.FrameStyle
import com.lihoo.socialcrop.data.WatermarkSpec
import com.lihoo.socialcrop.data.WatermarkBands
import com.lihoo.socialcrop.util.ExifInfo
import com.lihoo.socialcrop.util.recycleDerived
import com.lihoo.socialcrop.data.StrokeColor
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 边框渲染引擎：把成品图放进目标比例的画布，四周用设计感背景填充，
 * 替代传统 letterbox 的黑边/白边。
 *
 * 纯 Bitmap/Canvas 实现，不依赖 UI 框架与第三方库；预览与导出共用此入口，
 * 所有参数来自 [FrameSpec]，保证所见即所得。
 */
object FrameRenderer {

    /**
     * @param src          源图（成品图或切格瓦片）
     * @param targetRatio  画布宽高比 w/h
     * @param outWidth     输出画布宽度（像素），高度按比例换算
     */
    fun render(
        src: Bitmap,
        spec: FrameSpec,
        targetRatio: Float,
        outWidth: Int = spec.outWidth,
        watermark: WatermarkSpec? = null,
        logo: Bitmap? = null,
        exif: ExifInfo? = null
    ): Bitmap {
        val ratio = targetRatio.coerceIn(0.05f, 20f)
        val reqW = if (outWidth <= 0) losslessWidth(src, ratio) else outWidth.coerceAtLeast(64)
        val reqH = (reqW / ratio).roundToInt().coerceAtLeast(64)
        // 用户手填的超大输出宽度也按最长边 4096 等比收敛（与无损档一致），
        // 否则「原比例 + 超长图 + 大输出宽度」会一次分配上百 MB 直接 OOM
        val longest = maxOf(reqW, reqH).toFloat()
        val cap = if (longest > 4096f) 4096f / longest else 1f
        val w = (reqW * cap).roundToInt().coerceAtLeast(64)
        val h = (reqH * cap).roundToInt().coerceAtLeast(64)
        val minSide = min(w, h).toFloat()

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)

        // 电影边框走独立布局：照片铺满画布 + 上下遮罩 + 字幕，不套通用留白逻辑
        if (spec.style == FrameStyle.CINEMA) {
            val photo = renderCinema(src, spec, canvas, w, h, minSide)
            val topBand = RectF(0f, 0f, w.toFloat(), photo.top)
            val bottomBand = RectF(0f, photo.bottom, w.toFloat(), h.toFloat())
            // 下遮罩条留给字幕，居中水印改放上遮罩条，其余仍在上下带内
            drawWatermark(
                canvas, watermark, w, h, logo,
                WatermarkBands(topBand, bottomBand, center = topBand),
                exif
            )
            return out
        }

        // ===== 四边留白（可独立） =====
        val pads = spec.effectivePads
        val padL = (minSide * pads[0].coerceIn(0f, 0.32f)).coerceAtMost(minSide * 0.4f)
        val padT = (minSide * pads[1].coerceIn(0f, 0.32f)).coerceAtMost(minSide * 0.4f)
        val padR = (minSide * pads[2].coerceIn(0f, 0.32f)).coerceAtMost(minSide * 0.4f)
        val padB = (minSide * pads[3].coerceIn(0f, 0.32f)).coerceAtMost(minSide * 0.4f)

        val paletteVisible = spec.style == FrameStyle.GRADIENT &&
            spec.showPalette && spec.paletteCount > 0
        val palCount = spec.paletteCount.coerceIn(1, 8)
        // 色圈半径相对「基准带高」（短边 × bottomBarRatio），与最终带高解耦，避免带越高圆越大的正反馈。
        val palMetrics = if (paletteVisible) paletteMetrics(spec, w.toFloat(), minSide, palCount) else null
        val palOffsetPx = if (paletteVisible) {
            spec.paletteDotOffsetY.coerceIn(-0.06f, 0.06f) * minSide
        } else 0f
        val needBottom = spec.bottomBar || paletteVisible
        val baseBottom = if (needBottom) minSide * spec.bottomBarRatio.coerceIn(0f, 0.35f) else 0f
        // 色带随色圈整块高度与偏移自动增高，放大色圈也不会溢出；上限保护避免吃掉整个画面。
        val bottomReserve = if (palMetrics != null) {
            min(
                max(baseBottom, palMetrics.blockH + 2f * abs(palOffsetPx) + minSide * 0.02f),
                minSide * 0.45f
            )
        } else baseBottom

        val availW = (w - padL - padR).coerceAtLeast(minSide * 0.15f)
        val availH = (h - padT - padB - bottomReserve).coerceAtLeast(minSide * 0.15f)
        val availLeft = padL
        val availTop = padT

        drawBackground(canvas, src, spec, w, h)

        // ===== 内容矩形 =====
        val content = if (spec.fit == FrameFit.CONTAIN) {
            val maxScale = min(availW / src.width, availH / src.height.coerceAtLeast(1))
            val scale = (maxScale * spec.contentScale.coerceIn(0.3f, 2f))
                .coerceAtMost(maxScale) // 不允许放大到裁掉画面
            val cw = src.width * scale
            val ch = src.height * scale
            val freeW = availW - cw
            val freeH = availH - ch
            val cx = availLeft + freeW / 2f + freeW / 2f * spec.horizontalBias.coerceIn(-1f, 1f)
            val cy = availTop + freeH / 2f + freeH / 2f * spec.verticalBias.coerceIn(-1f, 1f)
            RectF(cx, cy, cx + cw, cy + ch)
        } else {
            RectF(availLeft, availTop, availLeft + availW, availTop + availH)
        }

        val radius = min(
            minSide * spec.cornerRatio.coerceIn(0f, 0.3f),
            min(content.width(), content.height()) / 2f
        )

        if (spec.shadow) drawSoftShadow(canvas, content, radius, minSide, spec)
        drawContent(canvas, src, spec.fit, content, radius)
        if (spec.stroke && spec.strokeWidthRatio > 0f) {
            drawStroke(canvas, content, radius, minSide, spec)
        }

        if (palMetrics != null) {
            drawPalette(
                canvas,
                dominantColors(src, palCount),
                w.toFloat(),
                h - bottomReserve,
                bottomReserve,
                spec,
                palMetrics,
                palOffsetPx
            )
        }
        // 水印只落在照片矩形之外的上下留白带，CENTER 用下带正中的签名位
        val topBand = RectF(0f, 0f, w.toFloat(), content.top)
        val bottomBand = RectF(0f, content.bottom, w.toFloat(), h.toFloat())
        drawWatermark(
            canvas, watermark, w, h, logo,
            WatermarkBands(topBand, bottomBand, center = bottomBand),
            exif
        )
        return out
    }

    /** 收尾：在画布最上层叠加用户水印（仅当用户开启且配置了内容） */
    private fun drawWatermark(
        canvas: Canvas,
        watermark: WatermarkSpec?,
        w: Int,
        h: Int,
        logo: Bitmap?,
        bands: WatermarkBands,
        exif: ExifInfo? = null
    ) {
        if (watermark == null) return
        WatermarkRenderer.draw(canvas, watermark, w, h, logo, bands, exif)
    }

    /**
     * 水印可落留白带的高度 / 画布短边。设置页据此把「大小」滑块上限压到实际可用范围：
     * 旧版滑块固定 0.02–0.6，而渲染器按带高把字号钳在约 0.10，导致 15% 之后拉动无变化。
     * 这里只算比例、不分配位图，预览与导出仍由 [render] 里的真实带高决定最终字号。
     */
    fun watermarkBandRatio(spec: FrameSpec, targetRatio: Float, srcW: Int, srcH: Int): Float {
        val ratio = targetRatio.coerceIn(0.05f, 20f)
        // 用归一画布算比例：w=10000，h=10000/ratio
        val w = 10000f
        val h = 10000f / ratio
        val minSide = min(w, h)

        if (spec.style == FrameStyle.CINEMA) {
            // 电影边框水印放上遮罩条
            val barH = h * spec.cinemaBarRatio.coerceIn(0f, 0.30f)
            return (barH / minSide).coerceIn(0f, 2f)
        }

        val pads = spec.effectivePads
        val padL = (minSide * pads[0].coerceIn(0f, 0.32f)).coerceAtMost(minSide * 0.4f)
        val padT = (minSide * pads[1].coerceIn(0f, 0.32f)).coerceAtMost(minSide * 0.4f)
        val padR = (minSide * pads[2].coerceIn(0f, 0.32f)).coerceAtMost(minSide * 0.4f)
        val padB = (minSide * pads[3].coerceIn(0f, 0.32f)).coerceAtMost(minSide * 0.4f)
        val paletteVisible = spec.style == FrameStyle.GRADIENT &&
            spec.showPalette && spec.paletteCount > 0
        val baseBottom = if (spec.bottomBar || paletteVisible) {
            minSide * spec.bottomBarRatio.coerceIn(0f, 0.35f)
        } else 0f
        val bottomReserve = if (paletteVisible) {
            val pm = paletteMetrics(
                spec, w, minSide, spec.paletteCount.coerceIn(1, 8)
            )
            val off = spec.paletteDotOffsetY.coerceIn(-0.06f, 0.06f) * minSide
            min(max(baseBottom, pm.blockH + 2f * abs(off) + minSide * 0.02f), minSide * 0.45f)
        } else baseBottom

        val availW = (w - padL - padR).coerceAtLeast(minSide * 0.15f)
        val availH = (h - padT - padB - bottomReserve).coerceAtLeast(minSide * 0.15f)
        val contentBottom = if (spec.fit == FrameFit.CONTAIN) {
            val maxScale = min(
                availW / srcW.coerceAtLeast(1).toFloat(),
                availH / srcH.coerceAtLeast(1).toFloat()
            )
            val scale = (maxScale * spec.contentScale.coerceIn(0.3f, 2f)).coerceAtMost(maxScale)
            val ch = srcH.coerceAtLeast(1) * scale
            val freeH = availH - ch
            padT + freeH / 2f + freeH / 2f * spec.verticalBias.coerceIn(-1f, 1f) + ch
        } else {
            padT + availH
        }
        return ((h - contentBottom) / minSide).coerceIn(0f, 2f)
    }

    /**
     * 无损输出：画布宽度直接取原图宽度，照片区域以 1:1 绘制、绝不上采样；
     * 高度仍按平台比例换算，超大图整体等比压到 4096px 以内，避免 OOM。
     */
    private fun losslessWidth(src: Bitmap, ratio: Float): Int {
        val w = src.width.coerceAtLeast(64)
        val h = (w / ratio).roundToInt().coerceAtLeast(64)
        val longest = max(w, h).toFloat()
        if (longest <= 4096f) return w
        return (w * 4096f / longest).roundToInt().coerceAtLeast(64)
    }

    // ================= 背景 =================

    private fun drawBackground(canvas: Canvas, src: Bitmap, spec: FrameSpec, w: Int, h: Int) {
        when (spec.style) {
            FrameStyle.CUSTOM -> {
                canvas.drawColor(spec.customColor)
                drawSoftLight(canvas, w, h, Color.WHITE, 28, Color.BLACK, 20)
            }
            FrameStyle.BLUR -> drawBlurBackdrop(canvas, src, spec, w, h)
            // CINEMA 不走通用背景：render() 已提前 return 走独立布局，此处仅满足表达式穷尽
            FrameStyle.CINEMA -> Unit
            FrameStyle.GRADIENT -> {
                val colors = dominantColors(src, 2)
                val c1 = spec.gradientStart ?: colors.getOrElse(0) { Color.rgb(58, 70, 62) }
                val c2 = spec.gradientEnd ?: colors.getOrElse(1) { darken(c1, 0.55f) }
                // 以画布中心为中点，沿 angle 方向延伸，保证覆盖整个画布
                val rad = Math.toRadians(spec.gradientAngle.toDouble())
                val dx = cos(rad).toFloat()
                val dy = sin(rad).toFloat()
                val half = (w * abs(dx) + h * abs(dy)) / 2f
                val cx = w / 2f
                val cy = h / 2f
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(
                        cx - dx * half, cy - dy * half,
                        cx + dx * half, cy + dy * half,
                        c1, c2, Shader.TileMode.CLAMP
                    )
                }
                canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
                drawSoftLight(canvas, w, h, Color.WHITE, 22, Color.BLACK, 16)
            }
        }
    }

    /**
     * v2.1.6「两面叠加」背景：按参考实现 = 叠两层图片。
     * 底层 = 同一张原图放大 + 强模糊铺满，**天然继承照片自己的纹理与色系**（冷暖/明暗
     * 都来自原图本身，不再用 Shader 人工染色）；顶层 = 清晰照片 + 圆角 + 投影（在 render 里），
     * 靠「模糊 vs 清晰」的对比凸显前景。
     * 与 v2.1.5 的区别：删掉叠在模糊底上的 5 层人工光效（暗调基调/顶部压暗/底部柔光/
     * 右侧压暗/四角暗角）——那些会把背景变成人造调色，不再是「一张模糊的照片」。
     *
     * 处理链只有四步：缩略 → 强模糊 → cover 铺满 → 一层均匀的极轻压暗凸显前景。
     * 不做任何渐变/方向/暗角，保持两层的忠实观感；前景照片像素完全不动，
     * 不分配额外整屏 Bitmap。
     */
    private fun drawBlurBackdrop(canvas: Canvas, src: Bitmap, spec: FrameSpec, w: Int, h: Int) {
        // 1) 缩略 + 强模糊：缩略图越小、boxBlur 半径越大，背景越糊。
        val strength = spec.blurStrength.coerceIn(0f, 1f)
        val tw = (14 + (1f - strength) * 50f).roundToInt().coerceIn(14, 64)
        // 极窄长图会让 th 按宽高比放大到几十万像素，boxBlur 要分配两个 IntArray(w*h) 直接 OOM
        val th = (tw * src.height / src.width.coerceAtLeast(1))
            .coerceIn(1, tw * 8)
        val tiny = Bitmap.createScaledBitmap(src, tw, th, true)

        // 背景饱和度 = 「背景饱和度」滑块 × 「色系强度」滑块。
        // 两层方案里色系本就来自原图模糊：默认 warmth=0.55 归一到 1.0，即完全保留原图色
        // （参考图背景是饱满的暖棕/绿，不该被人为冲淡）；warmth=0 → 背景去色成中性灰；
        // warmth=1 → 在 sat 基础上进一步提饱和到原色饱和。sat 单独仍可降饱和。
        val sat = spec.blurSaturation.coerceIn(0f, 1f)
        val warmth = spec.blurWarmth.coerceIn(0f, 1f)
        val effSat = (sat * (warmth / 0.55f)).coerceIn(0f, 1f)
        val soft = boxBlur(tiny, (2 + strength * 5f).roundToInt(), 3, effSat)

        // 2) cover 铺满整幅画布 —— 这就是背景层
        val zoom = spec.blurZoom.coerceIn(1f, 1.8f)
        val dw = w * zoom
        val dh = h * zoom
        drawCover(
            canvas, soft,
            RectF((w - dw) / 2f, (h - dh) / 2f, (w + dw) / 2f, (h + dh) / 2f)
        )
        soft.recycle()
        recycleDerived(tiny, src)

        // 3) 一层均匀的极轻压暗，让清晰前景从模糊底里「浮」出来。
        //    不做渐变/方向/暗角——那会把背景变成人工光影，丢失「两层图片」的忠实观感。
        //    blurDim 默认 0.26 → 约 9% 压暗；blurLight 默认 0.70 → 约 4% 暖白回提，
        //    净约 5% 轻压暗，只做轻微凸显，不改造原图色调。
        val dimA = (spec.blurDim.coerceIn(0f, 0.7f) * 0.35f * 255f).roundToInt()
        if (dimA > 0) canvas.drawColor(Color.argb(dimA, 0, 0, 0))
        val lightA = (spec.blurLight.coerceIn(0f, 1f) * 0.06f * 255f).roundToInt()
        if (lightA > 0) canvas.drawColor(Color.argb(lightA, 255, 248, 238))
    }

    /** 纯色 / 渐变背景的柔和光感：中心偏上提亮 + 四角极轻收暗，避免死平的色块。 */
    private fun drawSoftLight(
        canvas: Canvas,
        w: Int,
        h: Int,
        glowColor: Int,
        glowAlpha: Int,
        cornerColor: Int,
        cornerAlpha: Int
    ) {
        val maxSide = max(w, h).toFloat()
        val full = RectF(0f, 0f, w.toFloat(), h.toFloat())
        val gr = Color.red(glowColor)
        val gg = Color.green(glowColor)
        val gb = Color.blue(glowColor)
        if (glowAlpha > 0) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    w / 2f, h * 0.42f, maxSide * 0.66f,
                    intArrayOf(
                        Color.argb(glowAlpha, gr, gg, gb),
                        Color.argb((glowAlpha * 0.45f).roundToInt(), gr, gg, gb),
                        Color.argb(0, gr, gg, gb)
                    ),
                    floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(full, p)
        }
        if (cornerAlpha > 0) {
            val cr = Color.red(cornerColor)
            val cg = Color.green(cornerColor)
            val cb = Color.blue(cornerColor)
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(
                    w / 2f, h * 0.48f, maxSide * 0.72f,
                    intArrayOf(
                        Color.argb(0, cr, cg, cb),
                        Color.argb((cornerAlpha * 0.35f).roundToInt(), cr, cg, cb),
                        Color.argb(cornerAlpha, cr, cg, cb)
                    ),
                    floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(full, p)
        }
    }

    /** 三次 box blur 近似高斯；同时按 saturation 降低背景饱和度 */
    private fun boxBlur(src: Bitmap, radius: Int, passes: Int, saturation: Float): Bitmap {
        val w = src.width
        val h = src.height
        val a = IntArray(w * h)
        val b = IntArray(w * h)
        src.getPixels(a, 0, w, 0, 0, w, h)
        val r = radius.coerceIn(1, 12)
        repeat(passes.coerceIn(1, 4)) {
            blurAxis(a, b, w, h, r, horizontal = true)
            blurAxis(b, a, w, h, r, horizontal = false)
        }
        val sat = saturation.coerceIn(0f, 1f)
        if (sat < 0.999f) {
            for (i in a.indices) {
                val c = a[i]
                val rr = (c shr 16) and 0xFF
                val gg = (c shr 8) and 0xFF
                val bb = c and 0xFF
                val gray = (rr * 299 + gg * 587 + bb * 114) / 1000
                val nr = (gray + (rr - gray) * sat).toInt().coerceIn(0, 255)
                val ng = (gray + (gg - gray) * sat).toInt().coerceIn(0, 255)
                val nb = (gray + (bb - gray) * sat).toInt().coerceIn(0, 255)
                a[i] = (0xFF000000.toInt()) or (nr shl 16) or (ng shl 8) or nb
            }
        }
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
            setPixels(a, 0, w, 0, 0, w, h)
        }
    }

    /** 滑动窗口均值模糊，单轴 O(n)，边界按钳制取样 */
    private fun blurAxis(
        input: IntArray,
        output: IntArray,
        w: Int,
        h: Int,
        radius: Int,
        horizontal: Boolean
    ) {
        val len = if (horizontal) w else h
        val lines = if (horizontal) h else w
        val n = 2 * radius + 1
        for (l in 0 until lines) {
            var sr = 0
            var sg = 0
            var sb = 0
            for (k in -radius..radius) {
                val c = input[axisIndex(k, l, w, len, horizontal)]
                sr += (c shr 16) and 0xFF
                sg += (c shr 8) and 0xFF
                sb += c and 0xFF
            }
            for (i in 0 until len) {
                output[axisIndex(i, l, w, len, horizontal)] =
                    (0xFF000000.toInt()) or ((sr / n) shl 16) or ((sg / n) shl 8) or (sb / n)
                val oldC = input[axisIndex(i - radius, l, w, len, horizontal)]
                val newC = input[axisIndex(i + radius + 1, l, w, len, horizontal)]
                sr += ((newC shr 16) and 0xFF) - ((oldC shr 16) and 0xFF)
                sg += ((newC shr 8) and 0xFF) - ((oldC shr 8) and 0xFF)
                sb += (newC and 0xFF) - (oldC and 0xFF)
            }
        }
    }

    private fun axisIndex(i: Int, line: Int, w: Int, len: Int, horizontal: Boolean): Int {
        val c = i.coerceIn(0, len - 1)
        return if (horizontal) line * w + c else c * w + line
    }

    // ================= 投影 / 描边 / 内容 =================

    /**
     * v1.9.0 真实柔化投影：把圆角矩形画进一张低分辨率 alpha 遮罩，
     * 只对 alpha 通道做两轮可分离 box blur，再以纯黑叠加回画布。
     * 旧版是 6 层硬圆角矩形堆出的短硬阴影（扩散仅 20 多像素），
     * 新版扩散约画布短边的 7%，照片才有「浮起来」的层次。
     * 遮罩最长边限制在 1024px，避免无损档大图时 IntArray 撑爆内存。
     */
    private fun drawSoftShadow(
        canvas: Canvas,
        r: RectF,
        radius: Float,
        minSide: Float,
        spec: FrameSpec
    ) {
        val spread = spec.shadowSpread.coerceIn(0.2f, 3f)
        // 滑块上限 1.2 对应满浓度；旧版范围 0–2 但这里 1.18 就饱和，后半段拉动无变化
        val strength = (spec.shadowStrength.coerceIn(0f, 1.2f) / 1.2f).coerceIn(0f, 1f)
        if (strength <= 0f) return

        val blurPx = (minSide * 0.074f * spread).roundToInt().coerceIn(2, 240)
        // 三次 box blur 的合成支撑宽度约 3×半径，留白要够才不会把阴影裁出硬边
        val margin = (blurPx * 3.2f).roundToInt()
        val cw = r.width().roundToInt().coerceAtLeast(1)
        val ch = r.height().roundToInt().coerceAtLeast(1)
        val mw = cw + margin * 2
        val mh = ch + margin * 2
        val scale = min(1f, 1024f / max(mw, mh).toFloat())
        val sw = (mw * scale).roundToInt().coerceAtLeast(1)
        val sh = (mh * scale).roundToInt().coerceAtLeast(1)

        val mask = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888)
        val mc = Canvas(mask)
        val mg = margin * scale
        val rr = radius * scale
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
        mc.drawRoundRect(RectF(mg, mg, mg + cw * scale, mg + ch * scale), rr, rr, fill)

        val px = IntArray(sw * sh)
        mask.getPixels(px, 0, sw, 0, 0, sw, sh)
        alphaBlur(px, sw, sh, (blurPx * scale).roundToInt().coerceIn(1, 200), 3)
        for (i in px.indices) {
            val a = (((px[i] ushr 24) and 0xFF) * strength).roundToInt().coerceIn(0, 255)
            px[i] = Color.argb(a, 0, 0, 0)
        }
        mask.setPixels(px, 0, sw, 0, 0, sw, sh)

        val offsetY = minSide * 0.006f * spread
        val dst = RectF(
            r.left - margin, r.top - margin + offsetY,
            r.right + margin, r.bottom + margin + offsetY
        )
        canvas.drawBitmap(mask, null, dst, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        mask.recycle()
    }

    /** 只模糊 alpha 通道的可分离 box blur；输出像素为纯黑 + 新 alpha */
    private fun alphaBlur(px: IntArray, w: Int, h: Int, radius: Int, passes: Int) {
        val tmp = IntArray(px.size)
        val r = radius.coerceIn(1, 200)
        val n = 2 * r + 1
        repeat(passes.coerceIn(1, 4)) {
            for (y in 0 until h) {
                val base = y * w
                var sum = 0
                for (k in -r..r) sum += (px[base + k.coerceIn(0, w - 1)] ushr 24) and 0xFF
                for (x in 0 until w) {
                    tmp[base + x] = sum / n
                    val old = (px[base + (x - r).coerceIn(0, w - 1)] ushr 24) and 0xFF
                    val next = (px[base + (x + r + 1).coerceIn(0, w - 1)] ushr 24) and 0xFF
                    sum += next - old
                }
            }
            for (x in 0 until w) {
                var sum = 0
                for (k in -r..r) sum += tmp[k.coerceIn(0, h - 1) * w + x]
                for (y in 0 until h) {
                    val idx = y * w + x
                    px[idx] = (sum / n) shl 24
                    val old = tmp[(y - r).coerceIn(0, h - 1) * w + x]
                    val next = tmp[(y + r + 1).coerceIn(0, h - 1) * w + x]
                    sum += next - old
                }
            }
        }
    }

    private fun drawStroke(
        canvas: Canvas,
        r: RectF,
        radius: Float,
        minSide: Float,
        spec: FrameSpec
    ) {
        val color = when (spec.strokeColorMode) {
            StrokeColor.WHITE -> Color.WHITE
            StrokeColor.BLACK -> Color.rgb(24, 24, 26)
            StrokeColor.AUTO -> if (spec.isLightBackground) Color.rgb(32, 32, 34) else Color.WHITE
        }
        val sw = (minSide * spec.strokeWidthRatio.coerceIn(0f, 0.03f)).coerceIn(0.5f, 40f)
        val inset = sw / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = sw
            this.color = color
        }
        canvas.drawRoundRect(
            RectF(r.left + inset, r.top + inset, r.right - inset, r.bottom - inset),
            (radius - inset).coerceAtLeast(0f),
            (radius - inset).coerceAtLeast(0f),
            paint
        )
    }

    private fun drawContent(
        canvas: Canvas,
        src: Bitmap,
        fit: FrameFit,
        rect: RectF,
        radius: Float
    ) {
        val cw = rect.width().roundToInt().coerceAtLeast(1)
        val ch = rect.height().roundToInt().coerceAtLeast(1)
        val layer = Bitmap.createBitmap(cw, ch, Bitmap.Config.ARGB_8888)
        val lc = Canvas(layer)
        if (radius > 0.5f) {
            val path = Path().apply {
                addRoundRect(
                    RectF(0f, 0f, cw.toFloat(), ch.toFloat()),
                    radius, radius, Path.Direction.CW
                )
            }
            lc.clipPath(path)
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
        if (fit == FrameFit.COVER) {
            drawCover(lc, src, RectF(0f, 0f, cw.toFloat(), ch.toFloat()), paint)
        } else {
            lc.drawBitmap(src, null, RectF(0f, 0f, cw.toFloat(), ch.toFloat()), paint)
        }
        canvas.drawBitmap(layer, rect.left, rect.top, paint)
        layer.recycle()
    }

    /** 把 src 居中裁切后铺满 dst */
    private fun drawCover(canvas: Canvas, src: Bitmap, dst: RectF, paint: Paint? = null) {
        val p = paint ?: Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val srcRatio = src.width.toFloat() / src.height.coerceAtLeast(1)
        val dstRatio = dst.width() / dst.height().coerceAtLeast(1f)
        val crop = if (srcRatio > dstRatio) {
            val cw = src.height * dstRatio
            Rect(((src.width - cw) / 2f).toInt(), 0, ((src.width + cw) / 2f).toInt(), src.height)
        } else {
            val ch = src.width / dstRatio
            Rect(0, ((src.height - ch) / 2f).toInt(), src.width, ((src.height + ch) / 2f).toInt())
        }
        val left = crop.left.coerceIn(0, src.width - 1)
        val top = crop.top.coerceIn(0, src.height - 1)
        val right = crop.right.coerceIn(left + 1, src.width)
        val bottom = crop.bottom.coerceIn(top + 1, src.height)
        canvas.drawBitmap(src, Rect(left, top, right, bottom), dst, p)
    }

    // ================= 电影边框 =================

    /**
     * 电影边框（v1.9.1 按参考录屏复刻）：
     * 照片按画布比例居中填满整块画布（选「原比例」时即为 1:1 完整保留），
     * 上下叠对称遮罩条盖在照片上，下遮罩内放两行紧贴的居中字幕。
     * 遮罩有纯黑 / 纯白 / 柔和渐隐三种主题，字幕颜色随主题反色。
     * 返回「上下遮罩之间」的照片区域，供水印层计算可用的边框带。
     */
    private fun renderCinema(
        src: Bitmap,
        spec: FrameSpec,
        canvas: Canvas,
        w: Int,
        h: Int,
        minSide: Float
    ): RectF {
        val theme = spec.cinemaTheme
        val barColor = if (theme == CinemaTheme.WHITE) Color.rgb(255, 255, 255) else Color.BLACK
        val textColor = if (theme == CinemaTheme.WHITE) Color.rgb(26, 26, 28) else Color.WHITE
        val full = RectF(0f, 0f, w.toFloat(), h.toFloat())

        canvas.drawColor(barColor)
        // 照片铺满画布：不再缩小居中留一圈黑边，这才是参考里的电影画面
        drawCover(canvas, src, full, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))

        val barH = h * spec.cinemaBarRatio.coerceIn(0f, 0.30f)
        if (barH > 0f) {
            when (theme) {
                CinemaTheme.BLACK, CinemaTheme.WHITE -> {
                    val barPaint = Paint().apply {
                        color = barColor
                        style = Paint.Style.FILL
                    }
                    canvas.drawRect(0f, 0f, w.toFloat(), barH, barPaint)
                    canvas.drawRect(0f, h - barH, w.toFloat(), h.toFloat(), barPaint)
                }
                // 柔和遮罩：把照片糊成一块铺满上下遮罩条（参考视频的 fade 是模糊块，不是渐变）
                CinemaTheme.FADE -> {
                    drawBlurredBars(canvas, src, w, h, barH)
                }
            }
        }

        val hasSub = spec.showSubtitleSecondary && spec.subtitleSecondary.isNotBlank()
        if (spec.subtitle.isNotBlank() || hasSub) {
            drawSubtitle(canvas, spec, w, h, minSide, barH, textColor, theme, hasSub)
        }
        return RectF(0f, barH, w.toFloat(), h.toFloat() - barH)
    }

    /** 柔焦遮罩条：照片降采样后强模糊，cover 填满上下遮罩条，呈现「模糊一块」效果 */
    private fun drawBlurredBars(canvas: Canvas, src: Bitmap, w: Int, h: Int, barH: Float) {
        val maxDim = 240
        val scale = minOf(1f, maxDim.toFloat() / maxOf(src.width, src.height))
        val sw = max(1, (src.width * scale).toInt())
        val sh = max(1, (src.height * scale).toInt())
        val small = Bitmap.createScaledBitmap(src, sw, sh, true)
        val radius = max(3, (minOf(sw, sh) * 0.05f).toInt())
        val blurred = boxBlur(small, radius, 3, 0.95f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        drawCover(canvas, blurred, RectF(0f, 0f, w.toFloat(), barH), paint)
        drawCover(canvas, blurred, RectF(0f, h - barH, w.toFloat(), h.toFloat()), paint)
        // 两张临时位图用完即回收，否则每次 FADE 渲染都会泄漏一份柔焦底
        blurred.recycle()
        recycleDerived(small, src)
    }

    /** 字幕两行紧贴居中：主行略大、副行约 0.78 倍，位置落在下方遮罩内 */
    private fun drawSubtitle(
        canvas: Canvas,
        spec: FrameSpec,
        w: Int,
        h: Int,
        minSide: Float,
        barH: Float,
        textColor: Int,
        theme: CinemaTheme,
        hasSub: Boolean
    ) {
        val main = spec.subtitle.trim()
        val sub = if (hasSub) spec.subtitleSecondary.trim() else ""
        val maxW = w * 0.88f
        val baseSize = minSide * spec.subtitleSizeRatio.coerceIn(0.015f, 0.09f)
        val subSize = baseSize * 0.78f
        val gap = baseSize * spec.subtitleLineGap.coerceIn(0f, 0.8f)
        val blockH = if (sub.isEmpty()) baseSize * 1.15f else baseSize * 1.15f + gap + subSize * 1.15f

        // 遮罩够高时字幕居中于下遮罩；遮罩太薄或为 0 时贴着画面底部并加轻投影保证可读
        val cy = if (barH > blockH * 1.12f) {
            h - barH / 2f
        } else {
            h - blockH / 2f - minSide * 0.022f
        }
        val needShadow = barH <= blockH * 1.12f || theme == CinemaTheme.FADE
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            letterSpacing = spec.subtitleLetterSpacing.coerceIn(-0.05f, 0.5f)
            if (needShadow) {
                val sr = (baseSize * 0.10f).coerceAtLeast(1f)
                setShadowLayer(sr, 0f, sr * 0.4f, Color.argb(190, 0, 0, 0))
            }
        }
        when (spec.subtitleAlign) {
            CinemaSubtitleAlign.START -> {
                paint.textAlign = Paint.Align.LEFT
            }
            CinemaSubtitleAlign.CENTER -> {
                paint.textAlign = Paint.Align.CENTER
            }
            CinemaSubtitleAlign.END -> {
                paint.textAlign = Paint.Align.RIGHT
            }
        }
        val x = when (spec.subtitleAlign) {
            CinemaSubtitleAlign.START -> w * 0.06f
            CinemaSubtitleAlign.CENTER -> w / 2f
            CinemaSubtitleAlign.END -> w * 0.94f
        }
        if (sub.isEmpty()) {
            paint.textSize = fittedSize(paint, main, maxW, baseSize)
            canvas.drawText(main, x, cy + paint.textSize * 0.35f, paint)
        } else {
            val mainSize = fittedSize(paint, main, maxW, baseSize)
            paint.textSize = mainSize
            val mainY = cy - (blockH - mainSize) / 2f + mainSize * 0.82f
            canvas.drawText(main, x, mainY, paint)
            paint.textSize = fittedSize(paint, sub, maxW, subSize)
            canvas.drawText(sub, x, mainY + mainSize * 0.35f + gap + paint.textSize * 0.72f, paint)
        }
    }

    /** 字幕过长时按可用宽度等比缩小字号，避免被画布裁掉 */
    private fun fittedSize(paint: TextPaint, text: String, maxW: Float, size: Float): Float {
        if (text.isEmpty()) return size
        paint.textSize = size
        val measured = paint.measureText(text)
        return if (measured > maxW) (size * maxW / measured).coerceAtLeast(size * 0.4f) else size
    }

    // ================= 色卡 =================

    private data class PaletteMetrics(
        val dotR: Float, val textSize: Float, val textGap: Float, val blockH: Float
    )

    /** 色圈整块尺寸：半径相对「基准带高」(短边 × bottomBarRatio)，与最终带高解耦；
     *  hex 文字随半径等比缩放；水平间距钳制保证圆不重叠。 */
    private fun paletteMetrics(
        spec: FrameSpec, w: Float, minSide: Float, count: Int
    ): PaletteMetrics {
        val gap = w / (count + 1)
        val baseBand = minSide * spec.bottomBarRatio.coerceIn(0f, 0.35f)
        val dotR = min(
            baseBand * spec.paletteDotRatio.coerceIn(0.06f, 1.8f),
            gap * 0.42f
        ).coerceAtLeast(2f)
        val textSize = max(7f, dotR * 0.72f)
        val textGap = textSize * 0.35f
        return PaletteMetrics(dotR, textSize, textGap, dotR * 2f + textGap + textSize)
    }

    private fun drawPalette(
        canvas: Canvas,
        colors: List<Int>,
        w: Float,
        top: Float,
        reserveH: Float,
        spec: FrameSpec,
        metrics: PaletteMetrics,
        offsetPx: Float
    ) {
        if (colors.isEmpty() || reserveH <= 0f) return
        val n = colors.size
        val gap = w / (n + 1)
        val (dotR, textSize, textGap, blockH) = metrics
        // 整块在带内垂直居中后再叠加用户上下偏移；bottomReserve 已为偏移预留空间。
        val cy = top + (reserveH - blockH).coerceAtLeast(0f) / 2f + dotR + offsetPx
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.textSize = textSize
            textAlign = Paint.Align.CENTER
            color = if (spec.isLightBackground) Color.rgb(90, 90, 90) else Color.rgb(220, 220, 220)
        }
        for (i in 0 until n) {
            val cx = gap * (i + 1)
            dotPaint.color = colors[i]
            canvas.drawCircle(cx, cy, dotR, dotPaint)
            val hex = "#%02x%02x%02x".format(
                Color.red(colors[i]), Color.green(colors[i]), Color.blue(colors[i])
            )
            canvas.drawText(hex, cx, cy + dotR + textGap + textSize * 0.85f, textPaint)
        }
    }

    /**
     * 主色调提取：把图片缩到 48×48，用确定性 k-means 聚类出 count 个代表性颜色，
     * 再做最小色距去重，保证色卡里是彼此可区分的主色，而不是一堆近似色。
     * 初始化用「均值 + 最远点采样」，结果稳定，不会在同一次预览中跳色。
     */
    fun dominantColors(src: Bitmap, count: Int): List<Int> {
        val n = 48
        val small = Bitmap.createScaledBitmap(src, n, n, true)
        val px = IntArray(n * n)
        small.getPixels(px, 0, n, 0, 0, n, n)
        recycleDerived(small, src)

        val pts = Array(px.size) { i ->
            val c = px[i]
            floatArrayOf(
                ((c shr 16) and 0xFF).toFloat(),
                ((c shr 8) and 0xFF).toFloat(),
                (c and 0xFF).toFloat()
            )
        }
        val k = count.coerceIn(1, 8).coerceAtMost(pts.size)

        val mean = FloatArray(3)
        for (p in pts) {
            mean[0] += p[0]; mean[1] += p[1]; mean[2] += p[2]
        }
        mean[0] /= pts.size; mean[1] /= pts.size; mean[2] /= pts.size

        val centers = ArrayList<FloatArray>(k)
        centers.add(mean)
        while (centers.size < k) {
            var best: FloatArray? = null
            var bestDist = -1f
            for (p in pts) {
                var d = Float.MAX_VALUE
                for (c in centers) d = min(d, dist2(p, c))
                if (d > bestDist) {
                    bestDist = d
                    best = p
                }
            }
            centers.add((best ?: mean).copyOf())
        }

        val sizes = IntArray(k)
        repeat(10) {
            val sums = Array(k) { FloatArray(4) }
            for (p in pts) {
                var bi = 0
                var bd = Float.MAX_VALUE
                for (c in 0 until k) {
                    val d = dist2(p, centers[c])
                    if (d < bd) {
                        bd = d
                        bi = c
                    }
                }
                sums[bi][0] += p[0]
                sums[bi][1] += p[1]
                sums[bi][2] += p[2]
                sums[bi][3] += 1f
            }
            for (c in 0 until k) {
                val s = sums[c]
                if (s[3] > 0f) {
                    centers[c][0] = s[0] / s[3]
                    centers[c][1] = s[1] / s[3]
                    centers[c][2] = s[2] / s[3]
                }
                sizes[c] = s[3].toInt()
            }
        }

        val ordered = (0 until k).map { c ->
            Pair(
                Color.rgb(
                    centers[c][0].roundToInt().coerceIn(0, 255),
                    centers[c][1].roundToInt().coerceIn(0, 255),
                    centers[c][2].roundToInt().coerceIn(0, 255)
                ),
                sizes[c]
            )
        }.sortedByDescending { it.second }

        val result = mutableListOf<Int>()
        for (threshold2 in listOf(42 * 42, 24 * 24, 0)) {
            for (c in ordered) {
                if (c.first in result) continue
                if (threshold2 > 0 && result.any { colorDist2(it, c.first) < threshold2 }) continue
                result.add(c.first)
                if (result.size >= k) break
            }
            if (result.size >= k) break
        }
        return result
    }

    private fun dist2(a: FloatArray, b: FloatArray): Float {
        val dr = a[0] - b[0]
        val dg = a[1] - b[1]
        val db = a[2] - b[2]
        return dr * dr + dg * dg + db * db
    }

    private fun colorDist2(c1: Int, c2: Int): Int {
        val dr = Color.red(c1) - Color.red(c2)
        val dg = Color.green(c1) - Color.green(c2)
        val db = Color.blue(c1) - Color.blue(c2)
        return dr * dr + dg * dg + db * db
    }

    private fun darken(color: Int, factor: Float): Int = Color.rgb(
        (Color.red(color) * factor).toInt().coerceIn(0, 255),
        (Color.green(color) * factor).toInt().coerceIn(0, 255),
        (Color.blue(color) * factor).toInt().coerceIn(0, 255)
    )
}
