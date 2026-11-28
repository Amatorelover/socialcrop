package com.lihoo.socialcrop.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.lihoo.socialcrop.R
import com.lihoo.socialcrop.ui.materialyou.M3
import com.lihoo.socialcrop.ui.materialyou.M3PrimaryButton
import com.lihoo.socialcrop.ui.materialyou.M3SecondaryButton

/**
 * 轻量色盘取色弹窗：饱和度/明度方块 + 色相条，拖动即改，所见即所得。
 * 不依赖任何第三方取色库；点击「添加」把 ARGB 回传给调用方。
 */
@Composable
fun ColorPickerDialog(
    initialColor: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val p = M3.palette
    val initialHsv = remember(initialColor) {
        FloatArray(3).let {
            android.graphics.Color.colorToHSV(initialColor, it)
            it
        }
    }
    var hue by remember {
        mutableStateOf(if (initialHsv[0] < 0f) 0f else initialHsv[0])
    }
    var saturation by remember {
        mutableStateOf(if (initialHsv[0] < 0f) 0f else initialHsv[1])
    }
    var value by remember {
        mutableStateOf(if (initialHsv[0] < 0f) 1f else initialHsv[2])
    }
    val argb = remember(hue, saturation, value) {
        android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value))
    }
    val pureHue = remember(hue) {
        android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f))
    }
    val density = LocalDensity.current

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(p.surfaceContainerHigh)
                .padding(24.dp)
        ) {
            Text(
                stringResource(R.string.color_picker_title),
                fontSize = 22.sp,
                color = p.label
            )
            Spacer(Modifier.height(16.dp))

            // 饱和度(横向) / 明度(纵向) 方块
            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(16.dp))
            ) {
                val boxW = with(density) { maxWidth.toPx() }
                val boxH = with(density) { maxHeight.toPx() }
                fun apply(offset: Offset) {
                    saturation = (offset.x / boxW).coerceIn(0f, 1f)
                    value = 1f - (offset.y / boxH).coerceIn(0f, 1f)
                }
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { apply(it) },
                                onDrag = { change, _ -> apply(change.position) }
                            )
                        }
                ) {
                    drawRect(color = Color(pureHue))
                    drawRect(brush = Brush.horizontalGradient(listOf(Color.Transparent, Color.White)))
                    drawRect(brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                    val x = saturation * size.width
                    val y = (1f - value) * size.height
                    drawCircle(
                        color = Color.White,
                        radius = 12.dp.toPx(),
                        center = Offset(x, y),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
                    )
                    drawCircle(
                        color = Color(argb),
                        radius = 8.dp.toPx(),
                        center = Offset(x, y)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // 色相条：横向 rainbow，拖动改 hue
            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .clip(RoundedCornerShape(14.dp))
            ) {
                val barW = with(density) { maxWidth.toPx() }
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = {
                                    hue = (it.x / barW).coerceIn(0f, 1f) * 360f
                                },
                                onDrag = { change, _ ->
                                    hue = (change.position.x / barW).coerceIn(0f, 1f) * 360f
                                }
                            )
                        }
                ) {
                    val stops = listOf(
                        Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue,
                        Color.Magenta, Color.Red
                    )
                    drawRect(brush = Brush.horizontalGradient(stops))
                    val x = (hue / 360f) * size.width
                    drawCircle(
                        color = Color.White,
                        radius = 12.dp.toPx(),
                        center = Offset(x, size.height / 2f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(argb))
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "#%06X".format(argb and 0xFFFFFF),
                    fontSize = 16.sp,
                    color = p.label
                )
            }

            Spacer(Modifier.height(20.dp))

            Row {
                M3SecondaryButton(
                    text = stringResource(R.string.wm_brand_picker_close),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(12.dp))
                M3PrimaryButton(
                    text = stringResource(R.string.action_done),
                    onClick = { onConfirm(argb) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
