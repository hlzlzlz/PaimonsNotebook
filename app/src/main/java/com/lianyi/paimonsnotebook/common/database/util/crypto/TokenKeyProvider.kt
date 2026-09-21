package com.lianyi.paimonsnotebook.common.database.util.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/*
* 凭证加密密钥的提供者(AndroidKeyStore)
*
* 密钥由 AndroidKeyStore 生成并保管,私钥材料**不会**进入应用进程内存,
* 也无法从应用沙箱里直接读出(即使数据库文件被拷走,没有该密钥也解不开)。
*
* ⚠️ 该密钥随应用卸载/清除数据一同销毁,且**不参与备份**:
*    这是有意的 —— 备份里带着密钥就等于没加密。
*    代价是"备份到新机后旧密文无法还原",此时会退回要求用户重新登录
*    (见 TokenCipher.decrypt 返回 null 的处理)。
* */
object TokenKeyProvider {

    private const val KEYSTORE = "AndroidKeyStore"
    private const val ALIAS = "paimons_notebook_token_key"

    @Volatile
    private var cached: SecretKey? = null

    /*
    * 取(必要时创建)密钥
    * 任何异常都返回 null,由调用方回退到明文读写 ——
    * 加密失败不该让应用无法使用。
    * */
    fun getOrCreateKey(): SecretKey? {
        cached?.let { return it }

        return runCatching {
            val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }

            (keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey?.let {
                cached = it
                return it
            }

            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
            generator.init(
                KeyGenParameterSpec.Builder(
                    ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    //不要求用户认证:签到/桌面组件等后台任务也要能读凭证
                    .setUserAuthenticationRequired(false)
                    .build()
            )

            generator.generateKey().also { cached = it }
        }.getOrNull()
    }
}
