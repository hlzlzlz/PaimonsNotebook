package com.lianyi.paimonsnotebook.common.components.placeholder

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
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

/*
* 错误占位
*
* onRetry:传入后会在文案下方渲染"重试"按钮。
*
* ⚠️ 这个参数存在的意义:本应用此前**全项目没有任何面向用户的重试入口** ——
*    加载失败只弹一个3秒toast,页面随即停在错误态,用户除了杀掉App重进
*    别无他法。凡是有明确"重新拉取"动作的页面都应传入 onRetry。
* */
@Composable
fun ErrorPlaceholder(
    text: String = "出错了...",
    onRetry: (() -> Unit)? = null,
    content: @Composable () -> Unit = {}
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Image(
            painter = painterResource(id = R.drawable.emotion_icon_paimon_error),
            contentDescription = null,
            modifier = Modifier.size(120.dp)
        )

        Spacer(modifier = Modifier.height(15.dp))

        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )

        if (onRetry != null) {
            Spacer(modifier = Modifier.height(20.dp))

            TextButton(
                text = "重试",
                onClick = onRetry,
                modifier = Modifier.width(140.dp),
                textSize = 14.sp,
                bold = true
            )
        }

        content.invoke()
    }
}
