package com.lihoo.socialcrop.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.lihoo.socialcrop.R
import com.lihoo.socialcrop.ui.materialyou.M3
import com.lihoo.socialcrop.ui.materialyou.M3BarIcon
import com.lihoo.socialcrop.ui.materialyou.M3NavBar
import com.lihoo.socialcrop.ui.materialyou.M3PrimaryButton
import com.lihoo.socialcrop.util.LocaleManager
import com.lihoo.socialcrop.viewmodel.EditorViewModel

@Composable
fun HomeScreen(nav: NavController, vm: EditorViewModel) {
    val context = LocalContext.current
    val p = M3.palette
    var showLanguage by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val bmp = vm.load(context, uri)
        if (bmp == null) {
            Toast.makeText(
                context,
                context.getString(R.string.toast_decode_failed),
                Toast.LENGTH_SHORT
            ).show()
        } else {
            nav.navigate("editor")
        }
    }

    Scaffold(
        containerColor = p.groupedBackground,
        topBar = {
            M3NavBar(
                // 语言入口放左边、关于放右边，两侧图标对称；中间留空保持首页极简
                leading = {
                    M3BarIcon(
                        Icons.Filled.Language,
                        stringResource(R.string.cd_language)
                    ) { showLanguage = true }
                },
                actions = {
                    M3BarIcon(
                        Icons.Filled.Info,
                        stringResource(R.string.cd_about)
                    ) { nav.navigate("about") }
                }
            )
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 首页只保留一句悬浮主文案 + 选图入口，说明与功能清单全部下沉到实际页面
                Text(
                    stringResource(R.string.home_headline),
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = p.label,
                    textAlign = TextAlign.Center,
                    lineHeight = 40.sp
                )
                Spacer(Modifier.height(28.dp))
                M3PrimaryButton(
                    text = stringResource(R.string.home_pick),
                    onClick = {
                        picker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    if (showLanguage) {
        LanguageDialog(
            current = LocaleManager.get(context),
            onDismiss = { showLanguage = false },
            onSelect = { tag ->
                showLanguage = false
                applyLanguage(context, tag)
            }
        )
    }
}
