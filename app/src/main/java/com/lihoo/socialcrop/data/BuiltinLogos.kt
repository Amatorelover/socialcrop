package com.lihoo.socialcrop.data

import androidx.annotation.StringRes
import com.lihoo.socialcrop.R

/** 内置品牌 Logo 的两个分类：手机厂商 / 相机（镜头）厂商。 */
enum class BuiltinLogoGroup { PHONE, CAMERA }

/**
 * 一条内置品牌 Logo。
 * [id] 同时是 assets/brand_logos 下的文件名（不含扩展名），选中后会把该资源
 * 复制到应用私有目录，之后和「从相册添加」的 Logo 走完全相同的渲染链路。
 */
data class BuiltinLogo(
    val id: String,
    @StringRes val nameRes: Int,
    val group: BuiltinLogoGroup
)

/**
 * 内置品牌 Logo 目录：28 个透明 PNG，预先从 worldvectorlogo.com 下载并随 APK 打包。
 * 运行时只从本地 assets 读取，不联网、不申请任何权限。
 */
object BuiltinLogos {
    val phones = listOf(
        BuiltinLogo("apple", R.string.brand_apple, BuiltinLogoGroup.PHONE),
        BuiltinLogo("samsung", R.string.brand_samsung, BuiltinLogoGroup.PHONE),
        BuiltinLogo("xiaomi", R.string.brand_xiaomi, BuiltinLogoGroup.PHONE),
        BuiltinLogo("huawei", R.string.brand_huawei, BuiltinLogoGroup.PHONE),
        BuiltinLogo("honor", R.string.brand_honor, BuiltinLogoGroup.PHONE),
        BuiltinLogo("oppo", R.string.brand_oppo, BuiltinLogoGroup.PHONE),
        BuiltinLogo("vivo", R.string.brand_vivo, BuiltinLogoGroup.PHONE),
        BuiltinLogo("oneplus", R.string.brand_oneplus, BuiltinLogoGroup.PHONE),
        BuiltinLogo("realme", R.string.brand_realme, BuiltinLogoGroup.PHONE),
        BuiltinLogo("redmi", R.string.brand_redmi, BuiltinLogoGroup.PHONE),
        BuiltinLogo("googlepixel", R.string.brand_googlepixel, BuiltinLogoGroup.PHONE),
        BuiltinLogo("sony", R.string.brand_sony, BuiltinLogoGroup.PHONE),
        BuiltinLogo("motorola", R.string.brand_motorola, BuiltinLogoGroup.PHONE),
        BuiltinLogo("lenovo", R.string.brand_lenovo, BuiltinLogoGroup.PHONE),
        BuiltinLogo("zte", R.string.brand_zte, BuiltinLogoGroup.PHONE),
        BuiltinLogo("meizu", R.string.brand_meizu, BuiltinLogoGroup.PHONE),
    )

    val cameras = listOf(
        BuiltinLogo("canon", R.string.brand_canon, BuiltinLogoGroup.CAMERA),
        BuiltinLogo("nikon", R.string.brand_nikon, BuiltinLogoGroup.CAMERA),
        BuiltinLogo("fujifilm", R.string.brand_fujifilm, BuiltinLogoGroup.CAMERA),
        BuiltinLogo("panasonic", R.string.brand_panasonic, BuiltinLogoGroup.CAMERA),
        BuiltinLogo("leica", R.string.brand_leica, BuiltinLogoGroup.CAMERA),
        BuiltinLogo("hasselblad", R.string.brand_hasselblad, BuiltinLogoGroup.CAMERA),
        BuiltinLogo("zeiss", R.string.brand_zeiss, BuiltinLogoGroup.CAMERA),
        BuiltinLogo("kodak", R.string.brand_kodak, BuiltinLogoGroup.CAMERA),
        BuiltinLogo("gopro", R.string.brand_gopro, BuiltinLogoGroup.CAMERA),
        BuiltinLogo("dji", R.string.brand_dji, BuiltinLogoGroup.CAMERA),
        BuiltinLogo("ricoh", R.string.brand_ricoh, BuiltinLogoGroup.CAMERA),
        BuiltinLogo("phaseone", R.string.brand_phaseone, BuiltinLogoGroup.CAMERA)
    )

    val all: List<BuiltinLogo> = phones + cameras

    fun assetPath(id: String): String = "brand_logos/$id.png"

    /** 去背白模：黑白单色化时使用，避免带底色的 Logo 变成一整块 */
    fun monoAssetPath(id: String): String = "brand_logos/${id}_mono.png"

    fun find(id: String): BuiltinLogo? = all.firstOrNull { it.id == id }
}
