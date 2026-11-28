package com.lihoo.socialcrop.data

/**
 * 边框（画布拓展）全部可调参数。
 *
 * 设计目的：把「任意比例原图 → 目标平台比例」时产生的黑边/白边，
 * 替换为可控的设计感背景，同时保留完整画面。
 * 不含 EXIF 参数识别与品牌 Logo/水印。
 *
 * 所有新增字段的默认值都与旧版渲染结果兼容；关闭边框时行为与 v1.0 完全一致。
 */
enum class FrameStyle {
    /** 背景模糊：同一张原图放大强模糊铺满做背景，清晰照片浮在其上（v2.1.9 由「模糊延展」改名） */
    BLUR,
    /** 从原图提取主色做渐变 + 底部色卡（样图 2） */
    GRADIENT,
    /** 自定义纯色画布 */
    CUSTOM,
    /** 电影边框：照片铺满画布，上下叠对称遮罩，下遮罩内放两行紧贴字幕 */
    CINEMA
}

/** 电影边框遮罩主题（对应参考的三个圆点：纯黑 / 纯白 / 柔和渐隐） */
enum class CinemaTheme { BLACK, WHITE, FADE }

/** 电影字幕水平对齐 */
enum class CinemaSubtitleAlign { START, CENTER, END }

enum class FrameFit {
    /** 完整显示原图，不裁切（避免丢内容，也避免黑边白边） */
    CONTAIN,
    /** 居中裁切填满内容区，再套边框 */
    COVER
}

/** 内容细描边配色，用于把深色照片从深色背景里分离出来 */
enum class StrokeColor { WHITE, BLACK, AUTO }

data class FrameSpec(
    val enabled: Boolean = false,
    val style: FrameStyle = FrameStyle.BLUR,
    val fit: FrameFit = FrameFit.CONTAIN,

    // ===== 布局 =====
    /** 四周统一留白，相对画布短边。v2.16.0 起出厂收窄到 1.55%，对齐参考图的细白边 */
    val paddingRatio: Float = 0.0155f,
    /** 底部额外留白条（纯色/渐变样式有效）。v2.16.0 起默认打开，给水印留出参考图那样的白底带 */
    val bottomBar: Boolean = true,
    /** 底部留白条高度，相对画布短边。0.08 ≈ 参考图 120px/1293短边 */
    val bottomBarRatio: Float = 0.08f,

    // ===== 画面 =====
    /** CONTAIN 基准上的缩放系数，0.5–1.0（放大最多到填满可用区） */
    val contentScale: Float = 1.0f,
    /** 画面在可用区内的水平偏移，-1 靠左 / 0 居中 / 1 靠右 */
    val horizontalBias: Float = 0f,
    /** 画面在可用区内的垂直偏移，-1 靠上 / 0 居中 / 1 靠下 */
    val verticalBias: Float = 0f,

    // ===== 形状 =====
    /** 图片圆角，相对画布短边；出厂为直角，用户需要时自己调 */
    val cornerRatio: Float = 0f,
    val stroke: Boolean = false,
    /** 描边宽度，相对画布短边 */
    val strokeWidthRatio: Float = 0.004f,
    val strokeColorMode: StrokeColor = StrokeColor.WHITE,

    // ===== 投影 =====
    val shadow: Boolean = false,
    /** 投影浓度倍率 0–2，1 = 默认（v1.9.0 起为真实柔化投影） */
    val shadowStrength: Float = 1.0f,
    /** 投影扩散倍率 0.3–2.5，1 = 默认扩散约画布短边的 7% */
    val shadowSpread: Float = 1.0f,

    // ===== 两层模糊背景（v2.1.6：原图强模糊铺满做背景，清晰照片浮其上） =====
    /** 背景模糊强度 0–1，越大越模糊 */
    val blurStrength: Float = 0.86f,
    /** 背景压暗 0–0.7：均匀轻压暗，让清晰前景从模糊底里凸显（不做顶部带状渐变） */
    val blurDim: Float = 0.26f,
    /** 背景放大倍率 1.0–1.8：模糊底 cover 放大，避免边缘露出 */
    val blurZoom: Float = 1.18f,
    /** 背景饱和度 0–1：模糊底保留原图色系的程度；默认 0.82 */
    val blurSaturation: Float = 0.82f,
    /** 色系强度 0–1：0 = 背景去色成中性灰，默认 0.55 = 完全保留原图色系，1 = 进一步提饱和。
     *  字段名沿用旧版以免已保存配置失效；两层方案里色系本就来自原图模糊。 */
    val blurWarmth: Float = 0.55f,
    /** 背景提亮 0–1：均匀暖白回提，抵消压暗、让背景不死沉 */
    val blurLight: Float = 0.70f,

    // ===== 渐变 / 色卡 =====
    /** 渐变方向角度 0–360 */
    val gradientAngle: Float = 75f,
    /** 渐变起始色，null = 自动取原图主色 */
    val gradientStart: Int? = null,
    /** 渐变终止色，null = 自动取原图次主色 */
    val gradientEnd: Int? = null,
    /** 渐变样式底部是否绘制取色色卡 */
    val showPalette: Boolean = true,
    /** 色卡颜色数量 0–8，0 = 关闭 */
    val paletteCount: Int = 5,
    /** 色圈半径，相对基准底部色带高度（短边 × bottomBarRatio）；带高会随色圈自动增高。
     *  hex 文字随半径等比缩放，整块在带内垂直居中 */
    val paletteDotRatio: Float = 0.20f,
    /** 色圈整块相对短边的垂直偏移，正 = 下、负 = 上；带高会自动留出偏移余量 */
    val paletteDotOffsetY: Float = 0f,
    /** CUSTOM 样式背景色，ARGB */
    val customColor: Int = 0xFFF5F2EC.toInt(),

    // ===== 电影边框 =====
    /** 上下遮罩条高度，相对画布高度（v1.9.1 起照片铺满画布、遮罩覆盖在其上） */
    val cinemaBarRatio: Float = 0.11f,
    /** 遮罩主题：纯黑 / 纯白 / 柔和渐隐 */
    val cinemaTheme: CinemaTheme = CinemaTheme.BLACK,
    /** 电影字幕主行，空 = 不显示 */
    val subtitle: String = "",
    /** 电影字幕副行：由主行中文离线自动翻译的英文，可手动修改；空 = 不显示 */
    val subtitleSecondary: String = "",
    /** 是否显示英文副行：可选开关，默认关闭，用户需要时再打开 */
    val showSubtitleSecondary: Boolean = false,
    /** 字幕主行字号，相对画布短边 */
    val subtitleSizeRatio: Float = 0.038f,
    /** 字幕字间距（em），0 = 正常 */
    val subtitleLetterSpacing: Float = 0.0f,
    /** 两行字幕之间的额外行距，相对主行字号 */
    val subtitleLineGap: Float = 0.18f,
    /** 字幕水平对齐 */
    val subtitleAlign: CinemaSubtitleAlign = CinemaSubtitleAlign.CENTER,

    // ===== 输出 =====
    /** 导出画布宽度（px），高度按目标比例换算；v2.1.8 起固定无损输出（0，按原图像素不上采样） */
    val outWidth: Int = OUT_WIDTH_LOSSLESS,

    /** 当前预设的稳定内部 key，不直接展示；UI 通过 presetNameRes() 取当前语言文案 */
    val presetName: String = "默认柔焦"
) {
    val isLightBackground: Boolean
        get() = when (style) {
            FrameStyle.CUSTOM -> {
                val r = (customColor shr 16) and 0xFF
                val g = (customColor shr 8) and 0xFF
                val b = customColor and 0xFF
                (r * 299 + g * 587 + b * 114) / 1000 > 140
            }
            else -> false
        }

    /** 四边留白最终值：v1.5.1 起固定四边对称，由 paddingRatio 统一控制 */
    val effectivePads: FloatArray
        get() = floatArrayOf(paddingRatio, paddingRatio, paddingRatio, paddingRatio)
}

// 显示名称不放在数据层：见 ui/L10n.kt（按当前语言映射到字符串资源），
// 避免语言切换时改动已保存的配置。

/** outWidth 取 0 表示无损输出：按原图像素出图，不放大、不重采样 */
const val OUT_WIDTH_LOSSLESS = 0

object FramePalettes {
    /** 自定义背景色候选：纯白 / 暖白 / 浅灰 / 中灰 / 深灰 / 纯黑（最后再附一个色盘自定义入口） */
    val CUSTOM_COLORS = listOf(
        0xFFFFFFFF.toInt(),
        0xFFF5F2EC.toInt(),
        0xFFE8E8E8.toInt(),
        0xFF8A8F8C.toInt(),
        0xFF3A3F3C.toInt(),
        0xFF000000.toInt()
    )

    /** 渐变 / 描边可选强调色 */
    val ACCENT_COLORS = listOf(
        0xFFEFEFEF.toInt(),
        0xFFF5F2EC.toInt(),
        0xFFD9C7A7.toInt(),
        0xFF8FA6A0.toInt(),
        0xFF6E7F9C.toInt(),
        0xFF3A4A42.toInt(),
        0xFF1A1A1A.toInt()
    )
}

/** 一键套用的内置风格预设（柔焦只保留一套，参数按参考样图调校） */
object FramePresets {
    data class Preset(val name: String, val spec: FrameSpec)

    val ALL: List<Preset> = listOf(
        Preset(
            "默认柔焦",
            FrameSpec(
                enabled = true,
                style = FrameStyle.BLUR,
                // v2.16.0：对齐参考图——四周细白边 + 底部留白带（关掉底部留白即纯细边）
                paddingRatio = 0.0155f,
                bottomBar = true,
                bottomBarRatio = 0.08f,
                cornerRatio = 0f,
                shadow = false,
                shadowStrength = 1.0f,
                blurStrength = 0.86f,
                blurDim = 0.26f,
                blurSaturation = 0.82f,
                blurZoom = 1.18f,
                blurWarmth = 0.55f,
                blurLight = 0.70f,
                presetName = "默认柔焦"
            )
        ),
        Preset(
            "杂志色卡",
            FrameSpec(
                enabled = true,
                style = FrameStyle.GRADIENT,
                paddingRatio = 0.0155f,
                cornerRatio = 0f,
                showPalette = true,
                paletteCount = 5,
                gradientAngle = 75f,
                presetName = "杂志色卡"
            )
        ),
        Preset(
            "电影边框",
            FrameSpec(
                enabled = true,
                style = FrameStyle.CINEMA,
                fit = FrameFit.CONTAIN,
                cornerRatio = 0f,
                shadow = false,
                stroke = false,
                cinemaBarRatio = 0.11f,
                cinemaTheme = CinemaTheme.BLACK,
                subtitleLetterSpacing = 0.0f,
                subtitleLineGap = 0.18f,
                subtitleAlign = CinemaSubtitleAlign.CENTER,
                presetName = "电影边框"
            )
        ),
        Preset(
            "暖白细框",
            FrameSpec(
                enabled = true,
                style = FrameStyle.CUSTOM,
                customColor = 0xFFF5F2EC.toInt(),
                paddingRatio = 0.0155f,
                cornerRatio = 0f,
                stroke = false,
                strokeWidthRatio = 0.003f,
                strokeColorMode = StrokeColor.WHITE,
                presetName = "暖白细框"
            )
        )
    )

    /**
     * 模式选择页使用的「推荐默认值」：每种 FrameStyle 恰好对应一套，
     * 切换模式时套用，避免从电影边框（无圆角/无投影）切到柔焦后还残留旧版式。
     */
    fun recommendedFor(style: FrameStyle): FrameSpec =
        ALL.firstOrNull { it.spec.style == style }?.spec
            ?: FrameSpec(enabled = true, style = style, presetName = "自定义")
}
