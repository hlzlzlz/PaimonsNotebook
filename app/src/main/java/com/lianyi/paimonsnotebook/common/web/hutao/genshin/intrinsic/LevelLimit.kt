package com.lianyi.paimonsnotebook.common.web.hutao.genshin.intrinsic

/*
* 角色/武器等级上限
*
* 背景:游戏已开放角色 100 级上限(需新材料「无主的命星」做 95/100 阶突破),
* 而本项目原先在 6 处硬编码 90。元数据侧早已就绪:实测
*   Snap.Metadata/Genshin/CHS/AvatarCurve.json  -> Level 1..100 连续 100 档
*   Snap.Metadata/Genshin/CHS/WeaponCurve.json  -> Level 1..100 连续 100 档
* 所以曲线的数据是够算到 100 的,缺的只是把这个上限暴露到 UI 与计算里。
*
* 判定依据(以胡桃工具箱为参照,它是维护活跃的同源实现):
*   - 角色:Avatar.GetMaxLevel() 直接返回 100U
*   - 武器:GetMaxLevelByQuality() 仍是 quality >= QUALITY_BLUE ? 90U : 70U
*   - 突破:OfflineCalculator 里有"95级上限突破"(需 1 个无主的命星)
*          与"100级上限突破"(需 2 个无主的命星)两段独立逻辑
* 即:角色升到 100,武器仍是 90/70 —— 两者不对称,不要一起改。
*
* 注意:AvatarPromote.json 的 Level 只到 6(90 级档),
* 95/100 两阶突破的加成数据不在 AvatarPromote 里,而在服务端
* batch_compute 接口的返回中。所以本地属性预估到 90 级为止是准确的,
* 90 级以上以接口结果为准(ItemBaseViewModel 走的就是接口)。
* */
object LevelLimit {

    //角色等级上限
    const val AvatarMaxLevel = 100

    //武器等级上限(按品质:1~2 星 70,3 星及以上 90)
    const val WeaponMaxLevelLow = 70
    const val WeaponMaxLevelHigh = 90

    //达到该品质(RankLevel)即用高上限
    private const val WEAPON_MAX_LEVEL_QUALITY_THRESHOLD = 3

    /*
    * 按武器品质取上限
    *
    * rankLevel:元数据里的 RankLevel(1~5)
    * 与 Web.hutao.genshin.weapon.WeaponData.maxLevel 的原逻辑一致:
    *   rankLevel >= 3 -> 90,否则 70
    * 与胡桃 GetMaxLevelByQuality(quality >= QUALITY_BLUE ? 90 : 70) 一致
    * (QUALITY_BLUE 即 3 星)
    * */
    fun weaponMaxLevel(rankLevel: Int): Int =
        if (rankLevel >= WEAPON_MAX_LEVEL_QUALITY_THRESHOLD) {
            WeaponMaxLevelHigh
        } else {
            WeaponMaxLevelLow
        }

    /*
    * 天赋(普通攻击/元素战技/元素爆发)等级上限
    *
    * 默认 10;有命座加成时可达 13(见 AvatarSkillFormat.maxLevel 取自
    * Proud.Parameters.size,此处不覆盖那条路径)
    * */
    const val SkillMaxLevel = 10
}
