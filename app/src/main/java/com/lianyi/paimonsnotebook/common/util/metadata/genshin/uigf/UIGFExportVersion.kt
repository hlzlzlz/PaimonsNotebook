package com.lianyi.paimonsnotebook.common.util.metadata.genshin.uigf

/*
* 导出时可选的 UIGF 版本
*
* UIGF v4.x 与 v3.0 是**两套互不兼容的结构**(v3 是 info + 顶层 list 单 uid,
* v4 是 info + hk4e[] 多 uid),所以导出必须按版本分流,
* 不能像原来那样只用一个 Boolean 表达"是不是 v3"。
*
* 保留 v3.0 是因为它兼容老工具;v4.0 兼容性最好,故作为默认值。
* */
sealed class UIGFExportVersion(
    val label: String,
    val description: String,
    //v3.0 为 true;v4.x 为 false
    val isLegacyV3: Boolean,
    //v4.x 对应的版本;v3.0 为 null
    val uigfVersion: UIGFHelper.UIGFVersion?
) {

    //UIGF v3.0(旧标准,只能导出单个 uid)
    data object V3_0 : UIGFExportVersion(
        label = "UIGF v3.0",
        description = "旧标准,单 uid,仅用于兼容未支持 v4 的工具",
        isLegacyV3 = true,
        uigfVersion = null
    )

    //v4.x 各版本
    data class V4(val version: UIGFHelper.UIGFVersion) : UIGFExportVersion(
        label = version.label,
        description = version.description,
        isLegacyV3 = false,
        uigfVersion = version
    )

    //持久化用的字符串("v3.0"/"v4.0"/"v4.1"/"v4.2")
    val storageValue: String
        get() = uigfVersion?.value ?: "v3.0"

    companion object {
        val default: UIGFExportVersion = V4(UIGFHelper.UIGFVersion.default)

        //全部可选值,供设置界面列出
        val all: List<UIGFExportVersion> = listOf(
            V4(UIGFHelper.UIGFVersion.V4_0),
            V4(UIGFHelper.UIGFVersion.V4_1),
            V4(UIGFHelper.UIGFVersion.V4_2),
            V3_0
        )

        /*
        * 从持久化的设置值还原
        *
        * 历史值有 "v3.0"/"v4.0"/"v4.1"/"v4.2";此前该设置是一个 Boolean
        * (GachaRecordExportToUIGFV3),迁移后仍读同一个键的字符串形式,
        * 未知值一律回退到默认,避免脏数据导致导出出错。
        * */
        fun fromValue(value: String?): UIGFExportVersion = when (value) {
            "v3.0" -> V3_0
            "v4.0" -> V4(UIGFHelper.UIGFVersion.V4_0)
            "v4.1" -> V4(UIGFHelper.UIGFVersion.V4_1)
            "v4.2" -> V4(UIGFHelper.UIGFVersion.V4_2)
            else -> default
        }
    }
}
