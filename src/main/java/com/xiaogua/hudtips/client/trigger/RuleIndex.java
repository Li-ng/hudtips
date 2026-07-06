package com.xiaogua.hudtips.client.trigger;

import com.xiaogua.hudtips.HUDTips;
import com.xiaogua.hudtips.client.KeyMappingLookup;
import com.xiaogua.hudtips.client.config.ConfigLoader;
import com.xiaogua.hudtips.client.config.HintRule;
import com.xiaogua.hudtips.client.config.HudConfig;
import com.xiaogua.hudtips.client.config.TriggerConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 规则索引管理器。
 * Rule index manager.
 *
 * <p>负责从 JSON 配置中加载所有规则，建立 O(1) 查找索引（物品 → 规则、字符串 → 规则），
 * 并提供候选规则查询。与 {@link TriggerManager} 协作：</p>
 * <p>Loads all rules from JSON configs, builds O(1) lookup indexes (item → rules, string → rules),
 * and provides candidate rule queries. Collaborates with {@link TriggerManager}:</p>
 * <ul>
 *   <li>TriggerManager 负责触发器注册和主循环 / TriggerManager handles trigger registration and main loop</li>
 *   <li>RuleIndex 负责规则的加载、索引、候选查找 / RuleIndex handles rule loading, indexing, and candidate lookup</li>
 * </ul>
 *
 * <h2>索引结构 / Index Structure</h2>
 * <table border="1">
 *   <tr><th>索引 / Index</th><th>Key</th><th>Value</th><th>使用者 / Used By</th></tr>
 *   <tr><td>{@code itemToRulesMap}</td><td>{@code Item}</td>
 *       <td>{@code List<HintRule>}</td>
 *       <td>hold_item / on_use / on_activate_block</td></tr>
 *   <tr><td>{@code stringToRulesMap}</td><td>{@code String}</td>
 *       <td>{@code List<HintRule>}</td>
 *       <td>on_low_health / on_dimension / on_dimension_change</td></tr>
 * </table>
 *
 * @see TriggerManager
 * @see HintRule
 */
public class RuleIndex {

    /** 物品 → 候选规则的快速索引 / Fast index: item → candidate rules */
    private final Map<Item, List<HintRule>> itemToRulesMap = new HashMap<>();

    /** 字符串标识 → 候选规则的快速索引（维度 ID、"on_low_health" 等） / Fast index: string identifier → candidate rules (dimension ID, "on_low_health", etc.) */
    private final Map<String, List<HintRule>> stringToRulesMap = new HashMap<>();

    /** 所有规则的完整列表 / Complete list of all rules */
    private final List<HintRule> allRules = new ArrayList<>();

    /** AND 模式规则子集（triggerOnMode="all"），避免每 tick 扫描全部规则 / AND-mode rule subset (triggerOnMode="all"), avoids scanning all rules each tick */
    private final List<HintRule> andModeRules = new ArrayList<>();

    // ============================================================
    //  加载与索引构建（由 TriggerManager.initTriggers() 调用）
    //  Loading and Index Building (called by TriggerManager.initTriggers())
    // ============================================================

    /**
     * 从已注册的触发器中加载所有规则，建立索引。
     * Load all rules from registered triggers and build indexes.
     *
     * <p>对每条规则：应用三层默认值 → 推断 triggerOn → 展开多文本 → 建立索引。</p>
     * <p>For each rule: apply three-layer defaults → infer triggerOn → expand multi-text → build index.</p>
     *
     * @param globalConfig 全局配置 / Global config
     * @param triggers     已注册的触发器列表 / List of registered triggers
     */
    public void loadAllRules(HudConfig globalConfig, List<HudTrigger> triggers) {
        clear();

        // 跟踪已分配的 computedId，检测重复 / Track assigned computedIds to detect duplicates
        java.util.Set<String> seenIds = new java.util.HashSet<>();
        // 跟踪文本 → computedId 映射，检测不同规则使用相同文本的情况 / Track text → computedId mapping to detect different rules sharing the same text
        java.util.Map<String, String> textToId = new java.util.HashMap<>();

        for (HudTrigger trigger : triggers) {
            String triggerType = trigger.getType();
            TriggerConfig triggerConfig = ConfigLoader.getTriggerConfig(triggerType);
            if (triggerConfig == null || triggerConfig.rules == null) continue;

            for (HintRule rule : triggerConfig.rules) {
                // 1) 应用三层默认值 / Apply three-layer defaults
                ConfigLoader.applyDefaults(rule, triggerConfig, globalConfig);

                // 2) 智能推断 triggerOn / Smart-infer triggerOn
                if (rule.triggerOn == null || rule.triggerOn.isEmpty()) {
                    rule.triggerOn = new HashMap<>(inferTriggerOn(rule, triggerType));
                }

                // 3) 展开多文本规则 / Expand multi-text rules
                List<HintRule> expanded = expandMultiTextRule(rule);

                // 4) 有效规则：有 triggerOn 且有文本 → 建立索引 / Valid rules: has triggerOn and has text → build index
                for (HintRule r : expanded) {
                    if (r.triggerOn != null && !r.triggerOn.isEmpty() && r.text != null) {
                        r.setTagBased(r.computeTagBased());
                        r.setComputedId(computeRuleId(r, triggerType));
                        if (!seenIds.add(r.getComputedId())) {
                            HUDTips.LOGGER.warn("Duplicate rule ID detected: \"{}\". "
                                + "Check your JSON configs — two rules in the same trigger section "
                                + "have the same effective ID. The second rule will be skipped.", r.getComputedId());
                        }
                        // 检测不同规则使用相同文本（已读状态以文本为键，同文本规则会共享已读状态）
                        // Detect different rules sharing the same text (seen state is keyed by text, rules with same text share seen state)
                        String prevId = textToId.putIfAbsent(r.text, r.getComputedId());
                        if (prevId != null && !prevId.equals(r.getComputedId())) {
                            HUDTips.LOGGER.warn("Two rules share the same text but have different IDs. "
                                + "They will share seen-state (completing one marks both as seen). "
                                + "Text: \"{}\"  ID 1: \"{}\"  ID 2: \"{}\"",
                                r.text, prevId, r.getComputedId());
                        }
                        allRules.add(r);
                        if (HudConfig.TRIGGER_MODE_ALL.equals(r.triggerOnMode)) {
                            andModeRules.add(r);
                        }
                        buildIndexForRule(r, triggerType);
                    }
                }
            }
        }

        HUDTips.LOGGER.info("RuleIndex loaded {} rules ({} AND-mode, {} items indexed, {} strings indexed).",
            allRules.size(), andModeRules.size(), itemToRulesMap.size(), stringToRulesMap.size());
    }

    /** 清空所有索引 / Clear all indexes */
    public void clear() {
        allRules.clear();
        andModeRules.clear();
        itemToRulesMap.clear();
        stringToRulesMap.clear();
    }

    // ── 多文本展开 / Multi-text expansion ──

    /**
     * 展开多文本规则。texts 数组 → 多条独立规则，优先级按数组顺序递减。
     * Expand multi-text rules. texts array → multiple independent rules, priority decreases by array order.
     */
    private static List<HintRule> expandMultiTextRule(HintRule rule) {
        if (rule.texts == null || rule.texts.size() <= 1) {
            return List.of(rule);
        }

        int basePriority = rule.priority != null ? rule.priority : 0;
        int n = rule.texts.size();
        List<HintRule> result = new ArrayList<>(n);

        for (int i = 0; i < n; i++) {
            HintRule clone = rule.copy();
            clone.text = rule.texts.get(i);
            clone.texts = null;
            clone.priority = basePriority + (n - 1 - i);
            result.add(clone);
        }
        return result;
    }

    // ── 索引构建 / Index building ──

    /**
     * 为单条规则建立索引（item、items，以及纯字符串触发器）。
     * Build indexes for a single rule (item, items, and pure string triggers).
     */
    private void buildIndexForRule(HintRule rule, String triggerType) {
        if (rule == null) return;

        if (rule.items != null) {
            for (String itemStr : rule.items) {
                if (itemStr != null && !itemStr.isEmpty()) {
                    indexItem(itemStr, rule);
                }
            }
        }
        // 从 triggerOn 索引实体值（on_kill 的字符串值，用于实体击杀触发器匹配）
        // Index entity value from triggerOn (on_kill string value, for entity kill trigger matching)
        String entityFromTrigger = rule.getEntity();
        if (entityFromTrigger != null && !entityFromTrigger.isEmpty()
            && !"true".equals(entityFromTrigger)) {
            indexItem(entityFromTrigger, rule);
        }
        // 从 triggerOn 索引实体值（on_look_entity 的字符串值，用于指向实体触发器匹配）
        // Index entity value from triggerOn (on_look_entity string value, for look-at-entity trigger matching)
        String lookEntity = rule.getLookEntity();
        if (lookEntity != null && !lookEntity.isEmpty()
            && !"true".equals(lookEntity)) {
            indexItem(lookEntity, rule);
        }
        // 从 triggerOn 索引方块值（on_look_block 的字符串值，用于指向方块触发器匹配）
        // Index block value from triggerOn (on_look_block string value, for look-at-block trigger matching)
        String lookBlock = rule.getLookBlock();
        if (lookBlock != null && !lookBlock.isEmpty()
            && !"true".equals(lookBlock)) {
            indexItem(lookBlock, rule);
        }
        List<String> lookBlockList = rule.getLookBlocks();
        if (lookBlockList != null) {
            for (String b : lookBlockList) {
                if (b != null && !b.isEmpty()) {
                    indexItem(b, rule);
                }
            }
        }
        // 维度字段纳入索引（使 on_dimension 触发器能查找到规则）
        // Index dimension field (so on_dimension triggers can find the rules)
        String dim = rule.getDimension();
        if (dim != null && !dim.isEmpty()) {
            indexItem(dim, rule);
        }
        // 从 triggerOn 索引按键值（on_key_press 的字符串值如 "key.sneak"，用于按键触发器匹配）
        // Index key press value from triggerOn (on_key_press string value e.g. "key.sneak", for key press trigger matching)
        String keyPress = rule.getKeyPress();
        if (keyPress != null && !keyPress.isEmpty()) {
            indexItem(keyPress, rule);
        }
        // 纯字符串标识触发器（如 on_low_health）：索引到触发器类型名 key 下
        // Pure string identifier triggers (e.g. on_low_health): index under trigger type name as key
        // 检查规则实际 triggerOn 中的所有 key，而不只是外层 section 名（兼容扁平数组格式）
        // Check all keys in the rule's actual triggerOn, not just the outer section name (compat with flat array format)
        if (rule.triggerOn != null) {
            for (String key : rule.triggerOn.keySet()) {
                if (isStringBasedTrigger(key)) {
                    stringToRulesMap.computeIfAbsent(key, k -> new ArrayList<>()).add(rule);
                }
            }
        }
    }

    /**
     * 将单个物品/字符串解析并加入索引。
     * Parse a single item/string and add it to the indexes.
     * <ul>
     *   <li>{@code #namespace:tag} → 物品或方块标签，展开为所有匹配项 / Item or block tag, expanded to all matching entries</li>
     *   <li>{@code namespace:id} → 具体物品，按 Item 索引 / Specific item, indexed by Item</li>
     *   <li>其他 → 字符串标识，放入 stringToRulesMap / Other → string identifier, placed in stringToRulesMap</li>
     * </ul>
     */
    private void indexItem(String itemStr, HintRule rule) {
        if (itemStr.startsWith("#")) {
            String tagId = itemStr.substring(1);
            Identifier id = Identifier.tryParse(tagId);
            if (id != null) {
                var tagKey = TagKey.create(Registries.ITEM, id);
                int count = 0;
                for (var holder : BuiltInRegistries.ITEM.getTagOrEmpty(tagKey)) {
                    Item item = holder.value();
                    itemToRulesMap.computeIfAbsent(item, k -> new ArrayList<>()).add(rule);
                    count++;
                }
                HUDTips.LOGGER.debug("Indexed item tag {} → {} items for rule: {}", tagId, count, rule.text);
            }
            return;
        }

        try {
            Item item = BuiltInRegistries.ITEM
                .getOptional(Identifier.tryParse(itemStr))
                .orElse(null);
            if (item != null) {
                itemToRulesMap.computeIfAbsent(item, k -> new ArrayList<>()).add(rule);
            } else {
                stringToRulesMap.computeIfAbsent(itemStr, k -> new ArrayList<>()).add(rule);
            }
        } catch (Exception e) {
            stringToRulesMap.computeIfAbsent(itemStr, k -> new ArrayList<>()).add(rule);
        }
    }

    /**
     * 判断触发器是否使用固定字符串作为激活标识（而非 Item 或动态维度 ID）。
     * Determine whether a trigger uses a fixed string as its activation identifier (rather than Item or dynamic dimension ID).
     */
    private static boolean isStringBasedTrigger(String triggerType) {
        return HudConfig.TRIGGER_ON_LOW_HEALTH.equals(triggerType)
            || HudConfig.TRIGGER_ON_KEY_PRESS.equals(triggerType);
    }

    // ── 规则 ID 计算 / Rule ID computation ──

    /**
     * 生成规则唯一标识（仅用于内存中规则去重，不参与持久化）。
     * Generate a unique rule identifier (only for in-memory dedup, not used for persistence).
     *
     * <p>格式：{@code {section}:{primaryId}:{textHash8}}。
     * primaryId 取自首条物品或维度，无则为空字符串。</p>
     * <p>Format: {@code {section}:{primaryId}:{textHash8}}.
     * primaryId is taken from the first item or dimension; empty string if none.</p>
     */
    private static String computeRuleId(HintRule rule, String sectionType) {
        String primary;
        if (rule.items != null && !rule.items.isEmpty()) {
            primary = rule.items.get(0);
        } else if (rule.hasDimension()) {
            primary = rule.getDimension();
        } else {
            primary = "";
        }
        String textHash = hashText(rule.text != null ? rule.text : "");
        return sectionType + ":" + primary + ":" + textHash;
    }

    /** SHA-256 前 8 位 hex / First 8 hex digits of SHA-256 */
    private static String hashText(String text) {
        try {
            var md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(8);
            for (int i = 0; i < 4; i++) {
                hex.append(String.format("%02x", hash[i] & 0xFF));
            }
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            return Integer.toHexString(text.hashCode());
        }
    }

    // ============================================================
    //  triggerOn 自动推断
    //  triggerOn Auto-Inference
    // ============================================================

    /**
     * 根据规则配置字段自动推断 triggerOn。
     * Auto-infer triggerOn from rule config fields.
     *
     * <p>推断优先级（从高到低）：/ Inference priority (highest to lowest):</p>
     * <ol>
     *   <li>AND 模式 + item + dimension → {"hold_item": true, "on_dimension": dimension值} / AND mode + item + dimension → {"hold_item": true, "on_dimension": dimension value}</li>
     *   <li>item + targetBlock/targetBlocks → {"on_activate_block": 参数值} / item + targetBlock/targetBlocks → {"on_activate_block": param value}</li>
     *   <li>dimension 且无 item → {"on_dimension_change": dimension值} / dimension and no item → {"on_dimension_change": dimension value}</li>
     *   <li>on_look_entity/on_look_block section → 以 true 兜底 / on_look_entity/on_look_block section → fallback to true</li>
     *   <li>on_key_press section → 从文本 {key:...} 提取按键名，失败以 true 兜底 / on_key_press section → extract key name from text {key:...}, fallback to true on failure</li>
     *   <li>有 item → {"hold_item": true} 或 section 自身类型 / has item → {"hold_item": true} or section's own type</li>
     *   <li>兜底 → section 自身类型 / Fallback → section's own type</li>
     * </ol>
     *
     * @param rule        规则 / Rule
     * @param sectionType 规则所在的 trigger section 名（如 "hold_item"） / Trigger section name the rule belongs to (e.g. "hold_item")
     * @return 推断的 triggerOn Map / Inferred triggerOn Map
     */
    public static Map<String, Object> inferTriggerOn(HintRule rule, String sectionType) {
        boolean hasItem = (rule.items != null && !rule.items.isEmpty());
        boolean hasDim = rule.hasDimension();
        boolean hasBlock = rule.hasTargetBlock();
        boolean isHoldItemSection = HudConfig.TRIGGER_HOLD_ITEM.equals(sectionType);

        // 1) AND 模式 + 物品 + 维度 → 双触发器（维度值内联）
        // 1) AND mode + item + dimension → dual triggers (dimension value inlined)
        if (HudConfig.TRIGGER_MODE_ALL.equals(rule.triggerOnMode) && hasItem && hasDim) {
            Map<String, Object> map = new java.util.LinkedHashMap<>();
            map.put(HudConfig.TRIGGER_HOLD_ITEM, true);
            map.put(HudConfig.TRIGGER_ON_DIMENSION, rule.getDimension());
            return map;
        }
        // 2) 物品 + 方块 → 激活方块触发器（方块值内联）
        // 2) Item + block → activate block trigger (block value inlined)
        if (hasItem && hasBlock) {
            Map<String, Object> map = new java.util.LinkedHashMap<>();
            map.put(HudConfig.TRIGGER_ON_ACTIVATE_BLOCK, makeBlockParam(rule));
            return map;
        }
        // 3) 只有维度（无物品） → 维度切换（维度值内联）
        // 3) Only dimension (no item) → dimension change (dimension value inlined)
        if (hasDim && !hasItem) {
            Map<String, Object> map = new java.util.LinkedHashMap<>();
            map.put(HudConfig.TRIGGER_ON_DIMENSION_CHANGE, rule.getDimension());
            return map;
        }
        // 4) on_look_entity / on_look_block section（无显式 triggerOn 时，以 true 兜底，具体实体/方块 ID 需用户在 triggerOn 中指定）
        // 4) on_look_entity / on_look_block section (when no explicit triggerOn, fallback to true; specific entity/block IDs must be specified by user in triggerOn)
        if (HudConfig.TRIGGER_ON_LOOK_ENTITY.equals(sectionType)) {
            return java.util.Collections.singletonMap(HudConfig.TRIGGER_ON_LOOK_ENTITY, true);
        }
        if (HudConfig.TRIGGER_ON_LOOK_BLOCK.equals(sectionType)) {
            return java.util.Collections.singletonMap(HudConfig.TRIGGER_ON_LOOK_BLOCK, true);
        }
        // 5) on_key_press section：尝试从文本 {key:...} 占位符中提取按键名，失败则以 true 兜底
        // 5) on_key_press section: try to extract key name from text {key:...} placeholder, fallback to true on failure
        if (HudConfig.TRIGGER_ON_KEY_PRESS.equals(sectionType)) {
            String inferredKey = KeyMappingLookup.extractFirstKeyName(rule.text);
            if (inferredKey != null) {
                return java.util.Collections.singletonMap(HudConfig.TRIGGER_ON_KEY_PRESS, inferredKey);
            }
            return java.util.Collections.singletonMap(HudConfig.TRIGGER_ON_KEY_PRESS, true);
        }
        // 6) 有物品 → hold_item section 用 hold_item，其他 section 用自身类型
        // 6) Has item → hold_item section uses hold_item, other sections use their own type
        if (hasItem) {
            Map<String, Object> map = new java.util.LinkedHashMap<>();
            String type = isHoldItemSection ? HudConfig.TRIGGER_HOLD_ITEM
                : (sectionType != null ? sectionType : HudConfig.TRIGGER_HOLD_ITEM);
            map.put(type, true);
            return map;
        }
        // 7) 兜底：section 自身类型
        // 7) Fallback: section's own type
        if (sectionType != null) {
            return java.util.Collections.singletonMap(sectionType, true);
        }
        return java.util.Collections.emptyMap();
    }

    /** 根据规则已有的 block 参数，构造 on_activate_block 的值 / Build on_activate_block value from the rule's existing block parameters */
    private static Object makeBlockParam(HintRule rule) {
        String tb = rule.getTargetBlock();
        if (tb != null && !tb.isEmpty()) return tb;
        List<String> tbs = rule.getTargetBlocks();
        if (tbs != null && !tbs.isEmpty()) {
            Map<String, Object> obj = new HashMap<>();
            obj.put("targetBlocks", tbs);
            return obj;
        }
        return true;
    }

    // ============================================================
    //  查询接口
    //  Query Interface
    // ============================================================

    /**
     * 使用预建索引查找标识对应的候选规则（O(1)）。
     * Look up candidate rules for an identifier using pre-built indexes (O(1)).
     *
     * <p>String 标识符优先查 {@code stringToRulesMap}（实体 ID、维度 ID 等），
     * 未命中时回退查 {@code itemToRulesMap}。
     * 回退是必要的：{@code on_look_block} 等触发器返回方块注册名（String），
     * 但方块注册名同时也是有效的 Item（方块物品），
     * 在 {@link #indexItem} 中被路由到了 {@code itemToRulesMap}。</p>
     * <p>String identifiers first check {@code stringToRulesMap} (entity IDs, dimension IDs, etc.),
     * falling back to {@code itemToRulesMap} on miss.
     * The fallback is necessary: triggers like {@code on_look_block} return block registry names (String),
     * but block registry names are also valid Items (block items),
     * which were routed to {@code itemToRulesMap} in {@link #indexItem}.</p>
     */
    public List<HintRule> getCandidateRules(Object identifier) {
        if (identifier == null) return null;
        if (identifier instanceof Item) return itemToRulesMap.get(identifier);
        if (identifier instanceof String str && !str.isEmpty()) {
            List<HintRule> result = stringToRulesMap.get(str);
            if (result != null) return result;
            // 回退：String 标识符可能是方块注册名（也是有效的 Item）
            // Fallback: string identifier may be a block registry name (which is also a valid Item)
            Item item = BuiltInRegistries.ITEM.getOptional(Identifier.tryParse(str)).orElse(null);
            if (item != null) return itemToRulesMap.get(item);
            return null;
        }
        return null;
    }

    /** @return 所有规则的只读列表 / Read-only list of all rules */
    public List<HintRule> getAllRules() {
        return Collections.unmodifiableList(allRules);
    }

    /** @return AND 模式规则子集的只读列表 / Read-only list of AND-mode rule subset */
    public List<HintRule> getAndModeRules() {
        return Collections.unmodifiableList(andModeRules);
    }

}
