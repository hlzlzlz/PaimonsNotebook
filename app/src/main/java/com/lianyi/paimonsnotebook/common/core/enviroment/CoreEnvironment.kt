package com.lianyi.paimonsnotebook.common.core.enviroment

import android.os.Build
import com.lianyi.paimonsnotebook.common.application.PaimonsNotebookApplication
import com.lianyi.paimonsnotebook.common.extension.data_store.editValue
import com.lianyi.paimonsnotebook.common.util.data_store.PreferenceKeys
import com.lianyi.paimonsnotebook.common.util.data_store.dataStoreValuesFirst
import com.lianyi.paimonsnotebook.common.util.json.JSON
import com.lianyi.paimonsnotebook.common.web.hoyolab.passport.AppSignInfoData
import com.lianyi.paimonsnotebook.common.web.hoyolab.public_data_api.PublicDataApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.UUID

/*
* 核心环境参数
* */
object CoreEnvironment {
    private val publicDataApiClient by lazy {
        PublicDataApiClient()
    }

    var skipSplashScreen = false

    fun init() {
        CoroutineScope(Dispatchers.IO).launch {
//            launch {
//                setAppSignInfo()
//            }
            launch {
                dataStoreValuesFirst {
//                    return@dataStoreValuesFirst

                    DeviceId = it[PreferenceKeys.DeviceId] ?: ""
                    BBSDeviceId = it[PreferenceKeys.BBSDeviceId] ?: ""
                    DeviceId40 = it[PreferenceKeys.DeviceId40] ?: ""

                    DeviceIdSeed = it[PreferenceKeys.DeviceIdSeed] ?: ""
                    DeviceIdSeedTime = it[PreferenceKeys.DeviceIdSeedTime] ?: -1L

                    skipSplashScreen = !(it[PreferenceKeys.EnableMetadata]
                        ?: true) || (it[PreferenceKeys.InitialMetadataDownload] ?: false)

                    if (DeviceId.isBlank()) {
                        generateDeviceId()
                    }

                    if (BBSDeviceId.isBlank()) {
                        generateBBSDeviceId()
                    }

                    //生成40位的deviceId，通过当前的deviceId
                    if (DeviceId40.isBlank()) {
                        generateDeviceId40()
                    }

                    if (DeviceIdSeed.isBlank()) {
                        DeviceIdSeed = UUID.randomUUID().toString()
                        PreferenceKeys.DeviceIdSeed.editValue(DeviceIdSeed)
                        PreferenceKeys.DeviceIdSeedTime.editValue(System.currentTimeMillis())
                    }

                    DeviceFp = it[PreferenceKeys.DeviceFp] ?: ""
                    FpDeviceId = it[PreferenceKeys.FpDeviceId] ?: ""

                    if (FpDeviceId.isBlank()) {
                        FpDeviceId = getRandomHex(16)
                        PreferenceKeys.FpDeviceId.editValue(FpDeviceId)
                    }

                    //胡桃同款静默续期:已有合法指纹且签发未超过7天时不再请求getFp,
                    //避免每次冷启动都注册设备带来的风控画像与指纹漂移风险
                    val fpUpdateTime = it[PreferenceKeys.DeviceFpUpdateTime] ?: 0L
                    if (!fpValid(DeviceFp) ||
                        System.currentTimeMillis() - fpUpdateTime >= 7 * 24 * 60 * 60 * 1000L
                    ) {
                        setFp(DeviceFp)
                    }
                }
            }
        }
    }

    //authorize_key 此处使用的是云·星铁的
    const val AuthorizeKeyStarRailCould = "e45a5ea9b62b87aa"

    //星铁的authorize_key
    const val AuthorizeKeyStarRail = "c90mr1bwo2rk"

    //app_key 此处使用的是云·星铁 2.1的app_sign
    var AuthorizeAppSign: String = "9ddde935852443ac9ecc5e794f9917f0"
        private set

    //签名版本(程序版本)
    var AuthorizeAppSignVersion = "2.1.0"
        private set

    const val SDKVersion = "2.22.1"

    //原神游戏id
    const val GameBizGenshin = "hk4e_cn"

    // 米游社 Rpc 版本
    //与胡桃工具箱保持一致,过旧的客户端版本会被风控判定为高风险
    const val XrpcVersion = "2.95.1"

    const val ClientType = EnvironmentClientType.BBS

    // 米游社移动端请求UA
    val HoyolabMobileUA =
        "Mozilla/5.0 (Linux; Android ${Build.VERSION.RELEASE}; ${Build.MODEL} Build/${Build.USER}; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/95.0.4638.74 Mobile Safari/537.36 miHoYoBBS/$XrpcVersion"

    //米游社移动端网页UA
    const val HoyolabMobileWebUA =
        "Mozilla/5.0 (Linux; Android 12) Mobile miHoYoBBS/$XrpcVersion"

    //TODO 添加版本限制(maybe)
    const val PaimonsNotebookUA = "PaimonsNotebook/${PaimonsNotebookApplication.version}"

    //原神4 星铁8 ZZZ 12
    const val APP_ID = "8"

    var DeviceFp = ""
        private set

    //getFp接口专用device_id,16位十六进制,与指纹绑定保持稳定
    var FpDeviceId = ""
        private set

    var DeviceId = ""
        private set

    var DeviceId40 = ""
        private set

    var BBSDeviceId = ""
        private set

    var DeviceIdSeed = ""
        private set

    var DeviceIdSeedTime = 0L
        private set

    //生成米游社设备id
    private suspend fun generateDeviceId() {
        PreferenceKeys.DeviceId.editValue(UUID.randomUUID().toString())
    }

    //扫码登录设备Id
    private suspend fun generateDeviceId40() {
        val namespaceUuid = UUID.fromString("9450ea74-be9c-35c0-9568-f97407856768")
        val uuid = UUID.nameUUIDFromBytes("$DeviceId:$namespaceUuid".toByteArray(Charsets.UTF_8))

        val uuidBytes = ByteBuffer.wrap(ByteArray(16))
            .putLong(uuid.mostSignificantBits)
            .putLong(uuid.leastSignificantBits)
            .array()

        val sha1Digest = MessageDigest.getInstance("SHA-1")
        val hashBytes = sha1Digest.digest(uuidBytes)

        PreferenceKeys.DeviceId40.editValue(hashBytes.joinToString("") { "%02x".format(it) })
    }

    private suspend fun generateBBSDeviceId() {
        val id = getRandomChars(16)
        PreferenceKeys.BBSDeviceId.editValue(id)
    }

    //设置App签名
    private suspend fun setAppSignInfo() {
        dataStoreValuesFirst {
            val value = it[PreferenceKeys.AuthorizeAppSign] ?: JSON.EMPTY_OBJ

            if (value == JSON.EMPTY_OBJ) {
                return@dataStoreValuesFirst
            }

            val info = JSON.parse<AppSignInfoData>(value)

            info.apply {
                appSign?.apply {
                    AuthorizeAppSign = this
                }
                appVersion?.apply {
                    AuthorizeAppSignVersion = this
                }
            }

            //当本地的值为不空表示需要使用本地的app_sign
            if (value.isNotEmpty()) {
                AuthorizeAppSign = value
            }
        }
    }

    private fun fpValid(fp: String) = fp.matches(Regex("^[0-9a-f]{13}$"))

    /*
    * 刷新设备指纹
    * getFp服务端校验收紧后:device_fp必须为13位十六进制,device_id必须为16位十六进制,
    * 否则返回-502或空指纹,而游戏记录接口会以5003拒绝缺少合法指纹的请求
    * 因此:只接受服务端签发的13位十六进制指纹;失败时保留现有合法指纹,不再写入无效值
    * 续期时把旧指纹作为候选重新提交,服务端会返回同一个fp(与胡桃一致)
    * */
    private suspend fun setFp(fp: String) {
        publicDataApiClient.getExtList()

        //旧版本持久化的指纹可能是10位数字或空串,视为无指纹强制刷新
        val existingValid = fp.takeIf { fpValid(it) }
        val candidate = existingValid ?: getRandomHex(13)

        val result = publicDataApiClient.getFp(candidate)

        val newFp = result.data?.device_fp.orEmpty()
        if (fpValid(newFp)) {
            DeviceFp = newFp
            PreferenceKeys.DeviceFp.editValue(newFp)
            PreferenceKeys.DeviceFpUpdateTime.editValue(System.currentTimeMillis())
        } else {
            //刷新失败:有合法旧指纹就继续用(下次启动会再试),没有则用格式正确的候选值
            DeviceFp = existingValid ?: candidate
        }
    }

    private fun getRandomChars(times: Int) = with(StringBuilder()) {
        val range = "abcdefghijklmnopqrstuvwxyz1234567890"
        repeat(times) {
            this.append(range.random())
        }
        this.toString()
    }

    private fun getRandomHex(times: Int) = with(StringBuilder()) {
        val range = "0123456789abcdef"
        repeat(times) {
            this.append(range.random())
        }
        this.toString()
    }

}