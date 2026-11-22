package io.github.watdafawx.shapewright.shape;

import java.util.Random;

/**
 * Procedural multi-block ceiling designer: produces an {@code int[depth][width]} grid of palette
 * indices ({@code -1} = empty/skylight, {@code 0} = base, {@code 1} = primary, {@code 2} = secondary,
 * {@code 3} = trim/beams, {@code 4} = accent/lights) plus an optional {@code int[depth][width]}
 * vertical relief map ({@code > 0} hangs down into the room, {@code < 0} recesses upward).
 */
public final class CeilingDesigner {

    public static final int EMPTY = -1;
    public static final int BASE = 0;
    public static final int PRIMARY = 1;
    public static final int SECONDARY = 2;
    public static final int TRIM = 3;
    public static final int ACCENT = 4;

    public enum Style {
        COFFERED("coffered"),
        RIBBED_VAULT("ribbed_vault"),
        DOME("dome"),
        CHANDELIER("chandelier"),
        TIMBER_BEAMS("timber_beams"),
        SKYLIGHT("skylight");

        public final String key;
        Style(String key) { this.key = key; }
        public String getNameKey() { return "shapewright.ceiling_designer.style." + key; }
    }

    public record Config(Style style, int width, int depth, int complexity, int depthLayers, int seed, boolean border) {
        public Config {
            width = Math.max(11, Math.min(61, width | 1));
            depth = Math.max(11, Math.min(61, depth | 1));
            complexity = Math.max(1, Math.min(5, complexity));
            depthLayers = Math.max(1, Math.min(3, depthLayers));
        }

        public static Config defaults() {
            return new Config(Style.COFFERED, 25, 25, 3, 2, 1, true);
        }
    }

    public record Result(int[][] grid, int[][] relief) {}

    private CeilingDesigner() {}

    public static Result generate(Config cfg) {
        int w = cfg.width(), d = cfg.depth();
        int[][] grid = new int[d][w];
        int[][] relief = new int[d][w];
        Random rng = new Random(cfg.seed() * 0x9E3779B97F4A7C15L + cfg.style().ordinal() * 37L + cfg.complexity());

        switch (cfg.style()) {
            case COFFERED -> coffered(grid, relief, cfg);
            case RIBBED_VAULT -> ribbedVault(grid, relief, cfg);
            case DOME -> dome(grid, relief, cfg);
            case CHANDELIER -> chandelier(grid, relief, cfg, rng);
            case TIMBER_BEAMS -> timberBeams(grid, relief, cfg);
            case SKYLIGHT -> skylight(grid, relief, cfg);
        }

        if (cfg.border()) {
            for (int z = 0; z < d; z++) {
                for (int x = 0; x < w; x++) {
                    if (x == 0 || z == 0 || x == w - 1 || z == d - 1) {
                        grid[z][x] = TRIM;
                        relief[z][x] = Math.min(1, cfg.depthLayers() - 1);
                    }
                }
            }
        }

        if (cfg.depthLayers() <= 1) {
            for (int z = 0; z < d; z++) {
                for (int x = 0; x < w; x++) relief[z][x] = 0;
            }
        }
        return new Result(grid, relief);
    }

    /** 1. Coffered ceiling: recessed square panels framed by hanging beams, central lights. */
    private static void coffered(int[][] g, int[][] rel, Config cfg) {
        int d = g.length, w = g[0].length;
        int cell = Math.max(4, 9 - cfg.complexity()); // 8,7,6,5,4
        int drop = cfg.depthLayers() - 1;
        for (int z = 0; z < d; z++) {
            for (int x = 0; x < w; x++) {
                int lx = x % cell;
                int lz = z % cell;
                boolean beam = (lx == 0 || lz == 0 || x == w - 1 || z == d - 1);
                boolean step = (lx == 1 || lz == 1 || lx == cell - 1 || lz == cell - 1);
                boolean center = (lx == cell / 2 && lz == cell / 2);
                if (beam) {
                    g[z][x] = (lx == 0 && lz == 0) ? PRIMARY : TRIM;
                    rel[z][x] = drop;
                } else if (center) {
                    g[z][x] = ACCENT;
                    rel[z][x] = 0;
                } else if (step) {
                    g[z][x] = SECONDARY;
                    rel[z][x] = Math.max(0, drop - 1);
                } else {
                    g[z][x] = BASE;
                    rel[z][x] = 0;
                }
            }
        }
    }

    /** 2. Ribbed Vault: gothic diagonal + cardinal stone ribs meeting at a central boss. */
    private static void ribbedVault(int[][] g, int[][] rel, Config cfg) {
        int d = g.length, w = g[0].length;
        int cx = w / 2, cz = d / 2;
        int maxDrop = cfg.depthLayers() - 1;
        for (int z = 0; z < d; z++) {
            for (int x = 0; x < w; x++) {
                double dx = (x - cx) / (cx + 0.5);
                double dz = (z - cz) / (cz + 0.5);
                double r = Math.hypot(dx, dz);
                double ax = Math.abs(dx), az = Math.abs(dz);
                boolean onCardinal = Math.abs(x - cx) <= 0 || Math.abs(z - cz) <= 0;
                boolean onDiag = Math.abs(ax - az) < (0.09 + 0.015 * cfg.complexity());
                boolean onOuter = ax > 0.88 || az > 0.88;

                if (Math.abs(x - cx) <= 1 && Math.abs(z - cz) <= 1) {
                    g[z][x] = ACCENT;
                    rel[z][x] = maxDrop;
                } else if (onCardinal || onDiag || onOuter) {
                    g[z][x] = (onOuter ? PRIMARY : TRIM);
                    rel[z][x] = maxDrop;
                } else {
                    // Vault web rises toward center (negative relief when depthLayers >= 3)
                    int webRel = maxDrop >= 2 && r < 0.65 ? -1 : 0;
                    g[z][x] = ((Math.abs(x - cx) + Math.abs(z - cz)) % 4 == 0) ? SECONDARY : BASE;
                    rel[z][x] = webRel;
                }
            }
        }
    }

    /** 3. Recessed Dome: concentric stepped rings rising upward to a glowing oculus. */
    private static void dome(int[][] g, int[][] rel, Config cfg) {
        int d = g.length, w = g[0].length;
        int cx = w / 2, cz = d / 2;
        int maxStep = cfg.depthLayers() - 1;
        for (int z = 0; z < d; z++) {
            for (int x = 0; x < w; x++) {
                double dx = (x - cx) / (cx + 0.5);
                double dz = (z - cz) / (cz + 0.5);
                double r = Math.hypot(dx, dz);
                if (r < 0.14) {
                    g[z][x] = ACCENT;
                    rel[z][x] = -maxStep;
                } else if (r < 0.22) {
                    g[z][x] = TRIM;
                    rel[z][x] = -maxStep;
                } else if (r < 0.52) {
                    double ang = Math.atan2(dz, dx);
                    int spokes = 4 + cfg.complexity() * 2;
                    boolean rib = Math.abs(Math.sin(ang * spokes / 2.0)) < 0.14;
                    g[z][x] = rib ? TRIM : PRIMARY;
                    rel[z][x] = -(maxStep > 0 ? 1 : 0);
                } else if (r < 0.80) {
                    g[z][x] = r > 0.72 ? TRIM : SECONDARY;
                    rel[z][x] = 0;
                } else {
                    g[z][x] = BASE;
                    rel[z][x] = maxStep > 0 ? 1 : 0;
                }
            }
        }
    }

    /** 4. Chandelier Medallion: ornate ceiling rosette with hanging multi-tier center fixture. */
    private static void chandelier(int[][] g, int[][] rel, Config cfg, Random rng) {
        int d = g.length, w = g[0].length;
        int cx = w / 2, cz = d / 2;
        int drop = cfg.depthLayers();
        double phase = rng.nextDouble() * Math.PI;
        for (int z = 0; z < d; z++) {
            for (int x = 0; x < w; x++) {
                double dx = (x - cx) / (cx + 0.5);
                double dz = (z - cz) / (cz + 0.5);
                double r = Math.hypot(dx, dz);
                double ang = Math.atan2(dz, dx);

                if (r < 0.10) {
                    g[z][x] = ACCENT;
                    rel[z][x] = drop;
                } else if (r < 0.24) {
                    boolean arm = Math.abs(Math.sin(ang * 4 + phase)) < 0.22;
                    g[z][x] = arm ? TRIM : PRIMARY;
                    rel[z][x] = Math.max(1, drop - 1);
                } else if (Math.abs(r - 0.34) < 0.06) {
                    g[z][x] = (Math.cos(ang * 8) > 0.3) ? ACCENT : TRIM;
                    rel[z][x] = Math.min(1, drop - 1);
                } else if (r < 0.62) {
                    double petal = Math.cos(ang * (4 + cfg.complexity() * 2));
                    g[z][x] = petal > 0.35 ? SECONDARY : BASE;
                    rel[z][x] = 0;
                } else if (Math.abs(r - 0.78) < 0.05) {
                    g[z][x] = TRIM;
                    rel[z][x] = Math.min(1, drop - 1);
                } else {
                    g[z][x] = BASE;
                    rel[z][x] = 0;
                }
            }
        }
    }

    /** 5. Timber Beams: heavy parallel rafters with cross purlins and hanging lantern accents. */
    private static void timberBeams(int[][] g, int[][] rel, Config cfg) {
        int d = g.length, w = g[0].length;
        int spacing = Math.max(3, 7 - cfg.complexity());
        int drop = cfg.depthLayers() - 1;
        for (int z = 0; z < d; z++) {
            for (int x = 0; x < w; x++) {
                boolean mainBeam = (z % spacing == 0);
                boolean crossBeam = (x % (spacing * 2) == 0);
                if (mainBeam && crossBeam) {
                    g[z][x] = ACCENT;
                    rel[z][x] = drop;
                } else if (mainBeam) {
                    g[z][x] = PRIMARY;
                    rel[z][x] = drop;
                } else if (crossBeam) {
                    g[z][x] = TRIM;
                    rel[z][x] = Math.max(0, drop - 1);
                } else {
                    g[z][x] = ((x + z) % 2 == 0 && cfg.complexity() >= 4) ? SECONDARY : BASE;
                    rel[z][x] = 0;
                }
            }
        }
    }

    /** 6. Skylight: framed central glass/open skylight with decorative coffered surround. */
    private static void skylight(int[][] g, int[][] rel, Config cfg) {
        int d = g.length, w = g[0].length;
        int cx = w / 2, cz = d / 2;
        int openW = Math.max(2, w / 4);
        int openD = Math.max(2, d / 4);
        int drop = cfg.depthLayers() - 1;
        for (int z = 0; z < d; z++) {
            for (int x = 0; x < w; x++) {
                int ax = Math.abs(x - cx), az = Math.abs(z - cz);
                if (ax < openW && az < openD) {
                    // Mullion cross inside skylight
                    if (ax == 0 || az == 0) {
                        g[z][x] = TRIM;
                        rel[z][x] = 0;
                    } else {
                        g[z][x] = EMPTY;
                        rel[z][x] = 0;
                    }
                } else if (ax == openW && az <= openD || az == openD && ax <= openW) {
                    g[z][x] = PRIMARY;
                    rel[z][x] = drop;
                } else if (ax == openW + 2 && az == openD + 2) {
                    g[z][x] = ACCENT;
                    rel[z][x] = 0;
                } else if (ax <= openW + 2 && az <= openD + 2) {
                    g[z][x] = SECONDARY;
                    rel[z][x] = 0;
                } else {
                    g[z][x] = BASE;
                    rel[z][x] = 0;
                }
            }
        }
    }
}
