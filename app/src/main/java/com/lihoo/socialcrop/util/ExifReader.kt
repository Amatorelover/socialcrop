package com.lihoo.socialcrop.util

import android.content.Context
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 读取照片 EXIF（拍摄参数），完全本地、不联网、不需要任何权限。
 * 仅从用户用系统选图器挑入的图片里读取其本身携带的元信息。
 *
 * 注意：截图、微信/QQ/WhatsApp 转发压缩、二次编辑保存的图，EXIF 基本被剥离，读不到属正常。
 * v2.7.0 起彻底不解析 GPS 坐标：水印不再提供地点行，照片位置信息连本机都不读取。
 */
data class ExifInfo(
    val make: String? = null,
    val model: String? = null,
    val fNumber: Double? = null,
    val exposureTime: Double? = null,
    val iso: Int? = null,
    val focalLength: Double? = null,
    val dateTime: String? = null
) {
    /** 是否至少读到了一项拍摄参数 */
    val available: Boolean
        get() = listOfNotNull(make, model, fNumber, exposureTime, iso, focalLength, dateTime).isNotEmpty()
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
            ExifInfo(
                make = make,
                model = model,
                fNumber = fNumber,
                exposureTime = exposureTime,
                iso = iso,
                focalLength = focalLength,
                dateTime = dateTime?.trim()
            )
        }.getOrNull()
    }

    /**
     * 无 Logo 时第一行自动文字：品牌 + 机型，如 "SAMSUNG S9280" / "Apple iPhone 16 Pro"。
     * 机型首字母统一大写，避免个别厂商 EXIF 写入全小写型号时显得突兀。
     */
    fun formatDeviceLine(info: ExifInfo?): String {
        if (info == null) return ""
        return capitalizeFirst(buildDevice(info.make, info.model))
    }

    /**
     * 有 Logo 时第一行只留机型后缀，把品牌前缀全部剥掉，避免和 Logo 重复：
     * "iPhone 16 Pro" → "16 Pro"、"HUAWEI P60 Pro" → "P60 Pro"、"SM-S9280" → "S9280"。
     * 首字母统一大写（"s9280" → "S9280"），所有品牌一致。
     */
    fun formatModelLine(info: ExifInfo?): String {
        if (info == null) return ""
        val model = info.model?.trim().orEmpty()
        val make = info.make?.trim().orEmpty()
        if (model.isBlank()) return capitalizeFirst(make)
        val suffix = stripBrandPrefix(model)
        return capitalizeFirst(suffix.ifBlank { model })
    }

    /**
     * 反复剥掉型号开头的品牌词 + 分隔符，直到遇到非品牌词为止。
     * 品牌词后必须紧跟非字母（空格 / 连字符 / 数字 / 逗号），避免把 "Mix" 误剥成 "x"。
     */
    private fun stripBrandPrefix(model: String): String {
        val brandPrefix = Regex(
            "(?i)^(iphone|ipad|apple|samsung|sm|huawei|honor|xiaomi|redmi|mi|oppo|oneplus|" +
                "vivo|iqoo|realme|google|pixel|sony|xperia|motorola|moto|lenovo|zte|meizu|" +
                "nokia|lg|htc|asus|sharp|tecno|infinix|canon|nikon|fujifilm|panasonic|lumix|" +
                "leica|hasselblad|sigma|zeiss|kodak|gopro|dji|ricoh|phaseone|casio)" +
                "(?=[^a-zA-Z]|$)[\\s\\-_,]*"
        )
        var s = model.trim()
        while (true) {
            val matched = brandPrefix.find(s) ?: break
            val next = s.substring(matched.range.last + 1).trim()
            if (next.isBlank() || next.length == s.length) break
            s = next
        }
        return s
    }

    /** 首字母（若为小写字母）转大写，其余原样保留。 */
    private fun capitalizeFirst(text: String): String =
        text.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

    /**
     * 第二行的镜头参数：22mm F1.7 1/100s ISO12（相机水印常见排版）。
     * 只输出原图 EXIF 中确实存在的字段，缺失字段直接省略。
     */
    fun formatLensLine(info: ExifInfo?): String {
        if (info == null) return ""
        val parts = mutableListOf<String>()
        info.focalLength?.let { parts += "${formatNum(it)}mm" }
        info.fNumber?.let { parts += "F${formatNum(it)}" }
        info.exposureTime?.let { parts += formatExposure(it) }
        info.iso?.let { parts += "ISO$it" }
        return parts.joinToString(" ")
    }

    /** 第二行的拍摄时间：把 EXIF 的 "2026:10:17 16:05:37" 规整为常见日期格式。 */
    fun formatDateLine(info: ExifInfo?): String {
        val raw = info?.dateTime?.trim()?.takeIf { it.isNotBlank() } ?: return ""
        val parts = raw.split(':', limit = 3)
        // EXIF 常见 "2026:10:17 16:05:37"，只把日期段的分隔符换成横杠，时间保持冒号
        return if (parts.size == 3) "${parts[0]}-${parts[1]}-${parts[2]}" else raw
    }

    /** v2.7.0：镜头参数行固定自动（有镜头参数优先，否则拍摄时间）；不再拼 GPS 坐标。 */
    fun formatAutoSecondLine(info: ExifInfo?): String =
        formatLensLine(info).ifBlank { formatDateLine(info) }

    /** 文字水印选「时间戳」且照片无 EXIF 时间时，取选中当下的本机时间。 */
    fun formatNow(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

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
}
