package com.lihoo.socialcrop.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.lihoo.socialcrop.data.LogoTint
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 安全回收「由 src 派生」的位图。
 *
 * Android 的 `Bitmap.createBitmap(src, 0, 0, src.width, src.height)` 与
 * `Bitmap.createScaledBitmap(src, src.width, src.height, …)` 在尺寸完全一致时会
 * **直接返回 src 本身**（共享同一块 native 像素）。此时若照常 `recycle()`，
 * 就会把 ViewModel 持有的源图一起销毁——后续所有预览/导出都拿到已回收的位图，
 * 表现为「预览不可用」甚至直接崩溃。这里统一做引用相等性判断，杜绝这类误伤。
 */
fun recycleDerived(derived: Bitmap?, src: Bitmap) {
    if (derived !== null && derived !== src && !derived.isRecycled) derived.recycle()
}

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
            // b 才是要保留的图；只有 decoded 与它不是同一个对象时才回收 decoded
            recycleDerived(decoded, b)
            b
        } else decoded
    } else decoded

    val rotation = getExifRotation(context, uri)
    if (rotation == 0) return raw
    // 旋转会再分配一张同等大小的位图，同样可能 OOM；失败时退回未旋转的图而不是崩溃
    return runCatching {
        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        val rotated = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
        recycleDerived(raw, rotated)
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
fun decodeLogo(
    path: String,
    maxEdge: Int = 512,
    tint: LogoTint = LogoTint.ORIGINAL
): Bitmap? {
    // 内置品牌 Logo 随包附带一份去背白模（<id>_mono.png），选中时一起复制到私有目录；
    // 单色化时用白模而不是原图，避免 GoPro/Lenovo/vivo/ZEISS 这类整块底色被刷成一坨。
    val useMono = tint != LogoTint.ORIGINAL
    val source = if (useMono) {
        val mono = File(File(path).parentFile, File(path).nameWithoutExtension + "_mono.png")
        if (mono.exists()) mono.absolutePath else path
    } else path

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(source, bounds)
    val (w, h) = bounds.outWidth to bounds.outHeight
    if (w <= 0 || h <= 0) return null
    var sample = 1
    while (maxOf(w, h) / sample > maxEdge) sample *= 2
    val opts = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    val decoded = runCatching { BitmapFactory.decodeFile(source, opts) }.getOrNull() ?: return null
    if (tint == LogoTint.ORIGINAL) return decoded
    return tintLogo(
        decoded,
        if (tint == LogoTint.WHITE) Color.WHITE else Color.BLACK,
        stripBox = !File(source).name.endsWith("_mono.png")
    )
}

/** 从 APK assets 读取内置品牌 Logo 的缩略图（降采样，仅用于选择网格）。 */
fun decodeBrandLogoAsset(context: Context, id: String): Bitmap? = runCatching {
    context.assets.open("brand_logos/$id.png").use { input ->
        BitmapFactory.decodeStream(input, null, BitmapFactory.Options().apply { inSampleSize = 4 })
    }
}.getOrNull()

/** 单色化：只替换 RGB、原样保留 alpha，透明背景和边缘抗锯齿都不受影响。
 *  stripBox=true 时对相册上传的 Logo 先去掉solid底色块，再上色，避免整块变纯黑/纯白。 */
private fun tintLogo(src: Bitmap, color: Int, stripBox: Boolean): Bitmap {
    val w = src.width
    val h = src.height
    val pixels = IntArray(w * h)
    src.getPixels(pixels, 0, w, 0, 0, w, h)
    val rgb = color and 0x00FFFFFF

    val boxColor = if (stripBox) detectCornerColor(pixels, w, h) else null
    for (i in pixels.indices) {
        val a = pixels[i] and 0xFF000000.toInt()
        if (a ushr 24 <= 20) continue
        if (boxColor != null && colorClose(pixels[i], boxColor, 45)) {
            pixels[i] = 0 //  solid 底色 → 透明，不参与单色化
        } else {
            pixels[i] = a or rgb
        }
    }
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    out.setPixels(pixels, 0, w, 0, 0, w, h)
    src.recycle()
    return out
}

/** 四角若有三个以上同色的不透明像素，认定为 solid 底色块（内置 Logo 已离线去背，不走这里）。 */
private fun detectCornerColor(pixels: IntArray, w: Int, h: Int): Int? {
    val pts = intArrayOf(0, w - 1, (h - 1) * w, h * w - 1)
    var opaque = 0
    var ref = 0
    for (idx in pts) {
        val px = pixels[idx]
        if (px ushr 24 > 140) {
            opaque++
            if (opaque == 1) ref = px else if (!colorClose(px, ref, 40)) return null
        }
    }
    return if (opaque >= 3) ref else null
}

private fun colorClose(a: Int, b: Int, tol: Int): Boolean =
    abs((a shr 16 and 0xFF) - (b shr 16 and 0xFF)) <= tol &&
        abs((a shr 8 and 0xFF) - (b shr 8 and 0xFF)) <= tol &&
        abs((a and 0xFF) - (b and 0xFF)) <= tol

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
