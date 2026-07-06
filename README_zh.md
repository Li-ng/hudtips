# HUD Tips

[![Minecraft](https://img.shields.io/badge/Minecraft-1.26.1-blue.svg)](https://www.minecraft.net/)
[![NeoForge](https://img.shields.io/badge/NeoForge-26.1-orange.svg)](https://neoforged.net/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

[English / 英文](README.md)

**HUD Tips** 是一个 Minecraft NeoForge 模组，允许整合包作者通过 JSON 配置文件自定义 HUD 提示信息。当玩家触发特定条件（如手持物品、看向方块、进入维度等）时，屏幕上会动态显示对应的提示文本。

---

## ✨ 功能特性

- **📝 JSON 驱动** — 所有提示规则通过 JSON 文件配置，无需编写代码
- **🎯 9 种触发器** — 手持物品、使用物品、按键、看向方块/实体、低血量、维度变化……满足各种场景
- **🔗 AND/OR 组合** — 多个触发器可自由组合（如"手持钻石剑 **且** 看向苦力怕"）
- **📖 教程系统** — 支持 `guide` 类型，显示一次后自动标记完成，带庆祝动画
- **⌨ 按键显示** — `{key:key.sneak}` 自动显示玩家实际绑定的按键
- **🎨 灵活样式** — 文字颜色、背景颜色、位置、缩放、偏移全部可配
- **📐 自适应缩放** — 根据屏幕分辨率和 GUI 比例自动调整文字大小
- **🏷 标签匹配** — 支持 `#minecraft:swords` 等物品/方块标签
- **📂 多文件配置** — 配置拆分为多个 JSON 文件，方便管理

---

## 📦 安装

1. 安装 [NeoForge 26.1](https://neoforged.net/) 或更高版本
2. 下载本模组的 `.jar` 文件，放入 `mods/` 文件夹
3. 启动游戏，模组会自动生成默认配置文件

---

## ⚙ 配置

### TOML 配置（游戏内可修改）

`config/hudtips/hudtips-client.toml`：

| 设置项 | 说明 | 默认值 |
|--------|------|--------|
| `enabled` | 模组总开关 | `true` |
| `scaleWithGui` | 自适应文字大小 | `false` |
| `textSize` | 基础文字大小 (0-100) | `50` |
| `resolutionComp` | 分辨率补偿强度 (0-100) | `50` |
| `guiScaleComp` | GUI 缩放补偿强度 (0-100) | `50` |
| `itemHintPosition` | 物品提示默认位置 | `bottom_left` |
| `globalSeenState` | 全局已读状态（跨存档共享教程进度） | `false` |

### JSON 规则配置

在 `config/hudtips/` 下创建 `.json` 文件定义提示规则。模组启动时会自动生成 `_reference.json` 参考文档。

---

## 🎮 触发器类型

### Continuous（持续型）— 条件满足期间持续显示

| 触发器 | 说明 | 示例 |
|--------|------|------|
| `hold_item` | 手持指定物品 | 手持钻石剑时显示剑气提示 |
| `on_dimension` | 处于指定维度 | 在下界时持续显示警告 |
| `on_low_health` | 血量低于阈值 | 低血量时显示逃生提示 |
| `on_look_block` | 看向指定方块 | 看向工作台时显示"右键打开合成" |
| `on_look_entity` | 看向指定实体 | 看向苦力怕时显示"快跑！" |

### Event（事件型）— 触发瞬间激活，由时间控制消失

| 触发器 | 说明 | 示例 |
|--------|------|------|
| `on_use` | 使用物品 | 右键弓时提示蓄力 |
| `on_dimension_change` | 切换维度 | 首次进入下界时显示指南 |
| `on_key_press` | 按下按键 | 按潜行键时显示操作提示 |
| `on_kill` | 击杀实体 | 击杀僵尸时显示战利品提示 |
| `on_activate_block` | 手持物品右键方块 | 手持骨粉右键树苗时提示催熟 |

---

## 📝 规则编写示例

### 基础示例：手持物品提示

```json
{
  "items": ["minecraft:diamond_sword"],
  "text": "💡 按 {key:key.sneak} + 右键 释放剑气"
}
```

### AND 组合：手持物品 + 特定维度

```json
{
  "items": ["minecraft:bed"],
  "text": "💥 在下界使用床会爆炸！",
  "triggerOn": {
    "hold_item": true,
    "on_dimension": "minecraft:the_nether"
  },
  "triggerOnMode": "all"
}
```

### 多触发器 OR：手持或使用时都显示

```json
{
  "items": ["minecraft:bow"],
  "text": "🎯 长按右键蓄力，松开发射！",
  "triggerOn": ["hold_item", "on_use"],
  "dismissOn": ["time:3000"]
}
```

### 教程型：显示一次 + 庆祝动画

```json
{
  "items": ["minecraft:wooden_pickaxe"],
  "type": "guide",
  "text": "⛏ 对准矿石右键挖掘！煤炭和铁是最好的初期资源",
  "triggerOn": {
    "hold_item": true,
    "on_look_block": {
      "targetBlocks": ["minecraft:iron_ore", "minecraft:coal_ore"]
    }
  },
  "triggerOnMode": "all",
  "dismissOn": ["on_activate_block"]
}
```

### 看向实体 + 手持物品

```json
{
  "items": ["minecraft:shears"],
  "type": "guide",
  "id": "shear_sheep",
  "text": "✂ 右键剪羊毛！",
  "triggerOn": {
    "hold_item": true,
    "on_look_entity": "minecraft:sheep"
  },
  "triggerOnMode": "all",
  "dismissOn": ["on_use"]
}
```

---

## 🏗 构建

```bash
# 克隆仓库
git clone https://github.com/Li-ng/hudtips.git
cd hudtips

# 构建
./gradlew build

# 构建产物位于 build/libs/
```

需要 JDK 25 和 NeoForge 26.1。

---

## 📄 许可证

MIT License — 详见 [LICENSE](LICENSE) 文件。

---

## 🙏 致谢

- [NeoForge](https://neoforged.net/) — Minecraft modding platform
- 所有提供反馈和建议的玩家
