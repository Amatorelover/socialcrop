package com.lihoo.socialcrop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.lihoo.socialcrop.R
import com.lihoo.socialcrop.data.CinemaSubtitleAlign
import com.lihoo.socialcrop.data.CinemaTheme
import com.lihoo.socialcrop.data.FramePalettes
import com.lihoo.socialcrop.data.FrameSpec
import com.lihoo.socialcrop.data.FrameStyle
import com.lihoo.socialcrop.data.StrokeColor
import com.lihoo.socialcrop.ui.components.ColorPickerDialog
import com.lihoo.socialcrop.ui.components.FramePreview
import com.lihoo.socialcrop.ui.materialyou.M3
import com.lihoo.socialcrop.ui.materialyou.M3BottomBar
import com.lihoo.socialcrop.ui.materialyou.M3Card
import com.lihoo.socialcrop.ui.materialyou.M3ColorDots
import com.lihoo.socialcrop.ui.materialyou.M3NavBar
import com.lihoo.socialcrop.ui.materialyou.M3PrimaryButton
import com.lihoo.socialcrop.ui.materialyou.M3Row
import com.lihoo.socialcrop.ui.materialyou.M3SecondaryButton
import com.lihoo.socialcrop.ui.materialyou.M3Section
import com.lihoo.socialcrop.ui.materialyou.M3Segmented
import com.lihoo.socialcrop.ui.materialyou.M3Slider
import com.lihoo.socialcrop.ui.materialyou.M3Switch
import com.lihoo.socialcrop.ui.materialyou.M3TextButton
import com.lihoo.socialcrop.ui.materialyou.M3TextField
import com.lihoo.socialcrop.viewmodel.EditorViewModel
import com.lihoo.socialcrop.viewmodel.EditorViewModel.WatermarkTarget

/**
 * 边框参数页：只显示「当前模式」用得到的参数。
 * 模式本身在编辑页直接平铺切换，这里不重复放模式选择；电影边框走独立布局，
 * 通用留白/圆角/投影等参数对它完全无效，因此该模式下只显示遮罩主题与字幕排版。
 */
@Composable
fun FrameSettingsScreen(nav: NavController, vm: EditorViewModel) {
    val frame = vm.frameSpec
    val src = vm.bitmap
    val p = M3.palette
    var showColorPicker by remember { mutableStateOf(false) }
    val modeName = stringResource(styleLabelRes(frame.style))

    fun doReset() {
        showColorPicker = false
        vm.resetFrame()
    }

    Scaffold(
        containerColor = p.groupedBackground,
        topBar = {
            M3NavBar(
                title = stringResource(R.string.settings_title_mode, modeName),
                onBack = { nav.popBackStack() },
                backText = stringResource(R.string.cd_back)
            )
        },
        bottomBar = {
            M3BottomBar {
                M3SecondaryButton(
                    text = stringResource(R.string.action_reset),
                    onClick = { doReset() },
                    modifier = Modifier.weight(1f)
                )
                M3PrimaryButton(
                    text = stringResource(R.string.action_done),
                    onClick = { finishSettings(nav) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            // 竖屏时预览限高、按比例居中，把更多空间让给下方滚动设置区
            val previewH = (maxHeight * 0.28f).coerceIn(130.dp, 200.dp)
            Column(Modifier.fillMaxSize()) {
                // ===== 常驻实时预览 =====
                if (src != null) {
                    M3Card(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Box(
                            Modifier.fillMaxWidth().padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            FramePreview(
                                src = src,
                                spec = frame,
                                targetRatio = vm.ratioValue,
                                watermark = vm.getWatermark(WatermarkTarget.Frame(frame.style)),
                                exif = vm.exifInfo,
                                // 预览按所选比例成型、限高居中，不铺黑幕、不留空白边
                                modifier = Modifier
                                    .height(previewH)
                                    .aspectRatio(vm.ratioValue.coerceIn(0.25f, 4f))
                            )
                        }
                    }
                    Text(
                        stringResource(
                            R.string.settings_preview_line,
                            vm.ratioPixelLabel,
                            stringResource(R.string.label_lossless),
                            stringResource(presetNameRes(frame.presetName))
                        ),
                        fontSize = 13.sp,
                        color = p.secondaryLabel,
                        lineHeight = 17.sp,
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 4.dp)
                    )
                }

                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 24.dp)
                ) {
                    if (frame.style == FrameStyle.CINEMA) {
                        // 电影边框走独立渲染分支，通用版面参数对它一律无效
                        CinemaSection(frame, vm)
                    } else {
                        PaddingSection(frame, vm)
                        ImageSection(frame, vm)
                        ShapeSection(frame, vm)
                        ShadowSection(frame, vm)
                        StyleSpecificSection(
                            frame = frame,
                            vm = vm,
                            onCustomColor = { showColorPicker = true }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }

    if (showColorPicker) {
        ColorPickerDialog(
            initialColor = frame.customColor,
            onDismiss = { showColorPicker = false },
            onConfirm = { color ->
                vm.tweak { s -> s.copy(customColor = color) }
                showColorPicker = false
            }
        )
    }
}

/** 「返回」回到编辑页；「完成」同样直接回到编辑页（模式在编辑页平铺选择） */
private fun finishSettings(nav: NavController) {
    if (!nav.popBackStack("editor", inclusive = false)) nav.popBackStack()
}

// ================= 各分区（只对适用的模式显示） =================

@Composable
private fun PaddingSection(frame: FrameSpec, vm: EditorViewModel) {
    Section(stringResource(R.string.section_padding)) {
        ParamSlider(
            label = stringResource(R.string.label_uniform_padding),
            value = frame.paddingRatio,
            range = 0f..0.28f,
            display = "${(frame.paddingRatio * 100).toInt()}%",
            onValueChange = { v -> vm.tweak { s -> s.copy(paddingRatio = v) } }
        )
        SwitchRow(
            label = stringResource(R.string.label_bottom_bar),
            subtitle = stringResource(R.string.subtitle_bottom_bar),
            checked = frame.bottomBar,
            onChange = { vm.tweak { s -> s.copy(bottomBar = it) } }
        )
        if (frame.bottomBar) {
            ParamSlider(
                label = stringResource(R.string.label_bottom_bar_height),
                value = frame.bottomBarRatio,
                range = 0.05f..0.30f,
                display = "${(frame.bottomBarRatio * 100).toInt()}%",
                onValueChange = { vm.tweak { s -> s.copy(bottomBarRatio = it) } }
            )
        }
    }
}

@Composable
private fun ImageSection(frame: FrameSpec, vm: EditorViewModel) {
    Section(stringResource(R.string.section_image)) {
        // v2.1.8：适配方式固定「完整显示」，不再提供裁切填满选项
        ParamSlider(
            label = stringResource(R.string.label_image_scale),
            value = frame.contentScale,
            range = 0.5f..1f,
            display = "${(frame.contentScale * 100).toInt()}%",
            onValueChange = { v -> vm.tweak { s -> s.copy(contentScale = v) } }
        )
        ParamSlider(
            label = stringResource(R.string.label_horizontal),
            value = frame.horizontalBias,
            range = -1f..1f,
            display = biasText(frame.horizontalBias, horizontal = true),
            onValueChange = { v -> vm.tweak { s -> s.copy(horizontalBias = v) } }
        )
        ParamSlider(
            label = stringResource(R.string.label_vertical),
            value = frame.verticalBias,
            range = -1f..1f,
            display = biasText(frame.verticalBias, horizontal = false),
            onValueChange = { v -> vm.tweak { s -> s.copy(verticalBias = v) } }
        )
    }
}

@Composable
private fun ShapeSection(frame: FrameSpec, vm: EditorViewModel) {
    Section(stringResource(R.string.section_shape)) {
        ParamSlider(
            label = stringResource(R.string.label_corner),
            value = frame.cornerRatio,
            range = 0f..0.25f,
            display = "${(frame.cornerRatio * 100).toInt()}%",
            onValueChange = { vm.tweak { s -> s.copy(cornerRatio = it) } }
        )
        SwitchRow(
            label = stringResource(R.string.label_stroke),
            subtitle = stringResource(R.string.subtitle_stroke),
            checked = frame.stroke,
            onChange = { vm.tweak { s -> s.copy(stroke = it) } }
        )
        if (frame.stroke) {
            ParamSlider(
                label = stringResource(R.string.label_stroke_width),
                value = frame.strokeWidthRatio,
                range = 0f..0.02f,
                display = "${(frame.strokeWidthRatio * 1000).toInt()}‰",
                onValueChange = { vm.tweak { s -> s.copy(strokeWidthRatio = it) } }
            )
            Caption(stringResource(R.string.label_stroke_color))
            ChipGroupRes(
                items = StrokeColor.entries.map { it to strokeLabelRes(it) },
                selected = frame.strokeColorMode,
                onSelect = { vm.tweak { s -> s.copy(strokeColorMode = it) } }
            )
        }
    }
}

@Composable
private fun ShadowSection(frame: FrameSpec, vm: EditorViewModel) {
    Section(stringResource(R.string.section_shadow)) {
        SwitchRow(
            label = stringResource(R.string.label_shadow),
            checked = frame.shadow,
            onChange = { vm.tweak { s -> s.copy(shadow = it) } }
        )
        if (frame.shadow) {
            // 上限 1.2：渲染器里 strength 会被钳到 1（全黑投影），
            // 旧范围给到 2 导致 60% 之后拉动完全没变化
            ParamSlider(
                label = stringResource(R.string.label_shadow_strength),
                value = frame.shadowStrength.coerceAtMost(1.2f),
                range = 0f..1.2f,
                display = "×${round1(frame.shadowStrength.coerceAtMost(1.2f))}",
                onValueChange = { vm.tweak { s -> s.copy(shadowStrength = it) } }
            )
            ParamSlider(
                label = stringResource(R.string.label_shadow_spread),
                value = frame.shadowSpread,
                range = 0.3f..2.5f,
                display = "×${round1(frame.shadowSpread)}",
                onValueChange = { vm.tweak { s -> s.copy(shadowSpread = it) } }
            )
        }
    }
}

/** 只有当前模式专属的参数 */
@Composable
private fun StyleSpecificSection(
    frame: FrameSpec,
    vm: EditorViewModel,
    onCustomColor: () -> Unit
) {
    Section(stringResource(styleSectionRes(frame.style))) {
        when (frame.style) {
            FrameStyle.BLUR -> {
                ParamSlider(
                    stringResource(R.string.label_blur_strength), frame.blurStrength, 0f..1f,
                    "${(frame.blurStrength * 100).toInt()}%"
                ) { vm.tweak { s -> s.copy(blurStrength = it) } }
                ParamSlider(
                    stringResource(R.string.label_blur_light), frame.blurLight, 0f..1f,
                    "${(frame.blurLight * 100).toInt()}%"
                ) { vm.tweak { s -> s.copy(blurLight = it) } }
                ParamSlider(
                    stringResource(R.string.label_blur_warmth), frame.blurWarmth, 0f..1f,
                    "${(frame.blurWarmth * 100).toInt()}%"
                ) { vm.tweak { s -> s.copy(blurWarmth = it) } }
                ParamSlider(
                    stringResource(R.string.label_blur_dim), frame.blurDim, 0f..0.7f,
                    "${(frame.blurDim * 100).toInt()}%"
                ) { vm.tweak { s -> s.copy(blurDim = it) } }
                ParamSlider(
                    stringResource(R.string.label_blur_zoom), frame.blurZoom, 1f..1.8f,
                    "×${round1(frame.blurZoom)}"
                ) { vm.tweak { s -> s.copy(blurZoom = it) } }
                ParamSlider(
                    stringResource(R.string.label_blur_saturation), frame.blurSaturation, 0f..1f,
                    "${(frame.blurSaturation * 100).toInt()}%"
                ) { vm.tweak { s -> s.copy(blurSaturation = it) } }
                Footnote(stringResource(R.string.blur_tint_hint))
            }
            FrameStyle.GRADIENT -> {
                ParamSlider(
                    stringResource(R.string.label_gradient_angle), frame.gradientAngle, 0f..360f,
                    "${frame.gradientAngle.toInt()}°"
                ) { vm.tweak { s -> s.copy(gradientAngle = it) } }
                SwitchRow(
                    label = stringResource(R.string.label_palette),
                    checked = frame.showPalette,
                    onChange = { vm.tweak { s -> s.copy(showPalette = it) } }
                )
                if (frame.showPalette) {
                    ParamSlider(
                        stringResource(R.string.label_palette_count),
                        frame.paletteCount.toFloat(), 0f..8f,
                        stringResource(R.string.palette_count_display, frame.paletteCount),
                        steps = 7
                    ) { vm.tweak { s -> s.copy(paletteCount = it.toInt()) } }
                    ParamSlider(
                        stringResource(R.string.label_palette_dot_size),
                        frame.paletteDotRatio, 0.06f..1.8f,
                        "${(frame.paletteDotRatio / 0.20f * 100).toInt()}%"
                    ) { vm.tweak { s -> s.copy(paletteDotRatio = it) } }
                    ParamSlider(
                        stringResource(R.string.label_palette_dot_offset),
                        frame.paletteDotOffsetY, -0.06f..0.06f,
                        "${(frame.paletteDotOffsetY * 100).toInt()}%"
                    ) { vm.tweak { s -> s.copy(paletteDotOffsetY = it) } }
                }
                Caption(stringResource(R.string.label_gradient_start))
                ColorDots(
                    colors = FramePalettes.ACCENT_COLORS,
                    selected = frame.gradientStart,
                    onSelect = { vm.tweak { s -> s.copy(gradientStart = it) } }
                )
                M3TextButton(
                    stringResource(R.string.action_start_auto),
                    onClick = { vm.tweak { s -> s.copy(gradientStart = null) } },
                    modifier = Modifier.fillMaxWidth()
                )
                Caption(stringResource(R.string.label_gradient_end))
                ColorDots(
                    colors = FramePalettes.ACCENT_COLORS,
                    selected = frame.gradientEnd,
                    onSelect = { vm.tweak { s -> s.copy(gradientEnd = it) } }
                )
                M3TextButton(
                    stringResource(R.string.action_end_auto),
                    onClick = { vm.tweak { s -> s.copy(gradientEnd = null) } },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            FrameStyle.CUSTOM -> {
                Caption(stringResource(R.string.label_bg_palette))
                ColorDots(
                    colors = FramePalettes.CUSTOM_COLORS,
                    selected = frame.customColor.takeIf { it in FramePalettes.CUSTOM_COLORS },
                    onSelect = { vm.tweak { s -> s.copy(customColor = it) } }
                )
                // 最后一项走系统式色盘：选中色以圆点回显，不再让用户输入 HEX
                M3Row(
                    title = stringResource(R.string.label_custom_color),
                    onClick = onCustomColor,
                    divider = false,
                    trailing = {
                        Box(
                            Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(frame.customColor))
                                .border(1.dp, M3.palette.separator, CircleShape)
                        )
                    }
                )
            }
            // CINEMA 走独立参数页，不会进入本分支；仅满足 when 表达式穷尽
            else -> Unit
        }
    }
}

@Composable
private fun CinemaSection(frame: FrameSpec, vm: EditorViewModel) {
    Section(stringResource(R.string.section_cinema_params)) {
        Caption(stringResource(R.string.label_cinema_theme))
        // 与其余模式一样用按钮式分段控件，不再用圆形渐变色点
        ChipGroupRes(
            items = CinemaTheme.entries.map { it to cinemaThemeLabelRes(it) },
            selected = frame.cinemaTheme,
            onSelect = { vm.tweak { s -> s.copy(cinemaTheme = it) } }
        )
        ParamSlider(
            stringResource(R.string.label_cinema_bar), frame.cinemaBarRatio,
            0f..0.30f,
            "${round1(frame.cinemaBarRatio * 100)}%"
        ) { vm.tweak { s -> s.copy(cinemaBarRatio = it) } }
        Caption(stringResource(R.string.label_subtitle))
        M3TextField(
            value = frame.subtitle,
            onValueChange = { vm.updateSubtitle(it) },
            placeholder = stringResource(R.string.subtitle_placeholder)
        )
        SwitchRow(
            label = stringResource(R.string.label_show_subtitle_secondary),
            checked = frame.showSubtitleSecondary,
            onChange = { vm.updateShowSubtitleSecondary(it) }
        )
        if (frame.showSubtitleSecondary) {
            M3TextField(
                value = frame.subtitleSecondary,
                onValueChange = { vm.updateSubtitleSecondary(it) },
                placeholder = stringResource(R.string.subtitle_secondary_placeholder)
            )
            Footnote(stringResource(R.string.subtitle_translation_hint))
        }
        ParamSlider(
            stringResource(R.string.label_subtitle_size), frame.subtitleSizeRatio,
            0.015f..0.09f,
            "${round1(frame.subtitleSizeRatio * 100)}%"
        ) { vm.tweak { s -> s.copy(subtitleSizeRatio = it) } }
        ParamSlider(
            stringResource(R.string.label_subtitle_letter_spacing), frame.subtitleLetterSpacing,
            -0.05f..0.5f,
            round1(frame.subtitleLetterSpacing)
        ) { vm.tweak { s -> s.copy(subtitleLetterSpacing = it) } }
        ParamSlider(
            stringResource(R.string.label_subtitle_line_gap), frame.subtitleLineGap,
            0f..0.8f,
            round1(frame.subtitleLineGap)
        ) { vm.tweak { s -> s.copy(subtitleLineGap = it) } }
        Caption(stringResource(R.string.label_subtitle_align))
        ChipGroupRes(
            items = CinemaSubtitleAlign.entries.map { it to cinemaAlignLabelRes(it) },
            selected = frame.subtitleAlign,
            onSelect = { vm.tweak { s -> s.copy(subtitleAlign = it) } }
        )
        Footnote(stringResource(R.string.cinema_hint))
    }
}

// ================= 复用控件（iOS 分组样式） =================

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    M3Section(title = title, content = content)
}

@Composable
private fun Caption(text: String) {
    Text(
        text,
        fontSize = 13.sp,
        color = M3.palette.secondaryLabel,
        modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 4.dp)
    )
}

@Composable
private fun Footnote(text: String) {
    Text(
        text,
        fontSize = 13.sp,
        color = M3.palette.secondaryLabel,
        lineHeight = 17.sp,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
    )
}

@Composable
private fun <T> ChipGroup(
    items: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit
) {
    Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        M3Segmented(
            items = items.map { it.first },
            selected = selected,
            onSelect = onSelect,
            label = { value -> items.first { it.first == value }.second },
            scroll = true
        )
    }
}

/** 标签来自字符串资源：语言切换后文案自动跟随 */
@Composable
private fun <T> ChipGroupRes(
    items: List<Pair<T, Int>>,
    selected: T,
    onSelect: (T) -> Unit
) {
    Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        M3Segmented(
            items = items.map { it.first },
            selected = selected,
            onSelect = onSelect,
            label = { value -> stringResource(items.first { it.first == value }.second) },
            scroll = true
        )
    }
}

@Composable
private fun ParamSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    display: String,
    steps: Int = 0,
    onValueChange: (Float) -> Unit
) {
    M3Slider(
        value = value,
        range = range,
        onValueChange = onValueChange,
        label = label,
        display = display,
        steps = steps
    )
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    subtitle: String? = null
) {
    M3Row(
        title = label,
        subtitle = subtitle,
        trailing = { M3Switch(checked = checked, onCheckedChange = onChange) }
    )
}

@Composable
private fun ColorDots(
    colors: List<Int>,
    selected: Int?,
    onSelect: (Int) -> Unit
) {
    M3ColorDots(colors = colors, selected = selected, onSelect = onSelect)
}

private fun round1(v: Float): String = "%.1f".format(v)
