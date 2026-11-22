package io.github.watdafawx.shapewright.screen;

import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.ParentComponent;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import net.minecraft.network.chat.Component;

/** Shared look and helpers for the mod's owo-ui screens. */
public final class Ui {

    public static final int TEXT = 0xE4E7EE, MUTED = 0x8B95A8, ACCENT = 0xFBBF24, SECTION = 0xE8A33D;
    /** Screen background, and panels on it. */
    public static final int BACKGROUND = 0xEE0F1218;

    // Modern flat dark kit tokens
    public static final int BG_SCREEN = 0xEE0F1218;
    public static final int BG_PANEL = 0xFF151922;
    public static final int BG_CARD = 0xFF1A1F2B;
    public static final int BG_BTN = 0xFF1E2430;
    public static final int BG_BTN_HOVER = 0xFF283042;
    public static final int BORDER = 0xFF2A3142;
    public static final int BORDER_HOVER = 0xFF44506B;
    public static final int GREEN = 0xFF22C55E;
    public static final int GREEN_HOVER = 0xFF34D399;
    public static final int GREEN_SURFACE = 0x2622C55E;
    public static final int AMBER_SURFACE = 0x24FBBF24;
    public static final int AMBER_BORDER = 0x88FBBF24;

    private Ui() {}

    /** Flat dark card container with crisp 1px border. */
    public static FlowLayout card(FlowLayout flow) {
        flow.surface(Surface.flat(BG_CARD).and(Surface.outline(BORDER)));
        flow.padding(Insets.of(6));
        return flow;
    }

    /** Flat dark button (no chunky vanilla stone texture). */
    public static io.wispforest.owo.ui.component.ButtonComponent flatBtn(
            Component text, java.util.function.Consumer<io.wispforest.owo.ui.component.ButtonComponent> onPress) {
        var btn = Components.button(text, onPress);
        btn.renderer(flatRenderer(BG_BTN, BG_BTN_HOVER, BORDER, BORDER_HOVER));
        return btn;
    }

    /** Flat pill tab with active accent highlight. */
    public static io.wispforest.owo.ui.component.ButtonComponent pillTab(
            Component text, boolean active,
            java.util.function.Consumer<io.wispforest.owo.ui.component.ButtonComponent> onPress) {
        var btn = Components.button(text, onPress);
        if (active) {
            btn.renderer(flatRenderer(AMBER_SURFACE, 0x36FBBF24, AMBER_BORDER, 0xCCFBBF24));
        } else {
            btn.renderer(flatRenderer(0xFF141821, BG_BTN_HOVER, BORDER, BORDER_HOVER));
        }
        return btn;
    }

    /** Primary green CTA button. */
    public static io.wispforest.owo.ui.component.ButtonComponent primaryBtn(
            Component text, java.util.function.Consumer<io.wispforest.owo.ui.component.ButtonComponent> onPress) {
        var btn = Components.button(text, onPress);
        btn.renderer(flatRenderer(0xFF1F8A4C, 0xFF26A65B, 0xFF34D399, 0xFF6EE7B7));
        return btn;
    }

    public static io.wispforest.owo.ui.component.ButtonComponent.Renderer flatRenderer(
            int bg, int bgHover, int border, int borderHover) {
        return (ctx, button, delta) -> {
            boolean hover = button.isHovered() && button.active;
            int x = button.getX(), y = button.getY(), w = button.getWidth(), h = button.getHeight();
            ctx.fill(x, y, x + w, y + h, hover ? bgHover : bg);
            ctx.renderOutline(x, y, w, h, hover ? borderHover : border);
        };
    }

    /** Compact modern pill toggle switch (28x14px). */
    public static io.wispforest.owo.ui.core.Component toggleSwitch(boolean on, Runnable flip) {
        return new io.wispforest.owo.ui.base.BaseComponent() {
            { sizing(Sizing.fixed(28), Sizing.fixed(14)); }

            @Override
            public void draw(io.wispforest.owo.ui.core.OwoUIDrawContext ctx, int mouseX, int mouseY, float partialTicks, float delta) {
                int track = on ? 0xFF1F9D55 : 0xFF283042;
                int rim = on ? 0xFF34D399 : (isInBoundingBox(mouseX, mouseY) ? BORDER_HOVER : BORDER);
                ctx.fill(x, y, x + width, y + height, track);
                ctx.renderOutline(x, y, width, height, rim);
                int knobX = on ? (x + width - 12) : (x + 2);
                ctx.fill(knobX, y + 2, knobX + 10, y + height - 2, 0xFFF1F5F9);
            }

            @Override
            public boolean onMouseDown(double mouseX, double mouseY, int button) {
                if (button == 0) {
                    flip.run();
                    return true;
                }
                return false;
            }
        };
    }

    /**
     * A label whose text clicks do nothing. owo passes clicks on plain text to the screen with no style,
     * and a mod in the pack (an EMI add-on) crashes on that; none of our labels have links anyway.
     */
    public static LabelComponent label(Component text) {
        return Components.label(text).textClickHandler(style -> false);
    }

    public static LabelComponent label(Component text, int color) {
        return label(text).color(Color.ofRgb(color));
    }

    /** A bold orange heading. */
    public static LabelComponent heading(String text) {
        return (LabelComponent) label(Component.literal(text).withStyle(s -> s.withBold(true)), SECTION).margins(Insets.bottom(2));
    }

    /** Applies the {@link #label} guard to every label under a component (e.g. dropdown entries). */
    public static void ignoreTextClicks(io.wispforest.owo.ui.core.Component c) {
        if (c instanceof LabelComponent l) l.textClickHandler(style -> false);
        if (c instanceof ParentComponent parent) parent.children().forEach(Ui::ignoreTextClicks);
    }

    /** Pushes what follows to the right edge (owo's own spacer also grows vertically, stretching the row). */
    public static io.wispforest.owo.ui.core.Component hspace() {
        return Components.spacer().verticalSizing(Sizing.fixed(0));
    }

    /** A dark panel with an outline and padding. */
    public static FlowLayout panel(FlowLayout flow) {
        flow.surface(Surface.flat(BG_PANEL).and(Surface.outline(BORDER)));
        flow.padding(Insets.of(6));
        return flow;
    }

    /** Height of one entry in a dropdown menu, for placing the menu before it is laid out. */
    private static final int MENU_ENTRY_H = 12;

    /**
     * A button showing the current choice with a ▼; clicking opens a menu of every option (current one
     * marked). Picking applies it, then runs {@code after}.
     */
    public static <T> io.wispforest.owo.ui.component.ButtonComponent choice(net.minecraft.client.gui.screens.Screen screen,
            FlowLayout root, String shown, java.util.List<T> options, java.util.function.Function<T, String> name,
            java.util.function.Consumer<T> pick, Runnable after) {
        return flatBtn(Component.literal(shown + "  ▼"), b -> openMenu(screen, root, b, shown, options, name, pick, after));
    }

    /** Opens a menu under the button (above it when there is no room below); entries are guarded like {@link #label}. */
    public static <T> void openMenu(net.minecraft.client.gui.screens.Screen screen, FlowLayout root,
            net.minecraft.client.gui.components.AbstractWidget b, String current, java.util.List<T> options,
            java.util.function.Function<T, String> name, java.util.function.Consumer<T> pick, Runnable after) {
        int menuH = options.size() * MENU_ENTRY_H + 8;
        int menuY = b.getY() + b.getHeight() + menuH <= screen.height - 4 ? b.getY() + b.getHeight() : Math.max(4, b.getY() - menuH);
        var opened = io.wispforest.owo.ui.component.DropdownComponent.openContextMenu(screen, root, FlowLayout::child, b.getX(), menuY, menu -> {
            menu.surface(Surface.flat(0xF5151922).and(Surface.outline(BORDER_HOVER)));
            for (T option : options) {
                String text = name.apply(option);
                menu.button(Component.literal(text.equals(current) ? "» " + text : "   " + text), m -> {
                    pick.accept(option);
                    m.remove();
                    after.run();
                });
            }
        });
        ignoreTextClicks(opened);
        opened.zIndex(300); // above fields (drawn batched, so depth decides) and below tooltips
    }

    /** owo turns vanilla widgets into UI components at runtime; this gives them that type for the compiler. */
    public static io.wispforest.owo.ui.core.Component w(Object widget) {
        return (io.wispforest.owo.ui.core.Component) widget;
    }
}
