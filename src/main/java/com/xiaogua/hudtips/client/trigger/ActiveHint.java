package com.xiaogua.hudtips.client.trigger;

import com.xiaogua.hudtips.client.TextAnimationManager;
import com.xiaogua.hudtips.client.config.HintRule;
import com.xiaogua.hudtips.client.trigger.dismiss.DismissConditions;

/**
 * 单条活跃提示 —— 将配置规则与运行时状态绑定。
 * A single active hint — binds a config rule with runtime state.
 *
 * <p>这是提示系统的<b>运行时实例</b>。当一条 {@link HintRule} 被触发后，
 * TriggerManager 创建 ActiveHint 并将它加入活跃队列。每条实例拥有独立的状态：</p>
 * <p>This is the <b>runtime instance</b> of the hint system. When a {@link HintRule} is triggered,
 * TriggerManager creates an ActiveHint and adds it to the active queue. Each instance has independent state:</p>
 *
 * <h2>持有的运行时状态 / Runtime State Held</h2>
 * <table border="1">
 *   <tr><th>组件 / Component</th><th>职责 / Responsibility</th><th>生命周期 / Lifecycle</th></tr>
 *   <tr><td>{@link #rule}</td><td>配置规则（文本、颜色、位置） / Config rule (text, color, position)</td><td>不可变 / Immutable</td></tr>
 *   <tr><td>{@link #animation}</td><td>逐字打印 / 渐出 / 庆祝动画 / Typewriter / fade-out / celebrate animation</td>
 *       <td>ENTERING → SHOWING → (CELEBRATING) → EXITING → 移除 / removed</td></tr>
 *   <tr><td>{@link #dismissConditions}</td><td>从 dismissOn 解析的关闭条件 / Dismiss conditions parsed from dismissOn</td><td>不可变 / Immutable</td></tr>
 *   <tr><td>{@link #tickDismissCount}</td><td>已流逝的 tick 数（tick: 语法用） / Elapsed tick count (for tick: syntax)</td><td>每 tick 自增 / increments per tick</td></tr>
 *   <tr><td>{@link #timeDismissStart}</td><td>计时起点（time: 语法用） / Timing start point (for time: syntax)</td><td>构造时记录 / recorded at construction</td></tr>
 *   <tr><td>{@link #addOrder}</td><td>全局插入序号 / Global insertion sequence number</td><td>构造时分配，不可变 / assigned at construction, immutable</td></tr>
 * </table>
 *
 * <h2>与其他组件的关系 / Relationship with Other Components</h2>
 * <pre>
 * TriggerManager
 *     │  创建 ActiveHint(rule, dismissConditions) / creates ActiveHint(rule, dismissConditions)
 *     │  加入 activeHints 队列 / adds to activeHints queue
 *     │  每 tick 调用 hint.tick() / calls hint.tick() each tick
 *     ▼
 * ActiveHint
 *     ├── animation.tick()    → 推进逐字打印 / 渐出 / advances typewriter / fade-out
 *     ├── tickDismissCount++  → 递增 tick 计数器 / increments tick counter
 *     └── isTimeOrTickExpired() → TriggerManager 查询是否超时 / TriggerManager queries for timeout
 *           │
 *           ▼
 *     HintManager.toRenderData() → HintRenderData → ClientHudRenderer
 * </pre>
 *
 * <h2>队列位置 / Queue Position</h2>
 * <p>多个 ActiveHint 可同时存在（最多 3 条，由 TriggerManager 控制），
 * 它们完全独立：一条可以正在逐字打印，另一条同时在渐出，第三条在庆祝。</p>
 * <p>Multiple ActiveHints can coexist (max 3, controlled by TriggerManager).
 * They are fully independent: one can be typing out while another fades and a third celebrates.</p>
 *
 * <h2>插入序号（addOrder）/ Insertion Sequence Number (addOrder)</h2>
 * <p>{@link #addOrder} 是全局单调递增序号，在构造函数中自动分配。
 * 用于队列满时找到最早加入的提示。（注意：现在驱逐策略已改为优先级驱逐，
 * addOrder 保留供未来调试/日志使用。）</p>
 * <p>{@link #addOrder} is a globally monotonic sequence number auto-assigned in the constructor.
 * Used to find the earliest-added hint when the queue is full. (Note: the eviction strategy
 * has been changed to priority-based eviction; addOrder is kept for future debug/logging use.)</p>
 *
 * @see TriggerManager
 * @see TextAnimationManager
 * @see DismissConditions
 */
public class ActiveHint {

    /** 全局插入序号计数器，每次构造新实例时自增 / Global insertion sequence counter, auto-increments per new instance */
    private static int globalOrder = 0;

    /** 关联的配置规则（包含文本、颜色、位置、触发条件等全部字段） / Associated config rule (contains all fields: text, color, position, trigger conditions, etc.) */
    public final HintRule rule;

    /**
     * 全局插入序号，值越小表示加入越早。
     * Global insertion sequence number; smaller values indicate earlier insertion.
     * <p>保留字段：早期版本用于 FIFO 驱逐，当前驱逐策略已改为优先级驱逐，
     * 但保留此字段供日志/调试/未来可能的回退使用。</p>
     * <p>Reserved field: earlier versions used it for FIFO eviction; the current eviction
     * strategy has changed to priority-based, but this field is kept for logging/debug/future rollback.</p>
     */
    public final int addOrder = globalOrder++;

    /** 独立的逐字打印 / 渐出 / 庆祝动画状态 / Independent typewriter / fade-out / celebrate animation state */
    public final TextAnimationManager animation;

    /** 提示首次出现的时间戳（毫秒），调试用 / Timestamp (ms) when the hint first appeared, for debugging */
    public final long showTime;

    /** 从规则 dismissOn 解析的关闭条件（time:/tick:/事件触发器） / Dismiss conditions parsed from the rule's dismissOn (time:/tick:/event triggers) */
    public final DismissConditions dismissConditions;

    /**
     * 时间关闭的计时起点（毫秒）。
     * Timing start point for time-based dismiss (milliseconds).
     * <p>构造时设为当前时间。当 {@code System.currentTimeMillis() - timeDismissStart >= dismissConditions.timeMs}
     * 时，{@link #isTimeOrTickExpired()} 返回 true。
     * 基于真实时间，不受游戏 TPS 波动影响。</p>
     * <p>Set to current time at construction. When {@code System.currentTimeMillis() - timeDismissStart >= dismissConditions.timeMs},
     * {@link #isTimeOrTickExpired()} returns true.
     * Based on real time, unaffected by game TPS fluctuations.</p>
     */
    long timeDismissStart;

    /**
     * 游戏刻关闭的计数器。
     * Tick counter for tick-based dismiss.
     * <p>每 tick 自增一次。当 {@code tickDismissCount >= dismissConditions.tickCount}
     * 时，{@link #isTimeOrTickExpired()} 返回 true。</p>
     * <p>Increments once per tick. When {@code tickDismissCount >= dismissConditions.tickCount},
     * {@link #isTimeOrTickExpired()} returns true.</p>
     */
    int tickDismissCount;

    /**
     * 创建活跃提示实例，立即开始逐字打印动画。
     * Create an active hint instance and immediately start the typewriter animation.
     *
     * <p>构造时自动：/ Automatically at construction:</p>
     * <ul>
     *   <li>分配全局递增的 {@link #addOrder} / Assign globally incrementing {@link #addOrder}</li>
     *   <li>创建独立的 {@link TextAnimationManager} 并进入 ENTERING 阶段 / Create independent {@link TextAnimationManager} and enter ENTERING phase</li>
     *   <li>记录 {@link #showTime} 和 {@link #timeDismissStart} / Record {@link #showTime} and {@link #timeDismissStart}</li>
     * </ul>
     *
     * @param rule 配置规则（不可变引用） / Config rule (immutable reference)
     * @param dc   解析后的关闭条件（不可变） / Parsed dismiss conditions (immutable)
     */
    public ActiveHint(HintRule rule, DismissConditions dc) {
        this.rule = rule;
        this.animation = new TextAnimationManager(rule.getDisplayText());
        this.showTime = System.currentTimeMillis();
        this.dismissConditions = dc;
        this.timeDismissStart = System.currentTimeMillis();
    }

    /**
     * 检查时间/tick 关闭条件是否满足。
     * Check whether time/tick dismiss conditions are met.
     *
     * <p>两种计时方式为 <b>OR</b> 关系：任一满足即返回 true。</p>
     * <p>The two timing methods are <b>OR</b>'d: returns true if either is satisfied.</p>
     *
     * <h3>时间计时（time:）/ Time-based (time:)</h3>
     * <p>基于 {@link System#currentTimeMillis()} 的真实时间——
     * 即使游戏卡顿/TPS 下降，提示也会在配置的毫秒数后消失。
     * 适合短提示（如"3秒后消失"）。</p>
     * <p>Based on real time from {@link System#currentTimeMillis()} —
     * even if the game lags or TPS drops, the hint will disappear after the configured
     * milliseconds. Suitable for short hints (e.g. "disappear after 3 seconds").</p>
     *
     * <h3>tick 计时（tick:）/ Tick-based (tick:)</h3>
     * <p>基于游戏刻的流逝——如果游戏卡顿，提示会停留更久。
     * 适合需要与游戏逻辑同步的场景。</p>
     * <p>Based on game tick passage — if the game lags, the hint stays longer.
     * Suitable for scenarios that need to stay in sync with game logic.</p>
     *
     * <p><b>重要</b>：此方法不检查动画阶段。
     * TriggerManager 会确保仅在 ENTERING/SHOWING 阶段调用它——
     * ENTERING 期间（逐字打印未完成）的调用会被跳过，
     * 等文字显示完整后才开始计时。见 {@code TriggerManager.tick()} 步骤 2d。</p>
     * <p><b>Important</b>: this method does not check the animation phase.
     * TriggerManager ensures it is only called during ENTERING/SHOWING —
     * calls during ENTERING (before typewriter completes) are skipped,
     * so timing only starts after the full text is displayed. See {@code TriggerManager.tick()} step 2d.</p>
     *
     * @return true 如果任一计时条件满足 / true if either timing condition is met
     */
    public boolean isTimeOrTickExpired() {
        if (dismissConditions.timeMs > 0) {
            if (System.currentTimeMillis() - timeDismissStart >= dismissConditions.timeMs) {
                return true;
            }
        }
        if (dismissConditions.tickCount > 0) {
            if (tickDismissCount >= dismissConditions.tickCount) {
                return true;
            }
        }
        return false;
    }

    /**
     * 每游戏刻调用：推进动画帧 + 递增 tick 计数。
     * Called per game tick: advances animation frame + increments tick count.
     *
     * <p>由 {@link TriggerManager#tick()} 步骤 2 调用。
     * 动画推进包括：逐字打印字符出现、庆祝进度更新、渐出 alpha 递减。
     * tick 计数器用于 {@code dismissOn: ["tick:N"]} 语法的倒计时。</p>
     * <p>Called by {@link TriggerManager#tick()} step 2.
     * Animation advancement includes: typewriter character reveal, celebrate progress update,
     * fade-out alpha decrement.
     * The tick counter is used for {@code dismissOn: ["tick:N"]} countdown.</p>
     */
    void tick() {
        animation.tick();
        tickDismissCount++;
    }
}
