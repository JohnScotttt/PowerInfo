package com.ruaorz.powerinfo.root

import android.os.Build
import android.util.Log
import java.io.DataOutputStream
import java.io.File

/**
 * Root 权限检测与授权工具。
 *
 * 区分两类操作：
 *  - [isRootAvailable]：静态检测设备是否「疑似」已 root，不会触发授权弹窗，可用于快速预判；
 *  - [requestRootAccess]：真正执行 `su -c` 命令，会触发超级用户管理器（Magisk / KernelSU / SuperSU）
 *    的授权弹窗，并根据用户是否放行返回结果。
 *
 * 以上方法均可能执行外部进程，请勿在主线程调用。
 */
object RootChecker {

    private const val TAG = "RootChecker"

    private val SU_PATHS = arrayOf(
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/system/su",
        "/system/bin/.ext/.su",
        "/system/xbin/mu",
        "/su/bin/su",
        "/data/local/su",
        "/data/local/bin/su",
        "/data/local/xbin/su",
        "/vendor/bin/su",
    )

    /**
     * 静态检测设备是否疑似已 root，不会触发授权弹窗。
     * 任意一种手段命中即返回 true。
     */
    fun isRootAvailable(): Boolean {
        return hasSuBinary() || hasTestKeys() || whichSu()
    }

    /**
     * 真正申请 root 权限：执行 `su -c id`。
     *
     * 首次调用通常会弹出超级用户管理器的授权对话框；
     * 用户放行则返回 true，拒绝或设备未 root 则返回 false。
     *
     * @return 是否成功获取到 root 权限。
     */
    fun requestRootAccess(): Boolean {
        val result = execAsRoot("id")
        if (!result.success) {
            Log.d(TAG, "requestRootAccess failed, exit=${result.exitCode}, err=${result.error}")
            return false
        }
        // 成功获取 root 时，`id` 输出应包含 uid=0(root)
        val granted = result.output.contains("uid=0")
        Log.d(TAG, "requestRootAccess output=${result.output}, granted=$granted")
        return granted
    }

    /**
     * 以 root 身份执行一条 shell 命令。
     *
     * @param command 要执行的命令，例如 `"id"`、`"cat /sys/class/power_supply/battery/uevent"`。
     * @return 执行结果，[ShellResult.success] 表示进程正常退出（exitCode == 0）。
     */
    fun execAsRoot(command: String): ShellResult {
        var process: Process? = null
        return try {
            process = Runtime.getRuntime().exec("su")
            DataOutputStream(process.outputStream).use { os ->
                os.writeBytes(command + "\n")
                os.writeBytes("exit\n")
                os.flush()
            }
            val output = process.inputStream.bufferedReader().readText().trim()
            val error = process.errorStream.bufferedReader().readText().trim()
            val exitCode = process.waitFor()
            ShellResult(exitCode == 0, exitCode, output, error)
        } catch (e: Exception) {
            Log.d(TAG, "execAsRoot exception: ${e.message}")
            ShellResult(false, -1, "", e.message ?: "unknown error")
        } finally {
            process?.destroy()
        }
    }

    /** 检查常见路径下是否存在 su 二进制文件。 */
    private fun hasSuBinary(): Boolean {
        return SU_PATHS.any { runCatching { File(it).exists() }.getOrDefault(false) }
    }

    /** 检查 build tags 是否包含 test-keys。 */
    private fun hasTestKeys(): Boolean {
        val tags = Build.TAGS
        return tags != null && tags.contains("test-keys")
    }

    /** 通过 `which su` 判断 su 是否在 PATH 中，不会触发授权弹窗。 */
    private fun whichSu(): Boolean {
        var process: Process? = null
        return try {
            process = Runtime.getRuntime().exec(arrayOf("which", "su"))
            val output = process.inputStream.bufferedReader().readLine()
            !output.isNullOrBlank()
        } catch (_: Exception) {
            false
        } finally {
            process?.destroy()
        }
    }
}

/**
 * Shell 命令执行结果。
 *
 * @param success 进程是否正常退出（exitCode == 0）。
 * @param exitCode 进程退出码，异常时为 -1。
 * @param output 标准输出内容。
 * @param error 标准错误内容或异常信息。
 */
data class ShellResult(
    val success: Boolean,
    val exitCode: Int,
    val output: String,
    val error: String,
)
