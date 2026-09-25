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
import com.lianyi.paimonsnotebook.ui.screen.announcement.view.AnnouncementScreen
import com.lianyi.paimonsnotebook.ui.screen.combat.view.CombatRecordScreen
import com.lianyi.paimonsnotebook.ui.screen.achievement.view.AchievementScreen
import com.lianyi.paimonsnotebook.ui.screen.app_widget.view.AppWidgetScreen
import com.lianyi.paimonsnotebook.ui.screen.daily_note.view.DailyNoteScreen
import com.lianyi.paimonsnotebook.ui.screen.furniture.view.FurnitureScreen
import com.lianyi.paimonsnotebook.ui.screen.gacha.view.GachaRecordScreen
import com.lianyi.paimonsnotebook.ui.screen.grow.view.GrowScreen
import com.lianyi.paimonsnotebook.ui.screen.home.data.HomeCustomDrawerData
import com.lianyi.paimonsnotebook.ui.screen.home.data.ModalItemData
import com.lianyi.paimonsnotebook.ui.screen.wiki.view.WikiScreen
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
            name = "资料库",
            icon = R.drawable.ic_genshin_game_character_card,
            target = WikiScreen::class.java,
            sortIndex = 40,
            requireMetadata = true
        ),
        ModalItemData(
            name = "养成素材",
            icon = R.drawable.ic_genshin_game_material,
            target = GrowScreen::class.java,
            sortIndex = 70,
            requireMetadata = true
        ),
        ModalItemData(
            name = "战斗记录",
            icon = R.drawable.ic_histogram,
            target = CombatRecordScreen::class.java,
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
            name = "游戏公告",
            icon = R.drawable.ic_channel,
            target = AnnouncementScreen::class.java,
            sortIndex = 96
        ),
        ModalItemData(
            name = "洞天摹本",
            icon = R.drawable.ic_page_view,
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

    //获取显示的侧边栏数据(按 sortIndex 升序,理由见 getAllModalItemData)
    fun getShowModalItemData(enableMetadata: Boolean) =
        modalItemData.filter { it.requireMetadata == enableMetadata || enableMetadata }
            .sortedBy { it.sortIndex }

    /*
    * 某个功能在当前配置下是否可用。
    *
    * 需要元数据的功能在未启用元数据时不可用 —— 但**仍然要显示出来**:
    * 原先侧边栏直接把这类项过滤掉(18 项只剩 7 项),用户既看不到功能、
    * 也无从知道它们是被"元数据未启用"藏起来的,只会以为应用缺功能。
    * 现在改为全部展示,不可用的置灰并提示如何启用。
    * */
    fun isModalItemEnabled(item: ModalItemData, enableMetadata: Boolean) =
        !item.requireMetadata || enableMetadata

    /*
    * 侧边栏要展示的全部功能项(不过滤),按 sortIndex 升序。
    *
    * 与 getShowModalItemData 的区别:后者会按元数据开关**过滤掉**不可用项,
    * 目前仍被桌面组件与快捷方式列表使用(那里的语义是"只能挑能用的",
    * 保持不变);而侧边栏需要完整展示以便用户发现功能。
    *
    * ⚠️ 必须显式排序:此前 sortIndex 声明了却**从未参与排序**,列表直接沿用
    *    声明顺序,而新功能一律追加在数组末尾 —— 于是"旅行者札记(95)、
    *    战斗记录(92)、游戏公告(96)、洞天摹本(97)、队伍DPS(98)"这五项
    *    全部排在"桌面组件(110)"之后,顺序与 sortIndex 所表达的设计意图不符,
    *    sortIndex 形同死字段。此处统一排序,既修正顺序也让该字段真正生效。
    * */
    fun getAllModalItemData() = modalItemData.sortedBy { it.sortIndex }

    private val ModalItemsStateFlow = MutableStateFlow<List<ModalItemData>>(listOf())

    //对外开放的modalItems流
    val modalItemsFlow = ModalItemsStateFlow.asStateFlow()

    /*
    * 当前元数据是否可用,供侧边栏判断哪些项要置灰。
    *
    * 侧边栏渲染需要"完整列表 + 每项是否可用"两份信息,而 modalItemsFlow
    * 只承载列表,故可用性单独用一个流表达。
    * */
    private val MetadataEnabledStateFlow = MutableStateFlow(true)

    val metadataEnabledFlow = MetadataEnabledStateFlow.asStateFlow()

    //更新侧边栏流
    suspend fun updateShowModalItemData(
        enableMetadata: Boolean,
        customDrawerListJson: String,
        enableCustomDrawer: Boolean
    ) {
        //元数据开关与列表在同一处更新,避免两者不同步导致置灰状态错乱
        MetadataEnabledStateFlow.value = enableMetadata

        if (enableCustomDrawer) {
            /*
            * 自定义侧边栏:按用户配置的显隐与顺序展示,但**不再按元数据过滤**。
            * 不可用项保留在列表里(置灰展示),否则用户会看到自己配置过的功能
            * 莫名其妙消失,且无从得知原因。
            *
            * ⚠️ 必须做旧类名迁移:自定义侧边栏以**类名**持久化用户配置。
            *    1.8.24 把 6 个功能合并掉了(角色/武器/怪物/圣遗物资料 ->
            *    资料库;养成计划+素材日历 -> 养成素材;深境螺旋+战斗记录 ->
            *    战斗记录),老配置里存的还是旧类名。若直接 mapNotNull,
            *    这些项会被**静默丢弃** —— 用户会发现自定义侧边栏"少了好几个
            *    功能"却毫无提示,正是本项目反复出现的失效模式。
            * */
            val migrated = getCustomDrawerListFromJson(json = customDrawerListJson)
                .filterNot { it.disable }
                .map { it.copy(targetClass = migrateLegacyDrawerTarget(it.targetClass)) }

            /*
            * 迁移后可能出现重复(用户原来同时启用了"角色资料"和"武器资料",
            * 现在都指向资料库),去重并按原顺序保留第一次出现的位置。
            * */
            val list = migrated
                .distinctBy { it.targetClass }
                .mapNotNull { modalItemMap[it.targetClass] }

            ModalItemsStateFlow.emit(list)
        } else {
            ModalItemsStateFlow.emit(getAllModalItemData())
        }
    }

    /*
    * 把 1.8.24 合并前的旧侧边栏类名映射到合并后的入口。
    *
    * 实现见 DrawerTargetMigration(抽成纯函数以便单测 —— 这类映射遗漏
    * 编译期不报错,只能靠用例钉住)。
    *
    * 对"侧边栏功能管理"页同样必须调用:那里用 getModelItemByClassName 取
    * 名称与图标,查不到就 `?: return@itemsIndexed` **整行不渲染** ——
    * 用户会看到列表里莫名少了几行,却没有任何说明。
    * */
    fun migrateLegacyDrawerTarget(className: String): String =
        DrawerTargetMigration.migrate(className)

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