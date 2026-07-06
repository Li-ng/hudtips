package com.xiaogua.hudtips.client.trigger;

import com.xiaogua.hudtips.HUDTips;
import com.xiaogua.hudtips.client.config.HudConfig;
import com.xiaogua.hudtips.client.config.TriggerConfig;
import net.minecraft.client.Minecraft;

import java.util.List;

/**
 * 触发器实现模板 —— 演示所有标准模式。
 * Trigger implementation template — demonstrates all standard patterns.
 *
 * <h2>新建触发器步骤 / Steps to Create a New Trigger</h2>
 * <ol>
 *   <li>复制本文件，重命名类名和 {@link #getType()} 返回值 / Copy this file, rename the class and {@link #getType()} return value</li>
 *   <li>决定类型：<b>continuous</b>（持续激活）还是 <b>event</b>（触发一次） / Decide type: <b>continuous</b> (continuously active) or <b>event</b> (trigger once)</li>
 *   <li>决定标识类型：返回 {@code Item}（物品驱动，互斥）还是 {@code String}（非互斥） / Decide identifier type: return {@code Item} (item-driven, exclusive) or {@code String} (non-exclusive)</li>
 *   <li>实现核心检测逻辑 → {@link #tick()} / Implement core detection logic</li>
 *   <li>实现激活标识返回 → {@link #getActiveTriggers()} / Implement active identifier return</li>
 *   <li>（可选）添加事件监听 → 参考 {@code OnUseTrigger.EventListener} / (Optional) Add event listener → see {@code OnUseTrigger.EventListener}</li>
 *   <li>在 {@code TriggerManager.initTriggers()} 中加一行 {@code registerIfUsed(...)} / Add a {@code registerIfUsed(...)} line in {@code TriggerManager.initTriggers()}</li>
 *   <li>在 {@code HudConfig} 中添加触发器类型常量 / Add trigger type constant in {@code HudConfig}</li>
 *   <li>在 {@code ConfigValidator.VALID_TRIGGER_TYPES} 中添加触发器类型名 / Add trigger type name in {@code ConfigValidator.VALID_TRIGGER_TYPES}</li>
 * </ol>
 *
 * <h2>关键决策表 / Key Decision Table</h2>
 * <table border="1">
 *   <tr><th>场景 / Scenario</th><th>continuous</th><th>isItemBased</th><th>标识类型 / Identifier Type</th></tr>
 *   <tr><td>手持物品时显示 / Show while holding item</td><td>true</td><td>true</td><td>Item</td></tr>
 *   <tr><td>右键使用物品时触发 / Trigger on right-click item use</td><td>false</td><td>true</td><td>Item</td></tr>
 *   <tr><td>手持物品右键方块时触发 / Trigger on right-click block while holding item</td><td>false</td><td>true</td><td>Item</td></tr>
 *   <tr><td>指向方块时 / While looking at block</td><td>true</td><td>false</td><td>String</td></tr>
 *   <tr><td>指向实体时 / While looking at entity</td><td>true</td><td>false</td><td>String</td></tr>
 *   <tr><td>血量低于阈值 / Health below threshold</td><td>true</td><td>false</td><td>String</td></tr>
 *   <tr><td>处于特定维度 / In specific dimension</td><td>true</td><td>false</td><td>String</td></tr>
 *   <tr><td>切换维度时触发 / Trigger on dimension change</td><td>false</td><td>false</td><td>String</td></tr>
 *   <tr><td>击杀实体时触发 / Trigger on entity kill</td><td>false</td><td>false</td><td>String</td></tr>
 * </table>
 *
 * <h2>方法实现顺序（所有触发器统一遵循）/ Method Implementation Order (followed uniformly by all triggers)</h2>
 * <pre>
 * [字段声明]              / [Field declarations]
 * [事件共享状态]          / [Event shared state]（如有事件监听器 / if has event listener）
 * [EventBusSubscriber 内部类] / [EventBusSubscriber inner class]（如有事件监听器 / if has event listener）
 * init()     → 读取配置，调用 reset() / Read config, call reset()
 * tick()     → 核心检测逻辑，更新内部状态 / Core detection logic, update internal state
 * reset()    → 清空所有状态 / Clear all state
 * getActiveTriggers() → 返回激活标识列表 / Return active identifier list
 * getPriority()       → 数值越大越优先 / Higher value = higher priority
 * getType()           → JSON 配置中的键名 / Key name in JSON config
 * isContinuous()      → true=持续, false=事件 / true=continuous, false=event
 * isItemBased()       → true=Item 互斥, false=独立 / true=Item exclusive, false=independent
 * getParentType()     → null=基础触发器, non-null=子类型 / null=base trigger, non-null=child type
 * </pre>
 *
 * @see HudTrigger      接口定义 / Interface definition
 * @see OnUseTrigger    事件型 + 事件监听 完整示例 / Complete example: event type + event listener
 * @see HoldItemTrigger 最简单的 continuous 实现 / Simplest continuous implementation
 */
public class TriggerTemplate implements HudTrigger {

    // ============================================================
    //  实例状态 — 每 tick 由 tick() 更新
    //  Instance State — updated each tick by tick()
    // ============================================================

    /** 当前激活的标识（仅在触发时非 null）。
     *  Current active identifier (non-null only when triggered).
     *  Item 型返回 Item，String 型返回 String。
     *  Item type returns Item, String type returns String.
     *  命名建议：currentXxx（currentItem、currentDimensionId 等）
     *  Naming suggestion: currentXxx (currentItem, currentDimensionId, etc.) */
    // TODO: 替换为你的状态字段 / Replace with your state field
    private Object currentId = null; // 示例：改为 private Item currentItem = null; / Example: change to private Item currentItem = null;

    /** 状态追踪字段（用于 event 型的状态转换检测）。
     *  State tracking field (for event-type state transition detection).
     *  例如 wasUsingItem、lastLevel 等。
     *  E.g. wasUsingItem, lastLevel, etc.
     *  continuous 型通常不需要此字段。
     *  Continuous type typically does not need this field. */
    // TODO: 按需添加追踪字段 / Add tracking fields as needed

    // ============================================================
    //  事件共享状态 — 仅当触发器有 @EventBusSubscriber 时需要
    //  Event Shared State — only needed when the trigger has @EventBusSubscriber
    // ============================================================

    /*
     * 如果你需要监听 NeoForge 事件（如 OnUseTrigger 监听右键按键）：
     * If you need to listen to NeoForge events (e.g. OnUseTrigger listens for right-click key):
     *
     * 1. 在此声明 static 字段供事件回调写入：
     *    Declare static fields here for event callbacks to write into:
     *      private static Item eventItem = null;
     *      private static boolean eventPending = false;
     *
     * 2. 添加 @EventBusSubscriber 内部类：
     *    Add @EventBusSubscriber inner class:
     *      @EventBusSubscriber(modid = HUDTips.MODID, value = Dist.CLIENT)
     *      public static class EventListener {
     *          @SubscribeEvent
     *          public static void onXxxEvent(XxxEvent event) { ... }
     *      }
     *
     * 3. 在 tick() 中消费 eventPending 标记
     *    Consume the eventPending flag in tick()
     *
     * 注意：NeoForge 客户端事件和 ClientTick 均在渲染线程执行，无需额外同步。
     * Note: NeoForge client events and ClientTick all execute on the render thread, no extra sync needed.
     * 参考：OnUseTrigger.java（双重检测）和 OnDimensionChangeTrigger.java（事件+兜底）
     * Reference: OnUseTrigger.java (dual detection) and OnDimensionChangeTrigger.java (event + fallback)
     */

    // ============================================================
    //  生命周期
    //  Lifecycle
    // ============================================================

    /**
     * 初始化触发器。
     * Initialize the trigger.
     *
     * <p>在配置加载或热重载时调用。在此读取 {@code settings} 中的触发器特有参数，
     * 然后调用 {@link #reset()} 清空运行时状态。</p>
     * <p>Called when configs are loaded or hot-reloaded. Read trigger-specific
     * parameters from {@code settings} here, then call {@link #reset()} to clear runtime state.</p>
     *
     * @param triggerConfig 触发器配置（含 settings），可能为 null / Trigger config (with settings), may be null
     * @param globalConfig  全局配置，可能为 null / Global config, may be null
     */
    @Override
    public void init(TriggerConfig triggerConfig, HudConfig globalConfig) {
        // TODO: 从 triggerConfig.settings 读取自定义参数 / Read custom parameters from triggerConfig.settings
        // 示例：读取血量阈值 / Example: read health threshold
        // if (triggerConfig != null && triggerConfig.settings != null) {
        //     Object value = triggerConfig.settings.get("yourSetting");
        //     if (value instanceof Number num) { yourSetting = num.floatValue(); }
        // }
        reset();
    }

    /**
     * 每游戏刻调用，检测触发条件。
     * Called per game tick to detect trigger conditions.
     *
     * <h3>continuous 型模板：/ Continuous type template:</h3>
     * <pre>
     * Object prev = currentId;
     * if (条件满足 / condition met) {
     *     currentId = 激活标识; / active identifier
     * } else {
     *     currentId = null;
     * }
     * return !java.util.Objects.equals(prev, currentId); // 状态变化时返回 true / return true on state change
     * </pre>
     *
     * <h3>event 型模板（状态转换检测）：/ Event type template (state transition detection):</h3>
     * <pre>
     * boolean nowActive = 检测条件(); / detect condition
     * if (nowActive &amp;&amp; !wasActive) {
     *     currentId = 激活标识;  // 仅在 false→true 时触发一次 / trigger only once on false→true
     *     return true;
     * } else {
     *     currentId = null;
     * }
     * wasActive = nowActive;
     * return false;
     * </pre>
     *
     * <h3>event + 事件监听模板：/ Event + event listener template:</h3>
     * <pre>
     * Item prev = currentId;
     * if (isUsingItem() 状态转换 / state transition) {
     *     currentId = useItem;   // 主路径：持续使用型物品 / Main path: continuous-use items
     *     eventPending = false; // 清除事件标记，防重复 / Clear event flag to prevent duplicates
     * } else if (eventPending) {
     *     currentId = eventItem; // 兜底路径：瞬发型物品 / Fallback path: instant-use items
     *     eventPending = false;
     * } else {
     *     currentId = null;
     * }
     * return currentId != null &amp;&amp; !currentId.equals(prev); // 仅在触发时返回 true / return true only on trigger
     * </pre>
     */
    @Override
    public boolean tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            boolean changed = (currentId != null);
            currentId = null;
            // TODO: 重置所有追踪字段 / Reset all tracking fields
            return changed;
        }

        // TODO: 在此实现核心检测逻辑 / Implement core detection logic here
        // 示例（continuous 型）：/ Example (continuous type):
        // currentId = mc.player.getMainHandItem().getItem();
        return false; // TODO: 状态变化时返回 true / Return true on state change
    }

    /** 重置所有运行时状态。在配置重载时调用。 / Reset all runtime state. Called on config reload. */
    @Override
    public void reset() {
        currentId = null;
        // TODO: 重置所有追踪字段 / Reset all tracking fields
        // TODO: 如有 eventPending，也在此清空 / If there is eventPending, clear it here too
    }

    // ============================================================
    //  查询 — TriggerManager 通过这些方法获取触发器信息
    //  Query — TriggerManager gets trigger info through these methods
    // ============================================================

    /**
     * 获取当前激活的标识列表。
     * Get the list of currently active identifiers.
     *
     * <p><b>Item 型</b>（isItemBased=true）：返回物品实例。
     *   TriggerManager 用它在 {@code itemToRulesMap} 中查找规则。
     *   不同物品间互斥（切换物品时旧提示立即移除）。</p>
     * <p><b>Item type</b> (isItemBased=true): returns item instances.
     *   TriggerManager uses them to look up rules in {@code itemToRulesMap}.
     *   Items are mutually exclusive (switching items immediately removes old hints).</p>
     *
     * <p><b>String 型</b>（isItemBased=false）：返回维度 ID 等字符串。
     *   TriggerManager 用它在 {@code stringToRulesMap} 中查找规则。
     *   不参与物品互斥。</p>
     * <p><b>String type</b> (isItemBased=false): returns strings like dimension IDs.
     *   TriggerManager uses them to look up rules in {@code stringToRulesMap}.
     *   Not subject to item mutual exclusion.</p>
     *
     * @return 激活标识列表，无激活时返回 {@code List.of()} / List of active identifiers, {@code List.of()} when none
     */
    @Override
    public List<Object> getActiveTriggers() {
        if (currentId != null) {
            // Item 型：return List.of(currentItem); / Item type: return List.of(currentItem);
            // String 型：return List.of((Object) currentDimensionId); / String type: return List.of((Object) currentDimensionId);
            return List.of(currentId);
        }
        return List.of();
    }

    /**
     * 获取优先级。数值越大越优先。
     * Get priority. Higher values have higher priority.
     *
     * <p>用于 ANY 模式下多个触发器同时活跃时决定返回哪个规则的提示。
     * 参考值：on_dimension_change(30) > on_dimension(25) >
     *         on_low_health(20) > on_activate_block(15) >
     *         on_use(10) > hold_item(0)</p>
     * <p>Used in ANY mode when multiple triggers are active to decide which rule's hint to return.
     * Reference values: on_dimension_change(30) > on_dimension(25) >
     *         on_low_health(20) > on_activate_block(15) >
     *         on_use(10) > hold_item(0)</p>
     */
    @Override
    public int getPriority() {
        // TODO: 选择优先级，参考上面的数值范围 / Choose a priority, refer to the value range above
        return 5;
    }

    /**
     * 触发器类型标识 —— 对应 JSON 配置中的键名和
     * {@code triggerOn} / {@code dismissOn} 中使用的值。
     * Trigger type identifier — corresponds to the key name in JSON config
     * and the values used in {@code triggerOn} / {@code dismissOn}.
     */
    @Override
    public String getType() {
        // TODO: 返回 JSON 中的键名，如 "on_xxx" / Return the JSON key name, e.g. "on_xxx"
        return "template_trigger";
    }

    /** continuous=true 条件满足期间持续激活；event=false 仅触发那一刻 / continuous=true continuously active while condition holds; event=false only on the triggering tick */
    @Override
    public boolean isContinuous() {
        // TODO: continuous 型返回 true，event 型返回 false / Return true for continuous, false for event
        return false;
    }

    /**
     * 是否以 Item 作为激活标识。
     * Whether to use Item as the activation identifier.
     *
     * <p>true → 物品驱动型，同类提示互斥（切物品时旧提示立即移除）。</p>
     * <p>true → item-driven, same-type hints are mutually exclusive (old hint removed immediately on item switch).</p>
     * <p>false → 非物品驱动型，不参与互斥。</p>
     * <p>false → non-item-driven, not subject to mutual exclusion.</p>
     */
    @Override
    public boolean isItemBased() {
        // TODO: 返回 Item 标识 → true，返回 String 标识 → false / Returns Item identifier → true, returns String identifier → false
        return false;
    }

    /**
     * 父触发器类型（可选覆写，默认 null）。
     * Parent trigger type (optional override, default null).
     *
     * <p>子类型使用父类型的标识体系和索引。
     * 例如 {@code on_tool_low_durability} 依赖 {@code hold_item} 的物品索引。</p>
     * <p>Child type uses the parent type's identifier system and indexes.
     * For example, {@code on_tool_low_durability} depends on {@code hold_item}'s item index.</p>
     *
     * @return 父类型名，null 表示基础触发器 / Parent type name, null for base trigger
     */
    @Override
    public String getParentType() {
        // TODO: 如果是子类型，返回父类型名（如 "hold_item"）；否则不覆写 / If child type, return parent type name (e.g. "hold_item"); otherwise don't override
        return null; // HudTrigger.super.getParentType() — 默认 null / default null
    }
}
