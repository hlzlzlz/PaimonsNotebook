package com.lianyi.paimonsnotebook.ui.screen.achievement.util.enums

/*
* UIAF 导入策略
*
* 语义对照胡桃工具箱 Snap.Hutao 的 ImportStrategyKind(已读源码确认):
*   AggressiveMerge = 0  合并,且**同 id 时用导入数据覆盖**本地
*   LazyMerge       = 1  合并,但**同 id 时保留本地**(只补本地没有的)
*   Overwrite       = 2  清空该用户的全部记录,再写入导入数据
*
* 背景:PN 原先只有"直接 INSERT OR REPLACE"这一种行为,等价于 AggressiveMerge
* 但不给用户选择 —— 导入别人的存档会**静默覆盖**自己的进度,且无法挽回。
* 这里把三种语义显式化,让用户自己决定。
*
* ⚠️ AggressiveMerge 的"覆盖"是**整条替换**:导入数据里若某成就未完成(status=0),
* 会把本地已完成的也改回未完成。这是胡桃的原版语义(它的 Merge 直接
* Remove+Add),故保持一致,不做"取更优者"的额外判断 ——
* 否则与其它 UIAF 工具互导时行为会不一致。
* */
enum class UIAFImportStrategy(
    val label: String,
    val description: String
) {
    AggressiveMerge(
        label = "合并(覆盖重复)",
        description = "保留本地独有记录;同一成就在导入数据中也存在时,以导入数据为准"
    ),

    LazyMerge(
        label = "合并(保留本地)",
        description = "只补充本地没有的成就;已存在的成就一律不动"
    ),

    Overwrite(
        label = "完全覆盖",
        description = "先清空该用户的全部成就记录,再写入导入数据(本地独有记录会丢失)"
    );

    companion object {
        //默认选中项:与原行为最接近,避免用户习惯突变
        val default = AggressiveMerge
    }

    /*
    * 写入时使用的 SQL 动词
    *
    * LazyMerge 用 INSERT OR IGNORE:主键(id + user_id)冲突时跳过,
    * 天然等价于"只补本地没有的",无需先查出已存在的 id 集合。
    * 另外两种用 INSERT OR REPLACE:冲突时整条替换。
    * */
    val sqlVerb: String
        get() = when (this) {
            AggressiveMerge -> "INSERT OR REPLACE"
            LazyMerge -> "INSERT OR IGNORE"
            Overwrite -> "INSERT OR REPLACE"
        }

    /*
    * 是否在写入前清空该用户的全部记录(Overwrite 独有)。
    * */
    val clearBeforeImport: Boolean
        get() = this == Overwrite
}
