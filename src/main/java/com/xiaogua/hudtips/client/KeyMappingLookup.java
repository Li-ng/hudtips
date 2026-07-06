package com.xiaogua.hudtips.client;

import com.xiaogua.hudtips.HUDTips;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 按键映射查找工具 —— 将翻译键名解析为实际的 {@link KeyMapping} 实例。
 * Key mapping lookup utility — resolves translation key names to actual {@link KeyMapping} instances.
 *
 * <p>通过反射扫描 {@link Options} 类的所有 {@code KeyMapping} 字段，
 * 在初始化时建立翻译键名到 KeyMapping 实例的查找表。
 * 用于两个场景：</p>
 * <p>Scans all {@code KeyMapping} fields of the {@link Options} class via reflection,
 * building a translation-key-name to KeyMapping instance lookup table at init time.
 * Used in two scenarios:</p>
 * <ul>
 *   <li><b>文本占位符替换</b>：将提示文本中 {@code {key:key.sneak}} 替换为玩家设置的实际按键名</li>
 *   <li><b>Text placeholder replacement</b>: replaces {@code {key:key.sneak}} in hint text with the player's actual bound key name</li>
 *   <li><b>触发器按键监控</b>：OnKeyPressTrigger 通过翻译键名查找对应的 KeyMapping 来轮询按下状态</li>
 *   <li><b>Trigger key monitoring</b>: OnKeyPressTrigger looks up the corresponding KeyMapping by translation key name to poll press state</li>
 * </ul>
 *
 * <h2>初始化 / Initialization</h2>
 * <p>在 {@code HUDTipsClient.onClientSetup()} 中调用 {@link #init()}。
 * 初始化是幂等的，多次调用等效于一次。</p>
 * <p>Called in {@code HUDTipsClient.onClientSetup()} via {@link #init()}.
 * Initialization is idempotent — multiple calls are equivalent to one.</p>
 *
 * <h2>设计决策 / Design Decisions</h2>
 * <ul>
 *   <li><b>反射而非硬编码</b>：自动发现所有 KeyMapping 字段，包括模组添加的按键绑定，
 *       避免了手动维护已知按键列表</li>
 *   <li><b>Reflection over hardcoding</b>: auto-discovers all KeyMapping fields, including mod-added key bindings,
 *       avoiding manual maintenance of a known key list</li>
 *   <li><b>全静态</b>：查找表全局共享，无需每触发器单独构建</li>
 *   <li><b>All-static</b>: lookup table is globally shared, no per-trigger rebuild needed</li>
 * </ul>
 *
 * @see OnKeyPressTrigger (位于 trigger 包 / in trigger package)
 */
public class KeyMappingLookup {

    /** 私有构造函数，防止实例化（全静态工具类）。 / Private constructor to prevent instantiation (all-static utility class). */
    private KeyMappingLookup() {}

    // ============================================================
    //  查找表
    //  Lookup Table
    // ============================================================

    /** 翻译键名（如 "key.sneak"）→ KeyMapping 实例 / Translation key name (e.g. "key.sneak") → KeyMapping instance */
    private static final Map<String, KeyMapping> nameToKeyMapping = new HashMap<>();

    /** 是否已初始化 / Whether already initialized */
    private static boolean initialized = false;

    /** 已报告过"未找到"的按键名（去重避免刷日志） / Key names already reported as "not found" (dedup to avoid log spam) */
    private static final Set<String> warnedMissingKeys = new HashSet<>();

    // ============================================================
    //  占位符正则
    //  Placeholder Regex
    // ============================================================

    /** 匹配文本中的 {key:翻译键名} 占位符。键名可含字母、数字、下划线、点号 / Matches {key:translationKeyName} placeholders in text. Key name may contain letters, digits, underscores, dots */
    private static final Pattern KEY_PLACEHOLDER_PATTERN =
        Pattern.compile("\\{key:([a-zA-Z0-9_.]+)}");

    // ============================================================
    //  初始化
    //  Initialization
    // ============================================================

    /**
     * 初始化按键映射查找表（幂等）。
     * Initialize the key mapping lookup table (idempotent).
     *
     * <p>通过反射遍历 {@link Options} 的所有公开字段，
     * 收集所有 {@link KeyMapping} 类型的字段，以 {@code getName()} 为键建立映射。</p>
     * <p>Iterates all public fields of {@link Options} via reflection,
     * collects all {@link KeyMapping} type fields, and maps them keyed by {@code getName()}.</p>
     *
     * <p>数组字段（如 {@code keyHotbarSlots}）也会逐元素处理。</p>
     * <p>Array fields (e.g. {@code keyHotbarSlots}) are also processed element by element.</p>
     */
    public static void init() {
        if (initialized) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.options == null) {
            HUDTips.LOGGER.warn("KeyMappingLookup: Minecraft.options is null, skipping init");
            return;
        }

        int count = 0;
        for (Field field : Options.class.getFields()) {
            try {
                if (KeyMapping.class.isAssignableFrom(field.getType())) {
                    // 单个 KeyMapping 字段 / Single KeyMapping field
                    KeyMapping km = (KeyMapping) field.get(mc.options);
                    if (km != null) {
                        nameToKeyMapping.put(km.getName(), km);
                        count++;
                    }
                } else if (field.getType().isArray()
                    && KeyMapping.class.isAssignableFrom(field.getType().getComponentType())) {
                    // KeyMapping[] 数组字段（如 keyHotbarSlots） / KeyMapping[] array field (e.g. keyHotbarSlots)
                    Object[] array = (Object[]) field.get(mc.options);
                    if (array != null) {
                        for (Object elem : array) {
                            if (elem instanceof KeyMapping km) {
                                nameToKeyMapping.put(km.getName(), km);
                                count++;
                            }
                        }
                    }
                }
            } catch (IllegalAccessException e) {
                HUDTips.LOGGER.debug("KeyMappingLookup: cannot access field {}: {}",
                    field.getName(), e.getMessage());
            }
        }

        initialized = true;
        HUDTips.LOGGER.info("KeyMappingLookup initialized with {} key mapping(s).", count);
    }

    // ============================================================
    //  查询
    //  Query
    // ============================================================

    /**
     * 按翻译键名查找 KeyMapping 实例。
     * Look up a KeyMapping instance by translation key name.
     *
     * @param translationKey 翻译键名，如 {@code "key.sneak"} / Translation key name, e.g. {@code "key.sneak"}
     * @return 对应的 KeyMapping 实例，未找到返回 null / Corresponding KeyMapping instance, null if not found
     */
    public static KeyMapping getKeyMapping(String translationKey) {
        if (!initialized) init();
        return nameToKeyMapping.get(translationKey);
    }

    /**
     * 获取按键的本地化显示名称。
     * Get the localized display name of a key.
     *
     * <p>例如 {@code "key.sneak"} → {@code "[左 Shift]"}（中文）或 {@code "[Left Shift]"}（英文）。
     * 未找到时返回 {@code "[translationKey]"} 作为兜底。</p>
     * <p>E.g. {@code "key.sneak"} → {@code "[Left Shift]"} (English) or {@code "[左 Shift]"} (Chinese).
     * Returns {@code "[translationKey]"} as fallback when not found.</p>
     *
     * @param translationKey 翻译键名 / Translation key name
     * @return 按键的显示名称字符串（含方括号） / Key display name string (with brackets)
     */
    public static String getKeyDisplayName(String translationKey) {
        KeyMapping km = getKeyMapping(translationKey);
        if (km != null) {
            return km.getTranslatedKeyMessage().getString();
        }
        // 兜底：只警告一次 / Fallback: warn only once
        if (warnedMissingKeys.add(translationKey)) {
            HUDTips.LOGGER.warn("KeyMappingLookup: unknown key translation name \"{}\"", translationKey);
        }
        return "[" + translationKey + "]";
    }

    /**
     * 获取所有已知的按键映射。
     * Get all known key mappings.
     *
     * <p>返回翻译键名（如 {@code "key.sneak"}）到 {@link KeyMapping} 实例的映射。
     * 调用方只应读取，不应修改返回的映射。</p>
     * <p>Returns a mapping from translation key names (e.g. {@code "key.sneak"}) to {@link KeyMapping} instances.
     * Callers should only read, not modify, the returned map.</p>
     *
     * <p>主要用于 {@code OnKeyPressTrigger} 遍历所有按键检测按下状态。</p>
     * <p>Mainly used by {@code OnKeyPressTrigger} to iterate all keys and detect press state.</p>
     *
     * @return 翻译键名 → KeyMapping 实例的映射（不可修改视图） / Translation key name → KeyMapping instance map (unmodifiable view)
     */
    public static Map<String, KeyMapping> getAllMappings() {
        if (!initialized) init();
        return Collections.unmodifiableMap(nameToKeyMapping);
    }

    // ============================================================
    //  文本占位符替换
    //  Text Placeholder Replacement
    // ============================================================

    /**
     * 替换文本中所有 {@code {key:翻译键名}} 占位符为实际按键显示名。
     * Replace all {@code {key:translationKeyName}} placeholders in text with actual key display names.
     *
     * <p>不含 "{key:" 的文本直接返回原实例（零开销快速路径）。
     * 使用 {@link Matcher#quoteReplacement} 防止按键名中的特殊字符干扰正则替换。</p>
     * <p>Text without "{key:" is returned as-is (zero-overhead fast path).
     * Uses {@link Matcher#quoteReplacement} to prevent special chars in key names from interfering with regex replacement.</p>
     *
     * @param text 可能包含占位符的原始文本 / Raw text that may contain placeholders
     * @return 替换后的文本；输入为 null 时返回 null，输入为空时返回空字符串 / Replaced text; null for null input, empty string for empty input
     */
    public static String resolveKeyPlaceholders(String text) {
        if (text == null || text.isEmpty()) return text;
        // 快速路径：不含占位符前缀则跳过正则 / Fast path: skip regex if no placeholder prefix present
        if (!text.contains("{key:")) return text;

        Matcher matcher = KEY_PLACEHOLDER_PATTERN.matcher(text);
        StringBuilder sb = new StringBuilder(text.length() + 16);
        while (matcher.find()) {
            String keyName = matcher.group(1);
            String display = getKeyDisplayName(keyName);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(display));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * 从文本中提取第一个 {@code {key:...}} 占位符中的按键翻译键名。
     * Extract the key translation name from the first {@code {key:...}} placeholder in text.
     *
     * <p>用于 {@code RuleIndex.inferTriggerOn()} 中的自动推断：
     * 当规则在 {@code on_key_press} section 中且未显式配置 triggerOn 时，
     * 从文本中提取按键名作为触发参数。</p>
     * <p>Used for auto-inference in {@code RuleIndex.inferTriggerOn()}:
     * when a rule is in the {@code on_key_press} section without explicit triggerOn config,
     * extracts the key name from the text as the trigger parameter.</p>
     *
     * @param text 提示文本 / Hint text
     * @return 第一个匹配的按键翻译键名，无匹配时返回 null / First matched key translation name, null if no match
     */
    public static String extractFirstKeyName(String text) {
        if (text == null || text.isEmpty()) return null;
        Matcher matcher = KEY_PLACEHOLDER_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}
