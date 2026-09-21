package com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.event.calculate

/*
* 洞天摹本分享码解析
*
* 分享码有两种形态:
*   1. 纯码,如 "1234567890"
*   2. 从游戏内复制的完整文本,如
*      "摹本分享码：1234567890" / 带 URL 的 "https://…?share_code=1234567890"
*
* 直接把整段文本拼进 URL 会 404,故这里统一抽取纯码。
* 抽成纯函数以便单测(这类字符串处理最容易漏分支)。
* */
object FurnitureShareCodeParser {

    /*
    * 分享码是数字串。不同版本的分享码长度不一致,
    * 故不写死长度,只要求"连续 8 位以上数字" —— 短于 8 位几乎不可能是分享码,
    * 却能避免把"摹本"二字旁边的年份之类的短数字误当分享码。
    * */
    private val codeRegex = Regex("""\d{8,}""")

    /*
    * 从用户输入中提取分享码
    * 提取不到返回 null(由调用方提示用户)
    * */
    fun extract(input: String?): String? {
        if (input.isNullOrBlank()) return null

        //优先认 URL 里的 share_code 参数(最明确)
        Regex("""[?&]share_code=([^&\s]+)""")
            .find(input)
            ?.groupValues
            ?.getOrNull(1)
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }

        //否则取第一段足够长的连续数字
        return codeRegex.find(input)?.value
    }

    /*
    * 是否是可用的分享码
    * 用于输入框的即时校验
    * */
    fun isValid(input: String?): Boolean = extract(input) != null
}
