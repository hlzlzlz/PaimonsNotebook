package com.lianyi.paimonsnotebook

import com.lianyi.paimonsnotebook.ui.screen.home.util.DrawerTargetMigration
import com.lianyi.paimonsnotebook.ui.screen.home.util.HomeHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
* 侧边栏类名迁移的回归测试(1.8.24 合并侧边栏条目)
*
* 钉住的是**静默丢条目**这一失效模式:
* 自定义侧边栏以类名持久化,合并不迁移的话 mapNotNull / 查表会把老条目丢掉,
* 用户看到自己配置过的功能莫名消失且无任何提示 —— 编译期完全不报错。
*
* 关键断言不是"迁移函数写对了",而是**迁移结果必须在侧边栏清单里查得到**
* (即迁移目标类名确实是 home 侧边栏的一个合法 target)。只断言字符串相等
* 是假信心:把目标类名写错一个字母,那条用例照样绿。
* */
class DrawerTargetMigrationTest {

    private val wiki = "com.lianyi.paimonsnotebook.ui.screen.wiki.view.WikiScreen"
    private val grow = "com.lianyi.paimonsnotebook.ui.screen.grow.view.GrowScreen"
    private val combat = "com.lianyi.paimonsnotebook.ui.screen.combat.view.CombatRecordScreen"

    /*
    * 1.8.24 之前存在于侧边栏、之后被合并掉的 8 个类名。
    *
    * ⚠️ 必须包含全部三组:资料库(4) + 养成素材(2) + 战斗记录(2)。
    *    最初写这条时漏了战斗记录那两个,导致"应收敛到 3 个入口"断言失败
    *    —— 正是这条用例把漏项抓了出来。
    * */
    private val mergedAway = listOf(
        "com.lianyi.paimonsnotebook.ui.screen.items.view.AvatarScreen",
        "com.lianyi.paimonsnotebook.ui.screen.items.view.WeaponScreen",
        "com.lianyi.paimonsnotebook.ui.screen.items.view.MonsterScreen",
        "com.lianyi.paimonsnotebook.ui.screen.items.view.ReliquaryScreen",
        "com.lianyi.paimonsnotebook.ui.screen.cultivate_project.view.CultivateProjectScreen",
        "com.lianyi.paimonsnotebook.ui.screen.weekly.view.WeeklyCalendarScreen",
        "com.lianyi.paimonsnotebook.ui.screen.abyss.view.AbyssScreen",
        "com.lianyi.paimonsnotebook.ui.screen.role_combat.view.RoleCombatScreen"
    )

    @Test
    fun 四个资料页都迁移到资料库() {
        listOf(
            "com.lianyi.paimonsnotebook.ui.screen.items.view.AvatarScreen",
            "com.lianyi.paimonsnotebook.ui.screen.items.view.WeaponScreen",
            "com.lianyi.paimonsnotebook.ui.screen.items.view.MonsterScreen",
            "com.lianyi.paimonsnotebook.ui.screen.items.view.ReliquaryScreen"
        ).forEach {
            assertEquals("$it 应迁移到资料库", wiki, DrawerTargetMigration.migrate(it))
        }
    }

    @Test
    fun 养成计划与素材日历都迁移到养成素材() {
        assertEquals(
            grow,
            DrawerTargetMigration.migrate(
                "com.lianyi.paimonsnotebook.ui.screen.cultivate_project.view.CultivateProjectScreen"
            )
        )
        assertEquals(
            grow,
            DrawerTargetMigration.migrate(
                "com.lianyi.paimonsnotebook.ui.screen.weekly.view.WeeklyCalendarScreen"
            )
        )
    }

    @Test
    fun 深境螺旋与战斗记录都迁移到战斗记录() {
        assertEquals(
            combat,
            DrawerTargetMigration.migrate(
                "com.lianyi.paimonsnotebook.ui.screen.abyss.view.AbyssScreen"
            )
        )
        assertEquals(
            combat,
            DrawerTargetMigration.migrate(
                "com.lianyi.paimonsnotebook.ui.screen.role_combat.view.RoleCombatScreen"
            )
        )
    }

    /*
    * ⚠️ 这是本测试真正要钉住的一条:
    * 迁移后的类名必须**能在侧边栏清单里查到**,否则条目依然会被丢掉。
    * 用真实的 modalItemMap 而不是自己维护一份字符串对照表 ——
    * 否则改了侧边栏却忘了改对照表,用例照样绿(循环论证)。
    * */
    @Test
    fun 全部迁移目标类名都能在侧边栏清单中查到() {
        mergedAway.forEach { old ->
            val new = DrawerTargetMigration.migrate(old)

            assertNotEquals("$old 未被迁移", old, new)
            assertNotNull(
                "$old 迁移到 $new,但侧边栏里没有这个 target —— 该条目仍会被静默丢弃",
                HomeHelper.modalItemMap[new]
            )
        }
    }

    /*
    * 未合并的功能必须**原样保留**:迁移函数不能误伤。
    * */
    @Test
    fun 未涉及的类名原样返回() {
        val untouched = listOf(
            "com.lianyi.paimonsnotebook.ui.screen.daily_note.view.DailyNoteScreen",
            "com.lianyi.paimonsnotebook.ui.screen.gacha.view.GachaRecordScreen",
            "com.lianyi.paimonsnotebook.ui.screen.dps.view.DpsCalculatorScreen",
            //资料库/养成素材/战斗记录自身也必须稳定(幂等)
            wiki, grow, combat
        )

        untouched.forEach {
            assertEquals("$it 不应被改动", it, DrawerTargetMigration.migrate(it))
        }
    }

    /*
    * 旅行者札记是**有意**不迁移的唯一条目(已从侧边栏移除,首页卡片仍可进入),
    * 它应当原样返回并因此被查表过滤掉。把这条写出来是为了区分
    * "有意不迁移"与"漏迁移" —— 后者才是 bug。
    * */
    @Test
    fun 旅行者札记有意不迁移() {
        val diary = "com.lianyi.paimonsnotebook.ui.screen.travelers_diary.view.TravelersDiaryScreen"

        assertEquals(diary, DrawerTargetMigration.migrate(diary))
        assertTrue(
            "札记已从侧边栏移除,故不应在 modalItemMap 中",
            HomeHelper.modalItemMap[diary] == null
        )
    }

    /*
    * 去重语义:两个旧条目映射到同一入口后必须只留一条。
    * 侧边栏与管理页都依赖 distinctBy(见调用点),这里钉住"确实会产生重复"
    * 这一前提 —— 若哪天映射改成一对一,这条会失败并提醒去复核去重逻辑。
    * */
    @Test
    fun 合并后的条目会映射到同一目标因此需要去重() {
        val migrated = mergedAway.map { DrawerTargetMigration.migrate(it) }

        assertEquals("8 个旧条目", 8, migrated.size)
        assertEquals("应收敛到 3 个入口", 3, migrated.distinct().size)
        assertEquals("资料库出现 4 次", 4, migrated.count { it == wiki })
        assertEquals("养成素材出现 2 次", 2, migrated.count { it == grow })
        assertEquals("战斗记录出现 2 次", 2, migrated.count { it == combat })
    }
}
