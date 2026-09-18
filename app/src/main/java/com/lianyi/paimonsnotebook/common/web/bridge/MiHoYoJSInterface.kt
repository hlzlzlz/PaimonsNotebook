package com.lianyi.paimonsnotebook.common.web.bridge

import android.os.Build
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.lianyi.paimonsnotebook.common.core.enviroment.CoreEnvironment
import com.lianyi.paimonsnotebook.common.data.hoyolab.user.User
import com.lianyi.paimonsnotebook.common.extension.intent.setComponentName
import com.lianyi.paimonsnotebook.common.util.hoyolab.DynamicSecret
import com.lianyi.paimonsnotebook.common.util.json.JSON
import com.lianyi.paimonsnotebook.common.view.HoyolabWebActivity
import com.lianyi.paimonsnotebook.common.web.bridge.model.ActionTypePayload
import com.lianyi.paimonsnotebook.common.web.bridge.model.CookieTokenPayload
import com.lianyi.paimonsnotebook.common.web.bridge.model.DynamicSecrect2Payload
import com.lianyi.paimonsnotebook.common.web.bridge.model.IJsResult
import com.lianyi.paimonsnotebook.common.web.bridge.model.JsParams
import com.lianyi.paimonsnotebook.common.web.bridge.model.JsResult
import com.lianyi.paimonsnotebook.common.web.bridge.model.PushPagePayload
import com.lianyi.paimonsnotebook.common.web.hoyolab.cookie.CookieHelper
import com.lianyi.paimonsnotebook.common.web.hoyolab.passport.PassportClient
import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.auth.AuthClient
import com.lianyi.paimonsnotebook.ui.screen.home.util.HomeHelper
import com.lianyi.paimonsnotebook.common.extension.scope.launchSafeIO
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MiHoYoJSInterface(
    private val user: User,
    private val webView: WebView,
    //首次加载的url,用作webView.url尚未就绪时的兜底
    private val initialUrl: String = "",
    private val closePage: () -> Unit = {}
) {

    companion object {
        /*
        * 需要校验来源的方法。
        * 这些方法会泄露账号凭证(ltoken/LTuid)、设备指纹(device_fp)、
        * 接口签名(DS),或能用stoken换出新的cookie_token,一旦被第三方页面
        * 调用等同于账号被接管,因此必须确认调用方来自官方域。
        * */
        private val SENSITIVE_METHODS = setOf(
            "getActionTicket",
            "getCookieInfo",
            "getCookieToken",
            "getDS",
            "getDS2",
            "getHTTPRequestHeaders",
            "getUserInfo",
        )

        //域名匹配逻辑见 WebViewUrlAllowlist(单独抽出以便单元测试)
        fun isTrustedUrl(url: String?) = WebViewUrlAllowlist.isTrustedUrl(url)
    }

    //当前页面是否可信(webView.url未就绪时回退到首次加载的url)
    private fun isCurrentPageTrusted() = isTrustedUrl(webView.url ?: initialUrl)

    private val authClient by lazy {
        AuthClient()
    }

    private val passportClient by lazy {
        PassportClient()
    }

    private fun closePage(params: JsParams<Any?>): JsResult<Map<String, Any>>? {
        CoroutineScope(Dispatchers.Main).launch {
            webView.goBack()
            if (!webView.canGoBack()) {
                closePage.invoke()
            }
        }
        return null
    }

    private fun configureShare(params: JsParams<Any?>) = null

    private suspend fun getActionTicket(params: JsParams<ActionTypePayload>): IJsResult {
        val actionTicketData =
            authClient.getActionTicketBySToken(user.userEntity, params.payload.actionType)
        return JsResult(
            retcode = actionTicketData.retcode,
            message = actionTicketData.message,
            data = actionTicketData.data
        )
    }

    private fun getCookieInfo(params: JsParams<Any?>): IJsResult {
        return JsResult(
            data = mapOf(
                CookieHelper.Keys.LTuid to user.userEntity.ltoken[CookieHelper.Keys.LTuid],
                CookieHelper.Keys.LToken to user.userEntity.ltoken[CookieHelper.Keys.LToken],
                CookieHelper.Keys.LoginTicket to ""
            )
        )
    }

    private suspend fun getCookieToken(params: JsParams<CookieTokenPayload>): IJsResult? {
        val res = passportClient.getCookieTokenBySToken(user.userEntity.stoken)
        if (!res.success) return null

        return JsResult(
            data = res.data
        )
    }

    private fun getCurrentLocale(params: JsParams<Any?>): IJsResult {
        return JsResult(
            data = mapOf(
                "language" to "zh-CN",
                "timeZone" to "GMT+8"
            )
        )
    }

    private fun getDS(params: JsParams<Any?>): IJsResult {
        return JsResult(
            data = mapOf(
                "DS" to DynamicSecret.getDynamicSecret(
                    DynamicSecret.Version.Gen1,
                    DynamicSecret.SaltType.LK2
                ),
            )
        )
    }

    private fun getRandomString(): String {
        val range = "abcdefghijklmnopqrstuvwxyz1234567890"
        with(StringBuilder()) {
            repeat(6) {
                this.append(range.random())
            }
            return this.toString()
        }
    }

    private fun getDS2(params: JsParams<DynamicSecrect2Payload>): IJsResult {
        val b = params.payload.body
        val q = params.payload.getQueryParam()
        return JsResult(
            data = mapOf(
                "DS" to DynamicSecret.getDynamicSecret(
                    DynamicSecret.Version.Gen2,
                    DynamicSecret.SaltType.X4,
                    query = q,
                    body = b
                )
            )
        )
    }

    private fun getHTTPRequestHeaders(params: JsParams<Any?>): IJsResult {
        return JsResult(
            data = mapOf(
                "x-rpc-client_type" to CoreEnvironment.ClientType,
                "x-rpc-device_id" to CoreEnvironment.DeviceId,
                "x-rpc-app_version" to CoreEnvironment.XrpcVersion,
                "x-rpc-app_id" to "bll8iq97cem8",
                "x-rpc-sdk_version" to "2.20.2",
                "x-rpc-device_fp" to CoreEnvironment.DeviceFp,
                "Content-Type" to "application/json",

                "x-rpc-device_name" to Build.DEVICE,
                "x-rpc-device_model" to Build.MODEL,
                "x-rpc-sys_version" to Build.VERSION.RELEASE,
            )
        )
    }

    private fun getStatusBarHeight(params: JsParams<Any?>): IJsResult {
        return JsResult(
            //始终将状态栏的高度设置为24
            data = mapOf(
//                "statusBarHeight" to SystemService.statusBarHeight,
                "statusBarHeight" to 0,
            )
        )
    }

    private fun getUserInfo(params: JsParams<Any?>): IJsResult {
        val info = user.userInfo
        return JsResult(
            data = mapOf(
                "id" to info.uid,
                "gender" to info.gender,
                "nickname" to info.nickname,
                "introduce" to info.introduce,
                "avatar_url" to info.avatar_url
            )
        )
    }

    private fun pushPage(payload: PushPagePayload): JsResult<Map<String, Any>>? {
        HomeHelper.goActivityByIntentNewTask {
            setComponentName(HoyolabWebActivity::class.java)
            putExtra(HoyolabWebActivity.EXTRA_URL, payload.page)
            putExtra(HoyolabWebActivity.EXTRA_MID, user.userEntity.mid)
            putExtra(HoyolabWebActivity.EXTRA_CLEAN_COOKIE, false)
        }
        return null
    }

    private fun eventTrack(param: JsParams<Any?>): IJsResult? {
        return null
    }

    private suspend fun tryGetJsResultFromJsParam(
        param: JsParams<Any?>,
    ): IJsResult? {
        return when (param.method) {
            "closePage" -> closePage(param)
            "configure_share" -> configureShare(param)
            "eventTrack" -> eventTrack(param)
            "getActionTicket" -> getActionTicket(
                JsParams(param.method, JSON.parse(JSON.stringify(param.payload)), param.callback)
            )

            "getCookieInfo" -> getCookieInfo(param)
            "getCookieToken" -> getCookieToken(
                JsParams(param.method, JSON.parse(JSON.stringify(param.payload)), param.callback)
            )

            "getCurrentLocale" -> getCurrentLocale(param)
            "getDS" -> getDS(param)
            "getDS2" ->
                getDS2(
                    JsParams(
                        param.method,
                        JSON.parse(JSON.stringify(param.payload)),
                        param.callback
                    )
                )

            "getHTTPRequestHeaders" -> getHTTPRequestHeaders(param)
            "getStatusBarHeight" -> getStatusBarHeight(param)
            "getUserInfo" -> getUserInfo(param)
            "hideLoading" -> null
            "login" -> null
            "pushPage" -> {
                val payload = JSON.parse<PushPagePayload>(JSON.stringify(param.payload))
                pushPage(payload)
            }

            "showLoading" -> null
            else -> {
                null
            }
        }
    }

    private fun callbackScript(callback: String, payload: String? = null) {

        println("callback = ${callback}")

        if (callback.isBlank()) {
            return
        }

        val js =
            "javascript:mhyWebBridge(\"${callback}\"${if (payload != null) ",${payload}" else ""})"

        CoroutineScope(Dispatchers.Main).launch {
            webView.loadUrl(js)
        }
    }

    @JavascriptInterface
    fun postMessage(str: String) {
        val param = JSON.parse<JsParams<Any?>>(str)

        /*
        * 来源校验:addJavascriptInterface注入的桥对本WebView加载的任意页面可见,
        * 而本Activity允许导航到外部链接(帖子内的超链接/服务器下发的page)。
        * 若不校验,任何被导航到的第三方页面都能调用下列方法拿到ltoken/LTuid/
        * device_fp/DS,或用stoken换出新的cookie_token,等同于账号被接管。
        * 非敏感方法(closePage/showLoading等)不校验,以免影响正常页面交互。
        * */
        if (param.method in SENSITIVE_METHODS && !isCurrentPageTrusted()) {
            println("MiHoYoJSInterface: 拒绝来自非官方域的敏感调用 ${param.method} url=${webView.url}")
            return
        }

        //用launchSafeIO:本方法由WebView页面回调,内部有网络请求与JSON解析,
        //裸launch抛异常会直接杀掉进程
        launchSafeIO {
            val result = tryGetJsResultFromJsParam(param)

            if (result != null && !param.callback.isNullOrBlank()) {
                callbackScript(param.callback, result.toJson())
            }
        }

    }
}