package com.lianyi.paimonsnotebook.common.web.hutao.genshin.common.service

import com.lianyi.paimonsnotebook.common.util.file.FileHelper
import com.lianyi.paimonsnotebook.common.util.json.JSON
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.common.util.MetadataHelper
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.gacha_event.GachaEventData
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.gacha_event.GachaEventEntry
import org.json.JSONArray

/*
* 祈愿卡池事件服务
* */
class GachaEventService(onMissingFile: () -> Unit) {
    //按开始时间升序的事件列表
    var eventList = listOf<GachaEventEntry>()
        private set

    init {
        val gachaEventFile = FileHelper.getMetadata(MetadataHelper.FileNameGachaEvent)

        if (gachaEventFile != null) {
            setEventList(JSONArray(gachaEventFile.readText()))
        } else {
            onMissingFile.invoke()
        }
    }

    private fun setEventList(jsonArray: JSONArray) {
        val list = mutableListOf<GachaEventEntry>()

        repeat(jsonArray.length()) {
            val jsonString = jsonArray.getJSONObject(it).toString()

            JSON.parse<GachaEventData>(jsonString)?.let { event ->
                val from = GachaEventData.parseEventTime(event.From)
                val to = GachaEventData.parseEventTime(event.To)

                if (from != null && to != null) {
                    list += GachaEventEntry(event, from, to)
                }
            }
        }

        list.sortBy { it.fromMillis }

        eventList = list
    }
}
