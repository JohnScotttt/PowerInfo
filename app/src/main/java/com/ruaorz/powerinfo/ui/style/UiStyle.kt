package com.ruaorz.powerinfo.ui.style

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 应用界面风格。
 *
 * - [MATERIAL]：默认，使用 Material 3 组件与主题。
 * - [MIUIX]：使用 miuix（top.yukonga.miuix.kmp）组件与主题，观感对齐 HyperOS。
 */
enum class UiStyle {
    MATERIAL,
    MIUIX,
    ;

    companion object {
        /** 从持久化字符串还原，无法识别时回退到 [MATERIAL]。 */
        fun fromName(name: String?): UiStyle =
            entries.firstOrNull { it.name == name } ?: MATERIAL
    }
}

/**
 * 界面风格的读写与状态载体：
 * - 用 [SharedPreferences] 同步持久化当前选择（仅一个枚举，无需 DataStore/协程 Flow）。
 * - 用 Compose 的 [mutableStateOf] 暴露 [style]，切换时触发整棵组合树重组，实现全局实时换肤。
 *
 * 在 `setContent` 中 `remember { UiStylePreference(context) }` 构造一次，
 * 通过 [LocalUiStyle] 下发给各界面读取与切换。
 */
class UiStylePreference(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 当前风格；作为 Compose 状态，赋值即触发重组。 */
    var style: UiStyle by mutableStateOf(UiStyle.fromName(prefs.getString(KEY_STYLE, null)))
        private set

    /** 切换风格：先落盘再更新状态，保证重启后保持且立即重组换肤。 */
    fun select(newStyle: UiStyle) {
        if (newStyle == style) return
        prefs.edit().putString(KEY_STYLE, newStyle.name).apply()
        style = newStyle
    }

    private companion object {
        const val PREFS_NAME = "settings"
        const val KEY_STYLE = "ui_style"
    }
}

/**
 * 当前界面风格的偏好载体。未提供时回退到一个只读的 [MATERIAL] 占位（仅用于预览等场景）。
 */
val LocalUiStyle = staticCompositionLocalOf<UiStylePreference> {
    error("LocalUiStyle 未提供，请在 setContent 中用 CompositionLocalProvider 下发 UiStylePreference")
}
