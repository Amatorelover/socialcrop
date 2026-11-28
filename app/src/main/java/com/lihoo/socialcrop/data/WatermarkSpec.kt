package com.lihoo.socialcrop.data

import android.graphics.Color
import android.graphics.RectF

/**
 * 水印在画面中的锚点位置。v2.1.8 起只保留居中一种：统一落在留白下带正中
 * （无边框时贴底居中）。枚举项保留仅为兼容旧存档。
 */
enum class WatermarkPosition {
    CENTER, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
}

/** Logo 着色：内置品牌 Logo 多为深色，可一键改纯白/纯黑剪影（保留原透明度）。 */
enum class LogoTint { ORIGINAL, WHITE, BLACK }

/** 单类水印在其可用区域内的水平对齐方式；出厂默认居中，保证整体均衡。 */
enum class WatermarkAlign { LEFT, CENTER, RIGHT }

/**
 * 整体水印相对照片的水平落点。
 * SIDES = 两边对齐：Logo 贴左、文字贴右，仅在「Logo + 照片参数」或「Logo + 文字」两种组合下生效。
 */
enum class WatermarkBlockAlign { CENTER, LEFT, RIGHT, SIDES }

/**
 * 用户自有水印配置。enabled 是唯一总开关：关闭时设置页只显示开关、导出不带任何水印；
 * 开启后图片 / 照片参数 / 文字三类可组合，排版最多占两行（参考相机水印：
 * 第一行 Logo 内联 + 机型/作者名，第二行时间戳/镜头参数，整体居中）。
 *
 * v2.7.0：
 *  - 照片参数由单个 autoExif 拆成 deviceOn（手机型号·第一行）/ lensOn（镜头参数·第二行）
 *    两个独立开关；GPS 开关与 EXIF 两行文字说明一并删除（不再解析/展示坐标）。
 *  - 开启 Logo 后，照片参数与文字水印互斥（只能再开一个），见
 *    EditorViewModel.normalizeExclusive。
 *  - 文字颜色 / 大小 / 透明度 / 旋转 / 边距 / 平铺 / 投影提升为「开启水印后统一设置」。
 */
data class WatermarkSpec(
    val enabled: Boolean = false,
    /** 作者名字（authorOn = true 且非空时显示） */
    val text: String = "",
    /** 作者名字开关：可独立关闭 */
    val authorOn: Boolean = false,
    /** 时间戳开关：可独立关闭 */
    val timeOn: Boolean = false,
    /** 自动生成的时间戳文本（timeOn = true 时使用），选中时算好并持久化，保证预览与导出一致 */
    val timeText: String = "",
    /** 用户 Logo 复制到应用私有目录后的内部路径；非空即显示 */
    val logoPath: String = "",
    val logoTint: LogoTint = LogoTint.ORIGINAL,
    /** 内置品牌 Logo id；空表示用户自己上传 */
    val logoBrandId: String = "",
    /** 照片参数·第一行：手机型号（品牌 + 机型，由 EXIF 现算） */
    val deviceOn: Boolean = false,
    /**
     * 手机型号文本框的用户自定义值。空 = 自动识别 EXIF（有 Logo 时自动剥掉品牌前缀）；
     * 非空 = 用户自由改过的机型，渲染时原样使用、不再剥前缀。
     */
    val deviceText: String = "",
    /** 照片参数·第二行：镜头参数（无镜头参数时为拍摄时间） */
    val lensOn: Boolean = false,
    val textColor: Int = Color.WHITE,
    /** 整体透明度 0..1；出厂 100% 完全不透明，用户需要半透明时自己调低 */
    val opacity: Float = 1f,
    /**
     * 三类水印各自的基准尺寸（相对短边；带边框时会被留白带高度统一钳制）。
     * v2.13.0：由原来的单一 scaleRatio 拆成三份，三个 Tab 的大小滑块各自独立记忆。
     * 旧存档读入时用旧 scaleRatio 作为三者的初值，视觉与升级前一致。
     */
    val logoScaleRatio: Float = 0.16f,
    val textScaleRatio: Float = 0.16f,
    val exifScaleRatio: Float = 0.16f,
    val rotation: Float = 0f,
    /** 位置统一为下方居中（枚举项仅兼容旧存档） */
    val position: WatermarkPosition = WatermarkPosition.CENTER,
    /** 边距：相对短边 */
    val marginRatio: Float = 0.04f,
    val tiled: Boolean = false,
    /** 投影是可选项，出厂关闭；用户需要时自己在通用设置里打开 */
    val shadowOn: Boolean = false,

    // ===== 整体位置（通用设置·整块大幅移动，相对画布短边比例）=====

    // 与下面三类各自的微调相互独立、可叠加：这里移动整个水印块，微调只动某一类。
    val blockOffsetX: Float = 0f,
    val blockOffsetY: Float = 0f,

    // ===== 三类水印各自的位移（相对画布短边比例，可正可负）=====
    val logoOffsetX: Float = 0f,
    val logoOffsetY: Float = 0f,
    val textOffsetX: Float = 0f,
    val textOffsetY: Float = 0f,
    val exifOffsetX: Float = 0f,
    val exifOffsetY: Float = 0f,

    // ===== 三类各自在其可用区域内的水平对齐（默认居中）=====
    val logoAlign: WatermarkAlign = WatermarkAlign.CENTER,
    val textAlign: WatermarkAlign = WatermarkAlign.CENTER,
    val exifAlign: WatermarkAlign = WatermarkAlign.CENTER,

    /** 通用设置里的整体对齐，作用于整块水印相对照片的落点 */
    val blockAlign: WatermarkBlockAlign = WatermarkBlockAlign.CENTER
) {
    companion object {
        /** 三类水印共用的出厂基准字号（相对短边）；换 Logo 时大小回到这个值 */
        const val DEFAULT_SCALE_RATIO = 0.16f
    }
}

/** 位移控制针对哪一类水印 */
enum class WatermarkMoveTarget { LOGO, TEXT, EXIF }

/**
 * 带边框渲染时，水印只允许落在照片矩形之外的上下留白带（电影模式为上下遮罩条）。
 * 均为画布坐标系下的整幅宽度矩形。
 */
data class WatermarkBands(
    val top: RectF,
    val bottom: RectF,
    val center: RectF
)
