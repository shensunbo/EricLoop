# EricLoop 模块与文件详细设计

状态：对应 `feat/ericloop-v1` 当前实现，更新于 2026-09-30。本文覆盖仓库手写的应用源码、构建配置、运行资源和文档入口；Gradle 生成目录、设备私有数据库和本机 `local.properties` 不属于版本库。产品规则见 [spec.md](spec.md)，跨模块约束和同步时序见 [architecture.md](architecture.md)，业务算法见 [detailed-design.md](detailed-design.md)，已验证/未验证状态见 [implementation.md](implementation.md)。文件职责以代码为准，本文不是未实现功能的承诺。

## 模块边界与数据流

仓库只有一个 Gradle 应用模块 `:app`；`data`、`sync`、`ui` 是包级逻辑模块，并非可独立编译的 Gradle 子模块。

| 模块 | 输入与输出 | 持有状态 | 依赖规则 |
| --- | --- | --- | --- |
| 启动层 `com.example.ericloop` | Android Activity 启动；向 Compose 提供 `DataRepository` 与 `GithubSync` | `LoopViewModel` 在配置变化间保留两个对象 | 只做装配，不实现业务写入 |
| `data` | 接收记录、标签、打卡操作；发布完整 `Backup` 与加载状态；提供备份快照/恢复 | Room `ericloop.db`、内存 `StateFlow<Backup>`、写入 `Mutex` | 不依赖 `ui` 或 `sync`；它是业务数据唯一写入入口 |
| `sync` | 接收手动上传/下载/恢复命令；发布连接与同步状态 | 私有连接设置、加密 Token、云端 SHA 基线、内存下载预览、操作 `Mutex` | 只通过 `DataRepository.snapshot/restoreBackup` 访问业务数据；GitHub 不直接写 Room |
| `ui` | 订阅 `data`/`sync` 的 StateFlow，显示列表和详情，并把用户动作回调到仓库或同步层 | Compose 页面、筛选、表单和弹窗状态；字体显示偏好 | 不直接执行 SQL 或构造 GitHub 请求 |

```text
MainActivity → LoopViewModel → DataRepository → Room
                         └────→ GithubSync → GitHub Git API
MainActivity → EricLoopApp → 各 Compose 页面 → DataRepository / GithubSync 的公开操作
```

冷启动时，仓库在事务中建立预定义标签或组装已有 Room 行，严格校验完整历史；若是备份格式 1，会先验证、再在同一事务中转换为格式 2。成功后才发布 `ready=true`。普通修改在 `Mutex` 内计算新 `Backup`、校验增量事件、写 Room 事务，再发布 StateFlow；失败时原状态保留。手动同步在另一个 `Mutex` 内取得一致快照，更新 GitHub 单个备份路径，不操作代码文件。恢复先校验下载内容和远端 SHA，再保存本地安全副本并原子替换 Room。

## 启动层与数据模块：逐文件

### `app/src/main/java/com/example/ericloop/MainActivity.kt`

`LoopViewModel` 是装配根，创建一个 `DataRepository(application)` 和一个 `GithubSync(application, repository)`；Activity 配置变化时复用这两个实例。`MainActivity.onCreate` 开启边到边布局并通过 `setContent` 注入 `EricLoopApp`。本文件不保存页面路由，也不在 Activity 中处理记录或 Token。

### `data/Models.kt`

定义 `RecordKind`、`PlanType`、`PlanStatus`、`LoopRecord`、`LoopTag`、`CheckIn`、`HistoryEvent`、`Backup` 的 Kotlin Serialization 交换模型。`backupJson` 使用 `prettyPrint=true`、`encodeDefaults=true`、`ignoreUnknownKeys=true`；`newId()` 生成 UUID。`LoopRecord` 包含可选 Emoji、类型、状态、标签 ID、截止和软删除时间；`CheckIn` 区分发生时间与创建/修改时间。`HistoryEvent.sequence` 对应全局修订号，新格式只写 `afterJson`，`beforeJson=null`。`Backup.schemaVersion=2`、`datasetId` 及四类列表构成完整备份；模型默认值不能代替外部文件必需字段校验，解码必须经过 `BackupValidation.kt`。

### `data/Database.kt`

定义五张 Room 表：`records`、`tags`、`checkins` 以 ID 和 JSON payload 保存实体；`events` 额外保存 `sequence`、`recordId` 以便按顺序读取；单行 `metadata(id=1)` 保存不含四类列表的 `Backup` 元数据。`LoopDao` 提供全量读取、同 ID upsert 和四类数据清空操作，所有方法为 suspend。`LoopDatabase` 当前 Room schema 版本为 1，和备份格式版本 2 是两套独立版本号。DAO 不负责业务验证；多表原子性由仓库的 `withTransaction` 保证。

### `data/DataRepository.kt`

这是本地数据所有者。初始化先检查 metadata 与业务表是否一致：空库建立五个预定义标签；旧库组装完整 `Backup` 后调用 `decodeBackup`，格式 1 的转换结果会在启动事务里 `replaceAll`，失败只发布 `initializationError`，不会用空库覆盖旧数据。公开 `data/ready/initializationError` StateFlow；`snapshot()` 返回锁内一致数据并刷新导出时间。

| 写入口 | 主要约束与事件 | 持久化行为 |
| --- | --- | --- |
| `saveRecord` | 规范标题/Emoji，保留 ID 与创建时间；相同值无事件；创建/编辑记录 | 当前记录与“操作后”快照一起提交 |
| `convert`、`changeStatus` | 想法转一次性/长期计划；仅未删除计划可改状态 | 更新记录与转换/状态事件；完成状态供列表收纳 |
| `trash`、`restoreRecord` | 设置或清除 `deletedAt`，无永久删除入口 | 保留记录、打卡和事件链 |
| `saveCheckIn`、`deleteCheckIn` | 只属于未删除长期计划；发生时间不能在未来；补记按发生日期判定 | 更新打卡及所属计划 `updatedAt`，追加对应事件 |
| `addTag`、`editTag` | 自定义标签名非空且大小写不敏感唯一，预定义标签不可编辑 | 名称/Emoji 更新与标签事件一起提交 |
| `restoreBackup` | 恢复对象先完整校验；写私有 `pre-restore-*.json` 并 fsync | 在单个 Room 事务中全量替换，成功后发布状态 |

内部 `mutate` 先等初始化，再锁定、计算并调用 `validateMutation`；`persistChanges` 只 upsert 变化的实体、追加一条事件并更新 metadata。`recordJson` 在记录操作后的快照内加入当时的标签实体，用于历史展示与重放。`replaceAll` 仅用于初始种子、旧格式迁移和确认后的恢复。仓库没有数据库层分页；页面从完整内存 `Backup` 筛选。

### `data/BackupValidation.kt`

这是所有外部备份和本地历史的不变量边界。`decodeBackup` 先检查顶层与每行必需键，防止序列化默认值伪造 ID/时间，再反序列化并完整验证。格式 1 先按原有前后快照重放，验证后清空事件 `beforeJson`、提升为格式 2，并再次验证。`validateBackup` 检查版本、唯一 ID、时间、标签引用、预定义标签、打卡归属、连续序号和最终重放结果；格式 2 以上一节点结果作为操作前状态，以当前 `afterJson` 推进。`validateMutation` 只核对新事件相对上一个已验证状态的变化，因此普通写入不重放全部旧事件。任何校验异常阻止 Room 写入或云端恢复；新增操作类型时必须同时更新合法操作集合、快照约束与重放分支。

### `data/RecordEmoji.kt` 与 `data/TagEmoji.kt`

`RecordEmoji.kt` 的 `isRecordEmoji` 使用 Android ICU 的 `RGI_EMOJI` 属性，接受系统常见的组合、肤色及旗帜 Emoji，并限制输入长度；保存记录/标签和备份校验共用这一规则。`TagEmoji.kt` 的 `tagEmoji` 优先使用自定义值，否则按稳定预定义 ID 映射 💼/📚/🏡/💪/🚀，其余标签回退 🏷️；预定义图标不靠修改旧数据库实体获得。

## GitHub 同步模块：逐文件

### `sync/GithubSync.kt`

公开 `settings/status` StateFlow 与 `saveSettings`、`clearToken`、`upload(force)`、`download`、`restore`。`github_sync_private` SharedPreferences 保存 owner/repo/branch、加密 Token、上次确认的 blob SHA 与上传修订号/数据集 ID；Token 经 Android Keystore AES-GCM 加密，状态流只暴露 `hasToken`。切换目标仓库或分支时清空云端基线。`operation` 在同步专用 `Mutex` 中运行 IO，统一把网络/校验错误转换为界面消息，取消协程仍传播取消。

上传从 `DataRepository.snapshot()` 取得当前整份数据，规范列表顺序，UTF-8 JSON 超过 20 MiB 即拒绝。先读取目标分支 HEAD、tree 和 `data/ericloop/backup.json` blob SHA；普通上传若目标 blob 偏离已确认基线则要求恢复或强制上传。云端语义相同且原始格式版本相同时只确认基线；格式 1 即使内容相同也会写格式 2。需要写入时通过 GitHub Git API 创建 blob、新 tree、新 commit，再以 `force=false` 更新 ref；并发分支变化最多重试三次，响应丢失时重读远端 blob 确认结果。`force=true` 仅跳过数据内容基线冲突，不执行 Git force push，也不修改仓库其他路径。

下载读取 blob 并验证标称大小、Base64 解码后大小、Git blob SHA、严格 UTF-8、必需字段及完整事件链；HTTP JSON 响应上限 32 MiB。`Preview` 在内存中绑定下载时的连接设置、SHA 和解码备份。确认恢复时重新读取 SHA，若预览过期拒绝覆盖，然后调用仓库安全恢复并更新同步基线。默认 `shensunbo/EricLoop/main`；分支名称可在设置页修改，但当前没有自动创建分支或本地 Git clone/push。云端成功路径是否已真机验收见实施记录。

## Compose UI 模块：逐文件

### `ui/App.kt`

`EricLoopApp` 是唯一页面路由和动作编排点。它订阅本地与同步 StateFlow，使用 `rememberSaveable` 保存路由、选中记录/标签及返回来源，顶级页为 `home/tags/history/settings`，子页为 `edit/detail/completed/active/ideas/trash/tagrecords/recordhistory`。系统返回与顶部返回都走 `back()`；详情返回进入来源列表，Graph 返回标题目录。`Scaffold` 管理顶栏、底部导航、首页新增按钮、加载/错误进度与 Snackbar。页面把保存、转换、状态、删除、标签、打卡动作作为回调交给仓库；`work` 串行 UI 动作并显示失败，`networkWork` 使用同步层忙碌状态。`SettingsPage` 管理连接表单、手动同步/强制上传/恢复入口、字体偏好和应用版本；强制上传与覆盖恢复均先经确认弹窗。Token 输入只在当前 Compose 状态中暂存，不回显已保存密文。

### `ui/Records.kt`

`RecordsPage` 接受完整 `Backup` 与列表模式，在内存中按软删除、完成/进行中/想法等模式选候选集，再将标题或正文搜索、类型、状态、标签任选匹配作交集。`tagrecords` 模式把已完成计划排末尾，同组按 `updatedAt` 倒序；首页不显示已完成计划，回收站只显示软删除。筛选状态由 `rememberSaveable(mode, initialTag)` 保存。首页计数卡进入 `active/ideas` 子页，完成入口进入 `completed`。`RecordCard` 按想法/计划/完成态选淡蓝/淡绿/磨砂金，显示 Emoji、标题、手写正文预览、标签及时间；点击事件由父页路由处理。`ChoiceMenu` 是共用的紧凑下拉控件，逻辑外观 36 dp、点击布局 48 dp。

### `ui/Details.kt`

`RecordEditor` 保持表单草稿，编辑标题、正文、Emoji、类型、计划类型、标签和截止日期；截止日期可手输或打开日期选择器，保存前检查标题和日期，实际业务约束交给仓库。`RecordDetail` 从传入的记录和备份显示元数据、状态、标签、正文及历史/编辑/删除操作；想法可转换为两种计划，长期计划显示按发生时间倒序的未删除打卡。打卡卡片优先呈现完整笔记，右上角菜单收纳编辑/删除，删除需确认；正文和笔记走 `HandwritingText`。`CheckInDialog` 将可空备注与发生时间提交给父页，日期和时间分别由 Material 选择器输入，并禁止未来时间。三个 Composable 不直接写 Room；详情中操作失败由 `App.kt` 的 Snackbar 反馈。

### `ui/Tags.kt`

`TagsPage` 从未删除记录计算计数，标签库按每行三列排列；点击标签、全部记录或无标签入口进入独立筛选页。新增/编辑自定义标签共用弹窗，可设置、更换或清除 Emoji；预定义标签不显示编辑按钮。界面先检查空名/重复名，仓库仍做最终唯一性验证。页面只通过回调发出 `onAddTag/onEditTag/onOpenTag`。

### `ui/History.kt`

`HistoryDirectory` 只列记录标题和图标，包含回收站记录；选择后进入单条记录 Graph。`HistoryPage` 先按 `recordId` 过滤，再按操作类型和设备本地日期范围筛选，按事件序号倒序显示彩色节点。起止日期点击后使用 Material 日历，筛选仅是页面状态。打卡/补记节点预览操作后备注及必要时的发生时间；编辑打卡与删除打卡节点不预览备注。点击普通节点弹窗只显示该次操作后的快照；删除打卡弹窗只显示操作和时间。`humanSnapshot` 将事件 JSON 格式化为人读得懂的字段；Graph 读的是应用事件，不访问 Git 提交，也不执行回滚。

### `ui/Design.kt`

提供 Material 3 浅/深配色、语义色 `UiAccent`、统一字阶/圆角的 `LoopTheme`；`accentColors` 为计划、想法、完成、标签、变更和设置返回配色。`LoopIcon`、`RecordIcon`、`CompletionMark` 统一图标与 Emoji 后备策略；`displayTime` 按设备本地时区格式化时间。`CompactTextField` 用单行 `BasicTextField` 与自绘边框保证视觉紧凑同时保持 48 dp 布局高度。`SectionHeading` 组合常规标题和手写描述文字，`EmptyPanel` 提供无内容反馈。业务规则不应放入此文件。

### `ui/Handwriting.kt`

`HandwritingText` 使用 `AnnotatedString` 按 Unicode 码点找出连续拉丁字母/数字 run，不逐字拆开单词；中文基准字体为志莽行书，英文默认 Dancing Script，也可选 Caveat 或系统字体。正文与打卡读取本机 `note_fonts` 的中英文字体设置；页面标题下的描述文字固定使用默认手写组合。字体设置只影响渲染，不更改记录、Room 或 GitHub 备份；`maxLines/overflow` 由列表卡片等调用方控制。

### `ui/EmojiPicker.kt` 与 `ui/MetallicGold.kt`

`EmojiPicker` 复用同一个弹窗给记录及自定义标签选图标：提供常用项、系统输入法自由输入、清除入口，确认按钮受 `isRecordEmoji` 校验控制。`MetallicGold.kt` 的 `Modifier.metallicGold` 在完成态绘制静态金色渐变、高光与确定性细颗粒，`drawWithCache` 缓存画笔和路径；颜色随系统主题选择，未启用时直接返回原 Modifier，不持有业务状态。

## 构建、清单与资源：逐文件

| 文件 | 设计职责 |
| --- | --- |
| `settings.gradle.kts` | 定义插件与依赖仓库，启用 Foojay JDK resolver，并声明唯一 `:app` 模块。 |
| `build.gradle.kts` | 锁定 Android Gradle Plugin、Kotlin Compose/Serialization、KSP 插件版本；本身不声明应用依赖。 |
| `app/build.gradle.kts` | 读取根目录 `version.properties` 生成 `versionName/versionCode` 和设置页同源字符串；定义 SDK 36、最低 Android 35、Java 17 字节码及 Compose/Room/OkHttp 等依赖。 |
| `version.properties` | 应用版本唯一来源；本节点更新为 `1.0.5` / code 6，与 Room schema、备份格式版本分开。 |
| `gradle.properties` | Gradle 堆、缓存、并行和 AndroidX 等构建行为，不保存用户/Token 数据。 |
| `.gitattributes` | 约束 Kotlin、Markdown、XML 与启动脚本的 Git 文本识别和换行格式，避免跨平台提交产生纯换行差异。 |
| `.gitignore` | 排除 Gradle/Kotlin 构建产物、本机 SDK 路径、IDE 元数据、截图和签名材料；业务源码与设计文档仍入库。 |
| `gradle/gradle-daemon-jvm.properties` | Gradle 生成的 JDK 21 平台 toolchain URL 映射，供 Foojay resolver 找到 daemon JDK。 |
| `gradle/wrapper/gradle-wrapper.properties` | 固定 Gradle 9.3.1 下载地址与 SHA-256；`gradlew`、`gradlew.bat` 和 `gradle-wrapper.jar` 是 Linux/macOS、Windows 与 JVM 启动器，不包含 App 业务逻辑。 |
| `app/src/main/AndroidManifest.xml` | 仅申请 Internet 权限；注册唯一 launcher Activity，禁用 Android 系统备份，设置图标和输入法 `adjustResize`。 |
| `app/src/main/res/values/styles.xml` | Activity 原生窗口的浅色无 ActionBar 基底、状态栏和背景；进入 Compose 后由 `LoopTheme` 控制组件外观。 |
| `app/src/main/res/drawable-nodpi/ericloop_launcher_art.png` | 用户提供的启动图源；`nodpi` 避免系统按密度再次放大。 |
| `app/src/main/res/drawable/ic_launcher_foreground.xml` | 调整图源在启动图前景的缩放与安全边距。 |
| `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` | 组合白色背景和前景为自适应启动图，Manifest 通过 `@mipmap/ic_launcher` 引用。 |
| `app/src/main/res/drawable/ic_launcher.xml` | 非自适应启动图的兼容图形。 |
| `app/src/main/res/font/zhi_mang_xing.ttf` | 中文行书字形。 |
| `app/src/main/res/font/dancing_script.ttf` | 英文默认连笔字形。 |
| `app/src/main/res/font/caveat.ttf` | 英文备选手写字形。 |

以下每个 `drawable/ic_*.xml` 是独立矢量资源，均由 `painterResource`/`LoopIcon` 渲染；只包含路径、尺寸和颜色，点击行为由调用方定义。`ic_launcher` 与 `ic_launcher_foreground` 已在上表单列。

| 资源文件 | 当前用途 | 资源文件 | 当前用途 |
| --- | --- | --- | --- |
| `ic_add.xml` | 首页新建、标签新增、打卡 | `ic_arrow_back.xml` | 子页返回 |
| `ic_arrow_forward.xml` | 完成入口、标签列表、回收站提示 | `ic_check.xml` | 保存记录按钮 |
| `ic_check_circle.xml` | 保留的确认图标资源，当前源码未直接引用 | `ic_close.xml` | 保留的关闭图标资源，当前源码未直接引用 |
| `ic_dashboard.xml` | 底部记录 Tab | `ic_delete.xml` | 删除记录、打卡与回收站 |
| `ic_edit.xml` | 编辑记录、标签、打卡 | `ic_event.xml` | 日期选择入口 |
| `ic_filter_list.xml` | 保留的筛选图标资源，当前源码未直接引用 | `ic_folder.xml` | 保留的文件夹图标资源，当前源码未直接引用 |
| `ic_history.xml` | 详情历史、初始化失败占位 | `ic_lightbulb.xml` | 想法默认图标、通用空状态 |
| `ic_more_vert.xml` | 进展卡片操作菜单 | `ic_note_add.xml` | 记录/打卡空状态 |
| `ic_restore.xml` | 回收站恢复记录 | `ic_search.xml` | 记录搜索框 |
| `ic_sell.xml` | 底部标签 Tab | `ic_settings.xml` | 底部设置 Tab |
| `ic_sync.xml` | 手动同步按钮 | `ic_task_alt.xml` | 计划默认图标/状态 |
| `ic_timeline.xml` | 底部变更 Tab、Graph 空状态 |  |  |

## 文档、素材授权与维护规则

| 文件 | 所属内容及维护职责 |
| --- | --- |
| `readme.md` | 项目入口、功能概要、当前设备构建运行、GitHub 设置与文档导航。 |
| `doc/spec_rough.md` | 用户提供的原始粗略需求，作为追溯来源保留，不把实施修订混入其中。 |
| `doc/spec.md` | 当前生效的产品规则、边界和验收口径；需求变化先同步这里。 |
| `doc/architecture.md` | 系统上下文、模块依赖、数据所有权、失败与部署边界。 |
| `doc/detailed-design.md` | 跨文件的数据结构、写入/恢复算法、页面导航与同步协议。 |
| `doc/module-file-design.md` | 本文；按当前文件清单说明职责、入口、数据和依赖。增删/拆分源码或资源时同步更新。 |
| `doc/implementation.md` | 按节点记录实施、版本、设备验证与仍待验收项；旧节点保留历史语境。 |
| `doc/history-after-snapshots-plan.md` | M19 只保留操作后快照的格式迁移和真机验收依据。 |
| `doc/history-ux-plan.md` | M14 变更节点与进展卡片当时的 UI 方案；后续语义以当前设计为准。 |
| `doc/ui-density-plan.md` | M15 密度调整的设备测量、取舍及阶段验收记录。 |
| `NOTICE`、`licenses/material-symbols-apache-2.0.txt` | Google Material Symbols 图标来源与 Apache 2.0 授权。 |
| `third_party/fonts/README.md` | 三种字体的来源/授权索引。 |
| `third_party/fonts/ZhiMangXing-OFL.txt`、`DancingScript-OFL.txt`、`Caveat-OFL.txt` | 各字体对应的 SIL OFL 1.1 授权文本；随二进制字形一起保留。 |

修改业务模型或操作时，至少复核 `Models → DataRepository → BackupValidation → 相关 UI → spec/detailed-design/本文`，并检查旧备份兼容；修改同步行为时复核 `GithubSync` 的基线、大小、SHA、非强制 ref 更新和恢复安全副本。按用户要求只在当前连接的 Android 设备验证 App，不新增或运行单元测试；文档本身以源码符号、文件清单、链接和 `git diff --check` 校对。
