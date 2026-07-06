# HUD Tips 触发器分类总表

> 本文档按三个维度系统分类所有触发器：持续性（continuous / event）、互斥关系、性能实现途径。

---

## 一、Continuous（持续型）

条件满足期间持续激活，条件不再满足时自动消失。

| 触发器 | 性能等级 | 实现途径 | 缓存策略 | 索引方式 | 互斥关系 | 复杂度 |
|--------|---------|---------|---------|---------|---------|--------|
| `hold_item` | 轻量 | `tick()` 读 `getMainHandItem()` | 无（每帧刷新） | `Map<Item, …>` | 无 | ★ |
| `on_low_health` | 轻量 | `tick()` 读 `getHealth() / getMaxHealth()` | 无 | 无（返回手持物品作为标识） | 无 | ★ |
| `on_food_level` | 轻量 | `tick()` 读 `getFoodData().getFoodLevel()` | 无 | `TreeMap<区间, …>` | 无 | ★ |
| `on_air` | 轻量 | `tick()` 读 `getAirSupply()` | 无 | `TreeMap<区间, …>` | 无 | ★ |
| `on_effect` | 轻量 | `tick()` 遍历 `getActiveEffects()` | 无 | 无（返回效果类型列表） | 无 | ★★ |
| `on_sprinting` | 轻量 | `tick()` 读 `isSprinting()` | 无 | 无 | 与 `on_elytra`/`on_riding` 互斥 | ★ |
| `on_elytra` | 轻量 | `tick()` 读 `isFallFlying()` | 无 | 无 | 与 `on_riding`/`on_sprinting` 互斥 | ★ |
| `on_riding` | 轻量 | `tick()` 读 `isPassenger()` + `getVehicle()` | 无 | `Map<String, …>`（载具类型） | 与 `on_elytra` 互斥 | ★ |
| `on_tool_low_durability` | 轻量 | `tick()` 读 `getDamageValue()` | 无 | `Map<Item, …>` | 是 `hold_item` 的子场景 | ★ |
| `on_dimension` | 中量 | 事件提前更新 + tick 引用比较兜底 | 事件 + 惰性 | `Map<String, …>` | 同类三值互斥 | ★★ |
| `on_biome` | 中量 | `tick()` 调 `level.getBiome(pos)` | 每 20 tick | `Map<String, …>` | 同类多值互斥 | ★★ |
| `on_height` | 中量 | `tick()` 读 `getY()` + 比较阈值 | 每 10 tick | `TreeMap<区间, …>` | 同类区间互斥 | ★★ |
| `on_time` | 中量 | `tick()` 读 `level.getDayTime()` 映射到白天/黄昏/夜晚 | 无（单次模运算） | `Map<String, …>` | 同类三值互斥 | ★ |
| `on_weather` | 中量 | `tick()` 读 `level.getRainLevel()`/`isThundering()` | 惰性（天气变化有事件） | `Map<String, …>` | 同类三值互斥；雷 ⊂ 雨 | ★★ |
| `on_moon_phase` | 中量 | `tick()` 读 `level.getMoonPhase()` | 惰性（每天才变） | `Map<String, …>` | 同类多值互斥 | ★ |
| `on_light_level` | 中量 | `tick()` 调 `level.getLightLevel(player.blockPosition())` | 每 10 tick | `TreeMap<区间, …>` | 与 `on_time` 弱相关 | ★★ |
| `on_structure` | **重型** | `tick()` 调 `level.structureManager().getStructureAt(pos, …)` | 每 100 tick + 坐标变更才刷新 | `Map<String, …>` | 同类多值互斥 | ★★★ |
| `on_nearby_entity` | **重型** | `tick()` 做 AABB 实体扫描 | **每 40 tick** + 只扫附近 16 格 | 无（需扫描） | 无 | ★★★ |

---

## 二、Event（事件型）

仅在触发瞬间激活一 tick，之后自动失效。消失由 `time:` / `tick:` 控制。

| 触发器 | 性能等级 | 实现途径 | tick() 开销 | 索引方式 | 互斥关系 | 复杂度 |
|--------|---------|---------|-----------|---------|---------|--------|
| `on_use` | 零 | `tick()` 读 `isUsingItem()` | 轻量 | `Map<Item, …>` | 无 | ★ |
| `on_dimension_change` | 零 | 订阅 `PlayerChangedDimensionEvent` | 消费标记 → 清空 | `Map<String, …>` | 同类多值互斥 | ★★ |
| `on_death` | 零 | 订阅 `LivingDeathEvent`（检查是玩家） | 消费标记 → 清空 | 无 | **暂停所有 continuous** | ★ |
| `on_respawn` | 零 | 订阅 `ClientPlayerNetworkEvent.Clone` 或 `LivingEvent` | 消费标记 → 清空 | 无 | 与 `on_death` 先后关系 | ★ |
| `on_wake_up` | 零 | 订阅 `WakeUpEvent` 或 tick 检测睡觉→醒转变 | 轻量状态比较 | 无 | 无 | ★ |
| `on_advancement` | 零 | 订阅 `AdvancementEvent` | 消费标记 → 清空 | `Map<String, …>`（进度 ID） | 无 | ★★ |
| `on_block_break` | 零 | 订阅 `ClientBlockEvent.Break` | 消费标记 → 清空 | `Map<Block, …>` | 无 | ★★ |
| `on_block_place` | 零 | 订阅 `ClientBlockEvent.Place` | 消费标记 → 清空 | `Map<Block, …>` | 无 | ★★ |
| `on_item_pickup` | 零 | 订阅 `ItemPickupEvent` | 消费标记 → 清空 | `Map<Item, …>` | 无 | ★★ |
| `on_fall_damage` | 零 | 订阅 `LivingFallEvent`（检查是玩家） | 消费标记 → 清空 | 无 | 无 | ★ |
| `on_entity_kill` | 零 | 订阅 `LivingDeathEvent`（检查 killer 是玩家） | 消费标记 → 清空 | `Map<EntityType, …>` | 无 | ★★ |

---

## 三、互斥关系分类

### 绝对互斥 — 同一时刻绝不可能同时激活

| 触发器组合 | 说明 |
|-----------|------|
| `on_elytra` ↔ `on_riding` | 滑翔和骑乘是排他的移动状态 |
| `on_elytra` ↔ `on_sprinting` | 滑翔中不可能冲刺 |
| `on_riding` ↔ `on_sprinting` | 骑乘中不可能冲刺 |
| `on_death` → 所有 continuous | 死亡瞬间 `player == null`，一切持续触发器停摆 |

### 同类互斥 — 同一触发器内部，不同值互斥

这些触发器描述的是**单值状态**，一次只能处于一个值。不同规则指定不同值时，`triggerOnMode: "all"` 下永远无法同时满足：

| 触发器 | 可能值 | 示例 |
|--------|-------|------|
| `on_dimension` | overworld / the_nether / the_end | 三值互斥 |
| `on_biome` | 所有生物群系 ID | 多值互斥 |
| `on_structure` | 所有结构 ID | 多值互斥 |
| `on_weather` | clear / rain / thunder | 三值互斥 |
| `on_time` | day / dusk / night | 三值互斥 |
| `on_moon_phase` | full / new / first_quarter / last_quarter | 多值互斥 |
| `on_height` | 不同数值区间 | 区间不重叠即互斥 |

### 单向包含 — A 隐含 B

| 包含关系 | 说明 |
|---------|------|
| `on_weather:"thunder"` ⊂ `on_weather:"rain"` | 雷暴必定伴随下雨 |
| `on_light_level` 弱相关 `on_time:"night"` | 夜晚通常光照低，但不是绝对的 |
| `on_tool_low_durability` ⊂ `hold_item` | 工具耐久警告是持有物品的子场景 |

### 无互斥 — 可以独立共存

| 触发器 | 说明 |
|--------|------|
| `on_effect` | 不同药水效果各自独立（同时中毒 + 着火完全可能） |
| `on_nearby_entity` | 附近同时有骷髅和僵尸，各自独立 |
| `on_low_health` | 与其他状态基本无关 |
| `on_food_level` | 同上 |
| `on_air` | 同上 |

---

## 四、性能实现途径分类

### 零成本 — 纯事件驱动，无需 tick 轮询

完全不需要 `tick()`，只需订阅一个 NeoForge 事件，事件触发时设置标记，`getActiveTriggers()` 返回缓存值。`tick()` 几乎空转。

```
on_death           ← LivingDeathEvent
on_respawn         ← ClientPlayerNetworkEvent.Clone
on_wake_up         ← WakeUpEvent 或 tick 状态检测
on_advancement     ← AdvancementEvent
on_block_break     ← ClientBlockEvent.Break
on_block_place     ← ClientBlockEvent.Place
on_item_pickup     ← ItemPickupEvent
on_fall_damage     ← LivingFallEvent
on_entity_kill     ← LivingDeathEvent（检查 killer）
on_dimension_change ← PlayerChangedDimensionEvent（已实现）
```

### 轻量轮询 — 仅访问 Player 自身字段

每 tick 开销极低（基本是字段读取），无需访问 Level 数据：

```
hold_item          ← player.getMainHandItem()                    [已实现]
on_low_health      ← player.getHealth()                          [已实现]
on_food_level      ← player.getFoodData().getFoodLevel()
on_air             ← player.getAirSupply()
on_effect          ← player.getActiveEffects()
on_sprinting       ← player.isSprinting()
on_elytra          ← player.isFallFlying()
on_riding          ← player.isPassenger() + getVehicle()
on_tool_low_durability ← 手持物品.getDamageValue()
```

### 中等轮询 — 需访问 Level/World 数据

每 tick 需要一次 Level 查询，必须加缓存降频：

| 触发器 | 缓存策略 | 说明 |
|--------|---------|------|
| `on_dimension` | 事件 + tick 引用比较 | 已实现 |
| `on_biome` | 每 20 tick 缓存 | `level.getBiome(pos)` |
| `on_height` | 每 10 tick 缓存 | `player.getY()` 本身极轻，缓存是为了减少下游匹配调用 |
| `on_time` | 无（单次模运算） | `level.getDayTime() % 24000` |
| `on_weather` | 惰性（天气变化有事件） | `level.getRainLevel()` / `isThundering()` |
| `on_moon_phase` | 惰性（每天才变） | `level.getMoonPhase()` |
| `on_light_level` | 每 10 tick 缓存 | `level.getLightLevel(player.blockPosition())` |

### 重型 — 需要空间搜索或迭代

| 触发器 | 缓存策略 | 风险点 |
|--------|---------|-------|
| `on_structure` | 每 100 tick + 坐标变更才刷新 | `structureManager()` 调用昂贵 |
| `on_nearby_entity` | 每 40 tick + 只扫 16 格半径 | AABB 实体扫描，**客户端无法获取完整实体列表**——最棘手的触发器 |

---

## 五、⚠️ 特别复杂的触发器

| 触发器 | 核心难点 | 需要特别处理的问题 |
|--------|---------|------------------|
| `on_structure` | 每 tick 调用 `structureManager()` 极重 | 必须 100 tick 间隔 + 仅当玩家移动超过 16 格才重新查询；结构检测 API 在各版本可能变动 |
| `on_nearby_entity` | AABB 实体扫描是 N² 问题 | 必须大间隔（40 tick）+ 限制半径（16 格）；服务端需网络包同步，**纯客户端无法获取完整实体列表**——是最棘手的 |
| `on_effect` | 多效果共存，标识符是列表 | `getActiveTriggers()` 返回多条标识，匹配逻辑需支持"任意匹配"而非"精确匹配" |
| `on_weather` | 雷暴状态嵌套 | `"thunder"` 意味着 `"rain"` 也为真，AND 模式下需特殊处理子集关系 |
| `on_advancement` | 进度 ID 是 Identifier 而非 Item | 需要新的索引维度；进度树有父子关系 |
| `on_riding` | 载具类型差异大 | 马/船/矿车/猪各有不同 API，`getVehicle()` 返回 `Entity` 需 `instanceof` 判断 |

---

## 六、实现顺序建议（按成本收益比）

### 第一梯队（零成本，无风险）
```
on_death → on_respawn → on_wake_up → on_fall_damage → on_entity_kill
全部纯事件驱动，tick() 空转，0 性能开销
```

### 第二梯队（轻量轮询，成熟 API）
```
on_food_level → on_air → on_sprinting
→ on_elytra → on_riding → on_effect → on_tool_low_durability
都是读 Player 字段，与 hold_item 模式一致
```

### 第三梯队（中量轮询，需缓存框架）
```
on_biome → on_height → on_time → on_weather → on_moon_phase
→ on_light_level
需要设计缓存框架，但难度可控
```

### 第四梯队（重型，需谨慎设计）
```
on_structure → on_nearby_entity
需要仔细设计，尤其 nearby_entity 的客户端限制
```

---

## 七、已有触发器对照

| 触发器 | 状态 | 类型 | 优先级 |
|--------|------|------|--------|
| `hold_item` | ✅ 已实现 | continuous | 0 |
| `on_use` | ✅ 已实现 | event | 10 |
| `on_low_health` | ✅ 已实现 | continuous | 20 |
| `on_dimension` | ✅ 已实现 | continuous | 25 |
| `on_dimension_change` | ✅ 已实现 | event | 30 |

