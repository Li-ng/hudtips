package com.xiaogua.hudtips.client;

import com.xiaogua.hudtips.Config;
import com.xiaogua.hudtips.client.config.ConfigLoader;
import com.xiaogua.hudtips.client.config.HudConfig;
import com.xiaogua.hudtips.client.trigger.ActiveHint;
import com.xiaogua.hudtips.client.trigger.TriggerManager;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

/**
 * 提示渲染数据的中间层。
 * Middle layer for hint rendering data.
 *
 * <p>在 {@link TriggerManager}（状态管理）和 {@link ClientHudRenderer}（渲染）之间
 * 充当适配器：从 TriggerManager 获取活跃提示队列，将每条提示的动画状态
 * 转换为渲染器可直接使用的 {@link HintRenderData}。</p>
 * <p>Serves as an adapter between {@link TriggerManager} (state management) and
 * {@link ClientHudRenderer} (rendering): obtains the active hint queue from TriggerManager
 * and converts each hint's animation state into {@link HintRenderData} ready for the renderer.</p>
 *
 * <h2>数据流</h2>
 * <h2>Data Flow</h2>
 * <pre>
 * TriggerManager.getActiveHints()           → List&lt;ActiveHint&gt;
 *     │
 *     │  HintManager.getRenderDataList()
 *     │    ├── toRenderData()       — 单条转换 / per-hint conversion
 *     │    │   ├── 获取动画文本 + alpha / get animated text + alpha
 *     │    │   ├── 计算颜色（渐出 alpha）/ compute color (fade-out alpha)
 *     │    │   ├── computeAdaptiveScale() — 三因子自适应 / three-factor adaptive
 *     │    │   └── 填充默认值 / fill defaults
 *     │    └── 过滤 null（无效提示跳过）/ filter null (skip invalid hints)
 *     ↓
 * ClientHudRenderer.onRenderGui()           → 逐条渲染 / render each hint
 * </pre>
 *
 * @see TriggerManager
 * @see com.xiaogua.hudtips.client.trigger.ActiveHint
 * @see ClientHudRenderer
 */
public class HintManager {

    /** 私有构造函数，防止实例化（全静态工具类）。 / Private constructor to prevent instantiation (all-static utility class). */
    private HintManager() {}

    /** 自适应文字开关缓存（每 100 帧刷新一次） / Adaptive text scale toggle cache (refreshed every 100 frames) */
    private static boolean cachedScaleWithGui = false;
    private static int scaleCacheTimer = 0;
    /** 自适应缩放缓存刷新间隔（tick），避免每帧读取 Config 值 / Adaptive scale cache refresh interval (ticks), avoids reading Config every frame */
    private static final int SCALE_CACHE_INTERVAL = 100;

    /**
     * 单条提示的渲染就绪数据 —— 将 ActiveHint 的运行时状态转换为渲染器可直接使用的值。
     * Render-ready data for a single hint — converts ActiveHint runtime state into values the renderer can use directly.
     *
     * <p>这是一个 record，不可变，所有字段在构造时计算完毕。
     * 由 {@link #toRenderData(ActiveHint)} 每帧生成，
     * 传给 {@link ClientHudRenderer} 逐条渲染。</p>
     * <p>This is an immutable record; all fields are computed at construction time.
     * Generated every frame by {@link #toRenderData(ActiveHint)} and
     * passed to {@link ClientHudRenderer} for per-hint rendering.</p>
     *
     * @param text              可见文本（已截断、已处理逐字打印）。ENTERING 阶段为部分字符，其余阶段为全文 / Visible text (truncated, typewriter-processed). Partial during ENTERING, full text otherwise.
     * @param textColor         文字颜色（ARGB，已应用渐出 alpha）。CELEBRATING 阶段混合金色高亮 / Text color (ARGB, fade-out alpha applied). Blended with gold highlight during CELEBRATING.
     * @param bgColor           背景颜色（ARGB，已应用渐出 alpha）。CELEBRATING 阶段混合金色高亮 / Background color (ARGB, fade-out alpha applied). Blended with gold highlight during CELEBRATING.
     * @param position          锚点位置字符串（"bottom_left"、"top_center" 等）。解析优先级：规则显式指定 > 物品驱动 TOML 配置 > 继承默认值 / Anchor position string ("bottom_left", "top_center", etc.). Resolution priority: rule explicit > item-driven TOML config > inherited default.
     * @param offsetX           X 偏移（像素），相对锚点 / X offset (pixels), relative to anchor
     * @param offsetY           Y 偏移（像素），相对锚点 / Y offset (pixels), relative to anchor
     * @param textScale         文字缩放倍率（已应用三因子自适应：基础大小 × 分辨率补偿 × GUI 缩放补偿） / Text scale multiplier (three-factor adaptive applied: base size × resolution comp × GUI scale comp).
     * @param alpha             当前 alpha 倍率（0~1），EXITING 阶段从 1 线性递减到 0 / Current alpha multiplier (0~1), linearly decreases from 1 to 0 during EXITING.
     * @param isCelebrating     是否处于庆祝动画中（CELEBRATING 阶段） / Whether in celebrate animation (CELEBRATING phase).
     * @param celebrateProgress 庆祝动画进度（0→1），用于渲染器控制动画 / Celebrate animation progress (0→1), used by renderer to control animation.
     * @param celebrateScale    庆祝缩放倍率（当前固定 1.0，缩放脉冲已由复选框替代） / Celebrate scale multiplier (currently fixed at 1.0; scale pulse replaced by checkbox).
     * @param celebrateColor    庆祝高亮色 ARGB（稳定金色 #FFD700，alpha≈90%），非 CELEBRATING 阶段为 0 / Celebrate highlight ARGB (steady gold #FFD700, alpha≈90%), 0 outside CELEBRATING phase.
     * @param showCheckbox      是否显示左侧复选框（教程型规则为 true） / Whether to show left-side checkbox (true for guide-type rules).
     * @param isChecked         复选框是否已完成（CELEBRATING 阶段 = true → 金色填充） / Whether checkbox is complete (CELEBRATING phase = true → gold fill).
     * @param fullTextWidth     完整文本的像素宽度（用于预分配框宽，避免逐字打印阶段框体晃动） / Full text pixel width (used to pre-allocate box width, avoiding box jitter during typewriter phase).
     */
    public record HintRenderData(
        String text,
        int textColor,
        int bgColor,
        String position,
        int offsetX,
        int offsetY,
        float textScale,
        float alpha,
        boolean isCelebrating,
        float celebrateProgress,
        float celebrateScale,
        int celebrateColor,
        boolean showCheckbox,
        boolean isChecked,
        int fullTextWidth
    ) {}

    // ============================================================
    //  生命周期（委托给 TriggerManager）
    //  Lifecycle (delegated to TriggerManager)
    // ============================================================

    public static void tick() {
        TriggerManager.tick();
    }

    public static void reload() {
        ConfigLoader.loadConfig();
        TriggerManager.initTriggers();
    }

    public static void reset() {
        TriggerManager.resetAll();
    }

    // ============================================================
    //  渲染数据生成
    //  Render Data Generation
    // ============================================================

    /**
     * 获取当前帧所有需要渲染的提示数据。
     * Gets all hint data needed for rendering in the current frame.
     *
     * <p>遍历活跃提示队列，逐条转换为渲染就绪数据。
     * 返回列表的顺序即渲染顺序（第一个在最上方）。
     * 提示已在 {@link TriggerManager#tick()} 中按有效优先级从高到低排列，
     * 此处无需再排序。</p>
     * <p>Iterates the active hint queue, converting each to render-ready data.
     * The order of the returned list is the render order (first at the top).
     * Hints are already sorted by effective priority (high to low) in
     * {@link TriggerManager#tick()}; no re-sorting is needed here.</p>
     */
    public static List<HintRenderData> getRenderDataList() {
        List<ActiveHint> hints = TriggerManager.getActiveHints();
        List<HintRenderData> result = new ArrayList<>(hints.size());

        for (ActiveHint hint : hints) {
            HintRenderData data = toRenderData(hint);
            if (data != null) {
                result.add(data);
            }
        }
        return result;
    }

    /**
     * 将单条活跃提示转换为渲染就绪数据。
     * Converts a single active hint into render-ready data.
     *
     * <h3>转换步骤 / Conversion Steps</h3>
     * <ol>
     *   <li><b>动画状态</b> → visibleText（逐字截断/全文）、alpha（渐出透明度） / <b>Animation state</b> → visibleText (typewriter/full), alpha (fade-out opacity)</li>
     *   <li><b>颜色</b> → ARGB 颜色 + alpha 混合 / <b>Color</b> → ARGB color + alpha blend</li>
     *   <li><b>缩放</b> → 三因子自适应（基础 × 分辨率补偿 × GUI 缩放补偿） / <b>Scale</b> → three-factor adaptive (base × resolution comp × GUI scale comp)</li>
     *   <li><b>庆祝动画</b> → celebrateProgress、celebrateColor、复选框状态 / <b>Celebrate animation</b> → celebrateProgress, celebrateColor, checkbox state</li>
     *   <li><b>位置解析</b> → 显式指定 > 物品驱动 TOML > 规则继承值 / <b>Position resolution</b> → explicit > item-driven TOML > rule inherited value</li>
     * </ol>
     *
     * @return 渲染数据；如果提示不应渲染（空文本 / 完全透明）则返回 null / Render data; null if the hint should not be rendered (empty text / fully transparent)
     */
    private static HintRenderData toRenderData(ActiveHint hint) {
        // ── 第 1 层：动画状态 / Layer 1: Animation state ──
        String visibleText = hint.animation.getVisibleText();
        if (visibleText.isEmpty()) return null;

        float alpha = hint.animation.getAlphaMultiplier();
        if (alpha <= 0f) return null;

        // ── 第 2 层：颜色 / Layer 2: Color ──
        int textColor = applyAlpha(hint.rule.getTextColorARGB(), alpha);
        int bgColor = applyAlpha(hint.rule.getBgColorARGB(), alpha);

        // ── 第 3 层：缩放（textScale 已由 applyDefaults 填充，非 null）/ Layer 3: Scale (textScale already filled by applyDefaults, non-null) ──
        float textScale = computeAdaptiveScale(hint.rule.textScale);

        // ── 第 4 层：庆祝动画 & 复选框状态 / Layer 4: Celebrate animation & checkbox state ──
        boolean isCelebrating = hint.animation.getPhase() == TextAnimationManager.Phase.CELEBRATING;
        boolean showCheckbox = hint.rule.isGuideComplete();

        // ── 第 5 层：构建结果 / Layer 5: Build result ──
        // 位置解析：显式指定 > 物品驱动 TOML 配置 > 规则继承值（已由 applyDefaults 填充）
        // Position resolution: explicit > item-driven TOML config > rule inherited value (already filled by applyDefaults)
        String resolvedPosition = hint.rule.positionExplicit ? hint.rule.position
            : hint.rule.hasItemTarget() ? Config.ITEM_HINT_POSITION.get()
            : hint.rule.position;

        int fullTextWidth = Minecraft.getInstance().font.width(hint.animation.getFullText());

        return new HintRenderData(
            visibleText,
            textColor,
            bgColor,
            resolvedPosition,
            hint.rule.offsetX,
            hint.rule.offsetY,
            textScale,
            alpha,
            isCelebrating,
            hint.animation.getCelebrateProgress(),
            hint.animation.getCelebrateScale(),
            hint.animation.getCelebrateColor(),
            showCheckbox,
            isCelebrating,
            fullTextWidth
        );
    }

    // ============================================================
    //  自适应文字大小
    //  Adaptive Text Scaling
    // ============================================================

    /**
     * 根据 GUI 缩放和屏幕分辨率计算最终文字缩放倍率。
     * Computes the final text scale multiplier based on GUI scale and screen resolution.
     *
     * <p>三因子独立控制：</p>
     * <p>Three independent factors:</p>
     * <ol>
     *   <li><b>基础大小</b>（textSize）— 0.5x ~ 2.5x</li>
     *   <li><b>Base size</b> (textSize) — 0.5x ~ 2.5x</li>
     *   <li><b>分辨率补偿</b>（resolutionComp）— 高分辨率放大文字</li>
     *   <li><b>Resolution compensation</b> (resolutionComp) — enlarges text at high resolutions</li>
     *   <li><b>GUI 缩放补偿</b>（guiScaleComp）— 低 GUI 缩放时放大文字</li>
     *   <li><b>GUI scale compensation</b> (guiScaleComp) — enlarges text at low GUI scales</li>
     * </ol>
     *
     * @param baseScale 规则配置的基础缩放倍率 / Base scale multiplier from rule config
     * @return 应用三因子后的最终缩放倍率 / Final scale multiplier after applying all three factors
     */
    private static float computeAdaptiveScale(float baseScale) {
        // 定期刷新缓存，避免每帧读取 Config
        // Periodically refresh cache to avoid reading Config every frame
        if (scaleCacheTimer-- <= 0) {
            cachedScaleWithGui = Config.SCALE_WITH_GUI.getAsBoolean();
            scaleCacheTimer = SCALE_CACHE_INTERVAL;
        }
        if (!cachedScaleWithGui) return baseScale;

        var window = Minecraft.getInstance().getWindow();
        int screenH = window.getHeight();
        double guiScale = window.getGuiScale();
        if (screenH <= 0 || guiScale <= 0) return baseScale;

        // 因子 1：基础大小 / Factor 1: Base size
        double sizeMult = 0.5 + Config.TEXT_SIZE.get() * 0.02;

        // 因子 2：分辨率补偿 / Factor 2: Resolution compensation
        double resComp = Config.RESOLUTION_COMP.get() / 100.0;
        double resFactor = 1.0 + resComp * (screenH / 1080.0 - 1.0);

        // 因子 3：GUI 缩放补偿 / Factor 3: GUI scale compensation
        double guiComp = Config.GUI_SCALE_COMP.get() / 100.0;
        double guiFactor = 1.0 + guiComp * (2.0 / guiScale - 1.0);

        return (float) (baseScale * sizeMult * resFactor * guiFactor);
    }

    // ============================================================
    //  颜色工具
    //  Color Utilities
    // ============================================================

    /**
     * 对 ARGB 颜色应用 alpha 倍率（渐出动画用）。
     * Applies an alpha multiplier to an ARGB color (for fade-out animation).
     */
    private static int applyAlpha(int color, float alphaMultiplier) {
        int originalAlpha = (color >>> 24) & 0xFF;
        int newAlpha = (int) (originalAlpha * alphaMultiplier);
        return (newAlpha << 24) | (color & 0x00FFFFFF);
    }

    // ============================================================
    //  公共查询
    //  Public Queries
    // ============================================================

    /** @return 当前配置的内边距 / Current configured padding */
    public static int getCurrentPadding() {
        var config = ConfigLoader.getCurrentConfig();
        return config != null ? config.padding : 5;
    }
}
