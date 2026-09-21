package com.lianyi.paimonsnotebook.common.database.util.crypto

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/*
* 凭证字段的加解密
*
* 背景:users 表里以 Cookie 形式**明文**保存着 cookie_token / ltoken / stoken,
* 设备被 root 或数据库被拷走即可直接使用。
*
* 设计要点:
*
* 1. **带版本前缀**。密文写成 "v1:" + base64(iv || ciphertext)。
*    读取时按前缀判断:有前缀才解密,没有前缀的当**历史明文**直接解析。
*    这样老用户升级后无需任何迁移即可继续使用,新写入的则自动加密。
*
* 2. **纯函数**。加解密只依赖传入的 SecretKey,不触碰 AndroidKeyStore,
*    因此可在纯 JVM 单测里用一个临时 AES 密钥完整验证
*    (本工作区已有教训:逻辑与平台耦合会导致单测与真机走不同分支)。
*    AndroidKeyStore 只在 TokenKeyProvider 里使用。
*
* 3. **解密失败不抛异常**。密钥丢失(如清除应用数据、换机恢复备份)时
*    密文无法还原,此时应让用户重新登录,而**不是**让整个数据库读取崩溃。
*
* ⚠️ AES-GCM 的 IV 每次加密都必须重新随机生成,绝不能复用 ——
*    GCM 下 IV 复用会同时破坏机密性与完整性。
* */
object TokenCipher {

    const val PREFIX = "v1:"

    //GCM 推荐 12 字节 IV
    private const val IV_LENGTH = 12

    //认证标签 128 位
    private const val TAG_LENGTH_BITS = 128

    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    private val secureRandom = SecureRandom()

    fun isEncrypted(value: String): Boolean = value.startsWith(PREFIX)

    /*
    * 加密
    * 返回 "v1:" + base64(iv || ciphertext)
    * 失败返回 null(由调用方决定回退策略)
    * */
    fun encrypt(plainText: String, key: SecretKey): String? = runCatching {
        val iv = ByteArray(IV_LENGTH).also { secureRandom.nextBytes(it) }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, iv))

        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        //IV 与密文拼在一起存储:解密时需要它,而它本身不是秘密
        val combined = iv + cipherText
        PREFIX + Base64.getEncoder().encodeToString(combined)
    }.getOrNull()

    /*
    * 解密
    *
    * 输入必须是带前缀的密文;不带前缀的(历史明文)返回 null,
    * 由调用方按明文处理 —— 这里不做兼容猜测,避免把明文误当密文解。
    *
    * 失败(密钥不对/密文被篡改/格式损坏)返回 null。
    * */
    fun decrypt(payload: String, key: SecretKey): String? = runCatching {
        if (!isEncrypted(payload)) return null

        val combined = Base64.getDecoder().decode(payload.removePrefix(PREFIX))

        //至少要有 IV + 认证标签
        if (combined.size <= IV_LENGTH) return null

        val iv = combined.copyOfRange(0, IV_LENGTH)
        val cipherText = combined.copyOfRange(IV_LENGTH, combined.size)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, iv))

        String(cipher.doFinal(cipherText), Charsets.UTF_8)
    }.getOrNull()
}
