package com.lihoo.socialcrop.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.lihoo.socialcrop.R
import com.lihoo.socialcrop.data.CinemaSubtitleAlign
import com.lihoo.socialcrop.data.CinemaTheme
import com.lihoo.socialcrop.data.FrameSpec
import com.lihoo.socialcrop.data.FrameStyle
import com.lihoo.socialcrop.data.RatioOption
import com.lihoo.socialcrop.data.StrokeColor
import com.lihoo.socialcrop.data.WatermarkSpec
import kotlin.math.abs

/**
 * 数据层里的中文名称（平台名、比例后缀、预设名）只作为稳定内部 key，
 * 用户可见文本统一映射为字符串资源 ID，再由 UI 按当前语言读取。
 * 返回资源 ID 的普通函数可以在 map/filter 等非 Composable lambda 里安全调用。
 */
fun platformNameRes(id: String): Int = when (id) {
    "xhs" -> R.string.platform_xhs
    "douyin" -> R.string.platform_douyin
    "wechat" -> R.string.platform_wechat
    "instagram" -> R.string.platform_instagram
    "x" -> R.string.platform_x
    "weibo" -> R.string.platform_weibo
    else -> R.string.platform_generic
}

fun styleLabelRes(style: FrameStyle): Int = when (style) {
    FrameStyle.BLUR -> R.string.style_blur
    FrameStyle.GRADIENT -> R.string.style_gradient
    FrameStyle.CUSTOM -> R.string.style_custom
    FrameStyle.CINEMA -> R.string.style_cinema
}

/** 电影边框遮罩主题标签 */
fun cinemaThemeLabelRes(theme: CinemaTheme): Int = when (theme) {
    CinemaTheme.BLACK -> R.string.cinema_theme_black
    CinemaTheme.WHITE -> R.string.cinema_theme_white
    CinemaTheme.FADE -> R.string.cinema_theme_fade
}

/** 电影字幕对齐标签 */
fun cinemaAlignLabelRes(align: CinemaSubtitleAlign): Int = when (align) {
    CinemaSubtitleAlign.START -> R.string.cinema_align_start
    CinemaSubtitleAlign.CENTER -> R.string.cinema_align_center
    CinemaSubtitleAlign.END -> R.string.cinema_align_end
}

fun strokeLabelRes(mode: StrokeColor): Int = when (mode) {
    StrokeColor.WHITE -> R.string.stroke_white
    StrokeColor.BLACK -> R.string.stroke_black
    StrokeColor.AUTO -> R.string.stroke_auto
}

/** 参数页按当前模式取的专属分区标题 */
fun styleSectionRes(style: FrameStyle): Int = when (style) {
    FrameStyle.BLUR -> R.string.section_blur_params
    FrameStyle.GRADIENT -> R.string.section_gradient_params
    FrameStyle.CUSTOM -> R.string.section_custom_params
    FrameStyle.CINEMA -> R.string.section_cinema_params
}

fun presetNameRes(key: String): Int = when (key) {
    // 旧版「柔焦淡背景」已合并进「默认柔焦」，旧配置仍能正确显示
    "默认柔焦", "柔焦淡背景" -> R.string.preset_default_blur
    "杂志色卡" -> R.string.preset_magazine
    // 旧版「电影黑」改名为「电影边框」，兼容已保存配置
    "电影边框", "电影黑" -> R.string.preset_cinema
    "暖白细框" -> R.string.preset_warm_frame
    else -> R.string.preset_custom
}

private fun ratioSuffixRes(suffix: String): Int = when (suffix) {
    "竖图" -> R.string.ratio_portrait
    "方图" -> R.string.ratio_square
    "全屏" -> R.string.ratio_fullscreen
    "封面" -> R.string.ratio_cover
    "首图" -> R.string.ratio_lead
    else -> 0
}

@Composable
fun platformName(id: String): String = stringResource(platformNameRes(id))

@Composable
fun styleLabel(style: FrameStyle): String = stringResource(styleLabelRes(style))

/** "3:4 竖图" → "3:4 Portrait" / "3:4 竖图"；无后缀的比例（如 "16:9"）原样返回 */
@Composable
fun ratioLabel(r: RatioOption): String {
    if (r.isOriginal) return stringResource(R.string.ratio_original)
    val raw = r.label
    val base = raw.substringBefore(' ')
    val resId = ratioSuffixRes(raw.substringAfter(' ', ""))
    return if (resId == 0) base else "$base ${stringResource(resId)}"
}

@Composable
fun frameSummary(spec: FrameSpec): String {
    val styleName = stringResource(styleLabelRes(spec.style))
    if (spec.style == FrameStyle.CINEMA) {
        return stringResource(
            R.string.frame_summary_cinema,
            styleName,
            stringResource(cinemaThemeLabelRes(spec.cinemaTheme)),
            (spec.cinemaBarRatio * 100).toInt(),
            stringResource(R.string.label_lossless)
        )
    }
    return stringResource(
        R.string.frame_summary,
        styleName,
        (spec.paddingRatio * 100).toInt(),
        (spec.cornerRatio * 100).toInt(),
        stringResource(R.string.label_lossless)
    )
}

/** 画面偏移的可读文案：左/右 或 上/下，居中时不带方向 */
@Composable
fun biasText(value: Float, horizontal: Boolean): String {
    val percent = (abs(value) * 100).toInt()
    return when {
        value < -0.05f -> stringResource(
            if (horizontal) R.string.bias_left else R.string.bias_top, percent
        )
        value > 0.05f -> stringResource(
            if (horizontal) R.string.bias_right else R.string.bias_bottom, percent
        )
        else -> stringResource(R.string.bias_center)
    }
}

/** 编辑页水印卡片的内容摘要：按已开启的类别组合显示，未开启显示「未开启」 */
@Composable
fun watermarkSummary(wm: WatermarkSpec): String {
    if (!wm.enabled) return stringResource(R.string.watermark_off)
    val parts = mutableListOf<String>()
    if (wm.logoPath.isNotBlank()) parts += stringResource(R.string.watermark_sum_logo)
    if (wm.deviceOn || wm.lensOn) parts += stringResource(R.string.wm_tab_exif)
    val hasAuthor = wm.authorOn && wm.text.isNotBlank()
    val hasTime = wm.timeOn && wm.timeText.isNotBlank()
    if (hasAuthor || hasTime) parts += stringResource(R.string.wm_tab_text)
    return when {
        parts.isEmpty() -> stringResource(R.string.watermark_off)
        parts.size == 1 && hasAuthor -> wm.text
        parts.size == 1 && hasTime -> wm.timeText
        else -> parts.joinToString(" + ")
    }
}
