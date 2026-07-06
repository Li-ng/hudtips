package com.xiaogua.hudtips.client.trigger;

import com.xiaogua.hudtips.client.config.HintRule;
import com.xiaogua.hudtips.client.config.HudConfig;
import com.xiaogua.hudtips.client.config.TriggerConfig;

import java.util.List;

/**
 * HUD 触发器基础接口。
 * Base interface for HUD triggers.
 *
 * <p>每个触发器代表一种提示触发条件。触发器负责检测游戏状态，
 * 并返回满足条件的"激活标识"供 {@link TriggerManager} 匹配规则。</p>
 * <p>Each trigger represents a hint trigger condition. Triggers detect game state
 * and return "active identifiers" that satisfy the condition for {@link TriggerManager} to match rules.</p>
 *
 * <h2>触发器分类 / Trigger Categories</h2>
 * <table border="1">
 *   <tr><th>类型 / Type</th><th>行为 / Behavior</th><th>隐式消失 / Implicit Dismiss</th><th>示例 / Example</th></tr>
 *   <tr><td>{@link #isContinuous() continuous}</td>
 *       <td>条件满足期间持续激活 / Continuously active while condition holds</td>
 *       <td>条件不再满足时自动消失 / Auto-dismissed when condition no longer holds</td>
 *       <td>hold_item, on_low_health, on_dimension</td></tr>
 *   <tr><td>event（非 continuous） / event (non-continuous)</td>
 *       <td>仅在条件触发的那一 tick 激活 / Active only on the tick the condition triggers</td>
 *       <td>由 time:/tick: 控制消失 / Dismiss controlled by time:/tick:</td>
 *       <td>on_use, on_dimension_change</td></tr>
 * </table>
 *
 * <h2>生命周期 / Lifecycle</h2>
 * <pre>
 * init(triggerConfig, globalConfig)  →  初始化配置 / Initialize config
 * tick()                             →  每刻更新，检测条件 / Per-tick update, detect conditions
 * getActiveTriggers()                →  获取当前激活的标识列表 / Get currently active identifier list
 * reset()                            →  重置状态 / Reset state
 * </pre>
 *
 * <h2>父子关系 / Parent-Child Relationship</h2>
 * <p>子触发器通过 {@link #getParentType()} 声明依赖的父类型。
 * 子类型使用父类型的标识体系和索引（如 {@code on_tool_low_durability} 依赖
 * {@code hold_item} 的 {@code itemToRulesMap}）。
 * 当子类型被 JSON 引用时，父类型自动启用。</p>
 * <p>Child triggers declare their parent type via {@link #getParentType()}.
 * The child uses the parent's identifier system and indexes (e.g. {@code on_tool_low_durability}
 * depends on {@code hold_item}'s {@code itemToRulesMap}).
 * When a child type is referenced by JSON, the parent type is auto-enabled.</p>
 *
 * <h2>优先级 / Priority</h2>
 * <p>数值越大越优先：/ Higher values have higher priority:</p>
 * <pre>
 * on_dimension_change (30)  &gt;  on_dimension (25)  &gt;
 * on_low_health       (20)  &gt;  on_look_entity (17) &gt;
 * on_look_block       (16)  &gt;  on_activate_block (15) &gt;
 * on_use / on_kill    (10)  &gt;  hold_item           ( 0)
 * </pre>
 *
 * <h2>已有实现参考 / Existing Implementations</h2>
 * <table border="1">
 *   <tr><th>触发器 / Trigger</th><th>类型 / Type</th><th>isContinuous</th><th>isItemBased</th><th>标识 / Identifier</th><th>优先级 / Priority</th></tr>
 *   <tr><td>HoldItemTrigger</td><td>continuous</td><td>true</td><td>true</td><td>Item</td><td>0</td></tr>
 *   <tr><td>OnUseTrigger</td><td>event</td><td>false</td><td>true</td><td>Item</td><td>10</td></tr>
 *   <tr><td>OnKillTrigger</td><td>event</td><td>false</td><td>false</td><td>String</td><td>10</td></tr>
 *   <tr><td>OnActivateBlockTrigger</td><td>event</td><td>false</td><td>true</td><td>Item</td><td>15</td></tr>
 *   <tr><td>OnLookBlockTrigger</td><td>continuous</td><td>true</td><td>false</td><td>String</td><td>16</td></tr>
 *   <tr><td>OnLookEntityTrigger</td><td>continuous</td><td>true</td><td>false</td><td>String</td><td>17</td></tr>
 *   <tr><td>OnLowHealthTrigger</td><td>continuous</td><td>true</td><td>false</td><td>String</td><td>20</td></tr>
 *   <tr><td>OnDimensionTrigger</td><td>continuous</td><td>true</td><td>false</td><td>String</td><td>25</td></tr>
 *   <tr><td>OnDimensionChangeTrigger</td><td>event</td><td>false</td><td>false</td><td>String</td><td>30</td></tr>
 * </table>
 *
 * @see TriggerManager
 * @see HintRule
 * @see TriggerTemplate 新建触发器时的完整参考模板 / Complete reference template for creating new triggers
 */
public interface HudTrigger {

    /**
     * 初始化触发器。
     * Initialize the trigger.
     *
     * <p>在配置加载或重载时调用。可在此读取触发器级别的特殊配置
     * （如 {@code on_low_health} 的 {@code healthThreshold}）。</p>
     * <p>Called when configs are loaded or reloaded. Read trigger-level
     * special config here (e.g. {@code on_low_health}'s {@code healthThreshold}).</p>
     *
     * @param triggerConfig 触发器配置（含默认值和 settings），可能为 null / Trigger config (with defaults and settings), may be null
     * @param globalConfig  全局配置，可能为 null / Global config, may be null
     */
    void init(TriggerConfig triggerConfig, HudConfig globalConfig);

    /**
     * 每刻更新。
     * Per-tick update.
     *
     * <p>由 {@link TriggerManager#tick()} 驱动。在此检测触发条件
     * （如物品、血量、维度），更新内部激活状态。</p>
     * <p>Driven by {@link TriggerManager#tick()}. Detect trigger conditions here
     * (e.g. item, health, dimension) and update internal activation state.</p>
     *
     * @return true 表示本 tick 激活状态发生变化，需要 TriggerManager 重新匹配规则；
     *         false 表示状态与上一 tick 相同，可跳过匹配步骤以节省性能
     *         / true if activation state changed this tick and TriggerManager should re-match rules;
     *         false if state is unchanged from previous tick so matching can be skipped for performance
     */
    boolean tick();

    /**
     * 获取当前激活的标识列表。
     * Get the list of currently active identifiers.
     *
     * <p>返回值类型取决于触发器：/ Return value type depends on the trigger:</p>
     * <ul>
     *   <li>{@code hold_item}         → 手持的 {@code Item} / held {@code Item}</li>
     *   <li>{@code on_use}           → 正在使用的 {@code Item} / {@code Item} being used</li>
     *   <li>{@code on_activate_block}  → 右键方块时手持的 {@code Item} / {@code Item} held when right-clicking a block</li>
     *   <li>{@code on_low_health}    → 固定字符串 {@code "on_low_health"} / fixed string</li>
     *   <li>{@code on_dimension}     → 维度 ID 字符串 / dimension ID string</li>
     *   <li>{@code on_dimension_change} → 新维度 ID 字符串 / new dimension ID string</li>
     * </ul>
     *
     * @return 激活标识列表，无激活时返回空列表（非 null） / List of active identifiers, empty list when none (non-null)
     */
    List<Object> getActiveTriggers();

    /**
     * 重置触发器状态。
     * Reset trigger state.
     *
     * <p>清除所有活跃状态和缓存，使下一刻重新检测。</p>
     * <p>Clear all active state and caches so the next tick re-detects from scratch.</p>
     */
    void reset();

    /**
     * 获取优先级。
     * Get priority.
     *
     * <p>数值越大越优先。用于 ANY 模式下多个触发器同时活跃时
     * 决定返回哪个规则的提示。</p>
     * <p>Higher values have higher priority. Used in ANY mode when multiple
     * triggers are active simultaneously to decide which rule's hint to show.</p>
     *
     * @return 优先级数值 / Priority value
     */
    int getPriority();

    /**
     * 获取触发器类型标识。
     * Get the trigger type identifier.
     *
     * <p>对应 JSON 配置中的触发器键名和
     * {@link HintRule#triggerOn} / {@link HintRule#dismissOn} 中使用的值。</p>
     * <p>Corresponds to the trigger key name in JSON config and the values
     * used in {@link HintRule#triggerOn} / {@link HintRule#dismissOn}.</p>
     *
     * @return 触发器类型字符串 / Trigger type string
     */
    String getType();

    /**
     * 是否为持续型触发器。
     * Whether this is a continuous trigger.
     *
     * <p>持续型触发器在条件满足期间<b>持续</b>激活，
     * 条件不再满足时 {@link TriggerManager} 会自动让对应提示消失。
     * 事件型触发器仅在触发的那一 tick 激活，消失由 time:/tick: 控制。</p>
     * <p>Continuous triggers remain <b>continuously</b> active while the condition holds;
     * when the condition no longer holds, {@link TriggerManager} auto-dismisses the hint.
     * Event triggers are only active on the triggering tick; dismissal is controlled by time:/tick:.</p>
     *
     * @return true 表示持续型，false 表示事件型（默认） / true for continuous, false for event (default)
     */
    default boolean isContinuous() {
        return false;
    }

    /**
     * 是否以 Item 作为激活标识。
     * Whether this trigger uses Item as its activation identifier.
     *
     * <p>Item 型触发器之间互斥：切换物品时旧提示立即消失（不等渐出）。
     * 非 Item 型触发器（返回 String 等）不受此限制。</p>
     * <p>Item-based triggers are mutually exclusive: switching items immediately
     * dismisses the old hint (without waiting for fade-out).
     * Non-item triggers (returning String etc.) are not subject to this restriction.</p>
     *
     * @return true 表示该触发器返回 Item 标识（默认 false） / true if this trigger returns Item identifiers (default false)
     */
    default boolean isItemBased() {
        return false;
    }

    /**
     * 父触发器类型。
     * Parent trigger type.
     *
     * <p>子触发器依赖父触发器的标识类型和索引体系。
     * 例如 {@code on_tool_low_durability} 返回 {@code Item} 作为标识，
     * 和 {@code hold_item} 共用 {@code itemToRulesMap} 索引，
     * 因此 {@code hold_item} 是其父类型。</p>
     * <p>Child triggers depend on the parent's identifier type and index system.
     * For example, {@code on_tool_low_durability} returns {@code Item} as its identifier
     * and shares the {@code itemToRulesMap} index with {@code hold_item},
     * making {@code hold_item} its parent type.</p>
     *
     * <p>当子类型被使用时，父类型自动启用（即使配置文件里没有父类型的规则）。
     * 反之，父类型被使用时，子类型可以单独禁用。</p>
     * <p>When a child type is used, the parent type is auto-enabled (even if no rules
     * reference the parent in config). Conversely, when the parent is used, the child
     * type can be individually disabled.</p>
     *
     * @return 父触发器类型名，null 表示基础触发器（无父类型，默认） / Parent trigger type name, null for base triggers (no parent, default)
     */
    default String getParentType() {
        return null;
    }
}
