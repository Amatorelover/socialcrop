package com.lihoo.socialcrop.ui.materialyou

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/**
 * M3 Expressive 顶栏：64dp 高、标题左对齐 22sp Medium、箭头返回用 arrow_back，
 * 图标按钮自带 Material ripple（v2.0 为 iOS 关掉的 ripple 在此回归）。
 * backText 在 iOS 皮肤里是可见的返回文字，M3 只用作无障碍描述。
 */
@Composable
fun M3NavBar(
    title: String? = null,
    onBack: (() -> Unit)? = null,
    backText: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    showDivider: Boolean = true,
    modifier: Modifier = Modifier
) {
    val p = M3.palette
    Column(modifier.fillMaxWidth().background(p.bar).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = backText,
                        tint = p.label,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            if (title != null) {
                Text(
                    title,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.W500,
                    color = p.label,
                    maxLines = 1,
                    modifier = Modifier.weight(1f).padding(start = if (onBack != null) 8.dp else 16.dp)
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                content = actions
            )
        }
        if (showDivider) {
            HorizontalDivider(color = p.separator.copy(alpha = 0.5f), thickness = 0.5.dp)
        }
    }
}

@Composable
fun M3BarIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String?,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        Icon(
            icon,
            contentDescription,
            tint = M3.palette.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
    }
}

/** M3 Expressive 大标题：32sp Medium 左对齐，副行 14sp onSurfaceVariant */
@Composable
fun M3LargeTitle(text: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    val p = M3.palette
    Column(modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 10.dp)) {
        Text(text, fontSize = 32.sp, fontWeight = FontWeight.W500, color = p.label, lineHeight = 40.sp)
        if (subtitle != null) {
            Spacer(Modifier.height(6.dp))
            Text(subtitle, fontSize = 14.sp, color = p.secondaryLabel, lineHeight = 20.sp)
        }
    }
}

/** M3 Bottom app bar：tonal 表面 + 无发丝线，靠色块与内容区分 */
@Composable
fun M3BottomBar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(M3.palette.surfaceContainer)
            .navigationBarsPadding()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = M3Dims.ScreenInset, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

/** M3 Dialog：28dp 圆角 + surfaceContainerHigh + 24sp 标题 + 右对齐文字按钮 */
@Composable
fun M3AlertDialog(
    title: String,
    dismissText: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val p = M3.palette
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(M3Dims.Corner))
                .background(p.surfaceContainerHigh)
                .padding(vertical = 20.dp)
        ) {
            Text(
                title,
                fontSize = 24.sp,
                fontWeight = FontWeight.W500,
                color = p.label,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            content()
            Box(
                Modifier
                    .align(Alignment.End)
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(M3Dims.Pill))
                    .m3Tap(onDismiss)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(dismissText, fontSize = 14.sp, fontWeight = FontWeight.W500, color = p.primary)
            }
        }
    }
}
