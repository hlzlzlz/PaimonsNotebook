package com.lianyi.paimonsnotebook.common.view

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.lianyi.paimonsnotebook.R
import com.lianyi.paimonsnotebook.common.core.base.BaseActivity
import com.lianyi.paimonsnotebook.common.core.enviroment.CoreEnvironment
import com.lianyi.paimonsnotebook.common.data.hoyolab.user.User
import com.lianyi.paimonsnotebook.common.database.user.util.AccountHelper
import com.lianyi.paimonsnotebook.common.extension.activity.setImmersionMode
import com.lianyi.paimonsnotebook.common.extension.list.takeFirstIf
import com.lianyi.paimonsnotebook.common.extension.string.errorNotify
import com.lianyi.paimonsnotebook.common.web.ApiEndpoints
import com.lianyi.paimonsnotebook.common.web.bridge.MiHoYoJSInterface
import com.lianyi.paimonsnotebook.common.web.bridge.setMiyouSheWebViewCookie
import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.binding.UserGameRoleData

class HoyolabWebActivity : BaseActivity() {

    companion object {
        //url
        const val EXTRA_URL = "extra_url"

        //用户mid
        const val EXTRA_MID = "mid"

        //是否在界面关闭时清理cookie
        const val EXTRA_CLEAN_COOKIE = "cleanCookie"
    }

    private val extraMid by lazy {
        intent.getStringExtra(EXTRA_MID)
    }

    private val cleanCookie by lazy {
        intent.getBooleanExtra(EXTRA_CLEAN_COOKIE, true)
    }

    //获取携带的url,默认为实时便笺界面
    private fun getExtraUrl(role: UserGameRoleData.Role): String {
        return intent?.getStringExtra(EXTRA_URL) ?: ApiEndpoints.getGenshinGameRecordUrl(role)
    }

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hoyolab_web)

        setImmersionMode()

        webView = findViewById(R.id.hoyolab_webview)

        val userList = AccountHelper.userListFlow.value

        val user = if (extraMid.isNullOrEmpty()) {
            AccountHelper.selectedUserFlow.value
        } else {
            userList.takeFirstIf { user: User -> user.userEntity.mid == extraMid }
        }

        if (user == null) {
            "请设置默认用户后再次尝试进入".errorNotify(false)
            finish()
            return
        }

        val role = user.getSelectedGameRole()

        if (role == null) {
            "请设置默认用户角色后再次尝试进入".errorNotify(false)
            finish()
            return
        }

        webView.apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                userAgentString = CoreEnvironment.HoyolabMobileUA
            }
        }

        val url = getExtraUrl(role)

        webView.setMiyouSheWebViewCookie(
            cookieToken = user.userEntity.cookieToken, lToken = user.userEntity.ltoken,sToken = user.userEntity.stoken
        )

        /*
        * 把首个url一并交给桥:webView.url在页面加载完成前为null,
        * 此时需要用它判断来源是否可信。
        * */
        val jsInterface = MiHoYoJSInterface(user, webView, url) { finish() }

        /*
        * 让桥接侧持续获知当前页面url。
        *
        * 桥接方法由网页JS调用,运行在JavaBridge后台线程上,在该线程读
        * webView.url 会因 checkThread() 抛 RuntimeException(本应用
        * targetSdk=34,线程检查恒开)。所以url必须由主线程侧的WebViewClient
        * 回调推送给桥,桥只读自己那份@Volatile副本。
        *
        * 三个回调都要接:
        *   onPageStarted              —— 首次加载与整页导航
        *   doUpdateVisitedHistory     —— 页内跳转(SPA改hash)、重定向
        *   shouldOverrideUrlLoading   —— 外部链接被loadUrl进来时
        * 少接任何一个,该场景下都会退回首次url判定,造成误拦或漏放。
        * */
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                if (view == null || request == null) return false

                val target = request.url.toString()
                jsInterface.onPageUrlChanged(target)

                view.loadUrl(target)
                return true
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                jsInterface.onPageUrlChanged(url)
            }

            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                super.doUpdateVisitedHistory(view, url, isReload)
                jsInterface.onPageUrlChanged(url)
            }
        }

        webView.addJavascriptInterface(jsInterface, "MiHoYoJSInterface")

        webView.loadUrl(url)
    }

    override fun onDestroy() {
        //移除JSInterface,防止内存泄漏
        webView.removeJavascriptInterface("MiHoYoJSInterface")

        if (cleanCookie) {
            CookieManager.getInstance().apply {
                removeAllCookies { }
                flush()
            }
        }
        super.onDestroy()
    }

}