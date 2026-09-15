package com.lianyi.paimonsnotebook.ui.screen.player_character.view

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import com.lianyi.paimonsnotebook.R
import com.lianyi.paimonsnotebook.common.components.popup.IconTitleInformationPopupWindow
import com.lianyi.core.ui.components.text.InfoText
import com.lianyi.paimonsnotebook.common.components.dialog.LazyColumnDialog
import com.lianyi.paimonsnotebook.common.components.widget.TextSlider
import com.lianyi.paimonsnotebook.common.core.base.BaseActivity
import com.lianyi.paimonsnotebook.common.extension.modifier.radius.radius
import com.lianyi.paimonsnotebook.common.util.reliquary.ReliquaryScoreWeight
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.intrinsic.ElementType
import com.lianyi.paimonsnotebook.ui.screen.items.components.content.ItemScreenContent
import com.lianyi.paimonsnotebook.ui.screen.items.components.information.InformationItem
import com.lianyi.paimonsnotebook.ui.screen.items.components.state.ItemScreenLoadingState
import com.lianyi.paimonsnotebook.ui.screen.items.data.ItemListCardData
import com.lianyi.paimonsnotebook.ui.screen.player_character.components.card.PlayerCharacterListCard
import com.lianyi.paimonsnotebook.ui.screen.player_character.components.card.PlayerCharacterPropertyCard
import com.lianyi.paimonsnotebook.ui.screen.player_character.components.card.PlayerCharacterRelicCard
import com.lianyi.paimonsnotebook.ui.screen.player_character.components.card.item.PlayerCharacterDetailSkillCard
import com.lianyi.paimonsnotebook.ui.screen.player_character.components.card.item.PlayerCharacterDetailTalentCard
import com.lianyi.paimonsnotebook.ui.screen.player_character.components.card.item.PlayerCharacterDetailWeaponCard
import com.lianyi.paimonsnotebook.ui.screen.player_character.viewmodel.PlayerCharacterDetailScreenViewModel
import com.lianyi.paimonsnotebook.ui.theme.Black
import com.lianyi.paimonsnotebook.ui.theme.Black_60
import com.lianyi.paimonsnotebook.ui.theme.FetterColor
import com.lianyi.paimonsnotebook.ui.theme.PaimonsNotebookTheme
import com.lianyi.paimonsnotebook.ui.theme.Primary
import com.lianyi.paimonsnotebook.ui.theme.White
import com.lianyi.paimonsnotebook.ui.theme.White_40
import kotlin.math.roundToInt

class PlayerCharacterDetailScreen : BaseActivity() {

    companion object {
        //当前用户与uid
        const val PARAM_USER_AND_UID_JSON = "user_and_uid_json"

        //玩家角色的列表集合
        const val PARAM_CHARACTER_LIST_JSON = "character_list_json"

        const val PARAM_SELECTED_CHARACTER_ID = "selected_character_id"
    }

    private val viewModel by lazy {
        ViewModelProvider(this)[PlayerCharacterDetailScreenViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel.init(intent) {
            finish()
        }

        setContent {
            PaimonsNotebookTheme(this) {

                var showReliquaryWeightDialog by remember {
                    mutableStateOf(false)
                }

                ItemScreenLoadingState(loadingState = viewModel.loadingState) {

                    ItemScreenContent(
                        backgroundImgUrl = viewModel.currentItem!!.gachaAvatarImg,
                        listButtonText = "角色列表",
                        baseInfoName = viewModel.currentItem!!.name,
                        baseInfoStarCount = viewModel.currentItem!!.starCount,
                        baseInfoIconUrl = viewModel.currentItem!!.fetterInfo.associationIconUrl,
                        tabs = viewModel.tabs,
                        itemFilterViewModel = viewModel.itemFilterViewModel,
                        onClickListButton = viewModel::toggleFilterContent,
                        getListItemDataContent = viewModel::getItemDataContent,
                        showAddButton = false,
                        baseInfoSlot = {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Spacer(modifier = Modifier.width(1.dp))

                                InformationItem(
                                    text = "Lv.${viewModel.currentCharacterDetail?.base?.level}",
                                    backgroundColor = White_40,
                                    textSize = 14.sp,
                                    paddingValues = PaddingValues(
                                        6.dp,
                                        2.dp
                                    )
                                )

                                InformationItem(
                                    text = "${viewModel.currentCharacterDetail?.base?.fetter}",
                                    iconResId = R.drawable.icon_fetter,
                                    backgroundColor = White_40,
                                    textSize = 14.sp,
                                    paddingValues = PaddingValues(
                                        6.dp,
                                        2.dp
                                    ),
                                    tint = FetterColor,
                                    textColor = FetterColor
                                )
                            }
                        },
                        getItemListCardData = ItemListCardData::fromAvatar,
                        onClickListItemCard = viewModel::onClickItem,
                        onClickAddButton = viewModel::addCurrentItemToCultivateProject,
                        itemAddedCurrentCultivateProject = viewModel.itemAddedToCurrentCultivateProject,
                        verticalListCardContent = { avatarData, _ ->
                            val characterData = viewModel.getCharacterListDataById(avatarData.id)
                                ?: return@ItemScreenContent

                            PlayerCharacterListCard(
                                characterData = characterData,
                                getAvatarDataById = viewModel::getAvatarDataById,
                                getWeaponDataById = viewModel::getWeaponDataById,
                                getWeaponFightPropertyFormatList = viewModel::getWeaponFightPropertyFormatList,
                                onClick = {
                                    viewModel.toggleFilterContent()
                                    viewModel.onClickItem(avatarData)
                                }
                            )
                        },
                        cardContent = {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                InformationItem(
                                    iconResId = ElementType.getElementResourceIdByName(viewModel.currentItem!!.fetterInfo.VisionBefore),
                                    textColor = White,
                                    iconSize = 20.dp,
                                    textSize = 14.sp,
                                    text = viewModel.currentItem!!.fetterInfo.VisionBefore,
                                    backgroundColor = ElementType.getElementColorByName(viewModel.currentItem!!.fetterInfo.VisionBefore),
                                )

                                InformationItem(
                                    iconUrl = viewModel.currentItem!!.weaponIconUrl,
                                    textColor = White,
                                    iconSize = 20.dp,
                                    textSize = 14.sp,
                                    text = viewModel.currentItem!!.weaponTypeName,
                                    backgroundColor = ElementType.getElementColorByName(viewModel.currentItem!!.fetterInfo.VisionBefore),
                                )
                            }

                            val character by remember(viewModel.currentCharacterDetail?.base?.id) {
                                mutableStateOf(viewModel.currentCharacterDetail)
                            }
                            val avatarData = viewModel.getAvatarDataById(character?.base?.id ?: -1)

                            if (character != null && avatarData != null) {
                                PlayerCharacterDetailSkillCard(
                                    skillDepot = avatarData.skillDepot,
                                    elementTypeName = character!!.base.element,
                                    skillLevelMap = character!!.skills.associate {
                                        it.skill_id to it.level
                                    },
                                    backgroundColor = White_40,
                                )

                                PlayerCharacterDetailTalentCard(
                                    talents = avatarData.skillDepot.Talents,
                                    elementTypeName = character!!.base.element,
                                    activateCount = character!!.base.actived_constellation_num,
                                    backgroundColor = White_40,
                                    clickable = true
                                )
                            }

                            PlayerCharacterPropertyCard(
                                propertyList = viewModel.currentCharacterDetail?.selected_properties
                                    ?: listOf(),
                                extraPropertyList = viewModel.currentCharacterDetail?.let {
                                    it.base_properties + it.extra_properties + it.element_properties
                                } ?: listOf()
                            )

                            val weapon = viewModel.currentCharacterDetail?.weapon
                            val weaponData = viewModel.getWeaponDataById(weapon?.id ?: -1)

                            if (weaponData != null && weapon != null) {
                                val list = remember(weapon.id, weapon.level) {
                                    viewModel.getWeaponFightPropertyFormatList(
                                        weaponData = weaponData,
                                        level = weapon.level,
                                        promoted = true
                                    )
                                }

                                PlayerCharacterDetailWeaponCard(
                                    weaponData = weaponData,
                                    level = weapon.level,
                                    affixLevel = weapon.affix_level,
                                    weaponFightPropertyFormatList = list,
                                    backgroundColor = White_40,
                                    clickable = true
                                )
                            }

                            val relics = viewModel.currentCharacterDetail?.relics
                            val recommendRelicProperty =
                                viewModel.currentCharacterDetail?.recommend_relic_property

                            if (!relics.isNullOrEmpty() && recommendRelicProperty != null) {
                                PlayerCharacterRelicCard(
                                    relicList = relics,
                                    getRelicById = viewModel::getRelicById,
                                    recommendRelicProperty = recommendRelicProperty,
                                    onClickRelicIcon = viewModel::onClickRelicIcon,
                                    relicScoreMap = viewModel.getRelicScoreMap(relics),
                                    onWeightSettingClick = { showReliquaryWeightDialog = true }
                                )
                            }
                        }
                    )

                    if(viewModel.showReliquarySetInfoPopupWindow){
                        IconTitleInformationPopupWindow(
                            data = viewModel.reliquarySetInfoDataSet,
                            popupProvider = viewModel.reliquarySetInfoPopupWindowProvider,
                            onDismissRequest = viewModel::onPopupWindowDismissRequest
                        )
                    }

                    if (showReliquaryWeightDialog) {
                        ReliquaryScoreWeightDialog(
                            viewModel = viewModel,
                            onDismiss = { showReliquaryWeightDialog = false }
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun ReliquaryScoreWeightDialog(
        viewModel: PlayerCharacterDetailScreenViewModel,
        onDismiss: () -> Unit
    ) {
        val weightLabels = listOf(
            "暴击率" to viewModel.reliquaryScoreWeight.critRate,
            "暴击伤害" to viewModel.reliquaryScoreWeight.critDmg,
            "攻击%" to viewModel.reliquaryScoreWeight.atkPercent,
            "生命%" to viewModel.reliquaryScoreWeight.hpPercent,
            "防御%" to viewModel.reliquaryScoreWeight.defPercent,
            "充能效率" to viewModel.reliquaryScoreWeight.chargeEfficiency,
            "元素精通" to viewModel.reliquaryScoreWeight.elementMastery
        )

        val values = remember {
            mutableStateOf(weightLabels.associate { it.first to it.second.toFloat() })
        }

        var enableCustom by remember {
            mutableStateOf(viewModel.enableReliquaryScoreCustomWeight)
        }

        LazyColumnDialog(
            title = "圣遗物评分权重",
            buttons = arrayOf("取消", "保存"),
            onDismissRequest = onDismiss,
            onClickButton = { index ->
                if (index == 1) {
                    if (enableCustom) {
                        val weight = ReliquaryScoreWeight(
                            critRate = values.value.getValue("暴击率").toDouble(),
                            critDmg = values.value.getValue("暴击伤害").toDouble(),
                            atkPercent = values.value.getValue("攻击%").toDouble(),
                            hpPercent = values.value.getValue("生命%").toDouble(),
                            defPercent = values.value.getValue("防御%").toDouble(),
                            chargeEfficiency = values.value.getValue("充能效率").toDouble(),
                            elementMastery = values.value.getValue("元素精通").toDouble()
                        )

                        viewModel.saveReliquaryScoreWeight(weight, enableCustom)
                    } else {
                        viewModel.saveReliquaryScoreWeight(viewModel.reliquaryScoreWeight, false)
                    }
                }

                onDismiss.invoke()
            }
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("自动(推荐词条)" to false, "手动权重" to true).forEach { (label, mode) ->
                        Text(
                            text = label,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (enableCustom == mode) White else Black,
                            modifier = Modifier
                                .radius(4.dp)
                                .background(if (enableCustom == mode) Primary else White_40)
                                .clickable { enableCustom = mode }
                                .padding(8.dp, 4.dp)
                        )
                    }
                }
            }

            if (enableCustom) {
                item {
                    InfoText(text = "各项0以上,0表示该项不计分")
                }
            }

            if (enableCustom) {
                items(weightLabels.size) { index ->
                    val (label, _) = weightLabels[index]

                    TextSlider(
                        value = values.value.getValue(label),
                        onValueChange = { input ->
                            values.value = values.value.toMutableMap().apply {
                                put(label, (input * 10).roundToInt() / 10f)
                            }
                        },
                        text = { "%.1f".format(it) },
                        title = label,
                        range = 0f..2f
                    )
                }
            }
        }
    }
}