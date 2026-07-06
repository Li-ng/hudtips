package com.xiaogua.hudtips.client.trigger;

import com.xiaogua.hudtips.HUDTips;
import com.xiaogua.hudtips.client.config.HudConfig;
import com.xiaogua.hudtips.client.config.TriggerConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.List;

/**
 * 激活方块触发器 [event] —— 独立触发器。
 * Activate-block trigger [event] — standalone trigger.
 *
 * <p>当玩家手持特定物品<b>右键点击特定方块</b>时在当前 tick 激活一次，
 * 下一 tick 自动失效。需同时满足物品匹配和方块匹配两个条件。</p>
 * <p>Activates once on the current tick when the player <b>right-clicks a specific block</b>
 * while holding a specific item, and auto-deactivates on the next tick.
 * Both item match and block match conditions must be satisfied simultaneously.</p>
 *
 * <p>与 {@code on_use}（开始使用物品——拉弓/吃东西/喝药水等）不同，
 * 本触发器捕获的是"右键点击方块"这一行为，不要求物品有使用动画过程。
 * 语义上更接近"激活方块"（开门、按按钮、催熟作物等）而非"使用物品"。</p>
 * <p>Unlike {@code on_use} (begin using an item — drawing a bow / eating / drinking potions, etc.),
 * this trigger captures the act of "right-clicking a block" and does not require the item
 * to have a use animation. Semantically it is closer to "activating a block"
 * (opening doors, pressing buttons, bonemealing crops, etc.) rather than "using an item".</p>
 *
 * <h2>分类 / Classification</h2>
 * <table border="1">
 *   <tr><th>维度 / Dimension</th><th>值 / Value</th></tr>
 *   <tr><td>类型 / Type</td><td><b>event</b></td></tr>
 *   <tr><td>性能等级 / Performance</td><td><b>零成本 / Zero-cost</b> — 纯事件驱动 / purely event-driven</td></tr>
 *   <tr><td>实现途径 / Implementation</td><td>订阅 {@code PlayerInteractEvent.RightClickBlock} / Subscribes to {@code PlayerInteractEvent.RightClickBlock}</td></tr>
 *   <tr><td>索引方式 / Indexing</td><td>{@code Map<Item, List<HintRule>>}</td></tr>
 *   <tr><td>互斥关系 / Mutual exclusion</td><td>Item 型互斥（同类型物品切物品时旧提示移除）/ Item-type mutual exclusion (old hint removed when switching items of the same type)</td></tr>
 * </table>
 *
 * <h2>匹配逻辑 / Matching Logic</h2>
 * <p>触发器仅负责捕获右键事件并返回手持物品标识。
 * 方块匹配（{@code triggerOn.on_activate_block} 的值）由
 * {@code TriggerManager.matchRulesFromTriggers()} 在候选规则中验证。</p>
 * <p>The trigger is only responsible for capturing right-click events and returning
 * the held item identifier. Block matching (the value of {@code triggerOn.on_activate_block})
 * is validated by {@code TriggerManager.matchRulesFromTriggers()} against candidate rules.</p>
 *
 * <h2>JSON 示例 / JSON Example</h2>
 * <pre>
 * "on_activate_block": {
 *   "rules": [
 *     {
 *       "items": ["minecraft:stick"],
 *       "text": "📦 手持木棍右键合成台打开快速合成"
 *     },
 *     {
 *       "items": ["minecraft:bone_meal"],
 *       "text": "🌱 骨粉用于催熟植物"
 *     }
 *   ]
 * }
 * </pre>
 *
 * @see HudTrigger
 * @see HudConfig#TRIGGER_ON_ACTIVATE_BLOCK
 * @see com.xiaogua.hudtips.client.config.HintRule#getTargetBlock()
 * @see com.xiaogua.hudtips.client.config.HintRule#getTargetBlocks()
 */
public class OnActivateBlockTrigger implements HudTrigger {

    // ============================================================
    //  实例状态（tick 线程读写）
    //  Instance State (read/written by tick thread)

    /** 当前帧激活的物品（仅在触发的那一 tick 非 null） / Item active this frame (non-null only during the triggering tick) */
    private Item currentItem = null;

    // ============================================================
    //  事件共享状态（由 EventListener 写入，tick 消费）
    //  Event Shared State (written by EventListener, consumed by tick)
    //  注意：NeoForge 客户端事件总线和 ClientTick 均在渲染线程执行，
    //  不存在多线程竞争，无需额外同步。
    //  Note: NeoForge client event bus and ClientTick both run on the render thread,
    //  so there is no multi-threaded contention and no extra synchronization is needed.
    // ============================================================

    /** 事件回调设置的物品（右键时手持的物品） / Item set by event callback (the item held during right-click) */
    private static Item eventItem = null;

    /** 事件回调设置的方块 ID（右键点击的目标方块） / Block ID set by event callback (the target block right-clicked) */
    private static String clickedBlockId = null;

    /** 标记：事件已设置，等待 tick 消费 / Flag: event has been set, waiting for tick to consume */
    private static boolean eventPending = false;

    // ============================================================
    //  事件监听（静态，由 NeoForge GAME 总线调用）
    //  Event Listener (static, invoked by NeoForge GAME bus)
    // ============================================================

    /**
     * 监听右键点击方块事件。
     * Listens for right-click-block events.
     *
     * <p>使用 {@code @EventBusSubscriber} 自动注册到 GAME 总线。
     * 独立于 {@code TriggerManager} 的注册流程——即使 TriggerManager
     * 尚未初始化，此监听器也能捕获右键方块事件。</p>
     * <p>Uses {@code @EventBusSubscriber} for automatic registration on the GAME bus.
     * Independent of the {@code TriggerManager} registration flow — even if TriggerManager
     * has not initialized yet, this listener can still capture right-click-block events.</p>
     *
     * <p>仅记录本地玩家的交互事件，忽略其他玩家和实体。</p>
     * <p>Only records interactions from the local player, ignoring other players and entities.</p>
     */
    @EventBusSubscriber(modid = HUDTips.MODID, value = Dist.CLIENT)
    public static class EventListener {

        @SubscribeEvent
        public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
            // 仅处理本地玩家
            // Only handle the local player
            if (event.getEntity() != Minecraft.getInstance().player) return;

            // 记录右键时手持的物品
            // Record the item held during right-click
            ItemStack held = event.getItemStack();
            if (held.isEmpty()) return;

            // 获取目标方块的注册名
            // Get the target block's registry name
            String blockId = BuiltInRegistries.BLOCK
                .getKey(event.getLevel().getBlockState(event.getPos()).getBlock())
                .toString();

            eventItem = held.getItem();
            clickedBlockId = blockId;
            eventPending = true;

            HUDTips.LOGGER.debug("OnActivateBlockTrigger event captured: item={}, block={}",
                eventItem, clickedBlockId);
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
            return changed;
        }

        Item previousItem = currentItem;

        if (eventPending) {
            // 消费事件标记：设置当前激活物品，clickedBlockId 由 EventListener 设置，此处不清空
            // Consume event flag: set current active item; clickedBlockId is set by EventListener, not cleared here
            currentItem = eventItem;
            eventPending = false;
            eventItem = null;
            HUDTips.LOGGER.debug("OnActivateBlockTrigger fired! item={}, block={}",
                currentItem, clickedBlockId);
        } else {
            // 无新事件：清空激活状态。clickedBlockId 不清空——
            // 由 EventListener 在下次事件时覆盖，reset() 时清空。
            // No new event: clear active state. clickedBlockId is not cleared —
            // it is overwritten by EventListener on the next event, and cleared on reset().
            currentItem = null;
        }

        // event 型：仅在触发时返回 true（null → non-null 转换）
        // event type: only returns true on trigger (null → non-null transition)
        return currentItem != null && !currentItem.equals(previousItem);
    }

    @Override
    public void reset() {
        currentItem = null;
        eventItem = null;
        clickedBlockId = null;
        eventPending = false;
    }

    // ============================================================
    //  查询
    //  Query
    // ============================================================

    /**
     * 获取最近一次右键点击的方块 ID（供 TriggerManager 匹配规则时使用）。
     * Returns the block ID of the most recent right-click (used by TriggerManager when matching rules).
     *
     * @return 方块注册名（如 "minecraft:dirt"），无事件时为 null
     *         Block registry name (e.g. "minecraft:dirt"), null when no event has occurred
     */
    public static String getClickedBlockId() {
        return clickedBlockId;
    }

    @Override
    public List<Object> getActiveTriggers() {
        if (currentItem != null) {
            return List.of(currentItem);
        }
        return List.of();
    }

    /** 优先级 15 — 介于 on_use(10) 和 on_low_health(20) 之间，右键方块优先于普通使用。 / Priority 15 — between on_use(10) and on_low_health(20); right-clicking a block takes priority over normal item use. */
    @Override
    public int getPriority() {
        return 15;
    }

    /** 触发器类型标识 —— 对应 JSON 中 {@code "on_activate_block"} 键。 / Trigger type identifier — corresponds to the {@code "on_activate_block"} key in JSON. */
    @Override
    public String getType() {
        return HudConfig.TRIGGER_ON_ACTIVATE_BLOCK;
    }

    /** event — 仅在右键点击方块的那一 tick 激活，后续由 dismissOn 控制消失。 / event — activates only on the tick the block was right-clicked; subsequent dismissal is controlled by dismissOn. */
    @Override
    public boolean isContinuous() {
        return false;
    }

    /** Item 型 — 切换物品时旧提示立即移除（物品互斥）。 / Item type — old hints are immediately removed when switching items (item mutual exclusion). */
    @Override
    public boolean isItemBased() {
        return true;
    }
}
