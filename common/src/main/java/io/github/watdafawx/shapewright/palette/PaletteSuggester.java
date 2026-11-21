package io.github.watdafawx.shapewright.palette;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Suggests block palettes from block colors and texture roughness. Colors are compared in OKLab
 * (scaled ×100 so L ∈ [0, 100]), where perceptual distance and hue uniformity are much cleaner
 * than CIE76 CIELAB—especially for darks, blues, and gradients. Pure: works on ids, RGB and roughness only.
 */
public final class PaletteSuggester {

    /** A block, its average texture color (0xRRGGBB), and texture roughness (luminance std-dev 0..1). */
    public record Swatch(String id, int rgb, float roughness) {
        public Swatch(String id, int rgb) {
            this(id, rgb, 0.15f);
        }
    }

    /** Suggestion strategies. */
    public enum Mode {
        /** Dark to light in the first block's color family. */
        SHADES,
        /** The closest colors to the first block. */
        SIMILAR,
        /** Even steps from the first block to the last. */
        BLEND,
        /** Nearby hues on the color wheel. */
        NEIGHBORS,
        /** The first block's color and its opposite, with lighter and darker versions. */
        COMPLEMENT,
        /** Three hues evenly spaced around the wheel. */
        TRIAD,
        /** The first block's hue from near-black to near-white. */
        CONTRAST,
        /** A random colorful block and a random one of the modes above. */
        SURPRISE;

        public String getNameKey() {
            return "shapewright.screen.suggest." + name().toLowerCase();
        }
    }

    /** Picked blocks must differ from each other by at least this much (OKLab×100; ~2.0 is just noticeable). */
    static final double MIN_DIFFERENCE = 2.0;
    /** With a seed, choose among this many best matches. */
    private static final int REROLL_CHOICES = 4;
    /** Chroma threshold (OKLab×100) below which a block is considered neutral grey/black/white. */
    private static final double GREY_CHROMA = 3.5;
    /** Gentle penalty weight for texture roughness mismatch (0..1 diff → 0..18 OKLab units). */
    private static final double ROUGHNESS_WEIGHT = 18.0;

    private PaletteSuggester() {}

    /**
     * @param start the palette's current blocks (first and last matter); must not be empty
     * @param all   every block to choose from
     * @param seed  0 for the best matches, anything else for a varied pick
     */
    public static List<Swatch> suggest(Mode mode, List<Swatch> start, List<Swatch> all, int count, long seed) {
        return suggest(mode, start, all, count, seed, Set.of());
    }

    /**
     * Suggests a palette while preserving any blocks whose ids are in {@code pinnedIds}.
     */
    public static List<Swatch> suggest(Mode mode, List<Swatch> start, List<Swatch> all, int count, long seed, Set<String> pinnedIds) {
        Random rng = seed == 0 ? null : new Random(seed);
        Swatch first = start.getFirst();
        if (mode == Mode.SURPRISE) {
            Random r = rng != null ? rng : new Random(1);
            List<Swatch> colorful = all.stream().filter(w -> lch(lab(w.rgb()))[1] > 6.0).toList();
            Swatch pick = colorful.isEmpty() ? first : colorful.get(r.nextInt(colorful.size()));
            Mode[] modes = {Mode.SHADES, Mode.NEIGHBORS, Mode.COMPLEMENT, Mode.TRIAD};
            return suggest(modes[r.nextInt(modes.length)], List.of(pick), all, count, r.nextLong() | 1, pinnedIds);
        }

        double[] s = lch(lab(first.rgb()));
        boolean isGrey = s[1] < GREY_CHROMA;
        // For grey/black seeds, pick a clean deterministic hue instead of amplifying grey's noisy hue angle
        double baseHue = isGrey ? ((seed == 0 ? 45.0 : Math.abs(seed % 360))) : s[2];
        List<double[]> targets = new ArrayList<>();
        List<Swatch> pool = all;
        switch (mode) {
            case SIMILAR -> {
                for (int i = 1; i < count; i++) targets.add(s);
            }
            case SHADES -> {
                pool = family(first, all);
                double lo = 100, hi = 0;
                for (Swatch w : pool) { double l = lab(w.rgb())[0]; lo = Math.min(lo, l); hi = Math.max(hi, l); }
                for (int i = 0; i < count; i++) targets.add(new double[]{lo + (hi - lo) * i / Math.max(1, count - 1), s[1], s[2]});
            }
            case BLEND -> {
                double[] a = lab(first.rgb()), b = lab(start.getLast().rgb());
                for (int i = 1; i < count - 1; i++) {
                    double t = (double) i / (count - 1);
                    targets.add(lch(new double[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t}));
                }
            }
            case NEIGHBORS -> {
                if (isGrey) {
                    // Subtle warm/cool neutral shifts around the grey seed
                    for (int i = 1; i < count; i++) {
                        double dl = (i % 2 == 1 ? 1 : -1) * 8.0 * ((i + 1) / 2);
                        targets.add(new double[]{clampL(s[0] + dl), 2.5, (baseHue + i * 40) % 360});
                    }
                } else {
                    for (int i = 1; i < count; i++) {
                        double step = 30.0 * ((i + 1) / 2) * (i % 2 == 1 ? 1 : -1);
                        targets.add(new double[]{s[0], Math.max(s[1], 8.0), s[2] + step});
                    }
                }
            }
            case COMPLEMENT -> {
                if (isGrey) {
                    // Pair dark/grey seed with clean accent + neutral steps
                    double[][] variants = {{10, 0, 12.0}, {18, 0, 2.0}, {-14, 0, 2.0}, {22, 180, 10.0}, {-10, 0, 10.0}};
                    for (int i = 0; i < count - 1; i++) {
                        double[] v = variants[i % variants.length];
                        targets.add(new double[]{clampL(s[0] + v[0]), v[2], (baseHue + v[1]) % 360});
                    }
                } else {
                    double[][] variants = {{0, 180}, {10, 0}, {10, 180}, {-10, 0}, {-10, 180}, {20, 0}, {20, 180}, {-20, 0}};
                    for (int i = 0; i < count - 1; i++) {
                        double[] v = variants[i % variants.length];
                        targets.add(new double[]{clampL(s[0] + v[0]), Math.max(s[1], 8.0), s[2] + v[1]});
                    }
                }
            }
            case TRIAD -> {
                for (int i = 1; i < count; i++) {
                    double lightness = s[0] + (i / 3) * (i % 2 == 0 ? 10 : -10);
                    double chroma = isGrey ? 9.0 : Math.max(s[1], 8.0);
                    targets.add(new double[]{clampL(lightness), chroma, (baseHue + 120 * (i % 3)) % 360});
                }
            }
            case CONTRAST -> {
                for (int i = 0; i < count; i++) targets.add(new double[]{18 + 72.0 * i / Math.max(1, count - 1), s[1] * 0.6, s[2]});
            }
            default -> { }
        }

        List<Swatch> picked = new ArrayList<>();
        // Preserve pinned blocks first
        if (!pinnedIds.isEmpty()) {
            for (Swatch w : start) {
                if (pinnedIds.contains(w.id()) && picked.size() < count
                        && picked.stream().noneMatch(p -> p.id().equals(w.id()))) {
                    picked.add(w);
                }
            }
        }
        // Always keep the seed block (including in SHADES and CONTRAST so the user's starting block never vanishes)
        if (picked.stream().noneMatch(p -> p.id().equals(first.id())) && picked.size() < count) {
            picked.add(first);
        }
        float targetRoughness = first.roughness();
        for (double[] target : targets) {
            if (picked.size() >= count) break;
            Swatch w = pickNear(labOf(target), targetRoughness, pool, picked, rng);
            if (w == null && pool != all) w = pickNear(labOf(target), targetRoughness, all, picked, rng);
            if (w != null) picked.add(w);
        }
        if (mode == Mode.BLEND && count >= 2 && picked.stream().noneMatch(w -> w.id().equals(start.getLast().id()))) {
            if (picked.size() >= count) picked.removeLast();
            picked.add(start.getLast());
        }
        if (mode == Mode.SHADES || mode == Mode.CONTRAST) picked.sort(Comparator.comparingDouble(w -> lab(w.rgb())[0]));
        return picked;
    }

    /**
     * The block nearest the target (by OKLab color + gentle roughness match) that is not already picked
     * and not nearly the same color as one that is.
     */
    private static Swatch pickNear(double[] targetLab, float targetRoughness, List<Swatch> pool, List<Swatch> picked, Random rng) {
        Set<String> used = new HashSet<>();
        List<double[]> pickedLabs = new ArrayList<>();
        for (Swatch w : picked) { used.add(w.id()); pickedLabs.add(lab(w.rgb())); }

        List<Swatch> best = new ArrayList<>();
        List<Double> bestD = new ArrayList<>();
        int keep = rng == null ? 1 : REROLL_CHOICES;
        for (Swatch w : pool) {
            if (used.contains(w.id())) continue;
            double[] l = lab(w.rgb());
            boolean tooClose = false;
            for (double[] p : pickedLabs) if (distance(l, p) < MIN_DIFFERENCE) { tooClose = true; break; }
            if (tooClose) continue;
            double d = distance(targetLab, l) + Math.abs(w.roughness() - targetRoughness) * ROUGHNESS_WEIGHT;
            int at = 0;
            while (at < bestD.size() && bestD.get(at) <= d) at++;
            if (at < keep) {
                best.add(at, w);
                bestD.add(at, d);
                if (best.size() > keep) { best.removeLast(); bestD.removeLast(); }
            }
        }
        if (best.isEmpty()) return null;
        if (rng == null) return best.getFirst();
        // Weighted toward the closest: 1, 1/2, 1/3, ...
        double total = 0;
        for (int i = 0; i < best.size(); i++) total += 1.0 / (i + 1);
        double r = rng.nextDouble() * total;
        for (int i = 0; i < best.size(); i++) {
            r -= 1.0 / (i + 1);
            if (r <= 0) return best.get(i);
        }
        return best.getLast();
    }

    /** Blocks of a similar hue and saturation (or all greys, for a grey block). */
    private static List<Swatch> family(Swatch seed, List<Swatch> all) {
        double[] s = lch(lab(seed.rgb()));
        boolean grey = s[1] < GREY_CHROMA;
        List<Swatch> out = new ArrayList<>();
        for (Swatch w : all) {
            double[] c = lch(lab(w.rgb()));
            boolean match = grey ? c[1] < GREY_CHROMA + 1.5 : c[1] >= 2.5 && hueDistance(c[2], s[2]) < 28 && Math.abs(c[1] - s[1]) < 14;
            if (match) out.add(w);
        }
        return out;
    }

    private static double clampL(double l) {
        return Math.max(12, Math.min(95, l));
    }

    // -------------------------------------------------------------------------
    // Color math (sRGB → OKLab, scaled ×100)
    // -------------------------------------------------------------------------

    public static double[] lab(int rgb) {
        double r = linear(((rgb >> 16) & 0xFF) / 255.0);
        double g = linear(((rgb >> 8) & 0xFF) / 255.0);
        double b = linear((rgb & 0xFF) / 255.0);
        double l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b);
        double m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b);
        double s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b);
        return new double[]{
                (0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s) * 100.0,
                (1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s) * 100.0,
                (0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s) * 100.0
        };
    }

    /** Perceptual color distance in OKLab (×100), with chroma weighted so greys stay grey. */
    public static double distance(double[] a, double[] b) {
        double dl = a[0] - b[0];
        double da = (a[1] - b[1]) * 1.5;
        double db = (a[2] - b[2]) * 1.5;
        return Math.sqrt(dl * dl + da * da + db * db);
    }

    /** Lightness, chroma and hue (degrees) of an OKLab color. */
    public static double[] lch(double[] lab) {
        double c = Math.hypot(lab[1], lab[2]);
        double h = Math.toDegrees(Math.atan2(lab[2], lab[1]));
        return new double[]{lab[0], c, h < 0 ? h + 360 : h};
    }

    private static double[] labOf(double[] lch) {
        double h = Math.toRadians(lch[2]);
        return new double[]{lch[0], lch[1] * Math.cos(h), lch[1] * Math.sin(h)};
    }

    private static double hueDistance(double a, double b) {
        double d = Math.abs(a - b) % 360;
        return d > 180 ? 360 - d : d;
    }

    private static double linear(double c) {
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }
}
