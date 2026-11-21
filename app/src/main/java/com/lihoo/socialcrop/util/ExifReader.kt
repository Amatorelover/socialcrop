package com.lihoo.socialcrop.util

import android.content.Context
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.InputStream

/**
 * 读取照片 EXIF（拍摄参数），完全本地、不联网、不需要任何权限。
 * 仅从用户用系统选图器挑入的图片里读取其本身携带的元信息。
 *
 * 注意：截图、微信/QQ/WhatsApp 转发压缩、二次编辑保存的图，EXIF 基本被剥离，读不到属正常。
 * GPS 属于敏感信息，这里只解析、默认不展示、绝不上传。
 */
data class ExifInfo(
    val make: String? = null,
    val model: String? = null,
    val fNumber: Double? = null,
    val exposureTime: Double? = null,
    val iso: Int? = null,
    val focalLength: Double? = null,
    val dateTime: String? = null,
    val gpsLat: Double? = null,
    val gpsLon: Double? = null
) {
    /** 是否至少读到了一项拍摄参数 */
    val available: Boolean
        get() = listOfNotNull(make, model, fNumber, exposureTime, iso, focalLength, dateTime).isNotEmpty()

    val hasGps: Boolean
        get() = gpsLat != null && gpsLon != null
}

object ExifReader {

    fun read(context: Context, uri: Uri): ExifInfo? {
        return runCatching {
            val resolver = context.applicationContext.contentResolver
            var stream: InputStream? = null
            val exif = try {
                stream = resolver.openInputStream(uri)
                stream?.let { ExifInterface(it) }
            } finally {
                runCatching { stream?.close() }
            } ?: return@runCatching null

            val make = exif.getAttribute(ExifInterface.TAG_MAKE)?.trim().takeIf { !it.isNullOrBlank() }
            val model = exif.getAttribute(ExifInterface.TAG_MODEL)?.trim().takeIf { !it.isNullOrBlank() }
            val fNumber = exif.getAttributeDouble(ExifInterface.TAG_F_NUMBER, Double.NaN).takeIf { !it.isNaN() }
            val exposureTime = exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, Double.NaN).takeIf { !it.isNaN() }
            val iso = exif.getAttributeInt(ExifInterface.TAG_ISO_SPEED_RATINGS, 0).takeIf { it != 0 }
                ?: exif.getAttributeInt(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, 0).takeIf { it != 0 }
            val focalLength = exif.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, Double.NaN).takeIf { !it.isNaN() }
            val dateTime = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
            val coords = exif.latLong
            val lat = coords?.getOrNull(0)
            val lon = coords?.getOrNull(1)

            ExifInfo(
                make = make,
                model = model,
                fNumber = fNumber,
                exposureTime = exposureTime,
                iso = iso,
                focalLength = focalLength,
                dateTime = dateTime?.trim(),
                gpsLat = lat,
                gpsLon = lon
            )
        }.getOrNull()
    }

    /**
     * 把拍摄参数拼成水印文字，例如：
     * "iPhone 16 Pro · f/1.8 · 1/120s · ISO64 · 24mm"
     * 当 showGps 为真且存在 GPS 时，末尾追加坐标（如 "31.230°N 121.474°E"）。
     * 无拍摄参数时返回空串。
     */
    fun formatWatermarkText(info: ExifInfo?, showGps: Boolean): String {
        if (info == null || !info.available) return ""
        val parts = mutableListOf<String>()
        val device = buildDevice(info.make, info.model)
        if (device.isNotBlank()) parts += device
        info.fNumber?.let { parts += "f/${formatNum(it)}" }
        info.exposureTime?.let { parts += formatExposure(it) }
        info.iso?.let { parts += "ISO$it" }
        info.focalLength?.let { parts += "${formatNum(it)}mm" }
        if (showGps && info.hasGps) {
            info.gpsLat?.let { lat -> info.gpsLon?.let { lon -> parts += formatGps(lat, lon) } }
        }
        return parts.joinToString(" · ")
    }

    private fun buildDevice(make: String?, model: String?): String {
        if (model.isNullOrBlank()) return make ?: ""
        // 多数机型 make 已包含在 model 中，避免 "Apple iPhone 16 Pro" 式重复
        return if (make != null && model.startsWith(make, ignoreCase = true)) model
        else listOfNotNull(make, model).joinToString(" ")
    }

    private fun formatNum(v: Double): String =
        if (v % 1.0 == 0.0) v.toInt().toString()
        else String.format("%.1f", v).trimEnd('0').trimEnd('.')

    private fun formatExposure(t: Double): String = when {
        t >= 1.0 -> {
            val s = if (t % 1.0 == 0.0) t.toInt().toString() else String.format("%.1f", t)
            "${s}s"
        }
        // t == 0（B 门 / 部分相机）时 1/t 会得到 Infinity，toInt() 变成 2147483647
        t > 0.0 -> "1/${(1.0 / t).toInt()}s"
        else -> "${t}s"
    }

    private fun formatGps(lat: Double, lon: Double): String {
        val ns = if (lat >= 0) "N" else "S"
        val ew = if (lon >= 0) "E" else "W"
        return String.format("%.3f°%s %.3f°%s", kotlin.math.abs(lat), ns, kotlin.math.abs(lon), ew)
    }
}
