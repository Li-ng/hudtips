package com.xiaogua.hudtips.client.config;

import com.xiaogua.hudtips.client.trigger.HudTrigger;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 单条提示规则。
 * A single hint rule.
 *
 * <p>定义了一个提示的触发条件、显示内容和消失条件。</p>
 * <p>Defines a hint's trigger conditions, display content, and dismiss conditions.</p>
 *
 * <h2>字段分层 / Field Layering</h2>
 * <table border="1">
 *   <tr><th>层级 / Level</th><th>字段 / Field</th><th>说明 / Description</th></tr>
 *   <tr><td>框架级（顶层） / Framework (top)</td>
 *       <td>{@code item / items}</td>
 *       <td>物品条件 — 被多个触发器共享，参与索引构建
 *           / Item condition — shared by multiple triggers, used for index building</td></tr>
 *   <tr><td>框架级（顶层） / Framework (top)</td>
 *       <td>{@code triggerOn / triggerOnMode / dismissOn / dismissMode}</td>
 *       <td>元控制 — 决定规则由哪些触发器管理、如何消失
 *           / Meta control — determines which triggers manage this rule and how it is dismissed</td></tr>
 *   <tr><td>触发器专属 / Trigger-specific</td>
 *       <td>{@code triggerOn} Map 的值 / Map values</td>
 *       <td>各个触发器的专属参数直接内联在 triggerOn 中
 *           / Per-trigger parameters inlined directly in triggerOn (e.g. on_dimension: "minecraft:the_nether")</td></tr>
 *   <tr><td>显示样式 / Display style</td>
 *       <td>{@code color / position / offsetX / textScale ...}</td>
 *       <td>三层继承：规则自身 → 触发器默认 → 全局默认
 *           / Three-layer inheritance: rule → trigger default → global default</td></tr>
 * </table>
 *
 * <h2>必填字段 / Required Fields:</h2>
 * <ul>
 *   <li>{@code text} - 提示文本 / hint text</li>
 * </ul>
 *
 * <h2>JSON 示例 / JSON Examples:</h2>
 * <pre>
 * // 示例1：物品中心 - 最简写法（自动推断 triggerOn 为 hold_item）
 * // Example 1: Item-centric — simplest form (auto-infers triggerOn as hold_item)
 * {
 *   "items": ["minecraft:diamond_sword"],
 *   "text": "💡 按 [Shift] + 右键 释放剑气"
 * }
 *
 * // 示例2：多触发器 OR 组合
 * // Example 2: Multi-trigger OR combination
 * {
 *   "items": ["minecraft:bow"],
 *   "text": "🎯 蓄力射击",
 *   "triggerOn": ["hold_item", "on_use"],
 *   "dismissOn": ["time:3000"]
 * }
 *
 * // 示例3：维度 + AND 组合（参数内联在 triggerOn 中）
 * // Example 3: Dimension + AND combination (params inlined in triggerOn)
 * {
 *   "items": ["minecraft:bed"],
 *   "text": "💥 在下界使用床会爆炸！",
 *   "triggerOn": {
 *     "hold_item": true,
 *     "on_dimension": "minecraft:the_nether"
 *   },
 *   "triggerOnMode": "all"
 * }
 *
 * // 示例4：激活方块触发器（参数内联）
 * // Example 4: Activate block trigger (params inlined)
 * {
 *   "items": ["minecraft:bone_meal"],
 *   "type": "guide",
 *   "triggerOn": {
 *     "on_activate_block": { "targetBlocks": ["#minecraft:saplings", "minecraft:grass_block"] }
 *   },
 *   "text": "🌱 骨粉催熟！",
 *   "dismissOn": ["time:3000"]
 * }
 * </pre>
 *
 * @see TriggerConfig
 * @see HudConfig
 */
public class HintRule {
    /**
     * 触发物品的注册名列表。
     * List of trigger item registry names.
     *
     * <p>格式为 {@code "namespace:item_id"} / Format: {@code "namespace:item_id"}，例如 / e.g.：</p>
     * <ul>
     *   <li>{@code "minecraft:diamond_sword"} - 钻石剑 / diamond sword</li>
     *   <li>{@code "minecraft:bow"} - 弓 / bow</li>
     *   <li>{@code "modname:custom_item"} - 其他模组的物品 / item from another mod</li>
     * </ul>
     *
     * <p>也可使用物品标签（以 {@code #} 开头）/ Also supports item tags (prefixed with {@code #})：
     * {@code "#minecraft:swords"}。</p>
     *
     * <p>JSON 中可使用 {@code "item": "xxx"}（单个字符串，自动转为单元素列表）
     * 或 {@code "items": ["a", "b"]}（数组）。内部统一存储为列表。</p>
     * <p>JSON accepts {@code "item": "xxx"} (single string, auto-wrapped to list)
     * or {@code "items": ["a", "b"]} (array). Stored internally as list.</p>
     *
     * <pre>
     * // 单个物品 / Single item
     * "items": ["minecraft:diamond_sword"]
     *
     * // 多个物品共享同一条提示 / Multiple items sharing one hint
     * "items": ["minecraft:bow", "minecraft:crossbow"]
     * </pre>
     */
    public List<String> items;

    /**
     * 提示文本（必填）。
     * Hint text (required).
     *
     * <p>支持以下两种模式：</p>
     * <p>Supports two modes:</p>
     * <ul>
     *   <li><b>翻译键模式</b>：文本为 Minecraft 翻译键名（如 {@code "hudtips.hint.diamond_sword"}），
     *       通过 {@code Component.translatable()} 解析，自动适配游戏语言。
     *       需在语言文件（如 {@code assets/<modid>/lang/zh_cn.json}）中定义对应条目。</li>
     *   <li><b>Translation key mode</b>: text is a Minecraft translation key (e.g. {@code "hudtips.hint.diamond_sword"}),
     *       resolved via {@code Component.translatable()}, auto-adapts to game language.
     *       Requires corresponding entries in language files (e.g. {@code assets/<modid>/lang/en_us.json}).</li>
     *   <li><b>直接文本模式</b>：文本为普通字符串（如 {@code "💡 钻石剑：右键触发剑气"}），
     *       直接显示。Minecraft 翻译系统对未知键会原样返回，因此两种模式可无缝混合使用。</li>
     *   <li><b>Direct text mode</b>: text is a plain string (e.g. {@code "💡 Diamond Sword: right-click for sword aura"}),
     *       displayed directly. Minecraft's translation system returns unknown keys as-is,
     *       so both modes can be mixed seamlessly.</li>
     * </ul>
     *
     * <p>翻译值中同样支持 {@code {key:翻译键名}} 占位符，
     * 会在翻译后被替换为玩家设置的实际按键名。</p>
     * <p>{@code {key:translation_key}} placeholders are also supported in translated values
     * and will be replaced with the player's actual bound key name after translation.</p>
     *
     * <p>也可以使用字符串数组 {@link #texts} 定义多条文本，
     * 每条文本自动展开为独立规则，优先级按数组顺序从高到低。</p>
     * <p>Can also use the string array {@link #texts} for multiple texts;
     * each text auto-expands into an independent rule, priority decreasing by array order.</p>
     *
     * <h3>JSON 示例 / JSON Examples:</h3>
     * <pre>
     * // 翻译键模式（推荐 —— 支持多语言）
     * // Translation key mode (recommended — i18n support)
     * {
     *   "items": ["minecraft:diamond_sword"],
     *   "text": "hudtips.hint.diamond_sword"
     * }
     *
     * // 直接文本模式（简单场景）
     * // Direct text mode (simple cases)
     * {
     *   "items": ["minecraft:diamond_sword"],
     *   "text": "💡 钻石剑：右键触发剑气"
     * }
     * </pre>
     *
     * @see #getDisplayText()
     */
    public String text;

    /**
     * 多文本列表（替代 {@link #text} 的数组语法）。
     * Multi-text list (the array syntax alternative to {@link #text}).
     *
     * <p>JSON 中 {@code "text"} 字段为数组时，Gson 通过
     * {@code HintRuleDeserializer} 将其移入此字段，
     * 同时将第一个元素放入 {@link #text}。</p>
     * <p>When the {@code "text"} field is an array in JSON, Gson moves it here
     * via {@code HintRuleDeserializer}, placing the first element into {@link #text}.</p>
     *
     * <p>在规则加载时，含多条文本的规则会被展开为多条独立规则，
     * 优先级按数组顺序从高到低自动分配：</p>
     * <p>When loading, rules with multiple texts are expanded into independent rules,
     * with priority auto-assigned from high to low by array order:</p>
     * <pre>
     * "text": ["第一优先 / highest", "第二优先 / medium", "第三优先 / lowest"]
     * // 展开后 / After expansion:
     * //   规则1: text="第一优先", priority=base+2
     * //   规则2: text="第二优先", priority=base+1
     * //   规则3: text="第三优先", priority=base+0
     * </pre>
     *
     * <p>如果规则同时设置了 {@link #priority} 显式值，
     * 则以显式值为最低优先级（最后一条）的基准。</p>
     * <p>If the rule also has an explicit {@link #priority}, that value becomes
     * the baseline for the lowest-priority (last) expanded rule.</p>
     *
     * <h3>JSON 示例 / JSON Example:</h3>
     * <pre>
     * {
     *   "items": ["minecraft:diamond_sword"],
     *   "type": "guide",
     *   "text": [
     *     "钻石剑: 按 Shift+右键 释放剑气",
     *     "钻石剑: 剑气冷却中...",
     *     "钻石剑: 剑气准备就绪！"
     *   ]
     * }
     * </pre>
     */
    public List<String> texts;

    /**
     * 提示文字颜色（十六进制）。
     * Hint text color (hex).
     *
     * <p>为 null 时使用触发器/全局的 defaultColor。 / null → uses trigger/global defaultColor.</p>
     *
     * <p>格式 / Format:</p>
     * <ul>
     *   <li>{@code "#RRGGBB"} - 6 位 RGB，自动不透明。示例 / e.g. {@code "#FFAA00"} = 金色 / gold</li>
     *   <li>{@code "#AARRGGBB"} - 8 位 ARGB，含透明度。示例 / e.g. {@code "#80FFFFFF"} = 半透明白 / semi-transparent white</li>
     * </ul>
     *
     * <p>常用颜色参考 / Common Color Reference:</p>
     * <ul>
     *   <li>{@code "#FFFFFF"} - 白色 / white</li>
     *   <li>{@code "#FFAA00"} - 金色/橙色 / gold/orange</li>
     *   <li>{@code "#55FF55"} - 绿色 / green</li>
     *   <li>{@code "#FF5555"} - 红色 / red</li>
     *   <li>{@code "#5555FF"} - 蓝色 / blue</li>
     *   <li>{@code "#AA00FF"} - 紫色 / purple</li>
     * </ul>
     */
    public String color;

    /**
     * 启动触发器配置（数组格式或对象格式）。
     * Trigger activation config (array or object format).
     *
     * <p>指定哪些触发器可以激活此提示。满足任一触发器的条件时，提示将显示
     * （{@code triggerOnMode: "all"} 时需全部满足）。</p>
     * <p>Specifies which triggers can activate this hint. The hint displays when any trigger's
     * condition is met (or all must be met when {@code triggerOnMode: "all"}).</p>
     *
     * <h3>格式一：数组格式（简洁 OR 语法）/ Format 1: Array (concise OR syntax)</h3>
     * <pre>
     * "triggerOn": ["hold_item", "on_use"]
     * </pre>
     * <p>每个元素是触发器类型名。数组在反序列化时自动转为对象格式。</p>
     * <p>Each element is a trigger type name. Arrays are auto-converted to object format during deserialization.</p>
     *
     * <h3>格式二：对象格式（支持参数传递）/ Format 2: Object (supports parameters)</h3>
     * <pre>
     * // 无参数触发器 / Parameterless triggers
     * "triggerOn": { "hold_item": true, "on_use": true }
     *
     * // 单参数简写 / Single-param shorthand
     * "triggerOn": { "on_dimension": "minecraft:the_nether" }
     *
     * // 多参数完整写法 / Multi-param full form
     * "triggerOn": {
     *   "on_activate_block": { "targetBlocks": ["minecraft:obsidian", "#minecraft:logs"] }
     * }
     * </pre>
     *
     * <p>值类型 / Value types：</p>
     * <ul>
     *   <li>{@code Boolean.TRUE} — 无参数触发器 / parameterless trigger (e.g. hold_item, on_use)</li>
     *   <li>{@code String} — 单参数简写 / single-param shorthand (e.g. on_dimension: "minecraft:the_nether")</li>
     *   <li>{@code Map<String,Object>} — 多参数完整写法 / multi-param full form (e.g. targetBlocks list)</li>
     * </ul>
     *
     * <h3>为 null 时的智能推断规则（按优先级）/ Auto-inference when null (by priority)</h3>
     * <ol>
     *   <li>有 item + dimension + triggerOnMode="all" → {@code {"hold_item": true, "on_dimension": dimension value}}</li>
     *   <li>有 item + targetBlock/targetBlocks → {@code {"on_activate_block": param value}}</li>
     *   <li>有 dimension 且无 item / has dimension, no item → {@code {"on_dimension_change": dimension value}}</li>
     *   <li>有 item → {@code ["hold_item"]} 或 section 自身类型 / or section's own type</li>
     * </ol>
     *
     * <h3>可用的触发器类型及参数形式 / Available Trigger Types & Parameter Forms</h3>
     * <table border="1">
     *   <tr><th>触发器 / Trigger</th><th>类型 / Type</th><th>参数形式 / Param Form</th><th>说明 / Description</th></tr>
     *   <tr><td>{@code hold_item}</td><td>continuous</td>
     *       <td>{@code true}</td>
     *       <td>无参数，手持规则中声明的任意物品时激活
     *           / No params, activates when holding any item declared in the rule</td></tr>
     *   <tr><td>{@code on_use}</td><td>event</td>
     *       <td>{@code true}</td>
     *       <td>无参数，使用规则中声明的物品时触发
     *           / No params, fires when using an item declared in the rule</td></tr>
     *   <tr><td>{@code on_key_press}</td><td>event</td>
     *       <td>{@code "key.sneak"} 或 / or {@code true}</td>
     *       <td>翻译键名 / translation key (e.g. key.sneak / key.inventory), {@code true} = match any key</td></tr>
     *   <tr><td>{@code on_kill}</td><td>event</td>
     *       <td>{@code "minecraft:zombie"} 或 / or {@code true}</td>
     *       <td>指定实体 ID 时仅匹配该实体，{@code true} 时击杀任意实体触发
     *           / Specific entity ID matches only that entity, {@code true} triggers on any kill</td></tr>
     *   <tr><td>{@code on_activate_block}</td><td>event</td>
     *       <td>简写 / shorthand: {@code "minecraft:crafting_table"}<br>
     *           完整 / full: {@code {"targetBlock": "...", "targetBlocks": [...]}}</td>
     *       <td>手持规则物品右键点击匹配方块时触发
     *           / Fires when right-clicking a matching block while holding the rule's item</td></tr>
     *   <tr><td>{@code on_look_entity}</td><td>continuous</td>
     *       <td>{@code "minecraft:creeper"} 或 / or {@code true}</td>
     *       <td>指定实体 ID 时仅匹配该实体，{@code true} 时指向任意实体激活
     *           / Specific entity ID matches only that entity, {@code true} activates when looking at any entity</td></tr>
     *   <tr><td>{@code on_look_block}</td><td>continuous</td>
     *       <td>简写 / shorthand: {@code "minecraft:chest"}<br>
     *           完整 / full: {@code {"targetBlock": "...", "targetBlocks": [...]}}</td>
     *       <td>指向匹配方块时持续激活，支持标签
     *           / Continuously active when looking at matching block, supports tags (e.g. {@code "#minecraft:logs"})</td></tr>
     *   <tr><td>{@code on_low_health}</td><td>continuous</td>
     *       <td>{@code true}</td>
     *       <td>无参数，血量低于阈值时激活（阈值在触发器 settings 中配置）
     *           / No params, activates when health is below threshold (configured in trigger settings)</td></tr>
     *   <tr><td>{@code on_dimension}</td><td>continuous</td>
     *       <td>{@code "minecraft:the_nether"}</td>
     *       <td>维度 ID / dimension ID (namespace:path)，处于该维度时持续激活 / continuously active in this dimension</td></tr>
     *   <tr><td>{@code on_dimension_change}</td><td>event</td>
     *       <td>{@code "minecraft:the_nether"}</td>
     *       <td>维度 ID，切换到该维度时触发（一次性事件）
     *           / Dimension ID, fires once when switching to this dimension (one-shot event)</td></tr>
     * </table>
     *
     * <h3>JSON 示例 / JSON Examples</h3>
     * <pre>
     * // 手持或使用弓时都显示提示 / Show hint when holding or using a bow
     * {
     *   "items": ["minecraft:bow"],
     *   "text": "🎯 蓄力射击",
     *   "triggerOn": ["hold_item", "on_use"]
     * }
     *
     * // 带参数的 AND 组合 / AND combination with params
     * {
     *   "items": ["minecraft:bed"],
     *   "text": "💥 在下界使用床会爆炸！",
     *   "triggerOn": {
     *     "hold_item": true,
     *     "on_dimension": "minecraft:the_nether"
     *   },
     *   "triggerOnMode": "all"
     * }
     * </pre>
     *
     * <p><b>兼容性 / Compatibility：</b>旧数组格式 / legacy array format {@code ["hold_item", "on_use"]}
     * 在反序列化时自动转为 {@code {"hold_item": true, "on_use": true}}。
     * / auto-converted to object format during deserialization.</p>
     *
     * @see #dismissOn
     * @see #triggerOnMode
     * @see HudTrigger#getType()
     */
    public Map<String, Object> triggerOn;

    /**
     * 背景颜色覆盖。
     * Background color override.
     *
     * <p>为 null 时使用触发器/全局的 backgroundColor。 / null → uses trigger/global backgroundColor.</p>
     *
     * <p>格式同 {@link #color} / Same format as {@link #color}。示例 / Examples：</p>
     * <ul>
     *   <li>{@code "#00000000"} - 全透明（无背景）/ fully transparent (no background)</li>
     *   <li>{@code "#80000000"} - 半透明黑 / semi-transparent black</li>
     *   <li>{@code "#FF000000"} - 纯黑背景 / solid black background</li>
     *   <li>{@code "#80FF0000"} - 半透明红 / semi-transparent red</li>
     * </ul>
     */
    public String backgroundColor;

    /**
     * 锚点位置覆盖。
     * Anchor position override.
     *
     * <p>为 null 时使用触发器/全局的 position。 / null → uses trigger/global position.</p>
     *
     * <p>可选值 / Valid values：</p>
     * <ul>
     *   <li>{@code "bottom_left"} - 左下角 / bottom-left corner</li>
     *   <li>{@code "bottom_center"} - 底部居中 / bottom center</li>
     *   <li>{@code "bottom_right"} - 右下角 / bottom-right corner</li>
     *   <li>{@code "top_left"} - 左上角 / top-left corner</li>
     *   <li>{@code "top_center"} - 顶部居中 / top center</li>
     *   <li>{@code "top_right"} - 右上角 / top-right corner</li>
     * </ul>
     */
    public String position;

    /**
     * X 偏移覆盖（像素）。
     * X offset override (pixels).
     * 为 null 时使用触发器/全局的 offsetX。 / null → uses trigger/global offsetX.
     */
    public Integer offsetX;

    /**
     * Y 偏移覆盖（像素）。
     * Y offset override (pixels).
     * 为 null 时使用触发器/全局的 offsetY。 / null → uses trigger/global offsetY.
     */
    public Integer offsetY;

    /**
     * 文字缩放覆盖。
     * Text scale override.
     *
     * <p>为 null 时使用触发器/全局的 textScale。 / null → uses trigger/global textScale.</p>
     *
     * <p>示例值 / Example values：</p>
     * <ul>
     *   <li>{@code 0.5} - 一半大小 / half size</li>
     *   <li>{@code 1.0} - 原始大小 / original size</li>
     *   <li>{@code 1.5} - 1.5 倍大小 / 1.5× size</li>
     *   <li>{@code 2.0} - 两倍大小 / double size</li>
     * </ul>
     */
    public Float textScale;

    /**
     * 触发器组合模式。
     * Trigger combination mode.
     *
     * <p>指定当多个触发器在 {@link #triggerOn} 中时，如何判断是否激活：</p>
     * <p>Determines how to judge activation when multiple triggers are in {@link #triggerOn}:</p>
     * <ul>
     *   <li>{@code "any"} 或 null（默认 / default） — 任一触发器激活即显示提示 / show hint when any trigger activates</li>
     *   <li>{@code "all"} — 所有触发器必须同时激活才显示提示 / all triggers must be simultaneously active</li>
     * </ul>
     *
     * <h3>JSON 示例（AND 组合）/ JSON Example (AND combination):</h3>
     * <pre>
     * {
     *   "items": ["minecraft:bed"],
     *   "text": "💥 在下界使用床会爆炸！",
     *   "triggerOn": {
     *     "hold_item": true,
     *     "on_dimension": "minecraft:the_nether"
     *   },
     *   "triggerOnMode": "all"
     * }
     * </pre>
     *
     * <p>为 null 时默认为 {@code "any"}（向后兼容）/ defaults to {@code "any"} when null (backward compatible).</p>
     */
    public String triggerOnMode;

    /**
     * 提示优先级（同一触发器的同位置提示，数值越大越优先）。
     * Hint priority (higher value = higher priority for hints at the same position from the same trigger).
     *
     * <p>为 null（默认）时，有效优先级为 0（普通规则）或 2（教程型规则）。
     * 非 null 时使用显式值覆盖隐式逻辑。</p>
     * <p>When null (default): effective priority is 0 (normal rule) or 2 (guide rule).
     * When non-null: explicit value overrides implicit logic.</p>
     *
     * <p>同有效优先级时，按 JSON 配置中的先后顺序决定（先写的优先）。</p>
     * <p>When effective priorities are equal, order in JSON config decides (first written = higher priority).</p>
     *
     * <h3>JSON 示例 / JSON Example:</h3>
     * <pre>
     * {
     *   "items": ["minecraft:diamond_sword"],
     *   "text": "高优先级提示",
     *   "priority": 5
     * }
     * </pre>
     */
    public Integer priority;

    /**
     * 提示类型 —— 区分教程型和普通型。
     * Hint type — distinguishes guide hints from normal hints.
     *
     * <table border="1">
     *   <tr><th>值 / Value</th><th>复选框 / Checkbox</th><th>庆祝动画 / Celebration</th><th>一次性 / One-shot</th><th>说明 / Description</th></tr>
     *   <tr><td>{@code "guide"}</td><td>有 / Yes ✓</td><td>有 / Yes ✨</td><td>是 / Yes</td><td><b>教程型 / Guide</b>：完成状态存于存档目录 / completion state stored in world save dir</td></tr>
     *   <tr><td>{@code null} / 不写 / omit</td><td>无 / No</td><td>无 / No</td><td>否 / No</td><td><b>普通型 / Normal</b>：每次都触发 / fires every time</td></tr>
     * </table>
     *
     * <h3>JSON 示例 / JSON Examples:</h3>
     * <pre>
     * // 教程型 / Guide type
     * {
     *   "items": ["minecraft:bow"],
     *   "type": "guide",
     *   "text": "🎯 长按右键蓄力，松开发射！",
     *   "dismissOn": ["on_use"]
     * }
     *
     * // 普通型 / Normal type (omit type)
     * {
     *   "items": ["minecraft:diamond_sword"],
     *   "text": "💡 按 [Shift] + 右键 释放剑气"
     * }
     * </pre>
     *
     * @see com.xiaogua.hudtips.client.TextAnimationManager.Phase#CELEBRATING
     */
    public String type;

    /** 教程型常量 / Guide type constant */
    public static final String TYPE_GUIDE = "guide";

    // ============================================================
    //  type 便捷方法
    //  Type Convenience Methods
    // ============================================================

    /** @return true 如果此规则是教程型提示（有复选框 + 庆祝动画 + 一次性）/ if this rule is a guide hint (checkbox + celebrate anim + one-shot) */
    public boolean isGuideComplete() {
        return TYPE_GUIDE.equals(type);
    }

    /**
     * 此规则是否显式配置了物品目标（items 字段）。
     * Whether this rule explicitly configures item targets (items field).
     *
     * <p>与触发器类型是否为物品驱动（{@code isItemBased()}）是不同的概念：</p>
     * <p>Distinct concept from trigger type being item-driven ({@code isItemBased()}):</p>
     * <ul>
     *   <li>{@code hasItemTarget()} — 规则配置中写了具体的物品 ID，用于决定位置来源（TOML 配置 vs 规则自身）
     *       / Rule config has specific item IDs, used to decide position source (TOML config vs rule itself)</li>
     *   <li>触发器 {@code isItemBased()} — 该触发器类型以 Item 作为激活标识，用于物品互斥驱逐
     *       / The trigger type uses Item as activation identifier, for item mutual exclusion</li>
     * </ul>
     *
     * @return true 如果 items 字段非空 / if items is non-empty
     */
    public boolean hasItemTarget() {
        return items != null && !items.isEmpty();
    }

    /**
     * 获取此规则的显示文本，通过 Minecraft 翻译系统解析。
     * Gets this rule's display text, resolved through Minecraft's translation system.
     *
     * <p>始终将 {@link #text} 作为翻译键调用 {@code Component.translatable()}：</p>
     * <p>Always calls {@code Component.translatable()} with {@link #text} as the translation key:</p>
     * <ul>
     *   <li>如果 {@code text} 是已定义的翻译键 → 返回对应语言的翻译文本
     *       / If {@code text} is a defined translation key → returns the translated text</li>
     *   <li>如果 {@code text} 是普通文本（翻译键不存在）→ 原样返回，行为等价于直接显示
     *       / If {@code text} is plain text (key not found) → returned as-is, equivalent to direct display</li>
     * </ul>
     *
     * <p>这意味着整合包作者可以：</p>
     * <p>This means modpack authors can:</p>
     * <ul>
     *   <li>直接写中文/英文提示文本 → 始终显示该文本（简单场景）
     *       / Write direct hint text → always displayed (simple cases)</li>
     *   <li>写翻译键名 → 自动适配游戏语言（多语言场景）
     *       / Write translation keys → auto-adapts to game language (i18n cases)</li>
     *   <li>从直接文本逐步迁移到翻译键 → 无需修改 JSON 结构
     *       / Gradually migrate from direct text to translation keys without changing JSON structure</li>
     * </ul>
     *
     * <p>注意：此方法在客户端调用，确保在有效 {@code Minecraft} 实例上下文中执行。
     * 返回的文本中可能仍包含 {@code {key:...}} 占位符，
     * 需由 {@link com.xiaogua.hudtips.client.KeyMappingLookup#resolveKeyPlaceholders(String)} 进一步处理。</p>
     * <p>Note: Called on the client; ensure within a valid {@code Minecraft} instance context.
     * Returned text may still contain {@code {key:...}} placeholders,
     * which need further processing by {@link com.xiaogua.hudtips.client.KeyMappingLookup#resolveKeyPlaceholders(String)}.</p>
     *
     * @return 翻译后的文本，或原始文本（翻译键不存在时）；text 为 null 时返回空字符串
     *         / Translated text, or raw text (if key not found); empty string when text is null
     */
    public String getDisplayText() {
        if (text == null || text.isEmpty()) return "";
        return net.minecraft.network.chat.Component.translatable(text).getString();
    }

    // ============================================================
    //  tagBased 判定（预计算缓存，供优先级比较用）
    //  Tag-Based Detection (precomputed cache, for priority comparison)
    // ============================================================

    /**
     * 判断此规则是否以物品标签（而非具体物品）匹配。
     * Checks whether this rule matches by item tag (rather than specific item).
     *
     * <p>标签以 {@code #} 开头，例如 {@code "#minecraft:swords"}。
     * 用于优先级比较：无显式 priority 时，具体物品规则默认优先于标签规则。</p>
     * <p>Tags start with {@code #}, e.g. {@code "#minecraft:swords"}.
     * Used for priority comparison: without explicit priority, specific-item rules default to higher priority than tag rules.</p>
     *
     * <p>结果在规则加载时预计算并缓存，首次调用时若尚未预计算则走惰性求值路径。</p>
     * <p>Result is precomputed and cached at rule load time; falls back to lazy evaluation on first call if not yet computed.</p>
     */
    public boolean isTagBased() {
        if (tagBasedCache == null) {
            tagBasedCache = computeTagBased();
        }
        return tagBasedCache;
    }

    /** 计算标签匹配状态 / Compute tag-based status */
    public boolean computeTagBased() {
        if (items != null) {
            for (String it : items) {
                if (it != null && it.startsWith("#")) return true;
            }
        }
        return false;
    }

    /** 设置标签匹配缓存 / Set tag-based cache */
    public void setTagBased(boolean value) {
        this.tagBasedCache = value;
    }

    // ============================================================
    //  深拷贝
    //  Deep Copy
    // ============================================================

    /**
     * 深拷贝此规则的所有配置字段（不含运行时缓存）。
     * Deep-copies all config fields of this rule (excluding runtime caches).
     *
     * <p>用于多文本展开：复制原始规则的全部设置（颜色、位置、触发条件等）。
     * 运行时缓存字段不会被复制，由调用方在适当时候重新计算。</p>
     * <p>Used for multi-text expansion: copies all settings (color, position, trigger conditions, etc.).
     * Runtime cache fields are not copied; re-computed by caller when needed.</p>
     *
     * <p><b>新增顶层字段时只需在此方法中加一行。</b></p>
     * <p><b>When adding a new top-level field, just add one line in this method.</b></p>
     *
     * @return 独立的新规则副本 / Independent new rule copy
     */
    public HintRule copy() {
        HintRule clone = new HintRule();
        clone.items = this.items != null ? new ArrayList<>(this.items) : null;
        clone.text = this.text;
        clone.texts = this.texts != null ? new ArrayList<>(this.texts) : null;
        clone.color = this.color;
        clone.backgroundColor = this.backgroundColor;
        clone.position = this.position;
        clone.offsetX = this.offsetX;
        clone.offsetY = this.offsetY;
        clone.textScale = this.textScale;
        clone.triggerOnMode = this.triggerOnMode;
        clone.triggerOn = this.triggerOn != null ? new HashMap<>(this.triggerOn) : null;
        clone.priority = this.priority;
        clone.type = this.type;
        clone.dismissOn = this.dismissOn != null ? new ArrayList<>(this.dismissOn) : null;
        // 运行时缓存不复制，由调用方重新计算
        // Runtime caches are not copied; re-computed by caller
        return clone;
    }

    // ============================================================
    //  triggerOn 参数读取
    //  triggerOn Parameter Reading
    // ============================================================

    /**
     * 获取指定触发器的参数值。
     * Gets the parameter value for a specific trigger.
     *
     * @param triggerType 触发器类型名 / trigger type name (e.g. "on_dimension")
     * @return 参数值 / parameter value (Boolean/true, String, Map), null if not present
     */
    private Object getParam(String triggerType) {
        if (triggerOn == null) return null;
        return triggerOn.get(triggerType);
    }

    /**
     * 获取指定触发器的字符串参数（简写形式）。
     * Gets the string parameter for a specific trigger (shorthand form).
     * 如 / e.g. {@code "on_dimension": "minecraft:the_nether"} → {@code "minecraft:the_nether"}。
     */
    private String getParamString(String triggerType) {
        Object val = getParam(triggerType);
        return val instanceof String s ? s : null;
    }

    /**
     * 获取指定触发器的子键字符串值（多参数对象形式）。
     * Gets a sub-key string value for a specific trigger (multi-param object form).
     * 如 / e.g. {@code "on_activate_block": {"targetBlock": "minecraft:obsidian"}} with key="targetBlock" → {@code "minecraft:obsidian"}。
     */
    @SuppressWarnings("unchecked")
    private String getSubParam(String triggerType, String key) {
        Object val = getParam(triggerType);
        if (val instanceof Map<?, ?> m) {
            Object sub = m.get(key);
            return sub instanceof String s ? s : null;
        }
        return null;
    }

    /**
     * 获取指定触发器的子键列表值。
     * Gets a sub-key list value for a specific trigger.
     */
    @SuppressWarnings("unchecked")
    private List<String> getSubParamList(String triggerType, String key) {
        Object val = getParam(triggerType);
        if (val instanceof Map<?, ?> m) {
            Object sub = m.get(key);
            if (sub instanceof List<?> list) {
                try { return (List<String>) list; } catch (ClassCastException e) { return null; }
            }
        }
        return null;
    }

    // ── 便捷封装 / Convenience Wrappers ──

    /** 从 triggerOn 中查找维度参数 / Look up dimension param from triggerOn (on_dimension or on_dimension_change string value) */
    public String getDimension() {
        String dim = getParamString("on_dimension");
        if (dim != null) return dim;
        return getParamString("on_dimension_change");
    }

    /** @return 是否配置了维度 / whether a dimension is configured */
    public boolean hasDimension() {
        return getDimension() != null;
    }

    /**
     * 规则是否指定了具体的实体类型 ID（而非只匹配任意实体）。
     * Whether the rule specifies a concrete entity type ID (rather than matching any entity).
     *
     * <p>用于 AND 模式的 checkAllTriggersMatch：当 triggerOn 值为 true 时，
     * 表示"只要该触发器激活就算匹配"，不需要检查具体实体/方块 ID。</p>
     * <p>Used in AND mode checkAllTriggersMatch: when triggerOn value is true,
     * means "match as long as this trigger is active", no need to check specific IDs.</p>
     *
     * @param triggerType 触发器类型名 / trigger type name
     * @return true 如果 triggerOn[triggerType] 的值是具体字符串（而非 Boolean.TRUE）/ if the value is a concrete string (not Boolean.TRUE)
     */
    public boolean hasSpecificTarget(String triggerType) {
        return getParamString(triggerType) != null;
    }

    /** 从 triggerOn 中查找实体参数 / Look up entity param from triggerOn (on_kill string value) */
    public String getEntity() {
        return getParamString("on_kill");
    }

    /** @return 是否配置了实体 / whether an entity is configured (via triggerOn.on_kill) */
    public boolean hasEntity() {
        return getEntity() != null;
    }

    /** 从 triggerOn 中查找实体参数 / Look up entity param from triggerOn (on_look_entity string value) */
    public String getLookEntity() {
        return getParamString("on_look_entity");
    }

    /** @return 是否配置了 on_look_entity 的实体参数 / whether on_look_entity entity param is configured */
    public boolean hasLookEntity() {
        return getLookEntity() != null;
    }

    /** 从 triggerOn 中查找目标方块参数 / Look up target block param from triggerOn (on_look_block value or sub-key) */
    public String getLookBlock() {
        String lb = getParamString("on_look_block");
        if (lb != null && !lb.isEmpty()) return lb;
        return getSubParam("on_look_block", "targetBlock");
    }

    /** 从 triggerOn 中查找目标方块列表 / Look up target block list from triggerOn (on_look_block sub-key list) */
    public List<String> getLookBlocks() {
        return getSubParamList("on_look_block", "targetBlocks");
    }

    /** @return 是否配置了 on_look_block 的方块参数 / whether on_look_block block param is configured */
    public boolean hasLookBlock() {
        return getParam("on_look_block") != null;
    }

    /** 从 triggerOn 中查找按键参数 / Look up key param from triggerOn (on_key_press string value, e.g. "key.sneak") */
    public String getKeyPress() {
        return getParamString("on_key_press");
    }

    /** @return 是否配置了 on_key_press 的按键参数 / whether on_key_press key param is configured */
    public boolean hasKeyPress() {
        return getKeyPress() != null;
    }

    /** 从 triggerOn 中查找 targetBlock / Look up targetBlock from triggerOn (on_activate_block value or sub-key) */
    public String getTargetBlock() {
        String tb = getParamString("on_activate_block");
        if (tb != null && !tb.isEmpty()) return tb;
        return getSubParam("on_activate_block", "targetBlock");
    }

    /** 从 triggerOn 中查找 targetBlocks / Look up targetBlocks from triggerOn (on_activate_block sub-key list) */
    public List<String> getTargetBlocks() {
        return getSubParamList("on_activate_block", "targetBlocks");
    }

    /** @return 是否配置了 targetBlock 或 targetBlocks / whether targetBlock or targetBlocks is configured */
    public boolean hasTargetBlock() {
        return getParam("on_activate_block") != null;
    }

    // ============================================================
    //  方块匹配（on_activate_block 触发器使用）
    //  Block Matching (used by on_activate_block trigger)
    // ============================================================

    /**
     * 验证此规则的 targetBlock / targetBlocks 是否匹配被右键点击的方块。
     * Validates whether this rule's targetBlock / targetBlocks match the right-clicked block.
     *
     * <p>规则未配置 targetBlock/targetBlocks 时视为不限制方块（返回 true）。</p>
     * <p>When no targetBlock/targetBlocks are configured, blocks are unrestricted (returns true).</p>
     *
     * @param clickedBlockId 被右键点击的方块注册名 / registry name of the right-clicked block (e.g. "minecraft:dirt")
     * @return true 如果匹配（或无限制）/ if matched (or unrestricted)
     */
    public boolean matchesBlock(String clickedBlockId) {
        // 规则不限制方块 → 通过 / Rule doesn't restrict block → pass
        if (!hasTargetBlock()) return true;
        // 规则限制了但未实际点击 → 不通过 / Rule restricts but no actual click → fail
        if (clickedBlockId == null) return false;

        String tb = getTargetBlock();
        if (tb != null && matchesBlockEntry(tb, clickedBlockId)) return true;

        List<String> tbs = getTargetBlocks();
        if (tbs != null) {
            for (String b : tbs) {
                if (b != null && !b.isEmpty() && matchesBlockEntry(b, clickedBlockId)) return true;
            }
        }
        return false;
    }

    /** 单条 block 条目匹配：精确 ID 或标签 / Single block entry match: exact ID or tag */
    private static boolean matchesBlockEntry(String entry, String clickedBlockId) {
        if (entry.startsWith("#")) {
            return isBlockInTag(clickedBlockId, entry.substring(1));
        }
        return entry.equals(clickedBlockId);
    }

    /** 检查指定方块 ID 是否属于指定方块标签 / Check if a block ID belongs to a block tag */
    private static boolean isBlockInTag(String blockId, String tagId) {
        Identifier tagIdentifier = Identifier.tryParse(tagId);
        if (tagIdentifier == null) return false;
        Identifier blockIdentifier = Identifier.tryParse(blockId);
        if (blockIdentifier == null) return false;

        Block block = BuiltInRegistries.BLOCK.getOptional(blockIdentifier).orElse(null);
        if (block == null) return false;

        var tagKey = TagKey.create(Registries.BLOCK, tagIdentifier);
        return block.builtInRegistryHolder().is(tagKey);
    }

    // ============================================================
    //  运行时缓存（transient，不参与 JSON 序列化）
    //  Runtime Caches (transient, excluded from JSON serialization)
    // ============================================================

    /** 文字颜色 ARGB 缓存，null 表示未解析 / Cached text color ARGB, null = unresolved */
    private transient Integer cachedTextColor;

    /** 背景颜色 ARGB 缓存，null 表示未解析 / Cached bg color ARGB, null = unresolved */
    private transient Integer cachedBgColor;

    /** 位置是否为 JSON 显式指定（false=继承自默认值），供游戏内配置覆盖用 / Whether position was explicitly set in JSON (false=inherited from defaults), for in-game config override */
    public transient boolean positionExplicit;

    /** 物品标签匹配缓存，null 表示未计算（惰性求值）/ Item tag match cache, null = not computed (lazy eval) */
    private transient Boolean tagBasedCache;

    /** 规则唯一标识，格式 / format: {section}:{primaryId}:{textHash8}，加载时计算 / computed at load time */
    private transient String computedId;

    public String getComputedId() { return computedId; }
    public void setComputedId(String id) { this.computedId = id; }

    /** 获取缓存的文字颜色（ARGB），首次调用时解析 / Get cached text color (ARGB), resolved on first call */
    public int getTextColorARGB() {
        if (cachedTextColor == null) {
            cachedTextColor = parseColor(color, 0xFFFFFFFF);
        }
        return cachedTextColor;
    }

    /** 获取缓存的背景颜色（ARGB），首次调用时解析 / Get cached bg color (ARGB), resolved on first call */
    public int getBgColorARGB() {
        if (cachedBgColor == null) {
            cachedBgColor = parseColor(backgroundColor, 0x80000000);
        }
        return cachedBgColor;
    }

    /** 解析十六进制颜色字符串为 ARGB 整数 / Parse hex color string to ARGB int */
    private static int parseColor(String colorStr, int fallback) {
        if (colorStr == null || colorStr.isEmpty()) return fallback;
        try {
            String hex = colorStr.replace("#", "");
            if (hex.length() > 8) return fallback;
            long value = Long.parseLong(hex, 16);
            if (hex.length() <= 6) {
                value |= 0xFF000000L;
            }
            return (int) value;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    // ============================================================
    //  消失控制
    //  Dismiss Control
    // ============================================================

    /**
     * 关闭触发器列表。
     * Dismiss trigger list.
     *
     * <p>指定哪些触发器可以关闭此提示。当这些触发器中的任何一个变为活跃时，
     * 当前提示会立即消失。</p>
     * <p>Specifies which triggers can dismiss this hint. When any of these triggers becomes active,
     * the current hint immediately disappears.</p>
     *
     * <p>特殊语法 / Special syntax：</p>
     * <ul>
     *   <li>{@code "time:milliseconds"} — 经过指定时间后关闭 / dismiss after specified time, e.g. {@code "time:5000"}</li>
     *   <li>{@code "tick:ticks"} — 经过指定刻数后关闭 / dismiss after specified ticks, e.g. {@code "tick:100"}</li>
     * </ul>
     *
     * <p>可用的触发器类型 / Available trigger types：</p>
     * <ul>
     *   <li>{@code "on_use"} [event] — 使用物品时立即关闭 / dismiss immediately on item use</li>
     *   <li>{@code "on_activate_block"} [event] — 手持物品右键点击方块时关闭 / dismiss on right-click block with item</li>
     *   <li>{@code "on_dimension_change"} [event] — 维度切换时立即关闭 / dismiss immediately on dimension change</li>
     *   <li>{@code "on_kill"} [event] — 击杀实体时关闭 / dismiss on entity kill</li>
     * </ul>
     *
     * <p>注意：[continuous] 型触发器（hold_item, on_low_health, on_dimension）
     * 不需要也不应该在 dismissOn 中列出——它们会在条件不再满足时自动关闭。
     * 写在 dismissOn 中会被忽略。</p>
     * <p>Note: [continuous] triggers (hold_item, on_low_health, on_dimension)
     * do not need and should not be listed in dismissOn — they auto-close when conditions are no longer met.
     * Entries are ignored if listed in dismissOn.</p>
     *
     * <h3>JSON 示例 / JSON Example:</h3>
     * <pre>
     * {
     *   "items": ["minecraft:diamond_sword"],
     *   "text": "💡 按 [Shift] + 右键 释放剑气",
     *   "dismissOn": ["on_use", "time:5000"]
     * }
     * </pre>
     *
     * @see #triggerOn
     * @see HudTrigger#getType()
     */
    public List<String> dismissOn;
}
