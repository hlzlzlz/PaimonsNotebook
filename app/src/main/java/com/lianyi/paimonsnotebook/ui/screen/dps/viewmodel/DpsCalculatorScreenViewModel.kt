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
* 5. **不臆测面板格式**:[panelFormatVerified] 恒为 false,
*    UI 据此显示"面板数据格式未经真机验证"的风险提示(诚实边界)。
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

    /**
     * 错误/提示反馈。
     *
     * ⚠️ 这里**不自造 message 状态**,而是直接用项目既有的通知机制
     * (`String.errorNotify()` / `warnNotify()`,全项目 ViewModel 中已有 105 处同样用法;
     *  其内部走 `launchSafeIO`,线程安全,且 `NotifyGroup` 由 `PaimonsNotebookTheme` 自动包裹)。
     *
     * 教训:我最初写了一个 `message` 状态 + `consumeMessage()`,但 **UI 从未消费它**
     * ⇒ "队伍最多 4 人""请先选择角色"等提示会**静默消失**,用户不知道为什么点了没反应。
     * 这正是本项目记录过的失败模式("功能静默消失且一直未被发现")。
     */
    val panelFormatVerified: Boolean = false

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
                attackerLevel = AVATAR_LEVEL,
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
            actions = actions
        )
    }

    /**
     * 依据**真实**技能等级构造出伤动作。
     *
     * ⚠️ 技能等级来自 `detail.skills`(按 skill_type 区分普攻/战技/爆发),
     * 取不到就返回空 —— **不猜等级**(猜等于编造)。
     */
    private fun buildActions(
        avatar: AvatarData?,
        detail: CharacterDetailData.DetailItem
    ): List<MemberAction> {
        if (avatar == null) return emptyList()

        val depot = avatar.skillDepot
        val result = mutableListOf<MemberAction>()

        // skill_type: 1=普攻 2=战技(元素战技) 3=爆发(按米游社口径)
        val normalLevel = detail.skills.firstOrNull { it.skill_type == SKILL_TYPE_NORMAL }?.level
        val skillLevel = detail.skills.firstOrNull { it.skill_type == SKILL_TYPE_SKILL }?.level
        val burstLevel = detail.skills.firstOrNull { it.skill_type == SKILL_TYPE_BURST }?.level

        // 普攻:取第 1 项倍率(一段伤害)
        normalLevel?.let { lv ->
            val na = depot.Skills.firstOrNull()
            if (na != null) {
                SkillScalingParser.multiplierAt(na.Proud, lv, 0)?.let { m ->
                    result += MemberAction(label = "${na.Name} · 一段伤害", multiplier = m, count = 1)
                }
            }
        }

        // 元素战技:通常是 Skills 里的第 2 项
        skillLevel?.let { lv ->
            val skill = depot.Skills.getOrNull(1)
            if (skill != null) {
                SkillScalingParser.multiplierAt(skill.Proud, lv, 0)?.let { m ->
                    result += MemberAction(label = "${skill.Name} · 技能伤害", multiplier = m, count = 1)
                }
            }
        }

        // 元素爆发
        burstLevel?.let { lv ->
            val burst = depot.EnergySkill
            SkillScalingParser.multiplierAt(burst.Proud, lv, 0)?.let { m ->
                result += MemberAction(label = "${burst.Name} · 技能伤害", multiplier = m, count = 1)
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

        /** 角色等级:面板里没有该字段,按满级 90 假定(⚠️ 属假定,已在 UI 标注) */
        const val AVATAR_LEVEL = 90

        /** 目标等级假定 90 */
        const val DEFENDER_LEVEL = 90

        /** 目标抗性假定 10%(常见值) */
        const val DEFAULT_RESISTANCE = 0.1

        /** 米游社 skill_type 口径 */
        private const val SKILL_TYPE_NORMAL = 1
        private const val SKILL_TYPE_SKILL = 2
        private const val SKILL_TYPE_BURST = 3
    }
}
