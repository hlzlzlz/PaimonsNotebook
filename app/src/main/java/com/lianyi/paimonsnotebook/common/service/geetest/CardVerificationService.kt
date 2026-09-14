package com.lianyi.paimonsnotebook.common.service.geetest

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.FrameLayout
import com.lianyi.paimonsnotebook.common.application.PaimonsNotebookApplication
import com.lianyi.paimonsnotebook.common.database.user.entity.User
import com.lianyi.paimonsnotebook.common.extension.string.errorNotify
import com.lianyi.paimonsnotebook.common.web.hoyolab.takumi.game_record.GameRecordClient
import com.lianyi.paimonsnotebook.common.util.request.buildRequest
import com.lianyi.paimonsnotebook.common.util.request.emptyOkHttpClient
import com.lianyi.paimonsnotebook.common.util.request.getAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/*
* 1034风控验证服务
* 流程与胡桃工具箱一致:
* createVerification获取极验挑战 -> 优先静默校验 -> 内嵌WebView加载极验JS组件完成验证 -> verifyVerification提交
* -> 拿到challenge用于重试原请求
* 注意:不使用GT3原生SDK,部分网络环境下SDK无法连接极验服务器,而WebView可以正常加载
* */
object CardVerificationService {

    //业务接口路径,作为challenge_path提交
    const val PATH_DAILY_NOTE = "https://api-takumi-record.mihoyo.com/game_record/app/genshin/api/dailyNote"
    const val PATH_INDEX = "https://api-takumi-record.mihoyo.com/game_record/app/genshin/api/index"
    const val PATH_SPIRAL_ABYSS = "https://api-takumi-record.mihoyo.com/game_record/app/genshin/api/spiralAbyss"
    const val PATH_CHARACTER_LIST = "https://api-takumi-record.mihoyo.com/game_record/app/genshin/api/character/list"
    const val PATH_ROLE_COMBAT = "https://api-takumi-record.mihoyo.com/game_record/app/genshin/api/role_combat"
    const val PATH_HARD_CHALLENGE = "https://api-takumi-record.mihoyo.com/game_record/app/genshin/api/hard_challenge"
    const val PATH_ACT_CALENDAR = "https://api-takumi-record.mihoyo.com/game_record/app/genshin/api/act_calendar"

    private val client = GameRecordClient()

    /*
    * 执行验证,返回用于重试原请求的challenge值
    * 验证失败或用户取消时返回null
    * */
    suspend fun verify(
        user: User,
        challengePath: String,
    ): String? {
        val activity = PaimonsNotebookApplication.currentActivity

        if (activity == null) {
            "没有可用的界面,无法弹出验证".errorNotify()
            return null
        }

        val registration = client.createVerification(user, challengePath)

        if (!registration.success || registration.data.gt.isBlank()) {
            "发起验证失败:${registration.message}".errorNotify()
            return null
        }

        //优先静默校验:低风险时挑战已被服务端预放行,直接换取validate,用户无感
        val validate = getSilentValidate(registration.data.gt, registration.data.challenge)
            ?: withContext(Dispatchers.Main) {
                withTimeout(180_000L) {
                    showGeetestWebviewAndAwaitValidate(
                        activity, registration.data.gt, registration.data.challenge
                    )
                }
            }

        if (validate.isNullOrBlank()) {
            "验证未完成".errorNotify()
            return null
        }

        val verifyResult = client.verifyVerification(
            user = user,
            challengePath = challengePath,
            challenge = registration.data.challenge,
            validate = validate
        )

        if (!verifyResult.success) {
            "验证提交失败:${verifyResult.message}".errorNotify()
            return null
        }

        return verifyResult.data.challenge
    }

    //静默校验:低风险挑战直接向极验换取validate,无需弹出验证界面
    private suspend fun getSilentValidate(gt: String, challenge: String): String? {
        val url = "https://apiv6.geetest.com/ajax.php?pt=3&client_type=web_mobile&lang=zh-cn" +
                "&challenge=${challenge}&gt=${gt}"

        val text = buildRequest {
            url(url)

            addHeader("User-Agent", "Mozilla/5.0 (Linux; Android ${android.os.Build.VERSION.RELEASE}; ${android.os.Build.MODEL}) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
            addHeader("Referer", "https://api-takumi-record.mihoyo.com/")
        }.getAsText(emptyOkHttpClient)

        if (text.isBlank()) {
            return null
        }

        //响应可能带有函数包裹,截取花括号内的JSON
        val json = try {
            val left = text.indexOf('{')
            val right = text.lastIndexOf('}')

            if (left == -1 || right <= left) {
                return null
            }

            JSONObject(text.substring(left, right + 1))
        } catch (e: Exception) {
            return null
        }

        return if (json.optString("status") == "success") {
            val data = json.optJSONObject("data")

            if (data?.optString("result") == "success") {
                data.optString("validate").ifBlank { null }
            } else {
                null
            }
        } else {
            null
        }
    }

    //弹出内嵌WebView加载极验JS组件,挂起等待验证完成,返回validate值
    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun showGeetestWebviewAndAwaitValidate(
        activity: Activity,
        gt: String,
        challenge: String,
    ): String? = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            val resumed = AtomicBoolean(false)

            var dialog: Dialog? = null

            val webView = WebView(activity).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webChromeClient = WebChromeClient()

                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun onResult(json: String) {
                        val validate = try {
                            JSONObject(json).optString("geetest_validate")
                        } catch (e: Exception) {
                            ""
                        }

                        activity.runOnUiThread {
                            if (resumed.compareAndSet(false, true)) {
                                continuation.resume(validate.ifBlank { null })
                            }
                            dialog?.dismiss()
                        }
                    }
                }, "AndroidBridge")
            }

            val frameLayout = FrameLayout(activity).apply {
                setBackgroundColor(0xAA000000.toInt())
                addView(
                    webView,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
            }

            dialog = Dialog(activity).apply {
                requestWindowFeature(Window.FEATURE_NO_TITLE)
                setContentView(frameLayout)
                window?.apply {
                    setGravity(Gravity.CENTER)
                    setLayout(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
                setCanceledOnTouchOutside(false)
                setOnDismissListener {
                    if (resumed.compareAndSet(false, true)) {
                        continuation.resume(null)
                    }
                }
                show()
            }

            //极验官方JS组件,与胡桃工具箱的WebView2实现一致
            //api_server双节点自动切换:通用节点失败时切换米哈游内置的中国加速节点
            val html = """
                <html>
                    <head>
                        <meta name="viewport" content="width=device-width, initial-scale=1">
                        <style>
                            body { background: transparent; }
                            #geetest-div { text-align: center; }
                        </style>
                    </head>
                    <body>
                        <div id="geetest-div"></div>
                        <script src="https://static.geetest.com/static/js/gt.0.5.2.js"></script>
                        <script>
                            var servers = ["api.geetest.com", "yumchina.geetest.com"];
                            var serverIndex = 0;
                            var gt = "$gt";
                            var challenge = "$challenge";
                            var finished = false;

                            function tryInit() {
                                document.getElementById("geetest-div").innerHTML = "";

                                initGeetest(
                                    {
                                        protocol: "https://",
                                        gt: gt,
                                        challenge: challenge,
                                        new_captcha: true,
                                        product: "bind",
                                        api_server: servers[serverIndex]
                                    },
                                    function (captchaObj) {
                                        var ready = false;

                                        captchaObj.onReady(function () {
                                            ready = true;
                                            captchaObj.verify();
                                        });

                                        captchaObj.onSuccess(function () {
                                            if (!finished) {
                                                finished = true;
                                                AndroidBridge.onResult(JSON.stringify(captchaObj.getValidate()));
                                            }
                                        });

                                        captchaObj.onError(function () {
                                            nextServer();
                                        });

                                        setTimeout(function () {
                                            if (!ready) {
                                                nextServer();
                                            }
                                        }, 8000);
                                    }
                                );
                            }

                            function nextServer() {
                                if (finished) {
                                    return;
                                }

                                serverIndex++;

                                if (serverIndex < servers.length) {
                                    tryInit();
                                } else {
                                    finished = true;
                                    AndroidBridge.onResult("");
                                }
                            }

                            tryInit();
                        </script>
                    </body>
                </html>
            """.trimIndent()

            webView.loadDataWithBaseURL("https://api-takumi-record.mihoyo.com/", html, "text/html", "utf-8", null)

            continuation.invokeOnCancellation {
                activity.runOnUiThread {
                    dialog.dismiss()
                }
            }
        }
    }
}
