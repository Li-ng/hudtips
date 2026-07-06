package com.xiaogua.hudtips.client.trigger;

import com.xiaogua.hudtips.client.config.HudConfig;
import com.xiaogua.hudtips.client.config.TriggerConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Item;

import java.util.List;

/**
 * 持有物品触发器 [continuous]。
 * Hold item trigger [continuous].
 *
 * <p>当玩家主手持有物品时持续激活，返回该物品作为匹配标识。</p>
 * <p>Continuously active when the player holds an item in the main hand; returns that item as the match identifier.</p>
 *
 * <h2>分类 / Classification</h2>
 * <table border="1">
 *   <tr><th>维度 / Dimension</th><th>值 / Value</th></tr>
 *   <tr><td>类型 / Type</td><td><b>continuous</b></td></tr>
 *   <tr><td>性能等级 / Cost</td><td><b>轻量 / Lightweight</b> — 仅读 Player 字段 / only reads Player field</td></tr>
 *   <tr><td>实现途径 / Implementation</td><td>每 tick 读 {@code getMainHandItem()} / reads {@code getMainHandItem()} each tick</td></tr>
 *   <tr><td>缓存策略 / Caching</td><td>无（每帧刷新）/ none (refreshed each frame)</td></tr>
 *   <tr><td>索引方式 / Index</td><td>{@code Map<Item, List<HintRule>>}</td></tr>
 *   <tr><td>互斥关系 / Exclusion</td><td>无 / none</td></tr>
 * </table>
 *
 * <h2>关闭逻辑 / Dismiss Logic</h2>
 * <p>切换物品后不再匹配 → TriggerManager Layer 3 判断条件不满足 → 自动渐出。</p>
 * <p>After switching items no longer matches → TriggerManager Layer 3 detects condition failure → auto fade-out.</p>
 *
 * <h2>JSON 示例 / JSON Example</h2>
 * <pre>
 * "hold_item": {
 *   "rules": [
 *     { "items": ["minecraft:diamond_sword"], "text": "💡 按 Shift+右键 释放剑气" }
 *   ]
 * }
 * </pre>
 *
 * @see HudTrigger
 * @see HudConfig#TRIGGER_HOLD_ITEM
 */
public class HoldItemTrigger implements HudTrigger {

    /** 当前手持物品，null 表示空手 / Currently held item, null = empty hand */
    private Item currentItem = null;

    // ============================================================
    //  生命周期
    //  Lifecycle
    // ============================================================

    @Override
    public void init(TriggerConfig triggerConfig, HudConfig globalConfig) {
        reset();
    }

    /**
     * 每 tick 比较主手物品是否变化。
     * Each tick, checks if the main hand item has changed.
     *
     * <p>返回 true 表示物品发生变化（包括空手↔物品切换），
     * 触发 TriggerManager 步骤 3 重新匹配规则。
     * 物品不变时返回 false，跳过匹配以节省性能。</p>
     * <p>Returns true when the item changes (including empty hand ↔ item switch),
     * triggering TriggerManager step 3 to re-match rules.
     * Returns false when unchanged, skipping match for performance.</p>
     *
     * <p><b>空手处理 / Empty hand handling</b>：空手时 {@code currentItem = AIR}（非 null），
     * 确保切换到空手也能触发一次状态变化。</p>
     * <p>Empty hand sets {@code currentItem = AIR} (non-null),
     * ensuring switching to empty hand also triggers a state change.</p>
     */
    @Override
    public boolean tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            boolean changed = (currentItem != null);
            currentItem = null;
            return changed;
        }
        Item newItem = mc.player.getMainHandItem().getItem();
        boolean changed = !newItem.equals(currentItem);
        currentItem = newItem;
        return changed;
    }

    @Override
    public void reset() {
        currentItem = null;
    }

    // ============================================================
    //  查询
    //  Query
    // ============================================================

    @Override
    public List<Object> getActiveTriggers() {
        if (currentItem != null) {
            return List.of(currentItem);
        }
        return List.of();
    }

    /** 优先级最低（0），物品提示作为兜底，其他触发器覆盖它。 / Lowest priority (0): item hints as fallback, overridden by other triggers. */
    @Override
    public int getPriority() {
        return 0;
    }

    /** 触发器类型标识 —— 对应 JSON 中 {@code "hold_item"} 键。 / Trigger type — matches the {@code "hold_item"} key in JSON. */
    @Override
    public String getType() {
        return HudConfig.TRIGGER_HOLD_ITEM;
    }

    /** continuous — 手持物品期间持续激活。 / Continuously active while holding an item. */
    @Override
    public boolean isContinuous() {
        return true;
    }

    /** Item 型 — 切换物品时旧提示立即移除（物品互斥）。 / Item-based — old hint removed immediately on item switch (item mutual exclusion). */
    @Override
    public boolean isItemBased() {
        return true;
    }
}
