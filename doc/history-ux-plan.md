# 变更节点与进展笔记卡片设计及实施计划

此文档保留当时的 UI 方案；M19 后的节点快照行为以 [history-after-snapshots-plan.md](history-after-snapshots-plan.md) 为准。

状态：用户于 2026-09-30 确认范围。关联需求见 [spec.md](spec.md)，总体设计见 [detailed-design.md](detailed-design.md)。

## 设计

变更目录继续仅列想法和计划标题。进入单条记录 Graph 后，页面顶部显示记录标题，节点只保留事件时间和操作名称，不重复记录标题；仅“打卡 / 补记打卡 / 编辑打卡 / 删除打卡”额外显示该次打卡的笔记内容（空白显示“无备注”），以及与操作时间不同的实际发生时间，避免补记产生误解。编辑记录、状态变更等操作不在节点展开具体内容；点节点后现有弹窗仍显示完整前后快照。

卡片采用按操作类别固定的浅色背景与对应深色文字；用户仅使用浅色主题，本节点不额外设计深色配色。打卡为绿色，记录编辑为紫色，创建为蓝色，状态为琥珀色，转计划为青色，删除为红色，恢复为青绿色。操作名称始终保留，颜色不作为唯一信息渠道。Graph 轨道线改用中性色，节点圆点跟随操作类别，避免同一记录内所有事件看起来同色。语义色保持固定且数量克制，参考 [Android 官方颜色设计建议](https://developer.android.com/design/ui/mobile/guides/styles/color)。

长期计划详情中的进展笔记卡片优先显示发生时间和正文，收紧卡片留白。右上角 `ic_more_vert` 打开“编辑 / 删除”菜单；删除沿用确认弹窗，已删除记录不显示菜单。不改动打卡实体、事件格式或 GitHub 备份。

## 实施步骤

- [x] `ui/History.kt`：按 `HistoryEvent.operation` 选择语义色卡片配色；轨道线用中性色，圆点和操作文字用操作色。
- [x] `ui/History.kt`：仅从打卡类事件 `afterJson` 读取笔记；“删除打卡”从 `beforeJson` 读取。无备注给出明确占位，长内容限制两行，完整内容仍可在弹窗读取。
- [x] `ui/Details.kt`：进展卡片右上角放 `IconButton(ic_more_vert)` 和 `DropdownMenu`，将编辑、删除回调接到原弹窗状态。
- [x] 更新 `spec.md`、`detailed-design.md`、`implementation.md`，编译 `:app:assembleDebug`，只在当前设备安装并检查相关页面；不新增或运行单元测试。
- [x] `git diff --check` 后按节点提交代码与文档，不推送远端。
