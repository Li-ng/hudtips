package com.xiaogua.hudtips;

import com.xiaogua.hudtips.client.config.HudConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 模组 TOML 配置（hudtips-client.toml）。
 *
 * <p>游戏内 Mods 界面 > HUD Tips > Config 可实时修改。
 * 这些配置控制渲染参数；提示规则本身在 hudtips/ 目录下的 JSON 文件中定义。</p>
 *
 * <h2>自适应文字缩放公式</h2>
 * <p>当 {@code scaleWithGui = true} 时，最终缩放 = 规则 textScale × 三因子：</p>
 * <pre>
 * finalScale = textScale × sizeMult × resFactor × guiFactor
 *
 * sizeMult  = 0.5 + textSize × 0.02        // textSize=0→0.5x, 50→1.5x, 100→2.5x
 * resFactor  = 1 + resolutionComp% × (screenH/1080 − 1)  // 高分辨率放大
 * guiFactor  = 1 + guiScaleComp% × (2/guiScale − 1)      // 低GUI缩放放大
 * </pre>
 */
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    /**
     * 模组总开关。
     *
     * <p>设为 {@code false} 时跳过所有 tick 和渲染逻辑，
     * 相当于完全禁用模组的 HUD 提示功能。热键重置不受影响。</p>
     */
    public static final ModConfigSpec.BooleanValue ENABLED = BUILDER
            .comment("启用模组", "Enable the mod")
            .define("enabled", true);

    /**
     * 自适应文字大小总开关。
     *
     * <p>启用后根据屏幕分辨率和 GUI 缩放比例动态调整文字大小。
     * 由 {@code HintManager.computeAdaptiveScale()} 执行三因子计算。</p>
     */
    public static final ModConfigSpec.BooleanValue SCALE_WITH_GUI = BUILDER
            .comment("自适应文字大小", "Adaptive text sizing")
            .define("scaleWithGui", false);

    /**
     * 基础文字大小。
     *
     * <p>控制自适应缩放的第一因子（sizeMult）。
     * 范围 0-100，映射关系：</p>
     * <ul>
     *   <li>{@code 0} → 0.5×（最小）</li>
     *   <li>{@code 50} → 1.5×（默认）</li>
     *   <li>{@code 100} → 2.5×（最大）</li>
     * </ul>
     */
    public static final ModConfigSpec.IntValue TEXT_SIZE = BUILDER
            .comment("文字大小", "Text size")
            .defineInRange("textSize", 50, 0, 100);

    /**
     * 分辨率补偿强度。
     *
     * <p>控制自适应缩放的第二因子（resFactor）。
     * 高分辨率屏幕（如 4K）上放大文字以确保可读性。
     * 0 = 不补偿，100 = 全补偿（与屏幕高度成正比）。</p>
     */
    public static final ModConfigSpec.IntValue RESOLUTION_COMP = BUILDER
            .comment("分辨率补偿", "Resolution compensation")
            .defineInRange("resolutionComp", 50, 0, 100);

    /**
     * GUI 缩放补偿强度。
     *
     * <p>控制自适应缩放的第三因子（guiFactor）。
     * 低 GUI 缩放设置时放大文字以补偿 UI 元素变小。
     * 0 = 不补偿（跟随 GUI 缩放），100 = 全补偿。</p>
     */
    public static final ModConfigSpec.IntValue GUI_SCALE_COMP = BUILDER
            .comment("GUI 缩放补偿", "GUI scale compensation")
            .defineInRange("guiScaleComp", 50, 0, 100);

    /**
     * 物品驱动提示的屏幕位置。
     *
     * <p>仅影响未在 JSON 中显式指定 {@code position} 且由物品驱动
     * （有 {@code item/items} 字段）的规则。</p>
     *
     * <p>可选值：{@code bottom_left, bottom_right, top_left, top_right, above_item}</p>
     */
    public static final ModConfigSpec.ConfigValue<String> ITEM_HINT_POSITION = BUILDER
            .comment("物品驱动提示的屏幕位置",
                     "Position for item-driven hints",
                     "可选: bottom_left, bottom_right, top_left, top_right, above_item")
            .define("itemHintPosition", HudConfig.DEFAULT_POSITION);

    /**
     * 全局已读状态开关。
     *
     * <p>启用后，教程型提示（{@code "type": "guide"}）的完成状态
     * 在<b>所有存档之间共享</b>——在一个存档中完成的教程不会在其他存档中再次显示。</p>
     *
     * <p>禁用（默认）时，每个存档独立记录已读状态；
     * 开新档时所有教程型提示会重新出现。</p>
     *
     * <p>已读状态文件位置：</p>
     * <ul>
     *   <li>禁用时（默认）：{@code saves/<存档名>/hudtips_completed.json}</li>
     *   <li>启用时：{@code config/hudtips/hudtips_completed_global.json}</li>
     * </ul>
     */
    public static final ModConfigSpec.BooleanValue GLOBAL_SEEN_STATE = BUILDER
            .comment("全局已读状态",
                     "Global seen state - share tutorial completion across all saves",
                     "启用后教程型提示在所有存档中只显示一次")
            .define("globalSeenState", false);

    /** NeoForge 配置规格，由主类注册 */
    static final ModConfigSpec SPEC = BUILDER.build();
}
