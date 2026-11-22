package io.github.watdafawx.shapewright.shape;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.watdafawx.shapewright.Constants;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Color themes (5-block palettes: base, primary, secondary, trim, accent) for {@link FloorDesigner}.
 * Ships with built-in defaults and loads/merges user or modpack themes from
 * {@code config/shapewright/floor_themes.json}.
 */
public final class FloorThemes {

    public static final Path FILE = Path.of("config", Constants.MOD_ID, "floor_themes.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public record Theme(String id, @Nullable String displayName, String[] blocks) {
        public String getNameKey() {
            return "shapewright.floor_designer.theme." + id;
        }
    }

    private static final List<Theme> DEFAULTS = List.of(
            new Theme("pastel_lavender", null, new String[]{
                    "minecraft:smooth_quartz", "minecraft:purple_terracotta", "minecraft:light_gray_terracotta",
                    "minecraft:purple_concrete_powder", "minecraft:sea_lantern"}),
            new Theme("royal_gold", null, new String[]{
                    "minecraft:smooth_sandstone", "minecraft:yellow_terracotta", "minecraft:terracotta",
                    "minecraft:brown_terracotta", "minecraft:glowstone"}),
            new Theme("emerald_oak", null, new String[]{
                    "minecraft:dark_oak_planks", "minecraft:dark_prismarine", "minecraft:birch_planks",
                    "minecraft:spruce_planks", "minecraft:ochre_froglight"}),
            new Theme("mossy_ruins", null, new String[]{
                    "minecraft:mud_bricks", "minecraft:moss_block", "minecraft:oxidized_cut_copper",
                    "minecraft:cobblestone", "minecraft:verdant_froglight"}),
            new Theme("desert_sunburst", null, new String[]{
                    "minecraft:deepslate_tiles", "minecraft:yellow_concrete", "minecraft:orange_terracotta",
                    "minecraft:blackstone", "minecraft:sea_lantern"}),
            new Theme("deepslate_quartz", null, new String[]{
                    "minecraft:polished_deepslate", "minecraft:quartz_block", "minecraft:cyan_terracotta",
                    "minecraft:deepslate_bricks", "minecraft:shroomlight"}),
            new Theme("gothic_cathedral", null, new String[]{
                    "minecraft:stone_bricks", "minecraft:deepslate_bricks", "minecraft:polished_andesite",
                    "minecraft:chiseled_stone_bricks", "minecraft:sea_lantern"}),
            new Theme("tudor_manor", null, new String[]{
                    "minecraft:white_terracotta", "minecraft:dark_oak_log", "minecraft:cobblestone",
                    "minecraft:stripped_spruce_log", "minecraft:ochre_froglight"}),
            new Theme("nether_palace", null, new String[]{
                    "minecraft:polished_blackstone_bricks", "minecraft:crimson_planks", "minecraft:gilded_blackstone",
                    "minecraft:nether_bricks", "minecraft:shroomlight"}),
            new Theme("prismarine_temple", null, new String[]{
                    "minecraft:prismarine_bricks", "minecraft:dark_prismarine", "minecraft:smooth_quartz",
                    "minecraft:oxidized_cut_copper", "minecraft:sea_lantern"})
    );

    private static List<Theme> loaded = null;

    private FloorThemes() {}

    public static List<Theme> get() {
        if (loaded == null) reload();
        return loaded;
    }

    /** Reloads built-in defaults merged with any custom themes in {@code config/shapewright/floor_themes.json}. */
    public static void reload() {
        Map<String, Theme> byId = new LinkedHashMap<>();
        for (Theme t : DEFAULTS) byId.put(t.id(), t);

        if (Files.isRegularFile(FILE)) {
            try {
                JsonArray arr = GSON.fromJson(Files.readString(FILE), JsonArray.class);
                if (arr != null) {
                    for (JsonElement el : arr) {
                        if (!el.isJsonObject()) continue;
                        JsonObject o = el.getAsJsonObject();
                        if (!o.has("id") || !o.has("blocks")) continue;
                        String id = o.get("id").getAsString();
                        String name = o.has("name") ? o.get("name").getAsString() : id;
                        JsonArray bArr = o.getAsJsonArray("blocks");
                        if (bArr.size() < 5) continue;
                        String[] blocks = new String[5];
                        for (int i = 0; i < 5; i++) blocks[i] = bArr.get(i).getAsString();
                        byId.put(id, new Theme(id, name, blocks));
                    }
                }
            } catch (IOException | RuntimeException e) {
                Constants.LOG.warn("[Shapewright] Cannot read custom floor themes from {}", FILE, e);
            }
        }
        loaded = List.copyOf(new ArrayList<>(byId.values()));
    }

    /** Saves a custom 5-block theme to {@code config/shapewright/floor_themes.json} and reloads. */
    public static void saveCustomTheme(String id, String displayName, String[] blocks) throws IOException {
        if (blocks == null || blocks.length < 5) return;
        JsonArray arr = new JsonArray();
        if (Files.isRegularFile(FILE)) {
            try {
                JsonArray existing = GSON.fromJson(Files.readString(FILE), JsonArray.class);
                if (existing != null) {
                    for (JsonElement el : existing) {
                        if (el.isJsonObject() && id.equals(el.getAsJsonObject().get("id").getAsString())) continue;
                        arr.add(el);
                    }
                }
            } catch (IOException | RuntimeException ignored) {}
        }
        JsonObject obj = new JsonObject();
        obj.addProperty("id", id);
        obj.addProperty("name", displayName);
        JsonArray bArr = new JsonArray();
        for (int i = 0; i < 5; i++) bArr.add(blocks[i]);
        obj.add("blocks", bArr);
        arr.add(obj);
        Files.createDirectories(FILE.getParent());
        Files.writeString(FILE, GSON.toJson(arr));
        reload();
    }
}
