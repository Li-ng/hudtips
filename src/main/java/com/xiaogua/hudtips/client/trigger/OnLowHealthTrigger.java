package com.xiaogua.hudtips.client.trigger;

import com.xiaogua.hudtips.HUDTips;
import com.xiaogua.hudtips.client.config.HudConfig;
import com.xiaogua.hudtips.client.config.TriggerConfig;
import net.minecraft.client.Minecraft;

import java.util.List;

/**
 * 低血量触发器 [continuous]。
 * Low-health trigger [continuous].
 *
 * <p>当玩家血量低于阈值时持续激活，与手持物品无关。
 * 血量恢复到阈值以上时自动消失。</p>
 * <p>Continuously active when the player's health falls below the threshold,
 * independent of the held item. Automatically deactivates when health recovers above the threshold.</p>
 *
 * <p>这是一个<b>独立触发器</b>——它不依赖物品、维度等外部条件，
 * 仅根据玩家血量比例判断是否激活。</p>
 * <p>This is an <b>independent trigger</b> — it does not depend on items, dimensions,
 * or other external conditions; it determines activation solely based on the player's health ratio.</p>
 *
 * <h2>分类 / Classification</h2>
 * <table border="1">
 *   <tr><th>维度 / Dimension</th><th>值 / Value</th></tr>
 *   <tr><td>类型 / Type</td><td><b>continuous</b></td></tr>
 *   <tr><td>性能等级 / Performance</td><td><b>轻量 / Lightweight</b> — 仅读 Player 字段 / Only reads Player fields</td></tr>
 *   <tr><td>实现途径 / Approach</td><td>每 tick 读 {@code getHealth() / getMaxHealth()} / Read {@code getHealth() / getMaxHealth()} per tick</td></tr>
 *   <tr><td>缓存策略 / Caching</td><td>无（血量每帧可能变化） / None (health may change every frame)</td></tr>
 *   <tr><td>索引方式 / Index</td><td>{@code Map<String, List<HintRule>>}（stringToRulesMap），key 为 {@code "on_low_health"} / {@code Map<String, List<HintRule>>} (stringToRulesMap), key is {@code "on_low_health"}</td></tr>
 *   <tr><td>互斥关系 / Exclusivity</td><td>无 / None</td></tr>
 * </table>
 *
 * <h2>触发器特有设置 / Trigger-specific Settings</h2>
 * <ul>
 *   <li>{@code healthThreshold} — 血量阈值比例 (0.0~1.0)，默认 0.3（30%） / Health threshold ratio (0.0~1.0), default 0.3 (30%)</li>
 * </ul>
 *
 * <h2>JSON 示例 / JSON Example</h2>
 * <pre>
 * "on_low_health": {
 *   "settings": { "healthThreshold": 0.3 },
 *   "rules": [
 *     { "text": "❤️ 血量低！注意安全" },
 *     { "text": "❤️ 危险！建议使用金苹果回血",
 *       "items": ["minecraft:golden_apple"],
 *       "dismissOn": ["time:5000"] }
 *   ]
 * }
 * </pre>
 *
 * <h2>与其他触发器组合 / Combining with Other Triggers</h2>
 * <p>可通过 {@code triggerOnMode: "all"} 与 {@code hold_item} 组合，
 * 实现「低血量 + 手持特定物品」的双条件提示：</p>
 * <p>Can combine with {@code hold_item} via {@code triggerOnMode: "all"}
 * to achieve a dual-condition hint of "low health + holding a specific item":</p>
 * <pre>
 * {
 *   "items": ["minecraft:golden_apple"],
 *   "text": "❤️ 低血量！右键食用金苹果回血",
 *   "triggerOn": ["hold_item", "on_low_health"],
 *   "triggerOnMode": "all"
 * }
 * </pre>
 *
 * @see HudTrigger
 * @see HudConfig#TRIGGER_ON_LOW_HEALTH
 */
public class OnLowHealthTrigger implements HudTrigger {

    /** 血量阈值，默认 0.3（30%） / Health threshold, default 0.3 (30%) */
    private float healthThreshold = 0.3f;

    /** 当前是否激活（血量低于阈值），与手持物品无关 / Whether currently active (health below threshold), independent of held item */
    private boolean isActive = false;

    // ============================================================
    //  生命周期
    //  Lifecycle
    // ============================================================

    @Override
    public void init(TriggerConfig triggerConfig, HudConfig globalConfig) {
        if (triggerConfig != null && triggerConfig.settings != null) {
            Object threshold = triggerConfig.settings.get("healthThreshold");
            if (threshold instanceof Number num) {
                healthThreshold = num.floatValue();
            } else if (threshold != null) {
                HUDTips.LOGGER.warn(
                    "on_low_health.healthThreshold 期望数字类型，实际为 {}，使用默认值 {}",
                    threshold.getClass().getSimpleName(), healthThreshold);
            }
        }
        reset();
    }

    @Override
    public boolean tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            boolean changed = isActive;
            isActive = false;
            return changed;
        }

        float health = mc.player.getHealth();
        float maxHealth = mc.player.getMaxHealth();

        boolean wasActive = isActive;
        isActive = (maxHealth > 0 && health / maxHealth < healthThreshold);

        // 仅在激活状态发生变化时返回 true（低于阈值 ↔ 恢复正常）
        // Only return true when activation state changes (below threshold ↔ recovered)
        return wasActive != isActive;
    }

    @Override
    public void reset() {
        isActive = false;
    }

    // ============================================================
    //  查询
    //  Query
    // ============================================================

    @Override
    public List<Object> getActiveTriggers() {
        if (isActive) {
            return List.of((Object) getType());
        }
        return List.of();
    }

    /** 优先级 20 — 高于 on_use(10)，血量警告优先于操作提示。 / Priority 20 — higher than on_use(10); health warnings take priority over action hints. */
    @Override
    public int getPriority() {
        return 20;
    }

    /** 触发器类型标识 —— 对应 JSON 中 {@code "on_low_health"} 键。 / Trigger type identifier — corresponds to the {@code "on_low_health"} key in JSON. */
    @Override
    public String getType() {
        return HudConfig.TRIGGER_ON_LOW_HEALTH;
    }

    /** continuous — 血量低于阈值期间持续激活，恢复后自动消失。 / continuous — continuously active while health is below threshold; auto-dismisses on recovery. */
    @Override
    public boolean isContinuous() {
        return true;
    }

    /**
     * 不覆写 isItemBased()（默认 false）。
     * Does not override isItemBased() (default false).
     *
     * <p>此触发器是独立的血量检测，与物品无关。
     * 返回字符串标识 {@code "on_low_health"}，通过 {@code stringToRulesMap} 匹配规则。</p>
     * <p>This trigger is an independent health check, unrelated to items.
     * Returns the string identifier {@code "on_low_health"} and matches rules via {@code stringToRulesMap}.</p>
     *
     * <p>如果需要「低血量 + 特定物品」的双条件，请使用 AND 模式：</p>
     * <p>If you need the dual condition "low health + specific item", use AND mode:</p>
     * <pre>
     * triggerOn: ["hold_item", "on_low_health"], triggerOnMode: "all"
     * </pre>
     */
}
