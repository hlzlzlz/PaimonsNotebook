package com.lianyi.paimonsnotebook.common.database.user.type_converter

import androidx.room.TypeConverter
import com.lianyi.paimonsnotebook.common.database.util.ITypeConverter
import com.lianyi.paimonsnotebook.common.database.util.crypto.TokenCipher
import com.lianyi.paimonsnotebook.common.database.util.crypto.TokenKeyProvider
import com.lianyi.paimonsnotebook.common.web.hoyolab.cookie.Cookie

/*
* Cookie(含 cookie_token/ltoken/stoken)的持久化转换器
*
* 写入时加密为 "v1:base64(iv||ciphertext)",读取时解密。
* 历史明文行(无前缀)按明文解析,因此**无需 schema 迁移** ——
* 列仍是 TEXT,只是内容格式变了(已写成用例钉住两种形态)。
*
* 三类失败都有明确回退,任何一类都不允许让数据库读写崩溃:
*   1. 取不到密钥        -> 退化为明文写入(应用仍可用)
*   2. 加密失败          -> 同上
*   3. 解密失败/密钥丢失 -> 返回空 Cookie,表现为"需要重新登录"
*      (例如用户换了手机并从备份恢复数据库;密钥不参与备份)
* */
class CookieConverter : ITypeConverter<Cookie, String> {

    @TypeConverter
    override fun convertToEntityProperty(databaseValue: String): Cookie {
        val plain = if (TokenCipher.isEncrypted(databaseValue)) {
            /*
            * 密钥丢失时 decrypt 返回 null。
            * 这里**不能**把密文原样丢给 parse —— 那会解析出一堆
            * 形如 "v1:xxx" 的垃圾键值对,后续带着它请求接口会得到
            * 难以排查的 401/风控错误。返回空 Cookie 更诚实。
            * */
            TokenCipher.decrypt(databaseValue, TokenKeyProvider.getOrCreateKey() ?: return Cookie())
                ?: return Cookie()
        } else {
            //历史明文行:直接使用
            databaseValue
        }

        return Cookie().apply { parse(plain) }
    }

    @TypeConverter
    override fun convertToDatabaseValue(entity: Cookie): String {
        val plain = entity.toString()

        val key = TokenKeyProvider.getOrCreateKey() ?: return plain
        return TokenCipher.encrypt(plain, key) ?: plain
    }
}
