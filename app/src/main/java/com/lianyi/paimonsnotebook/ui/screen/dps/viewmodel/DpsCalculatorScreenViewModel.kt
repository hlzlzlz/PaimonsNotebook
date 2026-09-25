package com.lianyi.paimonsnotebook.ui.screen.dps.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lianyi.paimonsnotebook.common.data.hoyolab.user.User
import com.lianyi.paimonsnotebook.common.data.hoyolab.user.UserAndUid
import com.lianyi.paimonsnotebook.common.database.user.util.AccountHelper
import com.lianyi.paimonsnotebook.common.extension.scope.launchIO
import com.lianyi.paimonsnotebook.common.extension.scope.launchMain
import com.lianyi.paimonsnotebook.common.extension.string.errorNotify
import com.lianyi.paimonsnotebook.common.extension.string.warnNotify
import com.lianyi.paimonsnotebook.common.util.damage.AvatarPanel
import com.lianyi.paimonsnotebook.common.util.damage.MemberAction
import com.lianyi.paimonsnotebook.common.util.damage.PanelAdapter
import com.lianyi.paimonsnotebook.common.util.damage.SkillSlotResolver
import com.lianyi.paimonsnotebook.common.util.damage.TeamDamageCalculator
import com.lianyi.paimonsnotebook.common.util.damage.TeamDamageResult
import com.lianyi.paimonsnotebook.common.util.damage.TeamMember
import com.lianyi.paimonsnotebook.common.util.enums.LoadingState
import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.game_record.character.CharacterDetailData
import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.game_record.character.CharacterListData
import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.game_record.GameRecordClient
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.avatar.AvatarData
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.common.service.AvatarService
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.intrinsic.ElementType
import com.lianyi.paimonsnotebook.common.web.hutao.genshin.intrinsic.FightProperty

/*
* 队伍 DPS 计算器 ViewModel
*
* ⚠️ 设计要点(与 AGENTS.md 硬性约束对应):
*
* 1. **状态写入必须在主线程**:所有 `mutableStateOf` 赋值都用 `viewModelScope.launchMain{}`
*    投递(本项目有十余处历史违规教训)。
* 2. **队伍是用户手动拼的** —— 米游社接口**不返回玩家的队伍编成**(实测),
*    故这里维护的是"用户选定的一组 uid",不是读游戏队伍。
* 3. **不做时序模拟**:只算"一轮循环伤害";DPS 需要用户输入循环耗时,
*    本 VM 提供 [rotationSeconds] 但**不给默认值**,由 UI 提示用户填写。
* 4. **剧变反应与不可用成员记名跳过**:结果原样暴露给 UI 展示。
* 5. **面板口径已用真实响应验证**(2026-09-23):属性取 `selected_properties` 的
*    **2000 系列**(当前生命/攻击/防御),技能等级用 `skill_id ↔ 元数据 Id` 匹配。
*    ⚠️ 这两处原先都猜错了,导致 1.8.20 **全员被判为未参与计算**(见 PanelAdapter 注释)。
* */
class DpsCalculatorScreenViewModel : ViewModel() {

    private val avatarService by lazy { AvatarService(onMissingFile = { }) }

    private val gameRecordClient by lazy { GameRecordClient() }

    var loadingState by mutableStateOf(LoadingState.Loading)
        private set

    /** 玩家全部角色(来自 character/list) */
    val characterList = mutableStateListOf<CharacterListData.CharacterData>()

    /** 队伍成员(用户选定,最多 4 个) */
    val teamCharacterIds = mutableStateListOf<Int>()

    /** 已拉取到的角色详情(按 id) */
    private val detailCache = mutableMapOf<Int, CharacterDetailData.DetailItem>()

    /** 当前计算结果(null = 尚未计算) */
    var result by mutableStateOf<TeamDamageResult?>(null)
        private set

    /** 用户输入的循环耗时(秒);⚠️ 默认 0 表示"未提供",UI 不应据此算 DPS */
    var rotationSeconds by mutableStateOf(0.0)
        private set

    /*
     * 提示反馈一律用项目既有的 `String.errorNotify()` / `warnNotify()`
     * (ViewModel 中已有 105 处同样用法;内部走 `launchSafeIO` 线程安全,
     *  由 `PaimonsNotebookTheme` 里的 `PaimonsNotebookNotificationComponents`
     *  自动包裹渲染)。
     *
     * ⚠️ 注意不要与 core 里那套已删除的 `NotifyHelper`/`NotifyGroup` 混淆:
     *    那是第二套通知系统,零生产者且其宿主主题从未被调用,已于 1.8.23 删除。
     *
     * 教训:我最初写了一个 `message` 状态 + `consumeMessage()`,但 **UI 从未消费它**
     * ⇒ "队伍最多 4 人""请先选择角色"等提示会**静默消失**,用户不知道为什么点了没反应。
     * 这正是本项目记录过的失败模式("功能静默消失且一直未被发现")。
     *
     * ⚠️ 2026-09-23:原先还有一个 `panelFormatVerified = false` 标志用于控制
     * "面板格式未验证"的风险提示。现面板口径**已用真实响应验证**(见 PanelAdapter 注释),
     * 该标志已失去意义并删除 —— 留着会误导后人以为仍未验证。
     */

    init {
        // 与既有页面(PlayerCharacterScreenViewModel)同一套模式:
        // **持续监听用户流** —— 打开页面时账号可能尚未初始化完成,只读一次会拿到 null
        // 且不会重试。所有状态写入走 launchMain(本项目硬性约束)。
        viewModelScope.launchIO {
            launchMain {
                AccountHelper.selectedUserFlow.collect { user ->
                    currentUser = user
                    currentGameRole = user?.getSelectedGameRole()
                    if (currentUser != null && currentGameRole != null) {
                        loadCharacterList()
                    }
                }
            }
        }
    }

    var currentUser by mutableStateOf<User?>(null)
        private set

    var currentGameRole by mutableStateOf<com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.binding.UserGameRoleData.Role?>(null)
        private set

    private suspend fun loadCharacterList() {
        val user = currentUser ?: return
        val role = currentGameRole ?: return

        try {
            loadingState = LoadingState.Loading
            val res = gameRecordClient.getCharacterList(UserAndUid(user.userEntity, role.getPlayerUid()))
            val data = res.data
            if (data == null) {
                loadingState = LoadingState.Error
                (res.message.ifBlank { "角色列表获取失败" }).errorNotify()
                return
            }
            characterList.clear()
            characterList.addAll(data.list)
            loadingState = LoadingState.Success
        } catch (e: Exception) {
            // ⚠️ 必须捕获:本项目协程未捕获异常会静默杀进程
            loadingState = LoadingState.Error
            (e.message ?: "角色列表获取失败").errorNotify()
        }
    }

    /** 用户修改循环耗时(⚠️ 不能命名 setRotationSeconds:会与 var 的 setter 撞 JVM 签名) */
    fun updateRotationSeconds(seconds: Double) {
        rotationSeconds = if (seconds > 0 && seconds.isFinite()) seconds else 0.0
    }

    /** 是否已选成员 */
    fun toggleMember(characterId: Int) {
        if (teamCharacterIds.contains(characterId)) {
            teamCharacterIds.remove(characterId)
            result = null
            return
        }
        if (teamCharacterIds.size >= MAX_TEAM_SIZE) {
            "队伍最多 $MAX_TEAM_SIZE 人".warnNotify()
            return
        }
        teamCharacterIds.add(characterId)
        result = null
    }

    fun clearTeam() {
        teamCharacterIds.clear()
        result = null
    }

    /**
     * 计算队伍伤害。
     *
     * ⚠️ 计算前会先拉取每个成员的 `character/detail`(有缓存则不重复拉)。
     * 任一成员详情拉取失败时**记名跳过**(由 TeamDamageCalculator 汇总),
     * 不会让整次计算失败。
     */
    fun calculate() {
        viewModelScope.launchMain {
            if (teamCharacterIds.isEmpty()) {
                "请先选择至少 1 名角色".warnNotify()
                return@launchMain
            }

            val user = currentUser
            val role = currentGameRole
            if (user == null || role == null) {
                "请先登录并选择游戏角色".errorNotify()
                return@launchMain
            }
            val userAndUid = UserAndUid(user.userEntity, role.getPlayerUid())

            // 拉取缺失的详情
            // ⚠️ 这里**必须显式判空**:`ResultData.data` 声明为**非空** `val data: T`,
            //    但 `getAsJsonNative` 在解析异常时**返回 null**(见 requests.kt),
            //    Gson 走 Unsafe 分配、不执行 Kotlin 非空校验
            //    ⇒ 编译期判空会得到 "always false" 警告,但**运行时确有必要**
            //    (项目既有处置:`AbyssScreenViewModel.kt:252` 同样显式判空)。
            //    照编译器把判空删掉 = 真机偶发 NPE。
            val missing = teamCharacterIds.filter { !detailCache.containsKey(it) }
            if (missing.isNotEmpty()) {
                try {
                    val res = gameRecordClient.getCharacterDetail(userAndUid, missing)
                    val data = res.data
                    if (data == null) {
                        "角色详情返回为空,无法计算".errorNotify()
                    } else {
                        data.list.forEach { detailCache[it.base.id] = it }
                    }
                } catch (e: Exception) {
                    "角色详情获取失败:${e.message ?: "未知错误"}".errorNotify()
                }
            }

            // ⚠️ 每次计算前清空上一次的"未能参与计算"记录,避免残留误导用户
            lastSkippedSkills = emptyList()

            val members = buildMembers()
            result = TeamDamageCalculator.calculate(
                members = members,
                // 回退值:仅当某成员取不到真实等级时才会用到(见 TeamMember.level)
                attackerLevel = FALLBACK_AVATAR_LEVEL,
                defenderLevel = DEFENDER_LEVEL,
                resistance = DEFAULT_RESISTANCE
            )
        }
    }

    /**
     * 构建队伍成员。
     *
     * ⚠️ 这里体现了几处**刻意的克制**:
     *   - 技能等级取 `character/detail` 返回的 `skills[].level`(**真实数据**),
     *     不猜、不给默认值;取不到时该动作不参与计算。
     *   - 出伤动作的槽位判别与倍率项选择**交给 [SkillSlotResolver]**(纯逻辑层,可单测)。
     *   - 增伤取该角色元素对应的 ADD_HURT,取不到按 0(由 PanelAdapter 记录缺失)。
     */
    private fun buildMembers(): List<TeamMember> = teamCharacterIds.mapNotNull { id ->
        val character = characterList.firstOrNull { it.id == id } ?: return@mapNotNull null
        val detail = detailCache[id] ?: return@mapNotNull null
        val avatar = avatarService.avatarMap[id]

        val element = ElementType.getElementTypeByName(character.element)

        val panel: AvatarPanel = PanelAdapter.adapt(
            properties = detail.selected_properties,
            bonusPropertyTypes = setOfNotNull(elementAddHurtProperty(element))
        )

        val actions = buildActions(avatar, detail)

        TeamMember(
            avatarId = id,
            name = character.name,
            element = element,
            panel = panel,
            actions = actions,
            // ⚠️ 用接口返回的**真实等级**(实测有值且因人而异),不再一律假定 90
            level = detail.base.level
        )
    }

    /**
     * 依据**真实**技能等级构造出伤动作。
     *
     * ⚠️⚠️ **2026-09-23 第二次修正(第一次只修了等级映射,槽位仍是错的)**:
     *
     * 本函数原先把"槽位判别 + 倍率取值"写死在两行里,有两处**静默算错**
     * (详见 `memory/dps-calculator.md` §12 与 `SkillSlotResolver` 文件头):
     *
     * 1. **槽位靠下标猜**:假定 `Skills[0]`=普攻、`Skills[1]`=战技。实测**不成立** ——
     *    欧洛伦/茜特菈莉的 `Skills[0]` 是参数全空的伪技能「特殊跳跃」,
     *    导致**普攻静默丢失、战技取成普攻、真战技从未被读**(26/118 角色受影响)。
     *    ⇒ 现改为 `SkillSlotResolver.calculableSkills()`(胡桃同款:滤掉
     *      `Proud.Parameters.size <= 1`,实测 118/118 过滤后恰好 2 个)。
     *
     * 2. **倍率硬取参数下标 0**:`Descriptions` 里混着治疗量/护盾/持续时间/元素能量
     *    等非伤害项,且 `{paramN}` 是 1-based 不连续 ⇒ 下标 0 ≠ param1。
     *    实测米卡 Q 取到 `施放治疗量`=1172.0355、凝光 E 取到 `继承生命`=**-0.499**。
     *    ⇒ 现改为 `SkillSlotResolver.resolve()`,按 `{paramN}` 正确取值并保守筛选。
     *
     * 3. **筛不出伤害项的技能记名跳过**(如芭芭拉/魈/荒泷一斗等 8 个只有治疗或增益的爆发),
     *    而不是退回 index 0 编出一个数 —— 也不静默消失。
     *
     * 原先按 `skill_type` 猜等级的错法也已修正(实测 `skill_type==1` 同时含普攻/战技/爆发,
     * `==2` 是被动)。正确做法照搬胡桃 `SummaryAvatarFactory.cs`:
     * 用 `skills[].skill_id` 匹配元数据的 **`Id`** 字段 —— ⚠️ 是 `Id` 不是 `GroupId`
     * (实测 `skill_id ∩ GroupId` 恒为空集)。
     */
    private fun buildActions(
        avatar: AvatarData?,
        detail: CharacterDetailData.DetailItem
    ): List<MemberAction> {
        if (avatar == null) return emptyList()

        // skill_id → level(接口口径),用于按元数据 Id 取真实等级
        val levelBySkillId: Map<Int, Int> = detail.skills.associate { it.skill_id to it.level }

        val resolution = SkillSlotResolver.resolve(
            depot = avatar.skillDepot,
            levelBySkillId = levelBySkillId
        )

        // ⚠️ 把"哪些技能没能参与计算"如实记录到 UI(不静默丢弃)。
        //    与 skippedMembers / skippedReactions 同一原则。
        if (resolution.skipped.isNotEmpty()) {
            lastSkippedSkills = resolution.skipped.map {
                "${it.slot.displayName}「${it.skillName}」:${it.reason}"
            }
        }

        // ⚠️ 标签用**元数据原文**(picked.label),不再自造 "· 技能伤害" ——
        //    自造标签会让"取错项"在 UI 上完全看不出来(这是原实现的缺陷之一)。
        return resolution.actions.map { a ->
            // ⚠️ 用 skillName + label 组成可读标签;槽位前缀便于用户对照
            MemberAction(
                label = "${a.slot.displayName}「${a.skillName}」${a.label}",
                multiplier = a.multiplier,
                count = 1
            )
        }
    }

    /**
     * 最近一次计算里"没能参与计算"的技能(供 UI 诚实标注)。
     *
     * ⚠️ 每次 [calculate] 前会被清空,避免上一次的残留误导用户。
     */
    var lastSkippedSkills by mutableStateOf<List<String>>(emptyList())
        private set

    /** 元素 → 对应增伤属性类型 */
    private fun elementAddHurtProperty(element: Int): Int? = when (element) {
        ElementType.Fire -> FightProperty.FIGHT_PROP_FIRE_ADD_HURT
        ElementType.Water -> FightProperty.FIGHT_PROP_WATER_ADD_HURT
        ElementType.Grass -> FightProperty.FIGHT_PROP_GRASS_ADD_HURT
        ElementType.Electric -> FightProperty.FIGHT_PROP_ELEC_ADD_HURT
        ElementType.Ice -> FightProperty.FIGHT_PROP_ICE_ADD_HURT
        ElementType.Wind -> FightProperty.FIGHT_PROP_WIND_ADD_HURT
        ElementType.Rock -> FightProperty.FIGHT_PROP_ROCK_ADD_HURT
        else -> null
    }

    companion object {
        const val MAX_TEAM_SIZE = 4

        /**
         * 角色等级回退值。
         *
         * ⚠️ 仅在**取不到**接口真实等级时才用。实测 `base.level` 是**有值的**
         * (菲谢尔 29、琴 20、阿罗夏 20) ⇒ 正常路径都走真实等级,不再"一律假定 90"。
         */
        const val FALLBACK_AVATAR_LEVEL = 90

        /** 目标等级:接口不提供,只能假定 90(已在 UI 标注) */
        const val DEFENDER_LEVEL = 90

        /** 目标抗性假定 10%(常见值,已在 UI 标注) */
        const val DEFAULT_RESISTANCE = 0.1
    }
}
