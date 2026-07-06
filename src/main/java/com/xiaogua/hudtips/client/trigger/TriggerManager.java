package com.xiaogua.hudtips.client.trigger;

import com.xiaogua.hudtips.HUDTips;
import com.xiaogua.hudtips.client.SeenStateManager;
import com.xiaogua.hudtips.client.TextAnimationManager;
import com.xiaogua.hudtips.client.trigger.dismiss.DismissConditions;
import com.xiaogua.hudtips.client.config.ConfigLoader;
import com.xiaogua.hudtips.client.config.HintRule;
import com.xiaogua.hudtips.client.config.HudConfig;
import com.xiaogua.hudtips.client.config.TriggerConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 触发器管理器 —— HUD 提示系统的核心调度器。
 * Trigger Manager — the core scheduler of the HUD tips system.
 *
 * <p>职责：</p>
 * <p>Responsibilities:</p>
 * <ul>
 *   <li><b>触发器注册</b>：按需实例化触发器，维护属性缓存</li>
 *   <li><b>Trigger Registration</b>: Instantiates triggers on demand and maintains attribute caches.</li>
 *   <li><b>规则索引</b>：委托 {@link RuleIndex} 管理规则加载和 O(1) 查找</li>
 *   <li><b>Rule Indexing</b>: Delegates to {@link RuleIndex} for rule loading and O(1) lookups.</li>
 *   <li><b>主循环</b>：每 tick 驱动触发器 → 匹配规则 → 管理提示队列</li>
 *   <li><b>Main Loop</b>: Drives triggers every tick → matches rules → manages the hint queue.</li>
 *   <li><b>关闭判断</b>：综合时间/tick/触发器条件，决定每条提示何时渐出</li>
 *   <li><b>Dismissal Judgment</b>: Combines time/tick/trigger conditions to decide when each hint fades out.</li>
 * </ul>
 *
 * <h2>每 tick 执行流程</h2>
 * <h2>Per-Tick Execution Flow</h2>
 * <pre>
 *  1. 驱动触发器 + 收集激活标识   → 更新所有 HudTrigger，构建 activeTriggers 映射
 *  1. Drive triggers + collect active IDs → update all HudTriggers, build activeTriggers map
 *  2. 单次遍历活跃提示队列        → 推进动画 / 清理完成渐出 / 检查关闭条件
 *  2. Single-pass active hint queue  → advance animations / clean finished exits / check dismiss
 *  3. 匹配并添加新提示（条件执行）→ 仅在状态变化或提示被移除时运行
 *  3. Match and add new hints (conditional) → runs only when state changes or a hint is removed
 * </pre>
 *
 * <h2>提示队列设计</h2>
 * <h2>Hint Queue Design</h2>
 * <p>支持最多 {@value #MAX_ACTIVE_HINTS} 条提示同时显示。
 * 每条提示拥有独立的 {@link TextAnimationManager} 动画状态。
 * 队列满时优先踢掉正在渐出的提示，否则踢掉优先级最低的。</p>
 * <p>Supports up to {@value #MAX_ACTIVE_HINTS} hints displayed simultaneously.
 * Each hint owns an independent {@link TextAnimationManager} animation state.
 * When the queue is full, exiting hints are evicted first; otherwise the lowest-priority hint is evicted.</p>
 *
 * @see HudTrigger     触发器接口 / Trigger interface
 * @see HintRule       单条提示规则 / Single hint rule
 * @see RuleIndex      规则索引管理器 / Rule index manager
 * @see ActiveHint     活跃提示 / Active hint
 * @see DismissConditions  消失条件 / Dismiss conditions
 */
public class TriggerManager {

    /** 私有构造函数，防止实例化（全静态工具类）。 / Private constructor to prevent instantiation (all-static utility class). */
    private TriggerManager() {}

    // ============================================================
    //  常量
    //  Constants
    // ============================================================

    private static final int MAX_ACTIVE_HINTS = 3;

    // ============================================================
    //  触发器与规则
    //  Triggers & Rules
    // ============================================================

    private static final List<HudTrigger> triggers = new ArrayList<>();
    private static final Map<String, Boolean> continuousTriggerCache = new HashMap<>();
    private static final Map<String, Boolean> itemBasedTriggerCache = new HashMap<>();
    private static final RuleIndex ruleIndex = new RuleIndex();

    // ============================================================
    //  活跃提示队列
    //  Active Hint Queue
    // ============================================================

    private static final List<ActiveHint> activeHints = new ArrayList<>();
    private static boolean needsInitialMatch = true;

    // ============================================================
    //  初始化（配置加载/重载时调用）
    //  Initialization (called on config load/reload)
    // ============================================================

    /**
     * 注册所有被引用的触发器并加载规则。
     * Registers all referenced triggers and loads rules.
     *
     * <p><b>按需注册</b>：仅注册 JSON 配置中实际使用的触发器类型。
     * <b>父依赖自动拉取</b>：子类型被使用时父类型自动启用。</p>
     * <p><b>On-demand registration</b>: Only registers trigger types actually used in the JSON config.
     * <b>Auto parent pull-in</b>: When a subtype is used, its parent type is automatically enabled.</p>
     */
    public static void initTriggers() {
        triggers.clear();
        HudConfig globalConfig = ConfigLoader.getCurrentConfig();

        // 步骤 1：扫描使用到的触发器类型
        // Step 1: Scan for used trigger types
        Set<String> usedTypes = collectUsedTriggerTypes(globalConfig);

        // 步骤 2：按需注册
        // Step 2: Register on demand
        registerIfUsed(new OnDimensionChangeTrigger(), HudConfig.TRIGGER_ON_DIMENSION_CHANGE, globalConfig, usedTypes);
        registerIfUsed(new OnDimensionTrigger(),        HudConfig.TRIGGER_ON_DIMENSION,        globalConfig, usedTypes);
        registerIfUsed(new OnLookEntityTrigger(),       HudConfig.TRIGGER_ON_LOOK_ENTITY,      globalConfig, usedTypes);
        registerIfUsed(new OnLookBlockTrigger(),        HudConfig.TRIGGER_ON_LOOK_BLOCK,       globalConfig, usedTypes);
        registerIfUsed(new OnLowHealthTrigger(),     HudConfig.TRIGGER_ON_LOW_HEALTH,     globalConfig, usedTypes);
        registerIfUsed(new OnKillTrigger(),          HudConfig.TRIGGER_ON_KILL,           globalConfig, usedTypes);
        registerIfUsed(new OnUseTrigger(),           HudConfig.TRIGGER_ON_USE,            globalConfig, usedTypes);
        registerIfUsed(new OnActivateBlockTrigger(), HudConfig.TRIGGER_ON_ACTIVATE_BLOCK, globalConfig, usedTypes);
        registerIfUsed(new OnKeyPressTrigger(),      HudConfig.TRIGGER_ON_KEY_PRESS,      globalConfig, usedTypes);
        registerIfUsed(new HoldItemTrigger(),        HudConfig.TRIGGER_HOLD_ITEM,         globalConfig, usedTypes);

        triggers.sort(Comparator.comparingInt(HudTrigger::getPriority).reversed());

        // 步骤 3：构建属性缓存
        // Step 3: Build attribute caches
        continuousTriggerCache.clear();
        itemBasedTriggerCache.clear();
        for (HudTrigger trigger : triggers) {
            continuousTriggerCache.put(trigger.getType(), trigger.isContinuous());
            itemBasedTriggerCache.put(trigger.getType(), trigger.isItemBased());
        }

        // 步骤 4：加载规则索引
        // Step 4: Load rule index
        ruleIndex.loadAllRules(globalConfig, triggers);
        HUDTips.LOGGER.info("Initialized {} triggers and {} rules.",
            triggers.size(), ruleIndex.getAllRules().size());
    }

    /** 扫描所有规则的 triggerOn/dismissOn，收集被引用的触发器类型 / Scan all rules' triggerOn/dismissOn, collect referenced trigger types */
    private static Set<String> collectUsedTriggerTypes(HudConfig globalConfig) {
        Set<String> used = new HashSet<>();
        Map<String, TriggerConfig> triggerConfigs = globalConfig.triggers;
        if (triggerConfigs == null) return used;

        for (Map.Entry<String, TriggerConfig> entry : triggerConfigs.entrySet()) {
            String triggerType = entry.getKey();
            TriggerConfig tc = entry.getValue();
            if (tc.rules == null) continue;

            for (HintRule rule : tc.rules) {
                Map<String, Object> triggerOn = rule.triggerOn;
                if (triggerOn == null || triggerOn.isEmpty()) {
                    triggerOn = RuleIndex.inferTriggerOn(rule, triggerType);
                }
                if (triggerOn != null) used.addAll(triggerOn.keySet());

                if (rule.dismissOn != null) {
                    for (String d : rule.dismissOn) {
                        if (!d.startsWith("time:") && !d.startsWith("tick:")) {
                            used.add(d);
                        }
                    }
                }
            }
        }
        return used;
    }

    private static void registerIfUsed(HudTrigger trigger, String triggerType,
                                        HudConfig globalConfig, Set<String> usedTypes) {
        if (usedTypes.contains(triggerType)) {
            register(trigger, triggerType, globalConfig);
        } else {
            HUDTips.LOGGER.debug("Skipping unused trigger: {}", triggerType);
        }
    }

    private static void register(HudTrigger trigger, String triggerType, HudConfig globalConfig) {
        TriggerConfig triggerConfig = ConfigLoader.getTriggerConfig(triggerType);
        trigger.init(triggerConfig, globalConfig);
        triggers.add(trigger);
    }

    // ============================================================
    //  每 tick 更新
    //  Per-Tick Update
    // ============================================================

    public static void tick() {
        // ── 步骤 1：驱动触发器 + 收集激活标识 / Step 1: Drive triggers + collect active IDs ──
        Map<String, List<Object>> activeTriggers = new HashMap<>();
        boolean anyTriggerChanged = needsInitialMatch;
        for (HudTrigger trigger : triggers) {
            anyTriggerChanged |= trigger.tick();
            List<Object> identifiers = trigger.getActiveTriggers();
            if (!identifiers.isEmpty()) {
                activeTriggers.put(trigger.getType(), identifiers);
            }
        }

        // ── 步骤 2：推进动画 + 清理 + 检查消失条件 / Step 2: Advance animations + cleanup + check dismiss ──
        boolean anyHintRemoved = false;
        for (int i = activeHints.size() - 1; i >= 0; i--) {
            ActiveHint hint = activeHints.get(i);
            hint.tick();

            TextAnimationManager.Phase phase = hint.animation.getPhase();

            if (phase == TextAnimationManager.Phase.EXITING
                && hint.animation.isExitFinished()) {
                activeHints.remove(i);
                anyHintRemoved = true;
                continue;
            }

            if (phase == TextAnimationManager.Phase.CELEBRATING) continue;
            if (phase == TextAnimationManager.Phase.EXITING) continue;

            boolean explicitDismiss = isDismissedByExplicitTrigger(hint, activeTriggers);
            boolean timeUp = phase != TextAnimationManager.Phase.ENTERING && hint.isTimeOrTickExpired();
            boolean conditionFailed = isTriggerConditionUnsatisfied(hint, activeTriggers);
            boolean didDismiss = false;

            if (explicitDismiss && hint.rule.isGuideComplete()) {
                // 教程型 + 显式触发 → 庆祝动画
                // Guide-type + explicit trigger → celebrate animation
                hint.animation.startCelebrating();
                playCelebrateSound();
                didDismiss = true;
                HUDTips.LOGGER.debug("Guide-complete celebrate triggered for: {}", hint.rule.text);
            } else if (conditionFailed) {
                // 条件失效 → 立即移除（不标记已读）
                // Condition failed → remove immediately (do not mark as seen)
                activeHints.remove(i);
                anyHintRemoved = true;
            } else if (timeUp || explicitDismiss) {
                // 超时 / 非教程型的显式触发 → 渐出
                // Timeout / non-guide explicit trigger → exit fade-out
                hint.animation.startExiting();
                didDismiss = true;
            }

            if (didDismiss && hint.rule.isGuideComplete()) {
                SeenStateManager.markSeen(hint.rule.text);
            }
        }

        // ── 步骤 3：匹配新规则，添加新提示 / Step 3: Match new rules, add new hints ──
        if (anyTriggerChanged || anyHintRemoved) {
            needsInitialMatch = false;
            List<HintRule> matched = matchRulesFromTriggers(activeTriggers);
            if (!matched.isEmpty()) {
                HUDTips.LOGGER.debug("Matched {} rule(s) from active triggers: {}",
                    matched.size(), activeTriggers.keySet());
            }
            for (HintRule rule : matched) {
                tryAddOrRefreshHint(rule);
            }
            activeHints.sort(HINT_PRIORITY_COMPARATOR);
        }
    }

    // ============================================================
    //  提示队列管理
    //  Hint Queue Management
    // ============================================================

    private static boolean tryAddOrRefreshHint(HintRule rule) {
        if (rule.isGuideComplete() && SeenStateManager.isSeen(rule.text)) {
            return false;
        }

        boolean isItemDriven = isItemDrivenRule(rule);
        for (int i = activeHints.size() - 1; i >= 0; i--) {
            ActiveHint existing = activeHints.get(i);
            if (existing.rule.getComputedId() != null
                && existing.rule.getComputedId().equals(rule.getComputedId())) {
                if (existing.animation.getPhase() == TextAnimationManager.Phase.EXITING) {
                    activeHints.remove(i);
                    break;
                }
                return false;
            }
            if (isItemDriven && isItemDrivenRule(existing.rule)) {
                activeHints.remove(i);
            }
        }

        while (activeHints.size() >= MAX_ACTIVE_HINTS) {
            ActiveHint toRemove = null;
            for (ActiveHint h : activeHints) {
                if (h.animation.getPhase() == TextAnimationManager.Phase.EXITING) {
                    toRemove = h;
                    break;
                }
            }
            if (toRemove == null) {
                toRemove = activeHints.stream()
                    .max((a, b) -> compareRulePriority(a.rule, b.rule))
                    .orElse(null);
            }
            activeHints.remove(toRemove);
        }

        DismissConditions dc = DismissConditions.parse(rule, continuousTriggerCache);
        activeHints.add(new ActiveHint(rule, dc));
        return true;
    }

    // ============================================================
    //  规则匹配
    //  Rule Matching
    // ============================================================

    /**
     * 两轮匹配策略：
     * Two-round matching strategy:
     * <ol>
     *   <li>AND 模式：全量扫描 andModeRules 子集</li>
     *   <li>AND mode: Full scan of the andModeRules subset.</li>
     *   <li>ANY 模式：按触发器优先级遍历，同位置竞争择优</li>
     *   <li>ANY mode: Iterate by trigger priority; same-position candidates compete for the best.</li>
     * </ol>
     */
    private static List<HintRule> matchRulesFromTriggers(Map<String, List<Object>> activeTriggers) {
        if (activeTriggers.isEmpty()) return List.of();

        LinkedHashSet<HintRule> result = new LinkedHashSet<>();

        // ── 第一轮：AND 模式 / Round 1: AND mode ──
        for (HintRule rule : ruleIndex.getAndModeRules()) {
            if (rule.triggerOn == null || rule.triggerOn.isEmpty()) continue;
            if (rule.isGuideComplete() && SeenStateManager.isSeen(rule.text)) continue;
            if (checkAllTriggersMatch(rule, activeTriggers)) {
                result.add(rule);
            }
        }

        // ── 第二轮：ANY 模式 / Round 2: ANY mode ──
        Map<String, HintRule> bestForPosition = new HashMap<>(8);

        for (HudTrigger trigger : triggers) {
            String triggerType = trigger.getType();
            List<Object> identifiers = activeTriggers.get(triggerType);
            if (identifiers == null || identifiers.isEmpty()) continue;

            for (Object identifier : identifiers) {
                List<HintRule> candidates = ruleIndex.getCandidateRules(identifier);
                if (candidates == null) continue;

                for (HintRule rule : candidates) {
                    if (HudConfig.TRIGGER_MODE_ALL.equals(rule.triggerOnMode)) continue;
                    if (rule.triggerOn == null || !rule.triggerOn.containsKey(triggerType)) continue;
                    if (rule.isGuideComplete() && SeenStateManager.isSeen(rule.text)) continue;

                    // 对方块触发器：额外验证方块匹配
                    // For block trigger: extra block match validation
                    if (HudConfig.TRIGGER_ON_ACTIVATE_BLOCK.equals(triggerType)) {
                        String clickedBlock = OnActivateBlockTrigger.getClickedBlockId();
                        if (!rule.matchesBlock(clickedBlock)) continue;
                    }

                    String pos = rule.position != null ? rule.position : HudConfig.DEFAULT_POSITION;
                    HintRule existing = bestForPosition.get(pos);
                    if (existing == null || RULE_PRIORITY_COMPARATOR.compare(rule, existing) < 0) {
                        bestForPosition.put(pos, rule);
                    }
                }
            }
        }

        result.addAll(bestForPosition.values());
        return new ArrayList<>(result);
    }

    /** 验证 AND 模式规则的所有触发器条件 / Verify all trigger conditions for AND-mode rules */
    private static boolean checkAllTriggersMatch(HintRule rule, Map<String, List<Object>> activeTriggers) {
        for (String reqType : rule.triggerOn.keySet()) {
            List<Object> identifiers = activeTriggers.get(reqType);
            if (identifiers == null || identifiers.isEmpty()) return false;

            // Boolean.TRUE 值 → 只要触发器活跃即可，无需检查具体标识
            // Boolean.TRUE value → trigger just needs to be active, no specific identifier check needed
            // （例如 on_look_entity: true 表示"看向任意实体"）
            // (e.g. on_look_entity: true means "looking at any entity")
            Object triggerOnValue = rule.triggerOn.get(reqType);
            if (triggerOnValue instanceof Boolean && (Boolean) triggerOnValue) {
                continue;
            }

            boolean anyMatch = false;
            for (Object id : identifiers) {
                List<HintRule> candidates = ruleIndex.getCandidateRules(id);
                if (candidates != null && candidates.contains(rule)) {
                    anyMatch = true;
                    break;
                }
            }
            if (!anyMatch) return false;

            // AND 模式中 on_activate_block 也需验证方块匹配
            // In AND mode, on_activate_block also requires block match validation
            if (HudConfig.TRIGGER_ON_ACTIVATE_BLOCK.equals(reqType)) {
                if (!rule.matchesBlock(OnActivateBlockTrigger.getClickedBlockId())) return false;
            }
        }
        return true;
    }

    // ============================================================
    //  关闭条件判断
    //  Dismiss Condition Evaluation
    // ============================================================

    private static boolean isDismissedByExplicitTrigger(ActiveHint hint, Map<String, List<Object>> activeTriggers) {
        for (String dismissType : hint.dismissConditions.dismissTriggers) {
            if (isContinuousTrigger(dismissType)) continue;
            if (activeTriggers.containsKey(dismissType)) return true;
        }
        return false;
    }

    private static boolean isTriggerConditionUnsatisfied(ActiveHint hint, Map<String, List<Object>> activeTriggers) {
        if (hint.rule.triggerOn == null) return false;

        boolean isAllMode = HudConfig.TRIGGER_MODE_ALL.equals(hint.rule.triggerOnMode);
        if (isAllMode) {
            // "all" 模式：任一 continuous 触发器失效 → 条件不满足
            // "all" mode: any single continuous trigger failure → condition unsatisfied
            return hasAnyContinuousTriggerFailed(hint.rule, activeTriggers);
        } else {
            // "any" 模式：全部 continuous 触发器都失效才 → 条件不满足
            // "any" mode: all continuous triggers must fail → condition unsatisfied
            return hasAllContinuousTriggersFailed(hint.rule, activeTriggers);
        }
    }

    /**
     * 检查规则中是否<b>任一</b> continuous 触发器已失效（用于 "all" 模式）。
     * Checks whether <b>any</b> continuous trigger has failed (used in "all" mode).
     *
     * @return true 如果至少有一个 continuous 触发器不再活跃 / true if at least one continuous trigger is no longer active
     */
    private static boolean hasAnyContinuousTriggerFailed(HintRule rule, Map<String, List<Object>> activeTriggers) {
        for (HudTrigger trigger : triggers) {
            if (!trigger.isContinuous()) continue;
            if (!rule.triggerOn.containsKey(trigger.getType())) continue;

            List<Object> ids = activeTriggers.get(trigger.getType());
            if (ids == null || ids.isEmpty()) return true;
            if (!ruleMatchesAnyIdentifier(rule, ids)) return true;
        }
        return false;
    }

    /**
     * 检查规则中所有 continuous 触发器是否<b>全部</b>失效（用于 "any" 模式）。
     * Checks whether <b>all</b> continuous triggers have failed (used in "any" mode).
     *
     * <p>只要还有至少一个 continuous 触发器活跃，条件就仍然满足。
     * 如果规则中没有 continuous 触发器（纯 event 型），返回 false——
     * event 型触发器的消失由 dismissOn 控制，不在此处理。</p>
     * <p>As long as at least one continuous trigger remains active, the condition is still satisfied.
     * If the rule has no continuous triggers (pure event type), returns false —
     * event-type trigger dismissal is controlled by dismissOn, not handled here.</p>
     *
     * @return true 如果所有 continuous 触发器均已失效 / true if all continuous triggers have failed
     */
    private static boolean hasAllContinuousTriggersFailed(HintRule rule, Map<String, List<Object>> activeTriggers) {
        boolean hasContinuous = false;
        for (HudTrigger trigger : triggers) {
            if (!trigger.isContinuous()) continue;
            if (!rule.triggerOn.containsKey(trigger.getType())) continue;
            hasContinuous = true;

            List<Object> ids = activeTriggers.get(trigger.getType());
            if (ids != null && !ids.isEmpty() && ruleMatchesAnyIdentifier(rule, ids)) {
                return false; // 至少有一个 continuous 触发器仍活跃 → 条件未失效 / at least one continuous trigger still active → condition not yet failed
            }
        }
        return hasContinuous; // 有 continuous 触发器且全部失效 → 条件失效 / has continuous triggers and all failed → condition failed
    }

    // ============================================================
    //  触发器属性查询（使用缓存，O(1)）
    //  Trigger Property Queries (cached, O(1))
    // ============================================================

    private static boolean isContinuousTrigger(String type) {
        return continuousTriggerCache.getOrDefault(type, false);
    }

    private static boolean isItemBasedTrigger(String type) {
        return itemBasedTriggerCache.getOrDefault(type, false);
    }

    private static boolean isItemDrivenRule(HintRule rule) {
        if (rule.triggerOn == null) return false;
        for (String triggerType : rule.triggerOn.keySet()) {
            if (isItemBasedTrigger(triggerType)) return true;
        }
        return false;
    }

    private static boolean ruleMatchesAnyIdentifier(HintRule rule, List<Object> identifiers) {
        if (identifiers == null || identifiers.isEmpty()) return false;
        for (Object id : identifiers) {
            List<HintRule> candidates = ruleIndex.getCandidateRules(id);
            if (candidates != null && candidates.contains(rule)) return true;
        }
        return false;
    }

    // ============================================================
    //  优先级比较
    //  Priority Comparison
    // ============================================================

    private static final Comparator<ActiveHint> HINT_PRIORITY_COMPARATOR =
        (a, b) -> compareRulePriority(a.rule, b.rule);

    private static final Comparator<HintRule> RULE_PRIORITY_COMPARATOR =
        TriggerManager::compareRulePriority;

    public static int compareRulePriority(HintRule a, HintRule b) {
        // 第 1 层：教程型始终优先于普通型
        // Layer 1: Guide-type always takes priority over normal-type
        if (a.isGuideComplete() != b.isGuideComplete()) return a.isGuideComplete() ? -1 : 1;

        if (a.priority == null && b.priority == null) {
            boolean aTag = a.isTagBased();
            boolean bTag = b.isTagBased();
            if (aTag != bTag) return aTag ? 1 : -1;
        }

        boolean aExplicit = a.priority != null;
        boolean bExplicit = b.priority != null;
        if (aExplicit && bExplicit) return Integer.compare(b.priority, a.priority);
        if (aExplicit) return -1;
        if (bExplicit) return 1;

        return 0;
    }

    // ============================================================
    //  公共查询
    //  Public Queries
    // ============================================================

    public static List<ActiveHint> getActiveHints() {
        return Collections.unmodifiableList(activeHints);
    }

    public static int getActiveHintCount() {
        return activeHints.size();
    }

    public static List<HintRule> getAllRules() {
        return ruleIndex.getAllRules();
    }

    // ============================================================
    //  重置
    //  Reset
    // ============================================================

    public static void resetAll() {
        for (HudTrigger trigger : triggers) {
            trigger.reset();
        }
        activeHints.clear();
        needsInitialMatch = true;
    }

    /**
     * 请求在下一次 tick 重新匹配规则。
     * Requests rematching rules on the next tick.
     *
     * <p>用于外部操作（如按键重置已读状态）后立即触发提示重新显示。</p>
     * <p>Used after external operations (e.g. key-press reset of seen state)
     * to immediately trigger hint redisplay.</p>
     */
    public static void requestRematch() {
        needsInitialMatch = true;
    }

    public static int getTriggerCount() {
        return triggers.size();
    }

    // ============================================================
    //  音效
    //  Sound Effects
    // ============================================================

    private static void playCelebrateSound() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, 1.2f);
        }
    }
}
