package com.lihoo.socialcrop.ui

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lihoo.socialcrop.BuildConfig
import com.lihoo.socialcrop.R
import com.lihoo.socialcrop.ui.materialyou.M3
import com.lihoo.socialcrop.ui.materialyou.M3Card
import com.lihoo.socialcrop.ui.materialyou.M3LargeTitle
import com.lihoo.socialcrop.ui.materialyou.M3NavBar
import com.lihoo.socialcrop.ui.materialyou.M3SecondaryButton
import com.lihoo.socialcrop.ui.materialyou.M3Section
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 「关于 · 开源与捐助」页。
 * 版本号来自 BuildConfig，由构建脚本注入，不会与真实版本脱节。
 * 所有链接与文案集中在 strings.xml，替换渠道无需改动代码。
 */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val p = M3.palette
    var qrDialogRes by remember { mutableStateOf<Int?>(null) }

    fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        if (runCatching { context.startActivity(intent) }.isFailure) {
            Toast.makeText(context, R.string.about_link_error, Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        containerColor = p.groupedBackground,
        topBar = {
            M3NavBar(onBack = onBack, backText = stringResource(R.string.cd_back))
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 40.dp)
        ) {
            M3LargeTitle(stringResource(R.string.about_title))
            Spacer(Modifier.height(8.dp))

            // ---------- 抬头：应用名与版本 ----------
            M3Card(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(p.blue.copy(alpha = 0.13f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.PhotoLibrary,
                            contentDescription = null,
                            tint = p.blue,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.app_name) + " · SocialCrop",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = p.label
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.about_tagline),
                            fontSize = 13.sp,
                            color = p.secondaryLabel,
                            lineHeight = 18.sp
                        )
                    }
                }
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    color = p.separator,
                    thickness = 0.5.dp
                )
                Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                    InfoRow(
                        stringResource(R.string.about_label_version),
                        "${BuildConfig.APP_VERSION} (${BuildConfig.APP_VERSION_CODE})"
                    )
                    InfoRow(stringResource(R.string.about_label_author), BuildConfig.AUTHOR_NAME)
                    InfoRow(stringResource(R.string.about_label_license), "MIT License")
                    InfoRow(
                        stringResource(R.string.about_label_android),
                        "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
                    )
                }
            }

            // ---------- 应用说明 ----------
            SectionCard(
                icon = Icons.Filled.Info,
                title = stringResource(R.string.about_section_intro)
            ) {
                BodyText(stringResource(R.string.about_intro_p1))
                Spacer(Modifier.height(10.dp))
                BodyText(stringResource(R.string.about_intro_p2))
            }

            // ---------- 隐私承诺 ----------
            SectionCard(
                icon = Icons.Filled.Lock,
                title = stringResource(R.string.about_section_privacy)
            ) {
                Bullet(stringResource(R.string.about_privacy_1))
                Bullet(stringResource(R.string.about_privacy_2))
                Bullet(stringResource(R.string.about_privacy_3))
            }

            // ---------- 开源说明 ----------
            SectionCard(
                icon = Icons.Filled.Code,
                title = stringResource(R.string.about_section_opensource)
            ) {
                BodyText(stringResource(R.string.about_opensource_p1))
                Spacer(Modifier.height(10.dp))
                BodyText(stringResource(R.string.about_opensource_p2))
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.about_opensource_repo_hint),
                    fontSize = 13.sp,
                    color = p.secondaryLabel
                )
                Text(
                    BuildConfig.GITHUB_URL,
                    fontSize = 13.sp,
                    color = p.blue
                )
                Spacer(Modifier.height(14.dp))
                // 两个按钮等分整行，中英文都对称；不再靠文案宽度自然排布而留半行空白
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    M3SecondaryButton(
                        text = stringResource(R.string.about_view_source),
                        onClick = { openUrl(BuildConfig.GITHUB_URL) },
                        compact = true,
                        modifier = Modifier.weight(1f)
                    )
                    M3SecondaryButton(
                        text = stringResource(R.string.about_copy_version),
                        onClick = {
                            clipboard.setText(
                                AnnotatedString(
                                    context.getString(
                                        R.string.about_version_line,
                                        BuildConfig.APP_VERSION,
                                        BuildConfig.APP_VERSION_CODE,
                                        Build.VERSION.RELEASE,
                                        Build.VERSION.SDK_INT
                                    )
                                )
                            )
                        },
                        compact = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // ---------- 声明与免责 ----------
            SectionCard(
                icon = Icons.Filled.Info,
                title = stringResource(R.string.about_section_disclaimer)
            ) {
                BodyText(stringResource(R.string.about_disclaimer_p1))
                Spacer(Modifier.height(10.dp))
                BodyText(stringResource(R.string.about_disclaimer_p2))
            }

            // ---------- 支持作者 ----------
            SectionCard(
                icon = Icons.Filled.Favorite,
                title = stringResource(R.string.about_section_sponsor)
            ) {
                BodyText(stringResource(R.string.about_sponsor_body))
                Spacer(Modifier.height(14.dp))
                // 三个按钮等分整行：中文短、英文长都不会出现一侧空一截的失衡感
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    M3SecondaryButton(
                        text = stringResource(R.string.about_sponsor_afdian),
                        onClick = { openUrl(context.getString(R.string.about_sponsor_url_afdian)) },
                        compact = true,
                        modifier = Modifier.weight(1f)
                    )
                    M3SecondaryButton(
                        text = stringResource(R.string.about_sponsor_wechat),
                        onClick = { qrDialogRes = R.drawable.donate_wechat },
                        compact = true,
                        modifier = Modifier.weight(1f)
                    )
                    M3SecondaryButton(
                        text = stringResource(R.string.about_sponsor_alipay),
                        onClick = { qrDialogRes = R.drawable.donate_alipay },
                        compact = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    // ---------- 收款二维码弹窗（与遮幕 SubBlock 一致） ----------
    val activeQr = qrDialogRes
    if (activeQr != null) {
        QrCodeDialog(
            resId = activeQr,
            title = stringResource(
                if (activeQr == R.drawable.donate_wechat) R.string.about_qr_title_wechat
                else R.string.about_qr_title_alipay
            ),
            onDismiss = { qrDialogRes = null }
        )
    }
}

/**
 * 收款码弹窗（移植自遮幕 SubBlock，行为与外观保持一致）。
 *
 * 两张码为纯二维码并统一为 1:1，这里用正方形白底 + [ContentScale.Fit] 展示，
 * 二维码完整不裁切。长按图片或点「保存到相册」都走 [saveQrToGallery]——
 * 只写 MediaStore，不申请任何存储权限，守住零权限承诺。
 */
@Composable
private fun QrCodeDialog(resId: Int, title: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 品牌色标识：微信绿 / 支付宝蓝，纯码外观相近时一眼可辨
    val isWechat = resId == R.drawable.donate_wechat
    val brandColor = if (isWechat) Color(0xFF07C160) else Color(0xFF1677FF)
    val brandLabel = stringResource(
        if (isWechat) R.string.about_sponsor_wechat else R.string.about_sponsor_alipay
    )

    fun save() {
        scope.launch {
            val ok = withContext(Dispatchers.IO) { saveQrToGallery(context, resId) }
            Toast.makeText(
                context,
                context.getString(if (ok) R.string.toast_qr_saved else R.string.toast_qr_save_failed),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(12.dp),
                    shape = CircleShape,
                    color = brandColor
                ) {}
                Spacer(Modifier.width(8.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = brandColor
                )
            }
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    shadowElevation = 2.dp,
                    modifier = Modifier
                        .size(260.dp) // 1:1，与纯二维码资源的正方形一致
                        .pointerInput(resId) {
                            detectTapGestures(onLongPress = { save() })
                        }
                ) {
                    Image(
                        painter = painterResource(resId),
                        contentDescription = title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit // 完整显示二维码，不裁切定位角
                    )
                }
                Spacer(Modifier.height(12.dp))
                // 品牌色胶囊标签：微信绿 / 支付宝蓝，纯码外观相近时一眼可辨
                Surface(
                    shape = RoundedCornerShape(50),
                    color = brandColor
                ) {
                    Text(
                        brandLabel,
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 5.dp)
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.about_qr_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                FilledTonalButton(onClick = { save() }) {
                    Icon(
                        Icons.Filled.Download,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.about_qr_save))
                }
            }
        }
    )
}

/**
 * 把二维码 drawable 存进系统相册（与遮幕 SubBlock 一致）。
 *
 * 全程只通过 MediaStore 写入，**不申请 READ/WRITE_EXTERNAL_STORAGE**：
 *  - Android 10+：MediaStore + IS_PENDING，系统授予应用写自己条目的权限；
 *  - Android 9 及以下：MediaStore.insert + openOutputStream 由系统代理落盘。
 * 因此不需要在 manifest 加任何存储权限，零权限承诺不变。
 *
 * 返回是否成功；任何异常都收敛为 false，由调用方给出文案反馈。
 */
private fun saveQrToGallery(context: Context, resId: Int): Boolean {
    val bitmap = BitmapFactory.decodeResource(context.resources, resId) ?: return false
    val name = "SocialCrop-QR-${System.currentTimeMillis()}.png"
    return runCatching {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/SocialCrop")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return false
        resolver.openOutputStream(uri)?.use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        } ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        true
    }.getOrDefault(false)
}

@Composable
private fun InfoRow(label: String, value: String) {
    val p = M3.palette
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            label,
            fontSize = 13.sp,
            color = p.secondaryLabel,
            modifier = Modifier.width(80.dp)
        )
        Text(
            value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = p.label
        )
    }
}

@Composable
private fun SectionCard(
    icon: ImageVector,
    title: String,
    content: @Composable () -> Unit
) {
    val p = M3.palette
    M3Section {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(17.dp),
                    tint = p.blue
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = p.label
                )
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun BodyText(text: String) {
    Text(
        text,
        fontSize = 15.sp,
        color = M3.palette.secondaryLabel,
        lineHeight = 21.sp
    )
}

@Composable
private fun Bullet(text: String) {
    val p = M3.palette
    Row(modifier = Modifier.padding(bottom = 8.dp)) {
        Text("·", fontSize = 15.sp, color = p.blue)
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 15.sp, color = p.secondaryLabel, lineHeight = 21.sp)
    }
}
