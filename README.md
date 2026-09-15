![](https://cdn.jsdelivr.net/gh/QooLianyi/PaimonsNotebook.github.io/bg_paimonsnotebook_uigf.webp)

派蒙笔记本是一个安卓平台下开源的原神工具，能够提供游戏相关的一些便捷服务，如：实时便笺、深渊记录查询、祈愿记录分析、成就管理、多用户管理、丰富的可自定义样式的桌面组件、角色资料、武器资料、怪物资料、素材刷新日历、...

> [!IMPORTANT]
> 本仓库是 [QooLianyi/PaimonsNotebook](https://github.com/QooLianyi/PaimonsNotebook) 的维护分支。上游自 2024 年 11 月起停止更新，且其依赖的 Snap.Hutao 服务已关停，本分支重建了元数据与图片源，并移植了胡桃工具箱的部分功能。

## 本分支的主要改动（基于上游 1.8.0）

### 修复

- **元数据/图片源重建**：改用 [Snap.Metadata](https://github.com/SnapHutaoRemasteringProject/Snap.Metadata) 社区镜像（jsDelivr）与社区静态资源服务，DS 盐值与 2.95.1 版本号配套
- **安卓 8.1 图片解码崩溃**：不再注册 GifDecoder（Coil 2.6 的 Movie 实现有缺陷），改用系统 BitmapFactory 解码静态首帧
- **图片下载健壮性**：HTTP 状态与 JSON 格式校验，失败不再写入垃圾文件；jsDelivr 主源 + fastly 备用源自动切换
- **降低 1034 风控触发频率（设备指纹对齐社区工具箱做法）**：getFp 按服务端收紧后的规范签发（16 位十六进制 device_id、13 位十六进制指纹候选），只接受合法指纹并在 7 天内沿用、刷新失败不再随机漂移；请求头缺什么补什么，消除 device_fp / client_type 重复矛盾；指纹注册设备信息与声明版本一致
- **1034 风控自动验证**：App 内滑块验证替代网页跳转（静默校验优先，失败才弹窗）
- **怪物资料页稳定性**：Monster.json 中大量 Id=0 条目的列表重复键崩溃改为按名称作键；详情弹窗对缺失 BaseValue/Description/Drops 的条目做空值防护

### 新增功能（移植自胡桃工具箱）

- **通行证扫码登录**（createQRLogin / queryQRLoginStatus）
- **旅行者札记**：月度原石/摩拉收支与来源饼图，首页摘要卡片
- **战斗记录**：幻想真境剧诗 + 幽境危战（双标签，按需加载）
- **米游社自动签到**（luna 接口，WorkManager 每日调度，默认关闭，风控时通知手动签到）
- **深渊数据库**：合并深境螺旋与全服数据库页（总览/出场率/使用率/配队），胡桃 API 只读

### 新增功能（1.8.2）

- **实时便笺提醒**：树脂/家园币/每日委托/参量质变仪/派遣条件达成通知（WorkManager 30/60 分钟调度，默认关闭）
- **祈愿保底与复刻估算**：大小保底进度、期望抽数、UP 复刻倒计时（基于 GachaEvent 事件表）
- **圣遗物评分**：移植胡桃自动模式计分并修正权重（非推荐词条不再直接归零）
- **全服统计**：深渊持有率、幻想真境剧诗全服数据并入各自页面
- **怪物资料页**：按称号分组，详情含基础属性、全元素抗性、掉落物
- **素材刷新日历**：一周七天天赋/周本轮换表与当日角色生日；原「养成材料」独立页并入为第二标签
- **当期卡池首页卡片**：替代原活动日历页（已移除），展示角色/武器/混池与剩余时间

### 新增功能（1.8.3）

- **前瞻直播兑换码**：首页卡片在直播期间自动展示官方兑换码（miyolive 接口），逐条或全部复制
- **圣遗物评分手动权重**：自动（推荐词条）/手动（7 项权重自定义）双模式，角色详情评分卡内设置
- **培养计划树脂预估**：移植胡桃树脂统计公式，按材料类型估算刷本次数、总树脂与预计天数

### 其他

- 角色资料页新增攻略跳转
- 图片加载错误诊断日志（ImageErrorLogger）
- Gradle wrapper 改回官方源；Release 签名配置就绪（密钥不入库）

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
