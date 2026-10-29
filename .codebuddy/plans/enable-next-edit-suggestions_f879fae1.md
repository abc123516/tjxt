---
name: enable-next-edit-suggestions
overview: 把 CodeBuddy CN 用户设置里的 codingcopilot.enableNextEditSuggestions 从 false 改为 true，启用 NES 前瞻式补全（Ctrl+Shift+Enter 可手动触发）。
todos:
  - id: read-and-backup
    content: 读取 settings.json 确认 enableNextEditSuggestions 当前为 false，并复制一份 settings.json.bak 备份
    status: completed
  - id: edit-flag
    content: 将 codingcopilot.enableNextEditSuggestions 由 false 改为 true，其余内容保持不变
    status: completed
    dependencies:
      - read-and-backup
  - id: verify-and-restart-hint
    content: 回读文件并用 ConvertFrom-Json 校验合法性与目标值，然后提示需重启 IDE 使配置生效
    status: completed
    dependencies:
      - edit-flag
---

## 用户需求

把 CodeBuddy IDE（国内版 CodeBuddy CN）的用户配置文件中的前瞻式补全开关打开：

- 目标文件：`C:\Users\86159\AppData\Roaming\CodeBuddy CN\User\settings.json`
- 目标项：`codingcopilot.enableNextEditSuggestions`，由 `false` 改为 `true`
- 目的：启用 NES（Next Edit Suggestions），使 `Ctrl+Shift+Enter` 可手动触发“写一补十”的前瞻补全，并让自动触发逻辑生效

## 范围约束

- 只改这一处布尔值，不删除、不改动文件中其他配置（尤其是已有的通义灵码 `Lingma.*` 配置、`codingcopilot.enableAutoCompletions` 等）
- 不修改系统代理、不安装或禁用任何扩展
- 改完后文件必须仍是合法 JSON，缩进风格保持现状（2 空格，数组/对象闭合括号的位置沿用现有写法）

## 技术方案

这是一次单点的配置文件修改，不涉及代码逻辑与架构变更。

- **修改方式**：直接对目标 JSON 文件做最小化文本替换，把第 10 行的 `"codingcopilot.enableNextEditSuggestions": false` 改为 `true`，其余内容原样保留。
- **前置保护**：修改前先读取文件确认当前值，并在同目录生成一份 `settings.json.bak` 备份，便于回滚。
- **校验方式**：修改后用 `Get-Content -Raw | ConvertFrom-Json` 解析，确认 JSON 合法且目标键值为 `true`；若解析失败，立即用备份还原。
- **生效方式**：该文件是 IDE 用户设置，CodeBuddy CN 进程（当前共 14 个，自 2026-10-26 14:26 起持续运行）可能持有内存副本。若修改后界面未生效，需完全退出 IDE 再启动；若 IDE 退出时用内存副本回写覆盖，则需重启后再执行一次修改。
- **风险控制**：不使用整体重写文件的方式（文件较长且存在非标准缩进），只做精确替换，避免破坏其他配置或引入格式变动。