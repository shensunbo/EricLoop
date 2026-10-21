# 朋友好感度功能实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. 在当前会话按模块顺序实施；不主动派发子代理。

**Goal:** 在 EricLoop 底部新增朋友入口，实现人物管理、好感度事件、时间线、排行和独立 GitHub 备份。

**Architecture:** 朋友功能使用独立的 Room 数据库 `friends.db`，由专属 Repository、ViewModel 和 Compose 页面组成。仅在应用导航和通用视觉组件处接入现有应用；共用 GitHub 连接配置，备份文件使用主仓库 `data/ericloop/ppl.json`，同步基线、恢复预览和本地安全副本与原功能隔离。

**Tech Stack:** 现有 Kotlin、Compose Material 3、Room/KSP、StateFlow、Kotlin Serialization、OkHttp 和 Android Keystore。

---

## 需求依据与实施边界

需求来源：`doc/spec/draft_spec.md` 及本次会话确认。

- 人物使用昵称、性别、简介、备注和可配置初始好感度。昵称必填，其余资料选填；性别可为男、女、未设置，分别显示男孩、女孩、通用人物默认图标。
- 使用 Google Material Symbols 本地图标资源，不引入头像服务或头像选择库。
- 新增事件只能选择 `+1` 或 `-1`，事件说明必填，发生时间由系统生成，不支持补记。
- 已有事件允许修改说明与整数分值，发生时间保持不变；不保留修改前的副本，不生成编辑审计记录，不支持单独删除事件。
- 当前好感度为初始值与全部事件分值之和。初始值在人物编辑中可修改，不生成虚构事件。
- 允许负数，不设置业务上下限。使用十进制字符串持久化任意精度整数，业务计算使用 `BigInteger`，避免固定整数溢出。
- 时间线展示事件当前内容、发生时间和分值，按发生时间倒序；编辑不改变原节点的位置。
- 最近互动时间按事件发生时间计算，编辑人物或事件不改变它；没有事件的人物不伪装成有互动。
- 排序支持好感度、最近互动时间、创建时间，均可升降序。默认好感度降序，同分按最近互动时间降序，再按创建时间和 ID 稳定排序；无互动者在互动排序中始终排最后。
- 底部新增“朋友”，默认进入人物管理页；页内“排行”按钮进入独立排行页，点击人物进入详情。
- 排行仅显示名次、头像、昵称、性别、简介、分数；详情显示完整资料及时间线。
- 人物颜色自动分配、不可手动修改、当前人物间不重复，存储于数据库并随备份保留。
- 删除人物前确认，永久删除该人物及关联事件，不接入原有回收站。
- 手机使用独立 Room；GitHub 使用同一连接配置和主仓库 `data/ericloop/ppl.json`。原文件 `data/ericloop/backup.json` 保留原有用途。
- 不新增或运行单元测试。通过模块编译和指定真机功能检查验证；无可用设备时明确记录尚未验证的项目。
- 此计划只描述朋友功能，不扩展原有记录、标签、历史或回收站模型。

## 文件与职责

统一新包路径：`app/src/main/java/com/example/ericloop/friends/`。

| 文件 | 职责 |
| --- | --- |
| `data/FriendModels.kt` | 性别枚举、人物、事件、备份 DTO、分数转换 |
| `data/FriendsDatabase.kt` | Room 实体、DAO、数据库定义 |
| `data/FriendColors.kt` | 唯一颜色分配与恢复校验 |
| `data/FriendsBackupValidation.kt` | 独立 JSON 格式、引用和分值校验 |
| `data/FriendsRepository.kt` | 初始化、事务、CRUD、导出和恢复 |
| `FriendsViewModel.kt` | 页面状态、操作状态、筛选排序和错误反馈 |
| `ui/FriendsPage.kt` | 人物管理列表、搜索、排行入口 |
| `ui/FriendEditor.kt` | 新增和编辑人物 |
| `ui/FriendDetail.kt` | 详情、删除确认、事件时间线 |
| `ui/FriendEventEditor.kt` | 新增与编辑事件 |
| `ui/FriendsRanking.kt` | 独立排行及排序控件 |
| `ui/FriendComponents.kt` | 头像、性别、颜色与分数等功能内组件 |
| `ui/FriendsHost.kt` | 功能内路由、返回栈、恢复状态 |
| `sync/FriendsGithubSync.kt` | `ppl.json` 同步基线、状态、预览、恢复 |
| `ui/FriendsSyncSection.kt` | 朋友功能的手动备份操作与预览 |

现有文件的改动：

- `ui/App.kt`：第五个底部入口、朋友页面宿主、标题和原数据库加载门禁调整。
- `MainActivity.kt`：持有独立朋友 ViewModel，传递给应用入口。
- `sync/GithubSync.kt`：提取共用连接和 GitHub 文件传输，保留原业务备份校验与流程。
- 新增 `sync/GithubConnection.kt`：现有连接配置、Token 加解密、清除授权。
- 新增 `sync/GithubFileClient.kt`：按路径获取 blob、校验 SHA、提交单个文件和非强制更新引用。
- `res/drawable/`：新增朋友入口及默认头像 VectorDrawable，沿用现有许可说明。
- `readme.md`、`doc/architecture.md`、`doc/implementation.md`、`version.properties`：功能说明、架构、验证记录和最终版本信息。

## 模块 1：独立数据库与业务模型

**涉及文件：** `friends/data/` 下六个文件。

- [x] 定义 `FriendGender`（男、女、未设置），人物 DTO 和事件 DTO。人物字段为 `id/name/gender/intro/notes/initialScore/colorArgb/createdAt/updatedAt`；事件字段为 `id/friendId/delta/description/occurredAt/updatedAt`。
- [x] 定义 `FriendsBackup`：`schemaVersion=1`、`datasetId`、`revision`、`exportedAt`、`friends`、`events`。人物颜色、初始值和事件分值都进入备份。
- [x] 分值统一解析为 `BigInteger`，导出规范十进制字符串；拒绝空值、小数、科学计数法。编辑允许任意整数，新增事件只接受 ±1。
- [x] Room 建立 `friends`、`friend_events`、单行 `friends_metadata` 表；分数字段为字符串。事件通过外键关联人物并建立 `friendId` 索引，删除人物使用级联删除。
- [x] 数据库文件为 `friends.db`，独立 schema 版本从 1 开始，不修改 `ericloop.db`。好友数据流与原 `Backup` 完全分开。
- [x] 颜色使用不透明 RGB 值，先使用可读的预设颜色，再通过确定性候选序列补充分配；所有候选都检查数据库现存颜色。数据库为颜色设置唯一索引，创建事务内完成分配；空间耗尽时明确报错，不重复分配。
- [x] 实现 Repository 的人物创建、编辑、删除，事件新增、编辑，一致快照和恢复接口。昵称、事件说明 trim 后不能为空；同名人物允许存在，以 ID 区分。
- [x] 所有写入在事务内更新业务表、revision 与 updatedAt；无实际变化不增加 revision。事件编辑只更新该行，人物当前分数始终由初始值与事件计算。
- [x] 实现备份严格校验：版本、唯一 ID、唯一颜色、人物引用、整数格式、非空昵称及说明、时间戳、修订号；非法备份不得写入数据库。
- [x] 恢复前保存应用私有目录的 `friends-pre-restore-*.json`，再通过单事务替换朋友数据；失败不发布新的内存状态。
- [x] 执行 `./gradlew :app:assembleDebug`，预期 `BUILD SUCCESSFUL`，确认 Room/KSP 和序列化编译通过。

**模块验收：** 独立数据库可构建；原有模型和数据库 schema 无改动。数据持久化与事务行为在页面接入后通过真机检查。

## 模块 2：人物管理与详情

**涉及文件：** `FriendsViewModel.kt`、`ui/FriendsPage.kt`、`ui/FriendEditor.kt`、`ui/FriendDetail.kt`、`ui/FriendComponents.kt`、默认头像资源。

- [x] ViewModel 暴露独立的加载、错误、人物列表和写入状态，采用 `viewModelScope`；Repository 在数据库事务提交后通过 StateFlow 发布数据；所有数据库写入均经 Repository。
- [x] 人物列表支持新增、按昵称搜索、点击详情；显示头像、颜色标识、昵称、性别、简介和当前分数，增加空列表及无搜索结果状态。
- [x] 人物编辑包含昵称、性别、简介、备注和初始分值；新建默认初始值 0，允许覆盖；编辑保留 ID、颜色和创建时间。
- [x] 使用本地男孩、女孩、通用人物矢量头像；颜色作为独立标识，不单靠颜色表达性别或分数正负。
- [x] 详情展示完整资料和当前总分，提供编辑人物、增加分数、减少分数、删除人物入口。
- [x] 删除弹窗明确会同时删除全部事件；确认后提交事务并回到原列表。重复点击保存或删除时禁用操作，失败保留输入并提示。
- [x] 编辑中返回若有未保存内容，提示放弃或继续编辑；键盘、长备注、负数输入与系统返回均可正常操作。
- [x] 执行 `./gradlew :app:assembleDebug`，预期 `BUILD SUCCESSFUL`。

**模块验收：** 页面可编译；通过模块 5 导航接入后，检查人物增删改查、空值拦截、重启保留与颜色唯一。

## 模块 3：好感度事件与时间线

**涉及文件：** `ui/FriendEventEditor.kt`、`ui/FriendDetail.kt`、`FriendsViewModel.kt`。

- [x] 点击 `+1` 或 `-1` 打开事件弹窗，显示本次变动，说明必填；仅在确认保存后写入分数事件，取消不变更数据。
- [x] 新增事件时间取提交时的系统时间，不提供日期选择器，不接受外部补记时间。
- [x] 编辑事件时展示原说明和分值；允许修改为任意整数，包括负数和 0；保存后重新计算总分，不追加第二条事件或编辑审计节点。
- [x] 时间线展示每条事件的发生时间、说明和带符号分值，倒序稳定排列；不提供单条删除操作。
- [x] 事件提交时再次确认人物仍存在；编辑时确认事件仍存在，缺失则提示并刷新页面，避免在恢复或删除后重建失效对象。
- [x] 更新分数后同步刷新详情、管理列表和排行；事件编辑不改变最近互动时间。
- [x] 执行 `./gradlew :app:assembleDebug`，预期 `BUILD SUCCESSFUL`。

**真机验收案例：** 初始值设为 -2，新增 +1、-1 后为 -2；把第一条事件改为 +5 后为 2；事件仍为两条且发生时间不变。空白说明不能提交，取消操作不增加事件，重启后数值一致。

## 模块 4：独立排行

**涉及文件：** `ui/FriendsRanking.kt`、`ui/FriendsPage.kt`、`FriendsViewModel.kt`。

- [x] 管理页增加明确的“排行”按钮，进入独立页面；排行仅显示简要资料，完整备注留在详情页。
- [x] 提供好感度、最近互动时间、创建时间三种排序与升降序选择，默认好感度降序。
- [x] 使用 `BigInteger` 数值比较，禁止直接按字符串排序；时间相同和分数相同时按需求规则及 ID 稳定排序。
- [x] 名次采用排序后的连续序号，不实现并列名次。点击排行条目进入详情，返回时保留排序方式与列表位置。
- [x] 执行 `./gradlew :app:assembleDebug`，预期 `BUILD SUCCESSFUL`。

**真机验收案例：** 分数 -10、-2、0、2、10 的数值顺序正确；同分时最近互动者在前；无事件人物排在互动排序末尾；三种排序切换及详情返回状态正确。

## 模块 5：底部入口与独立生命周期

**涉及文件：** `MainActivity.kt`、`ui/App.kt`、`friends/ui/FriendsHost.kt`、朋友导航图标。

- [x] Activity 通过独立 `FriendsViewModel` 持有朋友功能状态，原 `LoopViewModel` 保持原有业务职责。
- [x] 底部导航增加“朋友”；在 tab 判定、标题、返回处理、强调色中注册新入口，不增加原有记录数量统计。
- [x] `FriendsHost` 管理 `list/ranking/detail/editor` 和当前人物 ID，使用可保存状态保留路由。详情从哪里进入就返回哪里；朋友根页面保持主导航一致的系统返回行为。
- [x] 将原数据库的 loading/error 判断限制在原有业务页面；朋友页面自行显示自己的 loading/error。原 GitHub 同步状态不得阻断朋友本地编辑。
- [x] 原有新增记录浮动按钮保持在记录首页，朋友新增按钮归朋友管理页；详情及编辑页按现有二级页面习惯隐藏主底栏。
- [x] 朋友数据恢复或人物删除导致当前 ID 失效时，返回朋友列表并清理失效编辑状态。
- [x] 执行 `./gradlew :app:assembleDebug`，预期 `BUILD SUCCESSFUL`；检查五项底部导航的文字、图标和点击区域。

**真机验收：** 原四个入口正常，朋友的列表、排行、详情、编辑返回路径正确；旋转屏幕后页面状态合理；两个功能独立加载，无跨功能错误提示和统计。

## 模块 6：主仓库 ppl.json 独立备份

**涉及文件：** `sync/GithubConnection.kt`、`sync/GithubFileClient.kt`、原 `sync/GithubSync.kt`、`friends/sync/FriendsGithubSync.kt`、`friends/ui/FriendsSyncSection.kt`。

- [x] 提取现有连接配置与 Keystore 操作，保留既有 SharedPreferences 名称、字段和密钥别名，确保原 Token 与连接无需重新填写。原设置页仍为唯一连接设置入口。
- [x] 提取按文件路径工作的 GitHub 传输客户端：读 HEAD/tree/blob、大小和 SHA 校验、创建 blob/tree/commit、非强制更新 ref、最多三次并发重试、响应不明时核对 blob。保留现有超时及关闭重定向规则。
- [x] 原 `GithubSync` 继续使用 `data/ericloop/backup.json`、原偏好字段和原备份模型；提取完成先编译并检查原流程，再接朋友同步。
- [x] `FriendsGithubSync` 固定使用路径 `data/ericloop/ppl.json`，独立偏好命名空间 `friends_github_sync`，维护 blob SHA、datasetId、revision、成功时间和恢复预览。
- [x] 主仓库切换用户、仓库或分支时，使两个功能各自旧基线及预览失效；清除授权对两者生效。朋友同步记录连接指纹，避免跨仓库误用旧基线。
- [x] 在朋友管理页提供备份入口，包含手动同步、强制上传、下载预览及确认恢复；显示朋友功能自己的待上传状态和最后成功时间。
- [x] 沿用 20 MiB 备份及 32 MiB API 响应上限。已有 `ppl.json` 必须先按本功能 schema 校验；空文件、任意旧 JSON 或不兼容格式不能自动当成有效备份或覆盖。
- [x] 首次同步发现云端文件且无可信基线时，提示先下载恢复或明确确认强制上传；远端文件不存在时允许普通上传创建。
- [x] 强制上传仍只替换 `ppl.json`，基于最新 tree 追加提交，不 force push，不触碰原备份或代码。
- [x] 恢复预览显示导出时间、人物数、事件数，说明将替换朋友数据；确认前再次核对云端 blob、本地 revision 与连接指纹，任一变化要求重新下载预览，防止覆盖预览后新增的本地编辑。
- [x] 恢复先做本地安全副本，再执行独立数据库替换；颜色重复、引用断裂、格式错误、权限错误或断网均保留原数据库。
- [x] 执行 `./gradlew :app:assembleDebug`，预期 `BUILD SUCCESSFUL`。

**真机验收：** 未授权提示、首次已有文件冲突、正常上传、无变化上传、强制上传、下载预览、取消恢复、确认恢复、格式错误及断网保留数据。真实远端写入按用户授权执行，不为验证临时篡改正式云端文件；未实测项如实记录。比较上传前后提交涉及的路径，确认两个 JSON 不互相覆盖。

## 模块 7：整体验收与文档

**涉及文件：** `readme.md`、`doc/architecture.md`、`doc/implementation.md`、`version.properties`。

- [x] 执行 `adb devices -l` 确认可用设备；README 原指定序列号为 `10AE970D5P0017U`，若未连接，不自动选择其他设备执行安装。
- [x] 最终执行 `./gradlew :app:assembleDebug`，预期 `BUILD SUCCESSFUL`。
- [x] 指定真机安装：`adb -s 10AE970D5P0017U install -r app/build/outputs/apk/debug/app-debug.apk`，预期 `Success`；启动：`adb -s 10AE970D5P0017U shell am start -n com.example.ericloop/.MainActivity`。
- [ ] 完成模块 2 至 6 的真机案例，补查长昵称、中文、空列表、超长整数、连续点击、重启持久化、人员删除后的全部事件清理。
- [ ] 回归原有想法编辑、转计划、打卡、标签、历史和回收站；确认原数据库和原备份可继续读取。
- [x] 更新文档：两个数据域、入口、分值公式、事件编辑规则、删除语义、路径 `data/ericloop/ppl.json`、独立恢复及不包含编辑审计历史。
- [x] 交付代码时按仓库规则递增补丁版本与 VERSION_CODE；当前版本为 1.0.6（7），不自动提交代码。
- [x] 在实施记录中填写编译结果、设备序列号、已验证功能和未验证功能，不把计划中的验收项记录为已通过。

## 执行与交付规则

1. 按模块 1 → 7 顺序执行，每个模块编译通过后继续；编译失败先解决，不累积到最后。
2. 不新增测试依赖，不新增或运行单元测试，不为本功能重构原有记录业务。
3. 计划中的页面和数据接口可按编译约束细化，已确认的业务规则和两个备份文件的隔离边界必须保留。
4. 本计划已获用户授权实施；不自动提交、推送或修改真实云端备份。

## 计划自查

- [x] 覆盖人物资料、默认头像、初始值、±1 新增、必填事件和可编辑分值。
- [x] 覆盖禁止补记、无编辑审计、人物与事件一起删除。
- [x] 覆盖默认排序、独立排行、详情跳转、自动唯一颜色。
- [x] 明确手机独立 Room、主仓库 `data/ericloop/ppl.json`、独立同步和恢复。
- [x] 覆盖原数据库加载门禁、导航返回和原有功能回归。
- [x] 遵循不新增或运行单元测试的用户要求。

## 实施补充

用户追加界面要求：新增用加号，排行用排行榜图标，其他适合图标的操作也改为图标。所有操作图标提供无障碍说明和长按提示；确认弹窗保留明确文字。

## 最终验收状态（2026-10-21）

代码、编译、Lint 和 USB 真机主要业务检查已完成，详见 `doc/implementation.md` 的 M21。设备安装版本为 1.0.6（7），临时人物与事件已清理，原数据域源码及数据库 schema 保持不变。界面已复核图标操作、资料 CRUD、计分、事件编辑、排序、重启持久化及级联删除。

剩余验证：真实云端上传/恢复、远端并发与断网、超长整数等边界及原功能完整写操作回归。只读下载显示当前配置的 main 分支根目录无 ppl.json，本轮未写入云端；实现支持首次手动同步创建。上述剩余项是验收范围限制，不代表功能尚未实现。

## 备份路径更正（用户截图确认）

实际朋友备份文件为 `data/ericloop/ppl.json`，与 `backup.json` 同目录。此前根目录查询属于旧路径检查。同步基线增加备份路径校验，旧根路径的 SHA 和已上传状态不复用；不自动删除或改写旧根目录文件。
