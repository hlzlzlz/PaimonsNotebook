package com.lianyi.paimonsnotebook

import com.lianyi.paimonsnotebook.common.database.abyss.entity.AbyssSeasonSnapshot
import com.lianyi.paimonsnotebook.common.util.metadata.genshin.abyss.AbyssSnapshotMapper
import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.game_record.abyss.SpiralAbyssData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/*
* 深渊快照构建与整理的回归测试
*
* 重点:层星序列化往返(含损坏数据容错)与期数标签 ——
* 这两处最容易在后续改动中被静默改坏。
* */
class AbyssSnapshotMapperTest {

    private fun floor(index: Int, star: Int, maxStar: Int = 3) = SpiralAbyssData.Floor(
        icon = "",
        index = index,
        is_unlock = true,
        levels = emptyList(),
        ley_line_disorder = emptyList(),
        max_star = maxStar,
        settle_date_time = "",
        settle_time = "",
        star = star
    )

    private fun abyss(
        scheduleId: Int = 202501,
        totalStar: Int = 33,
        maxFloor: String = "12-3",
        floors: List<SpiralAbyssData.Floor> = listOf(floor(12, 3), floor(11, 3), floor(10, 3))
    ) = SpiralAbyssData(
        damage_rank = emptyList(),
        defeat_rank = emptyList(),
        end_time = "2025-09-15 23:59:59",
        energy_skill_rank = emptyList(),
        floors = floors,
        is_unlock = true,
        max_floor = maxFloor,
        normal_skill_rank = emptyList(),
        reveal_rank = emptyList(),
        schedule_id = scheduleId,
        start_time = "2025-09-01 04:00:00",
        take_damage_rank = emptyList(),
        total_battle_times = 12,
        total_star = totalStar,
        total_win_times = 12
    )

    @Test
    fun 快照保留关键字段() {
        val snap = AbyssSnapshotMapper.toSnapshot(abyss(), "100000001", 999L)

        assertEquals("100000001", snap.uid)
        assertEquals(202501, snap.schedule_id)
        assertEquals(33, snap.total_star)
        assertEquals("12-3", snap.max_floor)
        assertEquals(12, snap.total_battle_times)
        assertEquals(999L, snap.saved_at)
    }

    @Test
    fun 层星序列化往返() {
        val snap = AbyssSnapshotMapper.toSnapshot(
            abyss(floors = listOf(floor(12, 3), floor(11, 2), floor(10, 1))),
            "100000001",
            1L
        )

        val restored = AbyssSnapshotMapper.deserializeFloors(snap.floor_stars)

        assertEquals(3, restored.size)
        //按层号倒序
        assertEquals(listOf(12, 11, 10), restored.map { it.index })
        assertEquals(listOf(3, 2, 1), restored.map { it.star })
    }

    @Test
    fun 空层列表序列化往返为空() {
        val snap = AbyssSnapshotMapper.toSnapshot(abyss(floors = emptyList()), "u", 1L)

        assertTrue(AbyssSnapshotMapper.deserializeFloors(snap.floor_stars).isEmpty())
    }

    @Test
    fun 损坏的层星段被跳过而非整条作废() {
        // 第一段合法,第二段字段数不对,第三段数字非法
        val restored = AbyssSnapshotMapper.deserializeFloors("12:3:3|bad|11:x:3|10:2:3")

        assertEquals(2, restored.size)
        assertEquals(listOf(12, 10), restored.map { it.index })
    }

    @Test
    fun 期数标签用开始日期() {
        val snap = AbyssSnapshotMapper.toSnapshot(abyss(), "u", 1L)

        assertEquals("2025-09-01", AbyssSnapshotMapper.seasonLabel(snap))
    }

    @Test
    fun 开始时间为空时回退期数编号() {
        val snap = AbyssSnapshotMapper.toSnapshot(abyss(), "u", 1L).copy(start_time = "")

        assertEquals("第 202501 期", AbyssSnapshotMapper.seasonLabel(snap))
    }

    @Test
    fun 满星判定() {
        assertTrue(AbyssSnapshotMapper.isFullStar(36))
        assertTrue(AbyssSnapshotMapper.isFullStar(37))
        assertFalse(AbyssSnapshotMapper.isFullStar(35))
        assertFalse(AbyssSnapshotMapper.isFullStar(0))
    }

    @Test
    fun 星数走势按倒序取最近几期() {
        val snapshots = listOf(
            snapshotOf(202503, 36),
            snapshotOf(202502, 33),
            snapshotOf(202501, 30),
            snapshotOf(202412, 27)
        )

        assertEquals(
            listOf(36, 33, 30),
            AbyssSnapshotMapper.starTrend(snapshots, limit = 3)
        )
    }

    @Test
    fun 走势limit大于期数时全返回() {
        val snapshots = listOf(snapshotOf(202502, 33), snapshotOf(202501, 30))

        assertEquals(listOf(33, 30), AbyssSnapshotMapper.starTrend(snapshots, limit = 10))
    }

    private fun snapshotOf(scheduleId: Int, totalStar: Int) = AbyssSeasonSnapshot(
        uid = "100000001",
        schedule_id = scheduleId,
        start_time = "2025-09-01 04:00:00",
        end_time = "2025-09-15 23:59:59",
        total_star = totalStar,
        max_floor = "12-3",
        total_battle_times = 12,
        total_win_times = 12,
        floor_stars = "12:3:3",
        saved_at = 0L
    )
}
