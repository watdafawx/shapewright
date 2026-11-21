package io.github.watdafawx.shapewright.palette;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.IceBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.state.BlockState;
import io.github.watdafawx.shapewright.Constants;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Average texture color of every full-cube block, for palette suggestions and previews. Client-side.
 * <p>
 * Scanning reads each block's texture once, in the background, and saves the result to
 * {@code config/shapewright-block-colors.json}. The file is reused until the resource packs
 * or the set of blocks change.
 */
public final class BlockColorCache {

    private static final Path FILE = Path.of("config", Constants.MOD_ID + "-block-colors.json");
    private static final Gson GSON = new Gson();

    private static volatile Map<Item, Integer> colors = Map.of();
    /** Texture roughness (luminance std-dev in 0..1) per block, for texture-matched suggestions. */
    private static volatile Map<Item, Float> roughness = Map.of();
    /** Texture each color came from; blocks sharing a texture are copies of each other. */
    private static volatile Map<Item, ResourceLocation> textures = Map.of();
    private static Map<Item, Integer> distinctFor = null;
    private static Map<Item, Integer> distinct = Map.of();
    private static volatile boolean scanning = false;
    private static boolean loaded = false;

    private BlockColorCache() {}

    /** Item → 0xRRGGBB. Empty until loaded or scanned. */
    public static Map<Item, Integer> colors() {
        return colors;
    }

    /**
     * Like {@link #colors()} with copies collapsed: of the blocks sharing a texture (the same block
     * registered again by other mods), only one is kept, preferring vanilla, then the shortest id.
     */
    public static synchronized Map<Item, Integer> distinctColors() {
        Map<Item, Integer> all = colors;
        if (all != distinctFor) {
            Map<ResourceLocation, Item> byTexture = new HashMap<>();
            Map<Item, ResourceLocation> tex = textures;
            for (Item item : all.keySet()) {
                ResourceLocation t = tex.get(item);
                if (t != null) byTexture.merge(t, item, BlockColorCache::preferred);
            }
            Map<Item, Integer> out = new LinkedHashMap<>();
            for (var e : all.entrySet()) {
                ResourceLocation t = tex.get(e.getKey());
                if (t == null || byTexture.get(t) == e.getKey()) out.put(e.getKey(), e.getValue());
            }
            distinct = Collections.unmodifiableMap(out);
            distinctFor = all;
        }
        return distinct;
    }

    private static Item preferred(Item a, Item b) {
        ResourceLocation ia = BuiltInRegistries.ITEM.getKey(a), ib = BuiltInRegistries.ITEM.getKey(b);
        boolean va = ia.getNamespace().equals("minecraft"), vb = ib.getNamespace().equals("minecraft");
        if (va != vb) return va ? a : b;
        return ia.getPath().length() <= ib.getPath().length() ? a : b;
    }

    public static boolean isScanning() {
        return scanning;
    }

    /** Color for a block item, falling back to grey when it was not scanned. */
    public static int colorOf(Item item) {
        return colors.getOrDefault(item, 0x808080);
    }

    /** Texture roughness (0 = flat, ~0.4 = very noisy), falling back to 0.15 when unknown. */
    public static float roughnessOf(Item item) {
        return roughness.getOrDefault(item, 0.15f);
    }

    /**
     * Filters out blocks that make poor building-palette suggestions (ores, falling blocks,
     * TNT, meltable ice, unobtainable/creative blocks) while leaving them searchable in the grid.
     */
    public static boolean isSuggestible(Item item) {
        if (!(item instanceof BlockItem bi)) return false;
        Block block = bi.getBlock();
        if (block instanceof FallingBlock || block instanceof TntBlock || block instanceof IceBlock) return false;
        String path = BuiltInRegistries.ITEM.getKey(item).getPath();
        if (path.endsWith("_ore") || path.contains("_ore_") || path.startsWith("ore_")) return false;
        if (path.contains("command_block") || path.contains("structure_") || path.contains("jigsaw")) return false;
        if (path.equals("barrier") || path.equals("bedrock") || path.contains("spawner")
                || path.contains("budding_") || path.contains("reinforced_deepslate")) return false;
        return true;
    }

    /** Loads the saved colors if they match the current packs, otherwise scans. Call on the render thread. */
    public static void ensureReady() {
        if (loaded || scanning) return;
        loaded = true;
        if (!loadFromFile()) scan();
    }

    /** Scans every block again (e.g. after changing resource packs). Call on the render thread. */
    public static void scan() {
        if (scanning) return;
        Minecraft mc = Minecraft.getInstance();
        // Model and tint lookups on the render thread; texture reading in the background
        record Job(Item item, ResourceLocation texture, ResourceLocation sprite, int tint) {}
        List<Job> jobs = new ArrayList<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            Item item = block.asItem();
            if (!(item instanceof BlockItem)) continue;
            // Infested blocks look like stone but are not obtainable in survival
            if (BuiltInRegistries.ITEM.getKey(item).getPath().contains("infested")) continue;
            BlockState state = block.defaultBlockState();
            try {
                if (state.getRenderShape() != RenderShape.MODEL || state.hasBlockEntity()) continue;
                if (!state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) continue;
                var sprite = mc.getBlockRenderer().getBlockModel(state).getParticleIcon();
                ResourceLocation name = sprite.contents().name();
                if (name.getPath().contains("missingno")) continue;
                int tint = -1;
                try {
                    tint = mc.getBlockColors().getColor(state, null, null, 0);
                } catch (RuntimeException ignored) {
                    // Some tints need a level; use the plain texture
                }
                jobs.add(new Job(item, name.withPath(p -> "textures/" + p + ".png"), name, tint));
            } catch (RuntimeException e) {
                // A block with an unusual model: leave it out
            }
        }
        scanning = true;
        String key = packsKey();
        Map<Item, ResourceLocation> sprites = new HashMap<>();
        Map<Item, Float> roughMap = new HashMap<>();
        CompletableFuture.supplyAsync(() -> {
            Map<Item, Integer> result = new LinkedHashMap<>();
            Map<ResourceLocation, Optional<long[]>> byTexture = new HashMap<>(); // shared textures are read once
            for (Job job : jobs) {
                long[] stats = byTexture.computeIfAbsent(job.texture(), t -> Optional.ofNullable(textureStats(mc, t))).orElse(null);
                if (stats == null) continue;
                int rgb = (int) stats[0];
                float rgh = Float.intBitsToFloat((int) stats[1]);
                result.put(job.item(), job.tint() == -1 ? rgb : multiply(rgb, job.tint()));
                roughMap.put(job.item(), rgh);
                sprites.put(job.item(), job.sprite());
            }
            return result;
        }, Util.backgroundExecutor()).whenComplete((result, error) -> {
            scanning = false;
            if (error != null) {
                Constants.LOG.warn("[Shapewright] Block color scan failed", error);
                return;
            }
            textures = Collections.unmodifiableMap(sprites);
            roughness = Collections.unmodifiableMap(roughMap);
            colors = Collections.unmodifiableMap(result);
            saveToFile(result, roughMap, sprites, key);
            Constants.LOG.info("[Shapewright] Scanned colors of {} blocks", result.size());
        });
    }

    /** Alpha-weighted average RGB + luminance std-dev (roughness) of the texture's first frame, or null if unreadable/mostly transparent. */
    private static long[] textureStats(Minecraft mc, ResourceLocation texture) {
        Optional<Resource> resource = mc.getResourceManager().getResource(texture);
        if (resource.isEmpty()) return null;
        try (InputStream in = resource.get().open(); NativeImage image = NativeImage.read(in)) {
            int size = Math.min(image.getWidth(), image.getHeight()); // animated textures stack frames vertically
            long r = 0, g = 0, b = 0, weight = 0;
            double lumSum = 0, lumSqSum = 0;
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    int abgr = image.getPixelRGBA(x, y);
                    int a = (abgr >>> 24) & 0xFF;
                    if (a < 128) continue;
                    int pr = abgr & 0xFF;
                    int pg = (abgr >> 8) & 0xFF;
                    int pb = (abgr >> 16) & 0xFF;
                    r += pr;
                    g += pg;
                    b += pb;
                    double lum = (0.2126 * pr + 0.7152 * pg + 0.0722 * pb) / 255.0;
                    lumSum += lum;
                    lumSqSum += lum * lum;
                    weight++;
                }
            }
            if (weight < size * size / 4) return null;
            int rgb = (int) (r / weight) << 16 | (int) (g / weight) << 8 | (int) (b / weight);
            double mean = lumSum / weight;
            float stdDev = (float) Math.sqrt(Math.max(0.0, lumSqSum / weight - mean * mean));
            return new long[]{rgb, Float.floatToRawIntBits(stdDev)};
        } catch (Exception e) {
            return null;
        }
    }

    private static int multiply(int rgb, int tint) {
        int r = ((rgb >> 16) & 0xFF) * ((tint >> 16) & 0xFF) / 255;
        int g = ((rgb >> 8) & 0xFF) * ((tint >> 8) & 0xFF) / 255;
        int b = (rgb & 0xFF) * (tint & 0xFF) / 255;
        return r << 16 | g << 8 | b;
    }

    /** Identifies the resource packs and blocks the saved colors belong to. */
    private static String packsKey() {
        Minecraft mc = Minecraft.getInstance();
        return "v3|" + String.join(",", mc.getResourcePackRepository().getSelectedIds()) + "|" + BuiltInRegistries.BLOCK.size();
    }

    private static boolean loadFromFile() {
        if (!Files.isRegularFile(FILE)) return false;
        try {
            JsonObject root = GSON.fromJson(Files.readString(FILE), JsonObject.class);
            if (!packsKey().equals(root.get("packs").getAsString())) return false;
            Map<Item, Integer> result = new LinkedHashMap<>();
            Map<Item, Float> roughMap = new HashMap<>();
            Map<Item, ResourceLocation> sprites = new HashMap<>();
            for (var e : root.getAsJsonObject("colors").entrySet()) {
                ResourceLocation id = ResourceLocation.tryParse(e.getKey());
                if (id == null) continue;
                String[] value = e.getValue().getAsString().split(" ", 3); // "rrggbb rough texture"
                BuiltInRegistries.ITEM.getOptional(id).ifPresent(item -> {
                    result.put(item, Integer.parseInt(value[0], 16));
                    if (value.length > 1) {
                        try { roughMap.put(item, Float.parseFloat(value[1])); } catch (NumberFormatException ignored) {}
                    }
                    if (value.length > 2) {
                        ResourceLocation t = ResourceLocation.tryParse(value[2]);
                        if (t != null) sprites.put(item, t);
                    }
                });
            }
            textures = Collections.unmodifiableMap(sprites);
            roughness = Collections.unmodifiableMap(roughMap);
            colors = Collections.unmodifiableMap(result);
            return !result.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    private static void saveToFile(Map<Item, Integer> result, Map<Item, Float> roughMap, Map<Item, ResourceLocation> sprites, String key) {
        JsonObject root = new JsonObject();
        root.addProperty("packs", key);
        JsonObject map = new JsonObject();
        result.forEach((item, rgb) -> map.addProperty(BuiltInRegistries.ITEM.getKey(item).toString(),
                String.format(Locale.ROOT, "%06x %.3f %s", rgb, roughMap.getOrDefault(item, 0.15f), sprites.get(item))));
        root.add("colors", map);
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(root));
        } catch (Exception e) {
            Constants.LOG.warn("[Shapewright] Cannot save block colors to {}", FILE, e);
        }
    }
}
