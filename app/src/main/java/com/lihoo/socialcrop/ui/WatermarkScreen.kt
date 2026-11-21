package com.lihoo.socialcrop.ui

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.lihoo.socialcrop.R
import com.lihoo.socialcrop.data.FramePresets
import com.lihoo.socialcrop.data.FrameSpec
import com.lihoo.socialcrop.domain.ImageProcessor
import com.lihoo.socialcrop.ui.components.WatermarkPreview
import com.lihoo.socialcrop.ui.components.hasPreviewContent
import com.lihoo.socialcrop.ui.materialyou.M3
import com.lihoo.socialcrop.ui.materialyou.M3Card
import com.lihoo.socialcrop.ui.materialyou.M3NavBar
import com.lihoo.socialcrop.ui.materialyou.M3Row
import com.lihoo.socialcrop.ui.materialyou.M3SecondaryButton
import com.lihoo.socialcrop.ui.materialyou.M3Section
import com.lihoo.socialcrop.ui.materialyou.M3Segmented
import com.lihoo.socialcrop.ui.materialyou.M3Slider
import com.lihoo.socialcrop.ui.materialyou.M3Switch
import com.lihoo.socialcrop.ui.materialyou.M3TextField
import com.lihoo.socialcrop.ui.materialyou.m3Tap
import com.lihoo.socialcrop.viewmodel.EditorViewModel

/**
 * 水印设置页：顶部是常驻实时预览（不随参数滚动），下面所有控件改动都立即反映到预览上。
 * 边框目标按当前边框模式渲染，全局目标按纯裁剪 / 切格的真实输出渲染，均与导出共用渲染器。
 */
@Composable
fun WatermarkScreen(nav: NavController, vm: EditorViewModel, target: EditorViewModel.WatermarkTarget) {
    val wm = vm.getWatermark(target)
    val context = LocalContext.current
    val p = M3.palette
    val isFrame = target is EditorViewModel.WatermarkTarget.Frame
    val title = stringResource(R.string.watermark_title)
    val hint = if (isFrame) stringResource(R.string.watermark_frame_hint)
    else stringResource(R.string.watermark_global_hint)

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { vm.setWatermarkLogo(target, context, it) } }

    Scaffold(
        containerColor = p.groupedBackground,
        topBar = {
            M3NavBar(
                title = title,
                onBack = { nav.popBackStack() },
                backText = stringResource(R.string.cd_back)
            )
        }
    ) { padding ->
        val src = vm.bitmap
        Column(Modifier.fillMaxSize().padding(padding)) {
            // ===== 常驻实时预览：调位置 / 大小 / 透明度时始终可见 =====
            if (src != null) {
                val frame: FrameSpec? = if (target is EditorViewModel.WatermarkTarget.Frame) {
                    val cur = vm.frameSpec
                    if (cur.enabled && cur.style == target.style) cur
                    else FramePresets.recommendedFor(target.style).copy(enabled = true)
                } else null
                val slice: Pair<Int, Int>? =
                    if (!isFrame && vm.mode == EditorViewModel.Mode.SLICE) vm.columns to vm.rows else null
                // 预览尺寸 = 真实输出比例；没有黑幕，也不需要留白占位
                val previewRatio = if (slice != null) {
                    val plan = ImageProcessor.planGrid(src.width, src.height, slice.first, slice.second)
                    plan.cellW.toFloat() / plan.cellH.coerceAtLeast(1)
                } else vm.ratioValue

                M3Card(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Box(Modifier.padding(10.dp)) {
                        WatermarkPreview(
                            src = src,
                            watermark = wm,
                            frame = frame,
                            targetRatio = vm.ratioValue,
                            slice = slice,
                            cropCenterX = vm.cropCenterX,
                            cropCenterY = vm.cropCenterY,
                            modifier = Modifier.fillMaxWidth()
                                .aspectRatio(previewRatio.coerceIn(0.25f, 4f))
                        )
                    }
                }
                if (wm.enabled && !wm.hasPreviewContent()) {
                    Text(
                        stringResource(R.string.wm_preview_hint),
                        fontSize = 13.sp,
                        color = p.secondaryLabel,
                        lineHeight = 17.sp,
                        modifier = Modifier.padding(horizontal = 32.dp, vertical = 4.dp)
                    )
                }
            }

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp)
            ) {
                // ===== 总开关 =====
                M3Section(footer = hint) {
                    M3Row(
                        title = stringResource(R.string.watermark_enable),
                        divider = false,
                        trailing = {
                            M3Switch(
                                checked = wm.enabled,
                                onCheckedChange = { vm.setWatermarkEnabled(target, it) }
                            )
                        }
                    )
                }

                // ===== 从照片参数自动生成 =====
                M3Section(footer = stringResource(R.string.wm_auto_exif_hint)) {
                    M3Row(
                        title = stringResource(R.string.wm_auto_exif),
                        divider = wm.autoExif,
                        trailing = {
                            M3Switch(
                                checked = wm.autoExif,
                                onCheckedChange = { vm.setAutoExif(target, it) }
                            )
                        }
                    )
                    if (wm.autoExif) {
                        val exif = vm.exifInfo
                        if (exif == null || !exif.available) {
                            Text(
                                stringResource(R.string.wm_no_exif),
                                fontSize = 15.sp,
                                color = p.red,
                                modifier = Modifier.padding(16.dp)
                            )
                        } else {
                            M3Row(
                                title = stringResource(R.string.wm_show_gps),
                                divider = wm.text.isNotBlank(),
                                trailing = {
                                    M3Switch(
                                        checked = wm.showGps,
                                        enabled = exif.hasGps,
                                        onCheckedChange = { vm.setShowGps(target, it) }
                                    )
                                }
                            )
                            if (wm.text.isNotBlank()) {
                                Text(
                                    stringResource(R.string.wm_exif_preview, wm.text),
                                    fontSize = 13.sp,
                                    color = p.secondaryLabel,
                                    lineHeight = 17.sp,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                                )
                            }
                        }
                    }
                }

                // ===== 文字 =====
                M3Section(title = stringResource(R.string.watermark_text)) {
                    M3TextField(
                        value = wm.text,
                        onValueChange = { vm.setWatermark(target, wm.copy(text = it, showText = true)) },
                        placeholder = stringResource(R.string.watermark_text_hint)
                    )
                    M3Row(
                        title = stringResource(R.string.watermark_show_text),
                        trailing = {
                            M3Switch(
                                checked = wm.showText,
                                onCheckedChange = { vm.setWatermark(target, wm.copy(showText = it)) }
                            )
                        }
                    )
                    Text(
                        stringResource(R.string.watermark_color_label),
                        fontSize = 13.sp,
                        color = p.secondaryLabel,
                        modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 4.dp)
                    )
                    Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        M3Segmented(
                            items = listOf(0xFFFFFFFF.toInt(), 0xFF000000.toInt()),
                            selected = wm.textColor,
                            onSelect = { vm.setWatermark(target, wm.copy(textColor = it)) },
                            label = {
                                stringResource(
                                    if (it == 0xFFFFFFFF.toInt()) R.string.watermark_color_white
                                    else R.string.watermark_color_black
                                )
                            }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }

                // ===== Logo =====
                M3Section(title = stringResource(R.string.watermark_logo)) {
                    if (wm.logoPath.isNotBlank()) {
                        val bmp = remember(wm.logoPath) {
                            runCatching { BitmapFactory.decodeFile(wm.logoPath) }.getOrNull()
                        }
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            bmp?.let {
                                Image(
                                    bitmap = it.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(54.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .border(0.5.dp, p.separator, RoundedCornerShape(10.dp))
                                )
                            }
                            Spacer(Modifier.size(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.watermark_logo_set),
                                    fontSize = 17.sp,
                                    color = p.label
                                )
                                Text(
                                    stringResource(R.string.watermark_logo_path_hint),
                                    fontSize = 13.sp,
                                    color = p.secondaryLabel
                                )
                            }
                            Box(
                                Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                                    .m3Tap { vm.clearWatermarkLogo(target) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = stringResource(R.string.watermark_clear_logo),
                                    tint = p.red,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    } else {
                        Box(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                            M3SecondaryButton(
                                text = stringResource(R.string.watermark_add_logo),
                                onClick = {
                                    picker.launch(
                                        PickVisualMediaRequest.Builder()
                                            .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            .build()
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    M3Row(
                        title = stringResource(R.string.watermark_show_logo),
                        divider = false,
                        trailing = {
                            M3Switch(
                                checked = wm.showLogo,
                                onCheckedChange = { vm.setWatermark(target, wm.copy(showLogo = it)) }
                            )
                        }
                    )
                }

                // ===== 外观 =====
                M3Section(title = stringResource(R.string.watermark_appearance)) {
                    SliderRow(
                        label = stringResource(R.string.watermark_size),
                        value = wm.scaleRatio,
                        range = 0.02f..0.6f,
                        onValue = { vm.setWatermark(target, wm.copy(scaleRatio = it)) },
                        display = "${(wm.scaleRatio * 100).toInt()}%"
                    )
                    SliderRow(
                        label = stringResource(R.string.watermark_opacity),
                        value = wm.opacity,
                        range = 0f..1f,
                        onValue = { vm.setWatermark(target, wm.copy(opacity = it)) },
                        display = "${(wm.opacity * 100).toInt()}%"
                    )
                    SliderRow(
                        label = stringResource(R.string.watermark_rotation),
                        value = wm.rotation,
                        range = -45f..45f,
                        onValue = { vm.setWatermark(target, wm.copy(rotation = it)) },
                        display = "${wm.rotation.toInt()}°"
                    )
                    SliderRow(
                        label = stringResource(R.string.watermark_margin),
                        value = wm.marginRatio,
                        range = 0f..0.3f,
                        onValue = { vm.setWatermark(target, wm.copy(marginRatio = it)) },
                        display = "${(wm.marginRatio * 100).toInt()}%"
                    )
                    M3Row(
                        title = stringResource(R.string.watermark_tiled),
                        trailing = {
                            M3Switch(
                                checked = wm.tiled,
                                onCheckedChange = { vm.setWatermark(target, wm.copy(tiled = it)) }
                            )
                        }
                    )
                    M3Row(
                        title = stringResource(R.string.watermark_shadow),
                        divider = false,
                        trailing = {
                            M3Switch(
                                checked = wm.shadowOn,
                                onCheckedChange = { vm.setWatermark(target, wm.copy(shadowOn = it)) }
                            )
                        }
                    )
                }

                // 改动即时生效并自动保存，顶栏返回就是退出；
                // 旧版底部还有一个「完成」按钮，和顶栏返回完全重复
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValue: (Float) -> Unit,
    display: String
) {
    M3Slider(
        value = value,
        range = range,
        onValueChange = onValue,
        label = label,
        display = display
    )
}
