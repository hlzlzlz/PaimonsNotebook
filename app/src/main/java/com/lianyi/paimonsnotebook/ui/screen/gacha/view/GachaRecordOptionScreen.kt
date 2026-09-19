package com.lianyi.paimonsnotebook.ui.screen.gacha.view

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResult
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import com.lianyi.paimonsnotebook.common.components.dialog.LazyColumnDialog
import com.lianyi.paimonsnotebook.common.components.lazy.ContentSpacerLazyColumn
import com.lianyi.paimonsnotebook.common.components.loading.LoadingAnimationPlaceholder
import com.lianyi.paimonsnotebook.common.components.widget.ProgressBar
import com.lianyi.paimonsnotebook.common.core.base.BaseActivity
import com.lianyi.paimonsnotebook.common.extension.modifier.radius.radius
import com.lianyi.paimonsnotebook.common.extension.string.warnNotify
import com.lianyi.paimonsnotebook.common.util.metadata.genshin.uigf.UIGFExportVersion
import com.lianyi.paimonsnotebook.ui.screen.account.components.dialog.UserGameRolesDialog
import com.lianyi.paimonsnotebook.ui.screen.gacha.components.dialog.ChooseGameUidDialog
import com.lianyi.paimonsnotebook.ui.screen.gacha.viewmodel.GachaRecordOptionScreenViewModel
import com.lianyi.paimonsnotebook.ui.screen.setting.components.SettingOptionGroup
import com.lianyi.paimonsnotebook.ui.theme.BackGroundColor
import com.lianyi.paimonsnotebook.ui.theme.Black_60
import com.lianyi.paimonsnotebook.ui.theme.CardBackGroundColor
import com.lianyi.paimonsnotebook.ui.theme.PaimonsNotebookTheme
import com.lianyi.paimonsnotebook.ui.theme.Primary_2

class GachaRecordOptionScreen : BaseActivity() {

    private val viewModel by lazy {
        ViewModelProvider(this)[GachaRecordOptionScreenViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        registerRequestPermissionsResult()
        viewModel.startActivity = registerStartActivityForResult()

        viewModel.storagePermission = this::checkStoragePermission

        setContent {
            PaimonsNotebookTheme(this) {
                Content()
            }
        }
    }

    @Composable
    private fun Content() {
        ContentSpacerLazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(BackGroundColor),
            contentPadding = PaddingValues(12.dp, 8.dp)
        ) {
            item {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SettingOptionGroup(groupName = "祈愿记录", list = viewModel.gachaSettings)
                    SettingOptionGroup(groupName = "记录获取", list = viewModel.importSettings)
                    SettingOptionGroup(groupName = "记录导出", list = viewModel.exportSettings)
                    SettingOptionGroup(groupName = "关于", list = viewModel.aboutSettings)
                }
            }
        }

        if (viewModel.showLoadingDialog) {
            LazyColumnDialog(
                title = viewModel.loadingDialogTitle,
                titleSpacer = 24.dp,
                onDismissRequest = {},
                buttons = arrayOf()
            ) {
                item {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {

                        LoadingAnimationPlaceholder(shadowDp = 0.dp)

                        Text(text = viewModel.loadingDialogDescription, fontSize = 14.sp)

                        val progressBarValueAnim by animateFloatAsState(
                            targetValue = viewModel.loadingDialogProgressBarValue,
                            label = ""
                        )

                        ProgressBar(
                            progress = progressBarValueAnim,
                            progressColor = Primary_2,
                            modifier = Modifier.height(8.dp)
                        )
                    }
                }
            }
        }

        if (viewModel.showGameRoleDialog) {
            UserGameRolesDialog(
                onButtonClick = viewModel::dismissGameRoleDialog,
                onDismissRequest = viewModel::dismissGameRoleDialog,
                onSelectRole = viewModel::onSelectGameRole
            )
        }

        if (viewModel.showChooseExportUidDialog) {
            ChooseGameUidDialog(
                uidList = viewModel.gachaRecordGameUidList,
                onConfirm = viewModel::confirmExportSelectedUidRecord,
                viewModel::dismissChooseExportUidDialog
            )
        }

        //UIGF 导出格式选择
        if (viewModel.showUIGFVersionDialog) {
            LazyColumnDialog(
                title = "UIGF 导出格式",
                titleSpacer = 16.dp,
                verticalSpacedBy = 6.dp,
                onDismissRequest = viewModel::dismissUIGFVersionDialog,
                buttons = arrayOf("取消"),
                onClickButton = { viewModel.dismissUIGFVersionDialog() }
            ) {
                items(UIGFExportVersion.all) { version ->
                    UIGFVersionItem(
                        version = version,
                        selected = version.storageValue == viewModel.currentUIGFVersionStorageValue,
                        onClick = { viewModel.onSelectUIGFVersion(version) }
                    )
                }
            }
        }

        /*
        * 删除指定 uid 的祈愿记录
        *
        * 按钮用"删除"而非"确认":该操作不可撤销,按钮文案要能反映后果。
        * 标题里带上待删 uid,避免用户在多个 uid 间选错。
        * */
        if (viewModel.showDeleteUidDialog) {
            LazyColumnDialog(
                title = "删除祈愿记录",
                titleSpacer = 12.dp,
                verticalSpacedBy = 4.dp,
                onDismissRequest = viewModel::onDeleteUidDialogDismissRequest,
                buttons = arrayOf("取消", "删除"),
                onClickButton = {
                    if (it == 1) {
                        viewModel.onConfirmDeleteUid()
                    } else {
                        viewModel.onDeleteUidDialogDismissRequest()
                    }
                }
            ) {
                item {
                    Text(
                        text = "将删除 uid ${viewModel.pendingDeleteUid} 的全部祈愿记录," +
                                "此操作不可撤销。删除后可通过[记录获取]重新拉取。",
                        fontSize = 13.sp,
                        color = Black_60,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }

                items(viewModel.gachaRecordGameUidList) { uid ->
                    val selected = uid == viewModel.pendingDeleteUid

                    Row(
                        modifier = Modifier
                            .radius(4.dp)
                            .fillMaxWidth()
                            .clickable { viewModel.onDeleteUidSelect(uid) }
                            .background(if (selected) CardBackGroundColor else Color.Transparent)
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = uid,
                            fontSize = 15.sp,
                            color = if (selected) Primary_2 else Color.Unspecified
                        )

                        if (selected) {
                            Text(text = "✓", fontSize = 15.sp, color = Primary_2)
                        }
                    }
                }
            }
        }
    }

    /*
    * UIGF 版本选项
    *
    * 与项目其它选项保持一致:卡片底色 + radius(6.dp),选中项用主色文字区分。
    * */
    @Composable
    private fun UIGFVersionItem(
        version: UIGFExportVersion,
        selected: Boolean,
        onClick: () -> Unit
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .radius(6.dp)
                .background(CardBackGroundColor)
                .clickable(onClick = onClick)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = if (selected) "✓ ${version.label}" else version.label,
                fontSize = 15.sp,
                color = if (selected) Primary_2 else Color.Unspecified
            )

            Text(
                text = version.description,
                fontSize = 12.sp,
                color = Black_60
            )
        }
    }

    override fun onRequestPermissionsResult(result: Boolean) {
        viewModel.showRequestPermissionDialog = !result
        if (!result) {
            "没有获取到所需权限".warnNotify()
        }
    }

    override fun onStartActivityForResult(result: ActivityResult) {
        viewModel.activityResult(result)
    }
}