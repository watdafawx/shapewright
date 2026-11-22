package io.github.watdafawx.shapewright.screen;

import io.wispforest.owo.ui.base.BaseComponent;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import io.wispforest.owo.ui.core.OwoUIDrawContext;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import org.lwjgl.glfw.GLFW;
import io.github.watdafawx.shapewright.palette.BlockColorCache;
import io.github.watdafawx.shapewright.palette.SurfacePaletteGenerator;
import io.github.watdafawx.shapewright.shape.CeilingDesigner;
import io.github.watdafawx.shapewright.shape.FloorDesigner;
import io.github.watdafawx.shapewright.shape.FloorThemes;
import io.github.watdafawx.shapewright.shape.SchematicLibrary;
import io.github.watdafawx.shapewright.shape.SchematicWriter;
import io.github.watdafawx.shapewright.shape.ShapeGenerator.Cell;
import io.github.watdafawx.shapewright.shape.WallDesigner;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;
import java.util.function.Function;

import static io.github.watdafawx.shapewright.screen.Ui.w;

/**
 * Unified Surface Designer (Floor / Wall / Ceiling) using the modern flat dark UI kit:
 * canvas-left hero layout, visual style preset tiles, 5 horizontal block swatches with lock badges,
 * collapsible Advanced controls, and 3D relief shading.
 */
public class FloorDesignerScreen extends BaseOwoScreen<FlowLayout> {

    public enum Tab {
        FLOOR("floor"),
        WALL("wall"),
        CEILING("ceiling");

        public final String key;
        Tab(String key) { this.key = key; }
        public String getNameKey() { return "shapewright.surface_designer.tab." + key; }
    }

    public enum Tool {
        BRUSH("brush"),
        FILL("fill"),
        ERASER("eraser"),
        PICK("pick");

        public final String key;
        Tool(String key) { this.key = key; }
        public String getNameKey() { return "shapewright.surface_designer.tool." + key; }
    }

    private static final int CONTROL_H = 15;
    private static final int MAX_UNDO = 40;
    private int sideW = 216;
    private int cardInnerW = 194;

    private static final String[] SLOT_KEYS = {"base", "primary", "secondary", "trim", "accent"};

    private final Screen parent;
    private final Consumer<String> onCreated;
    private final Random rng = new Random();

    private Tab tab;
    private boolean showAdvanced = false;

    // Floor state
    private FloorDesigner.Style floorStyle = FloorDesigner.Style.MANDALA;
    private FloorDesigner.Outline floorOutline = FloorDesigner.Outline.QUATREFOIL;
    private int floorWidth = 33;
    private int floorDepth = 33;
    private boolean floorSquareLock = true;
    private int floorSymmetry = 8;
    private int floorComplexity = 3;
    private int floorSeed = 1;
    private boolean floorBorder = true;
    private boolean floorRelief = false;

    // Wall state
    private WallDesigner.Style wallStyle = WallDesigner.Style.MEDIEVAL_BAY;
    private int wallWidth = 13;
    private int wallHeight = 15;
    private int wallBays = 2;
    private int wallDepthLayers = 2;
    private boolean wallOpenWindow = true;
    private boolean wallDoorway = false;
    private boolean wallBattlements = false;
    private int wallSeed = 1;

    // Ceiling state
    private CeilingDesigner.Style ceilingStyle = CeilingDesigner.Style.COFFERED;
    private int ceilingWidth = 25;
    private int ceilingDepth = 25;
    private int ceilingComplexity = 3;
    private int ceilingDepthLayers = 2;
    private boolean ceilingBorder = true;
    private int ceilingSeed = 1;

    // Shared palette & canvas tools
    private int themeIndex = 0;
    private final String[] slotBlocks;
    private final boolean[] slotLocked = new boolean[5];
    private int activeBrush = FloorDesigner.PRIMARY;
    private Tool activeTool = Tool.BRUSH;
    private boolean paintMirror = true;
    private boolean showGrid = false;

    // Active grid + optional 3D relief map
    private int[][] grid;
    private int[][] relief;
    private final Map<Long, Integer> painted = new HashMap<>();
    private final Deque<Map<Long, Integer>> undoStack = new ArrayDeque<>();
    private final Deque<Map<Long, Integer>> redoStack = new ArrayDeque<>();
    private boolean strokeSnapshotTaken = false;

    private FlowLayout root;
    private TextBoxComponent nameBox;
    private String nameDraft;
    private LabelComponent infoLabel;
    private String themeStatus = null;

    public FloorDesignerScreen(Screen parent, Consumer<String> onCreated) {
        this(parent, Tab.FLOOR, onCreated);
    }

    public FloorDesignerScreen(Screen parent, Tab initialTab, Consumer<String> onCreated) {
        super(Component.translatable("shapewright.screen.surface_designer"));
        this.parent = parent;
        this.tab = initialTab != null ? initialTab : Tab.FLOOR;
        this.onCreated = onCreated;
        FloorThemes.reload();
        this.slotBlocks = FloorThemes.get().getFirst().blocks().clone();
        this.nameDraft = defaultNameForTab();
        regenerate();
    }

    public void selectTab(Tab next) {
        if (next == null || next == tab) return;
        tab = next;
        nameDraft = defaultNameForTab();
        themeStatus = null;
        regenerate();
        rebuildScreen();
    }

    public Tab currentTab() {
        return tab;
    }

    public String[] currentSlotBlocks() {
        return slotBlocks.clone();
    }

    public void setSlotLocked(int slot, boolean locked) {
        if (slot >= 0 && slot < slotLocked.length) {
            slotLocked[slot] = locked;
            rebuildScreen();
        }
    }

    public void randomizePalette() {
        String[] next = SurfacePaletteGenerator.generate(slotBlocks, null, rng);
        System.arraycopy(next, 0, slotBlocks, 0, slotBlocks.length);
        themeStatus = null;
        rebuildScreen();
    }

    public void matchLockedPalette() {
        boolean anyLocked = false;
        for (boolean l : slotLocked) if (l) { anyLocked = true; break; }
        boolean[] locks = anyLocked ? slotLocked : new boolean[]{true, true, false, false, false};
        String[] next = SurfacePaletteGenerator.generate(slotBlocks, locks, rng);
        System.arraycopy(next, 0, slotBlocks, 0, slotBlocks.length);
        themeStatus = null;
        rebuildScreen();
    }

    private String defaultNameForTab() {
        return switch (tab) {
            case FLOOR -> floorStyle.key + "_floor";
            case WALL -> wallStyle.key + "_wall";
            case CEILING -> ceilingStyle.key + "_ceiling";
        };
    }

    private static String themeLabel(FloorThemes.Theme t) {
        return t.displayName() != null ? t.displayName() : I18n.get(t.getNameKey());
    }

    private void regenerate() {
        switch (tab) {
            case FLOOR -> {
                grid = FloorDesigner.generate(new FloorDesigner.Config(
                        floorStyle, floorOutline, floorWidth, floorDepth, floorSymmetry, floorComplexity, floorSeed, floorBorder));
                int h = grid.length, w = grid[0].length;
                relief = new int[h][w];
                if (floorRelief) {
                    for (int z = 0; z < h; z++) {
                        for (int x = 0; x < w; x++) {
                            int v = grid[z][x];
                            relief[z][x] = (v == FloorDesigner.TRIM || v == FloorDesigner.PRIMARY) ? 1 : 0;
                        }
                    }
                }
            }
            case WALL -> {
                grid = WallDesigner.generate(new WallDesigner.Config(
                        wallStyle, wallWidth, wallHeight, wallOpenWindow, wallBattlements, wallSeed, wallBays, wallDoorway, wallDepthLayers));
                int h = grid.length, w = grid[0].length;
                relief = new int[h][w];
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        relief[y][x] = WallDesigner.reliefFor(grid[y][x], wallDepthLayers);
                    }
                }
            }
            case CEILING -> {
                CeilingDesigner.Result res = CeilingDesigner.generate(new CeilingDesigner.Config(
                        ceilingStyle, ceilingWidth, ceilingDepth, ceilingComplexity, ceilingDepthLayers, ceilingSeed, ceilingBorder));
                grid = res.grid();
                relief = res.relief();
            }
        }
        painted.clear();
        undoStack.clear();
        redoStack.clear();
    }

    private void pushUndo() {
        if (undoStack.size() >= MAX_UNDO) undoStack.removeFirst();
        undoStack.addLast(new HashMap<>(painted));
        redoStack.clear();
    }

    private void undo() {
        if (undoStack.isEmpty()) return;
        redoStack.addLast(new HashMap<>(painted));
        painted.clear();
        painted.putAll(undoStack.removeLast());
        updateInfo();
    }

    private void redo() {
        if (redoStack.isEmpty()) return;
        undoStack.addLast(new HashMap<>(painted));
        painted.clear();
        painted.putAll(redoStack.removeLast());
        updateInfo();
    }

    @Override
    protected OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, Containers::verticalFlow);
    }

    @Override
    protected void init() {
        boolean reopened = uiAdapter != null;
        super.init();
        if (reopened && root != null) {
            root.clearChildren();
            build(root);
        }
    }

    @Override
    protected void build(FlowLayout root) {
        this.root = root;
        this.sideW = Math.min(216, Math.max(166, (this.width * 42) / 100));
        this.cardInnerW = this.sideW - 22;
        BlockColorCache.ensureReady();
        root.surface(Surface.flat(Ui.BG_SCREEN));
        root.padding(Insets.of(8));

        // Top bar: Brand + Floor/Wall/Ceiling pills + Info badge + Randomize
        FlowLayout top = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        top.gap(6).verticalAlignment(VerticalAlignment.CENTER).margins(Insets.bottom(6));
        top.child(Ui.label(title.copy().withStyle(s -> s.withBold(true)), 0xFFFFFF));

        FlowLayout tabs = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        tabs.gap(3);
        for (Tab t : Tab.values()) {
            boolean active = t == tab;
            Component label = Component.translatable(t.getNameKey())
                    .withStyle(s -> s.withBold(active).withColor(active ? Ui.ACCENT : 0xB7C0D0));
            ButtonComponent btn = Ui.pillTab(label, active, b -> selectTab(t));
            tabs.child(w(btn).sizing(Sizing.fixed(Math.max(46, font.width(label) + 12)), Sizing.fixed(CONTROL_H)));
        }
        top.child(tabs);

        infoLabel = Ui.label(Component.empty(), Ui.MUTED);
        top.child(infoLabel);
        top.child(Ui.hspace());

        ButtonComponent randomize = Ui.flatBtn(Component.translatable("shapewright.floor_designer.randomize"), b -> {
            int nextSeed = rng.nextInt(1, 9999);
            switch (tab) {
                case FLOOR -> floorSeed = nextSeed;
                case WALL -> wallSeed = nextSeed;
                case CEILING -> ceilingSeed = nextSeed;
            }
            regenerate();
            rebuildScreen();
        });
        top.child(w(randomize).sizing(Sizing.fixed(font.width(randomize.getMessage()) + 12), Sizing.fixed(CONTROL_H)));
        root.child(top);

        // Main body: LEFT hero canvas, RIGHT calm 3-card inspector
        FlowLayout body = Containers.horizontalFlow(Sizing.fill(100), Sizing.expand());
        body.gap(6);

        FlowLayout canvasCol = Containers.verticalFlow(Sizing.expand(), Sizing.fill(100));
        canvasCol.surface(Surface.flat(Ui.BG_PANEL).and(Surface.outline(Ui.BORDER))).padding(Insets.of(6));
        canvasCol.child(buildCanvasToolbar());
        canvasCol.child(new CanvasComponent().sizing(Sizing.fill(100), Sizing.expand()));
        body.child(canvasCol);

        FlowLayout rightCards = Containers.verticalFlow(Sizing.content(), Sizing.content());
        rightCards.gap(5).padding(Insets.right(4));
        rightCards.child(buildStyleCard());
        rightCards.child(buildOptionsCard());
        rightCards.child(buildPaletteCard());

        FlowLayout rightPanel = Containers.verticalFlow(Sizing.fixed(sideW), Sizing.fill(100));
        rightPanel.child(Containers.verticalScroll(Sizing.fill(100), Sizing.fill(100), rightCards));
        body.child(rightPanel);

        root.child(body);

        // Footer bar: schematic name + Cancel + green primary CTA
        FlowLayout bottom = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        bottom.gap(6).verticalAlignment(VerticalAlignment.CENTER).margins(Insets.top(6));
        nameBox = Components.textBox(Sizing.fixed(180));
        w(nameBox).verticalSizing(Sizing.fixed(18));
        nameBox.setMaxLength(40);
        nameBox.text(nameDraft);
        nameBox.onChanged().subscribe(s -> nameDraft = s);
        bottom.child(w(nameBox));
        bottom.child(Ui.hspace());

        String useKey = switch (tab) {
            case FLOOR -> "shapewright.floor_designer.use";
            case WALL -> "shapewright.wall_designer.use";
            case CEILING -> "shapewright.ceiling_designer.use";
        };
        ButtonComponent cancel = Ui.flatBtn(Component.translatable("gui.cancel"), b -> onClose());
        ButtonComponent use = Ui.primaryBtn(Component.translatable(useKey).withStyle(s -> s.withBold(true)), b -> saveAndUse());
        bottom.child(w(cancel).sizing(Sizing.fixed(64), Sizing.fixed(18)));
        bottom.child(w(use).sizing(Sizing.fixed(Math.max(92, font.width(use.getMessage()) + 16)), Sizing.fixed(18)));
        root.child(bottom);

        updateInfo();
    }

    /** Card 1: Visual Style Preset Tiles (4x2 thumbnail grid + full style dropdown). */
    private FlowLayout buildStyleCard() {
        FlowLayout card = Ui.card(Containers.verticalFlow(Sizing.fixed(sideW - 10), Sizing.content()));
        card.gap(4);

        FlowLayout head = Containers.horizontalFlow(Sizing.fixed(cardInnerW), Sizing.content());
        head.verticalAlignment(VerticalAlignment.CENTER);
        head.child(Ui.label(Component.literal("STYLE").withStyle(s -> s.withBold(true)), Ui.MUTED));
        head.child(Ui.hspace());
        switch (tab) {
            case FLOOR -> head.child(compactChoice(I18n.get(floorStyle.getNameKey()),
                    FloorDesigner.Style.values(), s -> I18n.get(s.getNameKey()), s -> {
                        floorStyle = s;
                        nameDraft = s.key + "_floor";
                        regenerate();
                        rebuildScreen();
                    }));
            case WALL -> head.child(compactChoice(I18n.get(wallStyle.getNameKey()),
                    WallDesigner.Style.values(), s -> I18n.get(s.getNameKey()), s -> {
                        wallStyle = s;
                        nameDraft = s.key + "_wall";
                        regenerate();
                        rebuildScreen();
                    }));
            case CEILING -> head.child(compactChoice(I18n.get(ceilingStyle.getNameKey()),
                    CeilingDesigner.Style.values(), s -> I18n.get(s.getNameKey()), s -> {
                        ceilingStyle = s;
                        nameDraft = s.key + "_ceiling";
                        regenerate();
                        rebuildScreen();
                    }));
        }
        card.child(head);

        // Visual thumbnail grid (up to 8 presets in 2 rows of 4)
        int[] palColors = resolveSlotColors();
        int tileSize = (cardInnerW - 9) / 4;
        for (int rowIdx = 0; rowIdx < 2; rowIdx++) {
            FlowLayout tileRow = Containers.horizontalFlow(Sizing.fixed(cardInnerW), Sizing.fixed(tileSize));
            tileRow.gap(3);
            boolean anyInRow = false;
            for (int colIdx = 0; colIdx < 4; colIdx++) {
                int idx = rowIdx * 4 + colIdx;
                BaseComponent tile = makePresetTile(idx, tileSize, palColors);
                if (tile != null) {
                    tileRow.child(tile);
                    anyInRow = true;
                }
            }
            if (anyInRow) card.child(tileRow);
        }
        return card;
    }

    private BaseComponent makePresetTile(int idx, int size, int[] palColors) {
        return switch (tab) {
            case FLOOR -> {
                FloorDesigner.Style[] featured = {
                        FloorDesigner.Style.MANDALA, FloorDesigner.Style.FLORAL,
                        FloorDesigner.Style.SUNBURST, FloorDesigner.Style.COMPASS,
                        FloorDesigner.Style.ORNATE_TILE, FloorDesigner.Style.MEDALLION,
                        FloorDesigner.Style.HERRINGBONE, FloorDesigner.Style.RUG_RUNNER
                };
                if (idx >= featured.length) yield null;
                FloorDesigner.Style st = featured[idx];
                int[][] mini = FloorDesigner.generate(new FloorDesigner.Config(st, floorOutline, 11, 11, floorSymmetry, 3, floorSeed, true));
                yield new PresetTileComponent(mini, palColors, st == floorStyle, size, () -> {
                    floorStyle = st;
                    nameDraft = st.key + "_floor";
                    regenerate();
                    rebuildScreen();
                });
            }
            case WALL -> {
                WallDesigner.Style[] styles = WallDesigner.Style.values();
                if (idx >= styles.length) yield null;
                WallDesigner.Style st = styles[idx];
                int[][] mini = WallDesigner.generate(new WallDesigner.Config(st, 9, 9, wallOpenWindow, wallBattlements, wallSeed, 1, wallDoorway, 1));
                yield new PresetTileComponent(mini, palColors, st == wallStyle, size, () -> {
                    wallStyle = st;
                    nameDraft = st.key + "_wall";
                    regenerate();
                    rebuildScreen();
                });
            }
            case CEILING -> {
                CeilingDesigner.Style[] styles = CeilingDesigner.Style.values();
                if (idx >= styles.length) yield null;
                CeilingDesigner.Style st = styles[idx];
                int[][] mini = CeilingDesigner.generate(new CeilingDesigner.Config(st, 11, 11, 3, 1, ceilingSeed, true)).grid();
                yield new PresetTileComponent(mini, palColors, st == ceilingStyle, size, () -> {
                    ceilingStyle = st;
                    nameDraft = st.key + "_ceiling";
                    regenerate();
                    rebuildScreen();
                });
            }
        };
    }

    /** Card 2: Size & Core Options + Collapsible Advanced section. */
    private FlowLayout buildOptionsCard() {
        FlowLayout card = Ui.card(Containers.verticalFlow(Sizing.fixed(sideW - 10), Sizing.content()));
        card.gap(4);

        card.child(Ui.label(Component.literal("SIZE & SHAPE").withStyle(s -> s.withBold(true)), Ui.MUTED));

        switch (tab) {
            case FLOOR -> {
                card.child(optRow(floorSquareLock ? "shapewright.floor_designer.size" : "shapewright.wall_designer.width",
                        intStep(floorWidth, 2, 11, 81, v -> {
                            floorWidth = v | 1;
                            if (floorSquareLock) floorDepth = floorWidth;
                            regenerate();
                            rebuildScreen();
                        })));
                if (!floorSquareLock) {
                    card.child(optRow("shapewright.surface_designer.depth", intStep(floorDepth, 2, 11, 81, v -> {
                        floorDepth = v | 1;
                        regenerate();
                        rebuildScreen();
                    })));
                }
                card.child(optRow("shapewright.floor_designer.outline", compactChoice(I18n.get(floorOutline.getNameKey()),
                        FloorDesigner.Outline.values(), o -> I18n.get(o.getNameKey()), o -> {
                            floorOutline = o;
                            regenerate();
                            rebuildScreen();
                        })));
                card.child(optRow("shapewright.floor_designer.border", Ui.toggleSwitch(floorBorder, () -> {
                    floorBorder = !floorBorder;
                    regenerate();
                    rebuildScreen();
                })));
            }
            case WALL -> {
                card.child(optRow("shapewright.wall_designer.width", intStep(wallWidth, 2, 7, 35, v -> {
                    wallWidth = v | 1;
                    regenerate();
                    rebuildScreen();
                })));
                card.child(optRow("shapewright.wall_designer.height", intStep(wallHeight, 1, 7, 35, v -> {
                    wallHeight = v;
                    regenerate();
                    rebuildScreen();
                })));
                card.child(optRow("shapewright.wall_designer.bays", intStep(wallBays, 1, 1, 5, v -> {
                    wallBays = v;
                    regenerate();
                    rebuildScreen();
                })));
                card.child(optRow("shapewright.surface_designer.depth_layers", intStep(wallDepthLayers, 1, 1, 3, v -> {
                    wallDepthLayers = v;
                    regenerate();
                    rebuildScreen();
                })));
            }
            case CEILING -> {
                card.child(optRow("shapewright.wall_designer.width", intStep(ceilingWidth, 2, 11, 61, v -> {
                    ceilingWidth = v | 1;
                    regenerate();
                    rebuildScreen();
                })));
                card.child(optRow("shapewright.surface_designer.depth", intStep(ceilingDepth, 2, 11, 61, v -> {
                    ceilingDepth = v | 1;
                    regenerate();
                    rebuildScreen();
                })));
                card.child(optRow("shapewright.surface_designer.depth_layers", intStep(ceilingDepthLayers, 1, 1, 3, v -> {
                    ceilingDepthLayers = v;
                    regenerate();
                    rebuildScreen();
                })));
                card.child(optRow("shapewright.floor_designer.border", Ui.toggleSwitch(ceilingBorder, () -> {
                    ceilingBorder = !ceilingBorder;
                    regenerate();
                    rebuildScreen();
                })));
            }
        }

        // Collapsible Advanced section
        String advLabel = (showAdvanced ? "▾ Advanced" : "▸ Advanced");
        ButtonComponent advBtn = Ui.flatBtn(Component.literal(advLabel).withColor(Ui.MUTED), b -> {
            showAdvanced = !showAdvanced;
            rebuildScreen();
        });
        card.child(w(advBtn).sizing(Sizing.fixed(cardInnerW), Sizing.fixed(14)));

        if (showAdvanced) {
            switch (tab) {
                case FLOOR -> {
                    card.child(optRow("shapewright.surface_designer.square_lock", Ui.toggleSwitch(floorSquareLock, () -> {
                        floorSquareLock = !floorSquareLock;
                        if (floorSquareLock) floorDepth = floorWidth;
                        regenerate();
                        rebuildScreen();
                    })));
                    card.child(optRow("shapewright.floor_designer.symmetry", compactChoice(floorSymmetry + "-way",
                            new Integer[]{4, 8, 12}, n -> n + "-way", n -> {
                                floorSymmetry = n;
                                regenerate();
                                rebuildScreen();
                            })));
                    card.child(optRow("shapewright.floor_designer.complexity", intStep(floorComplexity, 1, 1, 5, v -> {
                        floorComplexity = v;
                        regenerate();
                        rebuildScreen();
                    })));
                    card.child(optRow("shapewright.surface_designer.relief", Ui.toggleSwitch(floorRelief, () -> {
                        floorRelief = !floorRelief;
                        regenerate();
                        rebuildScreen();
                    })));
                    card.child(optRow("shapewright.floor_designer.seed", intStep(floorSeed, 1, 1, 9999, v -> {
                        floorSeed = v;
                        regenerate();
                        rebuildScreen();
                    })));
                }
                case WALL -> {
                    card.child(optRow("shapewright.wall_designer.open_window", Ui.toggleSwitch(wallOpenWindow, () -> {
                        wallOpenWindow = !wallOpenWindow;
                        regenerate();
                        rebuildScreen();
                    })));
                    card.child(optRow("shapewright.wall_designer.doorway", Ui.toggleSwitch(wallDoorway, () -> {
                        wallDoorway = !wallDoorway;
                        regenerate();
                        rebuildScreen();
                    })));
                    card.child(optRow("shapewright.wall_designer.battlements", Ui.toggleSwitch(wallBattlements, () -> {
                        wallBattlements = !wallBattlements;
                        regenerate();
                        rebuildScreen();
                    })));
                    card.child(optRow("shapewright.floor_designer.seed", intStep(wallSeed, 1, 1, 9999, v -> {
                        wallSeed = v;
                        regenerate();
                        rebuildScreen();
                    })));
                }
                case CEILING -> {
                    card.child(optRow("shapewright.floor_designer.complexity", intStep(ceilingComplexity, 1, 1, 5, v -> {
                        ceilingComplexity = v;
                        regenerate();
                        rebuildScreen();
                    })));
                    card.child(optRow("shapewright.floor_designer.seed", intStep(ceilingSeed, 1, 1, 9999, v -> {
                        ceilingSeed = v;
                        regenerate();
                        rebuildScreen();
                    })));
                }
            }
        }
        return card;
    }

    /** Card 3: 5 Horizontal Block Swatches (keys 1-5, right-click lock) + Random/Match + Theme. */
    private FlowLayout buildPaletteCard() {
        FlowLayout card = Ui.card(Containers.verticalFlow(Sizing.fixed(sideW - 10), Sizing.content()));
        card.gap(4);

        FlowLayout head = Containers.horizontalFlow(Sizing.fixed(cardInnerW), Sizing.content());
        head.verticalAlignment(VerticalAlignment.CENTER);
        head.child(Ui.label(Component.literal("PALETTE").withStyle(s -> s.withBold(true)), Ui.MUTED));
        head.child(Ui.hspace());
        head.child(Ui.label(Component.literal("1–5 · R-click lock"), 0x64748B));
        card.child(head);

        // 5 horizontal swatch tiles
        int swatchW = (cardInnerW - 12) / 5;
        FlowLayout swatches = Containers.horizontalFlow(Sizing.fixed(cardInnerW), Sizing.fixed(28));
        swatches.gap(3);
        for (int i = 0; i < 5; i++) {
            swatches.child(new SwatchTileComponent(i, swatchW, 28));
        }
        card.child(swatches);

        // Active slot block picker + lock button
        int slot = Math.max(0, Math.min(4, activeBrush));
        Item activeItem = itemOf(slotBlocks[slot]);
        String slotLabel = I18n.get("shapewright.floor_designer.slot." + SLOT_KEYS[slot]);
        String blockLabel = activeItem != null ? activeItem.getDescription().getString() : "None";
        String combined = slotLabel + ": " + blockLabel;
        if (font.width(combined) > cardInnerW - 28) {
            combined = font.plainSubstrByWidth(combined, cardInnerW - 34) + "…";
        }
        FlowLayout pickRow = Containers.horizontalFlow(Sizing.fixed(cardInnerW), Sizing.fixed(CONTROL_H));
        pickRow.gap(3);
        ButtonComponent pickBtn = Ui.flatBtn(Component.literal(combined), b -> {
            if (minecraft == null) return;
            minecraft.setScreen(new BlockPickerScreen(this,
                    Component.translatable("shapewright.floor_designer.slot." + SLOT_KEYS[slot]),
                    picked -> {
                        if (picked != null) slotBlocks[slot] = BuiltInRegistries.ITEM.getKey(picked).toString();
                    }));
        });
        pickRow.child(w(pickBtn).sizing(Sizing.fixed(cardInnerW - 21), Sizing.fixed(CONTROL_H)));

        boolean locked = slotLocked[slot];
        ButtonComponent lockBtn = Ui.pillTab(
                Component.literal(locked ? "L" : "·").withColor(locked ? Ui.ACCENT : Ui.MUTED),
                locked, b -> setSlotLocked(slot, !slotLocked[slot]));
        pickRow.child(w(lockBtn).sizing(Sizing.fixed(18), Sizing.fixed(CONTROL_H)));
        card.child(pickRow);

        // Random + Match buttons
        FlowLayout genRow = Containers.horizontalFlow(Sizing.fixed(cardInnerW), Sizing.fixed(CONTROL_H));
        genRow.gap(3);
        int halfW = (cardInnerW - 3) / 2;
        ButtonComponent randBtn = Ui.flatBtn(Component.translatable("shapewright.surface_designer.random_palette"), b -> randomizePalette());
        ButtonComponent matchBtn = Ui.flatBtn(Component.translatable("shapewright.surface_designer.match_palette"), b -> matchLockedPalette());
        genRow.child(w(randBtn).sizing(Sizing.fixed(halfW), Sizing.fixed(CONTROL_H)));
        genRow.child(w(matchBtn).sizing(Sizing.fixed(cardInnerW - 3 - halfW), Sizing.fixed(CONTROL_H)));
        card.child(genRow);

        // Theme dropdown + Save
        List<FloorThemes.Theme> themes = FloorThemes.get();
        if (themeIndex >= themes.size()) themeIndex = 0;
        FlowLayout themeRow = Containers.horizontalFlow(Sizing.fixed(cardInnerW), Sizing.fixed(CONTROL_H));
        themeRow.gap(3);
        int themeW = cardInnerW - 35;
        String tLabel = themeLabel(themes.get(themeIndex));
        if (font.width(tLabel) > themeW - 14) tLabel = font.plainSubstrByWidth(tLabel, themeW - 20) + "…";
        ButtonComponent themeBtn = Ui.flatBtn(Component.literal(tLabel + " ▾"),
                b -> Ui.openMenu(this, root, b, themeLabel(themes.get(themeIndex)), themes, FloorDesignerScreen::themeLabel, t -> {
                    themeIndex = themes.indexOf(t);
                    System.arraycopy(t.blocks(), 0, slotBlocks, 0, slotBlocks.length);
                    themeStatus = null;
                    rebuildScreen();
                }, () -> {}));
        ButtonComponent saveBtn = Ui.flatBtn(Component.translatable("shapewright.surface_designer.save_theme"), b -> saveCurrentTheme());
        themeRow.child(w(themeBtn).sizing(Sizing.fixed(themeW), Sizing.fixed(CONTROL_H)));
        themeRow.child(w(saveBtn).sizing(Sizing.fixed(32), Sizing.fixed(CONTROL_H)));
        card.child(themeRow);

        if (themeStatus != null) {
            card.child(Ui.label(Component.literal(themeStatus), 0x8FD18F));
        }
        return card;
    }

    private FlowLayout buildCanvasToolbar() {
        FlowLayout bar = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        bar.gap(2).verticalAlignment(VerticalAlignment.CENTER).margins(Insets.bottom(5));
        boolean compact = this.width < 520;

        for (Tool t : Tool.values()) {
            boolean active = t == activeTool;
            String text = compact ? I18n.get(t.getNameKey()).substring(0, 1) : I18n.get(t.getNameKey());
            Component lbl = Component.literal(text).withColor(active ? Ui.ACCENT : 0xB7C0D0);
            ButtonComponent btn = Ui.pillTab(lbl, active, b -> {
                activeTool = t;
                rebuildScreen();
            });
            bar.child(w(btn).sizing(Sizing.fixed(compact ? 18 : Math.max(32, font.width(lbl) + 8)), Sizing.fixed(CONTROL_H)));
        }

        bar.child(Ui.hspace());

        Component mirrorTxt = Component.literal(compact ? "M" : I18n.get("shapewright.surface_designer.mirror"))
                .withColor(paintMirror ? 0x8FD18F : Ui.MUTED);
        ButtonComponent mirrorBtn = Ui.pillTab(mirrorTxt, paintMirror, b -> { paintMirror = !paintMirror; rebuildScreen(); });
        bar.child(w(mirrorBtn).sizing(Sizing.fixed(compact ? 18 : font.width(mirrorTxt) + 8), Sizing.fixed(CONTROL_H)));

        Component gridTxt = Component.literal(compact ? "G" : I18n.get("shapewright.surface_designer.grid"))
                .withColor(showGrid ? 0x8FD18F : Ui.MUTED);
        ButtonComponent gridBtn = Ui.pillTab(gridTxt, showGrid, b -> { showGrid = !showGrid; rebuildScreen(); });
        bar.child(w(gridBtn).sizing(Sizing.fixed(compact ? 18 : font.width(gridTxt) + 8), Sizing.fixed(CONTROL_H)));

        bar.child(w(Ui.flatBtn(Component.literal("↶"), b -> undo())).sizing(Sizing.fixed(16), Sizing.fixed(CONTROL_H)));
        bar.child(w(Ui.flatBtn(Component.literal("↷"), b -> redo())).sizing(Sizing.fixed(16), Sizing.fixed(CONTROL_H)));

        Component resetTxt = Component.literal(compact ? "R" : I18n.get("shapewright.surface_designer.reset"));
        ButtonComponent clearBtn = Ui.flatBtn(resetTxt, b -> {
            if (!painted.isEmpty()) {
                pushUndo();
                painted.clear();
                updateInfo();
            }
        });
        bar.child(w(clearBtn).sizing(Sizing.fixed(compact ? 18 : font.width(resetTxt) + 8), Sizing.fixed(CONTROL_H)));
        return bar;
    }

    private void saveCurrentTheme() {
        String raw = nameBox != null && !nameBox.getValue().isBlank() ? nameBox.getValue() : "custom_theme";
        String id = SchematicWriter.safeName(raw);
        try {
            FloorThemes.saveCustomTheme(id, raw, slotBlocks);
            List<FloorThemes.Theme> themes = FloorThemes.get();
            for (int i = 0; i < themes.size(); i++) {
                if (themes.get(i).id().equals(id)) {
                    themeIndex = i;
                    break;
                }
            }
            themeStatus = I18n.get("shapewright.surface_designer.theme_saved", raw);
        } catch (IOException e) {
            themeStatus = "Save failed";
        }
        rebuildScreen();
    }

    private void rebuildScreen() {
        if (root == null) return;
        root.clearChildren();
        build(root);
    }

    private void updateInfo() {
        if (infoLabel == null || grid == null) return;
        int h = grid.length, w = grid[0].length;
        int count = 0;
        for (int r = 0; r < h; r++)
            for (int c = 0; c < w; c++)
                if (cellAt(c, r) != FloorDesigner.EMPTY) count++;
        int depth = switch (tab) {
            case FLOOR -> floorRelief ? 2 : 1;
            case WALL -> wallDepthLayers;
            case CEILING -> ceilingDepthLayers;
        };
        String dims = depth > 1 ? (w + " × " + h + " × " + depth) : (w + " × " + h);
        infoLabel.text(Component.literal(dims + "  ·  " + count + " blocks"));
    }

    private int cellAt(int x, int y) {
        Integer over = painted.get(((long) y << 32) | (x & 0xFFFFFFFFL));
        return over != null ? over : grid[y][x];
    }

    private void paintCell(int x, int y, int value) {
        int h = grid.length, w = grid[0].length;
        if (x < 0 || y < 0 || x >= w || y >= h) return;
        if (tab == Tab.FLOOR && grid[y][x] == FloorDesigner.EMPTY && value != FloorDesigner.EMPTY) return;
        setPaint(x, y, value);
        if (paintMirror) {
            int mx = w - 1 - x;
            int my = h - 1 - y;
            if (tab == Tab.WALL) {
                setPaint(mx, y, value);
            } else {
                setPaint(mx, y, value);
                setPaint(x, my, value);
                setPaint(mx, my, value);
                if (w == h) {
                    setPaint(y, x, value);
                    setPaint(my, x, value);
                    setPaint(y, mx, value);
                    setPaint(my, mx, value);
                }
            }
        }
        updateInfo();
    }

    private void floodFill(int sx, int sy, int replacement) {
        int h = grid.length, w = grid[0].length;
        if (sx < 0 || sy < 0 || sx >= w || sy >= h) return;
        int target = cellAt(sx, sy);
        if (target == replacement) return;
        if (tab == Tab.FLOOR && target == FloorDesigner.EMPTY) return;

        pushUndo();
        Deque<int[]> q = new ArrayDeque<>();
        boolean[][] seen = new boolean[h][w];
        q.add(new int[]{sx, sy});
        seen[sy][sx] = true;
        while (!q.isEmpty()) {
            int[] p = q.poll();
            int cx = p[0], cy = p[1];
            setPaint(cx, cy, replacement);
            int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] d : dirs) {
                int nx = cx + d[0], ny = cy + d[1];
                if (nx < 0 || ny < 0 || nx >= w || ny >= h || seen[ny][nx]) continue;
                if (cellAt(nx, ny) == target) {
                    seen[ny][nx] = true;
                    q.add(new int[]{nx, ny});
                }
            }
        }
        updateInfo();
    }

    private void setPaint(int x, int y, int value) {
        if (tab == Tab.FLOOR && grid[y][x] == FloorDesigner.EMPTY) return;
        painted.put(((long) y << 32) | (x & 0xFFFFFFFFL), value);
    }

    private void saveAndUse() {
        if (minecraft == null) return;
        int h = grid.length, w = grid[0].length;
        int halfW = w / 2, halfH = h / 2;
        BlockState[] states = new BlockState[slotBlocks.length];
        for (int i = 0; i < slotBlocks.length; i++) {
            Item item = itemOf(slotBlocks[i]);
            if (item instanceof BlockItem bi) states[i] = bi.getBlock().defaultBlockState();
        }
        Map<Cell, BlockState> blocks = new HashMap<>();
        for (int r = 0; r < h; r++) {
            for (int c = 0; c < w; c++) {
                int idx = cellAt(c, r);
                if (idx < 0 || idx >= states.length || states[idx] == null) continue;
                int rel = relief != null ? relief[r][c] : 0;
                switch (tab) {
                    case FLOOR -> {
                        blocks.put(new Cell(c - halfW, 0, r - halfH), states[idx]);
                        if (floorRelief && rel > 0) {
                            blocks.put(new Cell(c - halfW, 1, r - halfH), states[idx]);
                        }
                    }
                    case WALL -> {
                        int wy = h - 1 - r;
                        blocks.put(new Cell(c - halfW, wy, rel), states[idx]);
                        if (rel > 0 && states[WallDesigner.BASE] != null) {
                            blocks.put(new Cell(c - halfW, wy, 0), states[WallDesigner.BASE]);
                        }
                    }
                    case CEILING -> {
                        int cy = -rel;
                        blocks.put(new Cell(c - halfW, cy, r - halfH), states[idx]);
                        if (rel > 0 && states[CeilingDesigner.BASE] != null) {
                            blocks.put(new Cell(c - halfW, 0, r - halfH), states[CeilingDesigner.BASE]);
                            for (int fillY = -1; fillY > cy; fillY--) {
                                blocks.put(new Cell(c - halfW, fillY, r - halfH), states[idx]);
                            }
                        }
                    }
                }
            }
        }
        String raw = nameBox != null && !nameBox.getValue().isBlank() ? nameBox.getValue() : defaultNameForTab();
        String name = SchematicWriter.safeName(raw);
        try {
            SchematicWriter.write(SchematicLibrary.FOLDERS.getFirst().resolve(name + ".schem"), blocks);
            onCreated.accept(name);
            onClose();
        } catch (IOException e) {
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(Component.translatable("shapewright.message.export_failed", e.getMessage()), false);
            }
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            if (keyCode == GLFW.GLFW_KEY_Z) { undo(); return true; }
            if (keyCode == GLFW.GLFW_KEY_Y) { redo(); return true; }
        }
        if (nameBox == null || !nameBox.isFocused()) {
            if (keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_5) {
                activeBrush = keyCode - GLFW.GLFW_KEY_1;
                if (activeTool == Tool.ERASER || activeTool == Tool.PICK) activeTool = Tool.BRUSH;
                rebuildScreen();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ---- UI helpers ----

    private FlowLayout optRow(String labelKey, io.wispforest.owo.ui.core.Component control) {
        FlowLayout line = Containers.horizontalFlow(Sizing.fixed(cardInnerW), Sizing.fixed(16));
        line.verticalAlignment(VerticalAlignment.CENTER);
        line.child(Ui.label(Component.translatable(labelKey), 0xB7C0D0));
        line.child(Ui.hspace());
        line.child(control);
        return line;
    }

    private <T> io.wispforest.owo.ui.core.Component compactChoice(String shown, T[] options, Function<T, String> name, Consumer<T> pick) {
        int w = 96;
        String text = shown;
        if (font.width(text) > w - 16) text = font.plainSubstrByWidth(text, w - 22) + "…";
        ButtonComponent btn = Ui.flatBtn(Component.literal(text + " ▾"),
                b -> Ui.openMenu(this, root, b, shown, List.of(options), name, pick, () -> {}));
        return w(btn).sizing(Sizing.fixed(w), Sizing.fixed(CONTROL_H));
    }

    private io.wispforest.owo.ui.core.Component intStep(int value, int step, int min, int max, Consumer<Integer> set) {
        FlowLayout flow = Containers.horizontalFlow(Sizing.fixed(82), Sizing.fixed(CONTROL_H));
        flow.gap(2).verticalAlignment(VerticalAlignment.CENTER);
        flow.child(w(Ui.flatBtn(Component.literal("−"), b -> set.accept(Math.max(min, value - step))))
                .sizing(Sizing.fixed(16), Sizing.fixed(CONTROL_H)));
        LabelComponent val = Ui.label(Component.literal(String.valueOf(value)), 0xFFFFFF);
        val.horizontalSizing(Sizing.fixed(46));
        val.horizontalTextAlignment(HorizontalAlignment.CENTER);
        flow.child(val);
        flow.child(w(Ui.flatBtn(Component.literal("+"), b -> set.accept(Math.min(max, value + step))))
                .sizing(Sizing.fixed(16), Sizing.fixed(CONTROL_H)));
        return flow;
    }

    private int[] resolveSlotColors() {
        int[] colors = new int[slotBlocks.length];
        for (int i = 0; i < slotBlocks.length; i++) {
            Item item = itemOf(slotBlocks[i]);
            colors[i] = item != null ? (0xFF000000 | BlockColorCache.colorOf(item)) : 0xFF888888;
        }
        return colors;
    }

    private static Item itemOf(String id) {
        ResourceLocation loc = id == null || id.isEmpty() ? null : ResourceLocation.tryParse(id);
        return loc == null ? null : BuiltInRegistries.ITEM.get(loc);
    }

    // ---- Visual Preset Thumbnail Tile ----

    private class PresetTileComponent extends BaseComponent {
        private final int[][] mini;
        private final int[] colors;
        private final boolean selected;
        private final Runnable onPick;

        PresetTileComponent(int[][] mini, int[] colors, boolean selected, int size, Runnable onPick) {
            this.mini = mini;
            this.colors = colors;
            this.selected = selected;
            this.onPick = onPick;
            sizing(Sizing.fixed(size), Sizing.fixed(size));
        }

        @Override
        public void draw(OwoUIDrawContext ctx, int mouseX, int mouseY, float partialTicks, float delta) {
            boolean hover = isInBoundingBox(mouseX, mouseY);
            ctx.fill(x, y, x + width, y + height, 0xFF12161E);
            int pad = 3;
            int gh = mini.length, gw = mini[0].length;
            int inner = Math.min(width, height) - pad * 2;
            int px = Math.max(1, inner / Math.max(gw, gh));
            int ox = x + (width - px * gw) / 2;
            int oy = y + (height - px * gh) / 2;
            for (int r = 0; r < gh; r++) {
                for (int c = 0; c < gw; c++) {
                    int idx = mini[r][c];
                    if (idx < 0 || idx >= colors.length) continue;
                    ctx.fill(ox + c * px, oy + r * px, ox + (c + 1) * px, oy + (r + 1) * px, colors[idx]);
                }
            }
            int rim = selected ? Ui.GREEN : (hover ? Ui.BORDER_HOVER : Ui.BORDER);
            ctx.renderOutline(x, y, width, height, rim);
        }

        @Override
        public boolean onMouseDown(double mouseX, double mouseY, int button) {
            if (button == 0) {
                onPick.run();
                return true;
            }
            return false;
        }
    }

    // ---- Horizontal Block Swatch Tile ----

    private class SwatchTileComponent extends BaseComponent {
        private final int slot;

        SwatchTileComponent(int slot, int w, int h) {
            this.slot = slot;
            sizing(Sizing.fixed(w), Sizing.fixed(h));
        }

        @Override
        public void draw(OwoUIDrawContext ctx, int mouseX, int mouseY, float partialTicks, float delta) {
            boolean selected = activeBrush == slot && activeTool != Tool.ERASER;
            boolean locked = slotLocked[slot];
            boolean hover = isInBoundingBox(mouseX, mouseY);

            ctx.fill(x, y, x + width, y + height, selected ? 0xFF252D3F : 0xFF12161F);
            Item item = itemOf(slotBlocks[slot]);
            if (item != null && item != Items.AIR) {
                ctx.renderItem(new ItemStack(item), x + (width - 16) / 2, y + (height - 16) / 2);
            }
            // Slot number badge (top-left)
            ctx.drawString(font, String.valueOf(slot + 1), x + 2, y + 2, selected ? Ui.ACCENT : 0x64748B, false);
            // Gold lock badge (bottom-right)
            if (locked) {
                ctx.fill(x + width - 9, y + height - 10, x + width - 1, y + height - 1, 0xCC151922);
                ctx.drawString(font, "L", x + width - 7, y + height - 9, Ui.ACCENT, false);
            }
            int rim = selected ? 0xFFFFFFFF : (locked ? Ui.AMBER_BORDER : (hover ? Ui.BORDER_HOVER : Ui.BORDER));
            ctx.renderOutline(x, y, width, height, rim);
        }

        @Override
        public boolean onMouseDown(double mouseX, double mouseY, int button) {
            if (button == 1) {
                setSlotLocked(slot, !slotLocked[slot]);
                return true;
            }
            if (button == 0) {
                activeBrush = slot;
                if (activeTool == Tool.ERASER || activeTool == Tool.PICK) activeTool = Tool.BRUSH;
                rebuildScreen();
                return true;
            }
            return false;
        }
    }

    // ---- Live 2D Relief-Shaded Canvas ----

    private class CanvasComponent extends BaseComponent {
        private int drawX, drawY, cellPx;

        @Override
        public void draw(OwoUIDrawContext ctx, int mouseX, int mouseY, float partialTicks, float delta) {
            ctx.fill(x, y, x + width, y + height, 0xFF10131A);
            if (grid == null || grid.length == 0) return;
            int gh = grid.length, gw = grid[0].length;
            int availW = Math.max(16, width - 16);
            int availH = Math.max(16, height - 28);
            cellPx = Math.max(2, Math.min(availW / gw, availH / gh));
            int totalW = cellPx * gw;
            int totalH = cellPx * gh;
            drawX = x + (width - totalW) / 2;
            drawY = y + (height - 14 - totalH) / 2;

            int[] colors = resolveSlotColors();
            ctx.fill(drawX, drawY, drawX + totalW, drawY + totalH, 0xFF181C24);

            for (int r = 0; r < gh; r++) {
                for (int c = 0; c < gw; c++) {
                    int idx = cellAt(c, r);
                    int px = drawX + c * cellPx, py = drawY + r * cellPx;
                    if (idx < 0 || idx >= colors.length) {
                        if (((c + r) & 1) == 0) ctx.fill(px, py, px + cellPx, py + cellPx, 0xFF1D222C);
                        continue;
                    }
                    ctx.fill(px, py, px + cellPx, py + cellPx, colors[idx]);
                    int rel = relief != null ? relief[r][c] : 0;
                    if (rel > 0 && cellPx >= 4) {
                        ctx.fill(px, py, px + cellPx, py + 1, 0x45FFFFFF);
                        ctx.fill(px, py, px + 1, py + cellPx, 0x35FFFFFF);
                        ctx.fill(px, py + cellPx - 1, px + cellPx, py + cellPx, 0x45000000);
                    } else if (rel < 0 && cellPx >= 4) {
                        ctx.fill(px, py, px + cellPx, py + cellPx, 0x30000000);
                    }
                    if (showGrid && cellPx >= 5) {
                        ctx.fill(px + cellPx - 1, py, px + cellPx, py + cellPx, 0x22000000);
                        ctx.fill(px, py + cellPx - 1, px + cellPx, py + cellPx, 0x22000000);
                    }
                }
            }
            ctx.renderOutline(drawX - 1, drawY - 1, totalW + 2, totalH + 2, Ui.BORDER_HOVER);

            int hx = (int) Math.floor((mouseX - drawX) / (double) cellPx);
            int hy = (int) Math.floor((mouseY - drawY) / (double) cellPx);
            String status;
            if (hx >= 0 && hy >= 0 && hx < gw && hy < gh) {
                ctx.renderOutline(drawX + hx * cellPx, drawY + hy * cellPx, cellPx, cellPx, 0xCCFFFFFF);
                int idx = cellAt(hx, hy);
                String slotName = (idx >= 0 && idx < SLOT_KEYS.length)
                        ? I18n.get("shapewright.floor_designer.slot." + SLOT_KEYS[idx])
                        : "Empty";
                int rel = relief != null ? relief[hy][hx] : 0;
                String relStr = rel != 0 ? ("  ·  depth " + (rel > 0 ? "+" + rel : rel)) : "";
                status = "(" + hx + ", " + hy + ")  ·  " + slotName + relStr;
            } else {
                status = I18n.get("shapewright.surface_designer.hint");
            }
            ctx.drawString(font, status, x + 6, y + height - 11, Ui.MUTED, false);
        }

        private boolean applyToolAt(double mouseX, double mouseY, int button, boolean isClick) {
            if (cellPx <= 0 || grid == null) return false;
            int gh = grid.length, gw = grid[0].length;
            int gx = (int) Math.floor((mouseX - drawX) / cellPx);
            int gy = (int) Math.floor((mouseY - drawY) / cellPx);
            if (gx < 0 || gy < 0 || gx >= gw || gy >= gh) return false;

            if (activeTool == Tool.PICK) {
                int picked = cellAt(gx, gy);
                if (picked >= 0 && picked < SLOT_KEYS.length) {
                    activeBrush = picked;
                    activeTool = Tool.BRUSH;
                    rebuildScreen();
                }
                return true;
            }

            int val = (button == 1 || activeTool == Tool.ERASER)
                    ? (tab == Tab.FLOOR ? FloorDesigner.BASE : FloorDesigner.EMPTY)
                    : activeBrush;

            if (activeTool == Tool.FILL && isClick) {
                floodFill(gx, gy, val);
                return true;
            }

            if (!strokeSnapshotTaken) {
                pushUndo();
                strokeSnapshotTaken = true;
            }
            paintCell(gx, gy, val);
            return true;
        }

        @Override
        public boolean onMouseDown(double mouseX, double mouseY, int button) {
            strokeSnapshotTaken = false;
            return applyToolAt(mouseX, mouseY, button, true);
        }

        @Override
        public boolean onMouseDrag(double mouseX, double mouseY, double deltaX, double deltaY, int button) {
            if (activeTool == Tool.FILL || activeTool == Tool.PICK) return false;
            return applyToolAt(mouseX, mouseY, button, false);
        }

        @Override
        public boolean onMouseUp(double mouseX, double mouseY, int button) {
            strokeSnapshotTaken = false;
            return super.onMouseUp(mouseX, mouseY, button);
        }
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
