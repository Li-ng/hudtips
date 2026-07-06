package com.xiaogua.hudtips.client.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 触发器类型配置。
 * Trigger type configuration.
 *
 * <p>每种触发器类型（如 {@code hold_item}）可以有自己的默认值和规则列表。
 * 这些默认值会覆盖全局默认值，但会被单条规则中的设置覆盖。</p>
 * <p>Each trigger type (e.g. {@code hold_item}) can have its own defaults and rule list.
 * These defaults override global defaults, but are overridden by per-rule settings.</p>
 *
 * <h2>三层默认值继承 / Three-Layer Default Inheritance:</h2>
 * <pre>
 * 规则字段 / Rule field → 触发器默认值 / Trigger default → 全局默认值 / Global default
 * </pre>
 * <p>例如：如果规则没有设置 {@code color}，则使用触发器的 {@code defaultColor}；
 * 如果触发器也没有设置，则使用全局的 {@code defaultColor}。</p>
 * <p>E.g.: If a rule doesn't set {@code color}, the trigger's {@code defaultColor} is used;
 * if the trigger also doesn't set it, the global {@code defaultColor} applies.</p>
 *
 * <h2>JSON 示例（hold_item 触发器）/ JSON Example (hold_item trigger):</h2>
 * <pre>
 * "triggers": {
 *   "hold_item": {
 *     "defaultColor": "#55FF55",    // 该触发器下所有规则默认绿色 / All rules under this trigger default to green
 *     "rules": [
 *       { "items": ["minecraft:diamond_sword"], "text": "这是一把钻石剑" },
 *       { "items": ["minecraft:bow"], "text": "这是弓", "color": "#FF0000" }
 *     ]
 *   }
 * }
 * </pre>
 *
 * <h2>未来触发器示例（on_craft）/ Future Trigger Example (on_craft):</h2>
 * <pre>
 * "triggers": {
 *   "on_craft": {
 *     "position": "top_center",     // 合成提示显示在顶部居中 / Craft hints show top-center
 *     "rules": [
 *       { "items": ["minecraft:diamond_pickaxe"], "text": "你合成了钻石镐！" }
 *     ]
 *   }
 * }
 * </pre>
 *
 * @see HudConfig
 * @see HintRule
 */
public class TriggerConfig {
    /**
     * 该触发器的默认文字颜色。
     * Default text color for this trigger.
     * 为 null 时使用全局 defaultColor。 / null → uses global defaultColor.
     * 示例 / Example: {@code "#FFAA00"} = 金色 / gold
     */
    public String defaultColor;

    /**
     * 该触发器的默认背景颜色。
     * Default background color for this trigger.
     * 为 null 时使用全局 backgroundColor。 / null → uses global backgroundColor.
     * 示例 / Example: {@code "#80000000"} = 半透明黑 / semi-transparent black, {@code "#00000000"} = 全透明 / fully transparent
     */
    public String backgroundColor;

    /**
     * 该触发器的默认锚点位置。
     * Default anchor position for this trigger.
     * 为 null 时使用全局 position。 / null → uses global position.
     * 可选值 / Valid values: bottom_left, bottom_center, bottom_right, top_left, top_center, top_right
     */
    public String position;

    /**
     * 该触发器的默认 X 偏移（像素）。
     * Default X offset in pixels for this trigger.
     * 为 null 时使用全局 offsetX。 / null → uses global offsetX.
     */
    public Integer offsetX;

    /**
     * 该触发器的默认 Y 偏移（像素）。
     * Default Y offset in pixels for this trigger.
     * 为 null 时使用全局 offsetY。 / null → uses global offsetY.
     */
    public Integer offsetY;

    /**
     * 该触发器的默认文字缩放。
     * Default text scale for this trigger.
     * 为 null 时使用全局 textScale。 / null → uses global textScale.
     * 示例 / Example: 0.5 = 一半大小 / half size, 1.0 = 原始大小 / original size, 2.0 = 两倍大小 / double size
     */
    public Float textScale;

    /**
     * 触发器特有的自定义设置。
     * Trigger-specific custom settings.
     * 不同触发器可从中读取特有的配置项。 / Different triggers read their specific settings from here.
     *
     * <p>例如 / For example:</p>
     * <ul>
     *   <li>{@code on_low_health} 触发器读取 {@code "healthThreshold"}（血量阈值比例，默认 0.3）
     *       / trigger reads {@code "healthThreshold"} (health threshold ratio, default 0.3)</li>
     * </ul>
     *
     * <h3>JSON 示例 / JSON Example:</h3>
     * <pre>
     * "on_low_health": {
     *   "settings": { "healthThreshold": 0.3 },
     *   "rules": [ ... ]
     * }
     * </pre>
     */
    public Map<String, Object> settings;

    /**
     * 该触发器类型下的规则列表。
     * Rule list under this trigger type.
     * 每条规则定义了一个物品（或多个物品）对应的提示文本。
     * Each rule defines hint text for one or more items.
     */
    public List<HintRule> rules = new ArrayList<>();
}
