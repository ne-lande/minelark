package ru.nelande.minelark;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.registry.FuelRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.util.Rarity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.nelande.minelark.pack.GeneratedDataPack;
import ru.nelande.minelark.script.BlockSpec;
import ru.nelande.minelark.script.Log;
import ru.nelande.minelark.script.Events;
import ru.nelande.minelark.script.ItemSpec;
import ru.nelande.minelark.script.RecipeSpec;
import ru.nelande.minelark.script.ScriptLog;
import ru.nelande.minelark.script.ServerResult;
import ru.nelande.minelark.script.StarlarkHost;
import ru.nelande.minelark.script.StartupResult;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class Minelark implements ModInitializer {
    public static final String MOD_ID = "minelark";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** Routes script output and diagnostics to the mod logger at the right level. */
    public static final ScriptLog SCRIPT_LOG = (level, message) -> {
        switch (level) {
            case DEBUG -> LOGGER.debug(message);
            case INFO -> LOGGER.info(message);
            case WARNING -> LOGGER.warn(message);
            case ERROR -> LOGGER.error(message);
        }
    };

    @Override
    public void onInitialize() {
        ensureScriptFolders();

        // Startup scripts run here, before the registries freeze, so they can register content.
        startup = StarlarkHost.runStartup(scriptDir("startup"), SCRIPT_LOG);
        registerItems(startup.items());
        registerBlocks(startup.blocks());

        // Server scripts declare the reloadable data (recipes) and register event callbacks. Run
        // them now so the generated data pack is complete before any world loads its datapacks.
        ServerResult server = reloadServerData();

        ServerLifecycleEvents.SERVER_STARTED.register(mc -> {
            serverEvents.fire("minelark:server_started", SCRIPT_LOG);
            long recipeCount = mc.getRecipeManager().values().stream()
                    .filter(entry -> entry.id().getNamespace().equals(MOD_ID))
                    .count();
            LOGGER.info("Minelark: {} recipe(s) active", recipeCount);
        });
        registerCommands();

        LOGGER.info("Minelark initialized with {} item(s), {} block(s), {} recipe(s).",
                startup.items().size(), startup.blocks().size(), server.recipes().size());
    }

    /** The startup content, kept so {@code /minelark reload} can rebuild the pack with fresh recipes. */
    private static StartupResult startup = new StartupResult(List.of(), List.of());
    /** The most recently loaded server-script event callbacks (replaced on load/reload). */
    private static Events serverEvents = new Events(new Log(SCRIPT_LOG));

    /** Re-runs server scripts and rewrites the generated data pack. Returns what was declared. */
    public static ServerResult reloadServerData() {
        ServerResult result = StarlarkHost.runServer(scriptDir("server"), SCRIPT_LOG);
        regeneratePack(result.recipes());
        serverEvents = result.events();
        return result;
    }

    private static void regeneratePack(List<RecipeSpec> recipes) {
        GeneratedDataPack.generate(
                FabricLoader.getInstance().getGameDir(), startup.items(), startup.blocks(), recipes);
    }

    private static void registerItems(List<ItemSpec> specs) {
        List<Item> registered = new ArrayList<>();
        for (ItemSpec spec : specs) {
            Identifier id = Identifier.of(MOD_ID, spec.id());
            Item.Settings settings = buildSettings(spec);
            Item item = spec.displayName().isEmpty()
                    ? new Item(settings)
                    : new NamedItem(settings, Text.literal(spec.displayName()));
            Registry.register(Registries.ITEM, id, item);
            if (spec.burnTime() > 0) {
                FuelRegistry.INSTANCE.add(item, spec.burnTime());
            }
            registered.add(item);
            LOGGER.info("Registered item {} ({})", id, item.getName().getString());
        }
        // Make the scripted items visible by adding them to the Ingredients creative tab.
        if (!registered.isEmpty()) {
            ItemGroupEvents.modifyEntriesEvent(ItemGroups.INGREDIENTS)
                    .register(entries -> registered.forEach(entries::add));
        }
    }

    private static void registerBlocks(List<BlockSpec> specs) {
        List<Item> blockItems = new ArrayList<>();
        for (BlockSpec spec : specs) {
            Identifier id = Identifier.of(MOD_ID, spec.id());

            AbstractBlock.Settings settings = AbstractBlock.Settings.create()
                    .strength((float) spec.hardness(), (float) spec.resistance());
            if (spec.requiresTool()) {
                settings.requiresTool();
            }
            if (spec.luminance() > 0) {
                int light = spec.luminance();
                settings.luminance(state -> light);
            }

            Block block = new Block(settings);
            Registry.register(Registries.BLOCK, id, block);
            Item.Settings itemSettings = new Item.Settings();
            BlockItem blockItem = spec.displayName().isEmpty()
                    ? new BlockItem(block, itemSettings)
                    : new NamedBlockItem(block, itemSettings, Text.literal(spec.displayName()));
            Registry.register(Registries.ITEM, id, blockItem);
            blockItems.add(blockItem);
            LOGGER.info("Registered block {} ({})", id, blockItem.getName().getString());
        }
        if (!blockItems.isEmpty()) {
            ItemGroupEvents.modifyEntriesEvent(ItemGroups.BUILDING_BLOCKS)
                    .register(entries -> blockItems.forEach(entries::add));
        }
    }

    private static Item.Settings buildSettings(ItemSpec spec) {
        Item.Settings settings = new Item.Settings();
        if (spec.maxDamage() > 0) {
            settings.maxDamage(spec.maxDamage()); // damageable items are implicitly unstackable
        } else {
            settings.maxCount(spec.maxStackSize());
        }
        settings.rarity(toMcRarity(spec.rarity()));
        if (spec.fireproof()) {
            settings.fireproof();
        }
        if (spec.nutrition() > 0) {
            FoodComponent food = new FoodComponent.Builder()
                    .nutrition(spec.nutrition())
                    .saturationModifier((float) spec.saturation())
                    .build();
            settings.food(food);
        }
        return settings;
    }

    /** An item whose displayed name is a fixed literal (from a script's {@code display_name}). */
    private static final class NamedItem extends Item {
        private final Text name;

        NamedItem(Item.Settings settings, Text name) {
            super(settings);
            this.name = name;
        }

        @Override
        public Text getName() {
            return name;
        }

        @Override
        public Text getName(ItemStack stack) {
            return name;
        }
    }

    /** A block item whose displayed name is a fixed literal (from a script's {@code display_name}). */
    private static final class NamedBlockItem extends BlockItem {
        private final Text name;

        NamedBlockItem(Block block, Item.Settings settings, Text name) {
            super(block, settings);
            this.name = name;
        }

        @Override
        public Text getName() {
            return name;
        }

        @Override
        public Text getName(ItemStack stack) {
            return name;
        }
    }

    private static Rarity toMcRarity(ru.nelande.minelark.script.Rarity rarity) {
        return switch (rarity) {
            case COMMON -> Rarity.COMMON;
            case UNCOMMON -> Rarity.UNCOMMON;
            case RARE -> Rarity.RARE;
            case EPIC -> Rarity.EPIC;
        };
    }

    private static void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) ->
                dispatcher.register(CommandManager.literal(MOD_ID)
                        .requires(source -> source.hasPermissionLevel(2))
                        .then(CommandManager.literal("reload").executes(ctx -> {
                            ServerResult result = reloadServerData();
                            MinecraftServer server = ctx.getSource().getServer();
                            server.reloadResources(server.getDataPackManager().getEnabledIds());
                            ctx.getSource().sendFeedback(() -> Text.literal("Minelark: reloaded "
                                    + result.scriptCount() + " server script(s), "
                                    + result.recipes().size() + " recipe(s)"), true);
                            return result.recipes().size();
                        }))
                        .then(CommandManager.literal("tags")
                                .then(CommandManager.argument("item", IdentifierArgumentType.identifier())
                                        .executes(ctx -> reportItemTags(
                                                ctx.getSource(), IdentifierArgumentType.getIdentifier(ctx, "item")))))));
    }

    /** Reports the tags an item currently belongs to - handy for verifying scripted `tags`. */
    private static int reportItemTags(ServerCommandSource source, Identifier id) {
        Item item = Registries.ITEM.getOrEmpty(id).orElse(null);
        if (item == null) {
            source.sendFeedback(() -> Text.literal("Minelark: unknown item '" + id + "'"), false);
            return 0;
        }
        List<String> tags = item.getRegistryEntry().streamTags()
                .map(tag -> tag.id().toString())
                .sorted()
                .toList();
        source.sendFeedback(() -> Text.literal(
                "Minelark: " + id + " is in " + (tags.isEmpty() ? "no tags" : String.join(", ", tags))), false);
        return tags.size();
    }

    /** The {@code <gamedir>/minelark/<phase>} folder for a given lifecycle phase. */
    public static Path scriptDir(String phase) {
        return FabricLoader.getInstance().getGameDir().resolve(MOD_ID).resolve(phase);
    }

    private static void ensureScriptFolders() {
        try {
            Files.createDirectories(scriptDir("startup"));
            Files.createDirectories(scriptDir("server"));
            Files.createDirectories(scriptDir("client"));

            writeIfAbsent(scriptDir("startup").resolve("example.star"), DEFAULT_STARTUP_SCRIPT);
            writeIfAbsent(scriptDir("server").resolve("example.star"), DEFAULT_SERVER_SCRIPT);
            writeIfAbsent(scriptDir("client").resolve("example.star"), DEFAULT_CLIENT_SCRIPT);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to prepare minelark script folders", e);
        }
    }

    private static void writeIfAbsent(Path file, String content) throws IOException {
        if (Files.notExists(file)) {
            Files.writeString(file, content);
        }
    }

    private static final String DEFAULT_STARTUP_SCRIPT = """
            # Minelark startup script.
            # Runs once at launch, before the item registry freezes.
            # Starlark is a small, deterministic Python dialect - declare content by calling
            # the builtins below.

            print("Hello from Starlark!")

            # item(...) returns a handle you can reuse instead of retyping the id.
            ruby = item("ruby", max_stack_size = 64, display_name = "Ruby", tags = ["c:gems"])
            item("sapphire", max_stack_size = 16, display_name = "Sapphire", tags = ["c:gems"])

            block("marble", hardness = 1.5, resistance = 6.0, display_name = "Marble",
                  tags = ["minecraft:mineable/pickaxe"])
            block("ruby_ore", hardness = 3.0, display_name = "Ruby Ore", drops = ruby)
            """;

    private static final String DEFAULT_SERVER_SCRIPT = """
            # Minelark server script.
            # Runs at startup and whenever you run /minelark reload.
            # Declare recipes here; they are reloadable.

            log.info("Server scripts loaded.")

            # Convert between the two example gems in a crafting grid.
            recipes.shapeless("minelark:ruby", ["minelark:sapphire"])
            recipes.shapeless("minelark:sapphire", ["minelark:ruby"])

            # Run code when the world has finished loading. The callback receives the event `ctx`.
            def on_started(ctx):
                log.info("The world is ready!")

            events.minelark.SERVER_STARTED.on(on_started)
            """;

    private static final String DEFAULT_CLIENT_SCRIPT = """
            # Minelark client script.
            # Runs once when the client starts.

            log.info("Client scripts loaded.")
            """;
}
