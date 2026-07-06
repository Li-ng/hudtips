package com.xiaogua.hudtips.client.config;

import java.util.HashMap;
import java.util.Map;

/**
 * 全局 HUD 配置，对应 JSON 配置文件 (hudtips/*.json) 的顶层结构。
 * Global HUD configuration, corresponding to the top-level structure of JSON config files (hudtips/*.json).
 *
 * <p>此类定义了两部分内容：</p>
 * <p>This class defines two parts:</p>
 * <ul>
 *   <li><b>全局显示默认值</b>：所有触发器和规则的兜底默认值</li>
 *   <li><b>Global display defaults</b>: fallback defaults for all triggers and rules</li>
 *   <li><b>触发器配置</b>：按触发器类型分组的配置，每种触发器可有自己的默认值和规则</li>
 *   <li><b>Trigger configuration</b>: configurations grouped by trigger type, each trigger can have its own defaults and rules</li>
 * </ul>
 *
 * <h2>JSON 示例： / JSON Example:</h2>
 * <pre>
 * {
 *   "// === 全局显示默认值 ===": "",
 *   "defaultColor": "#FFFFFF",      // 默认白色文字
 *   "backgroundColor": "#00000000", // 默认全透明背景（#AARRGGBB 格式）
 *   "position": "bottom_left",      // 默认锚点：左下角
 *   "offsetX": 5,                   // 默认 X 偏移（像素）
 *   "offsetY": 5,                   // 默认 Y 偏移（像素）
 *   "textScale": 1.0,               // 默认文字大小（1.0 = 原始大小）
 *   "padding": 5,                   // 默认内边距
 *
 *   "triggers": {
 *     "hold_item": { ... },         // 持有物品触发器
 *     "on_craft": { ... }           // 未来：合成触发器
 *   }
 * }
 * </pre>
 *
 * <h2>支持的锚点位置 (position)： / Supported Anchor Positions (position):</h2>
 * <ul>
 *   <li>{@code bottom_left} - 左下角 / Bottom-left</li>
 *   <li>{@code bottom_center} - 底部居中 / Bottom-center</li>
 *   <li>{@code bottom_right} - 右下角 / Bottom-right</li>
 *   <li>{@code top_left} - 左上角 / Top-left</li>
 *   <li>{@code top_center} - 顶部居中 / Top-center</li>
 *   <li>{@code top_right} - 右上角 / Top-right</li>
 * </ul>
 *
 * <h2>颜色格式： / Color Format:</h2>
 * <ul>
 *   <li>{@code #RRGGBB} - 6 位 RGB，自动不透明（如 {@code #FFAA00}） / 6-digit RGB, automatically opaque (e.g. {@code #FFAA00})</li>
 *   <li>{@code #AARRGGBB} - 8 位 ARGB，含透明度（如 {@code #80000000} = 半透明黑） / 8-digit ARGB with alpha (e.g. {@code #80000000} = semi-transparent black)</li>
 * </ul>
 *
 * @see TriggerConfig
 * @see HintRule
 */
public class HudConfig {
    // ============================================================
    //  全局显示默认值
    //  Global Display Defaults
    //  这些值作为所有触发器和规则的兜底默认值
    //  These values serve as fallback defaults for all triggers and rules
    // ============================================================

    /**
     * 全局默认文字颜色（十六进制）。示例：{@code "#FFFFFF"} = 白色。
     * Global default text color (hex). Example: {@code "#FFFFFF"} = white.
     */
    public String defaultColor = "#FFFFFF";

    /**
     * 全局默认背景颜色。默认全透明。示例：{@code "#80000000"} = 半透明黑。
     * Global default background color. Fully transparent by default. Example: {@code "#80000000"} = semi-transparent black.
     */
    public String backgroundColor = "#00000000";

    /**
     * 全局默认锚点位置。参见类文档中的锚点列表。
     * Global default anchor position. See the anchor list in the class documentation.
     */
    public String position = DEFAULT_POSITION;

    /**
     * 全局默认 X 偏移（像素），相对于锚点。
     * Global default X offset (pixels), relative to the anchor.
     */
    public int offsetX = 5;

    /**
     * 全局默认 Y 偏移（像素），相对于锚点。
     * Global default Y offset (pixels), relative to the anchor.
     */
    public int offsetY = 5;

    /**
     * 全局默认文字缩放倍数。1.0 = 原始大小，0.5 = 一半大小，2.0 = 两倍大小。
     * Global default text scale multiplier. 1.0 = original size, 0.5 = half size, 2.0 = double size.
     */
    public float textScale = 1.0f;

    /**
     * 全局默认内边距（像素）。
     * Global default padding (pixels).
     */
    public int padding = 5;

    // ============================================================
    //  触发器配置
    //  Trigger Configuration
    // ============================================================

    /**
     * 按触发器类型分组的配置映射。
     * Configuration map grouped by trigger type.
     *
     * <p>键为触发器类型名称（如 {@code "hold_item"}），值为该触发器的配置。</p>
     * <p>The key is the trigger type name (e.g. {@code "hold_item"}), and the value is that trigger's configuration.</p>
     *
     * <h3>当前支持的触发器类型： / Currently Supported Trigger Types:</h3>
     * <ul>
     *   <li>{@code hold_item} [continuous] — 手持物品时 / When holding an item</li>
     *   <li>{@code on_use} [event] — 开始使用物品时 / When starting to use an item</li>
     *   <li>{@code on_low_health} [continuous] — 血量低于阈值时 / When health drops below threshold</li>
     *   <li>{@code on_dimension} [continuous] — 处于特定维度时（AND 组合用） / When in a specific dimension (for AND combinations)</li>
     *   <li>{@code on_dimension_change} [event] — 切换维度时 / When changing dimensions</li>
     *   <li>{@code on_activate_block} [event] — 手持物品右键点击方块时 / When right-clicking a block while holding an item</li>
     *   <li>{@code on_kill} [event] — 本地玩家击杀实体时 / When the local player kills an entity</li>
     *   <li>{@code on_look_entity} [continuous] — 指向实体时 / When looking at an entity</li>
     *   <li>{@code on_look_block} [continuous] — 指向方块时 / When looking at a block</li>
     *   <li>{@code on_key_press} [event] — 按下指定按键时（通过翻译键名如 key.sneak 匹配） / When pressing a specified key (matched by translation key name, e.g. key.sneak)</li>
     * </ul>
     */
    public Map<String, TriggerConfig> triggers = new HashMap<>();

    // ============================================================
    //  触发器类型常量
    //  Trigger Type Constants
    // ============================================================

    /**
     * 持有物品触发器：当玩家主手持有指定物品时显示提示。
     * Hold item trigger: displays a hint when the player holds a specified item in the main hand.
     */
    public static final String TRIGGER_HOLD_ITEM = "hold_item";

    /**
     * 使用物品触发器：当玩家开始使用物品（吃食物、拉弓、举盾等）时显示提示。
     * Use item trigger: displays a hint when the player starts using an item (eating food, drawing a bow, raising a shield, etc.).
     */
    public static final String TRIGGER_ON_USE = "on_use";

    /**
     * 低血量触发器：当玩家血量低于阈值时显示提示。
     * Low health trigger: displays a hint when the player's health drops below the threshold.
     */
    public static final String TRIGGER_ON_LOW_HEALTH = "on_low_health";

    /**
     * 维度切换触发器：当玩家切换维度时显示对应维度的提示（事件型，只触发一次）。
     * Dimension change trigger: displays a hint for the corresponding dimension when the player changes dimensions (event type, triggers only once).
     */
    public static final String TRIGGER_ON_DIMENSION_CHANGE = "on_dimension_change";

    /**
     * 维度状态触发器：持续报告当前所在维度，用于 AND 组合条件（状态型）。
     * Dimension state trigger: continuously reports the current dimension, used for AND combination conditions (state type).
     */
    public static final String TRIGGER_ON_DIMENSION = "on_dimension";

    /**
     * 激活方块触发器：当玩家手持特定物品右键点击特定方块时触发（事件型）。
     * Activate block trigger: fires when the player right-clicks a specific block while holding a specific item (event type).
     */
    public static final String TRIGGER_ON_ACTIVATE_BLOCK = "on_activate_block";

    /**
     * 击杀实体触发器：当本地玩家击杀实体时触发（事件型）。
     * Kill entity trigger: fires when the local player kills an entity (event type).
     */
    public static final String TRIGGER_ON_KILL = "on_kill";

    /**
     * 指向实体触发器：当玩家准星指向实体时持续激活（continuous 型）。
     * Look at entity trigger: continuously active when the player's crosshair points at an entity (continuous type).
     */
    public static final String TRIGGER_ON_LOOK_ENTITY = "on_look_entity";

    /**
     * 指向方块触发器：当玩家准星指向方块时持续激活（continuous 型）。
     * Look at block trigger: continuously active when the player's crosshair points at a block (continuous type).
     */
    public static final String TRIGGER_ON_LOOK_BLOCK = "on_look_block";

    /**
     * 按键按下触发器：当玩家按下指定按键时触发（event 型，通过翻译键名匹配）。
     * Key press trigger: fires when the player presses a specified key (event type, matched by translation key name).
     */
    public static final String TRIGGER_ON_KEY_PRESS = "on_key_press";

    // ============================================================
    //  规则语义常量
    //  Rule Semantic Constants
    // ============================================================

    /**
     * 触发器组合模式：任一激活即触发（默认）。
     * Trigger combination mode: triggers when any is active (default).
     */
    public static final String TRIGGER_MODE_ANY = "any";

    /**
     * 触发器组合模式：全部激活才触发。
     * Trigger combination mode: triggers only when all are active.
     */
    public static final String TRIGGER_MODE_ALL = "all";

    /**
     * 默认锚点位置。
     * Default anchor position.
     */
    public static final String DEFAULT_POSITION = "bottom_left";
}
