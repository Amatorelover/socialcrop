package com.lihoo.socialcrop.domain

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.lihoo.socialcrop.data.LogoTint
import com.lihoo.socialcrop.data.WatermarkAlign
import com.lihoo.socialcrop.data.WatermarkBlockAlign
import com.lihoo.socialcrop.data.WatermarkBands
import com.lihoo.socialcrop.data.WatermarkSpec
import com.lihoo.socialcrop.util.ExifInfo
import com.lihoo.socialcrop.util.ExifReader

/**
 * 用户水印渲染：纯 Canvas 绘制。
 *
 * v2.20.0 三种排版：
 *  1) 有图片 / 图标 Logo + 只有一行文字（作者 / 时间 / 机型 / 镜头任一）：
 *     **Logo 在上、文字在下，均居中**，垂直堆叠，中间留间距，绝不重叠。
 *  2) 有图片 / 图标 Logo + 两行文字（作者+时间 或 机型+镜头）：
 *     v2.20 起，整体对齐为居中 / 左 / 右时统一走「Logo 与第一行文字同排、垂直居中对齐，
 *     第二行文字在两者下方居中」的排版；仅「两边对齐」(SIDES) 保留 Logo 左 / 文字右的旧逻辑。
 *  3) 无图片 Logo（纯文字）：沿用相机水印参考图的两行居中排版。
 *
 * 三类水印各有自己的基准字号（logoScaleRatio / textScaleRatio / exifScaleRatio），
 * 由各 Tab 的大小滑块独立记忆；带边框时按留白带对整块做一次等比钳制，保证不溢出。
 * 三类位移各自作用到自己的元素，但整块只旋转一次，避免「歪水印」。
 */
object WatermarkRenderer {

    private const val REF = 100f
    /** 同行项间距 / 行间距，按基准字号的比例 */
    private const val ITEM_GAP = 0.14f
    private const val ROW_GAP = 0.12f
    /** Logo 高、第一行文字高、第二行文字高（相对基准字号） */
    private const val LOGO_HF = 0.70f
    private const val LINE1_HF = 0.30f
    private const val LINE2_HF = 0.20f
    /** 宽高比超过该值的内置 Logo 视为「纯文字字标」（如 SAMSUNG），走横向同行排版 */
    private const val WORDMARK_RATIO = 2.5f

    private val textTypeface: Typeface by lazy {
        runCatching { Typeface.create("Microsoft YaHei", Typeface.BOLD) }
            .getOrNull() ?: Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    private val secondaryTypeface: Typeface by lazy {
        runCatching { Typeface.create("Microsoft YaHei", Typeface.NORMAL) }
            .getOrNull() ?: Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    }

    private enum class Source { LOGO, TEXT, EXIF }

    private data class Item(
        val source: Source,
        val text: String,
        val isBold: Boolean,
        val heightFactor: Float,
        val refW: Float
    )

    /** 三类各自的像素基准字号（短边 × 该类 scaleRatio）；带留白带钳制时整体等比缩小 */
    private class Metrics(minSide: Float) {
        var logo: Float = minSide * WatermarkSpec.DEFAULT_SCALE_RATIO
        var text: Float = minSide * WatermarkSpec.DEFAULT_SCALE_RATIO
        var exif: Float = minSide * WatermarkSpec.DEFAULT_SCALE_RATIO

        fun of(s: Source): Float = when (s) {
            Source.LOGO -> logo
            Source.TEXT -> text
            Source.EXIF -> exif
        }

        fun max(): Float = maxOf(logo, text, exif)

        fun scaleAll(k: Float) {
            logo *= k
            text *= k
            exif *= k
        }

        companion object {
            fun from(wm: WatermarkSpec, minSide: Float): Metrics = Metrics(minSide).apply {
                logo = minSide * wm.logoScaleRatio.coerceIn(0.02f, 0.8f)
                text = minSide * wm.textScaleRatio.coerceIn(0.02f, 0.8f)
                exif = minSide * wm.exifScaleRatio.coerceIn(0.02f, 0.8f)
            }
        }
    }

    /** 一行文字的实际像素尺寸 */
    private data class Row(val items: List<Item>, val w: Float, val h: Float)

    private data class BlockLayout(
        val logoH: Float,
        val logoW: Float,
        val row1: Row?,
        val row2: Row?,
        val blockW: Float,
        val blockH: Float,
        val twoRows: Boolean,
        /** 宽幅文字 Logo（如 SAMSUNG）走「Logo 与第一行文字同行」排版 */
        val wordmark: Boolean = false
    )

    private data class TextLayout(
        val row1: Row?,
        val row2: Row?,
        val blockW: Float,
        val blockH: Float
    )

    /**
     * 当前配置下水印块的总高度系数（基准字号 = 1）。
     * 设置页据此计算「大小」滑块上限，保证滑块整段行程都有可见变化。
     */
    fun layoutHeightFactor(wm: WatermarkSpec, hasLogo: Boolean): Float {
        if (hasLogo) {
            val r1 = (wm.authorOn && wm.text.isNotBlank()) || wm.deviceOn
            val r2 = wm.lensOn || (wm.timeOn && wm.timeText.isNotBlank())
            // v2.20：非 SIDES 的 Logo + 两行走同排排版，整块高度 = 第一行(与 Logo 取高) + 行距 + 第二行
            if (r1 && r2 && wm.blockAlign != WatermarkBlockAlign.SIDES) {
                return maxOf(LOGO_HF, LINE1_HF) + ROW_GAP + LINE2_HF
            }
            // 其余情况 Logo(0.70) 高于两行文字块(0.62)，整块高度由 Logo 决定
            return LOGO_HF
        }
        val r1 = if ((wm.authorOn && wm.text.isNotBlank()) || wm.deviceOn) LINE1_HF else 0f
        val r2 = if (wm.lensOn || (wm.timeOn && wm.timeText.isNotBlank())) LINE2_HF else 0f
        return r1 + r2 + if (r1 > 0f && r2 > 0f) ROW_GAP else 0f
    }

    /**
     * 「大小」滑块的可用上限。带边框时水印被留白带钳制，滑块若仍给到 0.6，
     * 后面大半段拉动完全没反应；把上限压到实际可用值即可全程有效。
     * @param bandRatio 留白带高度 / 画布短边，见 [FrameRenderer.watermarkBandRatio]；无边框传 1f
     */
    fun scaleRatioLimit(wm: WatermarkSpec, hasLogo: Boolean, bandRatio: Float): Float {
        val hf = layoutHeightFactor(wm, hasLogo).coerceAtLeast(0.15f)
        return (bandRatio.coerceIn(0f, 2f) * bandFill(hasLogo) / hf).coerceIn(0.04f, 0.6f)
    }

    /**
     * 图标 Logo 排版把 Logo 与文字叠在同一高度带，比旧版「Logo 行 + 文字行」更省高度；
     * 填带系数相应调小，保持原有视觉字号。
     */
    private fun bandFill(hasLogo: Boolean): Float = if (hasLogo) 0.56f else 0.82f

    /**
     * 无边框时图片底部留给水印的带高，相对短边。取 0.12f 与带边框模式的
     * 「细边 + 底部留白带」总高度（padding 1.55% + bottomBar 8%）同量级，
     * 既能放下两行水印，又不会遮住画面主体。
     */
    const val PLAIN_BAND_RATIO = 0.12f

    /**
     * 无边框成品图的水印入口：与带边框走**完全相同**的 [draw] 排版逻辑——
     * 同样把水印限制在底部留白带内、同样按带高钳制字号、同样支持三类对齐与位移。
     * 区别仅在于这条带是叠加在图片上的视觉区域，而不是画布外新增的白色留白。
     * 预览、取景框叠层、导出三处必须都调用这里，保证所见即所得。
     */
    fun drawOnPlainImage(
        canvas: Canvas,
        wm: WatermarkSpec,
        w: Int,
        h: Int,
        logo: Bitmap?,
        exif: ExifInfo? = null
    ) {
        if (!wm.enabled) return
        val minSide = minOf(w, h).toFloat()
        val bandH = (minSide * PLAIN_BAND_RATIO).coerceAtMost(h / 3f)
        val bottomBand = RectF(0f, h - bandH, w.toFloat(), h.toFloat())
        // 无边框不放上带；center 指向下带，CENTER 对齐即落在带正中
        draw(
            canvas, wm, w, h, logo,
            WatermarkBands(RectF(0f, 0f, 0f, 0f), bottomBand, bottomBand),
            exif
        )
    }

    /**
     * @param bands 非 null 表示带边框，水印只能落在留白带里；null 表示无边框
     * @param exif  本地 EXIF，用于生成机型 / 镜头行；不传时对应行留空
     */
    fun draw(
        canvas: Canvas,
        wm: WatermarkSpec,
        w: Int,
        h: Int,
        logo: Bitmap?,
        bands: WatermarkBands? = null,
        exif: ExifInfo? = null
    ) {
        if (!wm.enabled) return
        val minSide = minOf(w, h).toFloat()
        val alpha = (wm.opacity.coerceIn(0f, 1f) * 255).toInt()
        val m = Metrics.from(wm, minSide)
        val band = preferredBand(bands, minSide)
        val margin = minSide * wm.marginRatio.coerceIn(0f, 0.4f)
        val maxBlockW = (w - 2f * margin).coerceAtLeast(minSide * 0.2f)

        // 「两边对齐」是独立排版：Logo 贴左、文字贴右；组合不合法时已在 ViewModel 回落居中，
        // 这里再兜一层，避免旧存档里残留 SIDES 导致布局异常。
        if (wm.blockAlign == WatermarkBlockAlign.SIDES && logo != null && sidesValid(wm)) {
            drawSides(canvas, wm, w, h, logo, exif, minSide, m, band, margin, alpha)
        } else if (logo != null) {
            drawWithImageLogo(canvas, wm, w, h, logo, exif, minSide, m, band, margin, maxBlockW, alpha)
        } else {
            drawTextRows(canvas, wm, w, h, exif, minSide, m, band, margin, maxBlockW, alpha)
        }
    }

    // ================= 图标 Logo 排版 =================

    private fun drawWithImageLogo(
        canvas: Canvas,
        wm: WatermarkSpec,
        w: Int,
        h: Int,
        logo: Bitmap,
        exif: ExifInfo?,
        minSide: Float,
        m: Metrics,
        band: RectF?,
        margin: Float,
        maxBlockW: Float,
        alpha: Int
    ) {
        val line1 = buildLineOne(wm, exif, withLogo = true)
        val line2 = buildLineTwo(wm, exif)
        val wordmark = logo.width.toFloat() / logo.height.coerceAtLeast(1) >= WORDMARK_RATIO
        // v2.20：居中 / 左对齐 / 右对齐下，只要有 Logo 且有两行文字，就统一走
        // 「Logo 与第一行同排、第二行在两者下方居中」的排版；SIDES 保留原来的左右分栏。
        val inlineTwoRows = wm.blockAlign != WatermarkBlockAlign.SIDES &&
            line1.isNotEmpty() && line2.isNotEmpty()

        // 先按各自 scaleRatio 算真实尺寸，再对整块做一次钳制，三类相对比例保持不变
        fun currentLayout(): BlockLayout =
            if (wordmark || inlineTwoRows) wordmarkLayout(m, logo, line1, line2)
            else blockLayout(m, logo, line1, line2)

        var layout = currentLayout()
        if (band != null) {
            val maxH = band.height() * bandFill(true)
            if (layout.blockH > maxH) {
                m.scaleAll(maxH / layout.blockH)
                layout = currentLayout()
            }
        }
        if (layout.blockW > maxBlockW) {
            m.scaleAll(maxBlockW / layout.blockW)
            layout = currentLayout()
        }

        val gap = ITEM_GAP * m.max()
        val rowGap = ROW_GAP * m.max()
        // 通用设置的「整体位置」：整个水印块平移，三类微调在此之上各自叠加
        val blockDx = wm.blockOffsetX * minSide
        val blockDy = wm.blockOffsetY * minSide
        val centers = if (wm.tiled) {
            tiledCenters(w, h, m.max()).map { (cx, cy) -> cx + blockDx to cy + blockDy }
        } else {
            listOf(
                blockCenterX(wm.blockAlign, layout.blockW, margin, w) + blockDx to
                    (if (band != null) (band.top + band.bottom) / 2f else h - margin - layout.blockH / 2f) + blockDy
            )
        }

        val (lox, loy) = offsetOf(wm, Source.LOGO, minSide)
        val stageL = -maxBlockW / 2f
        val stageR = maxBlockW / 2f
        centers.forEach { (bx, by) ->
            canvas.save()
            canvas.translate(bx, by)
            // 整块只旋转一次：各元素自己的位移都在这个坐标系里，避免分别旋转造成歪斜
            canvas.rotate(wm.rotation)

            if (layout.wordmark) {
                // 文字字标（SAMSUNG 这类）：字标与第一行文字同排，第二行在其下方居中
                drawWordmark(canvas, wm, layout, m, logo, minSide, lox, loy, gap, rowGap, alpha)
            } else if (layout.twoRows) {
                // 两行文字：Logo 占左侧区域、两行文字占右侧区域，各区域内按自己的对齐方式摆放；
                // 左右互不相交，避免「左对齐 + 右对齐」时叠字。
                val left = -layout.blockW / 2f
                val right = left + layout.blockW
                val laneSplit = (left + maxOf(layout.logoW, (layout.blockW - gap) * 0.42f))
                    .coerceAtMost(right - gap)
                val logoCx = alignedCenter(wm.logoAlign, layout.logoW, left, laneSplit)
                val r1 = layout.row1!!
                val r2 = layout.row2!!
                val textL = laneSplit
                val textR = right
                drawLogoLocal(canvas, logo, logoCx + lox, loy, layout.logoH, alpha, wm)
                val textH = r1.h + rowGap + r2.h
                var y = -textH / 2f
                drawRowCentered(
                    canvas, r1, alignedCenter(rowAlign(r1, wm), r1.w, textL, textR),
                    y + r1.h / 2f, m, alpha, wm, minSide
                )
                y += r1.h + rowGap
                drawRowCentered(
                    canvas, r2, alignedCenter(rowAlign(r2, wm), r2.w, textL, textR),
                    y + r2.h / 2f, m, alpha, wm, minSide
                )
            } else {
                // 只有一行文字：Logo 在上、文字在下，各自按对齐方式落在整块宽度内
                val row = layout.row1 ?: layout.row2
                if (row != null) {
                    val totalH = layout.logoH + rowGap + row.h
                    val logoCx = alignedCenter(wm.logoAlign, layout.logoW, stageL, stageR)
                    drawLogoLocal(
                        canvas, logo, logoCx + lox,
                        -totalH / 2f + layout.logoH / 2f + loy,
                        layout.logoH, alpha, wm
                    )
                    drawRowCentered(
                        canvas, row, alignedCenter(rowAlign(row, wm), row.w, stageL, stageR),
                        totalH / 2f - row.h / 2f,
                        m, alpha, wm, minSide
                    )
                } else {
                    // 只开了 Logo，没有任何文字：Logo 按自己的对齐方式摆放
                    val logoCx = alignedCenter(wm.logoAlign, layout.logoW, stageL, stageR)
                    drawLogoLocal(canvas, logo, logoCx + lox, loy, layout.logoH, alpha, wm)
                }
            }
            canvas.restore()
        }
    }

    private fun blockLayout(m: Metrics, logo: Bitmap, line1: List<Item>, line2: List<Item>): BlockLayout {
        val r1 = layoutRow(line1, m)
        val r2 = layoutRow(line2, m)
        val logoH = LOGO_HF * m.logo
        val logoW = logoH * logo.width.toFloat() / logo.height.coerceAtLeast(1)
        val gap = ITEM_GAP * m.max()
        val rowGap = ROW_GAP * m.max()
        return when {
            r1 != null && r2 != null -> {
                val textH = r1.h + rowGap + r2.h
                BlockLayout(
                    logoH, logoW, r1, r2,
                    logoW + gap + maxOf(r1.w, r2.w),
                    maxOf(logoH, textH),
                    twoRows = true
                )
            }
            r1 != null || r2 != null -> {
                val r = r1 ?: r2!!
                BlockLayout(
                    logoH, logoW, r1, r2,
                    maxOf(logoW, r.w),
                    logoH + rowGap + r.h,
                    twoRows = false
                )
            }
            else -> BlockLayout(logoH, logoW, null, null, logoW, logoH, twoRows = false)
        }
    }

    /**
     * 纯文字字标排版：Logo 不再独占左列，而是与第一行文字同排（Logo 在左、文字紧随其右），
     * 第二行参数在整块下方居中，视觉与参考图的「SAMSUNG + 机型 / 参数」一致。
     */
    private fun wordmarkLayout(
        m: Metrics,
        logo: Bitmap,
        line1: List<Item>,
        line2: List<Item>
    ): BlockLayout {
        val r1 = layoutRow(line1, m)
        val r2 = layoutRow(line2, m)
        val logoH = LOGO_HF * m.logo
        val logoW = logoH * logo.width.toFloat() / logo.height.coerceAtLeast(1)
        val gap = ITEM_GAP * m.max()
        val rowGap = ROW_GAP * m.max()
        val row1W = logoW + (if (r1 != null) gap + r1.w else 0f)
        val row1H = maxOf(logoH, r1?.h ?: 0f)
        return BlockLayout(
            logoH = logoH,
            logoW = logoW,
            row1 = r1,
            row2 = r2,
            blockW = maxOf(row1W, r2?.w ?: 0f),
            blockH = row1H + (if (r2 != null) rowGap + r2.h else 0f),
            twoRows = r2 != null,
            wordmark = true
        )
    }

    private fun drawWordmark(
        canvas: Canvas,
        wm: WatermarkSpec,
        layout: BlockLayout,
        m: Metrics,
        logo: Bitmap,
        minSide: Float,
        lox: Float,
        loy: Float,
        gap: Float,
        rowGap: Float,
        alpha: Int
    ) {
        val r1 = layout.row1
        val r2 = layout.row2
        val row1W = layout.logoW + (if (r1 != null) gap + r1.w else 0f)
        val row1Left = -row1W / 2f
        val logoCx = row1Left + layout.logoW / 2f
        // Logo 与第一行共用同一垂直中心（v2.20：居中对齐），不再按各自顶部对齐
        val row1H = maxOf(layout.logoH, r1?.h ?: 0f)
        val row1Cy = -layout.blockH / 2f + row1H / 2f
        drawLogoLocal(
            canvas, logo, logoCx + lox, row1Cy + loy,
            layout.logoH, alpha, wm
        )
        if (r1 != null) {
            val rowLeft = row1Left + layout.logoW + gap
            drawRowCentered(
                canvas, r1,
                alignedCenter(rowAlign(r1, wm), r1.w, rowLeft, row1Left + row1W),
                row1Cy,
                m, alpha, wm, minSide
            )
        }
        if (r2 != null) {
            val y = -layout.blockH / 2f + row1H + rowGap + r2.h / 2f
            // 第二行也要遵守该类水印的对齐设置（v2.14 的三类对齐），否则字标排版下左/右对齐无效
            drawRowCentered(
                canvas, r2,
                alignedCenter(rowAlign(r2, wm), r2.w, -layout.blockW / 2f, layout.blockW / 2f),
                y, m, alpha, wm, minSide
            )
        }
    }

    private fun buildLineOne(wm: WatermarkSpec, exif: ExifInfo?, withLogo: Boolean): List<Item> {
        val out = mutableListOf<Item>()
        if (wm.authorOn && wm.text.isNotBlank()) {
            out += textItem(Source.TEXT, wm.text.trim(), bold = true, hf = LINE1_HF)
        }
        if (wm.deviceOn) {
            // v2.21：文本框里用户改过的机型原样使用（不再剥品牌前缀）；
            // 空值时才回落到 EXIF 自动识别，有 Logo 时只留机型后缀避免品牌重复。
            val device = wm.deviceText.trim().ifBlank {
                if (withLogo) ExifReader.formatModelLine(exif) else ExifReader.formatDeviceLine(exif)
            }
            if (device.isNotBlank()) {
                out += textItem(Source.EXIF, device, bold = !withLogo, hf = LINE1_HF)
            }
        }
        return out
    }

    private fun buildLineTwo(wm: WatermarkSpec, exif: ExifInfo?): List<Item> {
        val out = mutableListOf<Item>()
        if (wm.lensOn) {
            ExifReader.formatAutoSecondLine(exif).takeIf { it.isNotBlank() }?.let {
                out += textItem(Source.EXIF, it, bold = false, hf = LINE2_HF)
            }
        }
        if (wm.timeOn && wm.timeText.isNotBlank()) {
            out += textItem(Source.TEXT, wm.timeText.trim(), bold = false, hf = LINE2_HF)
        }
        return out
    }

    private fun layoutRow(items: List<Item>, m: Metrics): Row? {
        if (items.isEmpty()) return null
        val w = items.sumOf { (it.refW / REF * m.of(it.source)).toDouble() }.toFloat() +
            ITEM_GAP * m.max() * (items.size - 1).coerceAtLeast(0)
        val h = items.maxOf { it.heightFactor * m.of(it.source) }
        return Row(items, w, h)
    }

    /**
     * 一行内的所有项围绕 [centerX] 居中排布：整行作为一个整体居中，
     * 每个项再叠加自己来源（文字 / 参数）的独立微调位移。
     */
    private fun drawRowCentered(
        canvas: Canvas,
        row: Row,
        centerX: Float,
        centerY: Float,
        m: Metrics,
        alpha: Int,
        wm: WatermarkSpec,
        minSide: Float
    ) {
        var x = centerX - row.w / 2f
        row.items.forEach { item ->
            val iw = item.refW / REF * m.of(item.source)
            val ih = item.heightFactor * m.of(item.source)
            val (ox, oy) = offsetOf(wm, item.source, minSide)
            val tf = if (item.isBold) textTypeface else secondaryTypeface
            canvas.drawText(
                item.text,
                x + iw / 2f + ox,
                centerY + ih * 0.35f + oy,
                textPaint(ih, alpha, wm, tf, Paint.Align.CENTER)
            )
            x += iw + ITEM_GAP * m.max()
        }
    }

    // ================= 纯文字排版（参考相机水印两行居中）=================

    private fun drawTextRows(
        canvas: Canvas,
        wm: WatermarkSpec,
        w: Int,
        h: Int,
        exif: ExifInfo?,
        minSide: Float,
        m: Metrics,
        band: RectF?,
        margin: Float,
        maxBlockW: Float,
        alpha: Int
    ) {
        val line1 = buildLineOne(wm, exif, withLogo = false)
        val line2 = buildLineTwo(wm, exif)
        var layout = textLayout(m, line1, line2)
        if (layout.row1 == null && layout.row2 == null) return

        if (band != null) {
            val maxH = band.height() * bandFill(false)
            if (layout.blockH > maxH) {
                m.scaleAll(maxH / layout.blockH)
                layout = textLayout(m, line1, line2)
            }
        }
        if (layout.blockW > maxBlockW) {
            m.scaleAll(maxBlockW / layout.blockW)
            layout = textLayout(m, line1, line2)
        }

        val rowGap = ROW_GAP * m.max()
        val blockDx = wm.blockOffsetX * minSide
        val blockDy = wm.blockOffsetY * minSide
        val centers = if (wm.tiled) {
            tiledCenters(w, h, m.max()).map { (cx, cy) -> cx + blockDx to cy + blockDy }
        } else {
            listOf(
                blockCenterX(wm.blockAlign, layout.blockW, margin, w) + blockDx to
                    (if (band != null) (band.top + band.bottom) / 2f else h - margin - layout.blockH / 2f) + blockDy
            )
        }

        centers.forEach { (bx, by) ->
            canvas.save()
            canvas.translate(bx, by)
            canvas.rotate(wm.rotation)
            var y = -layout.blockH / 2f
            val stageL = -maxBlockW / 2f
            val stageR = maxBlockW / 2f
            listOf(layout.row1, layout.row2).forEach { row ->
                if (row == null) return@forEach
                val cx = alignedCenter(rowAlign(row, wm), row.w, stageL, stageR)
                drawRowCentered(canvas, row, cx, y + row.h / 2f, m, alpha, wm, minSide)
                y += row.h + rowGap
            }
            canvas.restore()
        }
    }

    private fun textLayout(m: Metrics, line1: List<Item>, line2: List<Item>): TextLayout {
        val r1 = layoutRow(line1, m)
        val r2 = layoutRow(line2, m)
        val rowGap = if (r1 != null && r2 != null) ROW_GAP * m.max() else 0f
        return TextLayout(
            r1, r2,
            maxOf(r1?.w ?: 0f, r2?.w ?: 0f),
            (r1?.h ?: 0f) + rowGap + (r2?.h ?: 0f)
        )
    }

    // ================= 公共绘制辅助 =================

    private fun textItem(source: Source, text: String, bold: Boolean, hf: Float): Item {
        val tf = if (bold) textTypeface else secondaryTypeface
        return Item(
            source = source,
            text = text,
            isBold = bold,
            heightFactor = hf,
            refW = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = hf * REF
                typeface = tf
            }.measureText(text)
        )
    }

    private fun textPaint(
        textH: Float,
        alpha: Int,
        wm: WatermarkSpec,
        typeface: Typeface,
        align: Paint.Align
    ): Paint {
        // 底色反相：白字用暗投影、黑字用亮投影，保证任意照片底色下文字都清晰
        val lum = (wm.textColor shr 16 and 0xFF) * 0.299f +
            (wm.textColor shr 8 and 0xFF) * 0.587f +
            (wm.textColor and 0xFF) * 0.114f
        val shadow = if (lum < 128f) 0x99FFFFFF.toInt() else 0x88000000.toInt()
        return Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = wm.textColor
            textSize = textH
            textAlign = align
            this.typeface = typeface
            this.alpha = alpha
            if (wm.shadowOn) setShadowLayer(textH * 0.08f, 0f, textH * 0.04f, shadow)
        }
    }

    private fun drawLogoLocal(
        canvas: Canvas,
        logo: Bitmap,
        cx: Float,
        cy: Float,
        height: Float,
        alpha: Int,
        wm: WatermarkSpec
    ) {
        val w = height * logo.width.toFloat() / logo.height.coerceAtLeast(1)
        val shadow = if (wm.logoTint == LogoTint.BLACK) 0x99FFFFFF.toInt() else 0x88000000.toInt()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            this.alpha = alpha
            if (wm.shadowOn) setShadowLayer(height * 0.06f, 0f, height * 0.03f, shadow)
        }
        canvas.drawBitmap(
            logo, null,
            RectF(cx - w / 2f, cy - height / 2f, cx + w / 2f, cy + height / 2f),
            paint
        )
    }

    private fun preferredBand(bands: WatermarkBands?, minSide: Float): RectF? {
        val minBandH = minSide * 0.03f
        return when {
            bands?.bottom != null && bands.bottom.height() >= minBandH -> bands.bottom
            bands?.top != null && bands.top.height() >= minBandH -> bands.top
            else -> null
        }
    }

    private fun offsetOf(wm: WatermarkSpec, source: Source, minSide: Float): Pair<Float, Float> =
        when (source) {
            Source.LOGO -> wm.logoOffsetX * minSide to wm.logoOffsetY * minSide
            Source.TEXT -> wm.textOffsetX * minSide to wm.textOffsetY * minSide
            Source.EXIF -> wm.exifOffsetX * minSide to wm.exifOffsetY * minSide
        }

    /** 取一行里第一个非居中来源的对齐方式；一行内混合来源时以先出现的设置为准 */
    private fun rowAlign(row: Row, wm: WatermarkSpec): WatermarkAlign =
        row.items.firstOrNull { alignOf(it.source, wm) != WatermarkAlign.CENTER }
            ?.let { alignOf(it.source, wm) } ?: WatermarkAlign.CENTER

    private fun alignOf(source: Source, wm: WatermarkSpec): WatermarkAlign = when (source) {
        Source.LOGO -> wm.logoAlign
        Source.TEXT -> wm.textAlign
        Source.EXIF -> wm.exifAlign
    }

    /**
     * 把宽度为 [ownW] 的元素按 [align] 放进水平区间 [left, right]，返回其中心 x。
     * 元素比区间还宽时退化为区间居中，绝不越界，保证各类水印即使对齐冲突也不重叠。
     */
    private fun alignedCenter(
        align: WatermarkAlign,
        ownW: Float,
        left: Float,
        right: Float
    ): Float {
        val w = ownW.coerceAtMost((right - left).coerceAtLeast(0f))
        return when (align) {
            WatermarkAlign.LEFT -> left + w / 2f
            WatermarkAlign.CENTER -> (left + right) / 2f
            WatermarkAlign.RIGHT -> right - w / 2f
        }
    }

    /** 通用整体对齐：把整块水印锚定到照片的居中 / 左边 / 右边 */
    private fun blockCenterX(
        align: WatermarkBlockAlign,
        blockW: Float,
        margin: Float,
        canvasW: Int
    ): Float {
        val left = margin.coerceAtMost(canvasW / 2f)
        val right = (canvasW - margin).coerceAtLeast(canvasW / 2f)
        if (blockW >= right - left) return canvasW / 2f
        return when (align) {
            WatermarkBlockAlign.LEFT -> left + blockW / 2f
            WatermarkBlockAlign.RIGHT -> right - blockW / 2f
            WatermarkBlockAlign.CENTER, WatermarkBlockAlign.SIDES -> canvasW / 2f
        }
    }

    private fun sidesValid(wm: WatermarkSpec): Boolean {
        val hasText = wm.authorOn || wm.timeOn
        val hasExif = wm.deviceOn || wm.lensOn
        return wm.logoPath.isNotBlank() && hasText != hasExif
    }

    /** 两边对齐：Logo 贴照片左边，文字 / 照片参数贴照片右边，垂直居中对齐 */
    private fun drawSides(
        canvas: Canvas,
        wm: WatermarkSpec,
        w: Int,
        h: Int,
        logo: Bitmap,
        exif: ExifInfo?,
        minSide: Float,
        m: Metrics,
        band: RectF?,
        margin: Float,
        alpha: Int
    ) {
        fun rows(): Pair<Row?, Row?> =
            layoutRow(buildLineOne(wm, exif, withLogo = true), m) to
                layoutRow(buildLineTwo(wm, exif), m)

        var (r1, r2) = rows()
        var logoH = LOGO_HF * m.logo
        var logoW = logoH * logo.width.toFloat() / logo.height.coerceAtLeast(1)
        var gap = ITEM_GAP * m.max()
        var rowGap = ROW_GAP * m.max()
        val availableW = (w - 2f * margin).coerceAtLeast(minSide * 0.2f)
        val neededW = logoW + gap + maxOf(r1?.w ?: 0f, r2?.w ?: 0f)
        if (neededW > availableW) {
            m.scaleAll(availableW / neededW)
            val scaled = rows()
            r1 = scaled.first
            r2 = scaled.second
            logoH = LOGO_HF * m.logo
            logoW = logoH * logo.width.toFloat() / logo.height.coerceAtLeast(1)
            gap = ITEM_GAP * m.max()
            rowGap = ROW_GAP * m.max()
        }

        val textH = (r1?.h ?: 0f) + (if (r1 != null && r2 != null) rowGap else 0f) + (r2?.h ?: 0f)
        val contentH = maxOf(logoH, textH)
        val baseCy = (if (band != null) (band.top + band.bottom) / 2f else h - margin - contentH / 2f) +
            wm.blockOffsetY * minSide
        // 整体位置的水平位移同样作用于两边对齐：Logo 与文字一起平移，不破坏左右分离
        val blockDx = wm.blockOffsetX * minSide

        drawLogoLocal(
            canvas, logo,
            margin + logoW / 2f + blockDx + wm.logoOffsetX * minSide,
            baseCy + wm.logoOffsetY * minSide,
            logoH, alpha, wm
        )

        var y = baseCy - textH / 2f
        listOf(r1, r2).forEach { row ->
            if (row == null) return@forEach
            val source = row.items.firstOrNull()?.source ?: Source.TEXT
            val (ox, oy) = offsetOf(wm, source, minSide)
            // 两边对齐的本义就是「Logo 左 / 文字右」，文字固定贴右边（不能被单类对齐改成居中）
            drawRowCentered(
                canvas, row, w - margin + blockDx - row.w / 2f + ox, y + row.h / 2f + oy,
                m, alpha, wm, minSide
            )
            y += row.h + rowGap
        }
    }

    /** 平铺是用户主动要的「铺满整图」，不受留白带约束；限制总数避免极端参数卡死 */
    private fun tiledCenters(w: Int, h: Int, base: Float): List<Pair<Float, Float>> {
        val step = (base * 3.2f).coerceAtLeast(1f)
        val out = mutableListOf<Pair<Float, Float>>()
        var row = 0
        var y = step / 2f
        while (y < h + step && out.size < 400) {
            val xOff = if (row % 2 == 0) step / 2f else 0f
            var x = step / 2f + xOff
            while (x < w + step && out.size < 400) {
                out += x to y
                x += step
            }
            y += step
            row++
        }
        return out
    }
}
