package com.ruaorz.powerinfo.root

import android.util.Log
import java.io.BufferedReader
import java.io.DataOutputStream

/**
 * 常驻 root shell 会话。
 *
 * 持有一个长期存活的 `su` 进程，反复向其 stdin 写入命令、从 stdout 读取结果，
 * 避免每次读取都新建进程并重新走授权校验——这是高频刷新（如 0.1s）能跟上的关键。
 *
 * 线程不安全的底层流通过 [exec] 上的同步保护；所有方法均可能阻塞，请勿在主线程调用。
 */
class RootShell {

    private var process: Process? = null
    private var stdin: DataOutputStream? = null
    private var stdout: BufferedReader? = null

    /** 命令输出结束哨兵，跟随退出码。取不易与节点内容冲突的形式。 */
    private val endMarker = "__PI_CMD_END__"

    /** 会话是否存活。 */
    val isAlive: Boolean
        @Synchronized get() = process?.isAlive == true

    /**
     * 打开会话并验证获得 root。会触发一次超级用户授权弹窗（若尚未授权）。
     *
     * @return 是否成功建立 root 会话。
     */
    @Synchronized
    fun open(): Boolean {
        if (isAlive) return true
        return try {
            val p = Runtime.getRuntime().exec("su")
            process = p
            stdin = DataOutputStream(p.outputStream)
            stdout = p.inputStream.bufferedReader()
            // 用 id 验证确实拿到 root。
            val out = execInternal("id")
            val granted = out.any { it.contains("uid=0") }
            if (!granted) close()
            granted
        } catch (e: Exception) {
            Log.d(TAG, "open failed: ${e.message}")
            close()
            false
        }
    }

    /**
     * 在常驻会话中执行一条命令，返回其标准输出的各行。
     * 会话未打开或已断开时返回空列表。
     */
    @Synchronized
    fun exec(command: String): List<String> {
        if (!isAlive) return emptyList()
        return try {
            execInternal(command)
        } catch (e: Exception) {
            Log.d(TAG, "exec failed: ${e.message}")
            close()
            emptyList()
        }
    }

    /** 实际写命令并读取到哨兵行为止；调用方需持有同步锁并保证流已就绪。 */
    private fun execInternal(command: String): List<String> {
        val os = stdin ?: return emptyList()
        val reader = stdout ?: return emptyList()

        os.writeBytes(command + "\n")
        // 追加哨兵，标记本条命令输出结束。
        os.writeBytes("echo $endMarker\n")
        os.flush()

        val lines = mutableListOf<String>()
        while (true) {
            val line = reader.readLine() ?: break // 流关闭
            if (line.startsWith(endMarker)) break
            lines.add(line)
        }
        return lines
    }

    /** 关闭会话，释放进程与流。 */
    @Synchronized
    fun close() {
        runCatching { stdin?.writeBytes("exit\n"); stdin?.flush() }
        runCatching { stdin?.close() }
        runCatching { stdout?.close() }
        runCatching { process?.destroy() }
        stdin = null
        stdout = null
        process = null
    }

    private companion object {
        const val TAG = "RootShell"
    }
}
