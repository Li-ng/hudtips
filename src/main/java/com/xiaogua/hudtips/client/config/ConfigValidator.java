package com.xiaogua.hudtips.client.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.xiaogua.hudtips.HUDTips;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 配置校验器。
 * Configuration validator.
 *
 * <p>在 JSON 配置加载完成后执行校验，向日志输出 WARN 级别的告警：</p>
 * <p>Validates the configuration after JSON loading completes, outputting WARN-level alerts to the log:</p>
 * <ul>
 *   <li>无效的物品 ID / 物品标签 / Invalid item IDs / item tags</li>
 *   <li>无效的方块 ID / 方块标签 / Invalid block IDs / block tags</li>
 *   <li>无效的维度 ID / Invalid dimension IDs</li>
 *   <li>JSON 中拼写错误的字段名（未知字段） / Misspelled field names in JSON (unknown fields)</li>
 *   <li>字段值不合法（如 position 不在允许值列表中） / Invalid field values (e.g. position not in the allowed values list)</li>
 * </ul>
 *
 * <p>校验不会阻止配置加载，仅输出警告日志，方便整合包作者排查问题。</p>
 * <p>Validation does not block configuration loading; it only outputs warning logs to help modpack authors troubleshoot issues.</p>
 */
public class ConfigValidator {

    private ConfigValidator() {}

    // ============================================================
    //  已知字段名（用于检测拼写错误）
    //  Known Field Names (for detecting misspellings)
    // ============================================================

    /**
     * HudConfig 顶层已知字段。
     * Known top-level fields of HudConfig.
     */
    private static final Set<String> KNOWN_GLOBAL_FIELDS = Set.of(
        "defaultColor", "backgroundColor", "position", "offsetX", "offsetY",
        "textScale", "padding", "triggers"
    );

    /**
     * TriggerConfig 已知字段。
     * Known fields of TriggerConfig.
     */
    private static final Set<String> KNOWN_TRIGGER_FIELDS = Set.of(
        "defaultColor", "backgroundColor", "position", "offsetX", "offsetY",
        "textScale", "settings", "rules"
    );

    /**
     * HintRule 已知字段。
     * Known fields of HintRule.
     */
    private static final Set<String> KNOWN_RULE_FIELDS = Set.of(
        "item", "items", "text", "texts", "color", "backgroundColor",
        "position", "offsetX", "offsetY", "textScale",
        "triggerOn", "triggerOnMode", "priority",
        "type", "dismissOn"
    );

    /**
     * 合法的锚点位置值。
     * Valid anchor position values.
     */
    private static final Set<String> VALID_POSITIONS = Set.of(
        "bottom_left", "bottom_center", "bottom_right",
        "top_left", "top_center", "top_right"
    );

    /**
     * 合法的触发器类型名。
     * Valid trigger type names.
     */
    private static final Set<String> VALID_TRIGGER_TYPES = Set.of(
        "hold_item", "on_use", "on_low_health", "on_dimension",
        "on_dimension_change", "on_activate_block", "on_kill",
        "on_look_entity", "on_look_block", "on_key_press"
    );

    /**
     * 合法的 triggerOnMode 值。
     * Valid triggerOnMode values.
     */
    private static final Set<String> VALID_TRIGGER_MODES = Set.of("any", "all");

    /**
     * 合法的 type 值。
     * Valid type values.
     */
    private static final Set<String> VALID_RULE_TYPES = Set.of("guide");

    /**
     * 已知的原版维度 ID（用于提示未知维度）。
     * Known vanilla dimension IDs (used to flag unknown dimensions).
     */
    private static final Set<String> KNOWN_VANILLA_DIMENSIONS = Set.of(
        "minecraft:overworld", "minecraft:the_nether", "minecraft:the_end",
        "minecraft:overworld_caves"
    );

    // ============================================================
    //  入口
    //  Entry Point
    // ============================================================

    /**
     * 对已加载的配置进行全面校验。
     * Performs comprehensive validation on the loaded configuration.
     *
     * @param config  反序列化后的 HudConfig / The deserialized HudConfig
     * @param rawJson 合并后的原始 JsonObject（用于检测未知字段） / The merged raw JsonObject (used to detect unknown fields)
     */
    public static void validate(HudConfig config, JsonObject rawJson) {
        if (config == null) return;

        int warnCountBefore = warnCount;
        warnCount = 0;

        // 第一遍：JSON 字段名校验（在原始 JSON 上逐层检查）
        // Pass 1: JSON field name validation (checked layer by layer on the raw JSON)
        if (rawJson != null) {
            validateJsonFields(rawJson);
        }

        // 第二遍：规则字段值校验（在反序列化后的对象上检查）
        // Pass 2: Rule field value validation (checked on the deserialized objects)
        if (config.triggers != null) {
            validateTriggerConfigs(config.triggers);
        }

        if (warnCount > 0) {
            HUDTips.LOGGER.warn("Config validation found {} issue(s). See warnings above for details.", warnCount);
        }
    }

    // ============================================================
    //  计数
    //  Counter
    // ============================================================

    private static int warnCount = 0;

    private static void warn(String format, Object... args) {
        HUDTips.LOGGER.warn("[ConfigValidator] " + format, args);
        warnCount++;
    }

    // ============================================================
    //  第一遍：JSON 字段名校验
    //  Pass 1: JSON Field Name Validation
    // ============================================================

    /**
     * 逐层遍历原始 JSON，检测未知字段名。
     * Traverses the raw JSON layer by layer to detect unknown field names.
     */
    private static void validateJsonFields(JsonObject root) {
        // ── 全局层 ──
        // ── Global layer ──
        for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
            String key = entry.getKey();
            if (isCommentKey(key)) continue;

            if (!KNOWN_GLOBAL_FIELDS.contains(key)) {
                warn("未知的全局字段: \"{}\"（是否拼写错误？）", key);
            }
        }

        // ── 触发器层 ──
        // ── Trigger layer ──
        JsonElement triggersElem = root.get("triggers");
        if (triggersElem == null || !triggersElem.isJsonObject()) return;

        JsonObject triggersObj = triggersElem.getAsJsonObject();
        for (Map.Entry<String, JsonElement> triggerEntry : triggersObj.entrySet()) {
            String triggerType = triggerEntry.getKey();
            if (isCommentKey(triggerType)) continue;

            JsonElement triggerVal = triggerEntry.getValue();
            if (!triggerVal.isJsonObject()) continue;

            JsonObject triggerObj = triggerVal.getAsJsonObject();
            validateTriggerJsonFields(triggerType, triggerObj);
        }
    }

    /**
     * 校验单个触发器配置的 JSON 字段。
     * Validates JSON fields of a single trigger configuration.
     */
    private static void validateTriggerJsonFields(String triggerType, JsonObject triggerObj) {
        for (Map.Entry<String, JsonElement> entry : triggerObj.entrySet()) {
            String key = entry.getKey();
            if (isCommentKey(key)) continue;

            if ("rules".equals(key)) {
                // 深入规则数组
                // Drill into the rules array
                JsonElement rulesElem = entry.getValue();
                if (rulesElem.isJsonArray()) {
                    int idx = 0;
                    for (JsonElement ruleElem : rulesElem.getAsJsonArray()) {
                        if (ruleElem.isJsonObject()) {
                            validateRuleJsonFields(triggerType, idx, ruleElem.getAsJsonObject());
                        }
                        idx++;
                    }
                }
            } else if ("settings".equals(key)) {
                // settings 内容由各触发器自定义，跳过字段名校验
                // settings content is custom for each trigger, skip field name validation
            } else if (!KNOWN_TRIGGER_FIELDS.contains(key)) {
                warn("[{}] 未知的触发器字段: \"{}\"（是否拼写错误？）", triggerType, key);
            }
        }
    }

    /**
     * 校验单条规则的所有 JSON 字段。
     * Validates all JSON fields of a single rule.
     */
    private static void validateRuleJsonFields(String triggerType, int ruleIdx, JsonObject ruleObj) {
        String loc = ruleLoc(triggerType, ruleIdx);

        for (Map.Entry<String, JsonElement> entry : ruleObj.entrySet()) {
            String key = entry.getKey();
            if (isCommentKey(key)) continue;

            if (!KNOWN_RULE_FIELDS.contains(key)) {
                warn("{} 未知的规则字段: \"{}\"（是否拼写错误？）", loc, key);
            }
        }

        // 额外检查 triggerOn 内部的触发器类型名
        // Additional check: trigger type names inside triggerOn
        JsonElement toe = ruleObj.get("triggerOn");
        if (toe != null && toe.isJsonObject()) {
            for (Map.Entry<String, JsonElement> te : toe.getAsJsonObject().entrySet()) {
                String tType = te.getKey();
                if (!VALID_TRIGGER_TYPES.contains(tType)) {
                    warn("{} triggerOn 中的触发器类型 \"{}\" 不在已知列表中，可能无效",
                        loc, tType);
                }
            }
        }

        // 检查 dismissOn 条目格式
        // Check dismissOn entry formats
        JsonElement doe = ruleObj.get("dismissOn");
        if (doe != null && doe.isJsonArray()) {
            for (JsonElement de : doe.getAsJsonArray()) {
                if (de.isJsonPrimitive()) {
                    String d = de.getAsString();
                    validateDismissOnEntry(loc, d);
                }
            }
        }
    }

    /**
     * 校验单条 dismissOn 条目的格式。
     * Validates the format of a single dismissOn entry.
     */
    private static void validateDismissOnEntry(String loc, String entry) {
        if (entry.startsWith("time:") || entry.startsWith("tick:")) {
            // 解析时间/刻数值
            // Parse time/tick numeric value
            String numPart = entry.substring(entry.indexOf(':') + 1);
            try {
                long val = Long.parseLong(numPart);
                if (val <= 0) {
                    warn("{} dismissOn \"{}\" 的值必须 > 0", loc, entry);
                }
            } catch (NumberFormatException e) {
                warn("{} dismissOn \"{}\" 中的数值无法解析", loc, entry);
            }
        } else if (!VALID_TRIGGER_TYPES.contains(entry)) {
            warn("{} dismissOn 中的 \"{}\" 不是已知的触发器类型（也不以 time:/tick: 开头）",
                loc, entry);
        }
    }

    // ============================================================
    //  第二遍：规则字段值校验（物品 ID、方块 ID、维度 ID 等）
    //  Pass 2: Rule Field Value Validation (item IDs, block IDs, dimension IDs, etc.)
    // ============================================================

    private static void validateTriggerConfigs(Map<String, TriggerConfig> triggers) {
        for (Map.Entry<String, TriggerConfig> entry : triggers.entrySet()) {
            String triggerType = entry.getKey();
            TriggerConfig tc = entry.getValue();

            // 校验触发器级字段值
            // Validate trigger-level field values
            validateTriggerFieldValues(triggerType, tc);

            // 校验每条规则
            // Validate each rule
            if (tc.rules != null) {
                for (int i = 0; i < tc.rules.size(); i++) {
                    HintRule rule = tc.rules.get(i);
                    validateRuleFieldValues(triggerType, i, rule);
                }
            }
        }
    }

    /**
     * 校验触发器级别的字段值。
     * Validates trigger-level field values.
     */
    private static void validateTriggerFieldValues(String triggerType, TriggerConfig tc) {
        if (tc.position != null && !VALID_POSITIONS.contains(tc.position)) {
            warn("[{}] position 值 \"{}\" 无效，合法值: {}",
                triggerType, tc.position, VALID_POSITIONS);
        }
        if (tc.defaultColor != null) {
            validateColorFormat("[" + triggerType + "] defaultColor", tc.defaultColor);
        }
        if (tc.backgroundColor != null) {
            validateColorFormat("[" + triggerType + "] backgroundColor", tc.backgroundColor);
        }
    }

    /**
     * 校验单条规则的字段值。
     * Validates field values of a single rule.
     */
    private static void validateRuleFieldValues(String triggerType, int ruleIdx, HintRule rule) {
        String loc = ruleLoc(triggerType, ruleIdx);

        // ── 物品 ID 校验 ──
        // ── Item ID validation ──
        if (rule.items != null) {
            for (int i = 0; i < rule.items.size(); i++) {
                String itemStr = rule.items.get(i);
                if (itemStr != null && !itemStr.isEmpty()) {
                    validateItemRef(loc, "items[" + i + "]", itemStr);
                }
            }
        }

        // ── 维度 ID 校验 ──
        // ── Dimension ID validation ──
        String dim = rule.getDimension();
        if (dim != null && !dim.isEmpty()) {
            validateDimensionId(loc, dim);
        }

        // ── 方块 ID 校验（on_activate_block 的 targetBlock / targetBlocks） ──
        // ── Block ID validation (targetBlock / targetBlocks for on_activate_block) ──
        String tb = rule.getTargetBlock();
        if (tb != null && !tb.isEmpty() && !tb.startsWith("#")) {
            validateBlockRef(loc, "targetBlock", tb);
        }
        List<String> tbs = rule.getTargetBlocks();
        if (tbs != null) {
            for (int i = 0; i < tbs.size(); i++) {
                String blockStr = tbs.get(i);
                if (blockStr != null && !blockStr.isEmpty() && !blockStr.startsWith("#")) {
                    validateBlockRef(loc, "targetBlocks[" + i + "]", blockStr);
                } else if (blockStr != null && blockStr.startsWith("#")) {
                    validateBlockTagRef(loc, "targetBlocks[" + i + "]", blockStr);
                }
            }
        }

        // ── on_look_entity 实体 ID 校验 ──
        // ── on_look_entity entity ID validation ──
        String lookEntity = rule.getLookEntity();
        if (lookEntity != null && !lookEntity.isEmpty() && !"true".equals(lookEntity)) {
            validateEntityRef(loc, "on_look_entity", lookEntity);
        }

        // ── on_look_block 方块 ID 校验 ──
        // ── on_look_block block ID validation ──
        String lb = rule.getLookBlock();
        if (lb != null && !lb.isEmpty() && !lb.startsWith("#") && !"true".equals(lb)) {
            validateBlockRef(loc, "on_look_block", lb);
        }
        List<String> lbList = rule.getLookBlocks();
        if (lbList != null) {
            for (int i = 0; i < lbList.size(); i++) {
                String blockStr = lbList.get(i);
                if (blockStr != null && !blockStr.isEmpty() && !blockStr.startsWith("#")) {
                    validateBlockRef(loc, "on_look_block.targetBlocks[" + i + "]", blockStr);
                } else if (blockStr != null && blockStr.startsWith("#")) {
                    validateBlockTagRef(loc, "on_look_block.targetBlocks[" + i + "]", blockStr);
                }
            }
        }

        // ── triggerOnMode 值校验 ──
        // ── triggerOnMode value validation ──
        if (rule.triggerOnMode != null && !VALID_TRIGGER_MODES.contains(rule.triggerOnMode)) {
            warn("{} triggerOnMode 值 \"{}\" 无效，合法值: any, all",
                loc, rule.triggerOnMode);
        }

        // ── position 值校验 ──
        // ── position value validation ──
        if (rule.position != null && !VALID_POSITIONS.contains(rule.position)) {
            warn("{} position 值 \"{}\" 无效，合法值: {}",
                loc, rule.position, VALID_POSITIONS);
        }

        // ── type 值校验 ──
        // ── type value validation ──
        if (rule.type != null && !VALID_RULE_TYPES.contains(rule.type)) {
            warn("{} type 值 \"{}\" 无效，合法值: guide（或省略）",
                loc, rule.type);
        }

        // ── 颜色格式校验 ──
        // ── Color format validation ──
        if (rule.color != null) {
            validateColorFormat(loc + " color", rule.color);
        }
        if (rule.backgroundColor != null) {
            validateColorFormat(loc + " backgroundColor", rule.backgroundColor);
        }
    }

    // ============================================================
    //  物品/方块/维度 引用校验
    //  Item/Block/Dimension Reference Validation
    // ============================================================

    /**
     * 校验物品引用（具体 ID 或标签）。
     * Validates an item reference (specific ID or tag).
     * <ul>
     *   <li>{@code #namespace:tag} → 检查标签是否存在 / Check if the tag exists</li>
     *   <li>{@code namespace:id} → 检查物品是否注册 / Check if the item is registered</li>
     * </ul>
     */
    private static void validateItemRef(String loc, String field, String itemStr) {
        if (itemStr.startsWith("#")) {
            validateItemTagRef(loc, field, itemStr);
            return;
        }

        Identifier id = Identifier.tryParse(itemStr);
        if (id == null) {
            warn("{} {} 的值 \"{}\" 不是合法的 ResourceLocation 格式", loc, field, itemStr);
            return;
        }

        if (!BuiltInRegistries.ITEM.containsKey(id)) {
            warn("{} {} 引用的物品 \"{}\" 不存在于物品注册表中（可能是模组未加载或拼写错误）",
                loc, field, itemStr);
        }
    }

    /**
     * 校验物品标签引用。检查标签是否存在于注册表中。
     * Validates an item tag reference. Checks whether the tag exists in the registry.
     */
    private static void validateItemTagRef(String loc, String field, String tagStr) {
        String tagId = tagStr.startsWith("#") ? tagStr.substring(1) : tagStr;
        Identifier id = Identifier.tryParse(tagId);
        if (id == null) {
            warn("{} {} 的标签 \"{}\" 不是合法的 ResourceLocation 格式", loc, field, tagStr);
            return;
        }

        var tagKey = TagKey.create(Registries.ITEM, id);
        var tagValues = BuiltInRegistries.ITEM.getTagOrEmpty(tagKey);
        if (!tagValues.iterator().hasNext()) {
            warn("{} {} 引用的物品标签 \"{}\" 不存在或为空", loc, field, tagStr);
        }
    }

    /**
     * 校验实体类型引用（具体 ID）。
     * Validates an entity type reference (specific ID).
     *
     * <p>实体类型来自 {@code BuiltInRegistries.ENTITY_TYPE}，
     * 仅校验 ID 是否存在于注册表中。</p>
     * <p>Entity types come from {@code BuiltInRegistries.ENTITY_TYPE};
     * only validates whether the ID exists in the registry.</p>
     */
    private static void validateEntityRef(String loc, String field, String entityStr) {
        Identifier id = Identifier.tryParse(entityStr);
        if (id == null) {
            warn("{} {} 的值 \"{}\" 不是合法的 ResourceLocation 格式", loc, field, entityStr);
            return;
        }

        if (!BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
            warn("{} {} 引用的实体类型 \"{}\" 不存在于实体注册表中（可能是模组未加载或拼写错误）",
                loc, field, entityStr);
        }
    }

    /**
     * 校验方块引用（具体 ID）。
     * Validates a block reference (specific ID).
     */
    private static void validateBlockRef(String loc, String field, String blockStr) {
        Identifier id = Identifier.tryParse(blockStr);
        if (id == null) {
            warn("{} {} 的值 \"{}\" 不是合法的 ResourceLocation 格式", loc, field, blockStr);
            return;
        }

        if (!BuiltInRegistries.BLOCK.containsKey(id)) {
            warn("{} {} 引用的方块 \"{}\" 不存在于方块注册表中（可能是模组未加载或拼写错误）",
                loc, field, blockStr);
        }
    }

    /**
     * 校验方块标签引用。
     * Validates a block tag reference.
     */
    private static void validateBlockTagRef(String loc, String field, String tagStr) {
        String tagId = tagStr.startsWith("#") ? tagStr.substring(1) : tagStr;
        Identifier id = Identifier.tryParse(tagId);
        if (id == null) {
            warn("{} {} 的标签 \"{}\" 不是合法的 ResourceLocation 格式", loc, field, tagStr);
            return;
        }

        var tagKey = TagKey.create(Registries.BLOCK, id);
        var tagValues = BuiltInRegistries.BLOCK.getTagOrEmpty(tagKey);
        if (!tagValues.iterator().hasNext()) {
            warn("{} {} 引用的方块标签 \"{}\" 不存在或为空", loc, field, tagStr);
        }
    }

    /**
     * 校验维度 ID。
     * Validates a dimension ID.
     *
     * <p>由于维度 ID 并非全部注册在静态注册表中（自定义维度来自数据包），
     * 这里只校验格式 + 对非原版维度给出温和提示。</p>
     * <p>Since not all dimension IDs are registered in static registries (custom dimensions come from datapacks),
     * this only validates the format + gives a gentle hint for non-vanilla dimensions.</p>
     */
    private static void validateDimensionId(String loc, String dimId) {
        Identifier id = Identifier.tryParse(dimId);
        if (id == null) {
            warn("{} 的维度 ID \"{}\" 不是合法的 ResourceLocation 格式", loc, dimId);
            return;
        }

        // 非原版维度 → 温和提示（不一定是错误，可能是数据包/模组维度）
        // Non-vanilla dimension → gentle hint (not necessarily an error, could be a datapack/mod dimension)
        if (!KNOWN_VANILLA_DIMENSIONS.contains(dimId)) {
            HUDTips.LOGGER.debug("[ConfigValidator] {} 引用的维度 \"{}\" 不在原版维度列表中，" +
                "如果来自数据包或模组则正常", loc, dimId);
        }
    }

    // ============================================================
    //  颜色格式校验
    //  Color Format Validation
    // ============================================================

    /**
     * 校验颜色字符串格式（#RRGGBB 或 #AARRGGBB）。
     * Validates color string format (#RRGGBB or #AARRGGBB).
     */
    private static void validateColorFormat(String loc, String colorStr) {
        if (colorStr == null || colorStr.isEmpty()) return;

        if (!colorStr.startsWith("#")) {
            warn("{} 颜色值 \"{}\" 缺少 # 前缀", loc, colorStr);
            return;
        }

        String hex = colorStr.substring(1);
        if (hex.length() != 6 && hex.length() != 8) {
            warn("{} 颜色值 \"{}\" 长度不对，应为 #RRGGBB (6位) 或 #AARRGGBB (8位)", loc, colorStr);
            return;
        }

        try {
            Long.parseLong(hex, 16);
        } catch (NumberFormatException e) {
            warn("{} 颜色值 \"{}\" 包含非法十六进制字符", loc, colorStr);
        }
    }

    // ============================================================
    //  工具方法
    //  Utility Methods
    // ============================================================

    /**
     * 生成规则定位字符串，如 "[hold_item]#2"。
     * Generates a rule location string, e.g. "[hold_item]#2".
     */
    private static String ruleLoc(String triggerType, int ruleIdx) {
        return "[" + triggerType + "]#" + ruleIdx;
    }

    /**
     * 判断是否为注释键（以 // 开头）。
     * Determines whether a key is a comment key (starts with //).
     */
    private static boolean isCommentKey(String key) {
        return key.startsWith("//");
    }
}
