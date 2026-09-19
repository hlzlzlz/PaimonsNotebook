package com.lianyi.paimonsnotebook.ui.screen.gacha.service

import com.google.gson.stream.JsonWriter
import com.lianyi.paimonsnotebook.common.application.PaimonsNotebookApplication
import com.lianyi.paimonsnotebook.common.database.PaimonsNotebookDatabase
import com.lianyi.paimonsnotebook.common.database.gacha.entity.GachaItems
import com.lianyi.paimonsnotebook.common.extension.string.warnNotify
import com.lianyi.paimonsnotebook.common.util.data_store.DataStoreHelper
import com.lianyi.paimonsnotebook.common.util.data_store.PreferenceKeys
import com.lianyi.paimonsnotebook.common.util.metadata.genshin.uigf.UIGFExportVersion
import com.lianyi.paimonsnotebook.common.util.metadata.genshin.uigf.UIGFHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter

/*
* 祈愿导出
* currentGameUid:目标uid
* saveFile:保存的文件
* database:数据库
* */
class GachaItemsExportService(
    val database: PaimonsNotebookDatabase = PaimonsNotebookDatabase.database,
) {

    /*
    * 祈愿记录一页条数
    * */
    private val queryPageSize = 2000

    private val dao by lazy {
        database.gachaItemsDao
    }

    /*
    * 导出祈愿记录
    *
    * uidList:要导出的 uid
    * saveFile:目标文件
    * version:UIGF 版本(v3.0 或 v4.0/v4.1/v4.2)
    *
    * 说明:v3.0 与 v4.x 是两套不兼容的结构,故按 version 分流;
    * v4.0/v4.1/v4.2 共用同一段写出逻辑,只有 info.version 不同,
    * v4.2 额外补一个空的 hk4e_ugc 数组(详见 UIGFHelper.UIGFVersion 注释)。
    * */
    suspend fun exportGachaRecordToUIGFJson(
        uidList: List<String>,
        saveFile: File,
        version: UIGFExportVersion
    ) {
        if (uidList.isEmpty()) {
            "导出失败:请先选中一个祈愿记录的id".warnNotify()
            return
        }

        if (version.isLegacyV3) {
            //v3 只能导出一个 uid,v4 可多 uid
            exportUIGFJsonV3File(
                saveFile = saveFile,
                uidList = uidList,
                uid = uidList.first()
            )
        } else {
            exportUIGFJsonV4File(
                saveFile = saveFile,
                uidList = uidList,
                uigfVersion = version.uigfVersion ?: UIGFHelper.UIGFVersion.default
            )
        }
    }

    //v3
    private suspend fun exportUIGFJsonV3File(
        saveFile: File,
        uidList: List<String>,
        uid: String
    ) {
        withContext(Dispatchers.IO) {
            val writer = JsonWriter(FileWriter(saveFile)).apply {
                setIndent("  ")
            }

            //如果为空,就设置为zh-cn(与v4导出的处理保持一致,空列表直接first()会抛)
            val lang = getLangByUid(uid)

            val region =
                DataStoreHelper.getLocalDataMap<String, Long>(PreferenceKeys.GachaRecordGameUidRegionMap)[uid]
                    ?: UIGFHelper.getRegionTimeZoneByUid(uid)

            writer.apply {
                beginObject()
                name("info")
                beginObject()
                name(UIGFHelper.Field.Info.Uid).value(uid)
                name(UIGFHelper.Field.Info.Lang).value(lang)
                name(UIGFHelper.Field.Info.ExportTimestamp).value(System.currentTimeMillis())
                name(UIGFHelper.Field.Info.ExportApp).value(PaimonsNotebookApplication.name)
                name(UIGFHelper.Field.Info.ExportAppVersion).value(PaimonsNotebookApplication.version)
                name(UIGFHelper.Field.Info.UIGFVersion).value(UIGFExportVersion.V3_0.uigfVersion!!.value)
                name(UIGFHelper.Field.Info.RegionTimeZone).value(region)
                endObject()

                name("list")
                saveGachaItems(writer = writer, uid = uid)

                endObject()

                flush()
                close()
            }
        }
    }

    //v4(v4.0 / v4.1 / v4.2 共用)
    private suspend fun exportUIGFJsonV4File(
        saveFile: File,
        uidList: List<String>,
        uigfVersion: UIGFHelper.UIGFVersion,
    ) {
        withContext(Dispatchers.IO) {
            val writer = JsonWriter(FileWriter(saveFile)).apply {
                setIndent("  ")
            }

            writer.apply {
                beginObject()
                name("info")
                beginObject()
                name(UIGFHelper.Field.Info.ExportTimestamp).value(System.currentTimeMillis() / 1000)
                name(UIGFHelper.Field.Info.ExportApp).value(PaimonsNotebookApplication.name)
                name(UIGFHelper.Field.Info.ExportAppVersion).value(PaimonsNotebookApplication.version)
                name(UIGFHelper.Field.Info.Version).value(uigfVersion.value)
                endObject()

                name(UIGFHelper.GameField.Hk4e)
                beginArray()

                val timeZoneIdCacheMap =
                    DataStoreHelper.getLocalDataMap<String, Long>(PreferenceKeys.GachaRecordGameUidRegionMap)

                uidList.forEach { uid ->
                    val timeZone = timeZoneIdCacheMap[uid] ?: UIGFHelper.getRegionTimeZoneByUid(uid)

                    val lang = getLangByUid(uid)

                    beginObject()
                    name(UIGFHelper.Field.Info.Uid).value(uid)
                    name(UIGFHelper.Field.Info.TimeZone).value(timeZone)
                    name(UIGFHelper.Field.Info.Lang).value(lang)

                    name("list")
                    saveGachaItems(writer = writer, uid = uid, exportUIGFV3 = false)
                    endObject()
                }

                endArray()

                /*
                * v4.2 起新增千星奇域字段。
                * 本应用暂不采集千星奇域祈愿,故输出空数组 ——
                * 规范允许"导出方可以选择性地填充针对每个游戏的字段或直接忽略",
                * 且 v4.2 兼容 v4.1,填充空数组不影响其它工具读取 hk4e。
                * */
                if (uigfVersion == UIGFHelper.UIGFVersion.V4_2) {
                    name(UIGFHelper.GameField.Hk4eUgc)
                    beginArray()
                    endArray()
                }

                endObject()

                flush()
                close()
            }
        }
    }

    //取该 uid 记录里的语言,缺失或为空时回退 zh-cn
    private suspend fun getLangByUid(uid: String): String {
        val list = dao.getGachaLogItemByUidPage(uid, 0, 1)

        return (if (list.isNotEmpty()) list.first().lang else "zh-cn").ifBlank { "zh-cn" }
    }

    private fun saveGachaItems(
        writer: JsonWriter,
        uid: String,
        exportUIGFV3: Boolean = true
    ) {
        writer.apply {
            beginArray()

            var list: List<GachaItems>
            var page = 0

            do {
                list = dao.getGachaLogItemByUidPage(uid, page, queryPageSize)
                list.forEach { uigfGachaItem ->
                    beginObject()

                    if (exportUIGFV3) {
                        name(UIGFHelper.Field.Item.Count).value(uigfGachaItem.count)
                        name(UIGFHelper.Field.Item.Name).value(uigfGachaItem.name)
                        name(UIGFHelper.Field.Item.RankType).value(uigfGachaItem.rank_type)
                        name(UIGFHelper.Field.Item.ItemType).value(uigfGachaItem.item_type)
                    }

                    name(UIGFHelper.Field.Item.UigfGachaType).value(uigfGachaItem.uigf_gacha_type)
                    name(UIGFHelper.Field.Item.GachaType).value(uigfGachaItem.gacha_type)
                    name(UIGFHelper.Field.Item.ItemId).value(uigfGachaItem.item_id)
                    name(UIGFHelper.Field.Item.Time).value(uigfGachaItem.time)
                    name(UIGFHelper.Field.Item.Id).value(uigfGachaItem.id)
                    endObject()
                }
                page++
            } while (list.size >= queryPageSize)

            endArray()
        }
    }

}