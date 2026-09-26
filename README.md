![](https://cdn.jsdelivr.net/gh/QooLianyi/PaimonsNotebook.github.io/bg_paimonsnotebook_uigf.webp)

派蒙笔记本是一个安卓平台下开源的原神工具，能够提供游戏相关的一些便捷服务，如：实时便笺、深渊记录查询、祈愿记录分析、成就管理、多用户管理、丰富的可自定义样式的桌面组件、角色资料、武器资料、怪物资料、素材刷新日历、...

> [!IMPORTANT]
> 本仓库是 [QooLianyi/PaimonsNotebook](https://github.com/QooLianyi/PaimonsNotebook) 的维护分支。上游自 2024 年 11 月起停止更新，且其依赖的 Snap.Hutao 服务已关停，本分支重建了元数据与图片源，并移植了胡桃工具箱的部分功能。
>
> 本分支在原版基础上持续更新，当前版本 **1.8.28**。逐版本变更记录请见 [Releases](https://github.com/hlzlzlz/PaimonsNotebook/releases)。

## 功能

- **实时便笺**：树脂、家园币、每日委托、参量质变仪、派遣等状态查询，支持后台周期提醒（树脂阈值可调，可选游戏运行时免打扰）
- **签到记录**：当前角色当月签到进度、漏签天数、补签卡余额与每日奖励一览；支持米游社自动签到与自动补签（默认关闭）
- **我的角色**：角色列表与详情，含属性、天赋、命座、武器与圣遗物评分（自动 / 手动权重双模式）
- **祈愿记录**：记录获取与导入导出，含保底进度、UP 复刻倒计时、出金分析与千星奇域（UGC）记录；支持 UIGF v3.0 / v4.0 / v4.1 / v4.2
- **资料库**：角色、武器、怪物、圣遗物四类资料查询
- **养成素材**：养成计划（含从米游社同步角色等级与天赋、树脂预估）与素材刷新日历
- **战斗记录**：深境螺旋与幻想真境剧诗，含全服统计（出场率 / 使用率 / 持有率 / 配队）与本地历史存档
- **成就管理**：成就进度管理，支持 UIAF 导入导出与三种合并策略
- **桌面组件**：多种尺寸与样式的可自定义桌面组件
- **游戏公告**：公告列表与详情，支持图文、表格、折叠等富文本正文
- **洞天摹本**：洞天摹本与家具计算
- **队伍 DPS**：队伍伤害计算器

此外还有旅行者札记（首页卡片进入）、通行证扫码登录、多账号管理、米游社 H5 页面、帖子与话题浏览等。

## 与上游版本的主要差异

上游停在 1.8.0，本分支在功能、数据源与稳定性三方面都有较大改动。

### 数据源重建

- 元数据改用 [Snap.Metadata](https://github.com/SnapHutaoRemasteringProject/Snap.Metadata) 社区镜像，主源 + 备用源自动切换
- 图片改用社区静态资源服务，并提供离线图标包与内置低清缩略图，弱网下也能快速出图
- 风控相关对齐社区工具箱做法：DS 盐值与 `x-rpc-app_version` 配套更新，设备指纹按服务端规范签发并在有效期内静默沿用，请求头缺什么补什么，消除重复与矛盾的指纹字段
- 风控自动验证：App 内滑块验证替代网页跳转

### 功能移植与新增

移植自胡桃工具箱的功能包括：深渊数据库与全服统计、圣遗物评分、素材刷新日历、养成计划树脂预估、祈愿保底与复刻估算、通行证扫码登录等。

本分支新增的功能包括：战斗记录、游戏公告、洞天摹本、队伍 DPS 计算器、签到记录、自动签到与补签、便笺提醒、兑换码、旅行者札记、UIAF 剪贴板导入等。

### 稳定性与安全

- 修复安卓 8.1 图片解码崩溃、怪物资料页重复键崩溃、应用内更新链路失效等多项缺陷
- 元数据下载失败不再杀进程，改为给出可见的失败出口与重试入口
- 用户凭证（cookie / ltoken / stoken）改为加密存储
- WebView 桥接增加来源校验，并为 FileProvider、Zip 解压等路径加固
- 协程异常统一兜底，避免后台异常导致进程静默退出；新增崩溃本地留痕与导出

### 工程

- Gradle wrapper 改回官方源，Release 签名配置就绪（密钥不入库）
- 建立单元测试回归闸门
- 移除 LeakCanary（其堆转储分析会造成启动与切页的持续卡顿）

## 下载使用

- 派蒙笔记本仅适用于 Android 8 (API 26) 至更高版本的 Android 设备

#### 从本仓库获取

- [点击此处下载最新版本](https://github.com/hlzlzlz/PaimonsNotebook/releases/latest)
- 也可以直接在仓库的 `app/release` 目录下下载（jsDelivr 链接可能存在缓存延迟）

## 特别感谢

派蒙笔记本离不开以下组织、项目、个人的帮助(排名不分先后)：

- [QooLianyi](https://github.com/QooLianyi) 及上游仓库的贡献者
- [UIGF organization](https://uigf.org/)
- [Snap.Hutao](https://github.com/DGP-Studio/Snap.Hutao) / [Snap.Metadata 社区镜像](https://github.com/SnapHutaoRemasteringProject/Snap.Metadata)
- [HolographicHat](https://github.com/HolographicHat)

#### 使用的技术栈

- [Kotlin](https://github.com/JetBrains/kotlin)
- [Jetpack Compose](https://developer.android.com/jetpack/compose)
