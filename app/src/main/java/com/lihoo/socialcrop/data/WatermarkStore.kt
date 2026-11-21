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
        return WatermarkSpec(
            enabled = o.optBoolean("enabled", d.enabled),
            text = o.optString("text", d.text),
            logoPath = o.optString("logoPath", d.logoPath),
            showText = o.optBoolean("showText", d.showText),
            showLogo = o.optBoolean("showLogo", d.showLogo),
            autoExif = o.optBoolean("autoExif", d.autoExif),
            showGps = o.optBoolean("showGps", d.showGps),
            textColor = o.optInt("textColor", d.textColor),
            opacity = o.optDouble("opacity", d.opacity.toDouble()).toFloat(),
            scaleRatio = o.optDouble("scaleRatio", d.scaleRatio.toDouble()).toFloat(),
            rotation = o.optDouble("rotation", d.rotation.toDouble()).toFloat(),
            // v2.1.8：只保留下方居中，旧存档的四角位置一律归一到 CENTER
            position = WatermarkPosition.CENTER,
            marginRatio = o.optDouble("marginRatio", d.marginRatio.toDouble()).toFloat(),
            tiled = o.optBoolean("tiled", d.tiled),
            shadowOn = o.optBoolean("shadowOn", d.shadowOn)
        )
    }

    private fun specToJson(wm: WatermarkSpec): JSONObject = JSONObject().apply {
        put("enabled", wm.enabled)
        put("text", wm.text)
        put("logoPath", wm.logoPath)
        put("showText", wm.showText)
        put("showLogo", wm.showLogo)
        put("autoExif", wm.autoExif)
        put("showGps", wm.showGps)
        put("textColor", wm.textColor)
        put("opacity", wm.opacity.toDouble())
        put("scaleRatio", wm.scaleRatio.toDouble())
        put("rotation", wm.rotation.toDouble())
        put("position", wm.position.name)
        put("marginRatio", wm.marginRatio.toDouble())
        put("tiled", wm.tiled)
        put("shadowOn", wm.shadowOn)
    }
}
