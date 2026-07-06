package com.xiaogua.hudtips.client.trigger;

import com.xiaogua.hudtips.HUDTips;
import com.xiaogua.hudtips.client.config.HudConfig;
import com.xiaogua.hudtips.client.config.TriggerConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;

import java.util.List;

/**
 * 使用物品触发器 [event] — 双重检测机制。
 * Use-item trigger [event] — dual-detection mechanism.
 *
 * <p>当玩家<b>开始</b>使用物品时在当前 tick 激活一次，下一 tick 自动失效。
 * 覆盖两种使用场景：</p>
 * <p>Activates once on the current tick when the player <b>starts</b> using an item,
 * and automatically deactivates on the next tick. Covers two usage scenarios:</p>
 * <ol>
 *   <li><b>持续使用型</b>（弓、食物、盾牌、药水等）→ 通过 {@code isUsingItem()}
 *       状态转换检测（false→true）</li>
 *   <li><b>Duration-type</b> (bow, food, shield, potion, etc.) → detected via
 *       {@code isUsingItem()} state transition (false→true)</li>
 *   <li><b>瞬发型</b>（末影珍珠、打火石、刷子等）→ 通过监听
 *       {@link InputEvent.InteractionKeyMappingTriggered} 捕获右键按下</li>
 *   <li><b>Instant-type</b> (ender pearl, flint and steel, brush, etc.) → detected via
 *       {@link InputEvent.InteractionKeyMappingTriggered} capturing right-click press</li>
 * </ol>
 *
 * <h2>分类 / Classification</h2>
 * <table border="1">
 *   <tr><th>维度 / Dimension</th><th>值 / Value</th></tr>
 *   <tr><td>类型 / Type</td><td><b>event</b></td></tr>
 *   <tr><td>性能等级 / Performance</td><td><b>轻量 / Lightweight</b> — 主路径零开销；事件监听仅写一个引用 / Zero overhead on main path; event listener only writes one reference</td></tr>
 *   <tr><td>实现途径 / Approach</td><td>每 tick 读 {@code isUsingItem()} + 消费事件标记 / Read {@code isUsingItem()} per tick + consume event flag</td></tr>
 *   <tr><td>缓存策略 / Caching</td><td>无 / None</td></tr>
 *   <tr><td>索引方式 / Index</td><td>{@code Map<Item, List<HintRule>>}</td></tr>
 *   <tr><td>互斥关系 / Exclusivity</td><td>无 / None</td></tr>
 * </table>
 *
 * <h2>双重检测防重 / Dual-detection deduplication</h2>
 * <p>持续使用型物品（弓等）会同时触发事件 <b>和</b> {@code isUsingItem()}。
 * tick() 中 {@code isUsingItem()} 路径优先——如果状态转换已检测到，则清除事件标记，
 * 保证每次右键只触发一次。</p>
 * <p>Duration-type items (bow, etc.) trigger both the event <b>and</b> {@code isUsingItem()}.
 * The {@code isUsingItem()} path in tick() takes priority — if the state transition has already
 * been detected, the event flag is cleared, ensuring only one activation per right-click.</p>
 *
 * @see HudTrigger
 * @see HudConfig#TRIGGER_ON_USE
 */
public class OnUseTrigger implements HudTrigger {

    // ============================================================
    //  实例状态（tick 线程读写）
    //  Instance State (read/written by tick thread)
    // ============================================================

    /** 当前帧激活的物品（仅在触发的那一 tick 非 null） / Currently activated item (non-null only on the triggering tick) */
    private Item currentItem = null;

    /** 上一帧是否正在使用物品（用于状态转换检测 — 持续使用型物品） / Whether the item was being used in the previous frame (for state transition detection — duration-type items) */
    private boolean wasUsingItem = false;

    // ============================================================
    //  事件共享状态（由 EventListener 写入，tick 消费）
    //  注意：NeoForge 客户端事件总线和 ClientTick 均在渲染线程执行，
    //  不存在多线程竞争，无需额外同步。
    //  Event Shared State (written by EventListener, consumed by tick)
    //  Note: NeoForge client event bus and ClientTick both run on the render thread,
    //  so there is no multi-thread contention; no extra synchronization needed.
    // ============================================================

    /** 事件回调设置的物品（玩家右键时手持的物品） / Item set by event callback (the item held when the player right-clicks) */
    private static Item eventUseItem = null;

    /** 标记：事件已设置，等待 tick 消费 / Flag: event has been set, awaiting tick consumption */
    private static boolean eventPending = false;

    // ============================================================
    //  事件监听（静态，由 NeoForge GAME 总线调用）
    //  Event Listener (static, called by NeoForge GAME bus)
    // ============================================================

    /**
     * 监听右键交互按键，覆盖瞬发型物品（末影珍珠、打火石等）。
     * Listens for right-click interaction key, covering instant-type items (ender pearl, flint and steel, etc.).
     *
     * <p>使用 {@code @EventBusSubscriber} 自动注册到 GAME 总线。
     * 独立于 {@code TriggerManager} 的注册流程——即使 TriggerManager
     * 尚未初始化，此监听器也能捕获右键事件。</p>
     * <p>Uses {@code @EventBusSubscriber} to auto-register on the GAME bus.
     * Independent of {@code TriggerManager}'s registration flow — even if TriggerManager
     * has not yet initialized, this listener can still capture right-click events.</p>
     *
     * <p>仅监听「使用物品」按键（默认右键），忽略攻击/选取方块按键。</p>
     * <p>Only listens for the "use item" key (default right-click), ignoring attack/pick-block keys.</p>
     */
    @EventBusSubscriber(modid = HUDTips.MODID, value = Dist.CLIENT)
    public static class EventListener {

        @SubscribeEvent
        public static void onInteractionKeyMapping(InputEvent.InteractionKeyMappingTriggered event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;

            // 仅响应「使用物品」按键（默认右键），忽略攻击/选取方块
            // Only respond to the "use item" key (default right-click), ignore attack/pick-block
            if (event.getKeyMapping() != mc.options.keyUse) return;

            // 记录右键时手持的物品，供 tick() 消费
            // Record the item held on right-click for tick() to consume
            ItemStack held = mc.player.getMainHandItem();
            if (!held.isEmpty()) {
                eventUseItem = held.getItem();
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
        if (mc.player == null) {
            boolean changed = (currentItem != null);
            currentItem = null;
            wasUsingItem = false;
            return changed;
        }

        boolean isUsing = mc.player.isUsingItem();
        ItemStack useItem = mc.player.getUseItem();
        Item previousItem = currentItem;

        // ── 第一道防线：isUsingItem() 状态转换（持续使用型：弓/食物/盾牌等）──
        // ── First line of defense: isUsingItem() state transition (duration-type: bow/food/shield/etc.) ──
        if (isUsing && !wasUsingItem && !useItem.isEmpty()) {
            currentItem = useItem.getItem();
            // 清除事件标记，防止同一 tick 内双重触发
            // Clear event flag to prevent double triggering within the same tick
            eventPending = false;
            eventUseItem = null;
            HUDTips.LOGGER.debug("OnUseTrigger fired (duration)! item={}", currentItem);
        }
        // ── 第二道防线：事件标记（瞬发型：末影珍珠/打火石/刷子等）──
        // ── Second line of defense: event flag (instant-type: ender pearl/flint and steel/brush/etc.) ──
        else if (eventPending) {
            currentItem = eventUseItem;
            eventPending = false;
            eventUseItem = null;
            HUDTips.LOGGER.debug("OnUseTrigger fired (instant)! item={}", currentItem);
        } else {
            currentItem = null;
        }

        wasUsingItem = isUsing;

        // event 型：仅在触发时返回 true（null → non-null 转换）
        // event type: only returns true on trigger (null → non-null transition)
        return currentItem != null && !currentItem.equals(previousItem);
    }

    @Override
    public void reset() {
        currentItem = null;
        wasUsingItem = false;
        eventUseItem = null;
        eventPending = false;
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

    /** 优先级 10 — 高于 hold_item(0)，使用物品时覆盖手持物品的提示。 / Priority 10 — higher than hold_item(0); overrides held-item hints when using an item. */
    @Override
    public int getPriority() {
        return 10;
    }

    /** 触发器类型标识 —— 对应 JSON 中 {@code "on_use"} 键。 / Trigger type identifier — corresponds to the {@code "on_use"} key in JSON. */
    @Override
    public String getType() {
        return HudConfig.TRIGGER_ON_USE;
    }

    /** event — 仅在开始使用物品的那一 tick 激活，后续由 dismissOn 控制消失。 / event — only active on the tick when item use starts; subsequent dismissal controlled by dismissOn. */
    @Override
    public boolean isContinuous() {
        return false;
    }

    /** Item 型 — 切换物品时旧提示立即移除（物品互斥）。 / Item-based — old hints are immediately removed when switching items (item exclusivity). */
    @Override
    public boolean isItemBased() {
        return true;
    }
}
