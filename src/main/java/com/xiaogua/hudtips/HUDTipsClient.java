package com.xiaogua.hudtips;

import com.xiaogua.hudtips.client.HintManager;
import com.xiaogua.hudtips.client.KeyBindingHandler;
import com.xiaogua.hudtips.client.SeenStateManager;
import com.xiaogua.hudtips.client.config.ConfigLoader;
import com.xiaogua.hudtips.client.trigger.TriggerManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

import java.nio.file.Path;

/**
 * 模组客户端入口类。
 * Client-side mod entry point.
 *
 * <p>此类仅在客户端加载时生效，不会在专用服务器上运行。
 * 主要负责：</p>
 * <p>This class is client-only and does not run on dedicated servers.
 * Primary responsibilities:</p>
 * <ul>
 *   <li>注册配置界面（Mods 界面 > HUD Tips > Config）
 *       / Register config screen (Mods screen → HUD Tips → Config)</li>
 *   <li>初始化时加载 JSON 配置 / Load JSON config on initialization</li>
 *   <li>每次加入世界时自动重载配置（方便调试）
 *       / Auto-reload config on each world join (for easy debugging)</li>
 * </ul>
 *
 * <h2>总线说明 / Bus Notes:</h2>
 * <ul>
 *   <li>{@link FMLClientSetupEvent} → <b>MOD 总线 / MOD bus</b>（构造函数中手动注册 / manually registered in constructor）</li>
 *   <li>{@link ClientPlayerNetworkEvent.LoggingIn} → <b>GAME 总线 / GAME bus</b>（内嵌类 @EventBusSubscriber 自动注册 / auto-registered via nested @EventBusSubscriber）</li>
 * </ul>
 *
 * <h2>生命周期 / Lifecycle:</h2>
 * <pre>
 * 游戏启动 / Game start → onClientSetup() → 加载 JSON 配置 / Load JSON config
 * 加入世界 / Join world → onPlayerJoin() → 重新加载配置 / Reload config (hot-reload)
 * </pre>
 *
 * @see ConfigLoader
 * @see HintManager
 */
@Mod(value = HUDTips.MODID, dist = Dist.CLIENT)
public class HUDTipsClient {

    /**
     * 构造函数，注册配置界面工厂和 MOD 总线事件。
     * Constructor: registers config screen factory and MOD bus events.
     *
     * <p>配置界面允许玩家通过游戏内 UI 修改 hudtips-client.toml 中的选项。
     * 注意：JSON 配置（config/hudtips/ 目录下的 .json 文件）需要通过外部编辑器修改。</p>
     * <p>The config screen allows players to modify hudtips-client.toml options in-game.
     * Note: JSON configs (files under config/hudtips/) require an external editor.</p>
     *
     * <p>{@link FMLClientSetupEvent} 是 MOD 总线事件，不能通过
     * @EventBusSubscriber（默认 GAME 总线）自动注册，必须在此手动注册。</p>
     * <p>{@link FMLClientSetupEvent} is a MOD bus event and cannot be auto-registered via
     * @EventBusSubscriber (default GAME bus), so it must be registered manually here.</p>
     *
     * @param modEventBus 模组事件总线（MOD 总线） / Mod event bus (MOD bus)
     * @param container   模组容器 / Mod container
     */
    public HUDTipsClient(IEventBus modEventBus, ModContainer container) {
        // 注册配置界面工厂，使 Mods 界面中出现 Config 按钮
        // Register config screen factory so Config button appears in Mods screen
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

        // FMLClientSetupEvent 是 MOD 总线事件，必须手动注册到此总线
        // FMLClientSetupEvent is a MOD bus event, must be manually registered
        modEventBus.addListener(this::onClientSetup);
        // RegisterKeyMappingsEvent 也是 MOD 总线事件，注册按键绑定
        // RegisterKeyMappingsEvent is also a MOD bus event, register keybindings
        modEventBus.addListener(KeyBindingHandler::onRegisterKeyMappings);
    }

    /**
     * 客户端初始化事件（MOD 总线）。
     * Client setup event (MOD bus).
     *
     * <p>在游戏启动、客户端环境准备好后调用。
     * 此时可以安全地访问 Minecraft 实例和加载配置文件。</p>
     * <p>Called when the game starts and the client environment is ready.
     * Safe to access the Minecraft instance and load config files at this point.</p>
     *
     * @param event 客户端设置事件 / Client setup event
     */
    private void onClientSetup(FMLClientSetupEvent event) {
        // 首次加载 JSON 配置 / First load of JSON configs
        ConfigLoader.loadConfig();
        // 初始化触发器（按需注册 + 输出日志供创作者确认）
        // Initialize triggers (on-demand registration + log for author confirmation)
        TriggerManager.initTriggers();
        HUDTips.LOGGER.info("HUDTips client setup complete.");
    }

    // ============================================================
    //  GAME 总线事件（通过内嵌 @EventBusSubscriber 自动注册）
    //  GAME Bus Events (auto-registered via nested @EventBusSubscriber)
    // ============================================================

    /**
     * GAME 总线事件订阅者。
     * GAME bus event subscriber.
     *
     * <p>与外部类分离，确保 GAME 总线事件不会被错误注册到 MOD 总线。
     * 外部类的 MOD 总线事件在构造函数中手动注册。</p>
     * <p>Separated from outer class to ensure GAME bus events are not mistakenly registered
     * to the MOD bus. Outer class MOD bus events are manually registered in the constructor.</p>
     */
    @EventBusSubscriber(modid = HUDTips.MODID, value = Dist.CLIENT)
    public static class GameEvents {

        /**
         * 玩家加入世界事件（GAME 总线）。
         * Player join world event (GAME bus).
         *
         * <p>每次玩家进入存档（包括重新加入服务器）时触发。
         * 用于实现配置热重载：整合包作者修改 JSON 后，只需重新进入世界即可生效。</p>
         * <p>Fires whenever the player enters a save (including reconnecting to a server).
         * Enables config hot-reload: modpack authors edit JSON files, re-enter the world, and changes take effect.</p>
         *
         * <h3>热重载流程 / Hot-reload flow:</h3>
         * <ol>
         *   <li>整合包作者编辑 config/hudtips/ 目录下的 .json 文件
         *       / Modpack author edits .json files under config/hudtips/</li>
         *   <li>保存文件 / Save files</li>
         *   <li>重新进入世界（或退出再进入）/ Re-enter world (or exit and re-enter)</li>
         *   <li>配置自动生效 / Config auto-applied</li>
         * </ol>
         *
         * @param event 玩家登录事件 / Player login event
         */
        @SubscribeEvent
        public static void onPlayerJoin(ClientPlayerNetworkEvent.LoggingIn event) {
            // 重新加载配置并重置显示状态 / Reload config and reset display state
            HintManager.reload();

            // 为此存档初始化完成状态持久化 / Initialize seen-state persistence for this save
            Path worldPath = SeenStateManager.resolveWorldPath();
            if (worldPath != null) {
                SeenStateManager.initForWorld(worldPath);
            }

            HUDTips.LOGGER.info("HUDTips config reloaded on world join.");
        }
    }
}
