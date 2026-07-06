package com.xiaogua.hudtips.client.trigger;

import com.xiaogua.hudtips.HUDTips;
import com.xiaogua.hudtips.client.config.HudConfig;
import com.xiaogua.hudtips.client.config.TriggerConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

import java.util.List;

/**
 * 击杀实体触发器 [event]。
 * Kill-entity trigger [event].
 *
 * <p>当本地玩家<b>击杀</b>实体时在当前 tick 激活一次，下一 tick 自动失效。
 * 通过监听 {@link LivingDeathEvent} 并检查伤害来源是否为本地玩家来判断击杀。
 * 提示由 {@code dismissOn} 控制消失。</p>
 * <p>Activates once on the current tick when the local player <b>kills</b> an entity,
 * and auto-deactivates on the next tick. Determines a kill by listening to
 * {@link LivingDeathEvent} and checking whether the damage source is the local player.
 * The hint's dismissal is controlled by {@code dismissOn}.</p>
 *
 * <h2>分类 / Classification</h2>
 * <table border="1">
 *   <tr><th>维度 / Dimension</th><th>值 / Value</th></tr>
 *   <tr><td>类型 / Type</td><td><b>event</b></td></tr>
 *   <tr><td>性能等级 / Performance</td><td><b>零成本 / Zero-cost</b> — 纯事件驱动 / purely event-driven</td></tr>
 *   <tr><td>实现途径 / Implementation</td><td>订阅 {@code LivingDeathEvent}，检查伤害来源为本地玩家 / Subscribes to {@code LivingDeathEvent}, checks damage source is local player</td></tr>
 *   <tr><td>缓存策略 / Caching</td><td>无需 — event 消费后即清空 / Not needed — cleared after event is consumed</td></tr>
 *   <tr><td>索引方式 / Indexing</td><td>{@code Map<String, List<HintRule>>}</td></tr>
 *   <tr><td>互斥关系 / Mutual exclusion</td><td>无（非 Item 型，不参与物品互斥）/ None (non-item type, not subject to item mutual exclusion)</td></tr>
 * </table>
 *
 * <h2>实现细节 / Implementation Details</h2>
 * <p>通过 {@code event.getSource().getEntity()} 获取伤害来源实体，
 * 仅当来源为本地玩家时才记录击杀。支持近战攻击、远程武器（弓/弩）、
 * 投掷物（雪球/鸡蛋）等间接击杀——{@code DamageSource#getEntity()}
 * 会追溯回真正的攻击者。</p>
 * <p>Obtains the damage source entity via {@code event.getSource().getEntity()},
 * recording a kill only when the source is the local player. Supports melee attacks,
 * ranged weapons (bow/crossbow), projectiles (snowball/egg), and other indirect kills
 * — {@code DamageSource#getEntity()} traces back to the true attacker.</p>
 *
 * <h2>JSON 示例 / JSON Example</h2>
 * <pre>
 * "on_kill": {
 *   "rules": [
 *     {
 *       "items": ["minecraft:diamond_sword"],
 *       "entity": "minecraft:zombie",
 *       "triggerOn": {
 *         "hold_item": true,
 *         "on_kill": "minecraft:zombie"
 *       },
 *       "triggerOnMode": "all",
 *       "text": "⚔ 钻石剑击杀僵尸！"
 *     }
 *   ]
 * }
 * </pre>
 *
 * @see HudTrigger
 * @see HudConfig#TRIGGER_ON_KILL
 */
public class OnKillTrigger implements HudTrigger {

    // ============================================================
    //  实例状态（tick 线程读写）
    //  Instance State (read/written by tick thread)

    /** 当前触发的新实体类型 ID（仅在触发的那一 tick 内非 null） / Currently triggered entity type ID (non-null only during the triggering tick) */
    private String currentEntityId = null;

    // ============================================================
    //  事件共享状态（由 EventListener 写入，tick 消费）
    //  Event Shared State (written by EventListener, consumed by tick)
    //  注意：NeoForge 客户端事件总线和 ClientTick 均在渲染线程执行，
    //  不存在多线程竞争，无需额外同步。
    //  Note: NeoForge client event bus and ClientTick both run on the render thread,
    //  so there is no multi-threaded contention and no extra synchronization is needed.
    // ============================================================

    /** 事件回调设置的实体类型 ID。由 EventListener 写入，tick() 消费后清空。 / Entity type ID set by event callback. Written by EventListener, cleared after tick() consumes it. */
    private static String eventEntityId = null;

    /** 标记：事件已设置实体类型 ID，等待 tick 消费 / Flag: event has set an entity type ID, waiting for tick to consume */
    private static boolean eventPending = false;

    // ============================================================
    //  事件监听（静态，由 NeoForge GAME 总线调用）
    //  Event Listener (static, invoked by NeoForge GAME bus)
    // ============================================================

    /**
     * 监听实体死亡事件，检测是否为本地玩家击杀。
     * Listens for entity death events and detects whether it was a kill by the local player.
     *
     * <p>使用 {@code @EventBusSubscriber} 自动注册到 GAME 总线。
     * 独立于 {@code TriggerManager} 的注册流程——即使 TriggerManager
     * 尚未初始化，此监听器也能捕获击杀事件。</p>
     * <p>Uses {@code @EventBusSubscriber} for automatic registration on the GAME bus.
     * Independent of the {@code TriggerManager} registration flow — even if TriggerManager
     * has not initialized yet, this listener can still capture kill events.</p>
     *
     * <p>通过 {@code DamageSource#getEntity()} 回溯伤害的真实来源，
     * 支持近战攻击、远程武器和投掷物等间接击杀方式。</p>
     * <p>Traces back to the true damage source via {@code DamageSource#getEntity()},
     * supporting indirect kills such as melee attacks, ranged weapons, and projectiles.</p>
     */
    @EventBusSubscriber(modid = HUDTips.MODID, value = Dist.CLIENT)
    public static class EventListener {

        @SubscribeEvent
        public static void onLivingDeath(LivingDeathEvent event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;

            // 检查伤害来源是否为本地玩家
            // Check whether the damage source is the local player
            if (event.getSource().getEntity() != mc.player) return;

            // 记录被击杀实体的类型 ID
            // Record the killed entity's type ID
            String entityId = BuiltInRegistries.ENTITY_TYPE
                .getKey(event.getEntity().getType())
                .toString();

            eventEntityId = entityId;
            eventPending = true;

            HUDTips.LOGGER.debug("OnKillTrigger event captured: entity={}", entityId);
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
            boolean changed = (currentEntityId != null);
            currentEntityId = null;
            return changed;
        }

        if (eventPending) {
            currentEntityId = eventEntityId;
            eventPending = false;
            eventEntityId = null;
            HUDTips.LOGGER.debug("OnKillTrigger fired! entity={}", currentEntityId);
            return true;
        }

        currentEntityId = null;
        return false;
    }

    @Override
    public void reset() {
        currentEntityId = null;
        eventEntityId = null;
        eventPending = false;
    }

    // ============================================================
    //  查询
    //  Query
    // ============================================================

    @Override
    public List<Object> getActiveTriggers() {
        if (currentEntityId != null) {
            return List.of((Object) currentEntityId);
        }
        return List.of();
    }

    /** 优先级 10 — 与 on_use 同级，击杀实体是重要的玩家行为。 / Priority 10 — same level as on_use; killing entities is a significant player action. */
    @Override
    public int getPriority() {
        return 10;
    }

    /** 触发器类型标识 —— 对应 JSON 中 {@code "on_kill"} 键。 / Trigger type identifier — corresponds to the {@code "on_kill"} key in JSON. */
    @Override
    public String getType() {
        return HudConfig.TRIGGER_ON_KILL;
    }

    /** event — 仅在击杀实体的那一 tick 激活，后续由 dismissOn 控制消失。 / event — activates only on the tick the entity was killed; subsequent dismissal is controlled by dismissOn. */
    @Override
    public boolean isContinuous() {
        return false;
    }

    /** 非 Item 型 — 返回实体类型 ID 字符串，不参与物品互斥。 / Non-item type — returns entity type ID string, not subject to item mutual exclusion. */
    @Override
    public boolean isItemBased() {
        return false;
    }
}
