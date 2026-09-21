package com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.game_record.act_calendar

/*
* 活动日历的数据整理
*
* act_calendar 接口除了卡池,还返回三个活动列表:
*   act_list / fixed_act_list / selected_act_list
* 它们此前只被解析进 ActCalendarData 就丢掉了(首页只用了卡池),
* 属于"请求已经发出、数据已经在手"却没展示的部分。
*
* 抽成纯函数以便单测 —— 排序/去重这类逻辑最容易写错,
* 且与 Compose 解耦后不需要真机即可验证。
* */
object ActCalendarHelper {

    /*
    * 合并三个活动列表
    *
    * 实测这三者**存在重叠**(selected_act_list 是"精选",内容来自 act_list),
    * 直接相加会出现重复条目,故按 id 去重(保留先出现的)。
    *
    * 排序规则:进行中(status=2)优先,其次即将开始(status=1),
    * 同状态内按剩余时间升序 —— 快结束的排前面,符合"活动日历"的使用预期。
    * */
    fun mergeActs(data: ActCalendarData): List<ActCalendarData.Act> {
        val seen = HashSet<Int>()
        val merged = ArrayList<ActCalendarData.Act>()

        //顺序即优先级:精选在前,便于用户先看到重点活动
        val sources = listOf(
            data.selected_act_list,
            data.act_list,
            data.fixed_act_list
        )

        for (list in sources) {
            for (act in list) {
                //id 为 0 属异常数据,不参与去重(否则所有脏数据会被当成同一条)
                if (act.id != 0 && !seen.add(act.id)) {
                    continue
                }
                merged += act
            }
        }

        return merged.sortedWith(
            compareByDescending<ActCalendarData.Act> { statusWeight(it.status) }
                .thenBy { it.countdown_seconds }
        )
    }

    /*
    * status: 1 即将开始, 2 进行中 (接口定义)
    * 其余值(含 0/未知)排最后,避免脏数据挤到列表顶部
    * */
    private fun statusWeight(status: Int): Int = when (status) {
        2 -> 2
        1 -> 1
        else -> 0
    }

    /*
    * 状态文案
    * 用接口的 status 而非 countdown_seconds 推断:倒计时为 0 时无法区分
    * "刚开始"与"已结束",而 status 是服务端明确给出的
    * */
    fun statusText(status: Int): String = when (status) {
        2 -> "进行中"
        1 -> "即将开始"
        else -> "已结束"
    }

    /*
    * 倒计时文案
    *
    * 信任服务端下发的 countdown_seconds(与卡池卡片一致的做法),
    * 不自行用 end_time 计算 —— 设备时钟不可靠,且跨时区会算错。
    * */
    fun countdownText(seconds: Long): String {
        if (seconds <= 0) return ""

        val days = seconds / 86400
        val hours = seconds % 86400 / 3600
        val minutes = seconds % 3600 / 60

        return when {
            days >= 1 -> "剩 ${days}天${hours}时"
            hours >= 1 -> "剩 ${hours}小时"
            minutes >= 1 -> "剩 ${minutes}分钟"
            else -> "即将结束"
        }
    }
}
