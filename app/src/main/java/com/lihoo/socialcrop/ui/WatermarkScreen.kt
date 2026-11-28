package com.lihoo.socialcrop.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.lihoo.socialcrop.R
import com.lihoo.socialcrop.data.BuiltinLogoGroup
import com.lihoo.socialcrop.data.BuiltinLogos
import com.lihoo.socialcrop.data.FramePresets
import com.lihoo.socialcrop.data.FrameSpec
import com.lihoo.socialcrop.data.LogoTint
import com.lihoo.socialcrop.data.WatermarkAlign
import com.lihoo.socialcrop.data.WatermarkBlockAlign
import com.lihoo.socialcrop.data.WatermarkMoveTarget
import com.lihoo.socialcrop.data.WatermarkSpec
import com.lihoo.socialcrop.domain.FrameRenderer
import com.lihoo.socialcrop.domain.ImageProcessor
import com.lihoo.socialcrop.domain.WatermarkRenderer
import com.lihoo.socialcrop.util.decodeBrandLogoAsset
import com.lihoo.socialcrop.util.decodeLogo
import com.lihoo.socialcrop.ui.components.WatermarkPreview
import com.lihoo.socialcrop.ui.materialyou.M3
import com.lihoo.socialcrop.ui.materialyou.M3BottomBar
import com.lihoo.socialcrop.ui.materialyou.M3AlertDialog
import com.lihoo.socialcrop.ui.materialyou.M3Card
import com.lihoo.socialcrop.ui.materialyou.M3NavBar
import com.lihoo.socialcrop.ui.materialyou.M3PrimaryButton
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
 * 水印设置页：总开关是唯一入口——关闭时只显示开关，不出现任何其它选项。
 * 开启后图片 / 文字 / 照片参数三类可同时配置，实时预览始终常驻在最上方。
 */
@Composable
fun WatermarkScreen(nav: NavController, vm: EditorViewModel, target: EditorViewModel.WatermarkTarget) {
    val wm = vm.getWatermark(target)
    val context = LocalContext.current
    var showBrandPicker by remember { mutableStateOf(false) }
    var tab by remember {
        mutableStateOf(
            when {
                wm.deviceOn || wm.lensOn -> WatermarkTab.EXIF
                wm.authorOn || wm.timeOn -> WatermarkTab.TEXT
                wm.logoPath.isNotBlank() -> WatermarkTab.IMAGE
                else -> WatermarkTab.IMAGE
            }
        )
    }
    val p = M3.palette
    val isFrame = target is EditorViewModel.WatermarkTarget.Frame
    val title = stringResource(R.string.watermark_title)
    val hint = if (isFrame) stringResource(R.string.watermark_frame_hint)
    else stringResource(R.string.watermark_global_hint)

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { vm.setWatermarkLogo(target, context, it) } }

    // 有 Logo 时照片参数 / 文字水印二选一：被自动关掉的一侧弹 1~2 秒短提示
    val notice = vm.exclusiveNotice
    LaunchedEffect(notice?.seq) {
        notice?.let {
            val text = when (it.side) {
                EditorViewModel.ExclusiveSide.TEXT -> R.string.wm_exclusive_text_off
                EditorViewModel.ExclusiveSide.EXIF -> R.string.wm_exclusive_exif_off
            }
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        }
    }
    // 两边对齐的组合不合法（必须 Logo + 文字 或 Logo + 照片参数，且只能二选一）
    val alignNotice = vm.alignmentNotice
    LaunchedEffect(alignNotice) {
        if (alignNotice > 0) {
            Toast.makeText(context, R.string.wm_sides_invalid, Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        containerColor = p.groupedBackground,
        topBar = {
            M3NavBar(
                title = title,
                onBack = { nav.popBackStack() },
                backText = stringResource(R.string.cd_back)
            )
        },
        bottomBar = {
            M3BottomBar {
                M3SecondaryButton(
                    text = stringResource(R.string.action_reset),
                    onClick = { vm.resetWatermark(target) },
                    modifier = Modifier.weight(1f)
                )
                M3PrimaryButton(
                    text = stringResource(R.string.action_done),
                    onClick = {
                        if (!nav.popBackStack("editor", inclusive = false)) nav.popBackStack()
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    ) { padding ->
        val src = vm.bitmap
        // v2.9.0：这里必须缓存实例。之前每次重组都 new 一个 FrameSpec，
        // 会让 WatermarkPreview 的 LaunchedEffect 无限重启，预览永远停在转圈。
        val frame: FrameSpec? = remember(target, vm.frameSpec) {
            if (target is EditorViewModel.WatermarkTarget.Frame) {
                val cur = vm.frameSpec
                if (cur.enabled && cur.style == target.style) cur
                else FramePresets.recommendedFor(target.style).copy(enabled = true)
            } else null
        }
        // 大小滑块上限按当前留白带的真实高度计算，整段行程都有效；
        // 无边框时水印落在图片底部 12% 带内，与带边框用同一套钳制逻辑，滑块上限也按这条带算。
        val bandRatio = remember(frame, vm.ratioValue, src?.width, src?.height) {
            if (frame != null && src != null) {
                FrameRenderer.watermarkBandRatio(frame, vm.ratioValue, src.width, src.height)
            } else WatermarkRenderer.PLAIN_BAND_RATIO
        }
        val sizeMax = remember(wm, bandRatio) {
            WatermarkRenderer.scaleRatioLimit(wm, wm.logoPath.isNotBlank(), bandRatio)
        }

        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            // 竖屏时预览按可用高度限高、宽度按真实比例自适应居中，
            // 把更多空间让给下方的滚动设置区（不再让预览撑满整宽把设置挤没）。
            val previewH = (maxHeight * 0.28f).coerceIn(130.dp, 200.dp)
            Column(Modifier.fillMaxSize()) {
                if (src != null) {
                    val slice: Pair<Int, Int>? =
                        if (!isFrame && vm.mode == EditorViewModel.Mode.SLICE) vm.columns to vm.rows else null
                    val previewRatio = if (slice != null) {
                        val plan = ImageProcessor.planGrid(src.width, src.height, slice.first, slice.second)
                        plan.cellW.toFloat() / plan.cellH.coerceAtLeast(1)
                    } else vm.ratioValue

                    M3Card(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Box(
                            Modifier.fillMaxWidth().padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            WatermarkPreview(
                                src = src,
                                watermark = wm,
                                frame = frame,
                                targetRatio = previewRatio,
                                slice = slice,
                                cropCenterX = vm.cropCenterX,
                                cropCenterY = vm.cropCenterY,
                                exif = vm.exifInfo,
                                modifier = Modifier
                                    .height(previewH)
                                    .aspectRatio(previewRatio.coerceIn(0.25f, 4f))
                            )
                        }
                        Text(
                            stringResource(R.string.preview_zoom_hint),
                            fontSize = 12.sp,
                            color = p.secondaryLabel,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                        )
                    }
                }

                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)
                ) {
                    // 总开关：关闭时不渲染下面任何选项
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

                    if (!wm.enabled) {
                        Text(
                            stringResource(R.string.wm_enable_first),
                            fontSize = 14.sp,
                            color = p.secondaryLabel,
                            lineHeight = 19.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
                        )
                    } else {
                        // 通用设置提权到「开启水印之后」的主层级：一次设置作用于全部类型
                        CommonSection(wm = wm, target = target, vm = vm)

                        // 三类内容：每次只显示一类，避免长列表堆在一起
                        Box(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                            M3Segmented(
                                items = listOf(WatermarkTab.IMAGE, WatermarkTab.EXIF, WatermarkTab.TEXT),
                                selected = tab,
                                onSelect = { tab = it },
                                label = { stringResource(tabLabelRes(it)) }
                            )
                        }
                        when (tab) {
                            WatermarkTab.IMAGE -> ImageWatermarkSection(
                                wm = wm, target = target, vm = vm, sizeMax = sizeMax, picker = picker,
                                onPickBrand = { showBrandPicker = true }
                            )
                            WatermarkTab.TEXT -> TextWatermarkSection(
                                wm = wm, target = target, vm = vm, sizeMax = sizeMax
                            )
                            WatermarkTab.EXIF -> ExifWatermarkSection(
                                wm = wm, target = target, vm = vm, sizeMax = sizeMax
                            )
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

    if (showBrandPicker) {
        BrandLogoPickerDialog(
            currentId = wm.logoBrandId,
            onDismiss = { showBrandPicker = false },
            onSelect = { id ->
                vm.setWatermarkBuiltinLogo(target, context, id)
                showBrandPicker = false
            }
        )
    }
}

private enum class WatermarkTab { IMAGE, TEXT, EXIF }

private fun tabLabelRes(tab: WatermarkTab): Int = when (tab) {
    WatermarkTab.IMAGE -> R.string.wm_tab_image
    WatermarkTab.TEXT -> R.string.wm_tab_text
    WatermarkTab.EXIF -> R.string.wm_tab_exif
}

/** 第一类：图片水印。选中 Logo 即显示，不再有「显示 Logo」开关。 */
@Composable
private fun ImageWatermarkSection(
    wm: WatermarkSpec,
    target: EditorViewModel.WatermarkTarget,
    vm: EditorViewModel,
    sizeMax: Float,
    picker: ActivityResultLauncher<PickVisualMediaRequest>,
    onPickBrand: () -> Unit
) {
    val p = M3.palette
    M3Section(title = stringResource(R.string.watermark_logo)) {
        if (wm.logoPath.isNotBlank()) {
            val bmp = remember(wm.logoPath, wm.logoTint) {
                decodeLogo(wm.logoPath, maxEdge = 256, tint = wm.logoTint)
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                bmp?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.size(54.dp).clip(RoundedCornerShape(10.dp))
                            .border(0.5.dp, p.separator, RoundedCornerShape(10.dp))
                    )
                }
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    val brand = BuiltinLogos.find(wm.logoBrandId)
                    Text(
                        text = if (brand != null) stringResource(brand.nameRes)
                        else stringResource(R.string.watermark_logo_set),
                        fontSize = 17.sp, color = p.label
                    )
                    Text(
                        stringResource(R.string.watermark_logo_path_hint),
                        fontSize = 13.sp, color = p.secondaryLabel
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
                        tint = p.red, modifier = Modifier.size(20.dp)
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
        Box(
            Modifier.padding(
                horizontal = 16.dp,
                vertical = if (wm.logoPath.isNotBlank()) 0.dp else 4.dp
            )
        ) {
            M3SecondaryButton(
                text = stringResource(R.string.wm_brand_pick),
                onClick = onPickBrand,
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (wm.logoPath.isNotBlank()) {
            Text(
                stringResource(R.string.wm_logo_tint),
                fontSize = 13.sp, color = p.secondaryLabel,
                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
            )
            Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                M3Segmented(
                    items = listOf(LogoTint.ORIGINAL, LogoTint.WHITE, LogoTint.BLACK),
                    selected = wm.logoTint,
                    onSelect = { vm.setWatermark(target, wm.copy(logoTint = it)) },
                    label = { stringResource(logoTintLabelRes(it)) }
                )
            }
        }
    }
    AppearanceSection(
        wm = wm, target = target, vm = vm, sizeMax = sizeMax,
        which = WatermarkMoveTarget.LOGO, showTextColor = false
    )
    FineTuneBlock(
        title = stringResource(R.string.wm_move_logo),
        align = wm.logoAlign,
        onMove = { dx, dy -> vm.moveWatermark(target, WatermarkMoveTarget.LOGO, dx, dy) },
        onReset = { vm.resetWatermarkOffset(target, WatermarkMoveTarget.LOGO) },
        onAlign = { vm.setWatermarkAlign(target, WatermarkMoveTarget.LOGO, it) }
    )
}

/** 第二类：文字水印。作者名字 / 时间戳各自带独立开关，可同时打开叠加两行。 */
@Composable
private fun TextWatermarkSection(
    wm: WatermarkSpec,
    target: EditorViewModel.WatermarkTarget,
    vm: EditorViewModel,
    sizeMax: Float
) {
    val p = M3.palette
    M3Section(title = stringResource(R.string.watermark_text)) {
        M3Row(
            title = stringResource(R.string.wm_text_author),
            divider = wm.authorOn,
            trailing = {
                M3Switch(
                    checked = wm.authorOn,
                    onCheckedChange = { vm.setAuthorOn(target, it) }
                )
            }
        )
        if (wm.authorOn) {
            M3TextField(
                value = wm.text,
                onValueChange = { vm.setWatermark(target, wm.copy(text = it)) },
                placeholder = stringResource(R.string.watermark_text_hint)
            )
        }
        M3Row(
            title = stringResource(R.string.wm_text_time),
            divider = wm.timeOn,
            trailing = {
                M3Switch(
                    checked = wm.timeOn,
                    onCheckedChange = { vm.setTimeOn(target, it) }
                )
            }
        )
        if (wm.timeOn) {
            Text(
                stringResource(R.string.wm_time_value, wm.timeText.ifBlank { "-" }),
                fontSize = 15.sp, color = p.label,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }
    }
    AppearanceSection(
        wm = wm, target = target, vm = vm, sizeMax = sizeMax,
        which = WatermarkMoveTarget.TEXT, showTextColor = true
    )
    FineTuneBlock(
        title = stringResource(R.string.wm_move_text),
        align = wm.textAlign,
        onMove = { dx, dy -> vm.moveWatermark(target, WatermarkMoveTarget.TEXT, dx, dy) },
        onReset = { vm.resetWatermarkOffset(target, WatermarkMoveTarget.TEXT) },
        onAlign = { vm.setWatermarkAlign(target, WatermarkMoveTarget.TEXT, it) }
    )
}

/** 第三类：照片参数。手机型号（第一行）/ 镜头参数（第二行）各自独立开关，默认两行居中。 */
@Composable
private fun ExifWatermarkSection(
    wm: WatermarkSpec,
    target: EditorViewModel.WatermarkTarget,
    vm: EditorViewModel,
    sizeMax: Float
) {
    M3Section(title = stringResource(R.string.wm_exif_section_title)) {
        M3Row(
            title = stringResource(R.string.wm_device_on),
            divider = wm.deviceOn,
            trailing = {
                M3Switch(
                    checked = wm.deviceOn,
                    onCheckedChange = { vm.setDeviceOn(target, it) }
                )
            }
        )
        // v2.21：打开型号后自动识别 EXIF 填入文本框，用户可直接改写；留空则回落自动识别
        if (wm.deviceOn) {
            M3TextField(
                value = wm.deviceText,
                onValueChange = { vm.setWatermark(target, wm.copy(deviceText = it)) },
                placeholder = stringResource(R.string.wm_device_hint)
            )
            Box(Modifier.padding(horizontal = 16.dp, vertical = 2.dp)) {
                M3SecondaryButton(
                    text = stringResource(R.string.wm_device_redetect),
                    onClick = { vm.refreshDeviceText(target) },
                    compact = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        M3Row(
            title = stringResource(R.string.wm_lens_on),
            divider = false,
            trailing = {
                M3Switch(
                    checked = wm.lensOn,
                    onCheckedChange = { vm.setLensOn(target, it) }
                )
            }
        )
    }
    AppearanceSection(
        wm = wm, target = target, vm = vm, sizeMax = sizeMax,
        which = WatermarkMoveTarget.EXIF, showTextColor = true
    )
    FineTuneBlock(
        title = stringResource(R.string.wm_move_exif),
        align = wm.exifAlign,
        onMove = { dx, dy -> vm.moveWatermark(target, WatermarkMoveTarget.EXIF, dx, dy) },
        onReset = { vm.resetWatermarkOffset(target, WatermarkMoveTarget.EXIF) },
        onAlign = { vm.setWatermarkAlign(target, WatermarkMoveTarget.EXIF, it) }
    )
}

/** 开启水印后的统一设置：透明度 / 旋转 / 平铺 / 投影，作用于全部类型。 */
@Composable
private fun CommonSection(
    wm: WatermarkSpec,
    target: EditorViewModel.WatermarkTarget,
    vm: EditorViewModel
) {
    M3Section(
        title = stringResource(R.string.wm_common_title),
        footer = stringResource(R.string.wm_appearance_hint)
    ) {
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
    // 通用·整体对齐：相对照片的左 / 中 / 右；两边对齐只在 Logo + 单一文字类时生效
    val p = M3.palette
    Text(
        stringResource(R.string.wm_block_align_label),
        fontSize = 13.sp,
        color = p.secondaryLabel,
        modifier = Modifier.padding(start = 16.dp, top = 4.dp)
    )
    Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        M3Segmented(
            items = listOf(
                WatermarkBlockAlign.CENTER,
                WatermarkBlockAlign.LEFT,
                WatermarkBlockAlign.RIGHT,
                WatermarkBlockAlign.SIDES
            ),
            selected = wm.blockAlign,
            onSelect = { vm.setWatermarkBlockAlign(target, it) },
            label = {
                stringResource(
                    when (it) {
                        WatermarkBlockAlign.CENTER -> R.string.wm_block_align_center
                        WatermarkBlockAlign.LEFT -> R.string.wm_block_align_left
                        WatermarkBlockAlign.RIGHT -> R.string.wm_block_align_right
                        WatermarkBlockAlign.SIDES -> R.string.wm_block_align_sides
                    }
                )
            }
        )
    }
    // 通用·整体位置：整块大步长移动（每次 5%），与三类各自的 0.5% 微调互不重复、可叠加
    MovePad(
        title = stringResource(R.string.wm_move_block),
        hint = stringResource(R.string.wm_move_block_hint),
        onMove = { dx, dy -> vm.moveWatermarkBlock(target, dx, dy) },
        onReset = { vm.resetWatermarkBlock(target) }
    )
}

/**
 * 大小 / 文字颜色从通用设置里挪出来，单独放在对应水印类型下面。
 * Logo 已有自己的「Logo 颜色」，所以图片水印这一类不显示文字颜色。
 */
@Composable
private fun AppearanceSection(
    wm: WatermarkSpec,
    target: EditorViewModel.WatermarkTarget,
    vm: EditorViewModel,
    sizeMax: Float,
    which: WatermarkMoveTarget,
    showTextColor: Boolean
) {
    val p = M3.palette
    // v2.13.0：三类水印大小各自独立记忆，滑块绑定到本类自己的 scaleRatio
    val curScale = when (which) {
        WatermarkMoveTarget.LOGO -> wm.logoScaleRatio
        WatermarkMoveTarget.TEXT -> wm.textScaleRatio
        WatermarkMoveTarget.EXIF -> wm.exifScaleRatio
    }
    M3Section(title = stringResource(R.string.watermark_appearance)) {
        SliderRow(
            label = stringResource(R.string.watermark_size),
            value = curScale.coerceAtMost(sizeMax),
            range = 0.02f..sizeMax,
            onValue = { vm.setWatermarkScale(target, which, it) },
            display = "${(curScale.coerceAtMost(sizeMax) * 100).toInt()}%"
        )
        if (showTextColor) {
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
        }
    }
}

/** 一类水印的微调区：方向键 + 水平对齐（左 / 中 / 右，默认居中） */
@Composable
private fun FineTuneBlock(
    title: String,
    align: WatermarkAlign,
    onMove: (Float, Float) -> Unit,
    onReset: () -> Unit,
    onAlign: (WatermarkAlign) -> Unit
) {
    MovePad(title = title, onMove = onMove, onReset = onReset)
    val p = M3.palette
    Text(
        stringResource(R.string.wm_align_label),
        fontSize = 13.sp,
        color = p.secondaryLabel,
        modifier = Modifier.padding(start = 16.dp, top = 2.dp)
    )
    Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        M3Segmented(
            items = listOf(WatermarkAlign.LEFT, WatermarkAlign.CENTER, WatermarkAlign.RIGHT),
            selected = align,
            onSelect = onAlign,
            label = {
                stringResource(
                    when (it) {
                        WatermarkAlign.LEFT -> R.string.wm_align_left
                        WatermarkAlign.CENTER -> R.string.wm_align_center
                        WatermarkAlign.RIGHT -> R.string.wm_align_right
                    }
                )
            }
        )
    }
}

/**
 * 上下左右方向键；中间按钮复位。[hint] 为空时不显示说明。
 * - 三类微调：每次 0.5% 短边（细粒度）；
 * - 通用整体位置：每次 5% 短边（大步长），作用于整个水印块。
 */
@Composable
private fun MovePad(
    title: String,
    onMove: (Float, Float) -> Unit,
    onReset: () -> Unit,
    hint: String? = null
) {
    val p = M3.palette
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(title, fontSize = 13.sp, color = p.secondaryLabel)
        if (hint != null) {
            Text(
                hint, fontSize = 12.sp, color = p.secondaryLabel,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            MoveButton(Icons.Filled.KeyboardArrowUp, R.string.wm_move_up) { onMove(0f, -1f) }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MoveButton(Icons.Filled.KeyboardArrowLeft, R.string.wm_move_left) { onMove(-1f, 0f) }
            Spacer(Modifier.size(12.dp))
            MoveButton(Icons.Filled.Refresh, R.string.wm_move_reset) { onReset() }
            Spacer(Modifier.size(12.dp))
            MoveButton(Icons.Filled.KeyboardArrowRight, R.string.wm_move_right) { onMove(1f, 0f) }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            MoveButton(Icons.Filled.KeyboardArrowDown, R.string.wm_move_down) { onMove(0f, 1f) }
        }
    }
}

@Composable
private fun MoveButton(icon: ImageVector, labelRes: Int, onClick: () -> Unit) {
    val p = M3.palette
    Box(
        Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
            .background(p.surfaceContainer).m3Tap(onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = stringResource(labelRes),
            tint = p.label,
            modifier = Modifier.size(22.dp)
        )
    }
}

/** 内置品牌 Logo 选择弹窗：手机厂商 / 相机厂商两页，离线从 assets 读缩略图。 */
@Composable
private fun BrandLogoPickerDialog(
    currentId: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    var tab by remember { mutableStateOf(BuiltinLogoGroup.PHONE) }
    val list = if (tab == BuiltinLogoGroup.PHONE) BuiltinLogos.phones else BuiltinLogos.cameras

    M3AlertDialog(
        title = stringResource(R.string.wm_brand_picker_title),
        dismissText = stringResource(R.string.wm_brand_picker_close),
        onDismiss = onDismiss
    ) {
        Box(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            M3Segmented(
                items = listOf(BuiltinLogoGroup.PHONE, BuiltinLogoGroup.CAMERA),
                selected = tab,
                onSelect = { tab = it },
                label = {
                    stringResource(
                        if (it == BuiltinLogoGroup.PHONE) R.string.wm_brand_tab_phone
                        else R.string.wm_brand_tab_camera
                    )
                }
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.fillMaxWidth().height(340.dp)
                .padding(horizontal = 14.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(list, key = { it.id }) { logo ->
                BrandLogoCell(
                    id = logo.id,
                    name = stringResource(logo.nameRes),
                    selected = logo.id == currentId,
                    onClick = { onSelect(logo.id) }
                )
            }
        }
    }
}

@Composable
private fun BrandLogoCell(
    id: String,
    name: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val p = M3.palette
    val bmp = remember(id) { decodeBrandLogoAsset(context, id) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(if (selected) p.secondaryContainer else p.surfaceContainer)
            .m3Tap(onClick).padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(width = 52.dp, height = 36.dp), contentAlignment = Alignment.Center) {
            bmp?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(width = 52.dp, height = 36.dp)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            name, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = if (selected) p.onSecondaryContainer else p.secondaryLabel
        )
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

private fun logoTintLabelRes(tint: LogoTint): Int = when (tint) {
    LogoTint.ORIGINAL -> R.string.wm_logo_tint_original
    LogoTint.WHITE -> R.string.wm_logo_tint_white
    LogoTint.BLACK -> R.string.wm_logo_tint_black
}
