package com.lianyi.paimonsnotebook.common.web.hutao.genshin.common.util

import com.lianyi.paimonsnotebook.common.extension.string.notify
import com.lianyi.paimonsnotebook.common.extension.string.warnNotify
import com.lianyi.paimonsnotebook.common.util.file.FileHelper
import com.lianyi.paimonsnotebook.common.util.hash.XXHash
import com.lianyi.paimonsnotebook.common.util.json.JSON
import com.lianyi.paimonsnotebook.common.util.notification.PaimonsNotebookNotification
import com.lianyi.paimonsnotebook.common.util.parameter.getParameterizedType
import com.lianyi.paimonsnotebook.common.util.request.applicationOkHttpClient
import com.lianyi.paimonsnotebook.common.util.request.buildRequest
import com.lianyi.paimonsnotebook.common.util.request.getAsText
import com.lianyi.paimonsnotebook.common.web.HutaoEndpoints
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.intrinsic.LocaleNames
import com.lianyi.paimonsnotebook.common.extension.scope.launchSafeIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/*
* 元数据名称
* */
object MetadataHelper {
    //启动时检查的元数据列表
    private val metadataCheckList by lazy {
        listOf(
//            FileNameAvatar, //角色拆分为单独的文件
            FileNameAvatarCurve,
            FileNameAvatarPromote,
            FileNameWeapon,
            FileNameWeaponCurve,
            FileNameWeaponPromote,
            FileNameMaterial,
            FileNameMonster,
            FileNameTowerSchedule,
            FileNameAchievement,
            FileNameAchievementGoal,
            FileNameReliquary,
            FileNameReliquarySet,
            FileNameGachaEvent
        )
    }

    //图包检查列表
    private val zipCheckList by lazy {
        listOf(
            ZipFileNameAvatarIcon,
            ZipFileNameEquipIcon,
            ZipFileNameMonsterIcon,
            ZipFileNameGachaAvatarImg,
            ZipFileNameSkill,
            ZipFileNameTalent,
            ZipFileNameLoadingPic
        )
    }

    private val hashMap = mutableMapOf<String?, String?>()


    private var latestCheckHashMapTime = 0L

    //哈希表的检查时间
    private const val HashMapCheckInterval = 60000L

    //协程最大并发数量
    private val semaphore by lazy {
        Semaphore(40)
    }

    /*
    * 返回map中哈希值不同的文件名集合
    * */
    @OptIn(ExperimentalStdlibApi::class)
    private fun getDownloadFileNameListFromMetadataMap(): List<String> {
        val updateFileList = mutableListOf<String>()

        val xxHash = XXHash()

        hashMap.forEach { (fileName, hashValue) ->

            val file = FileHelper.getMetadata(fileName ?: "")
            //Snap.Metadata镜像仓库的Meta.json使用LF行尾计算哈希,不再做CRLF替换
            val arr = file?.readText()?.toByteArray() ?: ByteArray(0)

            //此处使用.toString(16)会导致第一位如果为15会变F,导致无法与Map的0F对应
            //toHexString()同样不会补前导零,哈希首位为0时输出不足16位,需padStart对齐
            val xxh64 = xxHash.hash64(arr).toULong().toHexString().padStart(16, '0').uppercase()

            if (hashValue != xxh64 && !hashValue.isNullOrEmpty() && !fileName.isNullOrEmpty()) {
                updateFileList += fileName
            }
        }

        return updateFileList
    }

    private suspend fun metadataNeedUpdate(): Boolean {
        //本次会话已校验过且未发生下载时,直接复用上次结果。
        //全量校验需要逐个读取并哈希约19MB的元数据文件(154个),
        //而checkAndUpdateMetadata会被首页初始化与设置页手动同步分别触发,
        //短时间内重复校验没有意义。
        if (lastVerifyTime > 0 &&
            System.currentTimeMillis() - lastVerifyTime < MetadataVerifyInterval
        ) {
            return false
        }

        updateMetadataHashMap()

        val needUpdate = getDownloadFileNameListFromMetadataMap().isNotEmpty()

        if (!needUpdate) {
            lastVerifyTime = System.currentTimeMillis()
        }

        return needUpdate
    }

    //上次完成全量校验(且结果是不需要更新)的时间戳
    private var lastVerifyTime = 0L

    //全量校验结果的有效期
    private const val MetadataVerifyInterval = 5 * 60 * 1000L

    private var isUpdating = false

    fun checkAndUpdateMetadata(
        notify: Boolean = false,
        onSuccess: suspend () -> Unit = {}
    ) {
        //防止重复更新
        if (isUpdating) {
            if (notify) {
                "元数据正在进行更新".warnNotify(false)
            }
            return
        }
        isUpdating = true

        //用launchSafeIO:本方法可能由启动路径触发,裸launch抛异常会杀进程
        launchSafeIO {
            //哈希清单拉取/解析失败不能作为未捕获异常杀掉进程(协程里抛出即进程退出),退回旧数据
            val needUpdate = try {
                metadataNeedUpdate()
            } catch (e: Exception) {
                e.printStackTrace()
                if (notify) {
                    "检查元数据更新时发生错误,现在使用的仍是旧数据".warnNotify()
                }
                isUpdating = false
                return@launchSafeIO
            }

            if (!needUpdate) {
                if (notify) {
                    "当前元数据已是最新".notify()
                }

                isUpdating = false
                onSuccess.invoke()
                return@launchSafeIO
            }

            val notifyId = "发现新的元数据,正在更新...".notify(keepShow = true)

            updateMetadata(
                onFailed = {
                    "更新元数据时发生错误,现在使用的仍是旧数据,显示的内容可能会与最新的游戏内容有所差异".warnNotify()
                },
                onSuccess = {
                    "元数据更新完毕".notify()
                    onSuccess.invoke()
                },
                onLoadMetadataFile = {},
                onFinally = {
                    PaimonsNotebookNotification.removeNotifyById(notifyId)
                    isUpdating = false
                }
            )
        }
    }

    /*
    * 更新元数据
    * 成功与否取决于每个文件的实际下载结果,而非下载后的哈希二次比对
    * (哈希比对只用于挑选需要更新的文件,上游哈希表可能存在自引用失效或记录滞后的脏数据)
    * */
    suspend fun updateMetadata(
        updateMap: Boolean = false,
        onFailed: suspend () -> Unit,
        onSuccess: suspend () -> Unit,
        onLoadMetadataFile: (Int) -> Unit,
        onFinally: suspend () -> Unit
    ) {
        try {
            if (updateMap) {
                updateMetadataHashMap()
            }

            val allSuccess = withContext(Dispatchers.IO) {
                val downloadFileList = getDownloadFileNameListFromMetadataMap()

                val results = coroutineScope {
                    downloadFileList.map { name ->
                        async {
                            semaphore.withPermit {
                                loadAndSaveFile(name).also {
                                    onLoadMetadataFile.invoke(downloadFileList.size)
                                }
                            }
                        }
                    }.awaitAll()
                }

                results.all { it }
            }

            if (allSuccess) {
                //下载成功说明本地文件已变化,作废校验缓存,下次调用重新比对
                lastVerifyTime = 0L
                onSuccess.invoke()
            } else {
                onFailed.invoke()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            //onFailed自身抛异常不能阻断onFinally,否则isUpdating会永久卡在true
            try {
                onFailed.invoke()
            } catch (e2: Exception) {
                e2.printStackTrace()
            }
        } finally {
            //onFinally必须无条件执行:它负责复位isUpdating,
            //一旦漏执行,元数据将再也无法更新(且没有任何提示)
            try {
                onFinally.invoke()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    //更新元数据map
    private suspend fun updateMetadataHashMap() {
        val skipUpdateHashMap =
            System.currentTimeMillis() - latestCheckHashMapTime < HashMapCheckInterval

        if (skipUpdateHashMap) {
            return
        }

        val metaJson = buildRequest {
            url(HutaoEndpoints.metadata(LocaleNames.CHS, "$MetaFileName.json"))
        }.getAsText(applicationOkHttpClient)

        //先解析后提交,解析失败时保留原有哈希表
        val metaMap = JSON.parse<Map<String, String>>(
            metaJson,
            getParameterizedType(Map::class.java, String::class.java, String::class.java)
        )

        hashMap.clear()
        hashMap.putAll(metaMap)

        //Meta.json的自引用哈希条目永远无法与自己匹配(修改该文件必然使其失效),剔除以避免误判更新失败
        hashMap.remove(MetaFileName)

        //只保留本程序实际使用的元数据:检查列表内的文件与散装角色文件
        //镜像仓库中其余文件(如BeyondItem等)未被使用,且可能存在与实际文件不同步的哈希记录
        val usedKeys = hashMap.keys.filter { key ->
            key != null && (key in metadataCheckList || key.startsWith("$DirNameAvatar/"))
        }
        hashMap.keys.retainAll(usedKeys.toSet())

        latestCheckHashMapTime = System.currentTimeMillis()
    }

    //重新载入单个文件
    //注意:getAsText在请求失败时会返回伪造的retcode错误JSON且不抛异常,
    //若不校验会把错误占位符当作元数据写入本地文件,导致功能损坏与校验永久失败
    private suspend fun loadAndSaveFile(name: String): Boolean {
        HutaoEndpoints.metadataSources(LocaleNames.CHS, "${name}.json").forEach { url ->
            val success = withContext(Dispatchers.IO) {
                try {
                    applicationOkHttpClient.newCall(buildRequest { url(url) }).execute().use { response ->
                        val text = response.body?.string()
                        if (text.isNullOrBlank() || !response.isSuccessful) {
                            return@use false
                        }
                        val trimmed = text.trimStart()
                        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
                            return@use false
                        }
                        FileHelper.getMetadataSaveFile(name).writeText(text)
                        true
                    }
                } catch (e: Exception) {
                    false
                }
            }
            if (success) {
                return true
            }
        }
        return false
    }

    private const val MetaFileName = "Meta"

    const val DirNameAvatar = "Avatar"

    const val FileNameAchievement = "Achievement"
    const val FileNameAchievementGoal = "AchievementGoal"
    const val FileNameAvatar = "Avatar"
    const val FileNameAvatarCurve = "AvatarCurve"
    const val FileNameAvatarPromote = "AvatarPromote"
    const val FileNameDisplayItem = "DisplayItem"
    const val FileNameGachaEvent = "GachaEvent"
    const val FileNameMaterial = "Material"
    const val FileNameMonster = "Monster"
    const val FileNameMonsterCurve = "MonsterCurve"
    const val FileNameReliquary = "Reliquary"
    const val FileNameReliquaryAffixWeight = "ReliquaryAffixWeight"
    const val FileNameReliquaryMainAffix = "ReliquaryMainAffix"
    const val FileNameReliquaryMainAffixLevel = "ReliquaryMainAffixLevel"
    const val FileNameReliquarySet = "ReliquarySet"
    const val FileNameReliquarySubAffix = "ReliquarySubAffix"
    const val FileNameTowerFloor = "TowerFloor"
    const val FileNameTowerLevel = "TowerLevel"
    const val FileNameTowerSchedule = "TowerSchedule"
    const val FileNameWeapon = "Weapon"
    const val FileNameWeaponCurve = "WeaponCurve"
    const val FileNameWeaponPromote = "WeaponPromote"

    val ZipFileNameAchievementIcon = "AchievementIcon"
    val ZipFileNameAvatarCard = "AvatarCard"
    val ZipFileNameAvatarIcon = "AvatarIcon"
    val ZipFileNameBg = "Bg"
    val ZipFileNameChapterIcon = "ChapterIcon"
    val ZipFileNameCostume = "Costume"
    val ZipFileNameEmotionIcon = "EmotionIcon"
    val ZipFileNameEquipIcon = "EquipIcon"
    val ZipFileNameGachaAvatarIcon = "GachaAvatarIcon"
    val ZipFileNameGachaAvatarImg = "GachaAvatarImg"
    val ZipFileNameGachaEquipIcon = "GachaEquipIcon"
    val ZipFileNameIconElement = "IconElement"
    val ZipFileNameItemIcon = "ItemIcon"
    val ZipFileNameLoadingPic = "LoadingPic"
    val ZipFileNameMonsterIcon = "MonsterIcon"
    val ZipFileNameMonsterSmallIcon = "MonsterSmallIcon"
    val ZipFileNameNameCardIcon = "NameCardIcon"
    val ZipFileNameNameCardPic = "NameCardPic"
    val ZipFileNameProperty = "Property"
    val ZipFileNameRelicIcon = "RelicIcon"
    val ZipFileNameSkill = "Skill"
    val ZipFileNameTalent = "Talent"

}