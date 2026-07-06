package com.xiaogua.hudtips.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.xiaogua.hudtips.Config;
import com.xiaogua.hudtips.HUDTips;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 教程型提示完成状态管理器 —— 按存档或全局持久化。
 * Tutorial hint completion state manager — persisted per-save or globally.
 *
 * <p>每个存档独立的 {@code hudtips_completed.json} 存储已完成教程的提示文本。
 * 文件位于存档根目录下（如 {@code saves/新世界/hudtips_completed.json}）。</p>
 * <p>Each save has its own {@code hudtips_completed.json} storing completed tutorial hint texts.
 * The file resides in the save root directory (e.g. {@code saves/NewWorld/hudtips_completed.json}).</p>
 *
 * <p>当 {@link Config#GLOBAL_SEEN_STATE} 启用时，改为使用全局文件
 * {@code config/hudtips/hudtips_completed_global.json}，
 * 教程完成状态在所有存档之间共享。</p>
 * <p>When {@link Config#GLOBAL_SEEN_STATE} is enabled, a global file
 * {@code config/hudtips/hudtips_completed_global.json} is used instead,
 * sharing tutorial completion state across all saves.</p>
 *
 * <h2>数据流 / Data Flow</h2>
 * <pre>
 * 教程型提示完成（dismissOn 事件触发时标记）
 * Tutorial hint completed (marked when dismissOn event fires)
 *     └── SeenStateManager.markSeen(text) → 写入存档文件 / write to save file
 *
 * 下次在同一存档触发同一文本的规则
 * Next time same-text rule triggers in same save
 *     └── SeenStateManager.isSeen(text) → true → 跳过不显示 / skip, don't show
 *
 * 不同存档之间互不影响（globalSeenState=false 时）
 * Different saves don't affect each other (when globalSeenState=false)
 * </pre>
 *
 * <p>以提示文本（{@link com.xiaogua.hudtips.client.config.HintRule#text}）为键，
 * 而非 computedId。这样即使 JSON 配置重组、section 名称变化，
 * 已读状态也不丢失；持久化文件也直接可读。</p>
 * <p>Keys by hint text ({@link com.xiaogua.hudtips.client.config.HintRule#text}),
 * not computedId. This way even if JSON configs are reorganized or section names change,
 * seen state is not lost; the persistence file is also directly human-readable.</p>
 *
 * @see com.xiaogua.hudtips.client.config.HintRule#type
 * @see com.xiaogua.hudtips.client.trigger.TriggerManager
 */
public class SeenStateManager {

    /** 私有构造函数，防止实例化（全静态工具类）。 / Private constructor to prevent instantiation (all-static utility class). */
    private SeenStateManager() {}

    /** 每个存档下的持久化文件名 / Per-save persistence file name */
    private static final String FILE_NAME = "hudtips_completed.json";

    /** 全局持久化文件名（globalSeenState=true 时使用，位于 config/hudtips/ 下） / Global persistence file name (used when globalSeenState=true, located under config/hudtips/) */
    private static final String GLOBAL_FILE_NAME = "hudtips_completed_global.json";

    /** Gson 实例 / Gson instance */
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** 已完成的教程型提示文本集合（按完成时间排序，以 {@link HintRule#text} 为键） / Set of completed tutorial hint texts (ordered by completion time, keyed by {@link HintRule#text}) */
    private static final Set<String> completedTexts = new LinkedHashSet<>();

    /** 当前存档的持久化文件路径，null 表示尚未进入世界 / Current save's persistence file path, null means no world loaded yet */
    private static Path saveFilePath = null;

    // ============================================================
    //  存档生命周期
    //  Save Lifecycle
    // ============================================================

    /**
     * 为此存档初始化完成状态管理器。
     * Initialize the completion state manager for this save.
     *
     * <p>在玩家进入世界时调用。加载存档已有的完成记录，
     * 清空上一存档的状态。不同存档的状态互不影响。</p>
     * <p>Called when the player enters a world. Loads existing completion records
     * for the save and clears the previous save's state. States across saves are independent.</p>
     *
     * <p>当 {@link Config#GLOBAL_SEEN_STATE} 为 true 时，
     * 改用全局文件（所有存档共享），不再按存档隔离。</p>
     * <p>When {@link Config#GLOBAL_SEEN_STATE} is true, a global file is used instead
     * (shared across all saves), no longer isolated per save.</p>
     *
     * @param worldDir 存档根目录路径（如 saves/新世界/），全局模式下忽略 / Save root directory path (e.g. saves/NewWorld/), ignored in global mode
     */
    public static void initForWorld(Path worldDir) {
        completedTexts.clear();

        if (Config.GLOBAL_SEEN_STATE.getAsBoolean()) {
            // 全局模式：所有存档共享已读状态
            // Global mode: all saves share seen state
            Path configDir = FMLPaths.CONFIGDIR.get();
            saveFilePath = configDir.resolve("hudtips").resolve(GLOBAL_FILE_NAME);
            HUDTips.LOGGER.debug("Using global seen state file: {}", saveFilePath);
        } else {
            // 按存档隔离（默认）
            // Per-save isolation (default)
            saveFilePath = worldDir != null ? worldDir.resolve(FILE_NAME) : null;
        }

        loadFromFile();
        HUDTips.LOGGER.info("Loaded {} completed tutorial hint(s) for this {}.",
            completedTexts.size(),
            Config.GLOBAL_SEEN_STATE.getAsBoolean() ? "profile (global)" : "save");
    }

    /** 从存档文件加载已完成文本列表 / Load completed text list from save file */
    private static void loadFromFile() {
        if (saveFilePath == null || !Files.exists(saveFilePath)) return;

        try (var reader = Files.newBufferedReader(saveFilePath, StandardCharsets.UTF_8)) {
            Type type = new TypeToken<LinkedHashSet<String>>() {}.getType();
            Set<String> loaded = GSON.fromJson(reader, type);
            if (loaded != null) {
                completedTexts.addAll(loaded);
            }
        } catch (Exception e) {
            HUDTips.LOGGER.error("Failed to load completed hints file!", e);
        }
    }

    /** 将已完成文本列表写入存档文件 / Write completed text list to save file */
    private static void saveToFile() {
        if (saveFilePath == null) return;

        try {
            Files.createDirectories(saveFilePath.getParent());
            try (var writer = Files.newBufferedWriter(saveFilePath, StandardCharsets.UTF_8)) {
                GSON.toJson(completedTexts, writer);
            }
        } catch (IOException e) {
            HUDTips.LOGGER.error("Failed to save completed hints file!", e);
        }
    }

    // ============================================================
    //  公共 API
    //  Public API
    // ============================================================

    /**
     * 检查指定文本的教程是否已完成。
     * Check whether the tutorial for the specified text has been completed.
     *
     * @param text 提示文本（{@link HintRule#text}），为 null 或空时返回 false / Hint text ({@link HintRule#text}), returns false if null or empty
     */
    public static boolean isSeen(String text) {
        if (text == null || text.isEmpty()) return false;
        return completedTexts.contains(text);
    }

    /**
     * 标记教程文本为已完成并持久化到存档文件。
     * Mark tutorial text as completed and persist to the save file.
     *
     * @param text 提示文本（{@link HintRule#text}），为 null 或空时忽略 / Hint text ({@link HintRule#text}), ignored if null or empty
     */
    public static void markSeen(String text) {
        if (text == null || text.isEmpty()) return;
        if (completedTexts.add(text)) {
            saveToFile();
        }
    }

    /**
     * 清除指定文本的完成状态并持久化。
     * Clear completion state for the specified text and persist.
     *
     * @param text 提示文本，为 null 或空时忽略 / Hint text, ignored if null or empty
     */
    public static void unsee(String text) {
        if (text == null || text.isEmpty()) return;
        if (completedTexts.remove(text)) {
            saveToFile();
        }
    }

    /**
     * 清除所有完成状态并持久化。
     * Clear all completion states and persist.
     */
    public static void resetAll() {
        if (!completedTexts.isEmpty()) {
            completedTexts.clear();
            saveToFile();
        }
    }

    /** @return 已完成教程文本集合（只读快照） / Set of completed tutorial texts (read-only snapshot) */
    public static Set<String> getSeenTexts() {
        return Set.copyOf(completedTexts);
    }

    /** @return 已完成教程数量 / Number of completed tutorials */
    public static int getSeenCount() {
        return completedTexts.size();
    }

    // ============================================================
    //  工具
    //  Utilities
    // ============================================================

    /**
     * 从 Minecraft 实例获取当前存档路径。
     * Get the current save path from the Minecraft instance.
     *
     * @return 存档根目录，无法获取时返回 null / Save root directory, null if unavailable
     */
    public static Path resolveWorldPath() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return null;

        // 单人存档 / Singleplayer save
        if (mc.hasSingleplayerServer()) {
            return mc.getSingleplayerServer()
                .getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
        }
        // 多人/ realms：使用服务器地址作为标识，存于 gameDir
        // Multiplayer/Realms: use server address as identifier, stored under gameDir
        if (mc.getCurrentServer() != null) {
            String addr = mc.getCurrentServer().ip.replaceAll("[^a-zA-Z0-9._-]", "_");
            return net.neoforged.fml.loading.FMLPaths.GAMEDIR.get().resolve("hudtips_servers").resolve(addr);
        }
        return null;
    }
}
