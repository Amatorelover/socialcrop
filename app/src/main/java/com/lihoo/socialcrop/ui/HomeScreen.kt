package com.lihoo.socialcrop.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.lihoo.socialcrop.R
import com.lihoo.socialcrop.ui.materialyou.M3
import com.lihoo.socialcrop.ui.materialyou.M3Badge
import com.lihoo.socialcrop.ui.materialyou.M3BarIcon
import com.lihoo.socialcrop.ui.materialyou.M3Card
import com.lihoo.socialcrop.ui.materialyou.M3Hint
import com.lihoo.socialcrop.ui.materialyou.M3LargeTitle
import com.lihoo.socialcrop.ui.materialyou.M3NavBar
import com.lihoo.socialcrop.ui.materialyou.M3PrimaryButton
import com.lihoo.socialcrop.ui.materialyou.M3Row
import com.lihoo.socialcrop.ui.materialyou.M3Section
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
                actions = {
                    M3BarIcon(
                        Icons.Filled.Language,
                        stringResource(R.string.cd_language)
                    ) { showLanguage = true }
                    M3BarIcon(
                        Icons.Filled.Info,
                        stringResource(R.string.cd_about)
                    ) { nav.navigate("about") }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 28.dp)
        ) {
            M3LargeTitle(
                stringResource(R.string.home_headline),
                stringResource(R.string.home_desc)
            )
            Spacer(Modifier.height(12.dp))

            // 抬头卡：图标 + 应用名 + 一句话能力概览
            M3Card(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(vertical = 8.dp)
            ) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(58.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(p.blue.copy(alpha = 0.13f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.PhotoLibrary,
                            contentDescription = null,
                            tint = p.blue,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.app_name),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = p.label
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.home_features),
                            fontSize = 13.sp,
                            color = p.secondaryLabel,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            M3Section(title = stringResource(R.string.home_section_features)) {
                M3Row(
                    title = stringResource(R.string.home_feature_crop),
                    subtitle = stringResource(R.string.home_feature_crop_desc),
                    trailing = { M3Badge("01") }
                )
                M3Row(
                    title = stringResource(R.string.home_feature_frame),
                    subtitle = stringResource(R.string.home_feature_frame_desc),
                    trailing = { M3Badge("02") }
                )
                M3Row(
                    title = stringResource(R.string.home_feature_slice),
                    subtitle = stringResource(R.string.home_feature_slice_desc),
                    divider = false,
                    trailing = { M3Badge("03") }
                )
            }

            Spacer(Modifier.height(16.dp))
            M3PrimaryButton(
                text = stringResource(R.string.home_pick),
                onClick = {
                    picker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            )
            M3Hint(stringResource(R.string.home_privacy))
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
