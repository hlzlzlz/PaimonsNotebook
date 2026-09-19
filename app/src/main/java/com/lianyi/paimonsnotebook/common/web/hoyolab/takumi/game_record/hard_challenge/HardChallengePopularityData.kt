package com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.game_record.hard_challenge

/*
* 幽境危战 全服热门角色
*
* 实测(2026-09-19) GET /game_record/app/genshin/api/hard_challenge/popularity?role_id=&server=
*   retcode 0 / 3,158 B / avatar_list 16 条
*   字段类型逐条核对(非按命名推断):
*     avatar_id -> int
*     name      -> str
*     element   -> str
*     image     -> str(完整 https 图片地址,可直接喂 NetworkImage)
*     rarity    -> int
*
* 注:该端点返回的角色**自带 name/image/rarity**,不必再查本地元数据;
* 与胡桃的 HardChallengeSimpleAvatar 字段一一对应。
*
* 与个人战绩端点(hard_challenge)的区别:那个带 schedule/single/challenge 明细,
* 这个只有一个 avatar_list,是"全服热门"排行,不含比例数值 —— 服务端只给
* 名单与顺序,不给百分比,所以界面只能按返回顺序展示名次。
* */
data class HardChallengePopularityData(
    val avatar_list: List<SimpleAvatar>?
) {
    data class SimpleAvatar(
        val avatar_id: Int,
        val name: String,
        val element: String,
        val image: String,
        val rarity: Int
    )
}
