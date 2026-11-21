package com.lihoo.socialcrop.data

import android.graphics.Color
import android.graphics.RectF

/**
 * 水印在画面中的锚点位置。v2.1.8 起只保留居中一种：水印统一落在留白下带正中
 * （无边框时贴底居中），不再提供左上/右上/左下/右下。枚举项保留仅为兼容旧存档。
 */
enum class WatermarkPosition {
    CENTER, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
}

/**
 * 用户自有水印配置（文字 和/或 本地 Logo），与边框参数完全独立。
 * enabled 为总开关：关闭时一切导出都不带水印；开启后对所有模式（裁剪 / 边框 / 切格）生效。
 * 默认关闭，绝不强制给图片打上 App 自己的品牌标，符合「零权限、不打扰」的承诺。
 *
 * 所有尺寸均为相对画布短边的比例，保证不同分辨率输出下观感一致。
 */
data class WatermarkSpec(
    val enabled: Boolean = false,
    val text: String = "",
    /** 用户 Logo 复制到应用私有目录后的内部路径；空表示未设置 */
    val logoPath: String = "",
    val showText: Boolean = true,
    val showLogo: Boolean = false,
    /** 自动用照片 EXIF（机型/镜头/光圈/快门/ISO/时间）填充文字；开启时优先覆盖下方手动文字 */
    val autoExif: Boolean = false,
    /** 在自动文字里附带 GPS 坐标；默认关闭，仅本地显示，绝不上传 */
    val showGps: Boolean = false,
    val textColor: Int = Color.WHITE,
    /** 整体透明度 0..1 */
    val opacity: Float = 0.5f,
    /** 基准尺寸：相对短边，文字高度或 Logo 高度；带边框时会被留白带高度限制 */
    val scaleRatio: Float = 0.16f,
    /** 旋转角度（度） */
    val rotation: Float = 0f,
    /** 位置统一为下方居中（v2.1.8，枚举项仅兼容旧存档） */
    val position: WatermarkPosition = WatermarkPosition.CENTER,
    /** 边距：相对短边 */
    val marginRatio: Float = 0.04f,
    /** 平铺铺满整图 */
    val tiled: Boolean = false,
    /** 加投影提升在亮背景上的可读性 */
    val shadowOn: Boolean = true
)

/**
 * 带边框渲染时，水印只允许落在「照片矩形之外」的上下留白带（电影模式为上下遮罩条）。
 * center 单独给出：常规模式用下带作签名位；电影模式下带要留给字幕，改用上带。
 * 均为画布坐标系下的整幅宽度矩形。
 */
data class WatermarkBands(
    val top: RectF,
    val bottom: RectF,
    val center: RectF
)
