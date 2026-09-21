package com.lianyi.paimonsnotebook

import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.game_record.act_calendar.ActCalendarData
import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.game_record.act_calendar.ActCalendarHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/*
* 活动日历数据整理的回归测试
*
* 这些逻辑此前不存在(接口的三个活动列表被解析后直接丢弃),
* 属新增行为,故用测试把"去重/排序/文案"三条规则钉住。
* */
class ActCalendarHelperTest {

    private fun act(
        id: Int,
        name: String = "活动$id",
        status: Int = 2,
        countdown: Long = 3600L
    ) = ActCalendarData.Act(
        id = id,
        name = name,
        type = "Act",
        start_timestamp = "1700000000",
        start_time = null,
        end_timestamp = "1700100000",
        end_time = null,
        desc = "",
        strategy = "",
        countdown_seconds = countdown,
        status = status,
        reward_list = emptyList(),
        is_finished = false
    )

    private fun calendar(
        selected: List<ActCalendarData.Act> = emptyList(),
        actList: List<ActCalendarData.Act> = emptyList(),
        fixed: List<ActCalendarData.Act> = emptyList()
    ) = ActCalendarData(
        avatar_card_pool_list = emptyList(),
        weapon_card_pool_list = emptyList(),
        mixed_card_pool_list = emptyList(),
        selected_avatar_card_pool_list = emptyList(),
        selected_mixed_card_pool_list = emptyList(),
        act_list = actList,
        fixed_act_list = fixed,
        selected_act_list = selected
    )

    @Test
    fun 三个列表被合并() {
        val merged = ActCalendarHelper.mergeActs(
            calendar(
                selected = listOf(act(1)),
                actList = listOf(act(2)),
                fixed = listOf(act(3))
            )
        )

        assertEquals(3, merged.size)
        assertEquals(setOf(1, 2, 3), merged.map { it.id }.toSet())
    }

    @Test
    fun 重复活动按id去重() {
        //实测 selected_act_list 的内容来自 act_list,直接相加会出现重复
        val merged = ActCalendarHelper.mergeActs(
            calendar(
                selected = listOf(act(7, name = "精选副本")),
                actList = listOf(act(7, name = "原列表副本"), act(8))
            )
        )

        assertEquals("重复 id 必须只保留一条", 2, merged.size)
        assertEquals(
            "应保留先出现的(精选在前)",
            "精选副本",
            merged.first { it.id == 7 }.name
        )
    }

    @Test
    fun 进行中排在进行之前() {
        val merged = ActCalendarHelper.mergeActs(
            calendar(
                actList = listOf(
                    act(1, status = 1, countdown = 100L),
                    act(2, status = 2, countdown = 99999L)
                )
            )
        )

        assertEquals(
            "进行中(status=2)必须排在即将开始(status=1)之前",
            2,
            merged.first().id
        )
    }

    @Test
    fun 同状态内按剩余时间升序() {
        val merged = ActCalendarHelper.mergeActs(
            calendar(
                actList = listOf(
                    act(1, status = 2, countdown = 9000L),
                    act(2, status = 2, countdown = 100L),
                    act(3, status = 2, countdown = 5000L)
                )
            )
        )

        assertEquals(
            "快结束的应排前面",
            listOf(2, 3, 1),
            merged.map { it.id }
        )
    }

    @Test
    fun 未知状态排在最后() {
        val merged = ActCalendarHelper.mergeActs(
            calendar(
                actList = listOf(
                    act(1, status = 0, countdown = 1L),
                    act(2, status = 2, countdown = 999999L)
                )
            )
        )

        assertEquals("脏数据(status=0)不能挤到顶部", 2, merged.first().id)
    }

    @Test
    fun id为零的脏数据不参与去重() {
        //id=0 是异常数据,若参与去重会把多条不同活动误判成同一条
        val merged = ActCalendarHelper.mergeActs(
            calendar(actList = listOf(act(0, name = "A"), act(0, name = "B")))
        )

        assertEquals("id=0 的条目不应被去重掉", 2, merged.size)
    }

    @Test
    fun 空数据返回空列表() {
        assertTrue(ActCalendarHelper.mergeActs(calendar()).isEmpty())
    }

    @Test
    fun 状态文案正确() {
        assertEquals("进行中", ActCalendarHelper.statusText(2))
        assertEquals("即将开始", ActCalendarHelper.statusText(1))
        assertEquals("已结束", ActCalendarHelper.statusText(0))
    }

    @Test
    fun 倒计时文案按量级切换() {
        assertEquals("剩 2天3时", ActCalendarHelper.countdownText(2 * 86400L + 3 * 3600L))
        assertEquals("剩 5小时", ActCalendarHelper.countdownText(5 * 3600L))
        assertEquals("剩 30分钟", ActCalendarHelper.countdownText(30 * 60L))
        assertEquals("即将结束", ActCalendarHelper.countdownText(30L))
    }

    @Test
    fun 倒计时为零或负数返回空() {
        //已结束的活动不该显示"剩 0分钟"这类无意义文案
        assertEquals("", ActCalendarHelper.countdownText(0L))
        assertEquals("", ActCalendarHelper.countdownText(-100L))
    }
}
