package ru.nelande.minelark;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.registry.FuelRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.Rarity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.starlark.java.eval.Dict;
import net.starlark.java.eval.StarlarkFloat;
import net.starlark.java.eval.StarlarkInt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.nelande.minelark.pack.GeneratedDataPack;
import ru.nelande.minelark.script.BlockSpec;
import ru.nelande.minelark.script.ArgSpec;
import ru.nelande.minelark.script.CommandSourceView;
import ru.nelande.minelark.script.CommandSpec;
import ru.nelande.minelark.script.CommandsApi;
import ru.nelande.minelark.script.EntityView;
import ru.nelande.minelark.script.EventContext;
import ru.nelande.minelark.script.ItemStackView;
import ru.nelande.minelark.script.LevelView;
import ru.nelande.minelark.script.Log;
import ru.nelande.minelark.script.Events;
import ru.nelande.minelark.script.ItemSpec;
import ru.nelande.minelark.script.MineText;
import ru.nelande.minelark.script.PlatformInfo;
import ru.nelande.minelark.script.PlayerActions;
import ru.nelande.minelark.script.PlayerView;
import ru.nelande.minelark.script.RegistryAccess;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
        registerRuntimeEvents();
        registerCommands();

        LOGGER.info("Minelark initialized with {} item(s), {} block(s), {} recipe(s).",
                startup.items().size(), startup.blocks().size(), server.recipes().size());
    }

    /** The startup content, kept so {@code /minelark reload} can rebuild the pack with fresh recipes. */
    private static StartupResult startup = new StartupResult(List.of(), List.of());
    /** The most recently loaded server-script event callbacks (replaced on load/reload). */
    private static Events serverEvents = new Events(new Log(SCRIPT_LOG), Events.Scope.SERVER);
    /** The most recently loaded server-script custom commands (replaced on load/reload). */
    private static CommandsApi serverCommands = new CommandsApi(new Log(SCRIPT_LOG));

    /** Re-runs server scripts and rewrites the generated data pack. Returns what was declared. */
    public static ServerResult reloadServerData() {
        ServerResult result = StarlarkHost.runServer(
                scriptDir("server"), platformInfo(), registryAccess(), SCRIPT_LOG);
        regeneratePack(result.recipes());
        serverEvents = result.events();
        serverCommands = result.commands();
        return result;
    }

    /** The {@code mods} namespace's backing: reads the Fabric mod list. Shared with the client adapter. */
    public static PlatformInfo platformInfo() {
        FabricLoader loader = FabricLoader.getInstance();
        return new PlatformInfo() {
            @Override
            public boolean isLoaded(String modId) {
                return loader.isModLoaded(modId);
            }

            @Override
            public String version(String modId) {
                return loader.getModContainer(modId)
                        .map(c -> c.getMetadata().getVersion().getFriendlyString())
                        .orElse(null);
            }

            @Override
            public String name(String modId) {
                return loader.getModContainer(modId)
                        .map(c -> c.getMetadata().getName())
                        .orElse(null);
            }

            @Override
            public List<String> ids() {
                return loader.getAllMods().stream()
                        .map(c -> c.getMetadata().getId())
                        .sorted()
                        .toList();
            }
        };
    }

    /** The {@code registry} namespace's backing: reads the game registries. Shared with the client adapter. */
    public static RegistryAccess registryAccess() {
        return new RegistryAccess() {
            @Override
            public boolean has(Kind kind, String id) {
                Identifier parsed = Identifier.tryParse(id);
                return parsed != null && registry(kind).containsId(parsed);
            }

            @Override
            public List<String> ids(Kind kind, String namespace) {
                return registry(kind).getIds().stream()
                        .filter(id -> namespace == null || namespace.isEmpty()
                                || id.getNamespace().equals(namespace))
                        .map(Identifier::toString)
                        .sorted()
                        .toList();
            }
        };
    }

    private static Registry<?> registry(RegistryAccess.Kind kind) {
        return switch (kind) {
            case ITEM -> Registries.ITEM;
            case BLOCK -> Registries.BLOCK;
            case ENTITY_TYPE -> Registries.ENTITY_TYPE;
            case FLUID -> Registries.FLUID;
        };
    }

    private static void regeneratePack(List<RecipeSpec> recipes) {
        GeneratedDataPack.generate(
                FabricLoader.getInstance().getGameDir(), startup.items(), startup.blocks(), recipes);
    }

    /**
     * Bridges the runtime events (tick, player, block break, ...) to the server-script callbacks.
     * Registered once; every handler reads the current {@link #serverEvents}, so {@code /minelark
     * reload} swaps the subscriber set without re-registering with the game.
     */
    private static void registerRuntimeEvents() {
        // Gate the once-per-tick event on having a listener so idle servers pay nothing.
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (serverEvents.hasListeners("minelark:server_tick")) {
                dispatch("minelark:server_tick", Map.of(), Set.of(), false);
            }
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                dispatch("minelark:player_joined", data("player", playerView(handler.player)), Set.of(), false));

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                dispatch("minelark:player_left", data("player", playerView(handler.player)), Set.of(), false));

        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            if (!(entity instanceof ServerPlayerEntity player)) {
                return true;
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("player", playerView(player));
            data.put("source", source.getName());
            data.put("amount", (double) amount);
            if (source.getAttacker() != null) {
                data.put("attacker", entityView(source.getAttacker()));
            }
            EventContext ctx = dispatch("minelark:player_death", data, Set.of(), true);
            return ctx == null || !ctx.isCancelled();  // false = keep them alive
        });

        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
            String original = message.getSignedContent();
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("player", playerView(sender));
            data.put("message", original);
            EventContext ctx = dispatch("minelark:player_chat", data, Set.of("message"), true);
            if (ctx == null) {
                return true;
            }
            if (ctx.isCancelled()) {
                return false;
            }
            String edited = ctx.editedString("message");
            if (edited != null && !edited.equals(original)) {
                // Signed chat can't be rewritten in place; block the original and resend the edit.
                String name = sender.getName().getString();
                sender.server.getPlayerManager().broadcast(
                        Text.literal("<" + name + "> " + edited), false);
                return false;
            }
            return true;
        });

        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (world.isClient || !(player instanceof ServerPlayerEntity serverPlayer)
                    || !serverEvents.hasListeners("minelark:block_broken")) {
                return true;
            }
            EventContext ctx = dispatch("minelark:block_broken",
                    blockData(playerView(serverPlayer), state, pos, levelView(world)), Set.of(), true);
            return ctx == null || !ctx.isCancelled();  // false = cancel the break
        });
    }

    // --- shared event plumbing, also called by the mixins below ---

    /** Fires {@code id} with the given context data, returning the (mutated) ctx, or null if unheard. */
    private static EventContext dispatch(String id, Map<String, Object> data, Set<String> editable, boolean cancellable) {
        if (!serverEvents.hasListeners(id)) {
            return null;
        }
        EventContext ctx = new EventContext(id, data, editable, cancellable);
        serverEvents.fire(id, ctx, SCRIPT_LOG);
        return ctx;
    }

    private static PlayerView playerView(ServerPlayerEntity player) {
        return new PlayerView(
                player.getName().getString(),
                player.getUuidAsString(),
                player.getX(), player.getY(), player.getZ(),
                player.getHealth(),
                itemStackView(player.getMainHandStack()),
                levelView(player.getWorld()),
                new PlayerActions() {
                    @Override
                    public void tell(MineText message) {
                        player.sendMessage(toMcText(message));
                    }

                    @Override
                    public void give(String itemId, int count) {
                        Identifier id = Identifier.tryParse(itemId);
                        Item item = id == null ? null : Registries.ITEM.get(id);
                        if (item != null && item != Items.AIR) {
                            player.giveItemStack(new ItemStack(item, count));
                        }
                    }

                    @Override
                    public void teleport(double x, double y, double z) {
                        player.requestTeleport(x, y, z);
                    }
                });
    }

    private static LevelView levelView(World world) {
        return new LevelView(
                world.getRegistryKey().getValue().toString(),
                world.getTimeOfDay(),
                world.isDay(),
                world.isRaining());
    }

    private static ItemStackView itemStackView(ItemStack stack) {
        if (stack.isEmpty()) {
            return ItemStackView.empty();
        }
        return new ItemStackView(
                Registries.ITEM.getId(stack.getItem()).toString(),
                stack.getCount(),
                stack.getName().getString(),
                false);
    }

    private static EntityView entityView(Entity entity) {
        return new EntityView(
                EntityType.getId(entity.getType()).toString(),
                entity.getUuidAsString(),
                entity.getName().getString(),
                entity.getX(), entity.getY(), entity.getZ(),
                levelView(entity.getWorld()));
    }

    /** Turns a script-built {@link MineText} component into a real Minecraft {@link Text}. */
    public static MutableText toMcText(MineText text) {
        MutableText mc = text.isTranslation()
                ? Text.translatable(text.translateKey(), text.translateArgs().stream()
                        .map(Minelark::toMcText).toArray())
                : Text.literal(text.literal());

        Style style = Style.EMPTY;
        if (text.colorValue() != null) {
            style = style.withColor(parseColor(text.colorValue()));
        }
        if (text.boldValue() != null) {
            style = style.withBold(text.boldValue());
        }
        if (text.italicValue() != null) {
            style = style.withItalic(text.italicValue());
        }
        if (text.underlinedValue() != null) {
            style = style.withUnderline(text.underlinedValue());
        }
        if (text.strikethroughValue() != null) {
            style = style.withStrikethrough(text.strikethroughValue());
        }
        if (text.obfuscatedValue() != null) {
            style = style.withObfuscated(text.obfuscatedValue());
        }
        if (text.hoverText() != null) {
            style = style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, toMcText(text.hoverText())));
        }
        if (text.clickAction() != null) {
            style = style.withClickEvent(new ClickEvent(toClickAction(text.clickAction()), text.clickValue()));
        }
        mc.setStyle(style);

        for (MineText child : text.extra()) {
            mc.append(toMcText(child));
        }
        return mc;
    }

    private static TextColor parseColor(String value) {
        if (value.startsWith("#")) {
            return TextColor.fromRgb(Integer.parseInt(value.substring(1), 16));
        }
        return TextColor.fromFormatting(Formatting.byName(value));
    }

    private static ClickEvent.Action toClickAction(MineText.ClickAction action) {
        return switch (action) {
            case RUN_COMMAND -> ClickEvent.Action.RUN_COMMAND;
            case SUGGEST_COMMAND -> ClickEvent.Action.SUGGEST_COMMAND;
            case OPEN_URL -> ClickEvent.Action.OPEN_URL;
            case COPY_TO_CLIPBOARD -> ClickEvent.Action.COPY_TO_CLIPBOARD;
        };
    }

    private static String blockId(BlockState state) {
        return Registries.BLOCK.getId(state.getBlock()).toString();
    }

    private static Map<String, Object> data(String key, Object value) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(key, value);
        return map;
    }

    private static Map<String, Object> blockData(PlayerView player, BlockState state, BlockPos pos, LevelView level) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("player", player);
        map.put("block", blockId(state));
        map.put("x", pos.getX());
        map.put("y", pos.getY());
        map.put("z", pos.getZ());
        map.put("level", level);
        return map;
    }

    /** Called by {@code BlockItemMixin}: fires {@code block_placed}, returns whether to cancel it. */
    public static boolean fireBlockPlaced(ServerPlayerEntity player, BlockState state, BlockPos pos) {
        EventContext ctx = dispatch("minelark:block_placed",
                blockData(playerView(player), state, pos, levelView(player.getWorld())), Set.of(), true);
        return ctx != null && ctx.isCancelled();
    }

    /** Called by {@code CommandManagerMixin}: fires {@code command}, returns the ctx (cancel/edit) or null. */
    public static EventContext fireCommand(ServerCommandSource source, String command) {
        Map<String, Object> data = new LinkedHashMap<>();
        ServerPlayerEntity player = source.getPlayer();
        if (player != null) {
            data.put("player", playerView(player));
        }
        data.put("command", command);
        return dispatch("minelark:command", data, Set.of("command"), true);
    }

    /** Called by {@code ExplosionMixin}: fires the {@code explosion} notification. */
    public static void fireExplosion(World world, Vec3d pos, float power) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("x", pos.x);
        data.put("y", pos.y);
        data.put("z", pos.z);
        data.put("power", (double) power);
        data.put("level", levelView(world));
        dispatch("minelark:explosion", data, Set.of(), false);
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
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> {
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
                                            ctx.getSource(), IdentifierArgumentType.getIdentifier(ctx, "item"))))));

            // Script-registered commands. This callback re-fires when `/minelark reload` reloads
            // resources, so added/removed/changed commands take effect on reload.
            for (CommandSpec spec : serverCommands.commands()) {
                registerScriptCommand(dispatcher, spec);
            }
        });
    }

    /** Builds a Brigadier node for a script-declared command and registers it. */
    private static void registerScriptCommand(CommandDispatcher<ServerCommandSource> dispatcher, CommandSpec spec) {
        Command<ServerCommandSource> exec = bctx -> runScriptCommand(spec, bctx);
        List<ArgSpec> args = spec.args();
        List<String> literals = spec.literals();

        // Build the node chain from the inside out: arguments first, then literals.
        ArgumentBuilder<ServerCommandSource, ?> current = null;
        for (int i = args.size() - 1; i >= 0; i--) {
            ArgSpec arg = args.get(i);
            RequiredArgumentBuilder<ServerCommandSource, ?> node =
                    CommandManager.argument(arg.name(), brigadierType(arg.type()));
            if (i == args.size() - 1) {
                node.executes(exec);
            }
            if (current != null) {
                node.then(current);
            }
            current = node;
        }
        for (int i = literals.size() - 1; i >= 0; i--) {
            LiteralArgumentBuilder<ServerCommandSource> node = CommandManager.literal(literals.get(i));
            if (i == 0) {
                node.requires(source -> source.hasPermissionLevel(spec.permission()));
            }
            if (i == literals.size() - 1 && args.isEmpty()) {
                node.executes(exec);
            }
            if (current != null) {
                node.then(current);
            }
            current = node;
        }
        @SuppressWarnings("unchecked")
        LiteralArgumentBuilder<ServerCommandSource> root = (LiteralArgumentBuilder<ServerCommandSource>) current;
        dispatcher.register(root);
    }

    private static com.mojang.brigadier.arguments.ArgumentType<?> brigadierType(String type) {
        return switch (type) {
            case "word" -> StringArgumentType.word();
            case "string" -> StringArgumentType.greedyString();
            case "int" -> IntegerArgumentType.integer();
            case "float" -> FloatArgumentType.floatArg();
            case "bool" -> BoolArgumentType.bool();
            case "player" -> EntityArgumentType.player();
            default -> throw new IllegalStateException("unknown arg type " + type);
        };
    }

    /** Runs a script command: builds its {@code ctx}, invokes the handler, maps success to 1/0. */
    private static int runScriptCommand(CommandSpec spec, CommandContext<ServerCommandSource> bctx)
            throws CommandSyntaxException {
        try {
            LinkedHashMap<String, Object> args = new LinkedHashMap<>();
            for (ArgSpec arg : spec.args()) {
                args.put(arg.name(), readArg(arg, bctx));
            }
            ru.nelande.minelark.script.CommandContext ctx = new ru.nelande.minelark.script.CommandContext(
                    commandSource(bctx.getSource()), Dict.immutableCopyOf(args));
            return serverCommands.invoke(spec, ctx, SCRIPT_LOG) ? Command.SINGLE_SUCCESS : 0;
        } catch (CommandSyntaxException e) {
            throw e;  // let Brigadier report argument problems (e.g. player not found) normally
        } catch (RuntimeException e) {
            LOGGER.error("Minelark command /{} failed", spec.name(), e);
            bctx.getSource().sendError(Text.literal("Minelark: command '" + spec.name() + "' errored (see log)"));
            return 0;
        }
    }

    private static Object readArg(ArgSpec arg, CommandContext<ServerCommandSource> bctx)
            throws CommandSyntaxException {
        return switch (arg.type()) {
            case "word", "string" -> StringArgumentType.getString(bctx, arg.name());
            case "int" -> StarlarkInt.of(IntegerArgumentType.getInteger(bctx, arg.name()));
            case "float" -> StarlarkFloat.of(FloatArgumentType.getFloat(bctx, arg.name()));
            case "bool" -> BoolArgumentType.getBool(bctx, arg.name());
            case "player" -> playerView(EntityArgumentType.getPlayer(bctx, arg.name()));
            default -> throw new IllegalStateException("unknown arg type " + arg.type());
        };
    }

    private static CommandSourceView commandSource(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        World world = source.getWorld();
        return new CommandSourceView(
                source.getName(),
                player != null ? playerView(player) : null,
                world != null ? levelView(world) : null,
                message -> source.sendFeedback(() -> toMcText(message), false));
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

            # Adapt to what else is installed: `mods` and `registry` let a pack branch on its
            # environment without ever reaching into other mods directly.
            if mods.loaded("minecraft") and registry.item_exists("diamond"):
                log.info("Running on Minecraft with diamonds; " + str(len(mods.list())) + " mod(s) loaded.")

            # Run code when the world has finished loading. The callback receives the event `ctx`.
            def on_started(ctx):
                log.info("The world is ready!")

            events.minelark.SERVER_STARTED.on(on_started)

            # Greet players as they join, with a splash of colour.
            def on_join(ctx):
                ctx.player.tell(text("Welcome, ").append(text(ctx.player.name).color("gold").bold()))

            events.minelark.PLAYER_JOINED.on(on_join)

            # Chat is a mutable, cancellable event: block or rewrite the line.
            def on_chat(ctx):
                if "spam" in ctx.message:
                    ctx.cancel()
                else:
                    ctx.message = ctx.message.upper()

            events.minelark.PLAYER_CHAT.on(on_chat)

            # Register your own command: /wave [who]. ctx.source ran it; ctx.args holds the arguments.
            def cmd_wave(ctx):
                who = ctx.args["who"]
                ctx.source.tell(text(ctx.source.name + " waves at " + who).color("aqua"))

            commands.register("wave", cmd_wave, args = [{"name": "who", "type": "word"}])
            """;

    private static final String DEFAULT_CLIENT_SCRIPT = """
            # Minelark client script.
            # Runs once when the game client starts. These events fire on your own machine.

            log.info("Client scripts loaded.")

            # Run code once the client has finished starting up.
            def on_started(ctx):
                log.info("Client is ready!")

            events.minelark.CLIENT_STARTED.on(on_started)

            # Add a line to every item's tooltip. ctx.lines is the tooltip; reassign it to change it.
            def on_tooltip(ctx):
                ctx.lines = ctx.lines + [text("id: " + ctx.item.id).color("dark_gray")]

            events.minelark.ITEM_TOOLTIP.on(on_tooltip)

            # Hide incoming messages you would rather not see. Cancelling drops the line.
            def on_chat(ctx):
                if "spoiler" in ctx.message:
                    ctx.cancel()

            events.minelark.CLIENT_CHAT_RECEIVED.on(on_chat)

            # Show your coordinates on the F3 debug screen. `client` reads the local player live.
            def on_tick(ctx):
                p = client.player
                if p:
                    debug.set("pos", str(int(p.x)) + ", " + str(int(p.y)) + ", " + str(int(p.z)))

            events.minelark.CLIENT_TICK.on(on_tick)
            """;
}
