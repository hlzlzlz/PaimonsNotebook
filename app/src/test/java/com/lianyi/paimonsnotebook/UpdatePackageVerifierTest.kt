package com.lianyi.paimonsnotebook

import com.lianyi.paimonsnotebook.ui.screen.setting.util.UpdatePackageVerifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/*
* 更新包完整性校验的回归测试
*
* 重点覆盖两条容易写错的分支:
*   1. 摘要缺失时必须**回退**(放行)而不是判失败,否则老 release 永远更新不了
*   2. 摘要存在时必须真正比对,被截断的包要判失败
* */
class UpdatePackageVerifierTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val minSize = 16L

    //造一个内容可控的合法 zip(以 PK 开头)
    private fun zipFile(name: String, payload: String): File {
        val file = temp.newFile(name)
        ZipOutputStream(file.outputStream()).use { zos ->
            zos.putNextEntry(ZipEntry("entry.txt"))
            zos.write(payload.toByteArray())
            zos.closeEntry()
        }
        return file
    }

    // ---- normalizeDigest ----

    @Test
    fun 解析标准sha256摘要() {
        val hex = "95D132CB679A1C96FEB6C641B543B7ED7B2C90AD1EDBF5360CB6472785D7F372"
        assertEquals(hex.lowercase(), UpdatePackageVerifier.normalizeDigest("sha256:$hex"))
    }

    @Test
    fun 摘要为空或缺失返回null() {
        assertNull(UpdatePackageVerifier.normalizeDigest(null))
        assertNull(UpdatePackageVerifier.normalizeDigest(""))
        assertNull(UpdatePackageVerifier.normalizeDigest("   "))
    }

    @Test
    fun 非sha256算法返回null() {
        //sha512 的十六进制是 128 字符,不能按 sha256 比对
        val sha512 = "a".repeat(128)
        assertNull(UpdatePackageVerifier.normalizeDigest("sha512:$sha512"))
    }

    @Test
    fun 长度不对的摘要返回null() {
        assertNull(UpdatePackageVerifier.normalizeDigest("sha256:abc"))
        assertNull(UpdatePackageVerifier.normalizeDigest("sha256:" + "a".repeat(63)))
        assertNull(UpdatePackageVerifier.normalizeDigest("sha256:" + "a".repeat(65)))
    }

    @Test
    fun 含非十六进制字符的摘要返回null() {
        assertNull(UpdatePackageVerifier.normalizeDigest("sha256:" + "z".repeat(64)))
    }

    @Test
    fun 无冒号分隔的摘要返回null() {
        assertNull(UpdatePackageVerifier.normalizeDigest("a".repeat(64)))
    }

    // ---- verify:摘要缺失时回退 ----

    @Test
    fun 摘要缺失但包合法时放行() {
        val file = zipFile("ok.apk", "hello world content")

        assertTrue(
            "老 release 没有 digest 字段,必须回退放行,否则用户永远更新不了",
            UpdatePackageVerifier.verify(file, digest = null, minPackageSize = minSize)
        )
    }

    @Test
    fun 摘要格式无法识别时同样回退放行() {
        val file = zipFile("ok2.apk", "hello world content")

        assertTrue(UpdatePackageVerifier.verify(file, digest = "md5:deadbeef", minPackageSize = minSize))
    }

    // ---- verify:摘要存在时严格比对 ----

    @Test
    fun 摘要匹配时通过() {
        val file = zipFile("match.apk", "hello world content")
        val actual = UpdatePackageVerifier.sha256(file)!!

        assertTrue(
            UpdatePackageVerifier.verify(file, digest = "sha256:$actual", minPackageSize = minSize)
        )
    }

    @Test
    fun 摘要不匹配时失败() {
        val file = zipFile("mismatch.apk", "hello world content")
        val wrong = "0".repeat(64)

        assertFalse(
            "内容与摘要不符必须判失败(例如被篡改或下到了别的版本)",
            UpdatePackageVerifier.verify(file, digest = "sha256:$wrong", minPackageSize = minSize)
        )
    }

    @Test
    fun 被截断的包即使有PK头也会因摘要不符失败() {
        //这是原实现真正的漏洞:截断后仍是合法 ZIP 前缀、体积也可能够大
        val full = zipFile("full.apk", "A".repeat(4096))
        val expected = UpdatePackageVerifier.sha256(full)!!

        val truncated = temp.newFile("truncated.apk")
        truncated.writeBytes(full.readBytes().copyOfRange(0, (full.length() / 2).toInt()))

        assertFalse(
            "截断包必须被摘要校验拦下",
            UpdatePackageVerifier.verify(truncated, digest = "sha256:$expected", minPackageSize = minSize)
        )
    }

    // ---- verify:体积与魔数 ----

    @Test
    fun 体积过小的包被拒绝() {
        val file = temp.newFile("small.apk")
        file.writeBytes("PK".toByteArray())

        assertFalse(UpdatePackageVerifier.verify(file, digest = null, minPackageSize = 1024L))
    }

    @Test
    fun 非ZIP内容的包被拒绝() {
        //模拟 404 错误页:长度够但没有 PK 头
        val file = temp.newFile("html.apk")
        file.writeBytes("<html>404 not found</html>".toByteArray())

        assertFalse(UpdatePackageVerifier.verify(file, digest = null, minPackageSize = minSize))
    }

    @Test
    fun 不存在的文件被拒绝() {
        val missing = File(temp.root, "nope.apk")

        assertFalse(UpdatePackageVerifier.verify(missing, digest = null, minPackageSize = minSize))
    }

    // ---- sha256 ----

    @Test
    fun sha256计算已知内容() {
        val file = temp.newFile("known.txt")
        //"abc" 的 SHA-256 是公开的固定值
        file.writeBytes("abc".toByteArray())

        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            UpdatePackageVerifier.sha256(file)
        )
    }

    @Test
    fun sha256对不存在文件返回null() {
        assertNull(UpdatePackageVerifier.sha256(File(temp.root, "missing.txt")))
    }

    @Test
    fun 摘要比对忽略大小写() {
        assertTrue(UpdatePackageVerifier.matches("ABCD", "abcd"))
        assertTrue(UpdatePackageVerifier.matches("abcd", "ABCD"))
        assertFalse(UpdatePackageVerifier.matches("abcd", "abce"))
    }
}
