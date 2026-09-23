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
import com.lianyi.paimonsnotebook.common.util.damage.SkillScalingParser
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
     *  `NotifyGroup` 由 `PaimonsNotebookTheme` 自动包裹)。
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
     *   - 每个成员的出伤动作取"元素战技 + 元素爆发"的**第 1 项倍率**各 1 次,
     *     这是最保守的"最低限度循环",**不代表最优手法**。
     *     用户可调技能次数是后续增强项,当前版本先不做(避免编造手法)。
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

        // 取普攻/战技/爆发三组里**有倍率的前两项**,各 1 次
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
     * ⚠️⚠️ **2026-09-23 用真实响应修正(原来的实现根本取不到等级)**:
     *
     * 我原先假设 `skill_type` 是 `1=普攻 / 2=战技 / 3=爆发`。**实测三个角色全部推翻**:
     * - `skill_type == 1` **同时包含普攻、战技、爆发三条**(如琴:西风剑术/风压剑/蒲公英之风)
     * - `skill_type == 2` 是**固有天赋(被动)**,不参与伤害(如琴:顺风而行/听凭风引/引领之风)
     * - `skill_type == 3` 只在部分角色出现(菲谢尔 3151),琴/阿罗夏甚至没有
     * ⇒ 按 type 猜必然错。
     *
     * **正确做法(照搬胡桃 `SummaryAvatarFactory.cs`)**:
     * 用 `skills[].skill_id` 与元数据的 **`Id`** 字段匹配(实测 3/3 角色 100% 命中:
     * 如琴 `10031/10033/10034` ↔ 元数据 `Skills[].Id` 与 `EnergySkill.Id`)。
     * ⚠️ 匹配的是 **`Id` 而非 `GroupId`** —— 实测 `skill_id ∩ GroupId` 恒为空集。
     *
     * 取不到等级就**跳过该动作**,不猜(猜等于编造)。
     */
    private fun buildActions(
        avatar: AvatarData?,
        detail: CharacterDetailData.DetailItem
    ): List<MemberAction> {
        if (avatar == null) return emptyList()

        val depot = avatar.skillDepot
        val result = mutableListOf<MemberAction>()

        // skill_id → level(接口口径),用于按元数据 Id 取真实等级
        val levelBySkillId: Map<Int, Int> = detail.skills.associate { it.skill_id to it.level }

        // 元数据侧:普攻/战技在 Skills 里,爆发是 EnergySkill
        // ⚠️ 用 Id 匹配(不是 GroupId)
        val normalSkill = depot.Skills.getOrNull(0)
        val elementalSkill = depot.Skills.getOrNull(1)
        val burstSkill = depot.EnergySkill

        normalSkill?.let { sk ->
            levelBySkillId[sk.Id]?.let { lv ->
                SkillScalingParser.multiplierAt(sk.Proud, lv, 0)?.let { m ->
                    result += MemberAction(label = "${sk.Name} · 一段伤害", multiplier = m, count = 1)
                }
            }
        }

        elementalSkill?.let { sk ->
            levelBySkillId[sk.Id]?.let { lv ->
                SkillScalingParser.multiplierAt(sk.Proud, lv, 0)?.let { m ->
                    result += MemberAction(label = "${sk.Name} · 技能伤害", multiplier = m, count = 1)
                }
            }
        }

        burstSkill.let { sk ->
            levelBySkillId[sk.Id]?.let { lv ->
                SkillScalingParser.multiplierAt(sk.Proud, lv, 0)?.let { m ->
                    result += MemberAction(label = "${sk.Name} · 技能伤害", multiplier = m, count = 1)
                }
            }
        }

        return result
    }

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
