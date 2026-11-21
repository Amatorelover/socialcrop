package com.lihoo.socialcrop.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlin.math.roundToInt

/**
 * 从 Uri 解码位图：自动下采样避免 OOM，并按 EXIF 方向旋转。
 * 使用 Photo Picker 返回的 Uri，无需任何存储权限。
 */
fun decodeBitmap(context: Context, uri: Uri, maxEdge: Int = 4096): Bitmap? {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    runCatching { resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } }
    val (w, h) = bounds.outWidth to bounds.outHeight
    if (w <= 0 || h <= 0) return null

    val longest = maxOf(w, h)
    // inSampleSize 实际只取 2 的幂；旧版写成 max(1, longest/maxEdge) 整除，
    // 4096–8191px 的图会得到 1（等于完全没缩），12000px 的图也只缩到 6000px。
    // 这里让采样后尺寸仍 >= maxEdge（避免 5000px 被 sample=2 一刀砍到 2500px），
    // 再由 inDensity/inTargetDensity 让解码器一次精确缩到 maxEdge，不额外分配中间位图。
    var sample = 1
    while (longest / (sample * 2) >= maxEdge) sample *= 2
    val opts = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.ARGB_8888
        if (longest > maxEdge) {
            inScaled = true
            inDensity = (longest / sample).coerceAtLeast(1)
            inTargetDensity = maxEdge
        } else {
            inScaled = false
        }
    }
    // 解码超大图可能抛 OutOfMemoryError（是 Error 而非 Exception），必须兜住，
    // 否则从相册选一张高分辨率照片就会把整个 App 带崩。
    val decoded = runCatching {
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }.getOrNull() ?: return null

    // 少数 ROM 的 decoder 会忽略 inDensity，这里再兜一层，保证最长边真的不超上限
    val raw = if (maxOf(decoded.width, decoded.height) > maxEdge) {
        val k = maxEdge.toFloat() / maxOf(decoded.width, decoded.height)
        val b = runCatching {
            Bitmap.createScaledBitmap(
                decoded,
                (decoded.width * k).roundToInt().coerceAtLeast(1),
                (decoded.height * k).roundToInt().coerceAtLeast(1),
                true
            )
        }.getOrNull()
        if (b != null) {
            decoded.recycle()
            b
        } else decoded
    } else decoded

    val rotation = getExifRotation(context, uri)
    if (rotation == 0) return raw
    // 旋转会再分配一张同等大小的位图，同样可能 OOM；失败时退回未旋转的图而不是崩溃
    return runCatching {
        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        val rotated = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
        raw.recycle()
        rotated
    }.getOrElse {
        raw.recycle()
        null
    }
}

/**
 * 水印 Logo 在画面里最多只有几百像素，按最长边下采样解码。
 * 用户从相册选到 6000×6000 的图时，全尺寸 decodeFile 会直接 OOM。
 */
fun decodeLogo(path: String, maxEdge: Int = 512): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    val (w, h) = bounds.outWidth to bounds.outHeight
    if (w <= 0 || h <= 0) return null
    var sample = 1
    while (maxOf(w, h) / sample > maxEdge) sample *= 2
    val opts = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return runCatching { BitmapFactory.decodeFile(path, opts) }.getOrNull()
}

private fun getExifRotation(context: Context, uri: Uri): Int {
    return try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            when (ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } ?: 0
    } catch (_: Exception) {
        0
    }
}
