package com.xiaogua.hudtips.client;

import com.mojang.blaze3d.platform.Window;
import com.xiaogua.hudtips.Config;
import com.xiaogua.hudtips.HUDTips;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;

import java.util.List;

/**
 * HUD 提示渲染器 —— 将提示队列绘制到屏幕上。
 *
 * <p>每帧通过 {@link RenderGuiLayerEvent.Post} 事件触发渲染。</p>
 *
 * <h2>多提示排列规则</h2>
 *
 * <b>顶部锚点（top_*）</b>：从上到下排列。
 * <pre>
 * ┌──────────────────────┐
 * │ 提示 1  (最上方)      │  ← offsetY + padding
 * │ 提示 2               │  ← + textHeight + gap
 * │ 提示 3  (最下方)      │
 * └──────────────────────┘
 * </pre>
 *
 * <b>底部锚点（bottom_*）</b>：从下往上排列（第一个提示在最下方）。
 * <pre>
 * ┌──────────────────────┐
 * │ 提示 3  (最上方)      │
 * │ 提示 2               │
 * │ 提示 1  (最下方)      │  ← screenHeight - offsetY - padding
 * └──────────────────────┘
 * </pre>
 *
 * @see HintManager
 * @see HintManager.HintRenderData
 */
@EventBusSubscriber(modid = HUDTips.MODID, value = Dist.CLIENT)
public class ClientHudRenderer {

    /** 多条提示之间的垂直间距（像素） / Vertical gap between multiple hints (pixels) */
    private static final int HINT_GAP = 4;
    /** 背景矩形超出文字的边距（像素），防止文字贴边 / Margin of background rectangle beyond text (pixels), prevents text from touching edges */
    private static final int BG_PADDING = 2;

    /**
     * GUI 渲染后事件 —— 每帧调用一次。
     * Post-GUI-render event — called once per frame.
     *
     * <h3>渲染流程：/ Render Pipeline:</h3>
     * <ol>
     *   <li>前置检查 — 模组启用、玩家存在 / Pre-checks — mod enabled, player exists</li>
     *   <li>获取提示列表 — 空则跳过 / Get hint list — skip if empty</li>
     *   <li>获取渲染上下文 — 屏幕尺寸、内边距 / Get render context — screen size, padding</li>
     *   <li>逐条渲染 — 坐标计算 → 矩阵变换 → 背景 → 文字 / Render per hint — coordinate calc → matrix transform → background → text</li>
     * </ol>
     */
    @SubscribeEvent
    public static void onRenderGui(RenderGuiLayerEvent.Post event) {
        // ── 第 1 层：前置检查 ──
        // ── Layer 1: Pre-checks ──
        if (!Config.ENABLED.getAsBoolean()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.font == null) return;

        // ── 第 2 层：获取提示列表 ──
        // ── Layer 2: Get Hint List ──
        List<HintManager.HintRenderData> hints = HintManager.getRenderDataList();
        if (hints.isEmpty()) return;

        // ── 第 3 层：渲染上下文 ──
        // ── Layer 3: Render Context ──
        GuiGraphicsExtractor graphics = event.getGuiGraphics();
        Window window = mc.getWindow();
        int screenW = window.getGuiScaledWidth();
        int screenH = window.getGuiScaledHeight();
        int padding = HintManager.getCurrentPadding();

        // ── 第 4 层：逐条渲染 ──
        // ── Layer 4: Render Per Hint ──
        int yOffset = 0;
        for (HintManager.HintRenderData hint : hints) {
            yOffset += renderHint(graphics, mc, hint, screenW, screenH, padding, yOffset);
        }
    }

    // ============================================================
    //  单条提示渲染
    //  Single Hint Rendering
    // ============================================================

    /**
     * 渲染单条 HUD 提示。
     * Render a single HUD hint.
     *
     * <h3>子步骤：/ Sub-steps:</h3>
     * <ol>
     *   <li>计算文字尺寸 / Calculate text dimensions</li>
     *   <li>计算复选框尺寸（教程型规则专用） / Calculate checkbox size (tutorial-type rules only)</li>
     *   <li>计算屏幕坐标（含复选框宽度） / Calculate screen coordinates (including checkbox width)</li>
     *   <li>应用 2D 变换矩阵 / Apply 2D transform matrix</li>
     *   <li>绘制背景矩形（如有复选框则向左扩展） / Draw background rect (extend left if checkbox present)</li>
     *   <li>绘制复选框（空框或打勾） / Draw checkbox (empty or checked)</li>
     *   <li>绘制文字（庆祝时金色高亮） / Draw text (golden highlight when celebrating)</li>
     * </ol>
     *
     * @return 本条提示占用的 Y 轴高度（含间距），供下一条提示定位 / Y-axis height consumed by this hint (including gap), used for positioning the next hint
     */
    private static int renderHint(
        GuiGraphicsExtractor graphics,
        Minecraft mc,
        HintManager.HintRenderData hint,
        int screenW, int screenH,
        int padding, int yOffset
    ) {
        float scale = hint.textScale();
        String text = hint.text();
        int fullW = hint.fullTextWidth();   // 完整文本宽度（预分配，避免逐字时框晃动） / Full text width (pre-allocated to avoid box jitter during typewriter)

        // ── 子步骤 1：文字尺寸 ──
        // ── Sub-step 1: Text Dimensions ──
        int textH = mc.font.lineHeight;

        // ── 子步骤 1.5：复选框尺寸（教程型规则专用）──
        // ── Sub-step 1.5: Checkbox Size (tutorial-type rules only) ──
        int checkSize = 0, checkGap = 0, checkTotal = 0;
        if (hint.showCheckbox()) {
            checkSize = textH;              // 与文字等高 / Same height as text
            checkGap = 4;                    // 框与文字间距 / Gap between box and text
            checkTotal = checkSize + checkGap;
        }

        // 有效缩放（不再使用 celebrateScale 脉冲） / Effective scale (no longer uses celebrateScale pulse)
        float effectiveScale = scale;
        int scaledFullW = (int) (fullW * effectiveScale);
        int scaledCheckW = (int) (checkTotal * effectiveScale);
        int scaledTotalW = scaledCheckW + scaledFullW;
        int scaledH = (int) (textH * effectiveScale);

        // ── 子步骤 2：屏幕坐标（含复选框总宽度）──
        // ── Sub-step 2: Screen Coordinates (including checkbox total width) ──
        int x, y;
        if ("above_item".equals(hint.position())) {
            // 快捷栏正上方居中 / Centered directly above the hotbar
            x = (screenW - scaledTotalW) / 2;
            // 快捷栏 Y + 足够间距避开物品名（hotbar 22px + 物品名 ~12px + 余量 4px） / Hotbar Y + enough clearance to avoid item name (hotbar 22px + item name ~12px + margin 4px)
            y = screenH - 22 - 16 - scaledH - hint.offsetY() - padding;
            y -= yOffset;
        } else {
            x = calcX(hint.position(), screenW, scaledTotalW, hint.offsetX(), padding);
            y = calcY(hint.position(), screenH, scaledH, hint.offsetY(), padding, yOffset);
        }

        // ── 子步骤 3：2D 变换矩阵 ──
        // ── Sub-step 3: 2D Transform Matrix ──
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(effectiveScale, effectiveScale);

        // ── 子步骤 4：背景矩形（用完整文本宽度预分配）──
        // ── Sub-step 4: Background Rect (pre-allocated with full text width) ──
        int bgLeft = -BG_PADDING;
        int bgRight = checkTotal + fullW + BG_PADDING;
        int bg = hint.bgColor();
        if (hint.isCelebrating()) {
            int flash = hint.celebrateColor();
            if (flash != 0) {
                bg = blendColor(bg, flash);
            }
        }
        if ((bg >>> 24) > 0) {
            graphics.fill(bgLeft, -BG_PADDING, bgRight, textH + BG_PADDING, bg);
        }

        // ── 子步骤 5：复选框（白色基调：未达成=半透明白框 / 达成=填满金色高亮）──
        // ── Sub-step 5: Checkbox (white base tone: uncompleted=semi-transparent outline / completed=filled golden highlight) ──
        if (hint.showCheckbox()) {
            int cbX = 0;
            int cbY = (textH - checkSize) / 2 - 1;
            int cbColor = 0xFFFFFFFF; // 白色基调 / White base tone
            if (hint.isChecked()) {
                cbColor = blendColor(cbColor, hint.celebrateColor()); // 金色高亮 / Golden highlight
            }
            drawCheckbox(graphics, cbX, cbY, checkSize, hint.isChecked(), cbColor);
        }

        // ── 子步骤 6：文字颜色（庆祝时金色高亮）──
        // ── Sub-step 6: Text Color (golden highlight when celebrating) ──
        int textColor = hint.textColor();
        if (hint.isCelebrating()) {
            int flash = hint.celebrateColor();
            if (flash != 0) {
                textColor = blendColor(textColor, flash);
            }
        }

        // ── 子步骤 7：文字（带阴影，右移给复选框腾空间）──
        // ── Sub-step 7: Text (with shadow, shifted right to make room for checkbox) ──
        graphics.text(mc.font, text, checkTotal, 0, textColor, true);

        graphics.pose().popMatrix();

        return scaledH + HINT_GAP;
    }

    /**
     * 绘制提示文字左侧的复选框。
     * Draw the checkbox to the left of the hint text.
     *
     * <p>未打勾（SHOWING 阶段）：半透明文字色边框空框，与文字融为一体。
     * 已打勾（CELEBRATING 阶段）：用文字同色（金色高亮后）填充整个框，
     * 与文字一起高亮、一起消失。</p>
     * <p>Unchecked (SHOWING phase): semi-transparent text-colored border outline, blending with the text.
     * Checked (CELEBRATING phase): fills the entire box with the text color (after golden highlight),
     * highlighting and disappearing together with the text.</p>
     *
     * @param graphics GUI 渲染上下文 / GUI render context
     * @param x        框左上角 X 坐标（变换后空间） / Box top-left X coordinate (post-transform space)
     * @param y        框左上角 Y 坐标（变换后空间） / Box top-left Y coordinate (post-transform space)
     * @param size     框的边长（像素） / Box side length (pixels)
     * @param checked  是否已达成（填充或留空） / Whether completed (filled or empty)
     * @param color    框的颜色（已应用金色高亮后的文字色） / Box color (text color after golden highlight applied)
     */
    private static void drawCheckbox(GuiGraphicsExtractor graphics,
                                      int x, int y, int size, boolean checked, int color) {
        int border = Math.max(1, size / 8);
        if (checked) {
            // 用文字同色填满整个框（已金色高亮），无额外边框 / Fill the entire box with text color (already golden highlighted), no extra border
            graphics.fill(x, y, x + size, y + size, color);
        } else {
            // 半透明文字色边框空框 / Semi-transparent text-color border outline
            int borderColor = (color & 0x00FFFFFF) | (0x80 << 24);
            graphics.fill(x, y, x + size, y + border, borderColor);
            graphics.fill(x, y + size - border, x + size, y + size, borderColor);
            graphics.fill(x, y, x + border, y + size, borderColor);
            graphics.fill(x + size - border, y, x + size, y + size, borderColor);
        }
    }

    /**
     * 将闪烁色叠加到基础色上。
     * Blend the flash color onto the base color.
     *
     * <p>使用 {@code flash} 的 alpha 通道作为混合强度，
     * 对 RGB 三个通道分别进行线性插值。</p>
     * <p>Uses the alpha channel of {@code flash} as blend strength,
     * performing linear interpolation on each of the three RGB channels.</p>
     *
     * @param base  基础色 ARGB / Base color ARGB
     * @param flash 闪烁色 ARGB（alpha 通道 = 混合强度 0~255） / Flash color ARGB (alpha channel = blend strength 0~255)
     * @return 混合后的 ARGB 颜色 / Blended ARGB color
     */
    private static int blendColor(int base, int flash) {
        int flashAlpha = (flash >>> 24) & 0xFF;
        if (flashAlpha == 0) return base;

        float t = flashAlpha / 255f;

        int r = (int) (((base >> 16) & 0xFF) * (1 - t) + ((flash >> 16) & 0xFF) * t);
        int g = (int) (((base >> 8) & 0xFF) * (1 - t) + ((flash >> 8) & 0xFF) * t);
        int b = (int) ((base & 0xFF) * (1 - t) + (flash & 0xFF) * t);

        int baseAlpha = (base >>> 24) & 0xFF;
        return (baseAlpha << 24) | (r << 16) | (g << 8) | b;
    }

    // ============================================================
    //  坐标计算
    //  Coordinate Calculation
    // ============================================================

    /**
     * 根据锚点位置计算 X 坐标。
     * Calculate X coordinate based on anchor position.
     * <pre>
     * left   → offset + padding
     * center → (screenW - textW) / 2
     * right  → screenW - textW - offset - padding
     * </pre>
     */
    private static int calcX(String pos, int screenW, int textW, int offset, int padding) {
        return switch (pos) {
            case "top_center", "bottom_center" -> (screenW - textW) / 2;
            case "top_right", "bottom_right"   -> screenW - textW - offset - padding;
            default                             -> offset + padding;
        };
    }

    /**
     * 根据锚点位置和多提示偏移计算 Y 坐标。
     * Calculate Y coordinate based on anchor position and multi-hint offset.
     *
     * <p><b>顶部锚点</b>：{@code y = offset + padding + yOffset}
     * —— 每个提示依次向下排列。</p>
     * <p><b>Top anchor</b>: {@code y = offset + padding + yOffset}
     * — each hint is laid out downwards in sequence.</p>
     *
     * <p><b>底部锚点</b>：{@code y = screenH - offset - padding - textH - yOffset}
     * —— 第一个提示紧贴屏幕底部，后续提示依次向上排列。</p>
     * <p><b>Bottom anchor</b>: {@code y = screenH - offset - padding - textH - yOffset}
     * — the first hint hugs the screen bottom, subsequent hints are laid out upwards in sequence.</p>
     */
    private static int calcY(String pos, int screenH, int textH, int offset, int padding, int yOffset) {
        if (pos.startsWith("top_")) {
            return offset + padding + yOffset;
        }
        return screenH - offset - padding - textH - yOffset;
    }
}
