package com.lihoo.socialcrop.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.lihoo.socialcrop.data.FrameSettingsStore
import com.lihoo.socialcrop.data.FramePresets
import com.lihoo.socialcrop.data.FrameSpec
import com.lihoo.socialcrop.data.FrameStyle
import com.lihoo.socialcrop.data.Orientation
import com.lihoo.socialcrop.data.PlatformPreset
import com.lihoo.socialcrop.data.Presets
import com.lihoo.socialcrop.data.RatioOption
import com.lihoo.socialcrop.data.WatermarkSpec
import com.lihoo.socialcrop.data.WatermarkStore
import com.lihoo.socialcrop.domain.FrameRenderer
import com.lihoo.socialcrop.domain.ImageProcessor
import com.lihoo.socialcrop.domain.WatermarkRenderer
import com.lihoo.socialcrop.util.ExifInfo
import com.lihoo.socialcrop.util.ExifReader
import com.lihoo.socialcrop.util.SubtitleTranslator
import com.lihoo.socialcrop.util.decodeBitmap
import com.lihoo.socialcrop.util.decodeLogo
import java.io.File

class EditorViewModel : ViewModel() {

    enum class Mode { CROP, SLICE }

    var bitmap by mutableStateOf<Bitmap?>(null)
    var mode by mutableStateOf(Mode.CROP)
    var preset by mutableStateOf<PlatformPreset>(Presets.default)
    var orientation by mutableStateOf(Orientation.PORTRAIT)
    // 0 = 「原比例」，默认仍选平台第一档比例，避免新增选项改变原有行为
    var ratioIndex by mutableStateOf(1)
    var columns by mutableStateOf(3)
    var rows by mutableStateOf(3)
    var cropTrigger by mutableStateOf(0)

    /** 取景框中心在源图上的归一化位置，0.5 = 居中；预览与生成共用，换图时复位 */
    var cropCenterX by mutableStateOf(0.5f)
        private set
    var cropCenterY by mutableStateOf(0.5f)
        private set

    /** 边框配置：关闭时行为与旧版完全一致（纯裁剪/纯切格） */
    var frameSpec by mutableStateOf(FrameSpec())
        private set

    /** 用户水印配置（全局，用于纯裁剪 / 切格），默认关闭 */
    var watermark by mutableStateOf(WatermarkSpec())
        private set

    /** 各边框模式的独立水印配置：FrameStyle -> 水印；未配置则回落到关闭的默认 */
    var frameWatermarks by mutableStateOf<Map<FrameStyle, WatermarkSpec>>(emptyMap())
        private set

    /** 水印作用目标：GLOBAL 用于纯裁剪 / 切格；FRAME(style) 用于对应边框模式（各模式独立开关与位置） */
    sealed interface WatermarkTarget {
        object Global : WatermarkTarget
        data class Frame(val style: FrameStyle) : WatermarkTarget
    }

    /** 当前图片读到的 EXIF（拍摄参数）；本地解析、绝不持久化 GPS */
    var exifInfo by mutableStateOf<ExifInfo?>(null)
        private set

    var result by mutableStateOf<List<Bitmap>>(emptyList())
    var resultUris by mutableStateOf<List<Uri>>(emptyList())

    private var appContext: Context? = null

    /** 关闭边框前的配置：重新启用同一模式时原样恢复，不让推荐值冲掉调好的参数 */
    private var disabledFrame: FrameSpec? = null

    /** 任何平台 / 方向都在最前面提供「原比例」，且作为默认项（不裁切） */
    val ratioOptions: List<RatioOption>
        get() = listOf(RatioOption.ORIGINAL) + preset.ratios(orientation)

    val currentRatio: RatioOption
        get() {
            val list = ratioOptions
            return list.getOrElse(ratioIndex.coerceIn(0, list.lastIndex)) { list.first() }
        }

    /** 原比例时画布宽高比取自当前图片自身，保证完整不裁切 */
    val ratioValue: Float
        get() = if (currentRatio.isOriginal) {
            bitmap?.let { it.width.toFloat() / it.height.coerceAtLeast(1).toFloat() } ?: 1f
        } else currentRatio.ratio

    /** 提示文案里的像素标签：原比例显示原图真实尺寸 */
    val ratioPixelLabel: String
        get() = if (currentRatio.isOriginal) {
            bitmap?.let { "${it.width}x${it.height}" } ?: "-"
        } else currentRatio.pixelLabel

    /** 由 AppRoot 调用，加载上次保存的边框参数（只存 applicationContext，不泄漏 Activity） */
    fun initSettings(context: Context) {
        if (appContext != null) return
        val app = context.applicationContext
        appContext = app
        frameSpec = FrameSettingsStore.load(app)
        watermark = WatermarkStore.loadGlobal(app)
        frameWatermarks = WatermarkStore.loadFrameMap(app)
    }

    /** 直接替换配置（用于开关、套用预设等保留 presetName 的场景） */
    fun updateFrame(spec: FrameSpec) {
        if (frameSpec.enabled && !spec.enabled) disabledFrame = frameSpec
        frameSpec = spec
        appContext?.let { FrameSettingsStore.save(it, spec) }
    }

    /** 手动调参会脱离预设，标记为「自定义」 */
    fun tweak(block: (FrameSpec) -> FrameSpec) {
        updateFrame(block(frameSpec).copy(presetName = "自定义"))
    }

    /**
     * 模式选择页：切换到某种边框模式并套用该模式的推荐默认值。
     * 导出宽度与用户输入的字幕属于跨模式个人设置，切换时保留，避免每次重填。
     */
    fun selectFrameStyle(style: FrameStyle) {
        // 重复点当前模式只是「进去看参数」，不能把用户调好的值冲掉
        val current = frameSpec
        if (current.enabled && current.style == style) return
        // 关掉边框后再选回同一模式：直接恢复关闭前的参数。
        // 旧版只判断 enabled，关闭后守卫失效 → 走 recommendedFor，padding/圆角/柔焦全被重置。
        val cached = disabledFrame
        if (!current.enabled && cached != null && cached.style == style) {
            updateFrame(cached.copy(enabled = true))
            return
        }
        updateFrame(recommendedFor(style, enabled = true))
    }

    /** 恢复默认只重置「当前模式」的参数，不会偷偷把模式跳回背景模糊 */
    fun resetFrame() = updateFrame(recommendedFor(frameSpec.style, enabled = frameSpec.enabled))

    private fun recommendedFor(style: FrameStyle, enabled: Boolean): FrameSpec {
        val old = frameSpec
        return FramePresets.recommendedFor(style).copy(
            enabled = enabled,
            outWidth = old.outWidth,
            subtitle = old.subtitle,
            subtitleSecondary = old.subtitleSecondary,
            showSubtitleSecondary = old.showSubtitleSecondary,
            subtitleSizeRatio = old.subtitleSizeRatio,
            cinemaBarRatio = old.cinemaBarRatio
        )
    }

    /** 主字幕（中文）改动 → 离线词库自动重算英文副行 */
    fun updateSubtitle(text: String) {
        tweak { s -> s.copy(subtitle = text, subtitleSecondary = SubtitleTranslator.translate(text)) }
    }

    /** 自动译文可手动微调；只要不再改主字幕就会保留 */
    fun updateSubtitleSecondary(text: String) = tweak { s -> s.copy(subtitleSecondary = text) }

    fun updateShowSubtitleSecondary(on: Boolean) = tweak { s -> s.copy(showSubtitleSecondary = on) }

    // ===================== 水印 =====================

    /** 取某目标当前水印配置（边框模式未单独配置时回落到关闭的默认） */
    fun getWatermark(target: WatermarkTarget): WatermarkSpec = when (target) {
        WatermarkTarget.Global -> watermark
        is WatermarkTarget.Frame -> frameWatermarks[target.style] ?: WatermarkSpec()
    }

    fun setWatermark(target: WatermarkTarget, spec: WatermarkSpec) {
        when (target) {
            WatermarkTarget.Global -> {
                watermark = spec
                appContext?.let { WatermarkStore.saveGlobal(it, spec) }
            }
            is WatermarkTarget.Frame -> {
                frameWatermarks = frameWatermarks + (target.style to spec)
                appContext?.let { WatermarkStore.saveFrameMap(it, frameWatermarks) }
            }
        }
    }

    fun setWatermarkEnabled(target: WatermarkTarget, on: Boolean) =
        setWatermark(target, getWatermark(target).copy(enabled = on))

    /** 自动用照片 EXIF 填充水印文字：读不到 EXIF 时保留原文字，不强行打开文字层 */
    fun setAutoExif(target: WatermarkTarget, on: Boolean) {
        val cur = getWatermark(target)
        if (!on) {
            setWatermark(target, cur.copy(autoExif = false))
            return
        }
        val filled = ExifReader.formatWatermarkText(exifInfo, cur.showGps)
        setWatermark(
            target,
            if (filled.isBlank()) cur.copy(autoExif = true)
            else cur.copy(autoExif = true, text = filled, showText = true)
        )
    }

    /** GPS 开关：仅在自动 EXIF 开启时影响水印文字；默认关、仅本地显示 */
    fun setShowGps(target: WatermarkTarget, on: Boolean) {
        val cur = getWatermark(target)
        // 旧版无条件 showText = true，手动/纯 Logo 水印一开 GPS 就会凭空冒出一行文字
        val filled = if (cur.autoExif) ExifReader.formatWatermarkText(exifInfo, on) else cur.text
        setWatermark(
            target,
            if (!cur.autoExif || filled.isBlank()) cur.copy(showGps = on)
            else cur.copy(showGps = on, text = filled)
        )
    }

    /** 把用户选中的 Logo 复制到应用私有目录（零权限、不联网），并开启 Logo 显示 */
    fun setWatermarkLogo(target: WatermarkTarget, context: Context, uri: Uri) {
        val app = context.applicationContext
        val dir = File(app.filesDir, "watermark").apply { mkdirs() }
        val dest = File(dir, "logo_${System.currentTimeMillis()}.png")
        runCatching {
            app.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { out -> input.copyTo(out) }
            }
        }
        val old = getWatermark(target).logoPath
        setWatermark(target, getWatermark(target).copy(logoPath = dest.absolutePath, showLogo = true))
        if (old.isNotBlank() && old != dest.absolutePath) File(old).delete()
    }

    fun clearWatermarkLogo(target: WatermarkTarget) {
        val old = getWatermark(target).logoPath
        if (old.isNotBlank()) File(old).delete()
        setWatermark(target, getWatermark(target).copy(logoPath = "", showLogo = false))
    }

    /** 纯裁剪模式下，最终裁切结果由 CropView 产出，这里负责在导出前套上全局水印 */
    fun applyWatermark(bmp: Bitmap): Bitmap = drawWatermarkOn(bmp, getWatermark(WatermarkTarget.Global))

    private fun drawWatermarkOn(bmp: Bitmap, wm: WatermarkSpec): Bitmap {
        if (!wm.enabled) return bmp
        val logo = loadLogo(wm)
        val out = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawBitmap(bmp, 0f, 0f, null)
        WatermarkRenderer.draw(c, wm, bmp.width, bmp.height, logo)
        logo?.recycle()
        return out
    }

    private fun loadLogo(wm: WatermarkSpec): Bitmap? {
        if (!wm.showLogo || wm.logoPath.isBlank()) return null
        return decodeLogo(wm.logoPath)
    }

    fun updateCropCenter(x: Float, y: Float) {
        cropCenterX = x.coerceIn(0f, 1f)
        cropCenterY = y.coerceIn(0f, 1f)
    }

    fun resetCropCenter() {
        cropCenterX = 0.5f
        cropCenterY = 0.5f
    }

    fun load(context: Context, uri: Uri): Bitmap? {
        val bmp = decodeBitmap(context, uri)
        bitmap = bmp
        exifInfo = ExifReader.read(context, uri)
        // 换图即重置水印总开关为默认关闭，避免上一张图的水印悄悄盖到新图上；
        // 用户配置好的文字 / Logo / 样式都保留，想用时在原页面重新打开即可。
        resetWatermarkEnabledForNewImage()
        // 换图后，所有开启自动 EXIF 的水印（全局 + 各边框模式）都刷新文字
        refreshExifTexts()
        resetCropCenter()
        return bmp
    }

    /** 每次换图把水印开关归位到关闭；不清除文字 / Logo / 参数，且立即持久化 */
    private fun resetWatermarkEnabledForNewImage() {
        if (watermark.enabled) {
            watermark = watermark.copy(enabled = false)
            appContext?.let { WatermarkStore.saveGlobal(it, watermark) }
        }
        if (frameWatermarks.any { it.value.enabled }) {
            frameWatermarks = frameWatermarks.mapValues { (_, wm) ->
                if (wm.enabled) wm.copy(enabled = false) else wm
            }
            appContext?.let { WatermarkStore.saveFrameMap(it, frameWatermarks) }
        }
    }

    /**
     * 刷新自动 EXIF 文字。截图 / 二次压缩图往往没有 EXIF，此时必须把上一张图的
     * 机型参数清掉（否则会把旧机打到新照片上），并保留用户原本的 showText 设置，
     * 由设置页显示「此照片没有 EXIF 参数」提示。
     */
    private fun refreshExifTexts() {
        if (watermark.autoExif) {
            val text = ExifReader.formatWatermarkText(exifInfo, watermark.showGps)
            setWatermark(
                WatermarkTarget.Global,
                if (text.isBlank()) watermark.copy(text = "")
                else watermark.copy(text = text, showText = true)
            )
        }
        if (frameWatermarks.any { it.value.autoExif }) {
            frameWatermarks = frameWatermarks.mapValues { (_, wm) ->
                if (!wm.autoExif) wm
                else {
                    val text = ExifReader.formatWatermarkText(exifInfo, wm.showGps)
                    if (text.isBlank()) wm.copy(text = "") else wm.copy(text = text, showText = true)
                }
            }
            appContext?.let { WatermarkStore.saveFrameMap(it, frameWatermarks) }
        }
    }

    fun selectPreset(p: PlatformPreset) {
        preset = p
        ratioIndex = 1
    }

    fun updateOrientation(o: Orientation) {
        orientation = o
        ratioIndex = 1
    }

    fun requestCrop() {
        cropTrigger++
    }

    /**
     * 带边框生成（裁剪模式）：从原图出发，按目标比例创建画布。
     * CONTAIN 保留完整画面，COVER 居中裁切填满，四周由背景填充，不再出现黑边/白边。
     */
    fun generateFramed(): List<Bitmap> {
        val src = bitmap ?: return emptyList()
        val wm = getWatermark(WatermarkTarget.Frame(frameSpec.style))
        val logo = loadLogo(wm)
        val out = FrameRenderer.render(src, frameSpec, ratioValue, frameSpec.outWidth, wm, logo)
        logo?.recycle()
        result = listOf(out)
        return result
    }

    /** 切格只做等分裁切：输出恒为原图像素瓦片，不套用任何边框设置，但套用全局水印 */
    fun generateSlice(): List<Bitmap> {
        val src = bitmap ?: return emptyList()
        val wm = getWatermark(WatermarkTarget.Global)
        val logo = loadLogo(wm)
        val out = ImageProcessor.sliceGrid(src, columns, rows, wm, logo)
        logo?.recycle()
        result = out
        return result
    }
}
