# EricLoop 详细设计

状态：对应 2026-09-30 当前实现。架构与依赖边界见 [architecture.md](architecture.md)，产品规则见 [spec.md](spec.md)。这里记录实际数据结构、页面跳转、写入和同步算法，后续改动应同步修订。

## 代码入口与职责

| 文件 | 职责 |
| --- | --- |
| `MainActivity.kt` | 创建 `LoopViewModel`，持有仓库与同步对象并装载 Compose。 |
| `ui/App.kt` | 页面路由、返回目标、顶部/底部导航、异步动作、设置与同步确认弹窗。 |
| `ui/Records.kt` | 首页概览、进行中/想法/已完成/标签/回收站列表及组合筛选。 |
| `ui/Details.kt` | 想法/计划编辑、详情、转换、状态和长期计划打卡 UI。 |
| `ui/Tags.kt` | 标签目录、自定义标签新增/编辑、Emoji 选择。 |
| `ui/History.kt` | 记录标题目录、单条记录 Graph、事件筛选与快照展示。 |
| `ui/Design.kt`、`MetallicGold.kt`、`EmojiPicker.kt` | 主题配色、完成态金属背景、通用 Emoji 选择与校验反馈。 |
| `data/Models.kt`、`Database.kt` | 业务模型/备份 JSON 和 Room 表/DAO。 |
| `data/DataRepository.kt`、`BackupValidation.kt` | 本地操作、事务、事件、全量/增量校验、恢复。 |
| `data/RecordEmoji.kt`、`TagEmoji.kt` | Emoji 合法性和标签预定义图标映射。 |
| `sync/GithubSync.kt` | Token、同步基线、GitHub 上传、下载和恢复。 |

## 领域模型与不变量

`LoopRecord` 保存 `id/title/body/kind/planType/status/tagIds/deadline/createdAt/updatedAt/deletedAt/emoji`。`kind` 为 `IDEA` 或 `PLAN`；计划类型为 `ONCE` 或 `LONG_TERM`；状态为未开始、进行中、暂停、完成、放弃。标题去首尾空白后必须非空，`tagIds` 不重复且指向现有标签，截止日期使用 ISO `yyyy-MM-dd`。`emoji` 可空；非空时用 Android ICU RGI Emoji 校验。想法未使用的计划字段保留模型默认值，不驱动想法页面。

`LoopTag` 含稳定 ID、非空且大小写不敏感唯一的名称、`preset` 和可选 `emoji`。预定义标签 ID 为 `work/study/life/health/project`，显示图标分别为 💼/📚/🏡/💪/🚀；图标由 ID 映射，预定义实体仍保留旧 `emoji=null`，以兼容既有备份与事件。自定义标签未设置图标时显示 🏷️，可通过编辑弹窗修改或清除。预定义标签不允许编辑。记录对标签只存 ID；修改标签名称/Emoji 后，当前列表显示新值，旧记录事件内仍保留操作时标签快照。

`CheckIn` 以 `recordId` 属于长期计划，保存可空白备注、实际发生时间 `occurredAt`、创建/最近操作时间和软删除时间。同一天允许多次打卡；发生时间不能在未来。`HistoryEvent` 有全局连续 `sequence`，`revision == events.size`；每次有效修改只追加一条事件，`beforeJson/afterJson` 是对应对象完整快照，记录事件额外带当时关联的标签快照。当前数据必须能由初始预定义标签和全部事件重放得到。`Backup` 的 `schemaVersion` 当前只支持 1，`datasetId` 在本地编辑中不变。

## 本地写入与恢复算法

```text
用户动作 → DataRepository.mutate()
等待初次加载 → 获取 Mutex → 基于 current 计算 next
若 next == current：直接结束
追加事件、revision + 1 → validateMutation(previous, next)
Room transaction：只更新变化的实体、追加事件、更新 metadata
事务成功 → 发布新的 StateFlow → Compose 重组
```

`saveRecord` 创建/编辑记录，保存时规范标题和 Emoji，保留旧 `createdAt`/`deletedAt`；相同内容不产生事件。`convert` 保留记录 ID 和创建时间，将想法设为选定的一次性或长期计划。`changeStatus` 只接受未删除计划。完成状态让记录进入已完成视图，改回其他状态则回普通列表。`trash` 设置 `deletedAt`，`restoreRecord` 清除它；没有永久清除。编辑或删除已有打卡后，计划的 `updatedAt` 取相应变更时间的较大值。已有关联打卡的长期计划不能改成一次性，否则历史/当前数据校验拒绝保存；UI 当前未提前禁用该类型选项，失败通过提示反馈。

`addTag` 检查唯一名称后创建自定义标签；`editTag` 可同时编辑名称与图标，只有名称变更时记“重命名标签”，图标变化时记“编辑标签”。`saveCheckIn` 根据发生日期记“打卡”或“补记打卡”，编辑/删除分别产生事件。所有操作遵守同一事务与历史规则。

`decodeBackup` 在 Kotlin Serialization 解码前先检查顶层与每行必需字段，防止缺字段被模型默认值伪装成新数据。`validateBackup` 检查版本、ID、时间、引用、预定义标签、事件合法性和完整重放；`validateMutation` 只检查新事件及新旧数据差异。恢复入口先校验，再生成带 fsync 的私有安全副本，最后在单个 Room 事务中 `replaceAll`。验证或事务失败时不发布新数据。

## 页面与导航

底部四个顶级页是记录 `home`、标签 `tags`、变更 `history`、设置 `settings`。详情、编辑、已完成、正在推进、灵感收集、标签记录、单条变更和回收站使用子路由；系统返回与标题栏返回都走 `App.kt` 的 `back()`。详情记住进入来源，返回原列表；从详情编辑后回详情；单条变更返回变更目录；回收站返回设置。页面筛选值在同一页面/配置变化期间由 `rememberSaveable` 保留，按列表模式及初始标签重新建立。

首页“正在推进”卡进入只含进行中计划的 `active` 列表，“灵感收集”卡进入只含想法的 `ideas` 列表，两页保留关键词/标签筛选并返回首页。首页默认列表不含已完成计划；已完成入口进入专页。标签目录计数包含所有未删除想法与计划，包括已完成计划；进入标签页后默认选择该标签，多标签按任选匹配，再与关键词/类型/状态条件取交集。标签列表中已完成计划排末尾，其余按 `updatedAt` 倒序。回收站只显示软删除记录。

记录详情按类型显示对应动作：想法提供纵向排列的一次性/长期转换按钮；计划显示状态选择；长期计划增加打卡列表和新增/编辑/删除弹窗。记录 Emoji 和标签 Emoji 在卡片、详情与选择控件中展示。已完成入口/卡片/详情状态使用静态磨砂金色背景、🎉/🏅 标识；普通计划和想法分别用淡绿、淡蓝，跟随系统深浅主题。

“变更”顶级页只列记录标题，包含完成及回收站中的记录；点击进入该 `recordId` 的事件 Graph。Graph 先按记录 ID，再按操作类型和本地日期范围过滤，按事件序号倒序显示；点击节点可读前后快照。它使用应用事件，不读取 Git commit，也没有事件回滚操作。标签新增/编辑事件保存于备份和重放链，但目前没有独立标签历史查看页。

## GitHub 同步状态与算法

连接设置为 owner、repo、branch 和 Token；默认目标 `shensunbo/EricLoop/main`。Token 经 Keystore AES-GCM 加密后存 `github_sync_private`，界面只暴露 `hasToken`。同一设置中记录已确认的远端 blob SHA、`uploadedRevision`、`uploadedDatasetId` 和成功时间；改变仓库或分支会清空基线。设置页待上传数量用当前修订号减已上传修订号计算，数据集不同时以当前修订号为基准。

正常上传先在本地锁下取得快照、规范列表顺序并计算 Git blob SHA；读取远端分支 HEAD、根 tree，逐级定位 `data/ericloop/backup.json`。若远端 blob SHA 与本地上次确认基线不同，要求下载恢复或用户确认强制上传；只有代码文件变化不会触发备份冲突。若远端内容与快照语义相同，直接更新同步状态。否则创建 blob、以当前 tree 为 `base_tree` 创建只替换目标路径的新 tree、提交，并以 `force=false` 更新 branch ref。分支并发更新最多重试三次；更新响应不确定时重读远端目标 blob，确认已写入才回报成功。强制上传只跳过内容基线比较，仍在当前 HEAD 追加普通提交。

下载读取目标 blob，校验大小、SHA、UTF-8、必需字段和完整历史；仅将校验通过的备份存为内存预览。确认恢复前再次检查连接设置和远端 blob SHA，随后调用仓库恢复。恢复不合并两个数据集，也不会覆盖 Git 仓库代码。上传和备份文件上限 20 MiB；HTTP JSON 响应限制 32 MiB。应用未配置有效手机端 Token 时，真实云端上传、冲突处理和恢复成功路径不能视为端到端通过。

## 变更时的设计约束与验证

新增业务操作须同时更新数据写入、事件类型、快照校验/重放、备份兼容性和对应页面；改变备份必需字段或表结构须设计明确版本迁移。新增列表不应把 `LazyColumn` 当作数据库分页。变更 GitHub 同步逻辑时必须保留只写单个数据文件、非强制更新分支和恢复前安全副本的约束。

当前项目按用户要求不新增或运行单元测试。代码变更通过 `:app:assembleDebug`、指定设备安装与相关页面/流程验收；文档变更核对代码符号、链接及 `git diff --check`。尚未验证的路径在 [implementation.md](implementation.md) 明确记录，不用“已实现”替代“已验收”。
