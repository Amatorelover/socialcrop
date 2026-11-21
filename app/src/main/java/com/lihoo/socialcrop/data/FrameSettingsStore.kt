package com.lihoo.socialcrop.data

import android.content.Context
import org.json.JSONObject

/**
 * 边框参数本地持久化（SharedPreferences，零权限、不联网）。
 * 用 org.json（Android 内置）序列化，字段缺失时自动回落到 FrameSpec 默认值，
 * 因此后续新增参数不会导致旧配置读取崩溃。
 */
object FrameSettingsStore {
    private const val PREF_NAME = "socialcrop_frame_settings"
    private const val KEY_SPEC = "spec"

    fun load(context: Context): FrameSpec {
        val raw = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SPEC, null) ?: return FrameSpec()
        return runCatching {
            val o = JSONObject(raw)
            val d = FrameSpec()
            // v2.1.9：纯白/纯黑留白已并入「自定义背景色」，旧存档按原色迁移，避免白/黑模式凭空消失
            val savedStyle = o.optString("style", "")
            val migratedColor = when (savedStyle) {
                "WHITE" -> 0xFFFAFAFA.toInt()
                "BLACK" -> 0xFF121315.toInt()
                else -> null
            }
            val savedStyleOrCustom = if (migratedColor != null) FrameStyle.CUSTOM
                else enumOr(o, "style", d.style)
            FrameSpec(
                enabled = o.optBoolean("enabled", d.enabled),
                style = savedStyleOrCustom,
                // v2.1.8：适配固定完整显示、输出固定无损，旧存档值一律归一
                fit = FrameFit.CONTAIN,
                paddingRatio = o.optDouble("paddingRatio", d.paddingRatio.toDouble()).toFloat(),
                bottomBar = o.optBoolean("bottomBar", d.bottomBar),
                bottomBarRatio = o.optDouble("bottomBarRatio", d.bottomBarRatio.toDouble()).toFloat(),
                contentScale = o.optDouble("contentScale", d.contentScale.toDouble()).toFloat(),
                horizontalBias = o.optDouble("horizontalBias", d.horizontalBias.toDouble()).toFloat(),
                verticalBias = o.optDouble("verticalBias", d.verticalBias.toDouble()).toFloat(),
                cornerRatio = o.optDouble("cornerRatio", d.cornerRatio.toDouble()).toFloat(),
                stroke = o.optBoolean("stroke", d.stroke),
                strokeWidthRatio = o.optDouble("strokeWidthRatio", d.strokeWidthRatio.toDouble()).toFloat(),
                strokeColorMode = enumOr(o, "strokeColorMode", d.strokeColorMode),
                shadow = o.optBoolean("shadow", d.shadow),
                shadowStrength = o.optDouble("shadowStrength", d.shadowStrength.toDouble()).toFloat(),
                shadowSpread = o.optDouble("shadowSpread", d.shadowSpread.toDouble()).toFloat(),
                blurStrength = o.optDouble("blurStrength", d.blurStrength.toDouble()).toFloat(),
                blurDim = o.optDouble("blurDim", d.blurDim.toDouble()).toFloat(),
                blurZoom = o.optDouble("blurZoom", d.blurZoom.toDouble()).toFloat(),
                blurSaturation = o.optDouble("blurSaturation", d.blurSaturation.toDouble()).toFloat(),
                blurWarmth = o.optDouble("blurWarmth", d.blurWarmth.toDouble()).toFloat(),
                blurLight = o.optDouble("blurLight", d.blurLight.toDouble()).toFloat(),
                gradientAngle = o.optDouble("gradientAngle", d.gradientAngle.toDouble()).toFloat(),
                gradientStart = if (o.has("gradientStart") && !o.isNull("gradientStart"))
                    o.optInt("gradientStart") else null,
                gradientEnd = if (o.has("gradientEnd") && !o.isNull("gradientEnd"))
                    o.optInt("gradientEnd") else null,
                showPalette = o.optBoolean("showPalette", d.showPalette),
                paletteCount = o.optInt("paletteCount", d.paletteCount),
                customColor = migratedColor ?: o.optInt("customColor", d.customColor),
                cinemaBarRatio = o.optDouble("cinemaBarRatio", d.cinemaBarRatio.toDouble()).toFloat(),
                cinemaTheme = enumOr(o, "cinemaTheme", d.cinemaTheme),
                subtitle = o.optString("subtitle", d.subtitle),
                subtitleSecondary = o.optString("subtitleSecondary", d.subtitleSecondary),
                showSubtitleSecondary = o.optBoolean("showSubtitleSecondary", d.showSubtitleSecondary),
                subtitleSizeRatio = o.optDouble("subtitleSizeRatio", d.subtitleSizeRatio.toDouble()).toFloat(),
                subtitleLetterSpacing = o.optDouble("subtitleLetterSpacing", d.subtitleLetterSpacing.toDouble()).toFloat(),
                subtitleLineGap = o.optDouble("subtitleLineGap", d.subtitleLineGap.toDouble()).toFloat(),
                subtitleAlign = enumOr(o, "subtitleAlign", d.subtitleAlign),
                // v2.1.8：固定无损输出，忽略旧存档的分辨率档位
                outWidth = OUT_WIDTH_LOSSLESS,
                presetName = o.optString("presetName", d.presetName)
            )
        }.getOrDefault(FrameSpec())
    }

    fun save(context: Context, spec: FrameSpec) {
        val o = JSONObject().apply {
            put("enabled", spec.enabled)
            put("style", spec.style.name)
            put("fit", spec.fit.name)
            put("paddingRatio", spec.paddingRatio.toDouble())
            put("bottomBar", spec.bottomBar)
            put("bottomBarRatio", spec.bottomBarRatio.toDouble())
            put("contentScale", spec.contentScale.toDouble())
            put("horizontalBias", spec.horizontalBias.toDouble())
            put("verticalBias", spec.verticalBias.toDouble())
            put("cornerRatio", spec.cornerRatio.toDouble())
            put("stroke", spec.stroke)
            put("strokeWidthRatio", spec.strokeWidthRatio.toDouble())
            put("strokeColorMode", spec.strokeColorMode.name)
            put("shadow", spec.shadow)
            put("shadowStrength", spec.shadowStrength.toDouble())
            put("shadowSpread", spec.shadowSpread.toDouble())
            put("blurStrength", spec.blurStrength.toDouble())
            put("blurDim", spec.blurDim.toDouble())
            put("blurZoom", spec.blurZoom.toDouble())
            put("blurSaturation", spec.blurSaturation.toDouble())
            put("blurWarmth", spec.blurWarmth.toDouble())
            put("blurLight", spec.blurLight.toDouble())
            put("gradientAngle", spec.gradientAngle.toDouble())
            if (spec.gradientStart != null) put("gradientStart", spec.gradientStart)
            if (spec.gradientEnd != null) put("gradientEnd", spec.gradientEnd)
            put("showPalette", spec.showPalette)
            put("paletteCount", spec.paletteCount)
            put("customColor", spec.customColor)
            put("cinemaBarRatio", spec.cinemaBarRatio.toDouble())
            put("cinemaTheme", spec.cinemaTheme.name)
            put("subtitle", spec.subtitle)
            put("subtitleSecondary", spec.subtitleSecondary)
            put("showSubtitleSecondary", spec.showSubtitleSecondary)
            put("subtitleSizeRatio", spec.subtitleSizeRatio.toDouble())
            put("subtitleLetterSpacing", spec.subtitleLetterSpacing.toDouble())
            put("subtitleLineGap", spec.subtitleLineGap.toDouble())
            put("subtitleAlign", spec.subtitleAlign.name)
            put("outWidth", spec.outWidth)
            put("presetName", spec.presetName)
        }
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_SPEC, o.toString()).apply()
    }

    private inline fun <reified T : Enum<T>> enumOr(o: JSONObject, key: String, fallback: T): T {
        val name = o.optString(key, "")
        return runCatching { enumValueOf<T>(name) }.getOrDefault(fallback)
    }
}
