package com.lianyi.paimonsnotebook.common.util.weekly

/*
* 按星期轮换的素材表(移植自胡桃 MaterialIds.Entries,仅保留id,名称取自Material元数据)
* 天赋本与周本材料均为 周一/四、周二/五、周三/六 轮换,周日全部开放
* ids按低到高排列,末位为最高级
* */
object WeeklyMaterialTable {

    enum class Day { MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY }

    //一组同系列的轮换材料(3~4个进阶等级)
    data class RotationalGroup(
        val ids: List<Int>,
        val days: List<Day>
    ) {
        //展示用:最高级材料id
        val topId: Int get() = ids.last()
    }

    //天赋书系列
    val talentGroups = listOf(
        RotationalGroup(listOf(104301, 104302, 104303), listOf(Day.MONDAY, Day.THURSDAY)), //自由
        RotationalGroup(listOf(104310, 104311, 104312), listOf(Day.MONDAY, Day.THURSDAY)), //繁荣
        RotationalGroup(listOf(104320, 104321, 104322), listOf(Day.MONDAY, Day.THURSDAY)), //浮世
        RotationalGroup(listOf(104329, 104330, 104331), listOf(Day.MONDAY, Day.THURSDAY)), //诤言
        RotationalGroup(listOf(104338, 104339, 104340), listOf(Day.MONDAY, Day.THURSDAY)), //公平
        RotationalGroup(listOf(104347, 104348, 104349), listOf(Day.MONDAY, Day.THURSDAY)), //角逐
        RotationalGroup(listOf(104356, 104357, 104358), listOf(Day.MONDAY, Day.THURSDAY)), //月光
        RotationalGroup(listOf(104304, 104305, 104306), listOf(Day.TUESDAY, Day.FRIDAY)), //抗争
        RotationalGroup(listOf(104313, 104314, 104315), listOf(Day.TUESDAY, Day.FRIDAY)), //勤劳
        RotationalGroup(listOf(104323, 104324, 104325), listOf(Day.TUESDAY, Day.FRIDAY)), //风雅
        RotationalGroup(listOf(104332, 104333, 104334), listOf(Day.TUESDAY, Day.FRIDAY)), //巧思
        RotationalGroup(listOf(104341, 104342, 104343), listOf(Day.TUESDAY, Day.FRIDAY)), //正义
        RotationalGroup(listOf(104350, 104351, 104352), listOf(Day.TUESDAY, Day.FRIDAY)), //焚燔
        RotationalGroup(listOf(104359, 104360, 104361), listOf(Day.TUESDAY, Day.FRIDAY)), //乐园
        RotationalGroup(listOf(104307, 104308, 104309), listOf(Day.WEDNESDAY, Day.SATURDAY)), //诗文
        RotationalGroup(listOf(104316, 104317, 104318), listOf(Day.WEDNESDAY, Day.SATURDAY)), //黄金
        RotationalGroup(listOf(104326, 104327, 104328), listOf(Day.WEDNESDAY, Day.SATURDAY)), //天光
        RotationalGroup(listOf(104335, 104336, 104337), listOf(Day.WEDNESDAY, Day.SATURDAY)), //笃行
        RotationalGroup(listOf(104344, 104345, 104346), listOf(Day.WEDNESDAY, Day.SATURDAY)), //秩序
        RotationalGroup(listOf(104353, 104354, 104355), listOf(Day.WEDNESDAY, Day.SATURDAY)), //纷争
        RotationalGroup(listOf(104362, 104363, 104364), listOf(Day.WEDNESDAY, Day.SATURDAY)), //浪迹
    )

    //周本BOSS系列(武器突破材料)
    val bossGroups = listOf(
        RotationalGroup(listOf(114001, 114002, 114003, 114004), listOf(Day.MONDAY, Day.THURSDAY)), //高塔孤王
        RotationalGroup(listOf(114013, 114014, 114015, 114016), listOf(Day.MONDAY, Day.THURSDAY)), //孤云寒林
        RotationalGroup(listOf(114025, 114026, 114027, 114028), listOf(Day.MONDAY, Day.THURSDAY)), //远海夷地
        RotationalGroup(listOf(114037, 114038, 114039, 114040), listOf(Day.MONDAY, Day.THURSDAY)), //谧林涓露
        RotationalGroup(listOf(114049, 114050, 114051, 114052), listOf(Day.MONDAY, Day.THURSDAY)), //悠古弦音
        RotationalGroup(listOf(114061, 114062, 114063, 114064), listOf(Day.MONDAY, Day.THURSDAY)), //贡祭炽心
        RotationalGroup(listOf(114073, 114074, 114075, 114076), listOf(Day.MONDAY, Day.THURSDAY)), //奇巧秘器
        RotationalGroup(listOf(114005, 114006, 114007, 114008), listOf(Day.TUESDAY, Day.FRIDAY)), //凛风奔狼
        RotationalGroup(listOf(114017, 114018, 114019, 114020), listOf(Day.TUESDAY, Day.FRIDAY)), //雾海云间
        RotationalGroup(listOf(114029, 114030, 114031, 114032), listOf(Day.TUESDAY, Day.FRIDAY)), //鸣神御灵
        RotationalGroup(listOf(114041, 114042, 114043, 114044), listOf(Day.TUESDAY, Day.FRIDAY)), //绿洲花园
        RotationalGroup(listOf(114053, 114054, 114055, 114056), listOf(Day.TUESDAY, Day.FRIDAY)), //纯圣露滴
        RotationalGroup(listOf(114065, 114066, 114067, 114068), listOf(Day.TUESDAY, Day.FRIDAY)), //谵妄圣主
        RotationalGroup(listOf(114077, 114078, 114079, 114080), listOf(Day.TUESDAY, Day.FRIDAY)), //长夜燧火
        RotationalGroup(listOf(114009, 114010, 114011, 114012), listOf(Day.WEDNESDAY, Day.SATURDAY)), //狮牙斗士
        RotationalGroup(listOf(114021, 114022, 114023, 114024), listOf(Day.WEDNESDAY, Day.SATURDAY)), //漆黑陨铁
        RotationalGroup(listOf(114033, 114034, 114035, 114036), listOf(Day.WEDNESDAY, Day.SATURDAY)), //今昔剧画
        RotationalGroup(listOf(114045, 114046, 114047, 114048), listOf(Day.WEDNESDAY, Day.SATURDAY)), //玄水珠泪
        RotationalGroup(listOf(114057, 114058, 114059, 114060), listOf(Day.WEDNESDAY, Day.SATURDAY)), //无垢之海
        RotationalGroup(listOf(114069, 114070, 114071, 114072), listOf(Day.WEDNESDAY, Day.SATURDAY)), //神合秘烟
        RotationalGroup(listOf(114081, 114082, 114083, 114084), listOf(Day.WEDNESDAY, Day.SATURDAY)), //终北遗嗣
    )

    /*
    * 指定服务器星期几对应的分组
    *
    * 两种情况下"全部开放":
    *   1. 周日 —— 游戏本身周日全开
    *   2. forceAllOpen —— **新角色卡池开启后的 7 天**内游戏解除刷本限制
    *      (见 GachaMaterialOpenWindow)
    * 其余情况按星期几过滤。
    * */
    fun talentGroupsFor(day: Day, forceAllOpen: Boolean = false) =
        if (forceAllOpen || day == Day.SUNDAY) talentGroups
        else talentGroups.filter { day in it.days }

    fun bossGroupsFor(day: Day, forceAllOpen: Boolean = false) =
        if (forceAllOpen || day == Day.SUNDAY) bossGroups
        else bossGroups.filter { day in it.days }
}
