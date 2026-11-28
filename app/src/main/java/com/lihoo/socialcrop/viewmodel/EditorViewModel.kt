package com.lihoo.socialcrop.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.lihoo.socialcrop.data.BuiltinLogos
import com.lihoo.socialcrop.data.FrameSettingsStore
import com.lihoo.socialcrop.data.FramePresets
import com.lihoo.socialcrop.data.FrameSpec
import com.lihoo.socialcrop.data.FrameStyle
import com.lihoo.socialcrop.data.Orientation
import com.lihoo.socialcrop.data.PlatformPreset
import com.lihoo.socialcrop.data.Presets
import com.lihoo.socialcrop.data.RatioOption
import com.lihoo.socialcrop.data.WatermarkAlign
import com.lihoo.socialcrop.data.WatermarkBlockAlign
import com.lihoo.socialcrop.data.WatermarkMoveTarget
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
    // 0 = 「原比例」：换图默认完整显示、不裁切
    var ratioIndex by mutableStateOf(0)
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

    /** 二选一互斥提示：每次用户操作自动关掉另一侧时递增，UI 侧弹一次短 Toast */
    enum class ExclusiveSide { EXIF, TEXT }
    data class ExclusiveNotice(val seq: Int, val side: ExclusiveSide)
    var exclusiveNotice by mutableStateOf<ExclusiveNotice?>(null)
        private set

    /** 通用对齐选「两边对齐」但内容组合不符合要求时递增，UI 弹一次短提示 */
    var alignmentNotice by mutableStateOf(0)
        private set

    var result by mutableStateOf<List<Bitmap>>(emptyList())
    var resultUris by mutableStateOf<List<Uri>>(emptyList())

    private var appContext: Context? = null

    /**
     * 未单独配置水印的边框模式共用一个不可变默认实例。
     * 旧版每次 get 都 new 一个，会让预览的 LaunchedEffect 在无关重组时误判参数变化并重渲染。
     */
    private val defaultWatermark = WatermarkSpec()

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
        // v2.7.0：旧存档可能同时开着「照片参数 + 文字水印 + Logo」，读入时按新互斥规则收敛一次
        watermark = normalizeExclusive(WatermarkStore.loadGlobal(app), WatermarkSpec())
        frameWatermarks = WatermarkStore.loadFrameMap(app).mapValues { (_, wm) ->
            normalizeExclusive(wm, WatermarkSpec())
        }
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
        is WatermarkTarget.Frame -> frameWatermarks[target.style] ?: defaultWatermark
    }

    fun setWatermark(target: WatermarkTarget, spec: WatermarkSpec) {
        // v2.7.0：开启 Logo 后照片参数与文字水印互斥，写入前统一收敛一次；
        // v2.15.0：两边对齐只在合法组合下保留，内容变化后立即失效则回落居中。
        val exclusive = normalizeExclusive(spec, getWatermark(target))
        val norm = if (exclusive.blockAlign == WatermarkBlockAlign.SIDES &&
            !sidesLayoutValid(exclusive)
        ) exclusive.copy(blockAlign = WatermarkBlockAlign.CENTER) else exclusive
        // v2.8.0：互斥真的关掉了某一侧时发一条事件，UI 弹短提示告诉用户为什么
        val textKilled = (spec.authorOn || spec.timeOn) && !norm.authorOn && !norm.timeOn
        val exifKilled = (spec.deviceOn || spec.lensOn) && !norm.deviceOn && !norm.lensOn
        if (textKilled || exifKilled) {
            exclusiveNotice = ExclusiveNotice(
                seq = (exclusiveNotice?.seq ?: 0) + 1,
                side = if (textKilled) ExclusiveSide.TEXT else ExclusiveSide.EXIF
            )
        }
        when (target) {
            WatermarkTarget.Global -> {
                watermark = norm
                appContext?.let { WatermarkStore.saveGlobal(it, norm) }
            }
            is WatermarkTarget.Frame -> {
                frameWatermarks = frameWatermarks + (target.style to norm)
                appContext?.let { WatermarkStore.saveFrameMap(it, frameWatermarks) }
            }
        }
    }

    /**
     * 开启 Logo 图片后，照片参数(deviceOn/lensOn) 与文字水印(authorOn/timeOn)
     * 只能再开启一个：打开其中一侧时自动关掉另一侧，保证水印块最多两行。
     * 关闭 Logo 后两类可自由叠加，不做限制。
     */
    private fun normalizeExclusive(next: WatermarkSpec, prev: WatermarkSpec): WatermarkSpec {
        if (next.logoPath.isBlank()) return next
        val exifOn = next.deviceOn || next.lensOn
        val textOn = next.authorOn || next.timeOn
        if (!exifOn || !textOn) return next
        val prevExif = prev.deviceOn || prev.lensOn
        val prevText = prev.authorOn || prev.timeOn
        return when {
            // 之前只有文字 → 刚打开的是照片参数：保留照片参数，关文字
            prevText && !prevExif -> next.copy(authorOn = false, timeOn = false)
            // 之前只有照片参数 → 刚打开的是文字：保留文字，关照片参数
            prevExif && !prevText -> next.copy(deviceOn = false, lensOn = false)
            // 刚设置 Logo 且两类都开着 → 按参考图保留「Logo + 照片参数」
            else -> next.copy(authorOn = false, timeOn = false)
        }
    }

    fun setWatermarkEnabled(target: WatermarkTarget, on: Boolean) =
        setWatermark(target, getWatermark(target).copy(enabled = on))

    /** 「恢复默认」：整项水印回到出厂配置（关闭、无内容），并删掉已复制的 Logo 文件 */
    fun resetWatermark(target: WatermarkTarget) {
        val old = getWatermark(target)
        if (old.logoPath.isNotBlank()) deleteLogoFiles(old.logoPath)
        setWatermark(target, WatermarkSpec())
    }

    /**
     * 照片参数·手机型号（第一行）独立开关。v2.21：打开时立刻从当前照片 EXIF 识别一次并写入
     * deviceText，设置页下方的文本框可直接修改；用户没动过的自动值在 Logo 增减时会同步刷新
     * （有 Logo 只留机型后缀，避免品牌重复）。
     */
    fun setDeviceOn(target: WatermarkTarget, on: Boolean) {
        val cur = getWatermark(target)
        val device = if (on && cur.deviceText.isBlank()) autoDeviceText(cur) else cur.deviceText
        setWatermark(target, cur.copy(deviceOn = on, deviceText = device))
    }

    /** 重新从照片 EXIF 识别机型文字，覆盖文本框当前内容（用户主动点击才触发）。 */
    fun refreshDeviceText(target: WatermarkTarget) {
        val cur = getWatermark(target)
        setWatermark(target, cur.copy(deviceText = autoDeviceText(cur)))
    }

    /** 当前 EXIF 下的机型文字：有 Logo 时只留后缀，避免品牌与 Logo 重复。 */
    private fun autoDeviceText(wm: WatermarkSpec): String =
        if (wm.logoPath.isNotBlank()) ExifReader.formatModelLine(exifInfo)
        else ExifReader.formatDeviceLine(exifInfo)

    /**
     * Logo 增减后：若机型文本框仍是「自动识别值」或空白，按新的 Logo 状态重新识别；
     * 用户自己改过的文字原样保留。
     */
    private fun syncDeviceTextForLogo(before: WatermarkSpec, after: WatermarkSpec): WatermarkSpec {
        if (!after.deviceOn) return after
        if (before.deviceText.isNotBlank() && before.deviceText != autoDeviceText(before)) return after
        return after.copy(deviceText = autoDeviceText(after))
    }

    /** 照片参数·镜头参数（第二行）独立开关。 */
    fun setLensOn(target: WatermarkTarget, on: Boolean) =
        setWatermark(target, getWatermark(target).copy(lensOn = on))

    /** 作者名字开关：可独立关闭，不影响时间戳 */
    fun setAuthorOn(target: WatermarkTarget, on: Boolean) {
        setWatermark(target, getWatermark(target).copy(authorOn = on))
    }

    /**
     * 时间戳开关：首次打开时立刻生成并持久化一次，保证预览与导出一致；
     * 之后关闭再打开沿用已生成的时间，除非用户主动点「重新生成」。
     */
    fun setTimeOn(target: WatermarkTarget, on: Boolean) {
        val cur = getWatermark(target)
        val time = if (on && cur.timeText.isBlank()) {
            ExifReader.formatDateLine(exifInfo).ifBlank { ExifReader.formatNow() }
        } else cur.timeText
        setWatermark(target, cur.copy(timeOn = on, timeText = time))
    }

    /** 时间戳重新取当前照片的拍摄时间；没有 EXIF 时取当下本机时间。 */
    fun refreshTimeText(target: WatermarkTarget) {
        val cur = getWatermark(target)
        setWatermark(
            target,
            cur.copy(timeText = ExifReader.formatDateLine(exifInfo).ifBlank { ExifReader.formatNow() })
        )
    }

    /**
     * 通用设置的「整体位置」：一次移动整个水印块，每次 5% 短边（大步长，区别于各类 0.5% 微调），
     * 限制在 ±40% 内。与三类各自微调相互独立、可叠加。
     */
    fun moveWatermarkBlock(target: WatermarkTarget, dx: Float, dy: Float) {
        val cur = getWatermark(target)
        setWatermark(
            target,
            cur.copy(
                blockOffsetX = (cur.blockOffsetX + dx * 0.05f).coerceIn(-0.4f, 0.4f),
                blockOffsetY = (cur.blockOffsetY + dy * 0.05f).coerceIn(-0.4f, 0.4f)
            )
        )
    }

    /** 复位整个水印块的位置（不动三类各自微调） */
    fun resetWatermarkBlock(target: WatermarkTarget) {
        setWatermark(target, getWatermark(target).copy(blockOffsetX = 0f, blockOffsetY = 0f))
    }

    /** 三类水印位置微调：每次移动 0.5% 短边（细粒度），限制在 ±40% 内，避免移出画面找不回来。 */
    fun moveWatermark(target: WatermarkTarget, which: WatermarkMoveTarget, dx: Float, dy: Float) {
        val cur = getWatermark(target)
        val nx = { v: Float -> (v + dx * 0.005f).coerceIn(-0.4f, 0.4f) }
        val ny = { v: Float -> (v + dy * 0.005f).coerceIn(-0.4f, 0.4f) }
        val spec = when (which) {
            WatermarkMoveTarget.LOGO -> cur.copy(
                logoOffsetX = nx(cur.logoOffsetX), logoOffsetY = ny(cur.logoOffsetY)
            )
            WatermarkMoveTarget.TEXT -> cur.copy(
                textOffsetX = nx(cur.textOffsetX), textOffsetY = ny(cur.textOffsetY)
            )
            WatermarkMoveTarget.EXIF -> cur.copy(
                exifOffsetX = nx(cur.exifOffsetX), exifOffsetY = ny(cur.exifOffsetY)
            )
        }
        setWatermark(target, spec)
    }

    /** 三类水印各自的水平对齐：左 / 中 / 右，默认居中，互不干扰 */
    fun setWatermarkAlign(
        target: WatermarkTarget,
        which: WatermarkMoveTarget,
        align: WatermarkAlign
    ) {
        val cur = getWatermark(target)
        val spec = when (which) {
            WatermarkMoveTarget.LOGO -> cur.copy(logoAlign = align)
            WatermarkMoveTarget.TEXT -> cur.copy(textAlign = align)
            WatermarkMoveTarget.EXIF -> cur.copy(exifAlign = align)
        }
        setWatermark(target, spec)
    }

    /** 两边对齐的合法组合：必须有 Logo，且文字 / 照片参数恰好只有一类开启 */
    private fun sidesLayoutValid(wm: WatermarkSpec): Boolean {
        val hasLogo = wm.logoPath.isNotBlank()
        val hasText = wm.authorOn || wm.timeOn
        val hasExif = wm.deviceOn || wm.lensOn
        return hasLogo && (hasText != hasExif)
    }

    /**
     * 通用设置里的整体对齐。用户点「两边对齐」但组合不对时，弹提示且不应用，
     * 避免悄悄改用户当前配置；后续内容变化若让 SIDES 失效，则在 setWatermark 里回落居中。
     */
    fun setWatermarkBlockAlign(
        target: WatermarkTarget,
        align: WatermarkBlockAlign
    ) {
        if (align == WatermarkBlockAlign.SIDES && !sidesLayoutValid(getWatermark(target))) {
            alignmentNotice++
            return
        }
        setWatermark(target, getWatermark(target).copy(blockAlign = align))
    }

    /** 三类水印各自的大小滑块：互不影响，各自记忆 */
    fun setWatermarkScale(target: WatermarkTarget, which: WatermarkMoveTarget, value: Float) {
        val cur = getWatermark(target)
        val spec = when (which) {
            WatermarkMoveTarget.LOGO -> cur.copy(logoScaleRatio = value)
            WatermarkMoveTarget.TEXT -> cur.copy(textScaleRatio = value)
            WatermarkMoveTarget.EXIF -> cur.copy(exifScaleRatio = value)
        }
        setWatermark(target, spec)
    }

    /**
     * v2.13.0：更换 Logo 时清除所有「位置 / 大小 / 旋转」记忆，回到出厂对齐标准；
     * 作者 / 时间 / 机型 / 镜头等内容开关与文字保留，避免用户重填。
     */
    private fun resetLayoutForNewLogo(wm: WatermarkSpec): WatermarkSpec = wm.copy(
        logoScaleRatio = WatermarkSpec.DEFAULT_SCALE_RATIO,
        textScaleRatio = WatermarkSpec.DEFAULT_SCALE_RATIO,
        exifScaleRatio = WatermarkSpec.DEFAULT_SCALE_RATIO,
        rotation = 0f,
        blockOffsetX = 0f, blockOffsetY = 0f,
        logoOffsetX = 0f, logoOffsetY = 0f,
        textOffsetX = 0f, textOffsetY = 0f,
        exifOffsetX = 0f, exifOffsetY = 0f,
        logoAlign = WatermarkAlign.CENTER,
        textAlign = WatermarkAlign.CENTER,
        exifAlign = WatermarkAlign.CENTER
    )

    /** 单独复位某类水印的位移 */
    fun resetWatermarkOffset(target: WatermarkTarget, which: WatermarkMoveTarget) {
        val cur = getWatermark(target)
        val spec = when (which) {
            WatermarkMoveTarget.LOGO -> cur.copy(logoOffsetX = 0f, logoOffsetY = 0f)
            WatermarkMoveTarget.TEXT -> cur.copy(textOffsetX = 0f, textOffsetY = 0f)
            WatermarkMoveTarget.EXIF -> cur.copy(exifOffsetX = 0f, exifOffsetY = 0f)
        }
        setWatermark(target, spec)
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
        val before = getWatermark(target)
        setWatermark(
            target,
            syncDeviceTextForLogo(
                before,
                resetLayoutForNewLogo(before).copy(logoPath = dest.absolutePath, logoBrandId = "")
            )
        )
        if (old.isNotBlank() && old != dest.absolutePath) deleteLogoFiles(old)
    }

    /** 从 APK 内置 assets 复制品牌 Logo 到私有目录（离线、零权限），之后走与普通 Logo 相同的渲染链。 */
    fun setWatermarkBuiltinLogo(target: WatermarkTarget, context: Context, brandId: String) {
        if (BuiltinLogos.find(brandId) == null) return
        val app = context.applicationContext
        val dir = File(app.filesDir, "watermark").apply { mkdirs() }
        val stamp = System.currentTimeMillis()
        val dest = File(dir, "logo_brand_${brandId}_${stamp}.png")
        val monoDest = File(dir, "logo_brand_${brandId}_${stamp}_mono.png")
        val copied = runCatching {
            app.assets.open(BuiltinLogos.assetPath(brandId)).use { input ->
                dest.outputStream().use { out -> input.copyTo(out) }
            }
            // 同时复制去背白模：黑白单色化时用它，避免整块底色被刷成一坨
            runCatching {
                app.assets.open(BuiltinLogos.monoAssetPath(brandId)).use { input ->
                    monoDest.outputStream().use { out -> input.copyTo(out) }
                }
            }
            dest.length() > 0L
        }.getOrDefault(false)
        if (!copied) {
            dest.delete()
            monoDest.delete()
            return
        }
        val old = getWatermark(target).logoPath
        val oldId = getWatermark(target).logoBrandId
        if (oldId == brandId && old.isNotBlank()) {
            // 同一品牌重复点击：保留原文件与已有配置即可
            dest.delete()
            monoDest.delete()
            return
        }
        val before = getWatermark(target)
        setWatermark(
            target,
            syncDeviceTextForLogo(
                before,
                resetLayoutForNewLogo(before).copy(
                    logoPath = dest.absolutePath,
                    logoBrandId = brandId
                )
            )
        )
        if (old.isNotBlank() && old != dest.absolutePath) deleteLogoFiles(old)
    }

    /** 删除 Logo 主文件及其去背白模兄弟文件 */
    private fun deleteLogoFiles(path: String) {
        File(path).delete()
        val f = File(path)
        File(f.parentFile, f.nameWithoutExtension + "_mono.png").delete()
    }

    fun clearWatermarkLogo(target: WatermarkTarget) {
        val before = getWatermark(target)
        val old = before.logoPath
        if (old.isNotBlank()) deleteLogoFiles(old)
        setWatermark(
            target,
            syncDeviceTextForLogo(before, before.copy(logoPath = "", logoBrandId = ""))
        )
    }

    /** 纯裁剪模式下，最终裁切结果由 CropView 产出，这里负责在导出前套上全局水印 */
    fun applyWatermark(bmp: Bitmap): Bitmap = drawWatermarkOn(bmp, getWatermark(WatermarkTarget.Global))

    private fun drawWatermarkOn(bmp: Bitmap, wm: WatermarkSpec): Bitmap {
        if (!wm.enabled) return bmp
        val logo = loadLogo(wm)
        val out = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawBitmap(bmp, 0f, 0f, null)
        // 无边框导出与带边框共用底部留白带的排版/字号钳制逻辑，所见即所得
        WatermarkRenderer.drawOnPlainImage(c, wm, bmp.width, bmp.height, logo, exifInfo)
        logo?.recycle()
        return out
    }

    private fun loadLogo(wm: WatermarkSpec): Bitmap? {
        if (wm.logoPath.isBlank()) return null
        return decodeLogo(wm.logoPath, tint = wm.logoTint)
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
        // v2.13.0：每次上传新图都按图片自身方向给默认值（横图→横屏、竖图→竖屏），不沿用上一张的选择。
        // 注意直接改字段，不能调用 updateOrientation()——后者会把 ratioIndex 推到 1，破坏「原比例」默认。
        if (bmp != null) {
            orientation = if (bmp.width >= bmp.height) Orientation.LANDSCAPE else Orientation.PORTRAIT
        }
        // 每次换图都回到「原比例」：完整显示、不裁切
        ratioIndex = 0
        exifInfo = ExifReader.read(context, uri)
        // 换图 = 全新水印：全局 + 各边框模式全部回到出厂默认（全关、无 Logo、无文字），
        // 并删掉上一张图复制进私有目录的 Logo，避免旧水印残留。
        resetWatermarksForNewImage()
        // v2.16.0：换图同样让边框回到出厂默认（细白边 + 底部留白带），
        // 不沿用上一张图的留白/圆角/背景色；同步清掉「关闭边框前的缓存」，避免再选同一模式又恢复旧参数。
        resetFrameForNewImage()
        resetCropCenter()
        return bmp
    }

    /** 每次换图把边框恢复出厂默认并立即持久化（含清掉关闭缓存） */
    private fun resetFrameForNewImage() {
        disabledFrame = null
        frameSpec = FrameSpec()
        appContext?.let { FrameSettingsStore.save(it, frameSpec) }
    }

    /** 每次换图把全部水印（含 Logo 文件）清空并立即持久化 */
    private fun resetWatermarksForNewImage() {
        if (watermark.logoPath.isNotBlank()) deleteLogoFiles(watermark.logoPath)
        frameWatermarks.values.forEach { wm ->
            if (wm.logoPath.isNotBlank()) deleteLogoFiles(wm.logoPath)
        }
        watermark = WatermarkSpec()
        frameWatermarks = emptyMap()
        appContext?.let { ctx ->
            WatermarkStore.saveGlobal(ctx, watermark)
            WatermarkStore.saveFrameMap(ctx, emptyMap())
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
        val out = FrameRenderer.render(src, frameSpec, ratioValue, frameSpec.outWidth, wm, logo, exifInfo)
        logo?.recycle()
        result = listOf(out)
        return result
    }

    /** 切格只做等分裁切：输出恒为原图像素瓦片，不套用任何边框设置，但套用全局水印 */
    fun generateSlice(): List<Bitmap> {
        val src = bitmap ?: return emptyList()
        val wm = getWatermark(WatermarkTarget.Global)
        val logo = loadLogo(wm)
        val out = ImageProcessor.sliceGrid(src, columns, rows, wm, logo, exifInfo)
        logo?.recycle()
        result = out
        return result
    }
}
