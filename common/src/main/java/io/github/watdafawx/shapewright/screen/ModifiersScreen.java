package io.github.watdafawx.shapewright.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import io.github.watdafawx.shapewright.modifier.*;
import io.github.watdafawx.shapewright.network.PacketHandler;
import io.github.watdafawx.shapewright.network.UpdateModifiersC2SPacket;

import java.util.ArrayList;
import java.util.List;

/**
 * Screen for managing the player's modifier list (mirror, array, radial).
 * Delegates field building to {@link ScreenWidgets} and per-modifier settings
 * to {@link ModifierSettingsPanel}.
 */
public class ModifiersScreen extends Screen {

    // ---- layout ----
    private static final int PANEL_W = 408;
    private static final int PANEL_H = 214;
    private static final int HEADER_H = 24;
    private static final int FOOTER_H = 28;
    private static final int PAD = 10;
    private static final int LIST_W  = 176;
    private static final int DIV_OX  = PAD + LIST_W + 10;
    private static final int SET_OX  = DIV_OX + 10;
    private static final int ROW_H   = 22;

    // ---- state ----
    private int selectedIndex = -1;
    private List<IModifier> filteredModifiers = new ArrayList<>();
    private List<Integer> filteredToReal = new ArrayList<>();

    // ---- helpers ----
    private ScreenWidgets widgets;
    private ModifierSettingsPanel settingsPanel;

    public ModifiersScreen() {
        super(Component.literal("Modifiers"));
    }

    // =========================================================================
    // Init
    // =========================================================================

    @Override
    protected void init() {
        widgets = new ScreenWidgets(font, this::addRenderableWidget);
        settingsPanel = new ModifierSettingsPanel(widgets, this::rebuildWidgets);
        widgets.clear();
        rebuildFilteredList();

        int px = panelX(), py = panelY();
        buildListWidgets(px, py);

        // Right-side settings
        if (selectedIndex >= 0 && selectedIndex < filteredModifiers.size()) {
            int sx = px + SET_OX;
            int sy = py + HEADER_H + 10;
            settingsPanel.buildWidgets(filteredModifiers.get(selectedIndex), sx, sy);
        }

        addRenderableWidget(ScreenWidgets.primaryButton(
                px + PANEL_W - PAD - 66, py + PANEL_H - FOOTER_H + 6, 66, 16,
                Component.literal("Close"), btn -> onClose()));
    }

    // =========================================================================
    // Filtered list
    // =========================================================================

    private void rebuildFilteredList() {
        filteredModifiers.clear();
        filteredToReal.clear();
        String currentDim = currentDimension();
        List<IModifier> all = ModifierSystem.CLIENT.getModifiers();
        for (int i = 0; i < all.size(); i++) {
            IModifier m = all.get(i);
            String dim = m.getDimension();
            if (dim == null || dim.isEmpty() || dim.equals(currentDim)) {
                filteredModifiers.add(m);
                filteredToReal.add(i);
            }
        }
        if (selectedIndex >= filteredModifiers.size()) {
            selectedIndex = filteredModifiers.size() - 1;
        }
    }

    private static String currentDimension() {
        var player = Minecraft.getInstance().player;
        return player != null ? player.level().dimension().location().toString() : "";
    }

    // =========================================================================
    // Left panel: modifier list
    // =========================================================================

    private void buildListWidgets(int px, int py) {
        List<IModifier> modifiers = filteredModifiers;
        int lx = px + PAD;
        int rowBase = py + HEADER_H + 10;
        int count = modifiers.size();

        for (int i = 0; i < count; i++) {
            final int filteredIdx = i;
            final int realIdx = filteredToReal.get(i);
            IModifier modifier = modifiers.get(i);
            int ry = rowBase + i * ROW_H;

            widgets.addCheckbox(lx + 2, ry + 7, "", modifier.isEnabled(),
                    () -> { modifier.setEnabled(!modifier.isEnabled()); rebuildWidgets(); });

            Button upBtn = addRenderableWidget(ScreenWidgets.flatBuilder(Component.literal("↑"),
                            btn -> { ModifierSystem.CLIENT.moveModifier(realIdx, -1); selectedIndex = filteredIdx - 1; rebuildWidgets(); })
                    .bounds(lx + LIST_W - 44, ry + 4, 12, 14).build());
            if (filteredIdx == 0) upBtn.active = false;

            Button downBtn = addRenderableWidget(ScreenWidgets.flatBuilder(Component.literal("↓"),
                            btn -> { ModifierSystem.CLIENT.moveModifier(realIdx, +1); selectedIndex = filteredIdx + 1; rebuildWidgets(); })
                    .bounds(lx + LIST_W - 30, ry + 4, 12, 14).build());
            if (filteredIdx == count - 1) downBtn.active = false;

            addRenderableWidget(ScreenWidgets.flatBuilder(Component.literal("×"),
                            btn -> {
                                ModifierSystem.CLIENT.removeModifier(realIdx);
                                if (selectedIndex >= filteredModifiers.size() - 1) selectedIndex = filteredModifiers.size() - 2;
                                rebuildWidgets();
                            })
                    .bounds(lx + LIST_W - 16, ry + 4, 14, 14).build());
        }

        // Add buttons
        int addY = py + PANEL_H - FOOTER_H + 6;
        addRenderableWidget(ScreenWidgets.flatBuilder(Component.literal("+ Mirror"), btn -> {
            MirrorModifier mirror = new MirrorModifier();
            var pos = playerBlockPos();
            mirror.originX = pos.getX(); mirror.originY = pos.getY(); mirror.originZ = pos.getZ();
            mirror.setDimension(currentDimension());
            ModifierSystem.CLIENT.addModifier(mirror);
            rebuildFilteredList();
            selectedIndex = filteredModifiers.size() - 1;
            rebuildWidgets();
        }).bounds(px + 4, addY, 58, 16).build());

        addRenderableWidget(ScreenWidgets.flatBuilder(Component.literal("+ Array"), btn -> {
            ArrayModifier array = new ArrayModifier();
            array.setDimension(currentDimension());
            ModifierSystem.CLIENT.addModifier(array);
            rebuildFilteredList();
            selectedIndex = filteredModifiers.size() - 1;
            rebuildWidgets();
        }).bounds(px + 66, addY, 52, 16).build());

        addRenderableWidget(ScreenWidgets.flatBuilder(Component.literal("+ Radial"), btn -> {
            RadialMirrorModifier radial = new RadialMirrorModifier();
            var pos = playerBlockPos();
            radial.originX = pos.getX(); radial.originY = pos.getY(); radial.originZ = pos.getZ();
            radial.setDimension(currentDimension());
            ModifierSystem.CLIENT.addModifier(radial);
            rebuildFilteredList();
            selectedIndex = filteredModifiers.size() - 1;
            rebuildWidgets();
        }).bounds(px + 122, addY, 56, 16).build());


    }

    // =========================================================================
    // Input handling
    // =========================================================================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (widgets.handleCheckboxClick(mouseX, mouseY)) return true;

        int px = panelX(), py = panelY();
        int lx = px + PAD;
        int rowBase = py + HEADER_H + 10;

        for (int i = 0; i < filteredModifiers.size(); i++) {
            int ry = rowBase + i * ROW_H;
            if (mouseX >= lx + 18 && mouseX < lx + LIST_W - 47
                    && mouseY >= ry && mouseY < ry + ROW_H) {
                if (selectedIndex != i) {
                    selectedIndex = i;
                    rebuildWidgets();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (widgets.handleScroll(mouseX, mouseY, scrollY)) return true;
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    // =========================================================================
    // Rendering
    // =========================================================================

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fill(0, 0, this.width, this.height, Ui.BG_SCREEN);
        int px = panelX(), py = panelY();
        ScreenWidgets.drawDarkPanel(guiGraphics, px, py, PANEL_W, PANEL_H);
        // Header bar
        guiGraphics.fill(px + 1, py + 1, px + PANEL_W - 1, py + HEADER_H, Ui.BG_CARD);
        guiGraphics.fill(px + 1, py + HEADER_H, px + PANEL_W - 1, py + HEADER_H + 1, Ui.BORDER);
        // Footer bar
        int fy = py + PANEL_H - FOOTER_H;
        guiGraphics.fill(px + 1, fy, px + PANEL_W - 1, py + PANEL_H - 1, Ui.BG_CARD);
        guiGraphics.fill(px + 1, fy, px + PANEL_W - 1, fy + 1, Ui.BORDER);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        int px = panelX(), py = panelY();
        int divX = px + DIV_OX;

        // Vertical divider between list and settings
        graphics.fill(divX, py + HEADER_H + 1, divX + 1, py + PANEL_H - FOOTER_H, Ui.BORDER);

        // Header titles
        graphics.drawString(font, "Modifiers", px + PAD, py + 8, 0xFFFFFF);
        String settingsHeader = (selectedIndex >= 0 && selectedIndex < filteredModifiers.size())
                ? filteredModifiers.get(selectedIndex).getDisplayName().getString() + " Settings"
                : "Settings";
        graphics.drawString(font, settingsHeader, divX + 10, py + 8, Ui.ACCENT);

        // List rows
        int lx = px + PAD;
        int rowBase = py + HEADER_H + 10;
        if (filteredModifiers.isEmpty()) {
            graphics.drawString(font, "Add a modifier below.", lx + 2, rowBase + 7, Ui.MUTED);
        } else {
            for (int i = 0; i < filteredModifiers.size(); i++) {
                int ry = rowBase + i * ROW_H;
                if (i == selectedIndex) {
                    graphics.fill(lx, ry, lx + LIST_W, ry + ROW_H - 1, Ui.AMBER_SURFACE);
                    graphics.renderOutline(lx, ry, LIST_W, ROW_H - 1, Ui.AMBER_BORDER);
                }
                graphics.drawString(font, filteredModifiers.get(i).getDisplayName().getString(),
                        lx + 20, ry + 7, i == selectedIndex ? Ui.ACCENT : 0xE4E7EE);
            }
        }

        // Checkboxes
        widgets.renderCheckboxes(graphics, mouseX, mouseY);

        // Settings labels
        if (selectedIndex >= 0 && selectedIndex < filteredModifiers.size()) {
            int sx = px + SET_OX;
            int sy = py + 24;
            settingsPanel.renderLabels(graphics, filteredModifiers.get(selectedIndex), sx, sy);
        } else if (!filteredModifiers.isEmpty()) {
            graphics.drawString(font, "Select a modifier", divX + 6, py + 32, 0x888888);
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private int panelX() { return (width - PANEL_W) / 2; }
    private int panelY() { return (height - PANEL_H) / 2; }

    private static BlockPos playerBlockPos() {
        var player = Minecraft.getInstance().player;
        return player != null ? player.blockPosition() : BlockPos.ZERO;
    }

    @Override
    public void onClose() {
        String json = ModifierSerializer.serialize(ModifierSystem.CLIENT.getModifiers());
        PacketHandler.sendToServer(new UpdateModifiersC2SPacket(json));
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
