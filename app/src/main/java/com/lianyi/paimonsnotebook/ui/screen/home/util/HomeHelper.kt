package com.lianyi.paimonsnotebook.ui.screen.home.util

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.ActivityResultLauncher
import com.lianyi.paimonsnotebook.R
import com.lianyi.paimonsnotebook.common.application.PaimonsNotebookApplication
import com.lianyi.paimonsnotebook.common.util.json.JSON
import com.lianyi.paimonsnotebook.common.util.parameter.getParameterizedType
import com.lianyi.paimonsnotebook.ui.screen.abyss.view.AbyssScreen
import com.lianyi.paimonsnotebook.ui.screen.announcement.view.AnnouncementScreen
import com.lianyi.paimonsnotebook.ui.screen.role_combat.view.RoleCombatScreen
import com.lianyi.paimonsnotebook.ui.screen.travelers_diary.view.TravelersDiaryScreen
import com.lianyi.paimonsnotebook.ui.screen.achievement.view.AchievementScreen
import com.lianyi.paimonsnotebook.ui.screen.app_widget.view.AppWidgetScreen
import com.lianyi.paimonsnotebook.ui.screen.cultivate_project.view.CultivateProjectScreen
import com.lianyi.paimonsnotebook.ui.screen.daily_note.view.DailyNoteScreen
import com.lianyi.paimonsnotebook.ui.screen.furniture.view.FurnitureScreen
import com.lianyi.paimonsnotebook.ui.screen.gacha.view.GachaRecordScreen
import com.lianyi.paimonsnotebook.ui.screen.home.data.HomeCustomDrawerData
import com.lianyi.paimonsnotebook.ui.screen.home.data.ModalItemData
import com.lianyi.paimonsnotebook.ui.screen.items.view.AvatarScreen
import com.lianyi.paimonsnotebook.ui.screen.items.view.MonsterScreen
import com.lianyi.paimonsnotebook.ui.screen.items.view.ReliquaryScreen
import com.lianyi.paimonsnotebook.ui.screen.items.view.WeaponScreen
import com.lianyi.paimonsnotebook.ui.screen.weekly.view.WeeklyCalendarScreen
import com.lianyi.paimonsnotebook.ui.screen.sign_in_status.view.SignInStatusScreen
import com.lianyi.paimonsnotebook.ui.screen.player_character.view.PlayerCharacterScreen
import com.lianyi.paimonsnotebook.ui.screen.dps.view.DpsCalculatorScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object HomeHelper {
    private val context by lazy {
        PaimonsNotebookApplication.context
    }

    val modalItemMap by lazy {
        modalItemData.associateBy { it.target.name }
    }

    //侧边栏所有功能
    private val modalItemData = listOf(
        ModalItemData(
            name = "实时便笺",
            icon = R.drawable.ic_moon,
            target = DailyNoteScreen::class.java,
            sortIndex = 10
        ),
        ModalItemData(
            name = "签到记录",
            icon = R.drawable.ic_gift,
            target = SignInStatusScreen::class.java,
            sortIndex = 15
        ),
        ModalItemData(
            name = "我的角色",
            icon = R.drawable.ic_genshin_game_sign_character,
            target = PlayerCharacterScreen::class.java,
            sortIndex = 20,
            requireMetadata = true
        ),
        ModalItemData(
            name = "祈愿记录",
            icon = R.drawable.ic_genshin_game_wish,
            target = GachaRecordScreen::class.java,
            sortIndex = 30,
            requireMetadata = true
        ),
        ModalItemData(
            name = "角色资料",
            icon = R.drawable.ic_genshin_game_character_card,
            target = AvatarScreen::class.java,
            sortIndex = 40,
            requireMetadata = true
        ),
        ModalItemData(
            name = "武器资料",
            icon = R.drawable.ic_genshin_game_equip,
            target = WeaponScreen::class.java,
            sortIndex = 50,
            requireMetadata = true
        ),
        ModalItemData(
            name = "怪物资料",
            icon = R.drawable.ic_genshin_game_biology,
            target = MonsterScreen::class.java,
            sortIndex = 55,
            requireMetadata = true
        ),
        ModalItemData(
            name = "圣遗物资料",
            icon = R.drawable.ic_genshin_game_sign_artifacts,
            target = ReliquaryScreen::class.java,
            sortIndex = 60,
            requireMetadata = true
        ),
        ModalItemData(
            name = "养成计划",
            icon = R.drawable.ic_genshin_game_ggc_book,
            target = CultivateProjectScreen::class.java,
            sortIndex = 70,
            requireMetadata = true
        ),
        ModalItemData(
            name = "素材日历",
            icon = R.drawable.ic_genshin_game_material,
            target = WeeklyCalendarScreen::class.java,
            sortIndex = 80,
            requireMetadata = true
        ),
        ModalItemData(
            name = "深境螺旋",
            icon = R.drawable.ic_genshin_game_spiral_abyss,
            target = AbyssScreen::class.java,
            sortIndex = 90,
            requireMetadata = true
        ),
        ModalItemData(
            name = "成就管理",
            icon = R.drawable.ic_genshin_game_sign_cup,
            target = AchievementScreen::class.java,
            sortIndex = 100,
            requireMetadata = true
        ),
        ModalItemData(
            name = "桌面组件",
            icon = R.drawable.ic_appwidget,
            target = AppWidgetScreen::class.java,
            sortIndex = 110
        ),
        ModalItemData(
            name = "旅行者札记",
            icon = R.drawable.ic_genshin_game_ggc_book,
            target = TravelersDiaryScreen::class.java,
            sortIndex = 95
        ),
        ModalItemData(
            name = "战斗记录",
            icon = R.drawable.ic_genshin_game_spiral_abyss,
            target = RoleCombatScreen::class.java,
            sortIndex = 92
        ),
        ModalItemData(
            name = "游戏公告",
            icon = R.drawable.ic_genshin_game_ggc_book,
            target = AnnouncementScreen::class.java,
            sortIndex = 96
        ),
        ModalItemData(
            name = "洞天摹本",
            icon = R.drawable.ic_genshin_game_ggc_book,
            target = FurnitureScreen::class.java,
            sortIndex = 97
        ),
        ModalItemData(
            name = "队伍DPS",
            icon = R.drawable.ic_star_cup,
            target = DpsCalculatorScreen::class.java,
            sortIndex = 98,
            requireMetadata = true
        ),
    )

    //获取显示的侧边栏数据
    fun getShowModalItemData(enableMetadata: Boolean) =
        modalItemData.filter { it.requireMetadata == enableMetadata || enableMetadata }

    private val ModalItemsStateFlow = MutableStateFlow<List<ModalItemData>>(listOf())

    //对外开放的modalItems流
    val modalItemsFlow = ModalItemsStateFlow.asStateFlow()

    //更新侧边栏流
    suspend fun updateShowModalItemData(
        enableMetadata: Boolean,
        customDrawerListJson: String,
        enableCustomDrawer: Boolean
    ) {
        if (enableCustomDrawer) {
            //再次根据元数据启用状态筛选一次列表
            val list =
                getCustomDrawerListFromJson(json = customDrawerListJson).filterNot { it.disable }
                    .mapNotNull { modalItemMap[it.targetClass] }
            ModalItemsStateFlow.emit(list)
        } else {
            val items = getShowModalItemData(enableMetadata)
            ModalItemsStateFlow.emit(items)
        }
    }

    fun getCustomDrawerListFromJson(json: String) =
        JSON.parse<List<HomeCustomDrawerData>>(
            json = json,
            type = getParameterizedType(List::class.java, HomeCustomDrawerData::class.java)
        )

    fun <T : Activity> goActivity(
        cls: Class<T>,
        bundle: Bundle = Bundle(),
        flags: Int = Intent.FLAG_ACTIVITY_NEW_TASK
    ) {
        context.apply {
            startActivity(Intent(
                this,
                cls
            ).apply {
                putExtras(bundle)
                addFlags(flags)
            })
        }
    }

    fun goActivityByIntentNewTask(block: Intent.() -> Unit) = goActivityByIntent {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        block.invoke(this)
    }

    @SuppressLint("IntentWithNullActionLaunch")
    fun goActivityByIntent(block: Intent.() -> Unit) {
        val intent = Intent().apply(block)
        context.startActivity(intent)
    }


    fun goActivityForResultByIntent(
        launcher: ActivityResultLauncher<Intent>,
        block: Intent.() -> Unit
    ) {
        val intent = Intent().apply(block)
        launcher.launch(intent)
    }
}