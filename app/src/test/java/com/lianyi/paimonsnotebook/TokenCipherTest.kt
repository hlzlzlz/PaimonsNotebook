package com.lianyi.paimonsnotebook

import com.lianyi.paimonsnotebook.common.database.util.crypto.TokenCipher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/*
* 凭证加解密的回归测试
*
* 用临时 AES 密钥(不触碰 AndroidKeyStore)完整验证 TokenCipher ——
* 这样单测覆盖的就是真机同一条代码路径,而不是"降级分支"
* (本工作区已有 HtmlSpanParser 的教训:两条分支会导致测试假绿)。
* */
class TokenCipherTest {

    private fun newKey(): SecretKey =
        KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    private val cookie =
        "cookie_token=abc123;ltoken=def456;stoken=ghi789;ltuid=100;"

    @Test
    fun 加密后可原样解密() {
        val key = newKey()

        val encrypted = TokenCipher.encrypt(cookie, key)!!

        assertEquals(cookie, TokenCipher.decrypt(encrypted, key))
    }

    @Test
    fun 密文带版本前缀() {
        val encrypted = TokenCipher.encrypt(cookie, newKey())!!

        assertTrue("必须带 v1: 前缀,否则无法与历史明文区分", encrypted.startsWith("v1:"))
        assertTrue(TokenCipher.isEncrypted(encrypted))
    }

    @Test
    fun 密文不含明文凭证() {
        val encrypted = TokenCipher.encrypt(cookie, newKey())!!

        assertFalse("密文里绝不能出现明文 token", encrypted.contains("abc123"))
        assertFalse(encrypted.contains("def456"))
        assertFalse(encrypted.contains("stoken"))
    }

    @Test
    fun 相同明文两次加密结果不同() {
        val key = newKey()

        val first = TokenCipher.encrypt(cookie, key)!!
        val second = TokenCipher.encrypt(cookie, key)!!

        //IV 每次随机:相同密文说明 IV 被复用,那会破坏 GCM 的安全性
        assertNotEquals("IV 必须每次重新生成", first, second)
        //但两者都能解回同一明文
        assertEquals(cookie, TokenCipher.decrypt(first, key))
        assertEquals(cookie, TokenCipher.decrypt(second, key))
    }

    @Test
    fun 用错误密钥解密返回null() {
        val encrypted = TokenCipher.encrypt(cookie, newKey())!!

        assertNull(
            "密钥不匹配必须返回 null 而不是抛异常",
            TokenCipher.decrypt(encrypted, newKey())
        )
    }

    @Test
    fun 密文被篡改后解密返回null() {
        val key = newKey()
        val encrypted = TokenCipher.encrypt(cookie, key)!!

        //翻转最后一个字符,破坏 GCM 认证标签
        val tampered = encrypted.dropLast(1) + if (encrypted.last() == 'A') 'B' else 'A'

        assertNull("GCM 应检出篡改", TokenCipher.decrypt(tampered, key))
    }

    @Test
    fun 无前缀的明文不被当作密文解密() {
        //历史明文行必须走明文分支,这里返回 null 表示"不是密文"
        assertNull(TokenCipher.decrypt(cookie, newKey()))
        assertFalse(TokenCipher.isEncrypted(cookie))
    }

    @Test
    fun 前缀但内容损坏时返回null() {
        assertNull(TokenCipher.decrypt("v1:这不是合法base64!!", newKey()))
        assertNull(TokenCipher.decrypt("v1:", newKey()))
    }

    @Test
    fun 空字符串可被加密解密() {
        val key = newKey()

        val encrypted = TokenCipher.encrypt("", key)!!

        assertEquals("", TokenCipher.decrypt(encrypted, key))
    }

    @Test
    fun 中文与特殊字符往返正确() {
        val key = newKey()
        val value = "备注=中文测试;符号=&<>\"';token=a+b/c=d"

        val encrypted = TokenCipher.encrypt(value, key)!!

        assertEquals("UTF-8 往返必须无损", value, TokenCipher.decrypt(encrypted, key))
    }

    @Test
    fun 超长内容往返正确() {
        val key = newKey()
        //模拟包含多个账号信息的超长 cookie
        val value = (1..2000).joinToString(";") { "key$it=value$it" }

        val encrypted = TokenCipher.encrypt(value, key)!!

        assertEquals(value, TokenCipher.decrypt(encrypted, key))
    }
}
