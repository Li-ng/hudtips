package com.xiaogua.hudtips.client.trigger;

import com.xiaogua.hudtips.client.config.HudConfig;
import com.xiaogua.hudtips.client.config.TriggerConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

import java.util.List;

/**
 * 维度状态触发器 [continuous]。
 * Dimension state trigger [continuous].
 *
 * <p>持续报告玩家当前所在维度 ID，用于与其他触发器组合成 AND 条件。
 * 与 {@link OnDimensionChangeTrigger}（event 型）不同，此触发器<b>始终</b>反映当前维度状态。</p>
 * <p>Continuously reports the player's current dimension ID, used for combining with other
 * triggers into AND conditions. Unlike {@link OnDimensionChangeTrigger} (event type),
 * this trigger <b>always</b> reflects the current dimension state.</p>
 *
 * <h2>分类 / Classification</h2>
 * <table border="1">
 *   <tr><th>维度 / Dimension</th><th>值 / Value</th></tr>
 *   <tr><td>类型 / Type</td><td><b>continuous</b></td></tr>
 *   <tr><td>性能等级 / Performance</td><td><b>中量 / Medium</b> — 需访问 Level 数据 / Needs access to Level data</td></tr>
 *   <tr><td>实现途径 / Approach</td><td>tick 比较 {@code ClientLevel} 引用（维度切换时 MC 创建新 Level 实例） / tick compares {@code ClientLevel} references (MC creates a new Level instance on dimension change)</td></tr>
 *   <tr><td>缓存策略 / Caching</td><td>惰性 — 仅在 Level 引用变化时更新 / Lazy — only updates when Level reference changes</td></tr>
 *   <tr><td>索引方式 / Index</td><td>{@code Map<String, List<HintRule>>}</td></tr>
 *   <tr><td>互斥关系 / Exclusivity</td><td>同类三值互斥（overworld / the_nether / the_end） / Three-value mutual exclusion within same type (overworld / the_nether / the_end)</td></tr>
 * </table>
 *
 * <h2>设计决策 / Design Decision</h2>
 * <p>不使用 {@code PlayerChangedDimensionEvent} 事件订阅，理由：</p>
 * <p>Does not subscribe to {@code PlayerChangedDimensionEvent}, for these reasons:</p>
 * <ol>
 *   <li>此触发器是 continuous 型，状态应持续而非瞬间——用引用比较更自然 / This trigger is continuous type; state should persist rather than be instantaneous — reference comparison is more natural</li>
 *   <li>{@code ClientLevel} 引用比较是 O(1) 操作，零开销 / {@code ClientLevel} reference comparison is an O(1) operation with zero overhead</li>
 *   <li>维度 ID 字符串解析仅在 Level 变化时发生一次 / Dimension ID string parsing only happens once when Level changes</li>
 * </ol>
 *
 * <h2>JSON 示例 / JSON Example</h2>
 * <pre>
 * "on_dimension": {
 *   "rules": [
 *     {
 *       "items": ["minecraft:bed"],
 *       "text": "💥 在下界使用床会爆炸！",
 *       "triggerOn": { "hold_item": true, "on_dimension": "minecraft:the_nether" },
 *       "triggerOnMode": "all"
 *     }
 *   ]
 * }
 * </pre>
 *
 * @see OnDimensionChangeTrigger  event 型版本（切换时触发一次） / event-type version (triggers once on change)
 * @see HudTrigger
 * @see HudConfig#TRIGGER_ON_DIMENSION
 */
public class OnDimensionTrigger implements HudTrigger {

    /** 当前维度的 ID 字符串（如 "minecraft:the_nether"） / Current dimension ID string (e.g. "minecraft:the_nether") */
    private String currentDimensionId = null;

    /** 上一次的 ClientLevel 引用，用于快速检测维度切换 / Previous ClientLevel reference, used for fast dimension change detection */
    private ClientLevel lastLevel = null;

    // ============================================================
    //  生命周期
    //  Lifecycle
    // ============================================================

    @Override
    public void init(TriggerConfig triggerConfig, HudConfig globalConfig) {
        reset();
    }

    @Override
    public boolean tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.level() == null) {
            boolean changed = (currentDimensionId != null);
            currentDimensionId = null;
            lastLevel = null;
            return changed;
        }

        // ClientLevel 引用比较：维度切换时 MC 创建新 Level 实例
        // ClientLevel reference comparison: MC creates a new Level instance on dimension change
        ClientLevel currentLevel = (ClientLevel) mc.player.level();
        if (currentLevel != lastLevel) {
            currentDimensionId = currentLevel.dimension().identifier().toString();
            lastLevel = currentLevel;
            return true; // 维度发生变化 / Dimension changed
        }
        // 未变化 → 保持 currentDimensionId 不变（continuous 特性）
        // No change → keep currentDimensionId unchanged (continuous characteristic)
        return false;
    }

    @Override
    public void reset() {
        currentDimensionId = null;
        lastLevel = null;
    }

    // ============================================================
    //  查询
    //  Query
    // ============================================================

    @Override
    public List<Object> getActiveTriggers() {
        if (currentDimensionId != null) {
            return List.of((Object) currentDimensionId);
        }
        return List.of();
    }

    /** 优先级 25 — 高于 on_low_health(20)，维度信息优先于血量警告。 / Priority 25 — higher than on_low_health(20); dimension info takes priority over health warnings. */
    @Override
    public int getPriority() {
        return 25;
    }

    /** 触发器类型标识 —— 对应 JSON 中 {@code "on_dimension"} 键。 / Trigger type identifier — corresponds to the {@code "on_dimension"} key in JSON. */
    @Override
    public String getType() {
        return HudConfig.TRIGGER_ON_DIMENSION;
    }

    /** continuous — 持续反映当前维度状态，主要用于 AND 组合条件。 / continuous — continuously reflects the current dimension state, primarily used for AND combination conditions. */
    @Override
    public boolean isContinuous() {
        return true;
    }
}
