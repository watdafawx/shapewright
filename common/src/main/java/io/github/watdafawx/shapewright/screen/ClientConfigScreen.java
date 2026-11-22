package io.github.watdafawx.shapewright.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import io.github.watdafawx.shapewright.config.ClientConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ClientConfigScreen extends Screen {

    private static final int PANEL_W = 310;
    private static final int HEADER_H = 26;
    private static final int FOOTER_H = 32;
    private static final int PAD_X = 14;
    private static final int PAD_Y = 10;
    private static final int ROW_H = 26;
    private static final int FIELD_W = 104;
    private static final int FIELD_H = 18;

    private int cardH() { return HEADER_H + PAD_Y * 2 + LABEL_KEYS.length * ROW_H + FOOTER_H; }
    private int cardLeft() { return (width - PANEL_W) / 2; }
    private int cardTop() { return (height - cardH()) / 2; }

    /** Label keys in row order, used for both rendering and tooltip lookup. */
    private static final String[] LABEL_KEYS = {
        "shapewright.config.preview_block_size",
        "shapewright.config.preview_block_transparency",
        "shapewright.config.max_block_previews",
        "shapewright.config.protect_tile_entities",
    };

    private float sizeValue;
    private float transparencyValue;
    private int maxBlockPreviews;
    private boolean protectTileEntities;

    private EditBox maxPreviewsField;

    public ClientConfigScreen() {
        super(Component.translatable("shapewright.screen.client_config"));
    }

    @Override
    protected void init() {
        super.init();
        ClientConfig cfg = ClientConfig.INSTANCE;

        sizeValue = cfg.getPreviewBlockSize();
        transparencyValue = cfg.getPreviewBlockTransparency();
        maxBlockPreviews = cfg.getMaxBlockPreviews();
        protectTileEntities = cfg.shouldProtectTileEntities();

        int left = cardLeft();
        int top = cardTop() + HEADER_H + PAD_Y;
        int fieldX = left + PANEL_W - FIELD_W - PAD_X;
        int rowY = top;

        // Row 1: Preview block size (flat dark slider)
        addRenderableWidget(new FlatSlider(fieldX, rowY, FIELD_W, FIELD_H,
                Component.literal(Math.round(sizeValue * 100) + "%"), sizeFraction(sizeValue)) {
            @Override protected void updateMessage() { setMessage(Component.literal(Math.round(sizeValue * 100) + "%")); }
            @Override protected void applyValue() { sizeValue = sizeFromFraction((float) this.value); }
        });

        // Row 2: Preview block transparency (flat dark slider)
        rowY += ROW_H;
        addRenderableWidget(new FlatSlider(fieldX, rowY, FIELD_W, FIELD_H,
                Component.literal(Math.round(transparencyValue * 100) + "%"), transparencyValue) {
            @Override protected void updateMessage() { setMessage(Component.literal(Math.round(transparencyValue * 100) + "%")); }
            @Override protected void applyValue() { transparencyValue = Math.round((float) this.value * 20f) / 20f; }
        });

        // Row 3: Max block previews (int field with -/+ buttons)
        rowY += ROW_H;
        int btnW = 14;
        int editW = FIELD_W - btnW * 2;
        addRenderableWidget(ScreenWidgets.flatBuilder(Component.literal("−"),
                        btn -> stepMaxPreviews(-50))
                .bounds(fieldX, rowY, btnW, FIELD_H).build());
        maxPreviewsField = new EditBox(font, fieldX + btnW, rowY, editW, FIELD_H, Component.empty());
        maxPreviewsField.setValue(String.valueOf(maxBlockPreviews));
        maxPreviewsField.setFilter(s -> s.isEmpty() || s.matches("\\d{0,5}"));
        maxPreviewsField.setResponder(s -> {
            try { maxBlockPreviews = Integer.parseInt(s); }
            catch (NumberFormatException ignored) {}
        });
        addRenderableWidget(maxPreviewsField);
        addRenderableWidget(ScreenWidgets.flatBuilder(Component.literal("+"),
                        btn -> stepMaxPreviews(50))
                .bounds(fieldX + btnW + editW, rowY, btnW, FIELD_H).build());

        // Row 4: Protect tile entities (toggle)
        rowY += ROW_H;
        addRenderableWidget(ScreenWidgets.flatBuilder(
                        Component.literal(onOff(protectTileEntities)),
                        btn -> {
                            protectTileEntities = !protectTileEntities;
                            btn.setMessage(Component.literal(onOff(protectTileEntities)));
                        })
                .bounds(fieldX, rowY, FIELD_W, FIELD_H).build());

        // Save / Cancel inside footer bar (right-aligned)
        int footerY = cardTop() + cardH() - FOOTER_H + 7;
        addRenderableWidget(ScreenWidgets.flatBuilder(Component.translatable("gui.cancel"), btn -> onClose())
                .bounds(left + PANEL_W - PAD_X - 126, footerY, 58, 18).build());
        addRenderableWidget(ScreenWidgets.primaryButton(left + PANEL_W - PAD_X - 62, footerY, 62, 18,
                Component.translatable("shapewright.button.save"), btn -> save()));
    }

    private void stepMaxPreviews(int delta) {
        maxBlockPreviews = Math.clamp(maxBlockPreviews + delta,
                ClientConfig.MIN_MAX_BLOCK_PREVIEWS, ClientConfig.MAX_MAX_BLOCK_PREVIEWS);
        maxPreviewsField.setValue(String.valueOf(maxBlockPreviews));
    }

    private void save() {
        ClientConfig cfg = ClientConfig.INSTANCE;
        cfg.setPreviewBlockSize(sizeValue);
        cfg.setPreviewBlockTransparency(transparencyValue);
        cfg.setMaxBlockPreviews(maxBlockPreviews);
        cfg.setProtectTileEntities(protectTileEntities);
        cfg.save();
        onClose();
    }

    // =========================================================================
    // Rendering
    // =========================================================================

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(0, 0, this.width, this.height, Ui.BG_SCREEN);
        int left = cardLeft(), top = cardTop(), h = cardH();
        ScreenWidgets.drawDarkPanel(guiGraphics, left, top, PANEL_W, h);
        // Header bar
        guiGraphics.fill(left + 1, top + 1, left + PANEL_W - 1, top + HEADER_H, Ui.BG_CARD);
        guiGraphics.fill(left + 1, top + HEADER_H, left + PANEL_W - 1, top + HEADER_H + 1, Ui.BORDER);
        // Footer bar
        int fy = top + h - FOOTER_H;
        guiGraphics.fill(left + 1, fy, left + PANEL_W - 1, top + h - 1, Ui.BG_CARD);
        guiGraphics.fill(left + 1, fy, left + PANEL_W - 1, fy + 1, Ui.BORDER);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        int left = cardLeft();
        int cTop = cardTop();
        int top = cTop + HEADER_H + PAD_Y;
        int labelX = left + PAD_X;

        // Header title
        graphics.drawString(font, title, labelX, cTop + 9, 0xFFFFFF);

        // Labels
        int rowY = top;
        for (String key : LABEL_KEYS) {
            graphics.drawString(font, Component.translatable(key), labelX, rowY + 5, 0xD0D6E2);
            rowY += ROW_H;
        }

        // Tooltips — render last so they appear on top
        renderRowTooltips(graphics, mouseX, mouseY);
    }

    private void renderRowTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        int left = cardLeft();
        int top = cardTop() + HEADER_H + PAD_Y;
        int labelX = left + PAD_X;
        int labelMaxX = left + PANEL_W - FIELD_W - PAD_X - 8;

        if (mouseX < labelX || mouseX >= labelMaxX) return;

        int rowY = top;
        for (String key : LABEL_KEYS) {
            if (mouseY >= rowY && mouseY < rowY + ROW_H) {
                String tooltipKey = key + ".tooltip";
                String text = Component.translatable(tooltipKey).getString();
                if (!text.equals(tooltipKey)) {
                    String[] lines = text.split("\n");
                    List<Component> components = new ArrayList<>();
                    for (String line : lines) components.add(Component.literal(line));
                    graphics.renderTooltip(font, components, Optional.empty(), mouseX, mouseY);
                }
                return;
            }
            rowY += ROW_H;
        }
    }

    private abstract static class FlatSlider extends AbstractSliderButton {
        FlatSlider(int x, int y, int w, int h, Component msg, double value) {
            super(x, y, w, h, msg, value);
        }

        @Override
        public void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            boolean hover = isHoveredOrFocused();
            g.fill(x, y, x + w, y + h, Ui.BG_BTN);
            int fillW = (int) Math.round((w - 2) * this.value);
            if (fillW > 0) g.fill(x + 1, y + 1, x + 1 + fillW, y + h - 1, 0xFF1F8A4C);
            g.renderOutline(x, y, w, h, hover ? Ui.BORDER_HOVER : Ui.BORDER);
            int knobX = x + 1 + (int) Math.round((w - 6) * this.value);
            g.fill(knobX, y + 2, knobX + 4, y + h - 2, 0xFFF1F5F9);
            g.drawCenteredString(net.minecraft.client.Minecraft.getInstance().font, getMessage(), x + w / 2, y + (h - 8) / 2, 0xFFFFFF);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        // Allow scrolling over the max previews field
        if (maxPreviewsField != null
                && mouseX >= maxPreviewsField.getX() && mouseX <= maxPreviewsField.getX() + maxPreviewsField.getWidth()
                && mouseY >= maxPreviewsField.getY() && mouseY <= maxPreviewsField.getY() + maxPreviewsField.getHeight()) {
            stepMaxPreviews(verticalAmount > 0 ? 50 : -50);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    // =========================================================================
    // Value mapping
    // =========================================================================

    private static float sizeFraction(float size) {
        return (size - ClientConfig.MIN_PREVIEW_BLOCK_SIZE)
                / (ClientConfig.MAX_PREVIEW_BLOCK_SIZE - ClientConfig.MIN_PREVIEW_BLOCK_SIZE);
    }

    private static float sizeFromFraction(float frac) {
        float raw = ClientConfig.MIN_PREVIEW_BLOCK_SIZE
                + frac * (ClientConfig.MAX_PREVIEW_BLOCK_SIZE - ClientConfig.MIN_PREVIEW_BLOCK_SIZE);
        return Math.round(raw * 20f) / 20f; // snap to 5%
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static String onOff(boolean value) {
        return value ? "ON" : "OFF";
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}

