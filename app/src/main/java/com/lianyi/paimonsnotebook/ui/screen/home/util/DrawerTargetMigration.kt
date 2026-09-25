package com.lianyi.paimonsnotebook.ui.screen.home.util

/*
* 侧边栏类名迁移(纯函数,便于单测)
*
* 背景:自定义侧边栏与快捷方式都以 **Activity 类名** 持久化用户配置
* (见 ShortcutsManagerScreenViewModel 的 map key 与 HomeCustomDrawerData.targetClass)。
*
* 1.8.24 把 6 个侧边栏条目合并掉了:
*   角色/武器/怪物/圣遗物资料 -> 资料库
*   养成计划 + 素材日历        -> 养成素材
*   深境螺旋 + 战斗记录        -> 战斗记录
* 老配置里存的仍是旧类名。
*
* ⚠️ 不迁移的后果是**静默丢条目**:侧边栏用 modalItemMap[className] 查表,
*    查不到就 mapNotNull 丢掉;管理页则 `?: return@itemsIndexed` 整行不渲染。
*    用户会看到自己配置过的功能莫名消失,且没有任何提示 —— 属于本项目
*    反复出现的失效模式(见 AGENTS.md 的"功能静默消失"教训)。
*
* 抽成纯函数是因为这类**映射遗漏**编译期完全不报错,只能靠用例钉住。
* */
object DrawerTargetMigration {

    private const val WIKI_SCREEN =
        "com.lianyi.paimonsnotebook.ui.screen.wiki.view.WikiScreen"

    private const val GROW_SCREEN =
        "com.lianyi.paimonsnotebook.ui.screen.grow.view.GrowScreen"

    private const val COMBAT_RECORD_SCREEN =
        "com.lianyi.paimonsnotebook.ui.screen.combat.view.CombatRecordScreen"

    /*
    * 旧类名 -> 新入口类名。不在表里的原样返回。
    * */
    fun migrate(className: String): String = when (className) {
        //四个资料页 -> 资料库
        "com.lianyi.paimonsnotebook.ui.screen.items.view.AvatarScreen",
        "com.lianyi.paimonsnotebook.ui.screen.items.view.WeaponScreen",
        "com.lianyi.paimonsnotebook.ui.screen.items.view.MonsterScreen",
        "com.lianyi.paimonsnotebook.ui.screen.items.view.ReliquaryScreen" -> WIKI_SCREEN

        //养成计划 + 素材日历 -> 养成素材
        "com.lianyi.paimonsnotebook.ui.screen.cultivate_project.view.CultivateProjectScreen",
        "com.lianyi.paimonsnotebook.ui.screen.weekly.view.WeeklyCalendarScreen" -> GROW_SCREEN

        //深境螺旋 + 战斗记录 -> 战斗记录
        "com.lianyi.paimonsnotebook.ui.screen.abyss.view.AbyssScreen",
        "com.lianyi.paimonsnotebook.ui.screen.role_combat.view.RoleCombatScreen" ->
            COMBAT_RECORD_SCREEN

        /*
        * 旅行者札记:已从侧边栏移除(首页卡片仍可进入),故**有意不映射** ——
        * 它会在查表时被过滤掉。这是唯一一个有意让其消失的条目,并且用户
        * 仍能从首页进入该功能,不构成功能丢失。
        *
        * 其余未知类名同样原样返回(交给查表过滤),避免把不认识的类名
        * 误映射到某个功能上。
        * */
        else -> className
    }
}
