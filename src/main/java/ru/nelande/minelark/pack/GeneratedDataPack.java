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
import ru.nelande.minelark.script.ItemSpec;
import ru.nelande.minelark.script.RecipeSpec;

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
    public static void generate(Path gameDir, List<ItemSpec> items, List<BlockSpec> blocks, List<RecipeSpec> recipes) {
        Path dir = gameDir.resolve(MOD_ID).resolve(".generated").resolve("datapack");
        deleteRecursively(dir);

        Map<String, List<String>> itemTags = collectTags(items, ItemSpec::id, ItemSpec::tags);
        Map<String, List<String>> blockTags = collectTags(blocks, BlockSpec::id, BlockSpec::tags);

        if (itemTags.isEmpty() && blockTags.isEmpty() && recipes.isEmpty() && blocks.isEmpty()) {
            packDir = null;
            return;
        }

        try {
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("pack.mcmeta"), PACK_MCMETA);
            writeTagFiles(dir, "item", itemTags);
            writeTagFiles(dir, "block", blockTags);
            writeRecipeFiles(dir, recipes);
            writeBlockLoot(dir, blocks);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write Minelark generated data pack", e);
        }
        packDir = dir;
    }

    private static void writeBlockLoot(Path dir, List<BlockSpec> blocks) throws IOException {
        for (BlockSpec block : blocks) {
            Path file = dir.resolve("data").resolve(MOD_ID).resolve("loot_table").resolve("blocks")
                    .resolve(block.id() + ".json");
            Files.createDirectories(file.getParent());
            Files.writeString(file, blockLootJson(block.id(), block.drops()));
        }
    }

    private static String blockLootJson(String blockId, String drops) {
        if (drops.equals("none")) {
            return "{\"type\":\"minecraft:block\"}";
        }
        String item = drops.isEmpty() ? MOD_ID + ":" + blockId : drops;
        return "{\"type\":\"minecraft:block\",\"pools\":[{\"rolls\":1,"
                + "\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"" + item + "\"}],"
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
