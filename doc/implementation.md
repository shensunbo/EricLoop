# EricLoop 首版实施计划与进度

更新日期：2026-09-29。工作分支：`feat/ericloop-v1`。正式需求见 [spec.md](spec.md)。

用户约束：仅使用当前设备 `10AE970D5P0017U`；不新增或运行单元测试；每个节点更新本文件并提交代码；不自动推送远端。用户对 `spec_rough.md` 的修改不混入实施提交。

## 节点与实施步骤

### M0 — 需求确认：完成

- [x] 明确想法转计划、固定完成收纳、打卡、标签及完整历史。
- [x] 确认手机编辑、GitHub 手动备份、强制覆盖数据文件与独立 Graph。
- [x] 确认仓库和明文备份均公开，Token 在手机设置页输入。
- [x] 保存需求并提交：`4a569f5 docs: define EricLoop v1 requirements and acceptance criteria`。

### M1 — 首版代码与集成构建：完成

- [x] Android 工程：Gradle 9.3.1、AGP 9.1.1、Kotlin 2.2.10，SDK 35/36，独立 applicationId `com.example.ericloop`。
- [x] 数据模型：记录、标签、打卡、历史事件、版本化备份。
- [x] Room：分表存储；Mutex 串行写入；当前对象、事件和修订号原子提交；普通修改增量更新。
- [x] 历史：完整前后快照，标签名称保留当时值，打卡发生时间与操作时间分离，无变化保存不新增事件。
- [x] 校验：必需字段、类型、引用、内置标签、连续事件序列；加载和恢复通过事件重放比对当前数据；普通修改只验证新事件。
- [x] 页面：概览、详情与编辑、完成视图、回收站、标签管理筛选、Graph、设置；支持系统深浅主题。
- [x] 同步：Keystore 加密 Token、Git blob/tree/commit API、内容基线、非强制分支更新、三次并发重试、未知响应核对。
- [x] 恢复：来源绑定的预览、云端复核、严格校验、本地安全副本、原子替换。
- [x] 编译：`./gradlew :app:assembleDebug`，2026-09-29 最近一次输出 `BUILD SUCCESSFUL in 2s`。

### M2 — 真机功能验收：进行中

- [x] 当前设备安装、启动、浅色主题显示；未观察到 AndroidRuntime 崩溃。
- [x] 创建想法、正文与预置标签、转为长期计划。
- [x] 保存无备注打卡，打开状态调整菜单。
- [x] 完成计划自动进入“已完成”，调整为进行中后移回普通列表。
- [x] 标签页显示预定义标签及对应计划数量。
- [ ] 自定义标签、重命名、组合筛选及完成包含开关。
- [ ] 打卡补记、编辑、删除以及完整历史详情。
- [ ] 删除与回收站恢复、进程重启后的持久化。
- [ ] 深色主题及最终页面截图。
- [ ] Token 保存与授权失败反馈。

### M3 — 交付记录与节点提交：进行中

- [x] README 保存构建、安装、GitHub 授权与恢复使用方式。
- [x] 保存 Material Symbols 来源及 Apache 2.0 许可。
- [ ] 更新最终验收证据与限制，并提交功能代码及验收记录。

## 接口和数据流

- `data/Models.kt`：`Backup(schemaVersion=1)` 包含 datasetId、revision、exportedAt、records、tags、checkIns、events。标签以 ID 关联。
- `data/DataRepository.kt`：StateFlow 提供当前数据/加载状态；保存、转换、状态、删除恢复、标签与打卡 API；snapshot 与 restoreBackup 隔离持久化。
- `data/BackupValidation.kt`：decodeBackup 拒绝缺字段导入；validateBackup 重放全量历史；validateMutation 检查当前数据及增量事件。
- `sync/GithubSync.kt`：保存连接设置、upload(force)、download 预览、restore；仅修改 `data/ericloop/backup.json`，保留远端其他文件与历史。
- UI 的本地保存和网络上传分别执行，上传确认的是开始时的修订号，后续修改仍显示待上传。

## 审查修正记录

- 禁止把 `{}` 等缺字段 JSON 解码为带默认值的空备份，避免错误覆盖手机数据。
- 普通写入改为增量更新/追加事件；不反复清空数据库或重解析全部历史。
- 恢复检查创建事件、前后连续性与最终数据一致性；不能移除预定义标签。
- 强制上传可覆盖格式损坏的云端备份，但不能绕过授权或网络失败。
- 限制可恢复备份为 20 MB；上传也遵守同一上限。

## 验证限制

尚无真实 GitHub Token，云端真实上传、并发代码提交、强制覆盖及下载恢复成功路径未进行端到端实测。代码已实现相关路径；不能将静态审查或编译通过描述成云端成功验证。不会自动使用电脑 GitHub 登录凭据代替手机授权。

完整验收条件以 spec.md 为准。M2 逐项更新，并把结果随节点提交保存。
