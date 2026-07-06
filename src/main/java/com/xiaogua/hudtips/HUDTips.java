package com.xiaogua.hudtips;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;

/**
 * HUD Tips 模组主类。
 * Main mod class for HUD Tips.
 *
 * <p>本模组允许整合包作者通过 JSON 配置文件自定义 HUD 提示信息。
 * 当玩家触发特定条件（如手持指定物品）时，屏幕上会显示对应的提示文本。</p>
 * <p>This mod allows modpack authors to customize HUD hints via JSON configuration files.
 * When players trigger specific conditions (e.g. holding an item), corresponding hint texts appear on screen.</p>
 *
 * <h2>架构概览 / Architecture Overview:</h2>
 * <pre>
 * HUDTips (主类 / main class, 注册 TOML 配置 / registers TOML config)
 * ├── Config (TOML config: 开关/自适应/补偿 / toggle/adaptive/resolution comp)
 * └── HUDTipsClient (客户端入口 / client entry)
 *     ├── ConfigLoader → HudConfig / TriggerConfig / HintRule (JSON config)
 *     ├── TriggerManager (核心调度 / core scheduler)
 *     │   ├── HudTrigger 接口 (continuous / event, parent-child)
 *     │   │   ├── HoldItemTrigger       [continuous, priority= 0]
 *     │   │   ├── OnUseTrigger          [event,      priority=10]
 *     │   │   ├── OnLowHealthTrigger    [continuous, priority=20]
 *     │   │   ├── OnDimensionTrigger    [continuous, priority=25]
 *     │   │   └── OnDimensionChangeTrigger [event,  priority=30]
 *     │   ├── ActiveHint (提示实例 / hint instance: 规则 + 动画 + 关闭条件)
 *     │   └── dismiss / DismissConditions
 *     ├── TextAnimationManager (逐字打印 + 渐出 / typewriter + fade-out)
 *     ├── HintManager (TriggerManager → 渲染适配 / rendering adapter)
 *     ├── ClientHudRenderer (HUD 渲染 / HUD rendering)
 *     ├── ClientTickHandler (每 tick 驱动 / per-tick driver)
 *     ├── SeenStateManager (已读持久化 / seen-state persistence)
 *     └── KeyBindingHandler (按键重置已读 / key reset seen)
 * </pre>
 *
 * <h2>配置文件结构 / Config File Structure:</h2>
 * <pre>
 * config/hudtips/hudtips-client.toml   - NeoForge TOML config (in-game editable: toggle/adaptive text)
 * config/hudtips/*.json                - Hint rules edited by modpack authors (multi-file)
 * config/hudtips/_reference.json       - Auto-generated field reference (view only, not loaded)
 * config/hudtips_seen.json             - Seen hint records (auto-generated)
 * </pre>
 *
 * @see Config
 * @see com.xiaogua.hudtips.client.config.ConfigLoader
 */
@Mod(HUDTips.MODID)
public class HUDTips {
    /** 模组 ID，与 neoforge.mods.toml 中定义的一致 / Mod ID, matches neoforge.mods.toml */
    public static final String MODID = "hudtips";

    /** 模组日志记录器 / Mod logger */
    public static final Logger LOGGER = LogUtils.getLogger();

    /**
     * 模组构造函数，NeoForge 会自动调用。
     * Mod constructor, called automatically by NeoForge.
     *
     * @param modEventBus  模组事件总线，用于注册生命周期事件
     *                      / Mod event bus, for registering lifecycle events
     * @param modContainer 模组容器，用于注册配置等
     *                      / Mod container, for registering configs etc.
     */
    public HUDTips(IEventBus modEventBus, ModContainer modContainer) {
        // 注册 TOML 配置（CLIENT 类型，游戏内实时生效，文件位于 config/hudtips/ 目录下）
        // Register TOML config (CLIENT type, in-game real-time, stored under config/hudtips/)
        modContainer.registerConfig(ModConfig.Type.CLIENT, Config.SPEC, "hudtips/hudtips-client.toml");
    }
}
