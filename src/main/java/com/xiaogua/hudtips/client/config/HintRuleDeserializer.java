package com.xiaogua.hudtips.client.config;

import com.google.gson.*;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * HintRule 的 Gson 反序列化器。
 * Gson deserializer for HintRule.
 *
 * <p>职责： / Responsibilities:</p>
 * <ol>
 *   <li>处理 {@code "item"} 单字符串 → {@code "items"} 数组的自动转换</li>
 *   <li>Handle auto-conversion of {@code "item"} single string → {@code "items"} array</li>
 *   <li>处理 {@code "text"} 字段的多态性（单字符串 / 字符串数组）</li>
 *   <li>Handle polymorphism of the {@code "text"} field (single string / string array)</li>
 *   <li>将旧版 {@code triggerOn} 数组格式转为对象格式：
 *     {@code ["a","b"]} → {@code {"a":true,"b":true}}</li>
 *   <li>Convert legacy {@code triggerOn} array format to object format:
 *     {@code ["a","b"]} → {@code {"a":true,"b":true}}</li>
 * </ol>
 */
public class HintRuleDeserializer implements JsonDeserializer<HintRule> {

    /**
     * 缓存的 Gson 实例 —— 避免每条规则反序列化时重复创建。
     * Cached Gson instance — avoids repeated creation for each rule deserialization.
     */
    private static final Gson CACHED_GSON = new Gson();

    @Override
    public HintRule deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
        throws JsonParseException {

        JsonObject obj = json.getAsJsonObject();
        JsonObject normalized = new JsonObject();

        // ── 第一遍：收集已有 triggerOn Map（如果有的话）──
        // ── Pass 1: collect existing triggerOn Map (if any) ──
        JsonObject triggerOnMap = null;
        JsonElement toe = obj.get("triggerOn");
        if (toe != null && toe.isJsonObject()) {
            triggerOnMap = toe.getAsJsonObject();
        }

        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            String key = entry.getKey();
            JsonElement value = entry.getValue();

            // ── item 单字符串 → items 数组 ──
            // ── item single string → items array ──
            if ("item".equals(key) && value.isJsonPrimitive()) {
                if (!obj.has("items")) {
                    JsonArray itemsArr = new JsonArray();
                    itemsArr.add(value.getAsString());
                    normalized.add("items", itemsArr);
                }
                continue;
            }

            // ── text 多态：数组 → 拆分 ──
            // ── text polymorphism: array → split ──
            if ("text".equals(key) && value.isJsonArray()) {
                JsonArray arr = value.getAsJsonArray();
                List<String> textList = new ArrayList<>(arr.size());
                for (JsonElement e : arr) textList.add(e.getAsString());
                normalized.addProperty("text", textList.isEmpty() ? "" : textList.get(0));
                if (textList.size() > 1) {
                    JsonArray textsArr = new JsonArray();
                    for (String s : textList) textsArr.add(s);
                    normalized.add("texts", textsArr);
                }
                continue;
            }

            // ── 普通字段：原样传递 ──
            // ── Normal fields: pass through as-is ──
            normalized.add(key, value);
        }

        // ── 第二遍：构建最终的 triggerOn Map ──
        // ── Pass 2: build the final triggerOn Map ──
        JsonObject finalTriggerOn = new JsonObject();

        // 1) 先放入已有的 triggerOn Map（新版格式，直接保留）
        // 1) First put the existing triggerOn Map (new format, kept directly)
        if (triggerOnMap != null) {
            for (Map.Entry<String, JsonElement> e : triggerOnMap.entrySet()) {
                finalTriggerOn.add(e.getKey(), e.getValue());
            }
        }

        // 2) 旧 triggerOn 数组 → Map { name: true }
        // 2) Legacy triggerOn array → Map { name: true }
        if (toe != null && toe.isJsonArray()) {
            for (JsonElement e : toe.getAsJsonArray()) {
                String name = e.getAsString();
                if (!finalTriggerOn.has(name)) {
                    finalTriggerOn.addProperty(name, true);
                }
            }
        }

        if (finalTriggerOn.size() > 0) {
            normalized.add("triggerOn", finalTriggerOn);
        }

        return CACHED_GSON.fromJson(normalized, HintRule.class);
    }
}
