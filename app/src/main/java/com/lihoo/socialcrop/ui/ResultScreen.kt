package com.lihoo.socialcrop.ui

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.lihoo.socialcrop.R
import com.lihoo.socialcrop.domain.ExportManager
import com.lihoo.socialcrop.ui.materialyou.M3
import com.lihoo.socialcrop.ui.materialyou.M3BottomBar
import com.lihoo.socialcrop.ui.materialyou.M3NavBar
import com.lihoo.socialcrop.ui.materialyou.M3PrimaryButton
import com.lihoo.socialcrop.ui.materialyou.M3SecondaryButton
import com.lihoo.socialcrop.viewmodel.EditorViewModel

@Composable
fun ResultScreen(nav: NavController, vm: EditorViewModel) {
    val context = LocalContext.current
    val results = vm.result
    val p = M3.palette
    // 换一批结果时重置，避免上一批的「已保存」状态带到新图
    var saved by remember(results) { mutableStateOf(false) }

    Scaffold(
        containerColor = p.groupedBackground,
        topBar = {
            M3NavBar(
                title = stringResource(R.string.result_title, results.size),
                onBack = { nav.popBackStack() },
                backText = stringResource(R.string.cd_back)
            )
        },
        bottomBar = {
            M3BottomBar {
                M3SecondaryButton(
                    text = stringResource(
                        if (saved) R.string.action_saved else R.string.action_save_gallery
                    ),
                    enabled = results.isNotEmpty() && !saved,
                    onClick = {
                        val uris = ExportManager.saveBitmaps(context, results)
                        if (uris.isEmpty()) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.toast_save_failed),
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            vm.resultUris = uris
                            saved = true
                            Toast.makeText(
                                context,
                                context.getString(R.string.toast_saved, uris.size),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
                M3PrimaryButton(
                    text = stringResource(R.string.action_share),
                    enabled = results.isNotEmpty(),
                    onClick = {
                        val uris = if (vm.resultUris.isNotEmpty()) vm.resultUris
                        else ExportManager.saveBitmaps(context, results)
                        if (uris.isEmpty()) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.toast_save_failed),
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            vm.resultUris = uris
                            saved = true
                            runCatching {
                                context.startActivity(
                                    ExportManager.shareIntent(
                                        uris,
                                        context.getString(R.string.share_chooser_title)
                                    )
                                )
                            }.onFailure {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.toast_share_failed),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(p.groupedBackground)
                .padding(padding)
        ) {
            if (results.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.result_empty), color = p.secondaryLabel, fontSize = 17.sp)
                }
                return@Scaffold
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(120.dp),
                modifier = Modifier.fillMaxSize().padding(12.dp),
                contentPadding = PaddingValues(4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(results, key = { it.hashCode() }) { bmp ->
                    val ratio = remember(bmp) {
                        bmp.width.toFloat() / bmp.height.coerceAtLeast(1)
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(p.card)
                            .aspectRatio(ratio.coerceAtLeast(0.01f))
                    ) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}
