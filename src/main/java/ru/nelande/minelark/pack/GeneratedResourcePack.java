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
import ru.nelande.minelark.script.FluidSpec;
import ru.nelande.minelark.script.ItemSpec;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Builds and serves Minelark's runtime-generated <b>client resource pack</b> (models, blockstates,
 * textures) - the visual counterpart to {@link GeneratedDataPack}. Without it, scripted items and
 * blocks render as the missing-texture placeholder.
 *
 * <p>The {@link #generate} step is free of Minecraft types (just file IO + {@link AssetJson}), so it
 * is unit-testable without the game. Authors supply or override any asset by dropping files into
 * {@code <gamedir>/minelark/assets/} (which mirrors the pack's {@code assets/} tree); those win over
 * the generated defaults, which are only written where the author left a gap.
 */
public final class GeneratedResourcePack {
    private static final String MOD_ID = "minelark";
    // Resource pack format for Minecraft 1.21.1 (distinct from the data pack format, 48).
    private static final int PACK_FORMAT = 34;
    private static final String PACK_MCMETA =
            "{\"pack\":{\"description\":\"Minelark generated assets\",\"pack_format\":" + PACK_FORMAT + "}}";

    /** Set once the pack has been generated (null if there is nothing to serve). */
    private static volatile Path packDir;

    private GeneratedResourcePack() {
    }

    /**
     * (Re)generates the client resource pack from the declared content. Call on the client before
     * the first resource reload. If there is nothing to generate, no pack is produced.
     */
    public static void generate(Path gameDir, List<ItemSpec> items, List<BlockSpec> blocks, List<FluidSpec> fluids) {
        Path dir = gameDir.resolve(MOD_ID).resolve(".generated").resolve("resourcepack");
        deleteRecursively(dir);

        if (items.isEmpty() && blocks.isEmpty() && fluids.isEmpty()) {
            packDir = null;
            return;
        }

        try {
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("pack.mcmeta"), PACK_MCMETA);
            copyAuthorAssets(gameDir, dir);
            for (ItemSpec item : items) {
                writeItemAssets(dir, item);
            }
            for (BlockSpec block : blocks) {
                writeBlockAssets(dir, block);
            }
            for (FluidSpec fluid : fluids) {
                // The filled bucket is a plain inventory item; the fluid itself renders via a fluid handler.
                writeIfAbsent(modelPath(dir, "item", fluid.id() + "_bucket"),
                        AssetJson.itemModel(fluid.id() + "_bucket", false));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write Minelark generated resource pack", e);
        }
        packDir = dir;
    }

    /** Copies the author drop-in folder ({@code minelark/assets}) into the pack's {@code assets} tree. */
    private static void copyAuthorAssets(Path gameDir, Path packDir) throws IOException {
        Path source = gameDir.resolve(MOD_ID).resolve("assets");
        if (!Files.isDirectory(source)) {
            return;
        }
        Path target = packDir.resolve("assets");
        try (Stream<Path> walk = Files.walk(source)) {
            for (Path path : (Iterable<Path>) walk::iterator) {
                Path dest = target.resolve(source.relativize(path).toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(dest);
                } else {
                    Files.createDirectories(dest.getParent());
                    Files.copy(path, dest);
                }
            }
        }
    }

    private static void writeItemAssets(Path dir, ItemSpec item) throws IOException {
        writeIfAbsent(modelPath(dir, "item", item.id()), AssetJson.itemModel(item.id(), item.isTool()));
    }

    private static void writeBlockAssets(Path dir, BlockSpec block) throws IOException {
        String id = block.id();
        // The block-item model inherits the appropriate block/shape model.
        writeIfAbsent(modelPath(dir, "item", id), AssetJson.parentedItemModel(itemParentModel(block)));
        writeIfAbsent(blockstatePath(dir, id), blockstate(block));
        for (var entry : blockModels(block).entrySet()) {
            writeIfAbsent(modelPath(dir, "block", entry.getKey()), entry.getValue());
        }
    }

    // --- per-shape asset selection (built-ins + addon-registered shapes via ShapeAssetRegistry) ---

    private static String itemParentModel(BlockSpec block) {
        ShapeAssets shape = shapeAssets(block);
        return shape != null ? shape.itemParentModel(block.id()) : block.id();
    }

    private static String blockstate(BlockSpec block) {
        ShapeAssets shape = shapeAssets(block);
        return shape != null ? shape.blockstate(block.id()) : AssetJson.simpleBlockstate(block.id());
    }

    /** The block models to emit, keyed by model file name (all textured with {@code block/<id>}). */
    private static Map<String, String> blockModels(BlockSpec block) {
        ShapeAssets shape = shapeAssets(block);
        if (shape != null) {
            return shape.blockModels(block.id());
        }
        Map<String, String> models = new LinkedHashMap<>();
        models.put(block.id(), AssetJson.cubeAllBlockModel(block.id()));
        return models;
    }

    /** The registered asset generator for a block's shape, or {@code null} for a plain full cube. */
    private static ShapeAssets shapeAssets(BlockSpec block) {
        return block.shape().isEmpty() ? null : ShapeAssetRegistry.get(block.shape());
    }

    private static Path modelPath(Path dir, String kind, String id) {
        return dir.resolve("assets").resolve(MOD_ID).resolve("models").resolve(kind).resolve(id + ".json");
    }

    private static Path blockstatePath(Path dir, String id) {
        return dir.resolve("assets").resolve(MOD_ID).resolve("blockstates").resolve(id + ".json");
    }

    private static void writeIfAbsent(Path file, String content) throws IOException {
        if (Files.exists(file)) {
            return;  // an author-supplied asset wins
        }
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    /** Builds the pack profile for the client resource manager, or null if there is nothing to serve. */
    public static ResourcePackProfile createProfile() {
        Path dir = packDir;
        if (dir == null || !Files.isDirectory(dir)) {
            return null;
        }
        ResourcePackInfo info = new ResourcePackInfo(
                "minelark_generated_assets",
                Text.literal("Minelark (generated assets)"),
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
        ResourcePackPosition position =
                new ResourcePackPosition(true, ResourcePackProfile.InsertionPosition.TOP, false);
        return ResourcePackProfile.create(info, factory, ResourceType.CLIENT_RESOURCES, position);
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
                    throw new UncheckedIOException("Failed to clean generated resource pack: " + p, e);
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to clean generated resource pack dir " + dir, e);
        }
    }
}
