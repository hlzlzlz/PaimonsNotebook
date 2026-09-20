package com.lianyi.paimonsnotebook.ui.screen.gacha.service

import com.lianyi.paimonsnotebook.common.data.hoyolab.PlayerUid
import com.lianyi.paimonsnotebook.common.database.PaimonsNotebookDatabase
import com.lianyi.paimonsnotebook.common.database.gacha.entity.BeyondGachaItems
import com.lianyi.paimonsnotebook.common.util.metadata.genshin.uigf.UIGFHelper
import com.lianyi.paimonsnotebook.common.web.hoyolab.hk4e.event.gacha_info.BeyondGachaLogItem
import com.lianyi.paimonsnotebook.common.web.hoyolab.hk4e.event.gacha_info.GachaInfoClient
import com.lianyi.paimonsnotebook.common.web.hoyolab.hk4e.event.gacha_info.GachaQueryConfigData
import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.binding.GameAuthKeyData
import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.binding.GenAuthKeyData
import kotlinx.coroutines.delay

/*
* 千星奇域(UGC)祈愿记录拉取
*
* 与普通祈愿(见 GachaRecordOptionScreenViewModel.getGachaLog)是两条独立链路:
* 端点不同(getBeyondGachaLog)、响应结构不同、落库表不同(beyond_gacha_items)。
* 单独成类而不是塞进 ViewModel,是为了让它可被单测直接驱动。
*
* 分页参数要点(均据胡桃实现 + 实测):
*   - UGC 的 size 用 **5**(胡桃 GachaLogTypedQueryOptions.BeyondSize),
*     普通祈愿才是 20。用 20 服务端也返回 200,但按 5 对齐更稳妥。
*   - 翻页靠 end_id:取上一页最后一条的 id 作为下一页的 end_id。
*   - 终止条件:本页返回条数 < size(说明到底了)。
*   - 本地已存在的记录处截断:takeWhile { it.id != localEndId },
*     这样重复拉取时不会无限翻页(与普通祈愿同一策略)。
* */
class BeyondGachaLogService(
    private val database: PaimonsNotebookDatabase = PaimonsNotebookDatabase.database,
    private val gachaInfoClient: GachaInfoClient = GachaInfoClient()
) {

    private val dao by lazy { database.beyondGachaItemsDao }

    companion object {
        /*
        * UGC 分页大小
        * 与胡桃 GachaLogTypedQueryOptions.BeyondSize 一致(普通祈愿是 20)
        * */
        const val PAGE_SIZE = 5
    }

    /*
    * 拉取结果
    *
    * addedCount:本次新入库的条数
    * failedType:失败时是哪个卡池类型(供提示),全部成功为 null
    * */
    data class FetchResult(
        val addedCount: Int,
        val failedType: String?,
        val errorMessage: String?
    ) {
        val success: Boolean get() = failedType == null
    }

    /*
    * 拉取并保存千星奇域记录
    *
    * gameAuthKeyData 必须已做 URL 编码(见 GameAuthKeyData.asEncodeAuthKeyData):
    * authkey 是 base64,含 + / =,未编码时 + 会被当作空格导致
    * retcode -100 authkey error —— 这一点已实测确认。
    *
    * onProgress 用于更新界面描述;它可能被高频调用,调用方需自行节流。
    * */
    suspend fun fetchAndSave(
        gameAuthKeyData: GameAuthKeyData,
        playerUid: PlayerUid,
        lang: String = "zh-cn",
        onProgress: (String) -> Unit = {}
    ): FetchResult {
        var addedCount = 0

        UIGFHelper.BeyondGachaType.queryList.forEach { gachaType ->
            var endId = "0"

            //本地已有的该卡池最后一条记录 id,用于截断,避免重复拉取时死循环
            val localEndId = dao.getLastIdByUidAndGachaType(playerUid.value, gachaType)

            var pageCount = 1

            while (true) {
                onProgress(
                    "正在获取${UIGFHelper.getBeyondGachaName(gachaType)}的第${pageCount}页记录"
                )

                val result = gachaInfoClient.getBeyondGachaLogPage(
                    GachaQueryConfigData(
                        gachaType = gachaType,
                        gameAuthKeyData = gameAuthKeyData,
                        genAuthKeyData = GenAuthKeyData.createForWebViewGacha(playerUid = playerUid),
                        size = PAGE_SIZE,
                        endId = endId
                    )
                )

                if (!result.success) {
                    return FetchResult(
                        addedCount = addedCount,
                        failedType = gachaType,
                        errorMessage = result.errorMsg
                    )
                }

                val list = result.data.list

                //已到本地已有记录处则停止该卡池
                val shouldAdd = list.takeWhile { it.id != localEndId }

                if (shouldAdd.isNotEmpty()) {
                    dao.insert(shouldAdd.map { it.asEntity(lang = lang, fallbackUid = playerUid.value) })
                    addedCount += shouldAdd.size
                }

                //到底了(本页不足一页)或命中本地已有记录 => 换下一个卡池
                if (list.size < PAGE_SIZE || shouldAdd.size < list.size) {
                    break
                }

                endId = list.last().id
                pageCount++

                /*
                * 限速:服务端对高频访问返回 -110 visit too frequently
                * (实测连续快速请求 6 个类型码后触发)。
                * 与普通祈愿保持同样的 1~2 秒随机间隔。
                * */
                delay((1000L..2000L).random())
            }
        }

        if (addedCount > 0) {
            dao.notifyRoomUpdate()
        }

        return FetchResult(addedCount = addedCount, failedType = null, errorMessage = null)
    }

    /*
    * 转换为数据库实体
    *
    * uid 是主键的一部分,不能为空:若服务端未返回 uid(实测空响应里没有该字段),
    * 用调用方已知的 playerUid 兜底,否则记录会挂到空 uid 上、查询/导出都取不到。
    * */
    private fun BeyondGachaLogItem.asEntity(lang: String, fallbackUid: String) = BeyondGachaItems(
        id = id,
        uid = uid.ifBlank { fallbackUid },
        region = region,
        schedule_id = schedule_id,
        item_type = item_type,
        item_id = item_id,
        item_name = item_name,
        rank_type = rank_type,
        is_up = is_up,
        time = time,
        op_gacha_type = op_gacha_type,
        lang = lang
    )
}
