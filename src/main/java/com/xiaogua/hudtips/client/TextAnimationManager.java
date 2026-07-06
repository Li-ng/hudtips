package com.xiaogua.hudtips.client;

/**
 * 单条提示的文本动画管理器（实例化，非静态）。
 * Per-hint text animation manager (instantiated, not static).
 *
 * <p>每个 {@link com.xiaogua.hudtips.client.trigger.ActiveHint} 持有一个独立的动画实例，
 * 管理该提示从入场（逐字打印）到退场（渐出）的完整生命周期。</p>
 * <p>Each {@link com.xiaogua.hudtips.client.trigger.ActiveHint} holds an independent animation instance,
 * managing the full lifecycle from entrance (typewriter) to exit (fade-out).</p>
 *
 * <h2>动画状态机 / Animation State Machine</h2>
 * <pre>
 *                    构造时自动进入 / auto-enters on construction
 *                    ───────────────→ ENTERING ────────────┐
 *                                     │                    │ startCelebrating()
 *                                     │ 全部字符显示完成     │ (外部: isGuideComplete)
 *                                     │ all chars revealed  │ (external: isGuideComplete)
 *                                     ↓                    ↓
 *                                   SHOWING ───→ CELEBRATING
 *                                     │              │
 *                                     │ startExiting()│ 600ms 后自动过渡
 *                                     │              │ auto-transition after 600ms
 *                                     ↓              │
 *                                   EXITING ←────────┘
 *                                     │
 *                                     │ isExitFinished() → true
 *                                     ↓
 *                                  外部移除 ActiveHint / ActiveHint removed externally
 * </pre>
 *
 * <h3>CELEBRATING → EXITING 自动过渡 / CELEBRATING → EXITING Auto-Transition</h3>
 * <p>庆祝阶段持续 600ms。到达时间后 {@link #tick()} 自动将 phase 切换为 EXITING，
 * 且 {@code exitStartTime} 被设为过去的时间点（使 isExitFinished() 立即返回 true）。
 * 这意味着庆祝完成后<b>跳过渐出</b>直接移除——金色高亮消失得更干脆。</p>
 * <p>The celebrate phase lasts 600ms. When the time is reached, {@link #tick()} auto-switches
 * the phase to EXITING, and {@code exitStartTime} is set to a past point (making isExitFinished()
 * immediately return true). This means celebration completes by <b>skipping fade-out</b> —
 * the golden highlight disappears more cleanly.</p>
 *
 * <h2>设计决策 / Design Decisions</h2>
 * <ul>
 *   <li><b>不自动重置</b>：EXITING 完成后不会自动回到初始状态，
 *       而是由外部（TriggerManager）通过 {@link #isExitFinished()} 判断并移除整个实例。
 *       这避免了动画状态与规则状态不一致的问题。</li>
 *   <li><b>No auto-reset</b>: after EXITING completes, the instance doesn't auto-return to initial state;
 *       instead, the external caller (TriggerManager) detects it via {@link #isExitFinished()} and removes
 *       the entire instance. This avoids inconsistencies between animation state and rule state.</li>
 *   <li><b>基于真实时间</b>：使用 {@link System#currentTimeMillis()} 计时，
 *       不受游戏 TPS 波动影响，在任何帧率下都保持平滑。</li>
 *   <li><b>Real-time based</b>: uses {@link System#currentTimeMillis()} for timing,
 *       unaffected by game TPS fluctuations, remains smooth at any frame rate.</li>
 *   <li><b>首字符立即可见</b>：逐字打印时始终至少显示 1 个字符，
 *       避免首帧完全空白造成的"未触发"错觉。</li>
 *   <li><b>First character always visible</b>: the typewriter always shows at least 1 character,
 *       avoiding the "not triggered" illusion from a completely blank first frame.</li>
 * </ul>
 *
 * @see com.xiaogua.hudtips.client.trigger.ActiveHint
 * @see HintManager
 */
public class TextAnimationManager {

    /** 动画阶段 / Animation phase */
    public enum Phase {
        /** 逐字打印中 —— 字符逐个出现 / Typewriter in progress — characters appear one by one */
        ENTERING,
        /** 完全可见 —— 正常显示，无动画 / Fully visible — normal display, no animation */
        SHOWING,
        /** 庆祝中 —— 复选框打勾 + 金色高亮 + 音效，用于"完成任务"式消失反馈 / Celebrating — checkbox check + golden highlight + sound, for "task complete" style dismiss feedback */
        CELEBRATING,
        /** 渐出中 —— alpha 从 1.0 线性递减到 0.0 / Fading out — alpha linearly decreases from 1.0 to 0.0 */
        EXITING
    }

    // ============================================================
    //  动画参数常量
    //  Animation Parameter Constants

    /** 逐字打印速度（字符/秒）。
     *  30 字符/秒 ≈ 人类快速阅读速度，短提示约 0.5-1 秒显示完毕
     *  Typewriter speed (chars/sec).
     *  30 chars/sec ≈ fast human reading speed, short hints display in ~0.5-1s */
    private static final float CHARS_PER_SECOND = 30f;

    /** 渐出持续时间（毫秒）。
     *  500ms 兼顾"看得出是渐出"和"不拖沓"的平衡
     *  Fade-out duration (ms).
     *  500ms balances "noticeable fade" with "snappy feel" */
    private static final float FADE_DURATION_MS = 500f;

    /** 庆祝动画持续时间（毫秒）。
     *  600ms：打勾显示 + 金色高亮保持，节奏紧凑不拖沓
     *  Celebrate animation duration (ms).
     *  600ms: checkmark display + golden highlight hold, tight and snappy pacing */
    private static final float CELEBRATE_DURATION_MS = 600f;

    /** 庆祝闪烁颜色：金色 #FFD700 / Celebrate flash color: gold #FFD700 */
    private static final int CELEBRATE_FLASH_COLOR = 0xFFFFD700;

    // ============================================================
    //  实例状态
    //  Instance State

    /** 当前动画阶段，构造时自动设为 ENTERING / Current animation phase, auto-set to ENTERING at construction */
    private Phase currentPhase = Phase.ENTERING;

    /** 提示的完整文本（不变） / Full hint text (immutable) */
    private final String fullText;

    /** 入场动画开始时间戳（构造时记录，之后不变） / Enter animation start timestamp (recorded at construction, immutable thereafter) */
    private final long enterStartTime;

    /** 退场动画开始时间戳（调用 startExiting() 时记录） / Exit animation start timestamp (recorded when startExiting() is called) */
    private long exitStartTime = 0;

    /** 庆祝动画开始时间戳（调用 startCelebrating() 时记录） / Celebrate animation start timestamp (recorded when startCelebrating() is called) */
    private long celebrateStartTime = 0;

    // ============================================================
    //  构造与生命周期
    //  Construction and Lifecycle

    /**
     * 创建动画管理器并立即开始逐字打印。
     * Create an animation manager and immediately start the typewriter effect.
     *
     * @param text 完整提示文本，为 null 时当作空字符串 / Full hint text, treated as empty string if null
     */
    public TextAnimationManager(String text) {
        // 解析 {key:翻译键名} 占位符为玩家设置的实际按键名
        // Resolve {key:translationKey} placeholder to the player's actual bound key name
        this.fullText = text != null ? KeyMappingLookup.resolveKeyPlaceholders(text) : "";
        this.enterStartTime = System.currentTimeMillis();
    }

    /**
     * 开始渐出退场动画。
     * Start the fade-out exit animation.
     *
     * <p>仅在 ENTERING、SHOWING 或 CELEBRATING 阶段有效。
     * 如果已在 EXITING 阶段则忽略（防止重复触发）。</p>
     * <p>Only effective during ENTERING, SHOWING, or CELEBRATING phases.
     * Ignored if already in EXITING phase (prevents duplicate triggers).</p>
     */
    public void startExiting() {
        if (currentPhase == Phase.ENTERING || currentPhase == Phase.SHOWING
            || currentPhase == Phase.CELEBRATING) {
            exitStartTime = System.currentTimeMillis();
            currentPhase = Phase.EXITING;
        }
    }

    /**
     * 开始庆祝动画（复选框打勾 + 金色高亮 + 音效由 TriggerManager 播放）。
     * Start the celebrate animation (checkbox check + golden highlight; sound played by TriggerManager).
     *
     * <p>设计为幂等操作：仅在 ENTERING 或 SHOWING 阶段有效。
     * 已在 CELEBRATING 或 EXITING 阶段则忽略，防止重复庆祝。</p>
     * <p>Designed as idempotent: only effective during ENTERING or SHOWING phases.
     * Ignored if already in CELEBRATING or EXITING to prevent duplicate celebration.</p>
     *
     * <h3>庆祝 → 消失的自动过渡 / Celebrate → Dismiss Auto-Transition</h3>
     * <p>庆祝持续 {@value #CELEBRATE_DURATION_MS}ms。到达时间后，
     * {@link #tick()} 自动将 phase 切换为 EXITING，并将 exitStartTime
     * 后移使 {@link #isExitFinished()} 立即返回 true——
     * 跳过渐出直接移除。金色高亮消失得更干脆。</p>
     * <p>Celebration lasts {@value #CELEBRATE_DURATION_MS}ms. When the time is reached,
     * {@link #tick()} auto-switches the phase to EXITING, and shifts exitStartTime
     * so that {@link #isExitFinished()} immediately returns true —
     * skipping the fade-out and removing directly. The golden highlight disappears more cleanly.</p>
     *
     * <h3>调用路径 / Call Path</h3>
     * <pre>
     * TriggerManager.tick() 步骤 2 / step 2
     *   → isDismissedByExplicitTrigger() 返回 true / returns true
     *   → hint.rule.isGuideComplete()
     *   → animation.startCelebrating()   ← 这里 / here
     *   → playCelebrateSound()
     *   → markSeen()（如果是教程型规则 / if tutorial-type rule）
     * </pre>
     *
     * @see #getCelebrateProgress()
     * @see #getCelebrateScale()
     * @see #getCelebrateColor()
     */
    public void startCelebrating() {
        if (currentPhase == Phase.ENTERING || currentPhase == Phase.SHOWING) {
            celebrateStartTime = System.currentTimeMillis();
            currentPhase = Phase.CELEBRATING;
        }
    }

    /**
     * 每游戏刻调用，推进动画状态。
     * Called per game tick to advance animation state.
     *
     * <p>处理两种自动过渡：/ Handles two auto-transitions:</p>
     * <ol>
     *   <li><b>ENTERING → SHOWING</b>：全部字符显示完成后自动切换。
     *       空文本直接跳过逐字打印阶段。</li>
     *   <li><b>ENTERING → SHOWING</b>: auto-switches when all characters are revealed.
     *       Empty text skips the typewriter phase entirely.</li>
     *   <li><b>CELEBRATING → EXITING</b>：庆祝到达 {@value #CELEBRATE_DURATION_MS}ms 后切换。
     *       exitStartTime 前移使 isExitFinished() 立即返回 true，
     *       跳过渐出阶段直接移除。</li>
     *   <li><b>CELEBRATING → EXITING</b>: switches when celebration reaches {@value #CELEBRATE_DURATION_MS}ms.
     *       exitStartTime is shifted forward so isExitFinished() immediately returns true,
     *       skipping the fade-out phase for direct removal.</li>
     * </ol>
     *
     * <p>EXITING 完成后不自动重置——由外部 TriggerManager
     * 通过 {@link #isExitFinished()} 检测并移除整个 ActiveHint。</p>
     * <p>After EXITING completes, does not auto-reset — the external TriggerManager
     * detects completion via {@link #isExitFinished()} and removes the entire ActiveHint.</p>
     */
    public void tick() {
        if (currentPhase == Phase.ENTERING) {
            if (fullText.isEmpty()) {
                // 空文本直接跳过逐字打印 / Empty text skips the typewriter phase directly
                currentPhase = Phase.SHOWING;
            } else {
                int revealed = getRevealedCharCount();
                if (revealed >= fullText.length()) {
                    currentPhase = Phase.SHOWING;
                }
            }
        }

        // 庆祝完成后立即移除（不渐出） / Remove immediately after celebration completes (no fade-out)
        if (currentPhase == Phase.CELEBRATING) {
            if (getCelebrateProgress() >= 1.0f) {
                // 设置 exitStartTime 到过去，使 isExitFinished() 立即返回 true / Set exitStartTime in the past so isExitFinished() immediately returns true
                exitStartTime = System.currentTimeMillis() - (long)FADE_DURATION_MS;
                currentPhase = Phase.EXITING;
            }
        }
        // EXITING 不自动重置：由外部 isExitFinished() 判断移除时机 / EXITING does not auto-reset: external isExitFinished() determines removal timing
    }

    // ============================================================
    //  渲染查询
    //  Render Queries

    /**
     * 获取当前帧应显示的文本。
     * Get the text that should be displayed for the current frame.
     *
     * <p>ENTERING 阶段返回逐字截断的文本（始终 ≥ 1 字符）；
     * SHOWING / EXITING 阶段返回完整文本。</p>
     * <p>ENTERING phase returns typewriter-truncated text (always ≥ 1 char);
     * SHOWING / EXITING phases return the full text.</p>
     *
     * @return 当前可见文本，不会返回 null / Currently visible text, never null
     */
    public String getVisibleText() {
        return switch (currentPhase) {
            case ENTERING -> {
                if (fullText.isEmpty()) {
                    yield "";
                }
                // 基于真实时间计算已显示字符数 / Calculate revealed char count based on real time
                int count = getRevealedCharCount();
                // 始终至少显示 1 个字符：避免首帧空白 / Always show at least 1 char: avoid blank first frame
                count = Math.max(1, count);
                yield fullText.substring(0, Math.min(count, fullText.length()));
            }
            case SHOWING, CELEBRATING, EXITING -> fullText;
        };
    }

    /**
     * 获取当前帧的 alpha 透明度倍率。
     * Get the alpha transparency multiplier for the current frame.
     *
     * <p>ENTERING / SHOWING：1.0（完全不透明）。
     * EXITING：从 1.0 线性递减到 0.0，持续 {@value #FADE_DURATION_MS}ms。</p>
     * <p>ENTERING / SHOWING: 1.0 (fully opaque).
     * EXITING: linearly decreases from 1.0 to 0.0 over {@value #FADE_DURATION_MS}ms.</p>
     *
     * @return alpha 倍率，范围 [0, 1] / Alpha multiplier, range [0, 1]
     */
    public float getAlphaMultiplier() {
        return switch (currentPhase) {
            case ENTERING, SHOWING, CELEBRATING -> 1f;
            case EXITING -> {
                long now = System.currentTimeMillis();
                float elapsed = now - exitStartTime;
                // 线性插值：1.0 → 0.0 / Linear interpolation: 1.0 → 0.0
                float alpha = 1f - (elapsed / FADE_DURATION_MS);
                yield Math.max(0f, Math.min(1f, alpha));
            }
        };
    }

    /** @return 当前动画阶段 / Current animation phase */
    public Phase getPhase() {
        return currentPhase;
    }

    /**
     * 判断渐出动画是否已完成（alpha 已降至 0）。
     * Determine whether the fade-out animation has completed (alpha down to 0).
     *
     * <p>仅在 EXITING 阶段检查实际时间。非 EXITING 阶段返回 false。</p>
     * <p>Only checks elapsed time during EXITING phase. Returns false for non-EXITING phases.</p>
     *
     * @return true 如果渐出已完成，可以安全移除该提示 / true if fade-out is complete and the hint can be safely removed
     */
    public boolean isExitFinished() {
        if (currentPhase != Phase.EXITING) return false;
        return (System.currentTimeMillis() - exitStartTime) >= FADE_DURATION_MS;
    }

    /** @return 完整文本（不受动画影响） / Full text (unaffected by animation) */
    public String getFullText() {
        return fullText;
    }

    // ============================================================
    //  庆祝动画查询（CELEBRATING 阶段专用）
    //  Celebrate Animation Queries (CELEBRATING phase only)

    /**
     * 获取庆祝动画进度（0.0 → 1.0）。
     * Get the celebrate animation progress (0.0 → 1.0).
     *
     * <p>仅在 CELEBRATING 阶段有意义，其他阶段返回 0。
     * 基于真实时间，不受 TPS 波动影响。</p>
     * <p>Only meaningful during CELEBRATING phase; returns 0 in other phases.
     * Based on real time, unaffected by TPS fluctuations.</p>
     *
     * @return 庆祝进度 [0, 1]，1.0 表示庆祝完成 / Celebrate progress [0, 1], 1.0 means celebration complete
     */
    public float getCelebrateProgress() {
        if (currentPhase != Phase.CELEBRATING) return 0f;
        float elapsed = System.currentTimeMillis() - celebrateStartTime;
        return Math.min(1f, elapsed / CELEBRATE_DURATION_MS);
    }

    /**
     * 获取庆祝动画的缩放倍率。
     * Get the celebrate animation scale multiplier.
     *
     * <p>庆祝阶段不再缩放文字本体（缩放脉冲已由复选框打勾替代）。
     * 始终返回 1.0，保持文字原有大小。</p>
     * <p>Celebration no longer scales the text body (scale pulse has been replaced by the checkbox checkmark).
     * Always returns 1.0, keeping the text at its original size.</p>
     *
     * @return 始终 1.0（无缩放脉冲） / Always 1.0 (no scale pulse)
     */
    public float getCelebrateScale() {
        return 1f;
    }

    /**
     * 获取庆祝动画的金色高亮混合色。
     * Get the golden highlight blend color for the celebrate animation.
     *
     * <p>返回稳定的金色（非闪烁三角波），在整个庆祝期间保持恒定强度。
     * alpha 通道代表与原始颜色的混合强度（~90% 金色）。</p>
     * <p>Returns a steady gold (not a flickering triangle wave), maintaining constant intensity throughout the celebration.
     * The alpha channel represents the blend strength with the original color (~90% gold).</p>
     *
     * @return 固定金色高亮 ARGB（alpha≈230），非 CELEBRATING 阶段返回 0 / Fixed golden highlight ARGB (alpha≈230), returns 0 in non-CELEBRATING phases
     */
    public int getCelebrateColor() {
        if (currentPhase != Phase.CELEBRATING) return 0;
        // 固定金色高亮，alpha≈90%（230/255）—— 保持强势但留一点原色
        // Fixed golden highlight, alpha≈90% (230/255) — stays dominant while retaining a hint of original color
        return (230 << 24) | (CELEBRATE_FLASH_COLOR & 0x00FFFFFF);
    }

    // ============================================================
    //  内部计算
    //  Internal Calculation

    /**
     * 计算从入场开始到目前已显示的字符数。
     * Calculate the number of characters revealed since the enter animation began.
     *
     * <p>公式：{@code floor(已过秒数 × 30)}。
     * 由于使用真实时间，帧率越高过渡越平滑。</p>
     * <p>Formula: {@code floor(elapsedSeconds × 30)}.
     * Uses real time, so the higher the frame rate, the smoother the transition.</p>
     *
     * @return 已显示的字符数（可能为 0，调用方应处理） / Number of revealed characters (may be 0, caller should handle)
     */
    private int getRevealedCharCount() {
        float elapsedSeconds = (System.currentTimeMillis() - enterStartTime) / 1000f;
        return (int) (elapsedSeconds * CHARS_PER_SECOND);
    }
}
