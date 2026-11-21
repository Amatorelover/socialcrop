package com.lihoo.socialcrop.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lihoo.socialcrop.NavState
import com.lihoo.socialcrop.R
import com.lihoo.socialcrop.ui.materialyou.M3
import com.lihoo.socialcrop.ui.materialyou.M3AlertDialog
import com.lihoo.socialcrop.ui.materialyou.M3Checkmark
import com.lihoo.socialcrop.ui.materialyou.M3RowDivider
import com.lihoo.socialcrop.ui.materialyou.m3Tap
import com.lihoo.socialcrop.util.LocaleManager

/** 从 Compose 的 LocalContext（可能是多级 ContextWrapper）里找到宿主 Activity */
fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

/** 保存偏好并重建 Activity：attachBaseContext 会读到新语言，AppRoot 会恢复当前页面 */
fun applyLanguage(context: Context, tag: String) {
    LocaleManager.set(context.applicationContext, tag)
    NavState.restoreRoute = NavState.lastRoute
    context.findActivity()?.recreate()
}

/** iOS 风格语言选择：居中卡片 + 选中项蓝色勾 + 底部蓝色取消 */
@Composable
fun LanguageDialog(
    current: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    val options = listOf(
        LocaleManager.SYSTEM to stringResource(R.string.language_system),
        LocaleManager.ZH to stringResource(R.string.language_zh),
        LocaleManager.EN to stringResource(R.string.language_en)
    )
    M3AlertDialog(
        title = stringResource(R.string.language_dialog_title),
        dismissText = stringResource(R.string.language_cancel),
        onDismiss = onDismiss
    ) {
        options.forEachIndexed { idx, (tag, label) ->
            LanguageOption(
                label = label,
                selected = current == tag,
                onClick = { onSelect(tag) }
            )
            if (idx < options.lastIndex) M3RowDivider()
        }
    }
}

@Composable
private fun LanguageOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .m3Tap(onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.material3.Text(
            label,
            fontSize = 17.sp,
            color = M3.palette.label,
            modifier = Modifier.weight(1f)
        )
        if (selected) M3Checkmark()
    }
}
