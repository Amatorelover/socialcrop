package com.lihoo.socialcrop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.lihoo.socialcrop.R
import com.lihoo.socialcrop.data.FrameStyle
import com.lihoo.socialcrop.data.Orientation
import com.lihoo.socialcrop.data.Presets
import com.lihoo.socialcrop.domain.ImageProcessor
import com.lihoo.socialcrop.ui.components.CropView
import com.lihoo.socialcrop.ui.components.FramePreview
import com.lihoo.socialcrop.ui.components.SlicePreview
import com.lihoo.socialcrop.ui.components.WatermarkPreview
import com.lihoo.socialcrop.ui.components.hasPreviewContent
import com.lihoo.socialcrop.ui.materialyou.M3
import com.lihoo.socialcrop.ui.materialyou.M3BottomBar
import com.lihoo.socialcrop.ui.materialyou.M3Chevron
import com.lihoo.socialcrop.ui.materialyou.M3NavBar
import com.lihoo.socialcrop.ui.materialyou.M3PrimaryButton
import com.lihoo.socialcrop.ui.materialyou.M3Row
import com.lihoo.socialcrop.ui.materialyou.M3Section
import com.lihoo.socialcrop.ui.materialyou.M3Segmented
import com.lihoo.socialcrop.ui.materialyou.M3Stepper
import com.lihoo.socialcrop.ui.materialyou.M3Switch
import com.lihoo.socialcrop.ui.materialyou.m3Tap
import com.lihoo.socialcrop.viewmodel.EditorViewModel
import com.lihoo.socialcrop.viewmodel.EditorViewModel.WatermarkTarget

@Composable
fun EditorScreen(nav: NavController, vm: EditorViewModel) {
    val src = vm.bitmap
    val frame = vm.frameSpec
    val p = M3.palette
    val isCrop = vm.mode == EditorViewModel.Mode.CROP

    Scaffold(
        containerColor = p.groupedBackground,
        topBar = {
            M3NavBar(
                title = stringResource(R.string.editor_title),
                onBack = { nav.popBackStack() },
                backText = stringResource(R.string.cd_back)
            )
        },
        bottomBar = {
            M3BottomBar {
                M3PrimaryButton(
                    text = stringResource(R.string.action_generate),
                    enabled = src != null,
                    onClick = {
                        if (isCrop) {
                            if (frame.enabled) {
                                vm.generateFramed()
                                nav.navigate("result") { launchSingleTop = true }
                            } else {
                                vm.requestCrop()
                            }
                        } else {
                            vm.generateSlice()
                            nav.navigate("result") { launchSingleTop = true }
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    ) { padding ->
        if (src == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.editor_no_image), color = p.secondaryLabel, fontSize = 17.sp)
            }
            return@Scaffold
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            // ===== 模式：UISegmentedControl 取代 FilterChip =====
            M3Section(title = stringResource(R.string.editor_section_mode)) {
                Box(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    M3Segmented(
                        items = listOf(EditorViewModel.Mode.CROP, EditorViewModel.Mode.SLICE),
                        selected = vm.mode,
                        onSelect = { vm.mode = it },
                        label = {
                            stringResource(
                                if (it == EditorViewModel.Mode.CROP) R.string.mode_crop
                                else R.string.mode_slice
                            )
                        }
                    )
                }
            }

            // ===== 水印：纯裁剪 / 切格走全局配置；边框模式的水印在边框参数页内单独设置 =====
            if (!frame.enabled) {
                val wmGlobal = vm.getWatermark(WatermarkTarget.Global)
                M3Section(title = stringResource(R.string.label_watermark)) {
                    M3Row(
                        title = if (wmGlobal.enabled) stringResource(R.string.watermark_on)
                        else stringResource(R.string.watermark_off),
                        subtitle = watermarkSummary(wmGlobal),
                        trailing = {
                            M3Switch(
                                checked = wmGlobal.enabled,
                                onCheckedChange = { vm.setWatermarkEnabled(WatermarkTarget.Global, it) }
                            )
                        }
                    )
                    M3Row(
                        title = stringResource(R.string.action_watermark_settings),
                        onClick = { nav.navigate("watermark?target=global") },
                        divider = wmGlobal.enabled,
                        trailing = { M3Chevron() }
                    )
                    if (wmGlobal.enabled) {
                        Box(Modifier.padding(12.dp)) {
                            WatermarkPreview(
                                src = src,
                                watermark = wmGlobal,
                                frame = null,
                                targetRatio = vm.ratioValue,
                                slice = if (!isCrop) vm.columns to vm.rows else null,
                                cropCenterX = vm.cropCenterX,
                                cropCenterY = vm.cropCenterY,
                                modifier = Modifier.fillMaxWidth().aspectRatio(
                                    (if (isCrop) vm.ratioValue else {
                                        val plan = ImageProcessor.planGrid(src.width, src.height, vm.columns, vm.rows)
                                        plan.cellW.toFloat() / plan.cellH.coerceAtLeast(1)
                                    }).coerceIn(0.25f, 4f)
                                )
                            )
                        }
                        if (!wmGlobal.hasPreviewContent()) {
                            Text(
                                stringResource(R.string.wm_preview_hint),
                                fontSize = 13.sp,
                                color = p.secondaryLabel,
                                lineHeight = 17.sp,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            if (isCrop) {
                // ===== 画布：平台 / 方向 / 比例 =====
                M3Section(title = stringResource(R.string.editor_section_canvas)) {
                    Text(
                        stringResource(R.string.label_platform),
                        fontSize = 13.sp,
                        color = p.secondaryLabel,
                        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 8.dp)
                    )
                    Box(Modifier.padding(horizontal = 16.dp)) {
                        M3Segmented(
                            items = Presets.ALL,
                            selected = vm.preset,
                            onSelect = { vm.selectPreset(it) },
                            label = { platformName(it.id) },
                            scroll = true
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        stringResource(R.string.label_orientation_group),
                        fontSize = 13.sp,
                        color = p.secondaryLabel,
                        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                    )
                    Box(Modifier.padding(horizontal = 16.dp)) {
                        M3Segmented(
                            items = Orientation.entries,
                            selected = vm.orientation,
                            onSelect = { vm.updateOrientation(it) },
                            label = {
                                stringResource(
                                    if (it == Orientation.PORTRAIT) R.string.orientation_portrait
                                    else R.string.orientation_landscape
                                )
                            }
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        stringResource(
                            R.string.label_ratio,
                            ratioLabel(vm.currentRatio),
                            vm.ratioPixelLabel
                        ),
                        fontSize = 13.sp,
                        color = p.secondaryLabel,
                        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                    )
                    Box(Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) {
                        M3Segmented(
                            items = vm.ratioOptions.indices.toList(),
                            selected = vm.ratioIndex,
                            onSelect = { vm.ratioIndex = it },
                            label = { ratioLabel(vm.ratioOptions[it]) },
                            scroll = true
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }

                // ===== 边框：4 种模式直接平铺，不再有二级「更多模式」菜单 =====
                M3Section(title = stringResource(R.string.label_frame)) {
                    val modes = listOf(
                        FrameStyle.BLUR, FrameStyle.GRADIENT,
                        FrameStyle.CINEMA, FrameStyle.CUSTOM
                    )
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        modes.chunked(2).forEachIndexed { rowIdx, row ->
                            if (rowIdx > 0) Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { style ->
                                    FrameModeTile(
                                        style = style,
                                        selected = frame.enabled && frame.style == style,
                                        onClick = {
                                            if (!frame.enabled || frame.style != style) {
                                                vm.selectFrameStyle(style)
                                            }
                                            nav.navigate("frame_settings")
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                    if (frame.enabled) {
                        M3Row(
                            title = stringResource(R.string.label_current, styleLabel(frame.style)),
                            subtitle = frameSummary(frame),
                            onClick = { nav.navigate("frame_settings") },
                            trailing = { M3Chevron() }
                        )
                        M3Row(
                            title = stringResource(R.string.action_no_frame),
                            onClick = { vm.updateFrame(frame.copy(enabled = false)) },
                            divider = false
                        )
                    }
                }

                // 边框开启时给水印一个直达入口；所有水印设置只在独立水印页，参数页不再放水印开关
                if (frame.enabled) {
                    M3Section(title = stringResource(R.string.label_watermark)) {
                        M3Row(
                            title = stringResource(R.string.action_watermark_settings),
                            onClick = { nav.navigate("watermark?target=${frame.style.name}") },
                            divider = false,
                            trailing = { M3Chevron() }
                        )
                    }
                }
            } else {
                // ===== 切格：只有列数与行数 =====
                M3Section(title = stringResource(R.string.editor_section_grid)) {
                    M3Row(
                        title = stringResource(R.string.slice_columns),
                        value = "${vm.columns}",
                        trailing = {
                            M3Stepper(
                                value = vm.columns,
                                min = 1,
                                max = ImageProcessor.MAX_COLS,
                                onChange = { vm.columns = it }
                            )
                        }
                    )
                    M3Row(
                        title = stringResource(R.string.slice_rows),
                        value = "${vm.rows}",
                        divider = false,
                        trailing = {
                            M3Stepper(
                                value = vm.rows,
                                min = 1,
                                max = ImageProcessor.MAX_ROWS,
                                onChange = { vm.rows = it }
                            )
                        }
                    )
                }
            }

            // ===== 预览：与导出共用渲染链 =====
            val previewHint = if (isCrop) {
                if (frame.enabled) {
                    if (vm.currentRatio.isOriginal) stringResource(R.string.frame_preview_hint_original)
                    else stringResource(R.string.frame_preview_hint, vm.ratioPixelLabel)
                } else {
                    if (vm.currentRatio.isOriginal) stringResource(R.string.crop_hint_original, vm.ratioPixelLabel)
                    else stringResource(R.string.crop_hint, vm.ratioPixelLabel)
                }
            } else {
                val plan = ImageProcessor.planGrid(src.width, src.height, vm.columns, vm.rows)
                stringResource(
                    R.string.slice_hint_plain,
                    plan.cols, plan.rows, plan.count, plan.cellW, plan.cellH
                )
            }

            M3Section(
                title = stringResource(R.string.editor_section_preview),
                footer = previewHint
            ) {
                Box(Modifier.padding(12.dp)) {
                    // 预览按真实输出比例成型：宽满屏、高由比例决定，不再套固定尺寸的黑幕框
                    val srcRatio = src.width.toFloat() / src.height.coerceAtLeast(1)
                    val previewRatio = (if (isCrop && frame.enabled) vm.ratioValue else srcRatio)
                        .coerceIn(0.25f, 4f)
                    val previewModifier = Modifier.fillMaxWidth().aspectRatio(previewRatio)
                    if (isCrop) {
                        if (frame.enabled) {
                            FramePreview(
                                src = src,
                                spec = frame,
                                targetRatio = vm.ratioValue,
                                watermark = vm.getWatermark(WatermarkTarget.Frame(frame.style)),
                                modifier = previewModifier
                            )
                        } else {
                            CropView(
                                bitmap = src,
                                targetRatio = vm.ratioValue,
                                modifier = previewModifier,
                                centerX = vm.cropCenterX,
                                centerY = vm.cropCenterY,
                                onCenterChange = { x, y -> vm.updateCropCenter(x, y) },
                                cropTrigger = vm.cropTrigger,
                                onCropped = { bmp ->
                                    vm.result = listOf(vm.applyWatermark(bmp))
                                    nav.navigate("result") { launchSingleTop = true }
                                }
                            )
                        }
                    } else {
                        SlicePreview(
                            src = src,
                            cols = vm.columns,
                            rows = vm.rows,
                            modifier = previewModifier
                        )
                    }
                }
                if (!isCrop) {
                    val plan = ImageProcessor.planGrid(src.width, src.height, vm.columns, vm.rows)
                    if (plan.count > 30) {
                        Text(
                            stringResource(R.string.slice_hint_many, plan.count),
                            fontSize = 13.sp,
                            color = p.red,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                } else if (!frame.enabled && !vm.currentRatio.isOriginal) {
                    M3Row(
                        title = stringResource(R.string.action_recenter),
                        onClick = { vm.resetCropCenter() },
                        divider = false
                    )
                }
            }
        }
    }
}

/** 编辑页里的模式磁贴：色块示意 + 名称，选中态用主色描边；2×2 等分铺开 */
@Composable
private fun FrameModeTile(
    style: FrameStyle,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val p = M3.palette
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier
            .clip(shape)
            .background(if (selected) p.secondaryContainer else p.surfaceContainerHigh)
            .border(
                width = if (selected) 2.dp else 0.5.dp,
                color = if (selected) p.primary else p.separator,
                shape = shape
            )
            .m3Tap(onClick)
            .padding(vertical = 12.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(modeSwatch(style))
                .border(0.5.dp, p.separator, RoundedCornerShape(10.dp))
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(styleLabelRes(style)),
            fontSize = 13.sp,
            fontWeight = FontWeight.W500,
            color = if (selected) p.onSecondaryContainer else p.label,
            maxLines = 1
        )
    }
}

/** 模式磁贴里的小色块，只是视觉示意，不参与渲染 */
private fun modeSwatch(style: FrameStyle): Brush = when (style) {
    FrameStyle.BLUR -> Brush.verticalGradient(listOf(Color(0xFF8CA08E), Color(0xFF35433C)))
    FrameStyle.GRADIENT -> Brush.linearGradient(
        listOf(Color(0xFFEFB6C8), Color(0xFF8FA65E), Color(0xFF4B4A3A), Color(0xFF9AA7B0))
    )
    FrameStyle.CINEMA -> Brush.verticalGradient(
        listOf(Color(0xFF000000), Color(0xFF3A3A40), Color(0xFF000000))
    )
    FrameStyle.CUSTOM -> SolidColor(Color(0xFFF5F2EC))
}
