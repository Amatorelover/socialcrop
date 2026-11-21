package com.lihoo.socialcrop.data

enum class Orientation { PORTRAIT, LANDSCAPE }

data class PlatformPreset(
    val id: String,
    val portraitRatios: List<RatioOption>,
    val landscapeRatios: List<RatioOption>
) {
    fun ratios(orientation: Orientation): List<RatioOption> =
        if (orientation == Orientation.PORTRAIT) portraitRatios else landscapeRatios
}

object Presets {
    val ALL = listOf(
        PlatformPreset(
            id = "generic",
            portraitRatios = listOf(
                RatioOption("3:4", 1080, 1440),
                RatioOption("4:5", 1080, 1350),
                RatioOption("1:1", 1080, 1080),
                RatioOption("9:16", 1080, 1920)
            ),
            landscapeRatios = listOf(
                RatioOption("4:3", 1440, 1080),
                RatioOption("16:9", 1920, 1080),
                RatioOption("1.91:1", 1080, 566)
            )
        ),
        PlatformPreset(
            id = "xhs",
            portraitRatios = listOf(
                RatioOption("3:4 竖图", 1080, 1440),
                RatioOption("1:1 方图", 1080, 1080)
            ),
            landscapeRatios = listOf(
                RatioOption("4:3", 1440, 1080),
                RatioOption("16:9", 1920, 1080)
            )
        ),
        PlatformPreset(
            id = "douyin",
            portraitRatios = listOf(RatioOption("9:16 全屏", 1080, 1920)),
            landscapeRatios = listOf(RatioOption("16:9", 1920, 1080))
        ),
        PlatformPreset(
            id = "wechat",
            portraitRatios = listOf(
                RatioOption("1:1 方图", 1080, 1080),
                RatioOption("3:4 首图", 1080, 1440)
            ),
            landscapeRatios = listOf(
                RatioOption("2.35:1 封面", 900, 383),
                RatioOption("16:9", 1920, 1080)
            )
        ),
        PlatformPreset(
            id = "instagram",
            portraitRatios = listOf(
                RatioOption("4:5", 1080, 1350),
                RatioOption("1:1", 1080, 1080)
            ),
            landscapeRatios = listOf(
                RatioOption("1.91:1", 1080, 566),
                RatioOption("16:9", 1920, 1080)
            )
        ),
        PlatformPreset(
            id = "x",
            portraitRatios = listOf(
                RatioOption("4:5", 1080, 1350),
                RatioOption("1:1", 1080, 1080),
                RatioOption("9:16", 1080, 1920)
            ),
            landscapeRatios = listOf(
                RatioOption("16:9", 1600, 900),
                RatioOption("2:1", 1600, 800),
                RatioOption("3:2", 1200, 800)
            )
        ),
        PlatformPreset(
            id = "weibo",
            portraitRatios = listOf(
                RatioOption("9:16", 1080, 1920),
                RatioOption("1:1", 1080, 1080)
            ),
            landscapeRatios = listOf(RatioOption("16:9", 1920, 1080))
        )
    )
    val default: PlatformPreset get() = ALL[0]
}
