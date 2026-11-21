package com.lihoo.socialcrop.domain

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.lihoo.socialcrop.data.WatermarkBands
import com.lihoo.socialcrop.data.WatermarkSpec

/**
 * 用户水印渲染：纯 Canvas 绘制，文字 和/或 本地 Logo 任意组合。
 * 所有尺寸相对画布短边，保证不同分辨率下观感一致。
 * 仅当用户主动开启（enabled）且至少配置了文字或 Logo 时才绘制。
 *
 * v1.9.6：带边框时水印锚定到「照片之外的上下留白带」（电影模式为遮罩条），
 * 并按带高自适应缩小，绝不压到照片上；CENTER = 下留白正中的签名位。
 * v2.1.8：位置统一为下方居中，不再有左上/右上/左下/右下——带边框时取下留白带
 * 正中（电影模式下带留给字幕则退用上带），无边框时贴底居中，绝不落在画面正中。
 */
object WatermarkRenderer {

    /**
     * v2.1.8 默认字体：微软雅黑（无衬线黑体，与相机水印参考图一致）。
     * Android 不自带该字体时 Typeface.create 会回退到系统 sans-serif bold，
     * 观感仍是无衬线粗体；不打包字体文件，守住零权限 / 离线 / 体积不变。
     */
    private val textTypeface: Typeface by lazy {
        runCatching { Typeface.create("Microsoft YaHei", Typeface.BOLD) }
            .getOrNull() ?: Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }

    /**
     * @param bands 非 null 表示带边框，水印只允许落在这些留白带里；为 null 表示无边框
     */
    fun draw(
        canvas: Canvas,
        wm: WatermarkSpec,
        w: Int,
        h: Int,
        logo: Bitmap?,
        bands: WatermarkBands? = null
    ) {
        if (!wm.enabled) return
        val hasText = wm.showText && wm.text.isNotBlank()
        val hasLogo = wm.showLogo && logo != null
        if (!hasText && !hasLogo) return

        val minSide = minOf(w, h).toFloat()
        val alpha = (wm.opacity.coerceIn(0f, 1f) * 255).toInt()
        val userBase = minSide * wm.scaleRatio.coerceIn(0.02f, 0.8f)

        // 平铺是用户主动要的「铺满整图」，保持原样，不受留白带约束
        if (wm.tiled) {
            val step = (userBase * 3.2f).coerceAtLeast(1f)
            // 最小尺寸 + 极端长宽比可能铺出上万个水印，限制总绘制数避免卡死
            val maxTiles = 400
            var drawn = 0
            var row = 0
            var y = step / 2f
            while (y < h + step && drawn < maxTiles) {
                val xOff = if (row % 2 == 0) step / 2f else 0f
                var x = step / 2f + xOff
                while (x < w + step && drawn < maxTiles) {
                    drawOne(canvas, wm, logo, x, y, userBase, alpha)
                    drawn++
                    x += step
                }
                y += step
                row++
            }
            return
        }

        // v2.1.8：固定下方居中 —— 优先下留白带；太薄则退用上带（电影模式下带留给字幕）
        val preferred = bands?.bottom
        val fallback = bands?.top
        // 留白带太薄时换另一侧；两侧都不够就退回贴底逻辑，绝不落在画面正中
        val minBandH = minSide * 0.03f
        val band = when {
            preferred != null && preferred.height() >= minBandH -> preferred
            fallback != null && fallback.height() >= minBandH -> fallback
            else -> null
        }

        // 落在留白带内时按带高缩小，保证整个水印块不越界压到照片
        val heightFactor = blockHeightFactor(wm, hasLogo)
        var base = if (band != null) {
            minOf(userBase, band.height() * 0.82f / heightFactor)
        } else userBase

        // 超长文字会算出比画布还宽的水印块，左上角锚点被推到画布外、整段水印看不见。
        // 文字宽度与 Logo 宽度都和 base 线性相关，按可用宽度等比收敛一次即可。
        val margin = minOf(w, h).toFloat() * wm.marginRatio.coerceIn(0f, 0.4f)
        val maxBlockW = (w - 2f * margin).coerceAtLeast(minSide * 0.2f)
        blockSize(wm, base, hasLogo, logo).let { (bw, _) ->
            if (bw > maxBlockW) base *= (maxBlockW / bw).coerceAtLeast(0.1f)
        }

        val (cx, cy) = computeCenter(wm, w, h, base, hasLogo, logo, band)
        drawOne(canvas, wm, logo, cx, cy, base, alpha)
    }

    /** 水印块总高度相对 base 的系数，与 drawOne 里的排版严格一致 */
    private fun blockHeightFactor(wm: WatermarkSpec, hasLogo: Boolean): Float {
        val logoH = if (hasLogo) 1f else 0f
        val textH = if (wm.showText && wm.text.isNotBlank()) 0.30f else 0f
        val gap = if (logoH > 0f && textH > 0f) 0.12f else 0f
        return (logoH + gap + textH).coerceAtLeast(0.30f)
    }

    private fun computeCenter(
        wm: WatermarkSpec,
        w: Int,
        h: Int,
        base: Float,
        hasLogo: Boolean,
        logo: Bitmap?,
        band: RectF?
    ): Pair<Float, Float> {
        val margin = minOf(w, h).toFloat() * wm.marginRatio.coerceIn(0f, 0.4f)
        val (bw, bh) = blockSize(wm, base, hasLogo, logo)
        // v2.1.8：水平恒定居中；垂直方向落在留白带内则取带中心，否则贴底居中
        val cx = w / 2f
        val cy = if (band != null) {
            (band.top + band.bottom) / 2f
        } else {
            h - margin - bh / 2f
        }
        return Pair(cx, cy)
    }

    private fun blockSize(wm: WatermarkSpec, base: Float, hasLogo: Boolean, logo: Bitmap?): Pair<Float, Float> {
        val logoH = if (hasLogo && logo != null) base else 0f
        val textH = if (wm.showText && wm.text.isNotBlank()) base * 0.30f else 0f
        val gap = if (logoH > 0f && textH > 0f) base * 0.12f else 0f
        val bh = logoH + gap + textH
        val logoW = if (hasLogo && logo != null) base * logo.width.toFloat() / logo.height.coerceAtLeast(1) else 0f
        val textW = if (textH > 0f) measureText(wm.text, textH) else 0f
        val bw = maxOf(logoW, textW).coerceAtLeast(1f)
        return Pair(bw, bh.coerceAtLeast(1f))
    }

    private fun measureText(text: String, textH: Float): Float {
        if (text.isBlank()) return 0f
        return Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = textH
            typeface = textTypeface
        }.measureText(text)
    }

    private fun drawOne(
        canvas: Canvas, wm: WatermarkSpec, logo: Bitmap?, cx: Float, cy: Float, base: Float, alpha: Int
    ) {
        val logoH = if (wm.showLogo && logo != null) base else 0f
        val textH = if (wm.showText && wm.text.isNotBlank()) base * 0.30f else 0f
        val gap = if (logoH > 0f && textH > 0f) base * 0.12f else 0f

        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(wm.rotation)

        val totalH = logoH + gap + textH
        var top = -totalH / 2f

        if (logoH > 0f && logo != null) {
            val lw = base * logo.width.toFloat() / logo.height.coerceAtLeast(1)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                this.alpha = alpha
                if (wm.shadowOn) setShadowLayer(base * 0.04f, 0f, base * 0.02f, 0x55000000)
            }
            canvas.drawBitmap(logo, null, RectF(-lw / 2f, top, lw / 2f, top + logoH), paint)
            top += logoH + gap
        }

        if (textH > 0f) {
            val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = wm.textColor
                textSize = textH
                textAlign = Paint.Align.CENTER
                typeface = textTypeface
                this.alpha = alpha
                if (wm.shadowOn) setShadowLayer(textH * 0.08f, 0f, textH * 0.04f, 0x55000000)
            }
            canvas.drawText(wm.text, 0f, top + textH * 0.82f, tp)
        }

        canvas.restore()
    }
}
