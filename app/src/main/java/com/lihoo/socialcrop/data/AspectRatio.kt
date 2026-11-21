package com.lihoo.socialcrop.data

data class RatioOption(
    val label: String,
    val w: Int,
    val h: Int,
    /** true = 跟随上传图片自身的宽高比，不裁切任何画面 */
    val isOriginal: Boolean = false
) {
    val ratio: Float get() = w.toFloat() / h.toFloat()
    val pixelLabel: String get() = "${w}x$h"

    companion object {
        /** w/h 只是占位，真实比例由 EditorViewModel 从当前图片读取 */
        val ORIGINAL = RatioOption("original", 1, 1, isOriginal = true)
    }
}
