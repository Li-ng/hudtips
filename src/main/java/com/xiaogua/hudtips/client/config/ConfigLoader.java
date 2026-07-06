package com.xiaogua.hudtips.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.xiaogua.hudtips.HUDTips;
import com.xiaogua.hudtips.client.trigger.TriggerManager;
import net.neoforged.fml.loading.FMLPaths;

import java.io.StringReader;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * JSON 配置加载器。
 * JSON configuration loader.
 *
 * <p>负责读取和解析配置文件，将 JSON 数据转换为内部数据结构供运行时使用。</p>
 * <p>Responsible for reading and parsing configuration files, converting JSON data into internal data structures for runtime use.</p>
 *
 * <h2>配置文件目录： / Configuration File Directory:</h2>
 * <pre>
 * .minecraft/config/hudtips/
 *     ├── hudtips-client.toml    ← NeoForge TOML 配置（游戏内可改） / NeoForge TOML config (modifiable in-game)
 *     └── *.json                 ← 整合包作者创建的规则文件（按文件名升序加载，_ 开头跳过） / Rule files created by modpack authors (loaded in ascending filename order, files starting with _ are skipped)
 * </pre>
 *
 * <h2>加载规则： / Loading Rules:</h2>
 * <ul>
 *   <li>扫描目录下所有 {@code .json} 文件，按文件名升序加载</li>
 *   <li>Scan all {@code .json} files in the directory and load them in ascending filename order</li>
 *   <li>以下划线 {@code _} 开头的文件<b>跳过不加载</b></li>
 *   <li>Files starting with underscore {@code _} are <b>skipped and not loaded</b></li>
 *   <li>多文件按全局标量覆盖、触发器合并、规则列表追加的方式深度合并</li>
 *   <li>Multiple files are deeply merged by global scalar override, trigger merging, and rule list appending</li>
 * </ul>
 *
 * <h2>热重载： / Hot Reload:</h2>
 * <p>每次玩家加入世界时会自动调用 {@link #loadConfig()} 重新加载配置。
 * 整合包作者修改 JSON 后，只需重新进入世界即可生效。</p>
 * <p>{@link #loadConfig()} is automatically called each time the player joins a world to reload the configuration.
 * Modpack authors only need to re-enter the world after modifying JSON files for changes to take effect.</p>
 *
 * @see HudConfig
 * @see TriggerConfig
 * @see HintRule
 */
public class ConfigLoader {

    /**
     * 私有构造函数，防止实例化（全静态工具类）。
     * Private constructor to prevent instantiation (fully static utility class).
     */
    private ConfigLoader() {}

    /**
     * 配置文件目录名（位于 config/ 下）。
     * Configuration file directory name (located under config/).
     */
    private static final String CONFIG_FOLDER = "hudtips";

    /**
     * 当前加载的配置对象。
     * Currently loaded configuration object.
     */
    private static HudConfig currentConfig = new HudConfig();

    // ============================================================
    //  文件监听（热重载）
    //  File Watcher (Hot Reload)
    // ============================================================

    /**
     * WatchService 守护线程，null 表示未启动。
     * WatchService daemon thread, null means not started.
     */
    private static Thread watcherThread = null;

    /**
     * 标记：文件已变更，等待下一个 tick 重载。由 watcher 线程写入，主线程读取。
     * Flag: file has changed, waiting for the next tick to reload. Written by the watcher thread, read by the main thread.
     */
    private static volatile boolean pendingReload = false;

    /**
     * 上次重载时间戳（毫秒），用于防抖——避免 IDE 自动保存导致连续重载。
     * Last reload timestamp (milliseconds), used for debouncing—avoids consecutive reloads caused by IDE auto-save.
     */
    private static volatile long lastReloadTime = 0;

    /**
     * 文件变更后延迟重载的毫秒数（防抖窗口）。
     * Delay in milliseconds before reloading after a file change (debounce window).
     */
    private static final long RELOAD_DEBOUNCE_MS = 500;

    /**
     * 已启动监听的目录路径，用于避免重复启动。
     * Directory path already being watched, used to avoid duplicate starts.
     */
    private static Path watchedDir = null;

    /**
     * 加载 JSON 配置（入口方法）。
     * Load JSON configuration (entry point method).
     *
     * <p>扫描 {@code config/hudtips/} 目录下所有非 _ 开头的 .json 文件并合并加载。
     * 目录不存在时自动创建。</p>
     * <p>Scans all .json files not starting with _ under the {@code config/hudtips/} directory and merges them.
     * Automatically creates the directory if it does not exist.</p>
     */
    public static void loadConfig() {
        currentConfig = new HudConfig();

        Path configDir = FMLPaths.CONFIGDIR.get();
        Path folderPath = configDir.resolve(CONFIG_FOLDER);

        // 确保目录存在
        // Ensure the directory exists
        try {
            Files.createDirectories(folderPath);
        } catch (Exception e) {
            HUDTips.LOGGER.error("Failed to create config directory: {}", folderPath, e);
            return;
        }

        loadConfigDir(folderPath);

        // 首次加载后启动文件监听（幂等，多次调用只启动一次）
        // Start file watcher after initial load (idempotent, only starts once across multiple calls)
        startFileWatcher(folderPath);
    }

    // ============================================================
    //  文件监听（热重载）
    //  File Watcher (Hot Reload)
    // ============================================================

    /**
     * 启动文件监听守护线程，监控配置目录的 .json 文件变化。
     * Starts a file watcher daemon thread to monitor .json file changes in the config directory.
     *
     * <p>当检测到 .json 文件被创建、修改或删除时，设置 {@link #pendingReload} 标记。
     * 实际重载由主线程在下一 tick 通过 {@link #checkPendingReload()} 执行。</p>
     * <p>When a .json file is detected as created, modified, or deleted, sets the {@link #pendingReload} flag.
     * The actual reload is performed by the main thread on the next tick via {@link #checkPendingReload()}.</p>
     *
     * <p>使用 500ms 防抖窗口避免 IDE 自动保存导致的连续重载。</p>
     * <p>Uses a 500ms debounce window to avoid consecutive reloads caused by IDE auto-save.</p>
     *
     * @param folderPath 要监听的目录 / The directory to watch
     */
    private static void startFileWatcher(Path folderPath) {
        // 已经在监听同一目录 → 跳过
        // Already watching the same directory → skip
        if (watcherThread != null && watcherThread.isAlive() && folderPath.equals(watchedDir)) {
            return;
        }

        // 停止旧的监听器
        // Stop the old watcher
        stopFileWatcher();

        try {
            WatchService watchService = FileSystems.getDefault().newWatchService();
            folderPath.register(watchService, StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE);
            watchedDir = folderPath;

            watcherThread = new Thread(() -> {
                HUDTips.LOGGER.info("Config file watcher started on: {}", folderPath);
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        WatchKey key = watchService.take();
                        boolean hasJsonChange = false;

                        for (WatchEvent<?> event : key.pollEvents()) {
                            WatchEvent.Kind<?> kind = event.kind();
                            if (kind == StandardWatchEventKinds.OVERFLOW) continue;

                            Path fileName = (Path) event.context();
                            String name = fileName.toString();
                            // 只关注非 _ 开头的 .json 文件
                            // Only care about .json files not starting with _
                            if (name.endsWith(".json") && !name.startsWith("_")) {
                                hasJsonChange = true;
                                HUDTips.LOGGER.debug("Config file changed ({}): {}", kind.name(), name);
                            }
                        }

                        if (hasJsonChange) {
                            pendingReload = true;
                        }

                        if (!key.reset()) {
                            HUDTips.LOGGER.warn("Config watch key became invalid, stopping watcher.");
                            break;
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        HUDTips.LOGGER.error("Error in config file watcher", e);
                    }
                }
                HUDTips.LOGGER.info("Config file watcher stopped.");
            }, "HUDTips-ConfigWatcher");
            watcherThread.setDaemon(true);
            watcherThread.start();

        } catch (Exception e) {
            HUDTips.LOGGER.warn("Failed to start config file watcher: {}. Hot-reload disabled.", e.getMessage());
        }
    }

    /**
     * 停止文件监听守护线程。
     * Stop the file watcher daemon thread.
     */
    private static void stopFileWatcher() {
        if (watcherThread != null && watcherThread.isAlive()) {
            watcherThread.interrupt();
            try {
                watcherThread.join(2000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
        watchedDir = null;
    }

    /**
     * 检查是否有待处理的热重载请求，如有则在主线程执行重载。
     * Checks if there is a pending hot-reload request, and if so, executes the reload on the main thread.
     *
     * <p>由 {@link com.xiaogua.hudtips.client.ClientTickHandler#onClientTick}
     * 在每个游戏刻调用。防抖：距离上次重载不足 500ms 则推迟到下一 tick。</p>
     * <p>Called by {@link com.xiaogua.hudtips.client.ClientTickHandler#onClientTick}
     * on every game tick. Debounce: defers to the next tick if less than 500ms since the last reload.</p>
     *
     * @return true 如果执行了重载 / true if a reload was performed
     */
    public static boolean checkPendingReload() {
        if (!pendingReload) return false;

        long now = System.currentTimeMillis();
        if (now - lastReloadTime < RELOAD_DEBOUNCE_MS) {
            // 防抖：延迟到下一 tick
            // Debounce: defer to the next tick
            return false;
        }

        pendingReload = false;
        lastReloadTime = now;

        HUDTips.LOGGER.info("Config file change detected, hot-reloading...");
        try {
            loadConfig();
            TriggerManager.initTriggers();
            HUDTips.LOGGER.info("Config hot-reload complete.");
        } catch (Exception e) {
            HUDTips.LOGGER.error("Failed to hot-reload config", e);
        }
        return true;
    }

    // ============================================================
    //  文件夹加载
    //  Directory Loading
    // ============================================================

    /**
     * 从配置目录扫描并加载所有 JSON 文件，合并为一个 HudConfig。
     * Scans and loads all JSON files from the config directory, merging them into a single HudConfig.
     *
     * <p>合并策略： / Merge Strategy:</p>
     * <ul>
     *   <li>全局标量字段：后加载覆盖先加载</li>
     *   <li>Global scalar fields: later-loaded overrides earlier-loaded</li>
     *   <li>{@code triggers} Map：合并，同一触发器类型下 rules 列表追加</li>
     *   <li>{@code triggers} Map: merged, with rules lists under the same trigger type appended</li>
     *   <li>同一触发器的标量默认值（defaultColor 等）：后加载覆盖先加载</li>
     *   <li>Scalar defaults for the same trigger (defaultColor, etc.): later-loaded overrides earlier-loaded</li>
     * </ul>
     */
    private static void loadConfigDir(Path folderPath) {
        List<Path> jsonFiles;
        try (Stream<Path> stream = Files.list(folderPath)) {
            jsonFiles = stream
                .filter(p -> {
                    String name = p.getFileName().toString();
                    return name.endsWith(".json") && !name.startsWith("_");
                })
                .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                .toList();
        } catch (Exception e) {
            HUDTips.LOGGER.error("Failed to list config directory: {}", folderPath, e);
            return;
        }

        if (jsonFiles.isEmpty()) {
            HUDTips.LOGGER.warn("No loadable .json files found in {}, using defaults.", folderPath);
            return;
        }

        HUDTips.LOGGER.info("Loading {} config file(s) from {}", jsonFiles.size(), folderPath);
        for (Path f : jsonFiles) {
            HUDTips.LOGGER.debug("  → {}", f.getFileName());
        }

        try {
            // 先解析为 JsonObject，在 JSON 层面做深度合并，最后反序列化为 HudConfig
            // Parse as JsonObject first, deep-merge at the JSON level, then deserialize to HudConfig
            JsonObject merged = null;
            for (Path jsonFile : jsonFiles) {
                JsonObject partial = parseToJsonObject(jsonFile);
                if (partial == null) continue;

                if (merged == null) {
                    merged = partial;
                } else {
                    merged = deepMergeConfigs(merged, partial);
                }
            }

            if (merged == null) {
                HUDTips.LOGGER.warn("All config files failed to parse, using defaults.");
                return;
            }

            // 从合并后的 JsonObject 反序列化
            // Deserialize from the merged JsonObject
            HudConfig config = deserializeHudConfig(merged);
            if (config == null) {
                return;
            }

            // 补充 null 字段的默认值
            // Fill in default values for null fields
            if (config.defaultColor == null) config.defaultColor = "#FFFFFF";
            if (config.backgroundColor == null) config.backgroundColor = "#00000000";
            if (config.position == null) config.position = HudConfig.DEFAULT_POSITION;
            if (config.triggers == null) config.triggers = new HashMap<>();

            currentConfig = config;
            HUDTips.LOGGER.info("Config loaded successfully from {} file(s).", jsonFiles.size());

            // 校验配置（无效物品 ID、拼写错误的字段名等）
            // Validate configuration (invalid item IDs, misspelled field names, etc.)
            ConfigValidator.validate(currentConfig, merged);
        } catch (Exception e) {
            HUDTips.LOGGER.error("Failed to load config from directory! Using empty configuration.", e);
        }
    }

    /**
     * 在 JsonObject 层面深度合并两个配置文件。
     * Deep-merges two configuration files at the JsonObject level.
     *
     * <p>合并规则： / Merge Rules:</p>
     * <ul>
     *   <li>相同 key 的标量/数组：后加载（override）覆盖</li>
     *   <li>Scalars/arrays with the same key: later-loaded (override) overwrites</li>
     *   <li>{@code triggers} 子对象：深度合并——同一触发器类型下 rules 列表追加</li>
     *   <li>{@code triggers} sub-object: deep-merged—rules lists under the same trigger type are appended</li>
     *   <li>以 {@code //} 开头的注释键忽略</li>
     *   <li>Comment keys starting with {@code //} are ignored</li>
     * </ul>
     */
    private static JsonObject deepMergeConfigs(JsonObject base, JsonObject override) {
        JsonObject result = base.deepCopy();

        for (Map.Entry<String, JsonElement> entry : override.entrySet()) {
            String key = entry.getKey();
            JsonElement value = entry.getValue();

            // 跳过注释键
            // Skip comment keys
            if (key.startsWith("//")) continue;

            if ("triggers".equals(key) && value.isJsonObject() && result.has("triggers")
                && result.get("triggers").isJsonObject()) {
                // triggers → 深度合并
                // triggers → deep merge
                JsonObject mergedTriggers = deepMergeTriggers(
                    result.getAsJsonObject("triggers"),
                    value.getAsJsonObject());
                result.add("triggers", mergedTriggers);
            } else {
                // 标量/数组 → 直接覆盖
                // Scalar/array → direct overwrite
                result.add(key, value.deepCopy());
            }
        }

        return result;
    }

    /**
     * 深度合并 triggers Map。
     * Deep-merges the triggers Map.
     *
     * <p>同一触发器类型：标量字段覆盖，rules 列表追加，settings 合并。
     * 新触发器类型：直接添加。</p>
     * <p>Same trigger type: scalar fields overwrite, rules lists append, settings merge.
     * New trigger type: added directly.</p>
     */
    private static JsonObject deepMergeTriggers(JsonObject base, JsonObject override) {
        JsonObject result = base.deepCopy();

        for (Map.Entry<String, JsonElement> entry : override.entrySet()) {
            String triggerType = entry.getKey();
            if (triggerType.startsWith("//")) continue;

            JsonElement overrideElem = entry.getValue();
            if (!overrideElem.isJsonObject()) {
                result.add(triggerType, overrideElem.deepCopy());
                continue;
            }

            JsonObject overrideTrigger = overrideElem.getAsJsonObject();

            if (result.has(triggerType) && result.get(triggerType).isJsonObject()) {
                // 同一触发器 → 合并
                // Same trigger → merge
                JsonObject baseTrigger = result.getAsJsonObject(triggerType);
                result.add(triggerType, deepMergeTriggerConfig(baseTrigger, overrideTrigger));
            } else {
                // 新触发器类型 → 直接添加
                // New trigger type → add directly
                result.add(triggerType, overrideTrigger.deepCopy());
            }
        }

        return result;
    }

    /**
     * 合并同一触发器类型的两份配置。
     * Merges two configurations of the same trigger type.
     *
     * <p>{@code rules} 数组：追加合并。
     * {@code settings} 对象：合并（后加载覆盖）。
     * 其他字段（defaultColor, position 等）：后加载覆盖。</p>
     * <p>{@code rules} array: appended and merged.
     * {@code settings} object: merged (later-loaded overwrites).
     * Other fields (defaultColor, position, etc.): later-loaded overwrites.</p>
     */
    private static JsonObject deepMergeTriggerConfig(JsonObject base, JsonObject override) {
        JsonObject result = base.deepCopy();

        for (Map.Entry<String, JsonElement> entry : override.entrySet()) {
            String key = entry.getKey();
            JsonElement value = entry.getValue();

            if ("rules".equals(key) && value.isJsonArray()) {
                // rules → 追加合并（不是覆盖！）
                // rules → append and merge (not overwrite!)
                JsonArray mergedRules = new JsonArray();
                if (result.has("rules") && result.get("rules").isJsonArray()) {
                    for (JsonElement e : result.getAsJsonArray("rules")) {
                        mergedRules.add(e.deepCopy());
                    }
                }
                for (JsonElement e : value.getAsJsonArray()) {
                    mergedRules.add(e.deepCopy());
                }
                result.add("rules", mergedRules);
            } else if ("settings".equals(key) && value.isJsonObject()) {
                // settings → 合并
                // settings → merge
                JsonObject mergedSettings = result.has("settings") && result.get("settings").isJsonObject()
                    ? result.getAsJsonObject("settings").deepCopy()
                    : new JsonObject();
                for (Map.Entry<String, JsonElement> se : value.getAsJsonObject().entrySet()) {
                    mergedSettings.add(se.getKey(), se.getValue().deepCopy());
                }
                result.add("settings", mergedSettings);
            } else if (!key.startsWith("//")) {
                // 标量字段 → 覆盖
                // Scalar field → overwrite
                result.add(key, value.deepCopy());
            }
        }

        return result;
    }

    // ============================================================
    //  JSON 解析工具
    //  JSON Parsing Utilities
    // ============================================================

    /**
     * 读取 JSON 文件并解析为 JsonObject。
     * Reads a JSON file and parses it into a JsonObject.
     *
     * <p>支持两种文件格式： / Supports two file formats:</p>
     * <ul>
     *   <li>{@code { ... }} — 完整对象格式（向后兼容） / Full object format (backward compatible)</li>
     *   <li>{@code [ {...}, ... ]} — 纯规则数组格式，自动包装为内部结构 / Flat rule array format, automatically wrapped into internal structure</li>
     * </ul>
     */
    private static JsonObject parseToJsonObject(Path filePath) {
        try {
            String rawContent = Files.readString(filePath);
            String cleaned = stripJsonCommentLines(rawContent);
            cleaned = cleaned.replaceAll(",\\s*}", "}");
            cleaned = cleaned.replaceAll(",\\s*]", "]");

            JsonReader jsonReader = new JsonReader(new StringReader(cleaned));
            jsonReader.setLenient(true);
            JsonElement root = JsonParser.parseReader(jsonReader);

            JsonObject result;
            if (root.isJsonArray()) {
                // 纯规则数组 → 移除注释字符串后自动包装
                // Flat rule array → remove comment strings and auto-wrap
                JsonArray clean = new JsonArray();
                for (JsonElement el : root.getAsJsonArray()) {
                    if (el.isJsonPrimitive()) {
                        String s = el.getAsString();
                        if (s.stripLeading().startsWith("//")) continue;
                    }
                    clean.add(el);
                }
                result = new JsonObject();
                JsonObject triggers = new JsonObject();
                JsonObject section = new JsonObject();
                section.add("rules", clean);
                triggers.add("hold_item", section);
                result.add("triggers", triggers);
                HUDTips.LOGGER.debug("Parsed config file as flat rule array: {}", filePath);
            } else {
                result = root.getAsJsonObject();
            }

            stripCommentKeys(result, "triggers");
            return result;
        } catch (Exception e) {
            HUDTips.LOGGER.error("Failed to parse config file: {}", filePath, e);
            return null;
        }
    }

    /**
     * 从 JsonObject 反序列化为 HudConfig。
     * Deserializes a JsonObject into a HudConfig.
     */
    private static HudConfig deserializeHudConfig(JsonObject root) {
        Gson gson = new GsonBuilder()
            .registerTypeAdapter(HintRule.class, new HintRuleDeserializer())
            .create();
        HudConfig config = gson.fromJson(root, HudConfig.class);

        if (config == null) {
            HUDTips.LOGGER.warn("Config is empty or invalid, using defaults.");
            return null;
        }
        return config;
    }

    // ============================================================
    //  默认值填充
    //  Default Value Population
    // ============================================================

    /**
     * 应用三层默认值继承。
     * Applies three-level default value inheritance.
     *
     * <p>对于每个可配置字段，按以下优先级填充默认值：</p>
     * <p>For each configurable field, fills in defaults with the following priority:</p>
     * <pre>
     * 规则自身设置 > 触发器默认值 > 全局默认值
     * Rule's own setting > Trigger default > Global default
     * </pre>
     *
     * @param rule    要填充默认值的规则 / The rule to fill defaults for
     * @param trigger 触发器配置（提供触发器级默认值） / Trigger configuration (provides trigger-level defaults)
     * @param global  全局配置（提供全局默认值） / Global configuration (provides global defaults)
     */
    public static void applyDefaults(HintRule rule, TriggerConfig trigger, HudConfig global) {
        // 文字颜色：规则 → 触发器 → 全局
        // Text color: rule → trigger → global
        if (rule.color == null) {
            rule.color = trigger.defaultColor != null ? trigger.defaultColor : global.defaultColor;
        }

        // 背景颜色：规则 → 触发器 → 全局
        // Background color: rule → trigger → global
        if (rule.backgroundColor == null) {
            rule.backgroundColor = trigger.backgroundColor != null ? trigger.backgroundColor : global.backgroundColor;
        }

        // 锚点位置：规则 → 触发器 → 全局
        // Anchor position: rule → trigger → global
        rule.positionExplicit = (rule.position != null);
        if (rule.position == null) {
            rule.position = trigger.position != null ? trigger.position : global.position;
        }

        // X 偏移：规则 → 触发器 → 全局
        // X offset: rule → trigger → global
        if (rule.offsetX == null) {
            rule.offsetX = trigger.offsetX != null ? trigger.offsetX : global.offsetX;
        }

        // Y 偏移：规则 → 触发器 → 全局
        // Y offset: rule → trigger → global
        if (rule.offsetY == null) {
            rule.offsetY = trigger.offsetY != null ? trigger.offsetY : global.offsetY;
        }

        // 文字缩放：规则 → 触发器 → 全局
        // Text scale: rule → trigger → global
        if (rule.textScale == null) {
            rule.textScale = trigger.textScale != null ? trigger.textScale : global.textScale;
        }
    }

    /**
     * 获取当前加载的全局配置对象。
     * Gets the currently loaded global configuration object.
     *
     * @return 当前配置，如果未加载则返回默认配置 / The current config, or default config if not loaded
     */
    public static HudConfig getCurrentConfig() {
        return currentConfig;
    }

    /**
     * 获取指定触发器类型的配置。
     * Gets the configuration for a specific trigger type.
     *
     * <p>用于获取触发器级别的默认值设置。</p>
     * <p>Used to obtain trigger-level default value settings.</p>
     *
     * @param triggerType 触发器类型名称 / Trigger type name
     * @return 该触发器的配置，如果不存在则返回 null / The trigger's configuration, or null if not found
     */
    public static TriggerConfig getTriggerConfig(String triggerType) {
        return currentConfig.triggers != null ? currentConfig.triggers.get(triggerType) : null;
    }

    // ============================================================
    //  JSON 注释处理
    //  JSON Comment Handling
    // ============================================================

    /**
     * 移除 JSON 文本中的注释行。
     * Removes comment lines from JSON text.
     *
     * <p>逐行扫描，跳过以 {@code "//} 开头且以 {@code ": ""} 结尾的注释行。</p>
     * <p>Scans line by line, skipping lines that start with {@code "//} and end with {@code ": ""}.</p>
     */
    private static String stripJsonCommentLines(String rawJson) {
        StringBuilder sb = new StringBuilder(rawJson.length());
        for (String line : rawJson.split("\n", -1)) {
            String trimmed = line.stripLeading();
            if (trimmed.startsWith("\"//") && trimmed.endsWith("\": \"\",")) {
                continue;
            }
            if (trimmed.startsWith("\"//") && trimmed.endsWith("\": \"\"")) {
                continue;
            }
            sb.append(line).append('\n');
        }
        return sb.toString();
    }

    /**
     * 递归移除 JSON 对象中以 "//" 开头的注释键。
     * Recursively removes comment keys starting with "//" from a JSON object.
     */
    private static void stripCommentKeys(JsonObject parent, String fieldName) {
        JsonElement elem = parent.get(fieldName);
        if (elem == null || !elem.isJsonObject()) return;

        JsonObject obj = elem.getAsJsonObject();
        var toRemove = new ArrayList<String>();
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            if (entry.getKey().startsWith("//")) {
                toRemove.add(entry.getKey());
            }
        }
        for (String key : toRemove) {
            obj.remove(key);
        }
    }
}
