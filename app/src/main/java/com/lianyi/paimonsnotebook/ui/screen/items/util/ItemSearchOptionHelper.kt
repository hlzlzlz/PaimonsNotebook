package com.lianyi.paimonsnotebook.ui.screen.items.util

import androidx.compose.ui.unit.dp
import com.lianyi.paimonsnotebook.R
import com.lianyi.paimonsnotebook.common.util.enums.ListLayoutStyle
import com.lianyi.paimonsnotebook.common.util.enums.SortOrderBy
import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.game_record.character.CharacterListData
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.avatar.AvatarData
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.common.service.AvatarService
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.common.service.WeaponService
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.conveter.AssociationIconConverter
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.conveter.WeaponTypeIconConverter
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.intrinsic.AssociationType
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.intrinsic.ElementType
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.intrinsic.WeaponType
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.monster.MonsterData
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.reliquary.ReliquarySetData
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.weapon.WeaponData
import com.lianyi.paimonsnotebook.ui.screen.items.components.widget.StarGroup
import com.lianyi.paimonsnotebook.ui.screen.items.data.SearchOptionData
import com.lianyi.paimonsnotebook.ui.screen.items.viewmodel.filter.ItemFilterViewModel
import com.lianyi.paimonsnotebook.ui.theme.GachaStar5Color

object ItemSearchOptionHelper {
    private val list by lazy {
        mutableListOf<Pair<String, List<SearchOptionData>>>()
    }

    fun getPlayerCharacterItemFilterVieModel(
        list: List<AvatarData>,
        getCharacterListDataById: (Int) -> CharacterListData.CharacterData?
    ) = ItemFilterViewModel(
        items = list,
        searchOptionList = ItemSearchOptionHelper.apply {
            setOrderBy(
                options = listOf(
                    ItemFilterType.Default,
                    ItemFilterType.Level,
                    ItemFilterType.Fetter,
                    ItemFilterType.ActiveConstellation
                )
            )
            setStar()
            setWeapon()
            setElement()
            setAssociation()
        }.get(),
        getFilteredItemList = ItemSearchOptionHelper::filterAvatarList,
        itemSortCompareBy = { avatar, type ->

            val simpleCharacterListData =
                getCharacterListDataById.invoke(avatar.id) ?: return@ItemFilterViewModel 0L

            ItemContentFilterHelper.getPlayerGroupByKeyByType(
                type = type,
                characterListData = simpleCharacterListData
            )
        }
    )

    fun getAvatarItemFilterViewModel(
        avatarService: AvatarService
    ) = ItemFilterViewModel(
        items = avatarService.avatarList,
        searchOptionList = ItemSearchOptionHelper
            .apply {
                setListLayout()
                setOrderBy(
                    options = listOf(
                        ItemFilterType.Default,
                        ItemFilterType.BaseATK,
                        ItemFilterType.BaseHp,
                        ItemFilterType.BaseDef,
                        ItemFilterType.BirthDay,
                        ItemFilterType.CostumeCount
                    )
                )
                setStar()
                setWeapon()
                setElement()
                setAssociation()
            }.get(),
        getFilteredItemList = ItemSearchOptionHelper::filterAvatarList,
        itemSortCompareBy = { avatar, type ->
            ItemContentFilterHelper.getAvatarGroupByKeyByType(
                type = type,
                avatar = avatar,
                fightPropertyValueCalculateService = avatarService.fightPropertyValueCalculateService
            )
        }
    )


    fun getWeaponFilterItemViewModel(
        weaponService: WeaponService
    ) =
        ItemFilterViewModel(
            items = weaponService.weaponList,
            searchOptionList = ItemSearchOptionHelper
                .apply {
                    setListLayout()
                    setOrderBy(
                        options = listOf(
                            ItemFilterType.Default,
                            ItemFilterType.BaseATK,
                        )
                    )
                    setStar(starRange = 5 downTo 1)
                    setWeapon()
                }.get(),
            getFilteredItemList = ItemSearchOptionHelper::filterWeaponList,
            itemSortCompareBy = { weapon, type ->
                ItemContentFilterHelper.getWeaponGroupByKeyByType(
                    type,
                    weapon,
                    weaponService.fightPropertyValueCalculateService
                )
            }
        )

    /*
    * 怪物筛选
    *
    * ⚠️ 刻意只提供"列表布局 + 默认排序",不提供星级/元素/地区等选项 ——
    * MonsterData 里没有这些维度(没有星级字段;type 是未公开语义的 Int,
    * 不同版本的取值含义不明,凭它命名筛选条件等于编造)。能诚实支持的
    * 只有按名称/称号搜索,故选项就只给这些。
    * */
    fun getMonsterFilterItemViewModel(
        monsters: List<MonsterData>
    ) = ItemFilterViewModel(
        items = monsters,
        searchOptionList = ItemSearchOptionHelper.apply {
            setListLayout()
            setOrderBy(options = listOf(ItemFilterType.Default))
        }.get(),
        getFilteredItemList = ItemSearchOptionHelper::filterMonsterList,
        itemSortCompareBy = { monster, _ -> monster.id.toLong() }
    )

    /*
    * 圣遗物套装筛选
    *
    * 同样只给"列表布局 + 默认排序":ReliquarySetData 只有套装名、图标、
    * 件数与效果描述,没有星级/部位等可筛维度(星级要从 ReliquaryService
    * 的 starMap 反查,不适合做筛选键 —— 那会让排序键依赖另一个服务,
    * 与本页的筛选器生命周期不一致)。
    * */
    fun getReliquaryFilterItemViewModel(
        sets: List<ReliquarySetData>
    ) = ItemFilterViewModel(
        items = sets,
        searchOptionList = ItemSearchOptionHelper.apply {
            setListLayout()
            setOrderBy(options = listOf(ItemFilterType.Default))
        }.get(),
        getFilteredItemList = ItemSearchOptionHelper::filterReliquaryList,
        itemSortCompareBy = { set, _ -> set.SetId.toLong() }
    )


    //列表布局
    fun setListLayout() {
        list += "列表布局" to listOf(
            SearchOptionData(
                sortBy = ItemFilterType.ListLayout,
                name = "列表",
                value = ListLayoutStyle.ListVertical.ordinal,
                iconResId = R.drawable.ic_list
            ),
            SearchOptionData(
                sortBy = ItemFilterType.ListLayout,
                name = "网格",
                value = ListLayoutStyle.GridVertical.ordinal,
                iconResId = R.drawable.ic_grid
            )
        )
    }

    //排序列表
    private fun setOrderBy(
        options: List<ItemFilterType>
    ) {
        val orderBy = SearchOptionData(
            sortBy = ItemFilterType.Default,
            initOrderBy = SortOrderBy.Descend,
            value = ItemFilterType.Default.ordinal
        )

        list += "排序" to options.map {
            orderBy.copy(
                name = ItemContentFilterHelper.getSortTypeNameByType(it),
                value = it.ordinal
            )
        }
    }

    //星级
    private fun setStar(starRange: IntProgression = 5 downTo 4) {
        list += "星级" to starRange.map {
            SearchOptionData(sortBy = ItemFilterType.Star, value = it, contentSlot = {
                StarGroup(
                    starCount = it,
                    starTint = GachaStar5Color,
                    starSize = 18.dp
                )
            })
        }
    }

    //武器类型
    private fun setWeapon() {
        list += "武器" to listOf(
            WeaponType.WEAPON_SWORD_ONE_HAND,
            WeaponType.WEAPON_CLAYMORE,
            WeaponType.WEAPON_BOW,
            WeaponType.WEAPON_POLE,
            WeaponType.WEAPON_CATALYST
        ).map {
            SearchOptionData(
                sortBy = ItemFilterType.Weapon,
                iconUrl = WeaponTypeIconConverter.weaponTypeToIconUrl(it),
                name = WeaponType.getWeaponTypeName(it),
                value = it
            )
        }
    }

    //元素类型
    private fun setElement() {
        list += "元素" to listOf(
            ElementType.Fire,
            ElementType.Water,
            ElementType.Grass,
            ElementType.Electric,
            ElementType.Ice,
            ElementType.Wind,
            ElementType.Rock
        ).map {
            SearchOptionData(
                sortBy = ItemFilterType.Element,
                name = ElementType.getElementNameByType(it),
                iconResId = ElementType.getElementResourceIdByType(it),
                value = it
            )
        }
    }

    //地区
    private fun setAssociation() {
        list += "地区" to listOf(
            AssociationType.ASSOC_TYPE_MONDSTADT,
            AssociationType.ASSOC_TYPE_LIYUE,
            AssociationType.ASSOC_TYPE_INAZUMA,
            AssociationType.ASSOC_TYPE_SUMERU,
            AssociationType.ASSOC_TYPE_FONTAINE,
            AssociationType.ASSOC_TYPE_FATUI,
            AssociationType.ASSOC_TYPE_NATLAN
        ).map {
            SearchOptionData(
                sortBy = ItemFilterType.Association,
                name = AssociationType.getAssociationNameByType(it),
                iconUrl = AssociationIconConverter.avatarAssociationToUrl(it),
                value = it
            )
        }
    }

    fun get(): List<Pair<String, List<SearchOptionData>>> {
        val result = list.toList()
        list.clear()
        return result
    }


    //过滤武器列表
    private fun filterWeaponList(
        itemFilterViewModel: ItemFilterViewModel<WeaponData>,
        items: List<WeaponData>
    ): List<WeaponData> {
        val list = mutableListOf<WeaponData>()

        items.forEach { weaponData ->

            itemFilterViewModel.apply {

                if (!filterValueExists(ItemFilterType.Weapon, weaponData.weaponType)) return@forEach

                if (!filterValueExists(ItemFilterType.Star, weaponData.rankLevel)) return@forEach

                //匹配名称与称号
                if (inputNameValue.isNotEmpty() &&
                    weaponData.name.indexOf(inputNameValue) == -1
                ) {
                    return@forEach
                }
            }

            list += weaponData
        }

        return list
    }

    //过滤角色列表
    private fun filterAvatarList(        itemFilterViewModel: ItemFilterViewModel<AvatarData>,
        items: List<AvatarData>
    ): List<AvatarData> {
        val list = mutableListOf<AvatarData>()

        items.forEach { avatarData ->

            val fetterInfo = avatarData.fetterInfo

            itemFilterViewModel.apply {

                if (!filterValueExists(
                        ItemFilterType.Association,
                        fetterInfo.Association
                    )
                ) return@forEach

                if (!filterValueExists(ItemFilterType.Weapon, avatarData.weapon)) return@forEach

                val elementType = ElementType.getElementTypeByName(fetterInfo.VisionBefore)
                if (!filterValueExists(ItemFilterType.Element, elementType)) return@forEach

                if (!filterValueExists(ItemFilterType.Star, avatarData.starCount)) return@forEach

                //匹配名称与称号
                if (inputNameValue.isNotEmpty() &&
                    avatarData.name.indexOf(inputNameValue) == -1 &&
                    fetterInfo.Title.indexOf(inputNameValue) == -1
                ) {
                    return@forEach
                }
            }

            list += avatarData
        }

        return list
    }

    /*
    * 过滤怪物列表
    *
    * 只按名称与称号做子串匹配 —— 与 MonsterScreen 原实现一致(原页面的
    * 搜索框也只看 name)。加入 title 是因为同名变种会被去重折叠,
    * 称号是区分它们的唯一线索。
    * */
    private fun filterMonsterList(
        itemFilterViewModel: ItemFilterViewModel<MonsterData>,
        items: List<MonsterData>
    ): List<MonsterData> {
        val list = mutableListOf<MonsterData>()

        val keyword = itemFilterViewModel.inputNameValue

        items.forEach { monster ->
            if (keyword.isNotEmpty() &&
                monster.name.indexOf(keyword) == -1 &&
                monster.title.indexOf(keyword) == -1
            ) {
                return@forEach
            }

            list += monster
        }

        return list
    }

    //过滤圣遗物套装列表(按套装名)
    private fun filterReliquaryList(
        itemFilterViewModel: ItemFilterViewModel<ReliquarySetData>,
        items: List<ReliquarySetData>
    ): List<ReliquarySetData> {
        val list = mutableListOf<ReliquarySetData>()

        val keyword = itemFilterViewModel.inputNameValue

        items.forEach { set ->
            if (keyword.isNotEmpty() && set.Name.indexOf(keyword) == -1) {
                return@forEach
            }

            list += set
        }

        return list
    }

}