package com.lihoo.socialcrop.domain

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.lihoo.socialcrop.data.WatermarkSpec
import com.lihoo.socialcrop.util.ExifInfo
import com.lihoo.socialcrop.util.recycleDerived
import kotlin.math.roundToInt

/**
 * 纯 Bitmap 图像处理引擎：按比例裁剪 + 长图切格。
 * 不依赖任何 Android UI / 框架，便于单测。
 */
object ImageProcessor {

    /** 输出位图最长边上限：极端比例下 outH 可能是 10 万像素，一次分配数百 MB 直接 OOM */
    private const val MAX_OUTPUT_EDGE = 4096

    /**
     * 按源图坐标系下的裁切矩形，输出指定分辨率的位图（覆盖拉伸至目标尺寸）。
     */
    fun cropRegion(src: Bitmap, crop: RectF, outW: Int, outH: Int): Bitmap {
        // 四个边界都夹进源图范围，避免外部传入越界矩形时 createBitmap 抛异常
        val left = crop.left.roundToInt().coerceIn(0, (src.width - 1).coerceAtLeast(0))
        val top = crop.top.roundToInt().coerceIn(0, (src.height - 1).coerceAtLeast(0))
        val right = crop.right.roundToInt().coerceIn(left + 1, src.width)
        val bottom = crop.bottom.roundToInt().coerceIn(top + 1, src.height)
        val w = right - left
        val h = bottom - top

        val reqW = outW.coerceAtLeast(1)
        val reqH = outH.coerceAtLeast(1)
        val longest = maxOf(reqW, reqH).toFloat()
        val cap = if (longest > MAX_OUTPUT_EDGE) MAX_OUTPUT_EDGE.toFloat() / longest else 1f
        val dstW = (reqW * cap).roundToInt().coerceAtLeast(1)
        val dstH = (reqH * cap).roundToInt().coerceAtLeast(1)

        val region = Bitmap.createBitmap(src, left, top, w, h)
        val out = Bitmap.createBitmap(dstW, dstH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
        canvas.drawBitmap(region, null, RectF(0f, 0f, dstW.toFloat(), dstH.toFloat()), paint)
        // 裁切矩形覆盖整幅图时 createBitmap 会直接返回 src 本身；
        // 若照常 recycle 就会销毁 ViewModel 的源图，使后续预览/导出全部失效甚至崩溃。
        recycleDerived(region, src)
        return out
    }

    /**
     * 切格方案：整数格宽/格高 + 居中裁切矩形。
     * 预览网格线、编辑页提示与真正切图共用同一份 plan，保证三者一致。
     */
    data class SlicePlan(
        val cols: Int,
        val rows: Int,
        val cellW: Int,
        val cellH: Int,
        val cropLeft: Int,
        val cropTop: Int
    ) {
        val gridW: Int get() = cols * cellW
        val gridH: Int get() = rows * cellH
        val count: Int get() = cols * rows
    }

    /** 单个网格维度的安全上限，防止超长图一次生成上百张位图导致 OOM */
    const val MAX_COLS = 6
    const val MAX_ROWS = 20

    /**
     * 显式行列网格（v1.8.0）：把原图等分为 cols 列 × rows 行。
     * 格宽高取整除值，居中放置，只丢弃不能整除的零头像素（最多 cols-1 / rows-1 px），
     * 不拉伸、不上采样、不按任何平台比例二次裁切 —— 每格比例由原图与行列数自然决定。
     */
    fun planGrid(srcW: Int, srcH: Int, cols: Int, rows: Int): SlicePlan {
        val w = srcW.coerceAtLeast(1)
        val h = srcH.coerceAtLeast(1)
        // 列/行数不能超过图本身的像素数，否则 gridW > w 会算出负的 cropLeft，
        // createBitmap(src, x<0, ...) 直接抛 IllegalArgumentException
        val c = cols.coerceIn(1, minOf(MAX_COLS, w))
        val r = rows.coerceIn(1, minOf(MAX_ROWS, h))
        val cellW = (w / c).coerceIn(1, w)
        val cellH = (h / r).coerceIn(1, h)
        val gridW = cellW * c
        val gridH = cellH * r
        return SlicePlan(
            c, r, cellW, cellH,
            ((w - gridW) / 2).coerceAtLeast(0),
            ((h - gridH) / 2).coerceAtLeast(0)
        )
    }

    /** 按 plan 从原图 1:1 取块：零重采样、边界严格相邻、发布后拼回无缝 */
    fun sliceGrid(
        src: Bitmap,
        cols: Int,
        rows: Int,
        watermark: WatermarkSpec? = null,
        logo: Bitmap? = null,
        exif: ExifInfo? = null
    ): List<Bitmap> {
        val plan = planGrid(src.width, src.height, cols, rows)
        val result = ArrayList<Bitmap>(plan.count)
        for (r in 0 until plan.rows) {
            for (c in 0 until plan.cols) {
                val x = plan.cropLeft + c * plan.cellW
                val y = plan.cropTop + r * plan.cellH
                val raw = Bitmap.createBitmap(src, x, y, plan.cellW, plan.cellH)
                // 1×1 切格时 raw 会与 src 引用同一对象；只在确有需要且不是 src 时才回收
                if (watermark != null && watermark.enabled) {
                    val out = Bitmap.createBitmap(plan.cellW, plan.cellH, Bitmap.Config.ARGB_8888)
                    val cv = Canvas(out)
                    cv.drawBitmap(raw, 0f, 0f, null)
                    WatermarkRenderer.drawOnPlainImage(
                        cv, watermark, plan.cellW, plan.cellH, logo, exif
                    )
                    recycleDerived(raw, src)
                    result.add(out)
                } else {
                    result.add(raw)
                }
            }
        }
        return result
    }
}
