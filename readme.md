# EricLoop

个人想法和计划记录 Android 应用。Kotlin / Compose / Material 3，Room 离线保存，GitHub 手动备份。

## 功能

- 想法转为一次性或长期计划，保留全部历史。
- 计划状态调整；完成后自动进入“已完成”，删除进入可恢复的回收站。
- 长期计划自由打卡、补记、编辑和删除。
- 预置及自定义标签，独立标签筛选页。
- 全局变更 Graph，按记录、操作和日期筛选，查看修改前后内容。
- GitHub 手动同步、强制上传数据及预览恢复。

## 构建与指定真机运行

JDK 21（Gradle toolchain 自动选择）、Android SDK 36。local.properties 设置本机 sdk.dir，不提交此文件。

```bash
./gradlew :app:assembleDebug
adb -s 10AE970D5P0017U install -r app/build/outputs/apk/debug/app-debug.apk
adb -s 10AE970D5P0017U shell am start -n com.example.ericloop/.MainActivity
```

不新增或运行单元测试，通过编译和指定真机功能检查验证。

## GitHub 设置

1. 在 GitHub Settings → Developer settings → Personal access tokens → Fine-grained tokens 创建 Token。
2. 仅选择 `shensunbo/EricLoop` 仓库，Repository permissions 中给予 Contents 的 Read and write 权限。
3. 手机设置页填入仓库 `shensunbo/EricLoop`、分支 `main` 及 Token，保存连接设置。
4. 点击“手动同步”。云端已有未知数据时先恢复，或确认“强制上传”以手机数据为准。

Token 使用 Android Keystore 密钥加密保存，不进入日志或备份。过期后在手机重新配置。不要把 Token 写入源码或 GitHub 备份。

备份位于 `data/ericloop/backup.json`，包括 schemaVersion、datasetId、revision、exportedAt、records、tags、checkIns 和 events。此仓库公开，用户选择明文公开备份。

普通同步检测备份文件变化，不因电脑提交代码而冲突。“强制上传”只替换备份文件，在最新远端提交后追加提交，不覆盖代码或重写分支历史。恢复会先校验并预览，确认后保存本地安全副本，再原子替换。

本地安全副本在应用私有文件目录 `pre-restore-*.json`，可在 Debug 版本用 `adb -s 10AE970D5P0017U shell run-as com.example.ericloop ls files` 查看。卸载应用会删除本地数据与副本，卸载前先同步。

## 项目结构

- `data/`：模型、Room 数据库、Repository、备份校验。
- `sync/`：Token 保护、GitHub API 与同步状态。
- `ui/`：统一设计、记录/标签、详情编辑、Graph 和设置。
- [首版需求](doc/spec.md)、[实施记录](doc/implementation.md)。

UI 图标来自 Google Material Symbols，Apache 2.0，见 NOTICE 与 licenses/。页面使用统一主题，不需要额外购买素材。
