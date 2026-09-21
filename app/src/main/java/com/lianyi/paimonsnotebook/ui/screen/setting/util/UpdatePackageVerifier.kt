package com.lianyi.paimonsnotebook.ui.screen.setting.util

import java.io.File
import java.security.MessageDigest

/*
* 更新包完整性校验
*
* 背景:原实现只检查"文件以 PK 开头且大于 1MB"。
* 这挡得住 404 错误页,但挡不住**被截断的下载**(网络中断时得到一个
* 开头合法、长度够大、内容不全的 zip),那种包会被当成下载成功并交给安装器。
*
* GitHub releases API 会给出资产摘要(形如 "sha256:95d132cb…"),
* 据此可做真正的完整性校验。
*
* ⚠️ 兼容性:digest 是 GitHub 较新加入的字段,**旧 release 可能没有**。
* 因此摘要缺失时**不能**判定失败,而是回退到原有的 PK + 体积检查 ——
* 否则老版本用户会永远更新不了。
* */
object UpdatePackageVerifier {

    /*
    * 解析 GitHub 的摘要字符串
    *
    * 输入形如 "sha256:95d132cb…"(大小写不定),返回规范化的十六进制小写;
    * 无法识别(空/非 sha256/长度不对)时返回 null,表示"无法校验"而非"校验失败"。
    * */
    fun normalizeDigest(digest: String?): String? {
        if (digest.isNullOrBlank()) return null

        val parts = digest.split(":", limit = 2)
        if (parts.size != 2) return null

        val algorithm = parts[0].trim()
        val value = parts[1].trim()

        //只认 sha256(sha512 等其它算法长度不同,不能按 sha256 比对)
        if (!algorithm.equals("sha256", ignoreCase = true)) return null

        //sha256 的十六进制表示固定 64 字符
        if (value.length != 64) return null
        if (!value.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) return null

        return value.lowercase()
    }

    /*
    * 比对摘要(纯函数,便于单测)
    * expected 已由 normalizeDigest 规范化
    * */
    fun matches(actualHex: String, expectedHex: String): Boolean =
        actualHex.equals(expectedHex, ignoreCase = true)

    /*
    * 计算文件 SHA-256
    * 返回十六进制小写;读取失败返回 null
    * */
    fun sha256(file: File): String? = runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }.getOrNull()

    /*
    * 校验下载结果
    *
    * 返回值语义:
    *   true  —— 通过(含"摘要缺失但 PK/体积检查通过"的回退情形)
    *   false —— 明确不通过
    * */
    fun verify(file: File, digest: String?, minPackageSize: Long): Boolean {
        //先做原有检查:体积与 ZIP 魔数
        if (!file.exists() || file.length() < minPackageSize) return false
        if (!hasZipHeader(file)) return false

        val expected = normalizeDigest(digest) ?: return true //无摘要 -> 回退

        val actual = sha256(file) ?: return false
        return matches(actual, expected)
    }

    //ZIP 以 "PK" 开头
    private fun hasZipHeader(file: File): Boolean = runCatching {
        file.inputStream().use { input ->
            val header = ByteArray(2)
            input.read(header) == 2 &&
                    header[0] == 'P'.code.toByte() &&
                    header[1] == 'K'.code.toByte()
        }
    }.getOrDefault(false)
}
