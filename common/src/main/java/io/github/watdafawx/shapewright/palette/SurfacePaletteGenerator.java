package io.github.watdafawx.shapewright.palette;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Generates harmonious 5-slot architectural palettes ({@code [base, primary, secondary, trim, accent]})
 * either completely at random or matched to any locked anchor slots (e.g. Base and Primary, or any subset).
 */
public final class SurfacePaletteGenerator {

    private static final String[] LIGHT_BLOCKS = {
            "minecraft:sea_lantern",
            "minecraft:glowstone",
            "minecraft:shroomlight",
            "minecraft:ochre_froglight",
            "minecraft:verdant_froglight",
            "minecraft:pearlescent_froglight"
    };

    private static final String[] EXCLUDE_FRAGMENTS = {
            "tnt", "spawner", "command", "barrier", "bedrock", "sponge", "slime", "honey",
            "magma", "ice", "leaves", "ore", "coral", "egg", "cake", "redstone", "piston",
            "observer", "dropper", "dispenser", "furnace", "smoker", "blast", "crafting",
            "jukebox", "note_block", "melon", "pumpkin", "hay", "target", "sculk", "mushroom",
            "bee", "hive", "composter", "barrel", "bookshelf", "lectern", "loom", "cartography",
            "fletching", "smithing", "chiseled_bookshelf", "crafter", "vault", "trial",
            "shulker", "glazed", "powder", "sand", "gravel", "dirt", "grass", "podzol",
            "mycelium", "farmland", "path", "snow", "clay", "soul_", "netherrack", "end_stone",
            "raw_", "ancient_debris", "netherite", "diamond_block", "emerald_block", "lapis_block",
            "gold_block", "iron_block", "coal_block", "dried_kelp", "bone_block", "wart_block"
    };

    private record Entry(String id, float h, float s, float l) {}

    private SurfacePaletteGenerator() {}

    /**
     * Generates a 5-slot palette. Any slot with {@code locked[i] == true} is preserved verbatim;
     * unlocked slots are chosen to harmonize with the locked anchors (or a random anchor if none locked).
     */
    public static String[] generate(String[] current, boolean[] locked, Random rng) {
        List<Entry> pool = buildPool();
        String[] out = current.clone();
        if (pool.isEmpty()) return out;

        Set<String> used = new HashSet<>();
        List<Entry> anchors = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            if (locked != null && i < locked.length && locked[i] && current[i] != null && !current[i].isEmpty()) {
                used.add(current[i]);
                anchors.add(entryOf(current[i]));
            }
        }

        // Determine primary anchor hue/lightness
        Entry anchorA;
        Entry anchorB = null;
        if (anchors.isEmpty()) {
            anchorA = pool.get(rng.nextInt(pool.size()));
        } else {
            anchorA = anchors.getFirst();
            if (anchors.size() >= 2) anchorB = anchors.get(1);
        }

        // Pick a color harmony offset
        float[] schemes = {28f, -28f, 180f, 150f, -150f, 120f, 0f};
        float harmonyShift = schemes[rng.nextInt(schemes.length)];
        float baseH = anchorA.h();
        float baseL = anchorA.l();
        float primaryH = anchorB != null ? anchorB.h() : wrapHue(baseH + harmonyShift);
        float primaryS = anchorB != null ? anchorB.s() : Math.max(0.28f, Math.min(0.75f, anchorA.s() + 0.25f));
        float primaryL = anchorB != null ? anchorB.l() : (baseL > 0.55f ? 0.42f : 0.62f);

        // Slot 0: Base (calm foundation)
        if (!isLocked(locked, 0)) {
            float targetL = primaryL > 0.52f ? 0.28f + rng.nextFloat() * 0.14f : 0.65f + rng.nextFloat() * 0.18f;
            float targetS = Math.min(0.30f, anchorA.s() * 0.6f);
            out[0] = pickClosest(pool, used, baseH, targetS, targetL, rng);
        }

        // Slot 1: Primary (bold pattern color)
        if (!isLocked(locked, 1)) {
            out[1] = pickClosest(pool, used, primaryH, primaryS, primaryL, rng);
        }

        // Slot 2: Secondary (supporting mid-tone between Base and Primary)
        if (!isLocked(locked, 2)) {
            float secH = lerpHue(entryOf(out[0]).h(), entryOf(out[1]).h(), 0.5f);
            float secS = (entryOf(out[0]).s() + entryOf(out[1]).s()) * 0.5f;
            float secL = (entryOf(out[0]).l() + entryOf(out[1]).l()) * 0.5f;
            out[2] = pickClosest(pool, used, secH, secS, secL, rng);
        }

        // Slot 3: Trim (high-contrast architectural border/frame)
        if (!isLocked(locked, 3)) {
            float avgL = (entryOf(out[0]).l() + entryOf(out[1]).l()) * 0.5f;
            float trimL = avgL > 0.48f ? 0.18f + rng.nextFloat() * 0.12f : 0.78f + rng.nextFloat() * 0.12f;
            float trimS = Math.min(0.35f, entryOf(out[1]).s() * 0.5f);
            out[3] = pickClosest(pool, used, primaryH, trimS, trimL, rng);
        }

        // Slot 4: Accent (emissive light block matching the palette, or bright focal jewel)
        if (!isLocked(locked, 4)) {
            if (rng.nextFloat() < 0.75f) {
                List<Entry> lights = new ArrayList<>();
                for (String id : LIGHT_BLOCKS) {
                    if (!used.contains(id)) lights.add(entryOf(id));
                }
                if (!lights.isEmpty()) {
                    out[4] = pickClosest(lights, used, primaryH, 0.55f, 0.78f, rng);
                } else {
                    out[4] = pickClosest(pool, used, wrapHue(primaryH + 180f), 0.75f, 0.72f, rng);
                }
            } else {
                out[4] = pickClosest(pool, used, wrapHue(primaryH + 180f), 0.72f, 0.72f, rng);
            }
        }

        return out;
    }

    private static boolean isLocked(boolean[] locked, int i) {
        return locked != null && i < locked.length && locked[i];
    }

    private static List<Entry> buildPool() {
        List<Entry> list = new ArrayList<>();
        Map<Item, Integer> map = BlockColorCache.distinctColors();
        for (var e : map.entrySet()) {
            ResourceLocation loc = BuiltInRegistries.ITEM.getKey(e.getKey());
            if (loc == null) continue;
            String path = loc.getPath();
            if (isExcluded(path)) continue;
            float[] hsl = rgbToHsl(e.getValue());
            list.add(new Entry(loc.toString(), hsl[0], hsl[1], hsl[2]));
        }
        return list;
    }

    private static boolean isExcluded(String path) {
        for (String bad : EXCLUDE_FRAGMENTS) {
            if (path.contains(bad)) return true;
        }
        return false;
    }

    private static Entry entryOf(String id) {
        ResourceLocation loc = id == null ? null : ResourceLocation.tryParse(id);
        Item item = loc == null ? null : BuiltInRegistries.ITEM.get(loc);
        int rgb = item != null ? BlockColorCache.colorOf(item) : 0x808080;
        float[] hsl = rgbToHsl(rgb);
        return new Entry(id != null ? id : "minecraft:stone", hsl[0], hsl[1], hsl[2]);
    }

    private static String pickClosest(List<Entry> pool, Set<String> used, float th, float ts, float tl, Random rng) {
        List<Entry> candidates = new ArrayList<>();
        for (Entry e : pool) {
            if (!used.contains(e.id())) candidates.add(e);
        }
        if (candidates.isEmpty()) candidates.addAll(pool);
        candidates.sort(Comparator.comparingDouble(e -> colorDist(e, th, ts, tl)));
        int topK = Math.min(5, candidates.size());
        Entry chosen = candidates.get(rng.nextInt(topK));
        used.add(chosen.id());
        return chosen.id();
    }

    private static double colorDist(Entry e, float th, float ts, float tl) {
        float dh = Math.abs(e.h() - th);
        if (dh > 180f) dh = 360f - dh;
        double hNorm = (dh / 180.0) * Math.max(0.2, ts);
        double ds = e.s() - ts;
        double dl = (e.l() - tl) * 1.4;
        return hNorm * hNorm + ds * ds + dl * dl;
    }

    private static float wrapHue(float h) {
        float r = h % 360f;
        return r < 0 ? r + 360f : r;
    }

    private static float lerpHue(float a, float b, float t) {
        float diff = b - a;
        if (diff > 180f) diff -= 360f;
        if (diff < -180f) diff += 360f;
        return wrapHue(a + diff * t);
    }

    private static float[] rgbToHsl(int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255f;
        float g = ((rgb >> 8) & 0xFF) / 255f;
        float b = (rgb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float l = (max + min) * 0.5f;
        float s, h;
        if (max == min) {
            h = 0f;
            s = 0f;
        } else {
            float d = max - min;
            s = l > 0.5f ? d / (2f - max - min) : d / (max + min);
            if (max == r) h = ((g - b) / d + (g < b ? 6f : 0f)) * 60f;
            else if (max == g) h = ((b - r) / d + 2f) * 60f;
            else h = ((r - g) / d + 4f) * 60f;
        }
        return new float[]{h, s, l};
    }
}
