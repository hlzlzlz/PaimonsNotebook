package com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.game_record

import com.lianyi.paimonsnotebook.common.core.enviroment.CoreEnvironment
import com.lianyi.paimonsnotebook.common.core.enviroment.EnvironmentClientType
import com.lianyi.paimonsnotebook.common.data.hoyolab.PlayerUid
import com.lianyi.paimonsnotebook.common.data.hoyolab.user.UserAndUid
import com.lianyi.paimonsnotebook.common.extension.request.setDynamicSecret
import com.lianyi.paimonsnotebook.common.extension.request.setUser
import com.lianyi.paimonsnotebook.common.extension.request.setXRpcChallenge
import com.lianyi.paimonsnotebook.common.extension.request.setXRpcClientType
import com.lianyi.paimonsnotebook.common.util.hoyolab.DynamicSecret
import com.lianyi.paimonsnotebook.common.util.json.JSON
import com.lianyi.paimonsnotebook.common.util.request.buildRequest
import okhttp3.Request
import com.lianyi.paimonsnotebook.common.util.request.getAsJson
import com.lianyi.paimonsnotebook.common.util.request.post
import com.lianyi.paimonsnotebook.common.web.ApiEndpoints
import com.lianyi.paimonsnotebook.common.web.hoyolab.cookie.CookieHelper
import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.binding.UserGameRoleData
import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.game_record.abyss.SpiralAbyssData
import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.game_record.act_calendar.ActCalendarData
import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.game_record.character.CharacterDetailData
import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.game_record.character.CharacterListData
import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.game_record.daily_note.DailyNoteData
import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.game_record.hard_challenge.HardChallengeData
import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.game_record.ledger.LedgerData
import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.game_record.role_combat.RoleCombatData
import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.game_record.verification.GeetestVerificationData
import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.game_record.verification.VerificationResultData
import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.game_record.daily_note.DailyNoteWidgetData
import com.lianyi.paimonsnotebook.common.database.user.entity.User as UserEntity

class GameRecordClient {

    //携带Cookie的同时附带设备指纹,缺少指纹会被风控判定为不信任设备导致频繁触发1034验证
    //用header()而非addHeader():覆盖式设置,避免与拦截器默认头叠加出重复值
    private fun Request.Builder.setUserWithFp(
        user: UserEntity,
        cookieType: Int,
    ): Request.Builder {
        setUser(user, cookieType)
        header("x-rpc-device_fp", CoreEnvironment.DeviceFp)
        header("x-rpc-device_id", CoreEnvironment.DeviceId)
        header("Referer", "https://webstatic.mihoyo.com")
        return this
    }

    suspend fun getDailyNote(
        user: UserAndUid,
        challenge: String = "",
    ) = getDailyNote(user.userEntity, user.playerUid, challenge)

    suspend fun getDailyNote(
        user: UserEntity,
        role: UserGameRoleData.Role,
        challenge: String = "",
    ) = getDailyNote(user, PlayerUid(role.game_uid, role.region), challenge)

    suspend fun getDailyNoteForWidget(
        user: UserEntity
    ) = buildRequest {
        url(ApiEndpoints.CardWidgetDataV2)

        setUser(user, CookieHelper.Type.Ltoken or CookieHelper.Type.Stoken)
        setDynamicSecret(DynamicSecret.SaltType.K2)
    }.getAsJson<DailyNoteWidgetData>()

    private suspend fun getDailyNote(
        user: UserEntity,
        playerUid: PlayerUid,
        challenge: String = "",
    ) = buildRequest {
        url(ApiEndpoints.GameRecordDailyNote(playerUid))

        setUserWithFp(user = user, cookieType = CookieHelper.Type.Ltoken)

        setDynamicSecret(
            saltType = DynamicSecret.SaltType.X6,
            version = DynamicSecret.Version.Gen2
        )

        if (challenge.isNotBlank() && challenge != "error") {
            setXRpcChallenge(challenge)
        }

    }.getAsJson<DailyNoteData>()

    suspend fun getSpiralAbyssData(
        user: UserAndUid,
        scheduleType: String,
        challenge: String = "",
    ) = buildRequest {
        url(ApiEndpoints.gameRecordSpiralAbyss(scheduleType = scheduleType, uid = user.playerUid))

        setUserWithFp(user.userEntity, CookieHelper.Type.Cookie)
        //client_type = 5时使用 X4
        setDynamicSecret(DynamicSecret.SaltType.X6, DynamicSecret.Version.Gen2)

        if (challenge.isNotBlank() && challenge != "error") {
            setXRpcChallenge(challenge)
        }
    }.getAsJson<SpiralAbyssData>()

    //旅行者札记 month=0表示当月
    suspend fun getLedgerMonthInfo(
        user: UserAndUid,
        month: Int = 0
    ) = buildRequest {
        url(ApiEndpoints.gameRecordLedgerMonthInfo(month = month, uid = user.playerUid))

        setUserWithFp(user.userEntity, CookieHelper.Type.Cookie)

        setXRpcClientType(EnvironmentClientType.WEB)

        setDynamicSecret(DynamicSecret.SaltType.X4, DynamicSecret.Version.Gen2)

    }.getAsJson<LedgerData>()

    //幻想真境剧诗
    suspend fun getRoleCombatData(
        user: UserAndUid,
        challenge: String = "",
    ) = buildRequest {
        url(ApiEndpoints.gameRecordRoleCombat(user.playerUid))

        setUserWithFp(user.userEntity, CookieHelper.Type.Cookie)

        setXRpcClientType(EnvironmentClientType.WEB)

        setDynamicSecret(DynamicSecret.SaltType.X4, DynamicSecret.Version.Gen2)

        if (challenge.isNotBlank() && challenge != "error") {
            setXRpcChallenge(challenge)
        }

    }.getAsJson<RoleCombatData>()

    //幽境危战
    suspend fun getHardChallengeData(
        user: UserAndUid,
        challenge: String = "",
    ) = buildRequest {
        url(ApiEndpoints.gameRecordHardChallenge(user.playerUid))

        setUserWithFp(user.userEntity, CookieHelper.Type.Cookie)

        setXRpcClientType(EnvironmentClientType.WEB)

        setDynamicSecret(DynamicSecret.SaltType.X4, DynamicSecret.Version.Gen2)

        if (challenge.isNotBlank() && challenge != "error") {
            setXRpcChallenge(challenge)
        }

    }.getAsJson<HardChallengeData>()

    suspend fun getCharacterList(
        user: UserAndUid,
        sortType: Int = 1,
        challenge: String = "",
    ) = buildRequest {
        url(ApiEndpoints.gameRecordCharacterList)

        setUserWithFp(user.userEntity, CookieHelper.Type.Cookie)

        setXRpcClientType(EnvironmentClientType.WEB)

        if (challenge.isNotBlank() && challenge != "error") {
            setXRpcChallenge(challenge)
        }

        buildMap {
            put("server", user.playerUid.region)
            put("role_id", user.playerUid.value)
            put("sort_type", sortType)
        }.post(this)

    }.getAsJson<CharacterListData>()

    //1034风控:注册极验挑战
    suspend fun createVerification(
        user: UserEntity,
        challengePath: String,
        highRisk: Boolean = true,
    ) = buildRequest {
        url(ApiEndpoints.CardCreateVerification(highRisk))

        setUserWithFp(user, CookieHelper.Type.Cookie)

        addHeader("x-rpc-challenge_game", "2")
        addHeader("x-rpc-challenge_path", challengePath)

        setDynamicSecret(DynamicSecret.SaltType.X4, DynamicSecret.Version.Gen2)

    }.getAsJson<GeetestVerificationData>()

    //1034风控:提交验证结果,返回用于重试的challenge
    suspend fun verifyVerification(
        user: UserEntity,
        challengePath: String,
        challenge: String,
        validate: String,
    ) = buildRequest {
        url(ApiEndpoints.CardVerifyVerification)

        setUserWithFp(user, CookieHelper.Type.Cookie)

        addHeader("x-rpc-challenge_game", "2")
        addHeader("x-rpc-challenge_path", challengePath)

        setDynamicSecret(DynamicSecret.SaltType.X4, DynamicSecret.Version.Gen2)

        buildMap {
            put("challenge", challenge)
            put("validate", validate)
        }.post(this)

    }.getAsJson<VerificationResultData>()

    suspend fun getCharacterDetail(
        user: UserAndUid,
        characterIds: List<Int>
    ) = buildRequest {
        url(ApiEndpoints.gameRecordCharacterDetail)

        setUserWithFp(user.userEntity, CookieHelper.Type.Cookie)

        setXRpcClientType(EnvironmentClientType.WEB)

        buildMap {
            put("role_id", user.playerUid.value)
            put("server", user.playerUid.region)
            put("character_ids", characterIds)
        }.post(this)

    }.getAsJson<CharacterDetailData>()

    //活动与卡池日历
    suspend fun getActCalendar(
        user: UserAndUid,
        challenge: String = "",
    ) = buildRequest {
        url(ApiEndpoints.gameRecordActCalendar)

        setUserWithFp(user.userEntity, CookieHelper.Type.Cookie)

        setDynamicSecret(DynamicSecret.SaltType.X6, DynamicSecret.Version.Gen2)

        if (challenge.isNotBlank() && challenge != "error") {
            setXRpcChallenge(challenge)
        }

        buildMap {
            put("role_id", user.playerUid.value)
            put("server", user.playerUid.region)
        }.post(this)

    }.getAsJson<ActCalendarData>()

}