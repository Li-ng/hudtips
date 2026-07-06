package com.xiaogua.hudtips.client.trigger;

import com.xiaogua.hudtips.client.KeyMappingLookup;
import com.xiaogua.hudtips.client.config.HudConfig;
import com.xiaogua.hudtips.client.config.TriggerConfig;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 按键按下触发器 —— 检测玩家按下 Minecraft 中的任意按键。
 * Key-press trigger — detects when the player presses any key in Minecraft.
 *
 * <p>通过 {@link KeyMappingLookup} 获取所有已注册的按键映射（包括原版和模组），
 * 在每 tick 中轮询 {@link KeyMapping#isDown()} 状态并检测按下边沿（false→true 转换）。
 * 触发时将按键的翻译键名（如 {@code "key.sneak"}）作为激活标识返回，
 * 由 {@link RuleIndex} 通过 {@code stringToRulesMap} 进行 O(1) 规则匹配。</p>
 * <p>Obtains all registered key mappings (including vanilla and mod) via {@link KeyMappingLookup},
 * polls {@link KeyMapping#isDown()} state each tick and detects press edges (false→true transition).
 * On trigger, returns the key's translation key name (e.g. {@code "key.sneak"}) as the activation
 * identifier, matched against rules in O(1) by {@link RuleIndex} via {@code stringToRulesMap}.</p>
 *
 * <h2>设计决策 / Design Decisions</h2>
 * <ul>
 *   <li><b>轮询 isDown() 而非监听 InputEvent.Key</b>：避免消费按键事件，
 *       不干扰游戏正常输入处理；且实现更简洁，无需注册事件监听器</li>
 *   <li><b>Poll isDown() rather than listening for InputEvent.Key</b>: avoids consuming key events,
 *       does not interfere with normal game input processing; also simpler implementation
 *       without needing to register event listeners</li>
 *   <li><b>event 型</b>：按键按下是一次性事件，仅在按下边沿触发一次，
 *       持续按住不会重复触发。消失时机由规则的 {@code dismissOn} 控制</li>
 *   <li><b>event type</b>: key press is a one-time event, only triggered once on the press edge;
 *       holding the key down does not re-trigger. Dismissal timing is controlled by the rule's
 *       {@code dismissOn}</li>
 *   <li><b>String 标识</b>：返回按键的翻译键名（如 {@code "key.sneak"}），
 *       与 on_low_health、on_dimension 等 String 型触发器保持一致，
 *       复用 {@code stringToRulesMap} 索引</li>
 *   <li><b>String identifier</b>: returns the key's translation key name (e.g. {@code "key.sneak"}),
 *       consistent with String-type triggers like on_low_health and on_dimension,
 *       reusing the {@code stringToRulesMap} index</li>
 *   <li><b>同一 tick 只报告一个按键</b>：遍历时取第一个检测到边沿的按键，
 *       避免单 tick 内多键同时按下导致的不确定行为</li>
 *   <li><b>Only one key reported per tick</b>: takes the first edge-detected key during iteration,
 *       avoiding non-deterministic behavior from multiple simultaneous key presses in a single tick</li>
 * </ul>
 *
 * <h2>JSON 配置示例 / JSON Configuration Example</h2>
 * <pre>
 * // 基础用法：按下潜行键时显示提示
 * // Basic usage: show hint when sneak key is pressed
 * {
 *   "triggerOn": { "on_key_press": "key.sneak" },
 *   "text": "按下 {key:key.sneak} 可以潜行！",
 *   "dismissOn": ["time:5000"]
 * }
 *
 * // AND 组合：低血量时按下背包键
 * // AND combination: press inventory key while at low health
 * {
 *   "triggerOn": {
 *     "on_low_health": true,
 *     "on_key_press": "key.inventory"
 *   },
 *   "text": "血量不足！按 {key:key.inventory} 打开背包使用药水"
 * }
 *
 * // 作为 dismissOn：按下任意键关闭提示
 * // As dismissOn: press any key to dismiss the hint
 * {
 *   "triggerOn": { "hold_item": "minecraft:shield" },
 *   "text": "手持盾牌",
 *   "dismissOn": ["on_key_press"]
 * }
 * </pre>
 *
 * @see KeyMappingLookup 按键映射查找工具 / Key mapping lookup utility
 * @see HudTrigger     触发器接口 / Trigger interface
 * @see TriggerTemplate 触发器实现模板 / Trigger implementation template
 */
public class OnKeyPressTrigger implements HudTrigger {

    // ============================================================
    //  实例状态
    //  Instance State
    // ============================================================

    /**
     * 当前这一 tick 按下的按键翻译键名（如 {@code "key.sneak"}）。
     * 仅在检测到按下边沿的那个 tick 为非 null，下一 tick 即清空。
     * The translation key name of the key pressed on this tick (e.g. {@code "key.sneak"}).
     * Non-null only on the tick where a press edge is detected; cleared on the next tick.
     */
    private String currentKeyName = null;

    /**
     * 追踪每个按键上一 tick 的 {@link KeyMapping#isDown()} 状态。
     * 键为翻译键名，值为上一 tick 的 isDown() 结果。
     * 用于检测按下边沿（false→true 转换），避免持续按住时重复触发。
     * Tracks the {@link KeyMapping#isDown()} state of each key from the previous tick.
     * Key: translation key name; Value: previous tick's isDown() result.
     * Used to detect press edges (false→true transition), avoiding repeated triggers while holding.
     */
    private final Map<String, Boolean> prevDownStates = new HashMap<>();

    // ============================================================
    //  生命周期
    //  Lifecycle
    // ============================================================

    /**
     * 初始化触发器。
     * Initialize the trigger.
     *
     * <p>确保 {@link KeyMappingLookup} 已完成初始化（幂等操作），
     * 然后调用 {@link #reset()} 清空运行时状态。</p>
     * <p>Ensures {@link KeyMappingLookup} has completed initialization (idempotent operation),
     * then calls {@link #reset()} to clear runtime state.</p>
     *
     * @param triggerConfig 触发器配置（含 settings），本触发器暂无特有参数 / Trigger configuration (includes settings); this trigger currently has no specific parameters
     * @param globalConfig  全局配置 / Global configuration
     */
    @Override
    public void init(TriggerConfig triggerConfig, HudConfig globalConfig) {
        // 确保按键查找表已初始化（幂等，多次调用等效于一次）
        // Ensure key lookup table is initialized (idempotent; multiple calls equivalent to one)
        KeyMappingLookup.init();
        reset();
    }

    /**
     * 每游戏刻调用，轮询所有按键的按下状态并检测边沿。
     * Called every game tick; polls all key down states and detects edges.
     *
     * <p>遍历 {@link KeyMappingLookup#getAllMappings()} 中的所有按键映射，
     * 比较当前 isDown() 与上一 tick 记录的状态。
     * 检测到 false→true 转换时，记录该按键的翻译键名并立即返回。</p>
     * <p>Iterates over all key mappings in {@link KeyMappingLookup#getAllMappings()},
     * comparing current isDown() with the previous tick's recorded state.
     * When a false→true transition is detected, records that key's translation name and returns.</p>
     *
     * <p><b>性能说明</b>：原版 Minecraft 约 50-80 个按键映射，
     * 每个仅调用一次轻量级 boolean 检查，
     * 加上模组按键总数通常不超过 150，每 tick 开销可忽略。</p>
     * <p><b>Performance note</b>: Vanilla Minecraft has approximately 50-80 key mappings;
     * each only incurs a single lightweight boolean check.
     * With mod keys the total typically does not exceed 150; per-tick overhead is negligible.</p>
     *
     * @return true 如果本轮检测到了新的按键按下（状态发生变化） / true if a new key press was detected this cycle (state changed)
     */
    @Override
    public boolean tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            boolean changed = (currentKeyName != null);
            currentKeyName = null;
            prevDownStates.clear();
            return changed;
        }

        String prev = currentKeyName;
        currentKeyName = null;

        // 遍历所有已知按键，检测按下边沿（false → true）
        // 注意：必须遍历完所有按键以保证 prevDownStates 完整更新，不能提前 break
        // Iterate all known keys, detect press edges (false → true)
        // Note: must iterate all keys to ensure prevDownStates is fully updated; cannot break early
        for (Map.Entry<String, KeyMapping> entry : KeyMappingLookup.getAllMappings().entrySet()) {
            String keyName = entry.getKey();
            KeyMapping km = entry.getValue();
            boolean wasDown = prevDownStates.getOrDefault(keyName, false);
            boolean isDown = km.isDown();

            // 更新追踪状态（无论是否触发都要更新，保证下次能正确检测边沿）
            // Update tracking state (must update regardless of trigger, to ensure correct edge detection next time)
            prevDownStates.put(keyName, isDown);

            if (isDown && !wasDown && currentKeyName == null) {
                // 检测到按下边沿：记录第一个按下的按键，但不 break
                // —— 必须继续更新剩余按键的 prevDownStates，避免下一 tick 虚假触发
                // Detected press edge: record the first pressed key, but do not break
                // — must continue updating remaining keys' prevDownStates to avoid false triggers next tick
                currentKeyName = keyName;
            }
        }

        return !Objects.equals(prev, currentKeyName);
    }

    /**
     * 重置所有运行时状态。
     * Reset all runtime state.
     *
     * <p>在配置重载或离开世界时调用。清空当前按键名和所有按键追踪状态，
     * 确保下次进入世界时从干净状态开始。</p>
     * <p>Called on config reload or when leaving the world. Clears the current key name
     * and all key tracking state, ensuring a clean start when re-entering the world.</p>
     */
    @Override
    public void reset() {
        currentKeyName = null;
        prevDownStates.clear();
    }

    // ============================================================
    //  查询 — TriggerManager 通过这些方法获取触发器信息
    //  Query — TriggerManager obtains trigger info through these methods
    // ============================================================

    /**
     * 获取当前激活的标识列表。
     * Get the list of currently active identifiers.
     *
     * <p>返回按键的翻译键名字符串（如 {@code "key.sneak"}）。
     * TriggerManager 使用它在 {@code stringToRulesMap} 中查找匹配的规则。</p>
     * <p>Returns the key's translation key name string (e.g. {@code "key.sneak"}).
     * TriggerManager uses this to look up matching rules in {@code stringToRulesMap}.</p>
     *
     * @return 包含当前按下按键翻译键名的列表，无按下时返回空列表 / List containing the currently pressed key's translation name, or an empty list if none pressed
     */
    @Override
    public List<Object> getActiveTriggers() {
        if (currentKeyName != null) {
            return List.of((Object) currentKeyName);
        }
        return List.of();
    }

    /**
     * 获取优先级。
     * Get priority.
     *
     * <p>返回 8，介于 on_use(10) 和 hold_item(0) 之间。
     * 在 ANY 模式下，同一位置有多个触发器竞争时，优先级高的胜出。</p>
     * <p>Returns 8, between on_use(10) and hold_item(0).
     * In ANY mode, when multiple triggers compete for the same position, the higher priority wins.</p>
     */
    @Override
    public int getPriority() {
        return 8;
    }

    /**
     * 触发器类型标识 —— 对应 JSON 配置中的键名。
     * Trigger type identifier — corresponds to the key name in JSON configuration.
     *
     * <p>在 {@code triggerOn} 中使用：{@code { "on_key_press": "key.sneak" } }
     * <br>在 {@code dismissOn} 中使用：{@code ["on_key_press"] }</p>
     * <p>Used in {@code triggerOn}: {@code { "on_key_press": "key.sneak" } }
     * <br>Used in {@code dismissOn}: {@code ["on_key_press"] }</p>
     */
    @Override
    public String getType() {
        return "on_key_press";
    }

    /**
     * 是否为持续型触发器。
     * Whether this is a continuous trigger.
     *
     * @return false（event 型）：按键按下是瞬时事件，仅在按下边沿触发一次 / false (event type): key press is an instantaneous event, triggered only once on the press edge
     */
    @Override
    public boolean isContinuous() {
        return false;
    }

    /**
     * 是否以 Item 作为激活标识。
     * Whether to use Item as the activation identifier.
     *
     * @return false：返回 String 型标识（按键翻译键名），不参与物品互斥 / false: returns String-type identifier (key translation name), does not participate in item exclusivity
     */
    @Override
    public boolean isItemBased() {
        return false;
    }
}
