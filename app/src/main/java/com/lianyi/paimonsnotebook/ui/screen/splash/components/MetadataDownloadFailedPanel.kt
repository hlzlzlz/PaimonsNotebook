package com.lianyi.paimonsnotebook.ui.screen.splash.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lianyi.paimonsnotebook.R
import com.lianyi.paimonsnotebook.common.components.widget.TextButton
import com.lianyi.paimonsnotebook.ui.theme.BackGroundColor
import com.lianyi.paimonsnotebook.ui.theme.Black
import com.lianyi.paimonsnotebook.ui.theme.White_60

/*
* 元数据下载失败面板
*
* 背景:此前下载失败只弹一个3秒toast,不做任何导航 —— 而此刻
* showEnableMetadataHint 已被置 false、showLoading 又由 onFinally 复位,
* 两个界面都是空 ⇒ 用户看到的是一片**纯白屏且没有任何按钮**。
* 更糟的是重进App时会因 OnLaunchShowEnableMetadataHint=false 直接再次
* 自动下载,网络持续不可用即形成**永久白屏循环**(唯一出路是在20秒倒计时
* 内手点"暂不下载")。
*
* 故失败后必须给出可见的说明与两个可操作出口。
* */
@Composable
fun MetadataDownloadFailedPanel(
    onRetry: () -> Unit,
    onSkip: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackGroundColor)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.emotion_icon_paimon_error),
            contentDescription = null,
            modifier = Modifier.size(120.dp)
        )

        Spacer(modifier = Modifier.height(15.dp))

        Text(
            text = "元数据下载失败",
            color = Black,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "可能是网络不通畅,或元数据源暂时不可用。\n可以重试,也可以先跳过 —— 跳过之后程序仍可使用," +
                    "但角色资料、祈愿记录等需要元数据的功能会暂不显示;\n之后可在「设置 - 同步元数据」中随时下载。",
            color = Black,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(32.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            TextButton(
                text = "跳过并进入",
                onClick = onSkip,
                backgroundColor = White_60,
                textColor = Black,
                modifier = Modifier.weight(1f),
                textSize = 14.sp,
                bold = true
            )

            Spacer(modifier = Modifier.width(8.dp))

            TextButton(
                text = "重试下载",
                onClick = onRetry,
                modifier = Modifier.weight(1f),
                textSize = 14.sp,
                bold = true
            )
        }
    }
}
