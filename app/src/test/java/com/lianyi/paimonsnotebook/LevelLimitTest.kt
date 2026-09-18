package com.lianyi.paimonsnotebook

import com.lianyi.paimonsnotebook.common.web.hutao.genshin.intrinsic.LevelLimit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/*
* 等级上限的回归测试
*
* 背景:游戏已开放角色 100 级上限(需新材料「无主的命星」做 95/100 阶突破),
* 而本项目原先在 UI 与计算里硬编码 90,原作者自己也留了
* "TODO 角色等级上限提升至100" —— 养成计算器会算不出 90 级以上,
* 属性预览滑块也封顶在 90。这里锁死上限值,避免再次悄悄退回。
*
* 判定依据(以胡桃工具箱为参照,它是维护活跃的同源实现):
*   - 角色:Avatar.GetMaxLevel() 返回 100U
*   - 武器:GetMaxLevelByQuality() 仍是 quality >= QUALITY_BLUE ? 90U : 70U
*   - 突破:OfflineCalculator 里有"95级上限突破"与"100级上限突破"两段
* 即角色 100、武器 90/70,两者不对称。
*
* 元数据侧交叉验证(本地 Snap.Metadata 实测):
*   AvatarCurve.json / WeaponCurve.json 均为 Level 1..100 连续 100 档,
*   且 Lv100 曲线值真实递增(如 Type22: 8.349@90 -> 9.174@100)。
* */
class LevelLimitTest {

    @Test
    fun `角色等级上限为100`() {
        assertEquals(100, LevelLimit.AvatarMaxLevel)
    }

    @Test
    fun `武器等级上限按品质为90或70`() {
        assertEquals(70, LevelLimit.weaponMaxLevel(1))
        assertEquals(70, LevelLimit.weaponMaxLevel(2))
        //3 星起用高上限,与胡桃 QUALITY_BLUE 阈值一致
        assertEquals(90, LevelLimit.weaponMaxLevel(3))
        assertEquals(90, LevelLimit.weaponMaxLevel(4))
        assertEquals(90, LevelLimit.weaponMaxLevel(5))
    }

    @Test
    fun `武器上限阈值边界不被改错`() {
        //1~2 星低上限、3 星及以上高上限
        assertTrue(LevelLimit.weaponMaxLevel(2) < LevelLimit.weaponMaxLevel(3))
        //上限本身不能低于游戏实际开放的 90
        assertEquals(90, LevelLimit.WeaponMaxLevelHigh)
        assertEquals(70, LevelLimit.WeaponMaxLevelLow)

        //角色上限必须高于武器上限(角色已开放 100,武器仍是 90)
        assertTrue(LevelLimit.AvatarMaxLevel > LevelLimit.WeaponMaxLevelHigh)
    }

    @Test
    fun `天赋等级上限为10`() {
        assertEquals(10, LevelLimit.SkillMaxLevel)
    }

    @Test
    fun `角色上限覆盖游戏已开放的90级以上区间`() {
        //90 级是旧硬编码值,必须已被超越,否则等于没修
        assertTrue("角色上限应大于旧的 90", LevelLimit.AvatarMaxLevel > 90)
        //95/100 是两阶"上限突破",上限至少要能到 100
        assertTrue("角色上限应至少到 100", LevelLimit.AvatarMaxLevel >= 100)
    }
}
