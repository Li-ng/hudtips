# CLAUDE.md

## 语言偏好 / Language Preferences
- 请始终使用**简体中文**回复。 / Always reply in **Simplified Chinese**.
- 代码注释使用中文（如项目已有英文注释则保持一致）。 / Use Chinese for code comments (or match existing style if English).
- 变量名、函数名、类名等标识符使用英文（遵循 Java 惯例）。 / Identifiers (vars, functions, classes) use English (Java conventions).

## 项目概述 / Project Overview
- Minecraft NeoForge mod 项目（HUD Tips）。 / Minecraft NeoForge mod: HUD Tips.
- 构建系统：Gradle，Java 源码目录：`src/` / Build: Gradle, Java source: `src/`

---

## 架构速览 / Architecture Overview

```
JSON 配置 / JSON config (config/hudtips/*.json)
    │
    ▼
ConfigLoader ──→ HudConfig / TriggerConfig / HintRule
    │
    ▼
TriggerManager.initTriggers()          ← 启动时 + 每次进入世界 / on startup + each world join
    ├── 按需注册触发器（仅注册 JSON 中引用的类型） / Register triggers on-demand (only referenced types)
    ├── loadAllRules() → 建立 Item→HintRule 索引 / Build Item→HintRule index
    └── 分离 andModeRules 子集（triggerOnMode="all"） / Separate andModeRules subset
    │
    ▼ 每 tick / per tick ─────────────────────────────────────────────────
ClientTickHandler.onClientTick()       ← ClientTickEvent.Post
    └── HintManager.tick()
        └── TriggerManager.tick()
            ├─ 步骤1/Step1: 驱动触发器 → 收集 activeTriggers Map / Drive triggers → collect activeTriggers
            ├─ 步骤2/Step2: 遍历 activeHints 队列 → 动画推进/渐出清理/消失判断 / Iterate queue → anim/cleanup/dismiss
            └─ 步骤3/Step3: matchRulesFromTriggers() → 匹配新规则 → 加入队列 / Match rules → enqueue
    │
    ▼ 每帧 / per frame ─────────────────────────────────────────────────
ClientHudRenderer.onRenderGui()        ← RenderGuiLayerEvent.Post
    └── HintManager.getRenderDataList()
        └── 逐条 ActiveHint → HintRenderData → 渲染到屏幕 / Each hint → render data → draw
```

## 触发器体系 / Trigger System

```
HudTrigger (接口 / interface)
├── HoldItemTrigger        [continuous, priority= 0, item-based]
├── OnUseTrigger           [event,      priority=10, item-based]
├── OnLowHealthTrigger     [continuous, priority=20]
├── OnDimensionTrigger     [continuous, priority=25]
└── OnDimensionChangeTrigger [event,   priority=30]

continuous: 条件满足期间持续激活 → 条件不满足时 TriggerManager 自动关掉
            / Continuously active while conditions hold → auto-closed by TriggerManager when conditions fail
event:      仅触发那一刻激活一 tick → 由 time:/tick: 或 dismissOn event 控制消失
            / Active for one tick on trigger → dismissed by time:/tick: or dismissOn event
```

## 提示生命周期状态机 / Hint Lifecycle State Machine

```
ENTERING ──(逐字打印完成/typewriter done)──→ SHOWING ──(dismissOn 触发/fires)──→ EXITING ──(500ms)──→ 移除/removed
                                   │                                      │
                                   └──(dismissMode="complete")──→ CELEBRATING ──(600ms)──→ EXITING
```

## 关键约定 / Key Conventions

1. **触发器按需注册 / On-demand trigger registration**：`TriggerManager.initTriggers()` 扫描 JSON 的 `triggerOn`/`dismissOn`，只实例化被引用的触发器类型。未被引用的触发器 tick 零开销。 / Scans JSON `triggerOn`/`dismissOn`, only instantiates referenced trigger types. Unreferenced triggers have zero tick overhead.
2. **规则索引 O(1) / O(1) rule index**：`itemToRulesMap`（Item→HintRule）和 `stringToRulesMap`（维度 ID/entity ID→HintRule），避免每 tick 全量扫描规则。 / Avoids full rule scan every tick.
3. **Item 互斥 / Item mutual exclusion**：同类型 item-based 提示只保留最新一条，切物品时旧提示立即移除。 / Same-type item-based hints: only the latest is kept; old hint removed immediately on item switch.
4. **conditionFailed 不标记已读 / conditionFailed ≠ markSeen**：切走物品只是条件失效，不是"完成任务"，`markSeen` 只在显式 dismiss 或超时时调用。 / Switching away is condition failure, not task completion; `markSeen` only on explicit dismiss or timeout.
5. **三层默认值继承 / Three-layer default inheritance**：规则字段 → 触发器默认值 → 全局默认值 / Rule field → Trigger default → Global default.
6. **普通型未配 dismissOn 默认 5 秒消失 / Normal rules without dismissOn default to 5s**：纯 event 型规则未配置 dismissOn 时默认 `time:5000`（教程型 rule 仍需显式配置）。continuous 型仍由条件失效自动处理。 / Pure event rules without dismissOn default to `time:5000` (guide rules still need explicit config). Continuous rules are still auto-handled by condition failure.

---

## 如何新增一个触发器 / How to Add a New Trigger

参考 `TriggerTemplate.java`（位于 `client/trigger/`），步骤 / Reference `TriggerTemplate.java` (under `client/trigger/`), steps:

1. **创建类**实现 `HudTrigger` 接口 / **Create a class** implementing `HudTrigger`
2. **定义内部状态字段**（如 `currentItem`、`wasUsingItem`） / **Define internal state fields** (e.g. `currentItem`, `wasUsingItem`)
3. **实现 `tick()`**：每 tick 检测条件，更新状态 / **Implement `tick()`**: check conditions each tick, update state
4. **实现 `getActiveTriggers()`**：返回当前激活的标识列表（Item 或 String） / **Implement `getActiveTriggers()`**: return active identifier list (Item or String)
5. **覆写 `getType()`**：返回 JSON 中的触发器键名 / **Override `getType()`**: return the trigger key name used in JSON
6. **覆写 `getPriority()`**：与其他触发器比较优先级 / **Override `getPriority()`**: priority compared with other triggers
7. **覆写 `isContinuous()`**：continuous 或 event / **Override `isContinuous()`**: continuous or event
8. **按需覆写 `isItemBased()`**、`getParentType()` / **Optionally override** `isItemBased()`, `getParentType()`
9. **如需事件监听**：添加 `@EventBusSubscriber` 内部类（参考 `OnUseTrigger.EventListener`） / **If event listening needed**: add `@EventBusSubscriber` inner class (see `OnUseTrigger.EventListener`)
10. **在 `TriggerManager.initTriggers()`** 中加一行 `registerIfUsed(...)` 调用 / **Add a `registerIfUsed(...)` call** in `TriggerManager.initTriggers()`
11. **在 `ConfigValidator.VALID_TRIGGER_TYPES`** 中添加触发器类型名 / **Add trigger type name** to `ConfigValidator.VALID_TRIGGER_TYPES`

## 常见陷阱 / Common Pitfalls

- **瞬发物品无法用 `isUsingItem()` 检测 / Instant-use items can't be detected via `isUsingItem()`**：末影珍珠、打火石等用 `InputEvent.InteractionKeyMappingTriggered` 兜底（见 OnUseTrigger 双重检测） / Ender pearls, flint & steel, etc. fall back to `InputEvent.InteractionKeyMappingTriggered` (see OnUseTrigger dual detection)
- **JSON `//` 注释只能放在对象 `{}` 内部**，不能放在 Map 类型的对象（如 `triggers` 内部）或数组 `[]` 元素位置 / **JSON `//` comments only work inside object `{}` braces**, not inside Map-type objects (e.g. inside `triggers`) or array `[]` element positions
- **`conditionFailed` 分支不应调用 `markSeen`**：切物品不是完成 / **Don't call `markSeen` in `conditionFailed` branch**: switching items is not completion
- **`tryAddOrRefreshHint` 发现同规则 EXITING 时先移除再重建**：确保 event 型触发器（只活跃 1 tick）在渐出期间再次触发不会丢失。ENTERING/SHOWING/CELEBRATING 中仍不重复添加。 / **When `tryAddOrRefreshHint` finds same-rule EXITING, remove-then-rebuild**: ensures event triggers (active 1 tick) don't lose re-triggers during fade-out. Still no duplicate add in ENTERING/SHOWING/CELEBRATING.
- **`parseColor` sentinel 不能用 `-1`**：`-1` = `0xFFFFFFFF` 是合法不透明白色，改用 `Integer` null / **`parseColor` sentinel must not be `-1`**: `-1` = `0xFFFFFFFF` is valid opaque white, use `Integer` null instead
