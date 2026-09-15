package com.ruaorz.powerinfo.battery

/**
 * 一条电源信息字段定义。
 *
 * @param name 显示标签（来自定义文件，例如 `MB_01:`）。
 * @param path 要读取的 sysfs 节点路径；无效（null / 空）时为 null。
 */
data class BatteryField(
    val name: String,
    val path: String?,
)

/**
 * 一条已读取到值的电源信息。
 *
 * @param name 显示标签。
 * @param path 节点路径，可能为 null。
 * @param value 读取到的值；读取失败或路径无效时为 null。
 */
data class BatteryReading(
    val name: String,
    val path: String?,
    val value: String?,
) {
    /** 用于展示的值，无值时回退为占位符。 */
    val displayValue: String
        get() = value?.takeIf { it.isNotBlank() } ?: "N/A"
}
