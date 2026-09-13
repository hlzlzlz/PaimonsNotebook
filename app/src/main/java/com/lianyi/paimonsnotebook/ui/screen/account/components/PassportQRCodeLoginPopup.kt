package com.lianyi.paimonsnotebook.ui.screen.account.components

import android.graphics.Bitmap
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.lianyi.paimonsnotebook.R
import com.lianyi.paimonsnotebook.common.components.popup.BasePopup
import com.lianyi.paimonsnotebook.common.components.spacer.NavigationBarPaddingSpacer
import com.lianyi.paimonsnotebook.common.components.spacer.StatusBarPaddingSpacer
import com.lianyi.core.ui.components.text.InfoText
import com.lianyi.paimonsnotebook.common.components.widget.TextButton
import com.lianyi.paimonsnotebook.ui.theme.BackGroundColor
import com.lianyi.paimonsnotebook.ui.theme.Font_Primary
import com.lianyi.paimonsnotebook.ui.theme.Transparent

/*
* 通行证扫码登录弹窗
* 显示二维码,由米游社App扫码并在App内确认后完成登录
* */
@Composable
fun PassportQRCodeLoginPopup(
    visible: Boolean,
    bitmap: Bitmap?,
    statusText: String,
    onRequestDismiss: () -> Unit,
    onOpenMiyoushe: () -> Unit
) {
    BasePopup(
        visible = visible,
        onRequestDismiss = {},
        backgroundColor = BackGroundColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(PaddingValues(16.dp, 8.dp)),
            verticalArrangement = Arrangement.SpaceBetween
        ) {

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                StatusBarPaddingSpacer()

                Crossfade(
                    modifier = Modifier
                        .padding(36.dp)
                        .size(180.dp), targetState = bitmap == null, label = ""
                ) {
                    if (it) {
                        Image(
                            painter = painterResource(id = R.drawable.emotion_icon_nahida_drink),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        AsyncImage(
                            model = bitmap,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                        )
                    }
                }

                InfoText(text = statusText)
                InfoText(text = "使用米游社App扫一扫,或点击下方按钮在本机确认")
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                TextButton(text = "在本机米游社中确认") {
                    onOpenMiyoushe.invoke()
                }

                TextButton(
                    text = "取消登录",
                    textColor = Font_Primary,
                    backgroundColor = Transparent,
                    onClick = onRequestDismiss
                )

                NavigationBarPaddingSpacer()
            }
        }
    }
}
