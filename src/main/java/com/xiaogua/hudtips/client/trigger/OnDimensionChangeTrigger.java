package com.xiaogua.hudtips.client.trigger;

import com.xiaogua.hudtips.HUDTips;
import com.xiaogua.hudtips.client.config.HudConfig;
import com.xiaogua.hudtips.client.config.TriggerConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.List;

/**
 * 维度切换触发器 [event]。
 * Dimension change trigger [event].
 *
 * <p>当玩家切换维度时在当前 tick 激活一次，下一 tick 自动失效。
 * 提示短暂显示后由 {@code dismissOn} 控制消失。</p>
 * <p>Activates once on the current tick when the player changes dimensions,
 * and automatically deactivates on the next tick.
 * The hint is displayed briefly and then dismissed based on {@code dismissOn}.</p>
 *
 * <h2>分类 / Classification</h2>
 * <table border="1">
 *   <tr><th>维度 / Dimension</th><th>值 / Value</th></tr>
 *   <tr><td>类型 / Type</td><td><b>event</b></td></tr>
 *   <tr><td>性能等级 / Performance</td><td><b>零成本 / Zero-cost</b> — 纯事件驱动 / Purely event-driven</td></tr>
 *   <tr><td>实现途径 / Approach</td><td>订阅 {@code PlayerChangedDimensionEvent}（第一道防线）+ tick ClientLevel 引用比较（兜底） / Subscribe to {@code PlayerChangedDimensionEvent} (first line of defense) + tick ClientLevel reference comparison (fallback)</td></tr>
 *   <tr><td>缓存策略 / Caching</td><td>无需 — event 消费后即清空 / Not needed — cleared after event consumption</td></tr>
 *   <tr><td>索引方式 / Index</td><td>{@code Map<String, List<HintRule>>}</td></tr>
 *   <tr><td>互斥关系 / Exclusivity</td><td>同类多值互斥 / Multi-value mutual exclusion within same type</td></tr>
 * </table>
 *
 * <h2>双重检测机制 / Dual-detection Mechanism</h2>
 * <ol>
 *   <li><b>事件订阅（主路径）</b>：订阅 {@link PlayerEvent.PlayerChangedDimensionEvent}，
 *       在事件回调中设置 {@code eventDimensionId} + {@code eventPending} 标记。
 *       tick() 消费标记后清空。零轮询开销。</li>
 *   <li><b>Event subscription (primary path)</b>: subscribes to {@link PlayerEvent.PlayerChangedDimensionEvent},
 *       sets {@code eventDimensionId} + {@code eventPending} flag in the event callback.
 *       tick() consumes the flag and clears it. Zero polling overhead.</li>
 *   <li><b>tick 引用比较（兜底）</b>：如果事件因某种原因未触发（如模组热加载），
 *       tick() 中通过比较 {@link ClientLevel} 引用来检测维度变化。</li>
 *   <li><b>tick reference comparison (fallback)</b>: if the event fails to fire for some reason
 *       (e.g. mod hot-reload), tick() detects dimension changes by comparing {@link ClientLevel} references.</li>
 * </ol>
 *
 * <h2>状态转换 / State Transition</h2>
 * <pre>
 * 维度变化事件 → eventPending=true, eventDimensionId="the_nether"
 *   → tick: 消费标记, currentDimensionId="the_nether" (激活一 tick)
 *   → 下一 tick: currentDimensionId=null (自动失效)
 * </pre>
 *
 * <h2>JSON 示例 / JSON Example</h2>
 * <pre>
 * "on_dimension_change": {
 *   "position": "top_center",
 *   "rules": [
 *     { "triggerOn": { "on_dimension_change": "minecraft:the_nether" }, "text": "🔥 欢迎来到下界！" }
 *   ]
 * }
 * </pre>
 *
 * @see OnDimensionTrigger  continuous 型版本（用于 AND 组合） / continuous-type version (for AND combinations)
 * @see HudTrigger
 * @see HudConfig#TRIGGER_ON_DIMENSION_CHANGE
 */
public class OnDimensionChangeTrigger implements HudTrigger {

    // ============================================================
    //  实例状态（tick 线程读写）
    //  Instance State (read/written by tick thread)
    // ============================================================

    /** 当前触发的新维度 ID（仅在切换时的一 tick 内非 null） / The newly triggered dimension ID (non-null only during the one tick of change) */
    private String currentDimensionId = null;

    /** 上一帧的 ClientLevel 引用（用于兜底检测） / Previous frame's ClientLevel reference (used for fallback detection) */
    private ClientLevel lastLevel = null;

    // ============================================================
    //  事件共享状态（由静态事件处理器写入，tick 消费）
    //  注意：NeoForge 客户端事件和 ClientTick 均在渲染线程执行，无需额外同步。
    //  Event Shared State (written by static event handler, consumed by tick)
    //  Note: NeoForge client events and ClientTick both run on the render thread; no extra synchronization needed.
    // ============================================================

    /**
     * 事件回调设置的维度 ID。
     * Dimension ID set by the event callback.
     * <p>由 {@link EventListener#onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent)}
     * 写入，tick() 消费后清空。</p>
     * <p>Written by {@link EventListener#onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent)},
     * cleared after tick() consumes it.</p>
     */
    private static String eventDimensionId = null;

    /** 标记：事件已设置维度 ID，等待 tick 消费 / Flag: event has set a dimension ID, awaiting tick consumption */
    private static boolean eventPending = false;

    // ============================================================
    //  事件监听（静态，由 NeoForge GAME 总线调用）
    //  Event Listener (static, called by NeoForge GAME bus)
    // ============================================================

    /**
     * 监听维度切换事件（零成本优化）。
     * Listens for dimension change events (zero-cost optimization).
     *
     * <p>使用 {@code @EventBusSubscriber} 自动注册到 GAME 总线。
     * 独立于 {@code TriggerManager} 的注册流程——即使 TriggerManager 尚未初始化，
     * 此监听器也能捕获维度变化。</p>
     * <p>Uses {@code @EventBusSubscriber} to auto-register on the GAME bus.
     * Independent of {@code TriggerManager}'s registration flow — even if TriggerManager
     * has not yet initialized, this listener can still capture dimension changes.</p>
     */
    @EventBusSubscriber(modid = HUDTips.MODID, value = Dist.CLIENT)
    public static class EventListener {

        @SubscribeEvent
        public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
            if (event.getEntity() == Minecraft.getInstance().player) {
                eventDimensionId = event.getTo().identifier().toString();
                eventPending = true;
            }
        }
    }

    // ============================================================
    //  实例生命周期
    //  Instance Lifecycle
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

        // ── 第一道防线：消费事件标记 ──
        // ── First line of defense: consume event flag ──
        if (eventPending) {
            currentDimensionId = eventDimensionId;
            lastLevel = (ClientLevel) mc.player.level();
            eventPending = false;
            eventDimensionId = null;
            return true; // 事件触发，状态变化 / Event triggered, state changed
        }

        // ── 第二道防线：tick ClientLevel 引用比较（兜底） ──
        // ── Second line of defense: tick ClientLevel reference comparison (fallback) ──
        ClientLevel currentLevel = (ClientLevel) mc.player.level();
        if (currentLevel != lastLevel) {
            currentDimensionId = currentLevel.dimension().identifier().toString();
            lastLevel = currentLevel;
            return true; // 兜底检测到维度变化 / Fallback detected dimension change
        } else {
            // event 型：未变化则保持非激活
            // event type: remain inactive if no change
            currentDimensionId = null;
        }
        return false;
    }

    @Override
    public void reset() {
        currentDimensionId = null;
        lastLevel = null;
        eventDimensionId = null;
        eventPending = false;
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

    /** 优先级最高（30）— 维度切换是最重要的事件，覆盖所有其他提示。 / Highest priority (30) — dimension change is the most important event, overriding all other hints. */
    @Override
    public int getPriority() {
        return 30;
    }

    /** 触发器类型标识 —— 对应 JSON 中 {@code "on_dimension_change"} 键。 / Trigger type identifier — corresponds to the {@code "on_dimension_change"} key in JSON. */
    @Override
    public String getType() {
        return HudConfig.TRIGGER_ON_DIMENSION_CHANGE;
    }

    /** event — 仅在切换维度的那一 tick 激活，后续由 dismissOn 控制消失。 / event — only active on the tick of dimension change; subsequent dismissal controlled by dismissOn. */
    @Override
    public boolean isContinuous() {
        return false;
    }

    /** 非 Item 型 — 返回维度 ID 字符串，不参与物品互斥。 / Non-item-based — returns dimension ID string, does not participate in item exclusivity. */
    @Override
    public boolean isItemBased() {
        return false;
    }
}
