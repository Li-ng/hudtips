package com.xiaogua.hudtips.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.xiaogua.hudtips.HUDTips;
import com.xiaogua.hudtips.client.config.HintRule;
import com.xiaogua.hudtips.client.trigger.TriggerManager;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 按键绑定处理器 —— 支持通过按键（默认 \）重置已读提示状态。
 * Key binding handler — supports resetting seen hint state via a key (default \).
 *
 * <h2>生命周期 / Lifecycle</h2>
 * <ul>
 *   <li>MOD 总线 {@link RegisterKeyMappingsEvent}：在
 *       {@code HUDTipsClient} 构造函数中手动注册</li>
 *   <li>MOD bus {@link RegisterKeyMappingsEvent}: manually registered in
 *       the {@code HUDTipsClient} constructor</li>
 *   <li>GAME 总线 {@link InputEvent.Key}：通过
 *       {@code @EventBusSubscriber} 自动注册</li>
 *   <li>GAME bus {@link InputEvent.Key}: auto-registered via
 *       {@code @EventBusSubscriber}</li>
 * </ul>
 *
 * <h2>使用方式 / Usage</h2>
 * <p>玩家在游戏中手持物品时按下 <b>\键</b>，
 * 会重置该物品所有已被标记为「已读」的教程型提示（{@code "type": "guide"}）。
 * 提示会在下一个游戏刻重新显示。</p>
 * <p>When the player presses <b>\ key</b> while holding an item in-game,
 * all tutorial hints ({@code "type": "guide"}) marked as "seen" for that item are reset.
 * The hints will re-display on the next game tick.</p>
 *
 * @see SeenStateManager
 */
public class KeyBindingHandler {

    /** 私有构造函数，防止实例化（全静态工具类）。 / Private constructor to prevent instantiation (all-static utility class). */
    private KeyBindingHandler() {}

    /** 按键分类（延迟初始化 — 在 RegisterKeyMappingsEvent 中创建） / Key category (lazy init — created in RegisterKeyMappingsEvent) */
    private static KeyMapping.Category category;

    /** 重置提示按键（默认 \），延迟到分类创建后初始化 / Reset hint key (default \), lazily initialized after category creation */
    private static KeyMapping resetHintKey;

    /**
     * 获取重置提示按键，供外部（如配置界面）注册按键绑定。
     * Get the reset hint key for external use (e.g. config screen) to register the key binding.
     *
     * @return 重置提示按键实例，MOD 总线初始化完成后非 null / Reset hint key instance, non-null after MOD bus init completes
     */
    public static KeyMapping getResetHintKey() {
        return resetHintKey;
    }

    // ============================================================
    //  MOD 总线：注册按键绑定（在 HUDTipsClient 构造函数中调用）
    //  MOD Bus: Register Key Bindings (called in HUDTipsClient constructor)
    // ============================================================

    /**
     * 注册按键绑定到 MOD 事件总线。
     * Register key bindings to the MOD event bus.
     *
     * <p>{@link RegisterKeyMappingsEvent} 仅存在于 MOD 总线，
     * 无法通过 @EventBusSubscriber 默认总线注册，必须手动调用。</p>
     * <p>{@link RegisterKeyMappingsEvent} only exists on the MOD bus,
     * cannot be registered via @EventBusSubscriber default bus, must be called manually.</p>
     *
     * @param event 按键注册事件 / Key registration event
     */
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        // 创建自定义按键分类（使用推荐的 registerCategory API）
        // Create custom key category (using the recommended registerCategory API)
        category = new KeyMapping.Category(
            Identifier.fromNamespaceAndPath(HUDTips.MODID, "hudtips")
        );
        event.registerCategory(category);

        // 创建并注册重置提示按键
        // Create and register the reset hint key
        resetHintKey = new KeyMapping(
            "key.hudtips.reset_hint",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_BACKSLASH,
            category
        );
        event.register(resetHintKey);
    }

    // ============================================================
    //  GAME 总线：处理按键输入
    //  GAME Bus: Handle Key Input
    // ============================================================

    /**
     * GAME 总线事件订阅者 —— 处理按键按下。
     * GAME bus event subscriber — handles key presses.
     *
     * <p>使用默认 GAME 总线（与 {@code ClientTickHandler} 相同模式）。</p>
     * <p>Uses the default GAME bus (same pattern as {@code ClientTickHandler}).</p>
     */
    @EventBusSubscriber(modid = HUDTips.MODID, value = Dist.CLIENT)
    public static class GameEvents {

        @SubscribeEvent
        public static void onKeyInput(InputEvent.Key event) {
            // 使用 consumeClick() 确保每按一次只触发一次
            // Use consumeClick() to ensure it only fires once per press
            if (resetHintKey == null || !resetHintKey.consumeClick()) return;

            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;

            Item heldItem = mc.player.getMainHandItem().getItem();

            // 重置手持物品对应的所有已读一次性提示
            // Reset all seen one-shot hints for the held item
            int resetCount = resetSeenForHeldItem(heldItem);

            if (resetCount > 0) {
                // 在快捷栏上方显示反馈消息
                // Show feedback message above the hotbar
                mc.player.sendOverlayMessage(
                    Component.translatable("hudtips.reset_hint", resetCount)
                );
                HUDTips.LOGGER.debug("Reset {} seen hint(s) for held item.", resetCount);
            }
        }

        /**
         * 重置手持物品最近触发的一条已读提示（按一次回退一条）。
         * Reset the most recently triggered seen hint for the held item (one step back per press).
         *
         * <p>从最近标记已读的文本开始反向查找，找到第一条匹配手持物品
         * 的规则后清除其已读状态。</p>
         * <p>Searches backwards from the most recently marked-seen text; clears the seen state
         * of the first rule that matches the held item.</p>
         *
         * @param heldItem 玩家手持的物品 / The item the player is holding
         * @return 成功重置的提示数量（0 或 1） / Number of hints successfully reset (0 or 1)
         */
        private static int resetSeenForHeldItem(Item heldItem) {
            // 收集手持物品对应的所有教程型规则的文本
            // Collect texts of all tutorial-type rules matching the held item
            Set<String> itemTexts = new HashSet<>();
            for (HintRule rule : TriggerManager.getAllRules()) {
                if (!rule.isGuideComplete()) continue;
                if (!ruleMatchesItem(rule, heldItem)) continue;
                if (rule.text != null) itemTexts.add(rule.text);
            }
            if (itemTexts.isEmpty()) return 0;

            // 从最近标记的已读文本中反向查找
            // Search backwards from the most recently marked-seen texts
            List<String> seenList = new ArrayList<>(SeenStateManager.getSeenTexts());
            for (int i = seenList.size() - 1; i >= 0; i--) {
                String seenText = seenList.get(i);
                if (itemTexts.contains(seenText)) {
                    SeenStateManager.unsee(seenText);
                    // 强制下一次 tick 重新匹配，让刚重置的教程提示立即显示
                    // Force re-match on next tick so the just-reset tutorial hint displays immediately
                    TriggerManager.requestRematch();
                    return 1;
                }
            }
            return 0;
        }

        /**
         * 检查规则是否匹配指定的物品。
         * Check whether a rule matches the specified item.
         *
         * <p>检查 {@code rule.items} 字段。</p>
         * <p>Checks the {@code rule.items} field.</p>
         *
         * @param rule 提示规则 / Hint rule
         * @param item 要匹配的物品 / Item to match
         * @return true 如果规则的物品列表中包含该物品 / true if the rule's item list contains the item
         */
        private static boolean ruleMatchesItem(HintRule rule, Item item) {
            Identifier itemKey = BuiltInRegistries.ITEM.getKey(item);
            if (itemKey == null) return false;
            String itemStr = itemKey.toString();

            // 检查物品列表 / Check item list
            if (rule.items != null) {
                for (String candidate : rule.items) {
                    if (candidate != null && candidate.equals(itemStr)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }
}
