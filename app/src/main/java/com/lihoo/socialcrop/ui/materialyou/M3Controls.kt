package com.lihoo.socialcrop.ui.materialyou

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lihoo.socialcrop.R

/**
 * Material ripple 是 M3 设计语言的一部分：v2.0 的 iOS 皮肤用 iosTap 关掉了它，
 * 这里回到默认 clickable（indication 取 LocalIndication，即 Material3 的 state-layer ripple）。
 */
@Composable
fun Modifier.m3Tap(onClick: () -> Unit): Modifier =
    this.then(Modifier.clickable(onClick = onClick))

// ================= 容器与列表 =================

/** M3 分组：primary 色小标题 + 28dp 大圆角 tonal 卡片，靠色块分层而非发丝线 */
@Composable
fun M3Section(
    title: String? = null,
    footer: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val p = M3.palette
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = M3Dims.ScreenInset)
            .padding(vertical = M3Dims.SectionGap)
    ) {
        if (title != null) {
            Text(
                title,
                fontSize = 14.sp,
                fontWeight = FontWeight.W500,
                color = p.primary,
                lineHeight = 20.sp,
                modifier = Modifier.padding(start = 16.dp, bottom = 10.dp)
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(M3Dims.Corner))
                .background(p.card),
            content = content
        )
        if (footer != null) {
            Text(
                footer,
                fontSize = 12.sp,
                color = p.secondaryLabel,
                lineHeight = 16.sp,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)
            )
        }
    }
}

@Composable
fun M3Card(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier
            .clip(RoundedCornerShape(M3Dims.Corner))
            .background(M3.palette.card),
        content = content
    )
}

@Composable
fun M3Hint(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        fontSize = 12.sp,
        color = M3.palette.secondaryLabel,
        lineHeight = 17.sp,
        modifier = modifier.padding(horizontal = 32.dp, vertical = 6.dp)
    )
}

/** M3 ListItem 风格行：56dp 最小高度、20dp 内缩、极淡通栏分隔线 */
@Composable
fun M3Row(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
    onClick: (() -> Unit)? = null,
    divider: Boolean = true,
    leading: @Composable RowScope.() -> Unit = {},
    trailing: @Composable RowScope.() -> Unit = {}
) {
    val p = M3.palette
    Column {
        Row(
            modifier
                .fillMaxWidth()
                .heightIn(min = M3Dims.RowMinHeight)
                .then(if (onClick != null) Modifier.m3Tap(onClick) else Modifier)
                .padding(horizontal = M3Dims.RowInset, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            leading()
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 16.sp, color = p.label, lineHeight = 22.sp)
                if (subtitle != null) {
                    Text(subtitle, fontSize = 12.sp, color = p.secondaryLabel, lineHeight = 16.sp)
                }
            }
            if (value != null) {
                Text(value, fontSize = 14.sp, color = p.secondaryLabel, maxLines = 1)
            }
            trailing()
        }
        if (divider) {
            HorizontalDivider(color = p.separator.copy(alpha = 0.45f), thickness = 0.5.dp)
        }
    }
}

@Composable
fun M3RowDivider() {
    HorizontalDivider(color = M3.palette.separator.copy(alpha = 0.45f), thickness = 0.5.dp)
}

@Composable
fun M3Chevron() {
    Icon(
        Icons.Filled.ChevronRight,
        contentDescription = null,
        tint = M3.palette.secondaryLabel,
        modifier = Modifier.size(20.dp)
    )
}

/** M3 选中态：primaryContainer 圆底 + onPrimaryContainer 勾选 */
@Composable
fun M3Checkmark() {
    val p = M3.palette
    Box(
        Modifier.size(24.dp).clip(CircleShape).background(p.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Filled.Check,
            contentDescription = null,
            tint = p.onPrimaryContainer,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
fun M3Badge(text: String, modifier: Modifier = Modifier) {
    val p = M3.palette
    Box(
        modifier
            .clip(RoundedCornerShape(M3Dims.Pill))
            .background(p.primaryContainer)
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.W500, color = p.onPrimaryContainer)
    }
}

// ================= 控件 =================

/** M3 Switch：直接用原生组件，颜色自动读 MaterialTheme 的动态配色 */
@Composable
fun M3Switch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        modifier = modifier
    )
}

/**
 * M3 SegmentedButton（单选）：pill 外框 + outline 描边，选中项 secondaryContainer 填充。
 * 选项装得下时一律等分填满整条 pill（3 项就是 3 格，不会留一块空白装成第 4 格）；
 * 只有选项多到放不下才退化为横向滚动。
 */
@Composable
fun <T> M3Segmented(
    items: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    scroll: Boolean = false
) {
    val p = M3.palette
    val shape = RoundedCornerShape(M3Dims.Pill)
    BoxWithConstraints(modifier) {
        val minCell = 76.dp
        val dividerTotal = (items.size - 1).coerceAtLeast(0).dp
        val needScroll = scroll && minCell * items.size + dividerTotal > maxWidth
        val base = Modifier
            .clip(shape)
            .border(1.dp, p.outline, shape)
            .height(48.dp)
            .fillMaxWidth()
        if (needScroll) {
            Row(
                base.horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEachIndexed { i, item ->
                    if (i > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(p.outline))
                    SegmentCell(label(item), item == selected, fill = false) { onSelect(item) }
                }
            }
        } else {
            Row(base, verticalAlignment = Alignment.CenterVertically) {
                items.forEachIndexed { i, item ->
                    if (i > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(p.outline))
                    Box(Modifier.weight(1f)) {
                        SegmentCell(label(item), item == selected, fill = true) { onSelect(item) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SegmentCell(text: String, active: Boolean, fill: Boolean, onClick: () -> Unit) {
    val p = M3.palette
    Box(
        (if (fill) Modifier.fillMaxWidth() else Modifier.widthIn(min = 76.dp))
            .fillMaxHeight()
            .background(if (active) p.secondaryContainer else Color.Transparent)
            .m3Tap(onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text,
                fontSize = 14.sp,
                fontWeight = FontWeight.W500,
                color = if (active) p.onSecondaryContainer else p.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
    }
}

@Composable
fun M3Slider(
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    display: String? = null,
    steps: Int = 0
) {
    val p = M3.palette
    Column(modifier.fillMaxWidth().padding(horizontal = M3Dims.RowInset, vertical = 6.dp)) {
        if (label != null) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(label, fontSize = 14.sp, color = p.label, modifier = Modifier.weight(1f))
                if (display != null) {
                    Text(display, fontSize = 14.sp, fontWeight = FontWeight.W500, color = p.primary)
                }
            }
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = p.primary,
                activeTrackColor = p.primary,
                inactiveTrackColor = p.surfaceContainerHighest,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
                disabledThumbColor = p.secondaryLabel,
                disabledActiveTrackColor = p.secondaryLabel,
                disabledInactiveTrackColor = p.fill
            ),
            modifier = Modifier.fillMaxWidth().height(32.dp)
        )
    }
}

/** M3 Filled button：全圆角 pill、56dp 高、无 elevation */
@Composable
fun M3PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    val p = M3.palette
    Button(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        enabled = enabled,
        shape = RoundedCornerShape(M3Dims.Pill),
        colors = ButtonDefaults.buttonColors(
            containerColor = p.primary,
            contentColor = p.onPrimary,
            disabledContainerColor = p.surfaceContainerHighest,
            disabledContentColor = p.onSurfaceVariant
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.W500)
    }
}

/** M3 Filled tonal button：secondaryContainer 底 + onSecondaryContainer 字 */
@Composable
fun M3SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** 紧凑模式：更小字号与内边距，用于关于页多按钮并排、英文长文案不换行 */
    compact: Boolean = false
) {
    val p = M3.palette
    Button(
        onClick = onClick,
        modifier = modifier.height(if (compact) 44.dp else 56.dp),
        enabled = enabled,
        shape = RoundedCornerShape(M3Dims.Pill),
        contentPadding = if (compact)
            androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 6.dp)
        else ButtonDefaults.ContentPadding,
        colors = ButtonDefaults.buttonColors(
            containerColor = p.secondaryContainer,
            contentColor = p.onSecondaryContainer,
            disabledContainerColor = p.surfaceContainerHighest,
            disabledContentColor = p.onSurfaceVariant
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp)
    ) {
        Text(
            text,
            fontSize = if (compact) 13.sp else 16.sp,
            fontWeight = FontWeight.W500,
            maxLines = 1
        )
    }
}

@Composable
fun M3TextButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .height(44.dp)
            .clip(RoundedCornerShape(M3Dims.Pill))
            .m3Tap(onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.W500, color = M3.palette.primary)
    }
}

/** M3 Filled text field：tonal 圆角底 + 无边框，放进分组卡片里用 */
@Composable
fun M3TextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true
) {
    val p = M3.palette
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = M3Dims.RowInset, vertical = 8.dp)
            .clip(RoundedCornerShape(M3Dims.SmallCorner))
            .background(p.surfaceContainerHighest)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        textStyle = TextStyle(fontSize = 16.sp, color = p.label),
        singleLine = singleLine,
        cursorBrush = SolidColor(p.primary),
        decorationBox = { inner ->
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(placeholder, fontSize = 16.sp, color = p.secondaryLabel)
                }
                inner()
            }
        }
    )
}

/** 数值步进：tonal 圆角容器 + 两段图标按钮 */
@Composable
fun M3Stepper(value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    val p = M3.palette
    Row(
        Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(p.surfaceContainerHigh)
    ) {
        Box(
            Modifier.width(44.dp).fillMaxHeight()
                .m3Tap { if (value > min) onChange(value - 1) },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Remove,
                contentDescription = stringResource(R.string.cd_decrease),
                tint = if (value > min) p.primary else p.outline,
                modifier = Modifier.size(18.dp)
            )
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(p.outlineVariant))
        Box(
            Modifier.width(44.dp).fillMaxHeight()
                .m3Tap { if (value < max) onChange(value + 1) },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = stringResource(R.string.cd_increase),
                tint = if (value < max) p.primary else p.outline,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/** 颜色圆点：选中项 3dp primary 描边环 */
@Composable
fun M3ColorDots(
    colors: List<Int>,
    selected: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val p = M3.palette
    Row(
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = M3Dims.RowInset, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        colors.forEach { argb ->
            val active = selected == argb
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(argb))
                    .border(
                        width = if (active) 3.dp else 0.5.dp,
                        color = if (active) p.primary else p.separator,
                        shape = CircleShape
                    )
                    .m3Tap { onSelect(argb) }
            )
        }
    }
}
