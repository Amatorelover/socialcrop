package com.lihoo.socialcrop.data

import android.content.Context
import org.json.JSONObject

/**
 * 水印配置本地持久化（SharedPreferences，零权限、不联网）。
 * 两层结构：
 *  - 全局（KEY_GLOBAL）：用于纯裁剪 / 切格，所有图通用；
 *  - 每边框模式（KEY_FRAMES）：FrameStyle -> 水印，各模式独立开关与位置。
 * 字段缺失时自动回落到 WatermarkSpec 默认值，后续新增参数不会让旧配置崩溃。
 */
object WatermarkStore {
    private const val PREF_NAME = "socialcrop_watermark"
    private const val KEY_GLOBAL = "wm"
    private const val KEY_FRAMES = "frames"

    fun loadGlobal(context: Context): WatermarkSpec {
        val raw = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_GLOBAL, null) ?: return WatermarkSpec()
        return runCatching { specFromJson(JSONObject(raw)) }.getOrDefault(WatermarkSpec())
    }

    fun saveGlobal(context: Context, wm: WatermarkSpec) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_GLOBAL, specToJson(wm).toString()).apply()
    }

    /** 各边框模式的独立水印；未配置的模式不会出现在 map 中 */
    fun loadFrameMap(context: Context): Map<FrameStyle, WatermarkSpec> {
        val raw = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_FRAMES, null) ?: return emptyMap()
        return runCatching {
            val o = JSONObject(raw)
            val map = mutableMapOf<FrameStyle, WatermarkSpec>()
            o.keys().forEach { key ->
                if (key.startsWith("frame_")) {
                    val styleName = key.removePrefix("frame_")
                    val style = runCatching { enumValueOf<FrameStyle>(styleName) }.getOrNull()
                        ?: return@forEach
                    // 单条记录损坏只跳过这一个模式，不能让整张 map 被丢弃、
                    // 把用户其余 5 个模式的水印配置一起静默清空
                    val spec = runCatching { specFromJson(o.getJSONObject(key)) }.getOrNull()
                    if (spec != null) map[style] = spec
                }
            }
            map
        }.getOrDefault(emptyMap())
    }

    fun saveFrameMap(context: Context, map: Map<FrameStyle, WatermarkSpec>) {
        val o = JSONObject()
        map.forEach { (style, wm) -> o.put("frame_${style.name}", specToJson(wm)) }
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_FRAMES, o.toString()).apply()
    }

    private fun specFromJson(o: JSONObject): WatermarkSpec {
        val d = WatermarkSpec()
        // v2.5.0 迁移：旧版有独立 showText / showLogo 开关，关掉的内容视为未配置，
        // 避免升级后突然冒出用户当年刻意隐藏的文字或 Logo。
        val legacyText = o.optString("text", d.text)
        val text = if (o.optBoolean("showText", true)) legacyText else ""
        val legacyLogo = o.optString("logoPath", d.logoPath)
        val logoPath = if (o.optBoolean("showLogo", true)) legacyLogo else ""
        // v2.6.0 迁移：旧版是「作者名字 / 时间戳」二选一（textKind），现在两者各带独立开关。
        // 旧存档里实际写过内容的那一项自动打开，另一项保持关闭。
        val legacyKind = o.optString("textKind", "AUTHOR")
        val legacyAuthorOn = text.isNotBlank() && legacyKind == "AUTHOR"
        val legacyTimeOn = o.optString("timeText", d.timeText).isNotBlank() && legacyKind == "TIME"
        // v2.7.0 迁移：旧的 autoExif 单开关拆成「手机型号 / 镜头参数」两个独立开关
        val legacyAutoExif = o.optBoolean("autoExif", false)
        // v2.13.0 迁移：旧的单一 scaleRatio 拆成三类各自大小；旧存档三者取同一值，视觉不变
        val legacyScale = o.optDouble("scaleRatio", d.logoScaleRatio.toDouble()).toFloat()
        return WatermarkSpec(
            enabled = o.optBoolean("enabled", d.enabled),
            text = text,
            authorOn = o.optBoolean("authorOn", legacyAuthorOn),
            timeOn = o.optBoolean("timeOn", legacyTimeOn),
            timeText = o.optString("timeText", d.timeText),
            logoPath = logoPath,
            logoTint = runCatching { enumValueOf<LogoTint>(o.optString("logoTint", d.logoTint.name)) }
                .getOrDefault(d.logoTint),
            logoBrandId = if (logoPath.isBlank()) "" else o.optString("logoBrandId", d.logoBrandId),
            deviceOn = o.optBoolean("deviceOn", legacyAutoExif),
            deviceText = o.optString("deviceText", d.deviceText),
            lensOn = o.optBoolean("lensOn", legacyAutoExif),
            textColor = o.optInt("textColor", d.textColor),
            opacity = o.optDouble("opacity", d.opacity.toDouble()).toFloat(),
            logoScaleRatio = o.optDouble("logoScaleRatio", legacyScale.toDouble()).toFloat(),
            textScaleRatio = o.optDouble("textScaleRatio", legacyScale.toDouble()).toFloat(),
            exifScaleRatio = o.optDouble("exifScaleRatio", legacyScale.toDouble()).toFloat(),
            rotation = o.optDouble("rotation", d.rotation.toDouble()).toFloat(),
            // v2.1.8：只保留下方居中，旧存档的四角位置一律归一到 CENTER
            position = WatermarkPosition.CENTER,
            marginRatio = o.optDouble("marginRatio", d.marginRatio.toDouble()).toFloat(),
            tiled = o.optBoolean("tiled", d.tiled),
            shadowOn = o.optBoolean("shadowOn", d.shadowOn),
            blockOffsetX = o.optDouble("blockOffsetX", 0.0).toFloat(),
            blockOffsetY = o.optDouble("blockOffsetY", 0.0).toFloat(),
            logoOffsetX = o.optDouble("logoOffsetX", 0.0).toFloat(),
            logoOffsetY = o.optDouble("logoOffsetY", 0.0).toFloat(),
            textOffsetX = o.optDouble("textOffsetX", 0.0).toFloat(),
            textOffsetY = o.optDouble("textOffsetY", 0.0).toFloat(),
            exifOffsetX = o.optDouble("exifOffsetX", 0.0).toFloat(),
            exifOffsetY = o.optDouble("exifOffsetY", 0.0).toFloat(),
            logoAlign = runCatching {
                enumValueOf<WatermarkAlign>(o.optString("logoAlign", d.logoAlign.name))
            }.getOrDefault(d.logoAlign),
            textAlign = runCatching {
                enumValueOf<WatermarkAlign>(o.optString("textAlign", d.textAlign.name))
            }.getOrDefault(d.textAlign),
            exifAlign = runCatching {
                enumValueOf<WatermarkAlign>(o.optString("exifAlign", d.exifAlign.name))
            }.getOrDefault(d.exifAlign),
            blockAlign = runCatching {
                enumValueOf<WatermarkBlockAlign>(o.optString("blockAlign", d.blockAlign.name))
            }.getOrDefault(d.blockAlign)
        )
    }

    private fun specToJson(wm: WatermarkSpec): JSONObject = JSONObject().apply {
        put("enabled", wm.enabled)
        put("text", wm.text)
        put("authorOn", wm.authorOn)
        put("timeOn", wm.timeOn)
        put("timeText", wm.timeText)
        put("logoPath", wm.logoPath)
        put("logoTint", wm.logoTint.name)
        put("logoBrandId", wm.logoBrandId)
        put("deviceOn", wm.deviceOn)
        put("deviceText", wm.deviceText)
        put("lensOn", wm.lensOn)
        put("textColor", wm.textColor)
        put("opacity", wm.opacity.toDouble())
        put("scaleRatio", wm.logoScaleRatio.toDouble())
        put("logoScaleRatio", wm.logoScaleRatio.toDouble())
        put("textScaleRatio", wm.textScaleRatio.toDouble())
        put("exifScaleRatio", wm.exifScaleRatio.toDouble())
        put("rotation", wm.rotation.toDouble())
        put("position", wm.position.name)
        put("marginRatio", wm.marginRatio.toDouble())
        put("tiled", wm.tiled)
        put("shadowOn", wm.shadowOn)
        put("blockOffsetX", wm.blockOffsetX.toDouble())
        put("blockOffsetY", wm.blockOffsetY.toDouble())
        put("logoOffsetX", wm.logoOffsetX.toDouble())
        put("logoOffsetY", wm.logoOffsetY.toDouble())
        put("textOffsetX", wm.textOffsetX.toDouble())
        put("textOffsetY", wm.textOffsetY.toDouble())
        put("exifOffsetX", wm.exifOffsetX.toDouble())
        put("exifOffsetY", wm.exifOffsetY.toDouble())
        put("logoAlign", wm.logoAlign.name)
        put("textAlign", wm.textAlign.name)
        put("exifAlign", wm.exifAlign.name)
        put("blockAlign", wm.blockAlign.name)
    }
}
