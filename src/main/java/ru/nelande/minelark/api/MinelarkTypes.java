package ru.nelande.minelark.api;

import net.minecraft.block.Blocks;
import net.minecraft.block.FenceBlock;
import net.minecraft.block.SlabBlock;
import net.minecraft.block.StairsBlock;
import net.minecraft.block.WallBlock;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.item.ArmorMaterials;
import net.minecraft.item.ToolMaterial;
import net.minecraft.item.ToolMaterials;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;
import ru.nelande.minelark.pack.ShapeAssets;
import ru.nelande.minelark.pack.ShapeAssetRegistry;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The public extension point for mod-added content types. Minelark keeps the curated, fail-fast,
 * name-based API for pack scripts, but the set of valid names is open: this class holds the built-in
 * defaults, and addon mods register more through the {@code minelark:types} entrypoint (see
 * {@link MinelarkTypesInitializer}).
 *
 * <p>Covers the types that are <b>not</b> plain registries (so scripts can't just reference them by
 * {@code namespace:id}): block sound groups, tool tiers, and block shapes. Armor materials <i>are</i>
 * registry-backed, so besides the aliases registered here, {@link #resolveArmorMaterial} also accepts
 * any {@code namespace:id} straight from the game registry. Rarity is a fixed vanilla enum and cannot
 * be extended by anyone.
 *
 * <pre>{@code
 * // in an addon mod, from its MinelarkTypesInitializer:
 * MinelarkTypes.sound("create:cogs", AllSoundGroups.COGS);
 * MinelarkTypes.toolTier("create:brass", BrassToolMaterial.INSTANCE);
 * MinelarkTypes.shape("create:funnel", settings -> new FunnelBlock(settings), funnelAssets);
 * }</pre>
 */
public final class MinelarkTypes {
    private static final Map<String, BlockSoundGroup> SOUNDS = new LinkedHashMap<>();
    private static final Map<String, ToolMaterial> TOOL_TIERS = new LinkedHashMap<>();
    private static final Map<String, RegistryEntry<ArmorMaterial>> ARMOR_MATERIALS = new LinkedHashMap<>();
    private static final Map<String, Shape> SHAPES = new LinkedHashMap<>();

    private static boolean builtinsRegistered;

    private MinelarkTypes() {
    }

    // --- registration (addon-facing) ---

    /** Registers a block sound-group name usable as {@code block(sound=...)}. */
    public static void sound(String name, BlockSoundGroup group) {
        SOUNDS.put(name, group);
    }

    /** Registers a tool-material tier usable as {@code item(tool_tier=...)}. */
    public static void toolTier(String name, ToolMaterial material) {
        TOOL_TIERS.put(name, material);
    }

    /** Registers an armor-material alias usable as {@code item(armor_material=...)}. */
    public static void armorMaterial(String name, RegistryEntry<ArmorMaterial> material) {
        ARMOR_MATERIALS.put(name, material);
    }

    /** Registers a block shape usable as {@code block(shape=...)}, with its block factory and assets. */
    public static void shape(String name, Shape factory, ShapeAssets assets) {
        SHAPES.put(name, factory);
        ShapeAssetRegistry.register(name, assets);
    }

    // --- lookup (adapter-facing) ---

    /** The sound group for a name, or {@code null} if unknown. */
    public static BlockSoundGroup sound(String name) {
        return SOUNDS.get(name);
    }

    /** The tool material for a tier name, or {@code null} if unknown. */
    public static ToolMaterial toolTier(String name) {
        return TOOL_TIERS.get(name);
    }

    /** The block factory for a shape name, or {@code null} if unknown. */
    public static Shape shape(String name) {
        return SHAPES.get(name);
    }

    /**
     * Resolves an armor material: a registered alias first, then any {@code namespace:id} from the
     * game's armor-material registry (so mod materials work with no addon). {@code null} if neither.
     */
    public static RegistryEntry<ArmorMaterial> resolveArmorMaterial(String name) {
        RegistryEntry<ArmorMaterial> alias = ARMOR_MATERIALS.get(name);
        if (alias != null) {
            return alias;
        }
        Identifier id = Identifier.tryParse(name);
        return id == null ? null : Registries.ARMOR_MATERIAL.getEntry(id).orElse(null);
    }

    // --- valid-name sets, for the script layer's fail-fast validation ---

    public static Set<String> soundNames() {
        return Set.copyOf(SOUNDS.keySet());
    }

    public static Set<String> toolTierNames() {
        return Set.copyOf(TOOL_TIERS.keySet());
    }

    public static Set<String> armorMaterialNames() {
        return Set.copyOf(ARMOR_MATERIALS.keySet());
    }

    public static Set<String> shapeNames() {
        return Set.copyOf(SHAPES.keySet());
    }

    // --- built-in defaults ---

    /** Registers Minelark's built-in vanilla types. Called once, before addons and startup scripts. */
    public static synchronized void registerBuiltins() {
        if (builtinsRegistered) {
            return;
        }
        builtinsRegistered = true;

        sound("stone", BlockSoundGroup.STONE);
        sound("wood", BlockSoundGroup.WOOD);
        sound("gravel", BlockSoundGroup.GRAVEL);
        sound("grass", BlockSoundGroup.GRASS);
        sound("metal", BlockSoundGroup.METAL);
        sound("glass", BlockSoundGroup.GLASS);
        sound("wool", BlockSoundGroup.WOOL);
        sound("sand", BlockSoundGroup.SAND);
        sound("snow", BlockSoundGroup.SNOW);
        sound("ladder", BlockSoundGroup.LADDER);
        sound("anvil", BlockSoundGroup.ANVIL);
        sound("slime", BlockSoundGroup.SLIME);
        sound("honey", BlockSoundGroup.HONEY);
        sound("bamboo", BlockSoundGroup.BAMBOO);
        sound("nether", BlockSoundGroup.NETHERRACK);

        toolTier("wood", ToolMaterials.WOOD);
        toolTier("stone", ToolMaterials.STONE);
        toolTier("iron", ToolMaterials.IRON);
        toolTier("gold", ToolMaterials.GOLD);
        toolTier("diamond", ToolMaterials.DIAMOND);
        toolTier("netherite", ToolMaterials.NETHERITE);

        armorMaterial("leather", ArmorMaterials.LEATHER);
        armorMaterial("chainmail", ArmorMaterials.CHAIN);
        armorMaterial("iron", ArmorMaterials.IRON);
        armorMaterial("gold", ArmorMaterials.GOLD);
        armorMaterial("diamond", ArmorMaterials.DIAMOND);
        armorMaterial("netherite", ArmorMaterials.NETHERITE);
        armorMaterial("turtle", ArmorMaterials.TURTLE);

        // Built-in shape block factories (assets are registered statically in ShapeAssetRegistry).
        SHAPES.put("slab", SlabBlock::new);
        SHAPES.put("stairs", settings -> new StairsBlock(Blocks.STONE.getDefaultState(), settings) {
        });
        SHAPES.put("fence", FenceBlock::new);
        SHAPES.put("wall", WallBlock::new);
    }
}
