package ru.nelande.minelark.pack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Builds the client resource-pack JSON (item/block models and blockstates) for scripted content.
 *
 * <p>Deliberately free of any Minecraft types - just string/Gson work - so it is unit-testable
 * without launching the game (mirrors {@code RecipeSpec.json()} / the datapack JSON). Everything is
 * emitted under the {@code minelark:} namespace; textures reference {@code minelark:item/<id>} and
 * {@code minelark:block/<id>}, which pack authors supply (or override) via the {@code minelark/assets}
 * drop-in folder. The shape blockstate/model shapes mirror the vanilla templates exactly.
 */
public final class AssetJson {
    // HTML escaping off so the '=' in blockstate variant keys stays literal (valid + readable).
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final String NS = "minelark:";

    private AssetJson() {
    }

    /** An inventory model for a plain item. {@code handheld} tools tilt in first person. */
    public static String itemModel(String id, boolean handheld) {
        JsonObject model = new JsonObject();
        model.addProperty("parent", handheld ? "minecraft:item/handheld" : "minecraft:item/generated");
        JsonObject textures = new JsonObject();
        textures.addProperty("layer0", NS + "item/" + id);
        model.add("textures", textures);
        return GSON.toJson(model);
    }

    /** An item model for a block/shape: inherits an existing block model (e.g. the inventory model). */
    public static String parentedItemModel(String parentBlockModel) {
        JsonObject model = new JsonObject();
        model.addProperty("parent", NS + "block/" + parentBlockModel);
        return GSON.toJson(model);
    }

    /** A full-cube block model textured on every face with {@code minelark:block/<id>}. */
    public static String cubeAllBlockModel(String id) {
        return texturedModel("minecraft:block/cube_all", id, "all");
    }

    /** The blockstate for a plain full block: one variant pointing at the block model. */
    public static String simpleBlockstate(String id) {
        JsonObject variants = new JsonObject();
        variants.add("", modelRef(NS + "block/" + id));
        return wrapVariants(variants);
    }

    // --- slab ---

    public static String slabModel(String id) {
        return texturedModel("minecraft:block/slab", id, "bottom", "top", "side");
    }

    public static String slabTopModel(String id) {
        return texturedModel("minecraft:block/slab_top", id, "bottom", "top", "side");
    }

    public static String slabDoubleModel(String id) {
        return cubeAllBlockModel(id);
    }

    /** Blockstate for a slab: bottom / top / double (the full cube). */
    public static String slabBlockstate(String id) {
        JsonObject variants = new JsonObject();
        variants.add("type=bottom", modelRef(NS + "block/" + id));
        variants.add("type=top", modelRef(NS + "block/" + id + "_top"));
        variants.add("type=double", modelRef(NS + "block/" + id + "_double"));
        return wrapVariants(variants);
    }

    // --- stairs ---

    public static String stairsModel(String id) {
        return texturedModel("minecraft:block/stairs", id, "bottom", "top", "side");
    }

    public static String stairsInnerModel(String id) {
        return texturedModel("minecraft:block/inner_stairs", id, "bottom", "top", "side");
    }

    public static String stairsOuterModel(String id) {
        return texturedModel("minecraft:block/outer_stairs", id, "bottom", "top", "side");
    }

    // The full vanilla facing/half/shape rotation matrix, transcribed from oak_stairs.json.
    // Fields: facing, half, shape, model-suffix ("" straight / _inner / _outer), x, y (0 = omit).
    private static final String[][] STAIRS = {
            {"east", "bottom", "inner_left", "_inner", "0", "270"},
            {"east", "bottom", "inner_right", "_inner", "0", "0"},
            {"east", "bottom", "outer_left", "_outer", "0", "270"},
            {"east", "bottom", "outer_right", "_outer", "0", "0"},
            {"east", "bottom", "straight", "", "0", "0"},
            {"east", "top", "inner_left", "_inner", "180", "0"},
            {"east", "top", "inner_right", "_inner", "180", "90"},
            {"east", "top", "outer_left", "_outer", "180", "0"},
            {"east", "top", "outer_right", "_outer", "180", "90"},
            {"east", "top", "straight", "", "180", "0"},
            {"north", "bottom", "inner_left", "_inner", "0", "180"},
            {"north", "bottom", "inner_right", "_inner", "0", "270"},
            {"north", "bottom", "outer_left", "_outer", "0", "180"},
            {"north", "bottom", "outer_right", "_outer", "0", "270"},
            {"north", "bottom", "straight", "", "0", "270"},
            {"north", "top", "inner_left", "_inner", "180", "270"},
            {"north", "top", "inner_right", "_inner", "180", "0"},
            {"north", "top", "outer_left", "_outer", "180", "270"},
            {"north", "top", "outer_right", "_outer", "180", "0"},
            {"north", "top", "straight", "", "180", "270"},
            {"south", "bottom", "inner_left", "_inner", "0", "0"},
            {"south", "bottom", "inner_right", "_inner", "0", "90"},
            {"south", "bottom", "outer_left", "_outer", "0", "0"},
            {"south", "bottom", "outer_right", "_outer", "0", "90"},
            {"south", "bottom", "straight", "", "0", "90"},
            {"south", "top", "inner_left", "_inner", "180", "90"},
            {"south", "top", "inner_right", "_inner", "180", "180"},
            {"south", "top", "outer_left", "_outer", "180", "90"},
            {"south", "top", "outer_right", "_outer", "180", "180"},
            {"south", "top", "straight", "", "180", "90"},
            {"west", "bottom", "inner_left", "_inner", "0", "90"},
            {"west", "bottom", "inner_right", "_inner", "0", "180"},
            {"west", "bottom", "outer_left", "_outer", "0", "90"},
            {"west", "bottom", "outer_right", "_outer", "0", "180"},
            {"west", "bottom", "straight", "", "0", "180"},
            {"west", "top", "inner_left", "_inner", "180", "180"},
            {"west", "top", "inner_right", "_inner", "180", "270"},
            {"west", "top", "outer_left", "_outer", "180", "180"},
            {"west", "top", "outer_right", "_outer", "180", "270"},
            {"west", "top", "straight", "", "180", "180"},
    };

    public static String stairsBlockstate(String id) {
        JsonObject variants = new JsonObject();
        for (String[] row : STAIRS) {
            String key = "facing=" + row[0] + ",half=" + row[1] + ",shape=" + row[2];
            int x = Integer.parseInt(row[4]);
            int y = Integer.parseInt(row[5]);
            JsonObject v = new JsonObject();
            v.addProperty("model", NS + "block/" + id + row[3]);
            if (x != 0 || y != 0) {
                v.addProperty("uvlock", true);
            }
            if (x != 0) {
                v.addProperty("x", x);
            }
            if (y != 0) {
                v.addProperty("y", y);
            }
            variants.add(key, v);
        }
        return wrapVariants(variants);
    }

    // --- fence ---

    public static String fencePostModel(String id) {
        return texturedModel("minecraft:block/fence_post", id, "texture");
    }

    public static String fenceSideModel(String id) {
        return texturedModel("minecraft:block/fence_side", id, "texture");
    }

    public static String fenceInventoryModel(String id) {
        return texturedModel("minecraft:block/fence_inventory", id, "texture");
    }

    /** Multipart blockstate for a fence: an always-on post plus a side per connected direction. */
    public static String fenceBlockstate(String id) {
        String post = NS + "block/" + id + "_post";
        String sideModel = NS + "block/" + id + "_side";
        JsonArray multipart = new JsonArray();
        multipart.add(part(null, apply(post, 0, false)));
        String[] dirs = {"north", "east", "south", "west"};
        int[] rot = {0, 90, 180, 270};
        for (int i = 0; i < 4; i++) {
            multipart.add(part(when(dirs[i], "true"), apply(sideModel, rot[i], true)));
        }
        return wrapMultipart(multipart);
    }

    // --- wall ---

    public static String wallPostModel(String id) {
        return texturedModel("minecraft:block/template_wall_post", id, "wall");
    }

    public static String wallSideModel(String id) {
        return texturedModel("minecraft:block/template_wall_side", id, "wall");
    }

    public static String wallSideTallModel(String id) {
        return texturedModel("minecraft:block/template_wall_side_tall", id, "wall");
    }

    public static String wallInventoryModel(String id) {
        return texturedModel("minecraft:block/wall_inventory", id, "wall");
    }

    /** Multipart blockstate for a wall: optional post plus low/tall side per direction. */
    public static String wallBlockstate(String id) {
        String post = NS + "block/" + id + "_post";
        String side = NS + "block/" + id + "_side";
        String sideTall = NS + "block/" + id + "_side_tall";
        JsonArray multipart = new JsonArray();
        multipart.add(part(when("up", "true"), apply(post, 0, false)));
        String[] dirs = {"north", "east", "south", "west"};
        int[] rot = {0, 90, 180, 270};
        for (int i = 0; i < 4; i++) {
            multipart.add(part(when(dirs[i], "low"), apply(side, rot[i], true)));
        }
        for (int i = 0; i < 4; i++) {
            multipart.add(part(when(dirs[i], "tall"), apply(sideTall, rot[i], true)));
        }
        return wrapMultipart(multipart);
    }

    // --- small shared builders ---

    /** A model derived from a vanilla parent, texturing the given slots all with {@code block/<id>}. */
    private static String texturedModel(String parent, String id, String... slots) {
        JsonObject model = new JsonObject();
        model.addProperty("parent", parent);
        JsonObject textures = new JsonObject();
        for (String slot : slots) {
            textures.addProperty(slot, NS + "block/" + id);
        }
        model.add("textures", textures);
        return GSON.toJson(model);
    }

    private static JsonObject modelRef(String model) {
        JsonObject v = new JsonObject();
        v.addProperty("model", model);
        return v;
    }

    private static JsonObject apply(String model, int y, boolean uvlock) {
        JsonObject v = new JsonObject();
        v.addProperty("model", model);
        if (uvlock) {
            v.addProperty("uvlock", true);
        }
        if (y != 0) {
            v.addProperty("y", y);
        }
        return v;
    }

    private static JsonObject when(String property, String value) {
        JsonObject when = new JsonObject();
        when.addProperty(property, value);
        return when;
    }

    private static JsonObject part(JsonObject when, JsonObject apply) {
        JsonObject part = new JsonObject();
        part.add("apply", apply);
        if (when != null) {
            part.add("when", when);
        }
        return part;
    }

    private static String wrapVariants(JsonObject variants) {
        JsonObject root = new JsonObject();
        root.add("variants", variants);
        return GSON.toJson(root);
    }

    private static String wrapMultipart(JsonArray multipart) {
        JsonObject root = new JsonObject();
        root.add("multipart", multipart);
        return GSON.toJson(root);
    }
}
