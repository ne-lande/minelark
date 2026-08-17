package ru.nelande.minelark.pack;

import net.minecraft.resource.DirectoryResourcePack;
import net.minecraft.resource.ResourcePack;
import net.minecraft.resource.ResourcePackInfo;
import net.minecraft.resource.ResourcePackPosition;
import net.minecraft.resource.ResourcePackProfile;
import net.minecraft.resource.ResourcePackSource;
import net.minecraft.resource.ResourceType;
import net.minecraft.text.Text;
import ru.nelande.minelark.script.BlockSpec;
import ru.nelande.minelark.script.DatapackJsonSpec;
import ru.nelande.minelark.script.EntityDropSpec;
import ru.nelande.minelark.script.ItemSpec;
import ru.nelande.minelark.script.RecipeSpec;
import ru.nelande.minelark.script.TagSpec;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Builds and serves Minelark's runtime-generated data pack. Minecraft has no runtime API for adding
 * tags (or recipes/loot), and Fabric only loads jar-bundled packs - so script-declared data is
 * written to a folder on disk and exposed to the server's datapack manager as a
 * {@link DirectoryResourcePack} (injected via {@code ResourcePackManagerMixin}).
 *
 * <p>Currently generates item/block tags. Recipes and loot will write into the same pack later.
 */
public final class GeneratedDataPack {
    private static final String MOD_ID = "minelark";
    // Data pack format for Minecraft 1.21.1.
    private static final int PACK_FORMAT = 48;
    private static final String PACK_MCMETA =
            "{\"pack\":{\"description\":\"Minelark generated data\",\"pack_format\":" + PACK_FORMAT + "}}";

    /** Set once the pack has been generated (null if there is nothing to serve). */
    private static volatile Path packDir;

    private GeneratedDataPack() {
    }

    /**
     * (Re)generates the data pack from the declared content. Call before the world's datapacks load.
     * If nothing needs generating, no pack is produced.
     */
    public static void generate(Path gameDir, List<ItemSpec> items, List<BlockSpec> blocks,
                                List<RecipeSpec> recipes, List<TagSpec> extraTags,
                                List<EntityDropSpec> entityDrops, List<DatapackJsonSpec> datapackJson) {
        Path dir = gameDir.resolve(MOD_ID).resolve(".generated").resolve("datapack");
        deleteRecursively(dir);

        // Tags per registry kind: those derived from item()/block() specs, merged with explicit tags.
        Map<String, List<String>> itemTags = collectTags(items, ItemSpec::id, ItemSpec::tags);
        Map<String, List<String>> blockTags = collectTags(blocks, BlockSpec::id, BlockSpec::tags);
        Map<String, List<String>> fluidTags = new LinkedHashMap<>();
        Map<String, List<String>> entityTags = new LinkedHashMap<>();
        for (TagSpec tag : extraTags) {
            Map<String, List<String>> target = switch (tag.kind()) {
                case "item" -> itemTags;
                case "block" -> blockTags;
                case "fluid" -> fluidTags;
                default -> entityTags;  // "entity_type"
            };
            target.computeIfAbsent(tag.tag(), k -> new ArrayList<>()).addAll(tag.members());
        }

        boolean empty = itemTags.isEmpty() && blockTags.isEmpty() && fluidTags.isEmpty() && entityTags.isEmpty()
                && recipes.isEmpty() && blocks.isEmpty() && entityDrops.isEmpty() && datapackJson.isEmpty();
        if (empty) {
            packDir = null;
            return;
        }

        try {
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("pack.mcmeta"), PACK_MCMETA);
            writeTagFiles(dir, "item", itemTags);
            writeTagFiles(dir, "block", blockTags);
            writeTagFiles(dir, "fluid", fluidTags);
            writeTagFiles(dir, "entity_type", entityTags);
            writeRecipeFiles(dir, recipes);
            writeBlockLoot(dir, blocks);
            writeEntityDrops(dir, entityDrops);
            writeDatapackJson(dir, datapackJson);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write Minelark generated data pack", e);
        }
        packDir = dir;
    }

    private static void writeEntityDrops(Path dir, List<EntityDropSpec> entityDrops) throws IOException {
        for (EntityDropSpec spec : entityDrops) {
            String[] parts = spec.entityId().split(":", 2);
            Path file = dir.resolve("data").resolve(parts[0]).resolve("loot_table").resolve("entities")
                    .resolve(parts[1] + ".json");
            Files.createDirectories(file.getParent());
            Files.writeString(file, spec.json());
        }
    }

    private static void writeDatapackJson(Path dir, List<DatapackJsonSpec> datapackJson) throws IOException {
        for (DatapackJsonSpec spec : datapackJson) {
            Path file = dir.resolve(spec.path());
            Files.createDirectories(file.getParent());
            Files.writeString(file, spec.json());
        }
    }

    private static void writeBlockLoot(Path dir, List<BlockSpec> blocks) throws IOException {
        for (BlockSpec block : blocks) {
            Path file = dir.resolve("data").resolve(MOD_ID).resolve("loot_table").resolve("blocks")
                    .resolve(block.id() + ".json");
            Files.createDirectories(file.getParent());
            Files.writeString(file, blockLootJson(block));
        }
    }

    private static String blockLootJson(BlockSpec block) {
        String drops = block.drops();
        if (drops.equals("none")) {
            return "{\"type\":\"minecraft:block\"}";
        }
        String item = drops.isEmpty() ? MOD_ID + ":" + block.id() : drops;
        // A shape may add loot functions for a self-drop (e.g. a slab yields two when it was double).
        String functions = "";
        if (drops.isEmpty() && !block.shape().isEmpty()) {
            ShapeAssets shape = ShapeAssetRegistry.get(block.shape());
            if (shape != null) {
                functions = shape.selfDropLootFunctions(block.id());
            }
        }
        return "{\"type\":\"minecraft:block\",\"pools\":[{\"rolls\":1,"
                + "\"entries\":[{\"type\":\"minecraft:item\"," + functions + "\"name\":\"" + item + "\"}],"
                + "\"conditions\":[{\"condition\":\"minecraft:survives_explosion\"}]}]}";
    }

    private static void writeRecipeFiles(Path dir, List<RecipeSpec> recipes) throws IOException {
        for (RecipeSpec recipe : recipes) {
            Path file = dir.resolve("data").resolve(MOD_ID).resolve("recipe").resolve(recipe.idPath() + ".json");
            Files.createDirectories(file.getParent());
            Files.writeString(file, recipe.json());
        }
    }

    /** Builds the pack profile for the datapack manager, or null if there is nothing to serve. */
    public static ResourcePackProfile createProfile() {
        Path dir = packDir;
        if (dir == null || !Files.isDirectory(dir)) {
            return null;
        }
        ResourcePackInfo info = new ResourcePackInfo(
                "minelark_generated",
                Text.literal("Minelark (generated)"),
                ResourcePackSource.BUILTIN,
                Optional.empty());
        ResourcePackProfile.PackFactory factory = new ResourcePackProfile.PackFactory() {
            @Override
            public ResourcePack open(ResourcePackInfo openInfo) {
                return new DirectoryResourcePack(openInfo, dir);
            }

            @Override
            public ResourcePack openWithOverlays(ResourcePackInfo openInfo, ResourcePackProfile.Metadata metadata) {
                return new DirectoryResourcePack(openInfo, dir);
            }
        };
        // required = true keeps the pack always enabled; TOP so it can add to others.
        ResourcePackPosition position =
                new ResourcePackPosition(true, ResourcePackProfile.InsertionPosition.TOP, false);
        return ResourcePackProfile.create(info, factory, ResourceType.SERVER_DATA, position);
    }

    private static <T> Map<String, List<String>> collectTags(
            List<T> specs, java.util.function.Function<T, String> id, java.util.function.Function<T, List<String>> tags) {
        Map<String, List<String>> byTag = new LinkedHashMap<>();
        for (T spec : specs) {
            String memberId = MOD_ID + ":" + id.apply(spec);
            for (String tag : tags.apply(spec)) {
                byTag.computeIfAbsent(tag, k -> new ArrayList<>()).add(memberId);
            }
        }
        return byTag;
    }

    private static void writeTagFiles(Path dir, String kind, Map<String, List<String>> tagsByName) throws IOException {
        for (Map.Entry<String, List<String>> entry : tagsByName.entrySet()) {
            String[] parts = entry.getKey().split(":", 2);
            String namespace = parts[0];
            String path = parts[1];
            Path file = dir.resolve("data").resolve(namespace).resolve("tags").resolve(kind).resolve(path + ".json");
            Files.createDirectories(file.getParent());
            Files.writeString(file, tagJson(entry.getValue()));
        }
    }

    private static String tagJson(List<String> values) {
        String members = values.stream().map(v -> "\"" + v + "\"").collect(Collectors.joining(","));
        return "{\"replace\":false,\"values\":[" + members + "]}";
    }

    private static void deleteRecursively(Path dir) {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException e) {
                    throw new UncheckedIOException("Failed to clean generated pack: " + p, e);
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to clean generated pack dir " + dir, e);
        }
    }
}
