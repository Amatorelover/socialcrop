package com.lihoo.socialcrop.domain

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore

/**
 * 导出与分享：通过 MediaStore 写入相册（Android 10+ 零权限），
 * 并通过系统选择器分享多张图片。
 */
object ExportManager {

    fun saveBitmaps(
        context: Context,
        bitmaps: List<Bitmap>,
        folderName: String = "SocialCrop"
    ): List<Uri> {
        val resolver = context.contentResolver
        val uris = mutableListOf<Uri>()
        val stamp = System.currentTimeMillis()
        bitmaps.forEachIndexed { i, bmp ->
            val name = "socialcrop_${stamp}_$i.png"
            val values = android.content.ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/$folderName")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            // insert 也可能抛 SecurityException（部分 OEM 的分区存储实现），必须在 try 内
            val uri = try {
                resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            } catch (_: Exception) {
                null
            } ?: return@forEachIndexed

            var ok = false
            try {
                // openOutputStream 返回 null 时旧代码仍会把 IS_PENDING 置 0 并计入成功，
                // 相册里留下一个 0 字节文件还弹「已保存 N 张」。这里视为失败。
                val stream = resolver.openOutputStream(uri)
                if (stream != null) {
                    stream.use { out ->
                        if (!bmp.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                            throw java.io.IOException("compress failed")
                        }
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        values.clear()
                        values.put(MediaStore.Images.Media.IS_PENDING, 0)
                        resolver.update(uri, values, null, null)
                    }
                    ok = true
                }
            } catch (_: Exception) {
                ok = false
            }
            if (ok) {
                uris.add(uri)
            } else {
                // 清掉 0 字节 / IS_PENDING=1 的孤儿记录，别在相册里留垃圾
                runCatching { resolver.delete(uri, null, null) }
            }
        }
        return uris
    }

    fun shareIntent(uris: List<Uri>, title: String): Intent {
        // 单张图有很多应用只注册 ACTION_SEND，用 ACTION_SEND_MULTIPLE 会收不到
        val single = uris.size == 1
        val intent = Intent().apply {
            action = if (single) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE
            type = "image/png"
            if (single) putExtra(Intent.EXTRA_STREAM, uris.first())
            else putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(intent, title)
    }
}
