package com.xiaogua.hudtips.client.trigger;

import com.xiaogua.hudtips.client.config.HudConfig;
import com.xiaogua.hudtips.client.config.TriggerConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.List;

/**
 * 指向实体触发器 [continuous]。
 * Look-at-entity trigger [continuous].
 *
 * <p>当玩家准星指向实体时持续激活，返回该实体的注册名作为匹配标识。</p>
 * <p>Continuously active while the player's crosshair is pointing at an entity,
 * returning the entity's registry name as the match identifier.</p>
 *
 * <h2>分类 / Classification</h2>
 * <table border="1">
 *   <tr><th>维度 / Dimension</th><th>值 / Value</th></tr>
 *   <tr><td>类型 / Type</td><td><b>continuous</b></td></tr>
 *   <tr><td>性能等级 / Performance</td><td><b>轻量 / Lightweight</b> — 仅读每帧已缓存的 {@code mc.hitResult} / Only reads per-frame cached {@code mc.hitResult}</td></tr>
 *   <tr><td>实现途径 / Implementation</td><td>每 tick 检查 {@code Minecraft.getInstance().hitResult} / Checks {@code Minecraft.getInstance().hitResult} each tick</td></tr>
 *   <tr><td>索引方式 / Indexing</td><td>{@code Map<String, List<HintRule>>}（stringToRulesMap，实体注册名 → 规则 / entity registry name → rules）</td></tr>
 *   <tr><td>互斥关系 / Mutual exclusion</td><td>无（非 Item 型，不参与物品互斥）/ None (non-item type, not subject to item mutual exclusion)</td></tr>
 * </table>
 *
 * <h2>关闭逻辑 / Dismissal Logic</h2>
 * <p>视线移开实体后 → TriggerManager 判断条件不满足 → 自动渐出。</p>
 * <p>After looking away from the entity → TriggerManager detects condition not satisfied → auto fade-out.</p>
 *
 * <h2>JSON 示例 / JSON Example</h2>
 * <pre>
 * "on_look_entity": {
 *   "rules": [
 *     {
 *       "text": "⚠ 苦力怕！保持距离！",
 *       "triggerOn": { "on_look_entity": "minecraft:creeper" }
 *     },
 *     {
 *       "items": ["minecraft:lead"],
 *       "text": "🪢 右键可以牵引这头牛",
 *       "triggerOn": {
 *         "hold_item": true,
 *         "on_look_entity": "minecraft:cow"
 *       },
 *       "triggerOnMode": "all"
 *     }
 *   ]
 * }
 * </pre>
 *
 * @see HudTrigger
 * @see HudConfig#TRIGGER_ON_LOOK_ENTITY
 */
public class OnLookEntityTrigger implements HudTrigger {

    /** 当前指向的实体注册名（如 "minecraft:creeper"），null 表示未指向实体 / Current looked-at entity registry name (e.g. "minecraft:creeper"), null means not looking at an entity */
    private String currentEntityId = null;

    // ============================================================
    //  生命周期
    //  Lifecycle

    @Override
    public void init(TriggerConfig triggerConfig, HudConfig globalConfig) {
        reset();
    }

    /**
     * 每 tick 检查 {@link Minecraft#hitResult} 是否为 {@link EntityHitResult}。
     * Checks each tick whether {@link Minecraft#hitResult} is an {@link EntityHitResult}.
     *
     * <p>{@code mc.hitResult} 由 MC 每帧更新，这里只是读取已缓存的值，
     * 不触发新的射线检测，零额外性能开销。</p>
     * <p>{@code mc.hitResult} is updated by MC each frame; this only reads the cached value
     * without triggering a new ray trace, resulting in zero additional performance cost.</p>
     *
     * @return true 表示指向的实体发生变化（包括指向实体 ↔ 不指向），
     *         触发 TriggerManager 步骤 3 重新匹配规则
     *         true means the looked-at entity changed (including looking at an entity ↔ not looking at one),
     *         triggering TriggerManager step 3 to re-match rules
     */
    @Override
    public boolean tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            boolean changed = (currentEntityId != null);
            currentEntityId = null;
            return changed;
        }

        String prev = currentEntityId;

        HitResult hit = mc.hitResult;
        if (hit != null && hit.getType() == HitResult.Type.ENTITY) {
            EntityHitResult entityHit = (EntityHitResult) hit;
            currentEntityId = BuiltInRegistries.ENTITY_TYPE
                .getKey(entityHit.getEntity().getType())
                .toString();
        } else {
            currentEntityId = null;
        }

        return !java.util.Objects.equals(prev, currentEntityId);
    }

    @Override
    public void reset() {
        currentEntityId = null;
    }

    // ============================================================
    //  查询
    //  Query

    @Override
    public List<Object> getActiveTriggers() {
        if (currentEntityId != null) {
            return List.of((Object) currentEntityId);
        }
        return List.of();
    }

    /** 优先级 17 — 介于 on_activate_block(15) 和 on_low_health(20) 之间。 / Priority 17 — between on_activate_block(15) and on_low_health(20). */
    @Override
    public int getPriority() {
        return 17;
    }

    /** 触发器类型标识 —— 对应 JSON 中 {@code "on_look_entity"} 键。 / Trigger type identifier — corresponds to the {@code "on_look_entity"} key in JSON. */
    @Override
    public String getType() {
        return HudConfig.TRIGGER_ON_LOOK_ENTITY;
    }

    /** continuous — 看向实体期间持续激活，视线移开后自动消失。 / continuous — stays active while looking at the entity, auto-dismisses when looking away. */
    @Override
    public boolean isContinuous() {
        return true;
    }

    /** 非 Item 型 — 返回实体注册名字符串，不参与物品互斥。 / Non-item type — returns entity registry name string, not subject to item mutual exclusion. */
    @Override
    public boolean isItemBased() {
        return false;
    }
}
