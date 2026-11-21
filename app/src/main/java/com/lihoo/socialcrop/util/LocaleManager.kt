package com.lihoo.socialcrop.util

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * 应用内语言偏好。
 *
 * 不引入 AppCompat / 第三方库：把偏好写进 SharedPreferences，再由 Activity 的
 * attachBaseContext 用 createConfigurationContext 包一层，切换时重建 Activity 即可。
 * 「跟随系统」不做任何包装，交给系统按 values / values-zh 自动匹配。
 */
object LocaleManager {
    const val SYSTEM = "system"
    const val ZH = "zh"
    const val EN = "en"

    private const val PREF = "app_prefs"
    private const val KEY = "app_language"

    fun get(context: Context): String = runCatching {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY, SYSTEM) ?: SYSTEM
    }.getOrDefault(SYSTEM).let {
        if (it == ZH || it == EN || it == SYSTEM) it else SYSTEM
    }

    fun set(context: Context, tag: String) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, tag)
            .apply()
    }

    /** 返回带指定语言配置的 Context；SYSTEM 时原样返回 */
    fun wrap(context: Context, tag: String): Context {
        val locale = when (tag) {
            ZH -> Locale.SIMPLIFIED_CHINESE
            EN -> Locale.ENGLISH
            else -> return context
        }
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return context.createConfigurationContext(config)
    }
}
