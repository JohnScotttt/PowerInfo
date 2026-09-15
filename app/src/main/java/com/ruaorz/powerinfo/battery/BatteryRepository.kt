package com.ruaorz.powerinfo.battery

import android.content.Context
import com.ruaorz.powerinfo.root.RootShell

/**
 * 电源信息读取仓库。
 *
 * 负责从 assets 加载字段定义，并通过一个**常驻 root shell 会话**批量读取各 sysfs 节点的值。
 * 常驻会话避免了每次刷新都新建 su 进程/重走授权，是高频自动刷新能跟上的关键。
 */
class BatteryRepository(private val context: Context) {

    private val shell = RootShell()

    /** 用于分隔各字段输出的标记，取较难与节点内容冲突的形式。 */
    private val marker = "@@PI_FIELD@@"

    /** 确保常驻 root 会话已建立。首次调用可能触发授权弹窗。 */
    fun ensureRoot(): Boolean = shell.open()

    /** 释放常驻会话。 */
    fun release() = shell.close()

    /** 从 assets 加载并解析字段定义。 */
    fun loadFields(): List<BatteryField> {
        val xml = context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
        return BatteryFieldParser.parse(xml)
    }

    /**
     * 通过常驻会话批量读取所有字段的值。
     *
     * @return 每个字段对应的读取结果；无有效 path 或读取失败的字段其 value 为 null。
     */
    fun readAll(fields: List<BatteryField>): List<BatteryReading> {
        val readable = fields.withIndex().filter { it.value.path != null }
        if (readable.isEmpty() || !shell.isAlive) {
            return fields.map { BatteryReading(it.name, it.path, null) }
        }

        // 拼接批量命令：每个节点前打印 "标记 索引"，随后 cat 其内容。
        val command = buildString {
            for ((index, field) in readable) {
                append("echo '").append(marker).append(index).append("'; ")
                append("cat '").append(field.path).append("' 2>/dev/null; ")
            }
        }

        val outputLines = shell.exec(command)
        val valuesByIndex = parseOutput(outputLines)

        return fields.mapIndexed { index, field ->
            BatteryReading(
                name = field.name,
                path = field.path,
                value = if (field.path == null) null else valuesByIndex[index],
            )
        }
    }

    /**
     * 按标记切分批量输出，还原出 索引 -> 值 的映射。
     * 值可能跨多行，取标记之间的全部内容并去除首尾空白。
     */
    private fun parseOutput(lines: List<String>): Map<Int, String> {
        val map = mutableMapOf<Int, String>()
        var currentIndex: Int? = null
        val buffer = StringBuilder()

        fun flush() {
            val idx = currentIndex ?: return
            map[idx] = buffer.toString().trim()
            buffer.setLength(0)
        }

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith(marker)) {
                flush()
                currentIndex = trimmed.removePrefix(marker).toIntOrNull()
            } else if (currentIndex != null) {
                if (buffer.isNotEmpty()) buffer.append('\n')
                buffer.append(line)
            }
        }
        flush()
        return map
    }

    private companion object {
        const val ASSET_NAME = "battery_datas.xml"
    }
}
