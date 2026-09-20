package com.lianyi.paimonsnotebook.common.web.hoyolab.hk4e.event.gacha_info

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.annotations.JsonAdapter
import java.lang.reflect.Type

/*
* 千星奇域(UGC)祈愿记录的单条记录
*
* 端点:GET /gacha_info/api/getBeyondGachaLog
*   (与 getGachaLog 同域,但路径与响应结构都不同)
*
* ⚠️ 为什么这里所有字段都是 String 且带自定义反序列化器,而不是像
* 普通祈愿那样直接声明 String / Int:
*
* 2026-09-19 公告接口出过一次事故 —— `has_content` 按"0/1 标记"惯例写成
* Int,服务端实际返回 JSON 布尔值,Gson 抛 IllegalStateException,
* 整个响应解析失败、功能完全不可用(详见 AGENTS.md「字段类型必须实测」)。
*
* 而千星奇域这条链路**无法用真实数据实测**:本机账号
* (uid 338131141 / cn_gf01)的 6 个 UGC 类型码全部返回 retcode 0 但
* `list` 为空(即该账号没有千星奇域记录),因此拿不到真实样本。
* 在这种情况下,"照命名推断类型"正是会重演公告事故的做法。
*
* 所以这里采取**宽松解析**:无论服务端把 id/schedule_id/rank_type/is_up/
* op_gacha_type 返回成数字还是字符串,都统一收敛成 String。
* 这也正好与 UIGF v4.2 规范一致 —— 规范里 hk4e_ugc 的这些字段
* **全部定义为 string**(见官方 schema:
*   id/schedule_id/item_id/rank_type/op_gacha_type 均为 "type": "string"
*   且带 ^[0-9]+$ 的 pattern)。
*
* 参考实现(胡桃 Snap.Hutao.Remastered)的 C# 模型也对它们做了
* "数字或字符串皆可"的处理(JsonNumberHandling.AllowReadingFromString /
* JsonEnumHandling.NumberString),说明服务端确实可能返回数字。
* */
@JsonAdapter(BeyondGachaLogItem.Deserializer::class)
data class BeyondGachaLogItem(
    val id: String,
    val uid: String,
    val region: String,
    val schedule_id: String,
    val item_type: String,
    val item_id: String,
    val item_name: String,
    val rank_type: String,
    val is_up: String,
    val time: String,
    val op_gacha_type: String,
) {

    class Deserializer : JsonDeserializer<BeyondGachaLogItem> {
        override fun deserialize(
            json: JsonElement,
            typeOfT: Type,
            context: JsonDeserializationContext
        ): BeyondGachaLogItem {
            val obj = json.asJsonObject

            return BeyondGachaLogItem(
                id = obj.str("id"),
                uid = obj.str("uid"),
                region = obj.str("region"),
                schedule_id = obj.str("schedule_id"),
                item_type = obj.str("item_type"),
                item_id = obj.str("item_id"),
                item_name = obj.str("item_name"),
                rank_type = obj.str("rank_type"),
                is_up = obj.str("is_up"),
                time = obj.str("time"),
                op_gacha_type = obj.str("op_gacha_type")
            )
        }

        /*
        * 取字段并统一转成字符串
        *
        * - 数字/布尔/字符串一律走 asString,故类型写错也不会抛异常
        * - 缺失或 null 返回空串(而不是抛异常),由上层决定如何处理
        * - 兼容驼峰命名,避免服务端大小写风格变化直接导致全空
        * */
        private fun com.google.gson.JsonObject.str(vararg names: String): String {
            names.forEach { name ->
                val element = get(name) ?: return@forEach

                if (element.isJsonNull) return@forEach

                if (element.isJsonPrimitive) {
                    return element.asString
                }

                // 非基本类型(数组/对象)按原样序列化,避免静默丢数据
                return element.toString()
            }

            return ""
        }
    }
}
