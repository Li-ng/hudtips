# HUD Tips

[![Minecraft](https://img.shields.io/badge/Minecraft-1.26.1-blue.svg)](https://www.minecraft.net/)
[![NeoForge](https://img.shields.io/badge/NeoForge-26.1-orange.svg)](https://neoforged.net/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

[中文版 / Chinese](README_zh.md)

**HUD Tips** is a Minecraft NeoForge mod that lets modpack authors create custom HUD hints via JSON configuration. When players trigger specific conditions (holding an item, looking at a block, entering a dimension, etc.), the corresponding hint text dynamically appears on screen.

---

## ✨ Features

- **📝 JSON-Driven** — All hint rules configured via JSON files, no coding required
- **🎯 9 Trigger Types** — Hold item, use item, key press, look at block/entity, low health, dimension change... covers every scenario
- **🔗 AND/OR Logic** — Combine multiple triggers freely (e.g., "holding a diamond sword **AND** looking at a creeper")
- **📖 Tutorial System** — `guide` type rules display once, auto-mark as complete with a celebration animation
- **⌨ Key Display** — `{key:key.sneak}` automatically shows the player's actual keybinding
- **🎨 Flexible Styling** — Text color, background color, position, scale, and offset all configurable
- **📐 Adaptive Scaling** — Automatically adjusts text size based on screen resolution and GUI scale
- **🏷 Tag Matching** — Supports item/block tags like `#minecraft:swords`
- **📂 Multi-File Config** — Split configuration across multiple JSON files for easy management

---

## 📦 Installation

1. Install [NeoForge 26.1](https://neoforged.net/) or later
2. Download the mod `.jar` file and place it in your `mods/` folder
3. Launch the game — the mod will auto-generate default configuration files

---

## ⚙ Configuration

### TOML Config (editable in-game)

`config/hudtips/hudtips-client.toml`:

| Setting | Description | Default |
|---------|-------------|---------|
| `enabled` | Master toggle for the mod | `true` |
| `scaleWithGui` | Auto-adjust text size | `false` |
| `textSize` | Base text size (0-100) | `50` |
| `resolutionComp` | Resolution compensation (0-100) | `50` |
| `guiScaleComp` | GUI scale compensation (0-100) | `50` |
| `itemHintPosition` | Default position for item hints | `bottom_left` |
| `globalSeenState` | Global seen state (share tutorial progress across saves) | `false` |

### JSON Rule Config

Create `.json` files under `config/hudtips/` to define hint rules. A `_reference.json` file is auto-generated on first launch.

---

## 🎮 Trigger Types

### Continuous — displayed while conditions are met

| Trigger | Description | Example |
|---------|-------------|---------|
| `hold_item` | Holding a specific item | Show blade aura tip when holding diamond sword |
| `on_dimension` | In a specific dimension | Show warning while in the Nether |
| `on_low_health` | Health below threshold | Show escape tip when low on health |
| `on_look_block` | Looking at a specific block | Show "Right-click to open crafting" when looking at a crafting table |
| `on_look_entity` | Looking at a specific entity | Show "Run!" when looking at a creeper |

### Event — activated on trigger, dismissed by timer

| Trigger | Description | Example |
|---------|-------------|---------|
| `on_use` | Using an item | Show charge tip when right-clicking a bow |
| `on_dimension_change` | Changing dimensions | Show guide when first entering the Nether |
| `on_key_press` | Pressing a key | Show control tip when pressing sneak |
| `on_kill` | Killing an entity | Show loot tip when killing a zombie |
| `on_activate_block` | Right-clicking a block with an item | Show growth tip when using bonemeal on a sapling |

---

## 📝 Example Rules

### Basic: Hold Item Hint

```json
{
  "items": ["minecraft:diamond_sword"],
  "text": "💡 Press {key:key.sneak} + right-click to release blade aura"
}
```

### AND Combination: Hold Item + Specific Dimension

```json
{
  "items": ["minecraft:bed"],
  "text": "💥 Beds explode in the Nether!",
  "triggerOn": {
    "hold_item": true,
    "on_dimension": "minecraft:the_nether"
  },
  "triggerOnMode": "all"
}
```

### Multi-Trigger OR: Show on hold or use

```json
{
  "items": ["minecraft:bow"],
  "text": "🎯 Hold right-click to charge, release to fire!",
  "triggerOn": ["hold_item", "on_use"],
  "dismissOn": ["time:3000"]
}
```

### Tutorial: Show Once + Celebration Animation

```json
{
  "items": ["minecraft:wooden_pickaxe"],
  "type": "guide",
  "text": "⛏ Right-click on ores to mine! Coal and iron are the best early resources",
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

### Look at Entity + Hold Item

```json
{
  "items": ["minecraft:shears"],
  "type": "guide",
  "id": "shear_sheep",
  "text": "✂ Right-click to shear the sheep!",
  "triggerOn": {
    "hold_item": true,
    "on_look_entity": "minecraft:sheep"
  },
  "triggerOnMode": "all",
  "dismissOn": ["on_use"]
}
```

---

## 🏗 Building

```bash
# Clone the repository
git clone https://github.com/Li-ng/hudtips.git
cd hudtips

# Build
./gradlew build

# Output located at build/libs/
```

Requires JDK 25 and NeoForge 26.1.

---

## 📄 License

MIT License — see [LICENSE](LICENSE) for details.

---

## 🙏 Credits

- [NeoForge](https://neoforged.net/) — Minecraft modding platform
- All players who provided feedback and suggestions
