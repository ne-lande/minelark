package ru.nelande.minelark.pack;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The known block-shape asset generators, keyed by shape name. The built-in slab/stairs/fence/wall
 * shapes are always present (registered in a static block, delegating to {@link AssetJson}); addon
 * mods add their own through {@code MinelarkTypes.shape(...)}, which forwards the MC-free asset half
 * here. Free of Minecraft types so it stays unit-testable and available before the game loads.
 */
public final class ShapeAssetRegistry {
    private static final Map<String, ShapeAssets> SHAPES = new ConcurrentHashMap<>();

    static {
        registerBuiltins();
    }

    private ShapeAssetRegistry() {
    }

    /** Registers (or replaces) the assets for a shape name. */
    public static void register(String name, ShapeAssets assets) {
        SHAPES.put(name, assets);
    }

    /** The assets for a shape, or {@code null} if the name is unknown. */
    public static ShapeAssets get(String name) {
        return SHAPES.get(name);
    }

    /** Whether a shape with this name is registered. */
    public static boolean has(String name) {
        return SHAPES.containsKey(name);
    }

    /** Every registered shape name. */
    public static Set<String> names() {
        return Set.copyOf(SHAPES.keySet());
    }

    private static void registerBuiltins() {
        register("slab", new ShapeAssets() {
            @Override public String blockstate(String id) { return AssetJson.slabBlockstate(id); }
            @Override public Map<String, String> blockModels(String id) {
                Map<String, String> m = new LinkedHashMap<>();
                m.put(id, AssetJson.slabModel(id));
                m.put(id + "_top", AssetJson.slabTopModel(id));
                m.put(id + "_double", AssetJson.slabDoubleModel(id));
                return m;
            }
            @Override public String itemParentModel(String id) { return id; }
            @Override public String selfDropLootFunctions(String id) {
                return "\"functions\":[{\"function\":\"minecraft:set_count\",\"count\":2,"
                        + "\"conditions\":[{\"condition\":\"minecraft:block_state_property\","
                        + "\"block\":\"minelark:" + id + "\","
                        + "\"properties\":{\"type\":\"double\"}}]}],";
            }
        });

        register("stairs", new ShapeAssets() {
            @Override public String blockstate(String id) { return AssetJson.stairsBlockstate(id); }
            @Override public Map<String, String> blockModels(String id) {
                Map<String, String> m = new LinkedHashMap<>();
                m.put(id, AssetJson.stairsModel(id));
                m.put(id + "_inner", AssetJson.stairsInnerModel(id));
                m.put(id + "_outer", AssetJson.stairsOuterModel(id));
                m.put(id + "_inventory", AssetJson.stairsModel(id));
                return m;
            }
            @Override public String itemParentModel(String id) { return id + "_inventory"; }
        });

        register("fence", new ShapeAssets() {
            @Override public String blockstate(String id) { return AssetJson.fenceBlockstate(id); }
            @Override public Map<String, String> blockModels(String id) {
                Map<String, String> m = new LinkedHashMap<>();
                m.put(id + "_post", AssetJson.fencePostModel(id));
                m.put(id + "_side", AssetJson.fenceSideModel(id));
                m.put(id + "_inventory", AssetJson.fenceInventoryModel(id));
                return m;
            }
            @Override public String itemParentModel(String id) { return id + "_inventory"; }
        });

        register("wall", new ShapeAssets() {
            @Override public String blockstate(String id) { return AssetJson.wallBlockstate(id); }
            @Override public Map<String, String> blockModels(String id) {
                Map<String, String> m = new LinkedHashMap<>();
                m.put(id + "_post", AssetJson.wallPostModel(id));
                m.put(id + "_side", AssetJson.wallSideModel(id));
                m.put(id + "_side_tall", AssetJson.wallSideTallModel(id));
                m.put(id + "_inventory", AssetJson.wallInventoryModel(id));
                return m;
            }
            @Override public String itemParentModel(String id) { return id + "_inventory"; }
        });
    }
}
