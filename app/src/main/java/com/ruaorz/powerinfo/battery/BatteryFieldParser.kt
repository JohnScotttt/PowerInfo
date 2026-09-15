package com.ruaorz.powerinfo.battery

/**
 * 电源字段定义文件的容错解析器。
 *
 * 定义文件为类 XML 格式，但可能包含畸形标签（例如首条 `MB_00` 的 path 标签不闭合、值为 null），
 * 因此这里不使用严格 XML 解析器，而是用正则逐块提取，任何无法解析出有效 path 的字段其 path 记为 null。
 */
object BatteryFieldParser {

    private val BLOCK_REGEX =
        Regex("<BatteryData>(.*?)</BatteryData>", RegexOption.DOT_MATCHES_ALL)
    private val NAME_REGEX = Regex("<name>(.*?)</name>", RegexOption.DOT_MATCHES_ALL)
    private val PATH_REGEX = Regex("<path>(.*?)</path>", RegexOption.DOT_MATCHES_ALL)

    /**
     * 解析定义文件内容，返回字段列表。
     *
     * @param xml 定义文件全文。
     */
    fun parse(xml: String): List<BatteryField> {
        return BLOCK_REGEX.findAll(xml).mapNotNull { block ->
            val body = block.groupValues[1]
            val name = NAME_REGEX.find(body)?.groupValues?.get(1)?.trim()
                ?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null

            val rawPath = PATH_REGEX.find(body)?.groupValues?.get(1)?.trim()
            val path = rawPath
                ?.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }

            BatteryField(name = name, path = path)
        }.toList()
    }
}
