package com.lianyi.paimonsnotebook.ui.screen.items.data

import com.lianyi.paimonsnotebook.common.web.hutao.genshin.avatar.AvatarData
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.conveter.RelicIconConverter
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.intrinsic.QualityType
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.monster.MonsterData
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.reliquary.ReliquarySetData
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.weapon.WeaponData

data class ItemListCardData(
    val name: String = "",
    val iconUrl: String = "",
    val quality: Int = 0,
    val description: String = ""
) {
    val qualityBgResId:Int
        get() = QualityType.getQualityBgByType(quality)

    companion object {
        fun fromAvatar(avatar: AvatarData) =
            ItemListCardData(
                name = avatar.name,
                iconUrl = avatar.iconUrl,
                quality = avatar.quality,
                description = avatar.description
            )

        fun fromWeapon(weapon: WeaponData) =
            ItemListCardData(
                name = weapon.name,
                iconUrl = weapon.iconUrl,
                quality = weapon.rankLevel,
                description = weapon.description
            )

        /*
        * 怪物:MonsterData **没有星级字段**,quality 传 0。
        *
        * 0 会走 QualityType 的 QUALITY_NONE 分支回落成 quality_1 底色 ——
        * 这是有意为之:宁可用最低底色,也不编造星级。
        * */
        fun fromMonster(monster: MonsterData) =
            ItemListCardData(
                name = monster.name,
                iconUrl = monster.iconUrl,
                quality = 0,
                description = monster.title
            )

        /*
        * 圣遗物套装:quality 用套装最高星级(由 ReliquaryService 反查)。
        * 取不到时传 0,同样回落成最低底色而非编造。
        * */
        fun fromReliquarySet(set: ReliquarySetData, maxStar: Int) =
            ItemListCardData(
                name = set.Name,
                iconUrl = RelicIconConverter.iconNameToUrl(set.Icon),
                quality = maxStar,
                description = set.Descriptions.firstOrNull().orEmpty()
            )
    }
}
