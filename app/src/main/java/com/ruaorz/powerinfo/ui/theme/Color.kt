package com.ruaorz.powerinfo.ui.theme

import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

/**
 * 一组语义配色：前景（文字/图标）与背景（容器）。
 * 供 miuix 风格下的提示条等需要固定语义色的场景使用。
 */
data class SemanticColor(val fg: Color, val bg: Color)

/** miuix 风格语义色板：正常 / 警告 / 错误 / 提示。 */
object MiuixSemanticColors {
    /** 正常 Green。 */
    val Normal = SemanticColor(fg = Color(0xFF2E7D32), bg = Color(0xFFE8F5E9))

    /** 警告 Orange。 */
    val Warning = SemanticColor(fg = Color(0xFFE65100), bg = Color(0xFFFFF3E0))

    /** 错误 Red。 */
    val Error = SemanticColor(fg = Color(0xFFC62828), bg = Color(0xFFFFEBEE))

    /** 提示 Blue。 */
    val Info = SemanticColor(fg = Color(0xFF1565C0), bg = Color(0xFFE3F2FD))
}