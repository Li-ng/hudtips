package com.xiaogua.hudtips.client;

import com.xiaogua.hudtips.Config;
import com.xiaogua.hudtips.HUDTips;
import com.xiaogua.hudtips.client.config.ConfigLoader;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * 客户端刻事件处理器。
 * Client tick event handler.
 *
 * <p>每游戏刻（约 50ms）调用一次 {@link HintManager#tick()}，
 * 驱动触发器和提示状态机。</p>
 * <p>Calls {@link HintManager#tick()} once per game tick (~50ms),
 * driving the trigger and hint state machine.</p>
 *
 * <h2>调用链 / Call Chain:</h2>
 * <pre>
 * ClientTickEvent.Post (每 tick ~20 TPS / per tick ~20 TPS)
 *     └── ClientTickHandler.onClientTick()
 *             └── HintManager.tick()
 *                     └── TriggerManager.tick()
 *                             ├── 驱动触发器收集激活标识 / drive triggers, collect active IDs
 *                             ├── 遍历提示队列检查消失条件 / iterate hint queue, check dismiss conditions
 *                             └── 匹配规则添加新提示 / match rules, add new hints
 * </pre>
 *
 * <h2>性能说明 / Performance Notes:</h2>
 * <ul>
 *   <li>触发器按需注册，未被 JSON 引用的触发器 tick 零开销</li>
 *   <li>Triggers are registered on demand; unreferenced triggers incur zero tick overhead.</li>
 *   <li>规则使用 Item→List&lt;HintRule&gt; 索引，O(1) 匹配</li>
 *   <li>Rules use Item→List&lt;HintRule&gt; index for O(1) matching.</li>
 *   <li>如果模组被禁用（Config.ENABLED = false），跳过全部逻辑</li>
 *   <li>If the mod is disabled (Config.ENABLED = false), all logic is skipped.</li>
 * </ul>
 *
 * @see HintManager
 * @see Config
 */
@EventBusSubscriber(modid = HUDTips.MODID, value = Dist.CLIENT)
public class ClientTickHandler {

    /**
     * 客户端刻后事件处理。
     * Client post-tick event handler.
     *
     * <p>在每个游戏刻结束后调用。负责更新提示状态。</p>
     * <p>Called after each game tick. Responsible for updating hint state.</p>
     *
     * @param event 客户端刻事件 / Client tick event
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        // 检查模组是否启用
        // Check if mod is enabled
        if (!Config.ENABLED.getAsBoolean()) return;

        // 检查配置文件热重载
        // Check for config file hot-reload
        ConfigLoader.checkPendingReload();

        // 更新提示状态
        // Update hint state
        HintManager.tick();
    }
}
