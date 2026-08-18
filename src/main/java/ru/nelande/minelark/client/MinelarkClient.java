package ru.nelande.minelark.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandlerRegistry;
import net.fabricmc.fabric.api.client.render.fluid.v1.SimpleFluidRenderHandler;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import net.minecraft.fluid.Fluid;
import net.minecraft.util.Identifier;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.Sequence;
import net.starlark.java.eval.StarlarkList;
import ru.nelande.minelark.Minelark;
import ru.nelande.minelark.net.PushDeliverPayload;
import ru.nelande.minelark.net.PushOfferPayload;
import ru.nelande.minelark.net.PushRequestPayload;
import ru.nelande.minelark.net.ScriptPayload;
import ru.nelande.minelark.script.Capability;
import ru.nelande.minelark.script.ClientAccess;
import ru.nelande.minelark.script.ClientNetwork;
import ru.nelande.minelark.script.ClientNetworkApi;
import ru.nelande.minelark.script.ClientResult;
import ru.nelande.minelark.script.DebugApi;
import ru.nelande.minelark.script.EventContext;
import ru.nelande.minelark.script.Events;
import ru.nelande.minelark.script.HudAnchor;
import ru.nelande.minelark.script.HudApi;
import ru.nelande.minelark.script.HudElement;
import ru.nelande.minelark.script.ItemStackView;
import ru.nelande.minelark.script.LevelView;
import ru.nelande.minelark.script.Log;
import ru.nelande.minelark.script.MineText;
import ru.nelande.minelark.pack.GeneratedResourcePack;
import ru.nelande.minelark.script.FluidSpec;
import ru.nelande.minelark.script.PlayerActions;
import ru.nelande.minelark.script.PlayerView;
import ru.nelande.minelark.script.PushManifest;
import ru.nelande.minelark.script.RemoteScriptPolicy;
import ru.nelande.minelark.script.Sha256;
import ru.nelande.minelark.script.StarlarkHost;
import ru.nelande.minelark.script.StartupResult;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Client-side adapter: runs the {@code client/} scripts once at client startup and bridges the
 * game's client events (lifecycle, tick, tooltip, chat) to the callbacks they registered, and backs
 * the {@code client} and {@code debug} namespaces. This is the client mirror of {@link Minelark}; the
 * MC-agnostic engine lives in the {@code script} package.
 */
public final class MinelarkClient implements ClientModInitializer {
    /** The callbacks the client scripts registered. Read live so a future reload can swap the set. */
    private static Events clientEvents = new Events(new Log(Minelark.SCRIPT_LOG), Events.Scope.CLIENT);
    /** The F3 debug lines client scripts set, read by {@code DebugHudMixin}. */
    private static DebugApi debug = new DebugApi();
    /** The on-screen HUD elements client scripts set, drawn by the {@code HudRenderCallback}. */
    private static HudApi hud = new HudApi();
    /** The client scripts' {@code net} channel handlers, fired when a server message arrives. */
    private static ClientNetworkApi clientNetwork = new ClientNetworkApi(ClientNetwork.NOOP, new Log(Minelark.SCRIPT_LOG));

    // --- N3: server-pushed client scripts (kept separate from the local client scripts above, so both
    // sets of events/HUD/debug/net render and fire; the local ones are never affected by a server) ---

    /** The client's editable security policy for pushed scripts; loaded lazily on the first offer. */
    private static RemoteScriptPolicy remotePolicy;
    /** Results of the currently-running pushed bundle (empty until a server's scripts are accepted+run). */
    private static Events pushedEvents = new Events(new Log(Minelark.SCRIPT_LOG), Events.Scope.CLIENT);
    private static DebugApi pushedDebug = new DebugApi();
    private static HudApi pushedHud = new HudApi();
    private static ClientNetworkApi pushedNetwork = new ClientNetworkApi(ClientNetwork.NOOP, new Log(Minelark.SCRIPT_LOG));

    /** The offer being downloaded/awaited for the current server, or null. Guarded by the client thread. */
    private static PushManifest activeManifest;
    /** The source key (server address) the {@link #activeManifest} came from. */
    private static String activeSource;
    /** Capabilities granted for the active offer (server request capped by the client policy). */
    private static Set<Capability> activeGranted = Set.of();
    /** File names still awaited from the server before the active bundle can run. */
    private static final Set<String> pendingFiles = new LinkedHashSet<>();
    /** An offer awaiting the player's decision (from a chat prompt), or null. */
    private static PushManifest promptManifest;
    private static String promptSource;
    /** The bundle hash last run, so an unchanged hot-push offer is a no-op. */
    private static String lastRanBundleHash = "";

    /** Puts a client {@code net.send} on the wire to the server. */
    private static final ClientNetwork CLIENT_SENDER = (channel, json) -> {
        if (ClientPlayNetworking.canSend(ScriptPayload.ID)) {
            ClientPlayNetworking.send(new ScriptPayload(channel, json));
        }
    };

    // Chat edits are computed in the ALLOW_* pass and applied in the following MODIFY_* pass (same
    // message, same thread), so a thread-local carries the rewritten text between the two callbacks.
    private static final ThreadLocal<String> pendingSentChat = new ThreadLocal<>();
    private static final ThreadLocal<String> pendingReceivedGame = new ThreadLocal<>();

    @Override
    public void onInitializeClient() {
        // Build the client resource pack for the items/blocks the startup phase registered, so scripted
        // content has real models/textures instead of the missing-texture placeholder.
        StartupResult startup = Minelark.startupContent();
        GeneratedResourcePack.generate(
                FabricLoader.getInstance().getGameDir(), startup.items(), startup.blocks(), startup.fluids());
        registerFluidRendering(startup.fluids());

        ClientResult result = StarlarkHost.runClient(
                Minelark.scriptDir("client"), CLIENT_ACCESS,
                Minelark.platformInfo(), Minelark.registryAccess(), CLIENT_SENDER, Minelark.SCRIPT_LOG);
        clientEvents = result.events();
        debug = result.debug();
        hud = result.hud();
        clientNetwork = result.network();
        registerClientEvents();
        registerHudRendering();
        registerNetworking();
        registerPushReceivers();
        registerRemoteCommands();
        Minelark.LOGGER.info("Minelark client: {} script(s) loaded.", result.scriptCount());
    }

    /** Decodes server-to-client messages and fires both local and pushed {@code net.on} handlers. */
    private static void registerNetworking() {
        ClientPlayNetworking.registerGlobalReceiver(ScriptPayload.ID, (payload, context) -> {
            boolean local = clientNetwork.hasListeners(payload.channel());
            boolean pushed = pushedNetwork.hasListeners(payload.channel());
            if (!local && !pushed) {
                return;
            }
            context.client().execute(() -> {
                if (local) {
                    clientNetwork.dispatch(payload.channel(), payload.data(), Minelark.SCRIPT_LOG);
                }
                if (pushed) {
                    pushedNetwork.dispatch(payload.channel(), payload.data(), Minelark.SCRIPT_LOG);
                }
            });
        });
    }

    // --- N3: receiving, verifying, and running server-pushed client scripts ---

    /** The {@code <gamedir>/minelark} directory (parent of the phase folders). */
    private static Path minelarkDir() {
        return Minelark.scriptDir("startup").getParent();
    }

    /** The client's remote-script policy, loaded (with secure defaults) on first use. */
    private static RemoteScriptPolicy policy() {
        if (remotePolicy == null) {
            remotePolicy = RemoteScriptPolicy.load(minelarkDir().resolve("remote_policy.json"));
        }
        return remotePolicy;
    }

    /** Registers the offer/deliver receivers that drive script propagation from the server. */
    private static void registerPushReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(PushOfferPayload.ID, (payload, context) ->
                context.client().execute(() -> onOffer(payload.manifest())));
        ClientPlayNetworking.registerGlobalReceiver(PushDeliverPayload.ID, (payload, context) ->
                context.client().execute(() -> onDeliver(payload.name(), payload.body())));
        // Leaving a server stops its pushed scripts (they never linger onto the next connection).
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
            clearPushed();
            promptManifest = null;
            promptSource = null;
        }));
    }

    /** Handles a server's offer: gate singleplayer, consult the policy, then request, prompt, or ignore. */
    private static void onOffer(String manifestJson) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.isIntegratedServerRunning()) {
            return;   // never engage in singleplayer / self-hosted LAN
        }
        String source = currentSource();
        if (source == null) {
            return;   // no identifiable server to key consent on
        }
        PushManifest manifest;
        try {
            manifest = PushManifest.fromJsonString(manifestJson);
        } catch (RuntimeException malformed) {
            Minelark.LOGGER.warn("Minelark: ignoring a malformed push offer from {}", source);
            return;
        }
        if (manifest.entries().isEmpty()) {
            clearPushed();   // the server withdrew its scripts (e.g. emptied push/ then reloaded)
            return;
        }
        if (manifest.bundleHash().equals(lastRanBundleHash) && source.equals(activeSource)) {
            return;   // unchanged hot-push offer; already running this exact bundle
        }
        switch (policy().decide(source, manifest.requested())) {
            case ACCEPT -> beginDownload(source, manifest);
            case DECLINE -> { /* remembered no / blocked / feature off: silently ignore */ }
            case PROMPT -> prompt(source, manifest);
        }
    }

    /** Works out which files are not already cached at the right hash, and asks the server for them. */
    private static void beginDownload(String source, PushManifest manifest) {
        activeManifest = manifest;
        activeSource = source;
        activeGranted = policy().granted(manifest.requested());
        pendingFiles.clear();
        Path dir = receivedDir(source);
        for (PushManifest.Entry entry : manifest.entries()) {
            if (!cachedMatches(dir.resolve(entry.name()), entry.sha256())) {
                pendingFiles.add(entry.name());
            }
        }
        if (pendingFiles.isEmpty()) {
            runPushed();   // everything already cached (reconnect, or an unchanged file set)
            return;
        }
        ClientPlayNetworking.send(new PushRequestPayload(String.join("\n", pendingFiles)));
    }

    /** Handles a delivered body: verify its hash against the manifest, cache it, and run when complete. */
    private static void onDeliver(String name, String body) {
        if (activeManifest == null) {
            return;
        }
        PushManifest.Entry entry = activeManifest.entries().stream()
                .filter(e -> e.name().equals(name)).findFirst().orElse(null);
        if (entry == null) {
            return;   // not part of the current offer
        }
        if (!Sha256.hex(body).equals(entry.sha256())) {
            Minelark.LOGGER.warn("Minelark: dropping pushed script '{}' - hash mismatch (corrupt or spoofed)", name);
            return;
        }
        writeReceived(receivedDir(activeSource).resolve(name), body);
        pendingFiles.remove(name);
        if (pendingFiles.isEmpty()) {
            runPushed();
        }
    }

    /** Runs the fully-downloaded active bundle in the capability-restricted sandbox, replacing any prior. */
    private static void runPushed() {
        Path dir = receivedDir(activeSource);
        // Verify the whole set once more, and drop any cached file no longer in the offer (so a script
        // the server removed does not keep running).
        Set<String> names = new LinkedHashSet<>();
        for (PushManifest.Entry entry : activeManifest.entries()) {
            if (!cachedMatches(dir.resolve(entry.name()), entry.sha256())) {
                Minelark.LOGGER.warn("Minelark: pushed bundle incomplete ('{}' missing/mismatched); not running", entry.name());
                return;
            }
            names.add(entry.name());
        }
        pruneStale(dir, names);

        ClientResult result = StarlarkHost.runPushedClient(
                dir, CLIENT_ACCESS, Minelark.platformInfo(), Minelark.registryAccess(),
                CLIENT_SENDER, activeGranted, Minelark.SCRIPT_LOG);
        pushedEvents = result.events();
        pushedDebug = result.debug();
        pushedHud = result.hud();
        pushedNetwork = result.network();
        lastRanBundleHash = activeManifest.bundleHash();
        Minelark.LOGGER.info("Minelark: running {} pushed client script(s) from {} (granted: {})",
                result.scriptCount(), activeSource, Capability.tokens(activeGranted));
    }

    /** Drops all pushed state - used on decline, on an empty offer, or when leaving a server. */
    private static void clearPushed() {
        pushedEvents = new Events(new Log(Minelark.SCRIPT_LOG), Events.Scope.CLIENT);
        pushedDebug = new DebugApi();
        pushedHud = new HudApi();
        pushedNetwork = new ClientNetworkApi(ClientNetwork.NOOP, new Log(Minelark.SCRIPT_LOG));
        activeManifest = null;
        activeSource = null;
        activeGranted = Set.of();
        pendingFiles.clear();
        lastRanBundleHash = "";
    }

    /** Posts the clickable consent prompt and remembers the offer so a button can act on it. */
    private static void prompt(String source, PushManifest manifest) {
        promptManifest = manifest;
        promptSource = source;
        MutableText message = Text.literal("[Minelark] ").formatted(Formatting.AQUA)
                .append(Text.literal("This server wants to run client scripts on your machine. ")
                        .formatted(Formatting.WHITE))
                .append(button("[Accept]", Formatting.GREEN, "/mlremote accept"))
                .append(Text.literal(" "))
                .append(button("[Decline]", Formatting.RED, "/mlremote decline"))
                .append(Text.literal(" "))
                .append(button("[What can they do?]", Formatting.GRAY, "/mlremote details"));
        showLocal(message);
    }

    /** A single clickable chat button that runs a client command when clicked. */
    private static MutableText button(String label, Formatting color, String command) {
        return Text.literal(label).setStyle(Style.EMPTY
                .withColor(color)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal(command))));
    }

    /** Registers the client-only {@code /mlremote ...} commands the consent buttons and policy use. */
    private static void registerRemoteCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) ->
                dispatcher.register(ClientCommandManager.literal("mlremote")
                        .then(ClientCommandManager.literal("accept").executes(ctx -> {
                            respondToPrompt(true);
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("decline").executes(ctx -> {
                            respondToPrompt(false);
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("trust").executes(ctx -> {
                            trustCurrent();
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("details").executes(ctx -> {
                            showDetails();
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("list").executes(ctx -> {
                            listPolicy();
                            return 1;
                        }))));
    }

    /** Accept/decline a pending prompt: remember the choice and either download or drop. */
    private static void respondToPrompt(boolean accepted) {
        if (promptManifest == null || promptSource == null) {
            showLocal(Text.literal("[Minelark] No pending script offer.").formatted(Formatting.GRAY));
            return;
        }
        PushManifest manifest = promptManifest;
        String source = promptSource;
        promptManifest = null;
        promptSource = null;
        policy().remember(source, accepted);
        if (accepted) {
            showLocal(Text.literal("[Minelark] Accepted scripts from " + source + ".").formatted(Formatting.GREEN));
            beginDownload(source, manifest);
        } else {
            showLocal(Text.literal("[Minelark] Declined. This server's scripts will not run.").formatted(Formatting.RED));
            clearPushed();
        }
    }

    /** Always-allow the current server (skips future prompts) and download its pending offer now. */
    private static void trustCurrent() {
        String source = currentSource();
        if (source == null) {
            return;
        }
        policy().trust(source);
        showLocal(Text.literal("[Minelark] Trusting " + source + " - its scripts will run without asking.")
                .formatted(Formatting.GREEN));
        if (promptManifest != null && source.equals(promptSource)) {
            PushManifest manifest = promptManifest;
            promptManifest = null;
            promptSource = null;
            beginDownload(source, manifest);
        }
    }

    /** Explains what pushed scripts can and cannot do, and what this offer requested. */
    private static void showDetails() {
        showLocal(Text.literal("[Minelark] Pushed scripts run sandboxed. They can draw HUDs and F3 lines, "
                + "show local messages, and read your chat/tooltips. They cannot run commands or send chat "
                + "as you, read your files, or reach other servers.").formatted(Formatting.GRAY));
        if (promptManifest != null) {
            showLocal(Text.literal("This server requested: " + Capability.tokens(promptManifest.requested())
                    + "; your policy allows: " + Capability.tokens(policy().granted(promptManifest.requested()))
                    + ".").formatted(Formatting.GRAY));
        }
    }

    /** Prints the current remote-script policy (trusted sources and remembered decisions). */
    private static void listPolicy() {
        showLocal(Text.literal("[Minelark] Remote scripts " + (policy().enabled() ? "on" : "off")
                + "; allowed by default: " + Capability.tokens(policy().defaultAllow())).formatted(Formatting.AQUA));
        showLocal(Text.literal("Trusted: " + policy().trustedSources()).formatted(Formatting.GRAY));
        showLocal(Text.literal("Remembered: " + policy().decisions()).formatted(Formatting.GRAY));
    }

    // --- helpers for the received-script cache ---

    /** {@code <gamedir>/minelark/.received/<sanitised-source>/} - where a server's pushed files are cached. */
    private static Path receivedDir(String source) {
        String safe = source.replaceAll("[^a-zA-Z0-9_.-]", "_");
        return minelarkDir().resolve(".received").resolve(safe);
    }

    private static boolean cachedMatches(Path file, String sha256) {
        try {
            return Files.isRegularFile(file) && Sha256.hex(Files.readString(file)).equals(sha256);
        } catch (IOException e) {
            return false;
        }
    }

    private static void writeReceived(Path file, String body) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, body);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to cache pushed script " + file, e);
        }
    }

    /** Deletes cached {@code .star} files that are not in the current bundle, so removed scripts stop running. */
    private static void pruneStale(Path dir, Set<String> keep) {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            List<Path> stale = walk
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".star"))
                    .filter(p -> !keep.contains(dir.relativize(p).toString().replace('\\', '/')))
                    .toList();
            for (Path file : stale) {
                Files.deleteIfExists(file);
            }
        } catch (IOException e) {
            Minelark.LOGGER.warn("Minelark: could not prune stale pushed scripts in {}", dir, e);
        }
    }

    /** The current server's address, used as the consent key; null in singleplayer or if unknown. */
    private static String currentSource() {
        ServerInfo entry = MinecraftClient.getInstance().getCurrentServerEntry();
        return entry != null ? entry.address : null;
    }

    /** Shows a message in the local chat (never sent anywhere). */
    private static void showLocal(Text message) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.inGameHud != null) {
            mc.inGameHud.getChatHud().addMessage(message);
        }
    }

    /** The F3 debug lines to append to the vanilla overlay (local scripts, then pushed). */
    public static List<String> debugLines() {
        List<String> local = debug.lines();
        List<String> pushed = pushedDebug.lines();
        if (pushed.isEmpty()) {
            return local;
        }
        List<String> combined = new ArrayList<>(local);
        combined.addAll(pushed);
        return combined;
    }

    /** Draws the client scripts' {@code hud.*} elements onto the screen each frame. */
    private static void registerHudRendering() {
        HudRenderCallback.EVENT.register((context, tickCounter) -> {
            List<HudElement> local = hud.elements();
            List<HudElement> pushed = pushedHud.elements();
            if (local.isEmpty() && pushed.isEmpty()) {
                return;
            }
            TextRenderer font = MinecraftClient.getInstance().textRenderer;
            int screenW = context.getScaledWindowWidth();
            int screenH = context.getScaledWindowHeight();
            for (HudElement element : local) {
                renderElement(context, font, element, screenW, screenH);
            }
            for (HudElement element : pushed) {
                renderElement(context, font, element, screenW, screenH);
            }
        });
    }

    /** Positions an element by its anchor and footprint, then draws the matching shape. */
    private static void renderElement(DrawContext context, TextRenderer font, HudElement element, int sw, int sh) {
        switch (element) {
            case HudElement.Text e -> {
                Text text = Minelark.toMcText(e.content());
                int[] p = position(e.anchor(), e.x(), e.y(), font.getWidth(text), font.fontHeight, sw, sh);
                context.drawText(font, text, p[0], p[1], e.color(), e.shadow());
            }
            case HudElement.Rect e -> {
                int[] p = position(e.anchor(), e.x(), e.y(), e.width(), e.height(), sw, sh);
                context.fill(p[0], p[1], p[0] + e.width(), p[1] + e.height(), e.color());
            }
            case HudElement.Bar e -> {
                int[] p = position(e.anchor(), e.x(), e.y(), e.width(), e.height(), sw, sh);
                context.fill(p[0], p[1], p[0] + e.width(), p[1] + e.height(), e.background());
                int filled = (int) Math.round(e.width() * clamp01(e.progress()));
                if (filled > 0) {
                    context.fill(p[0], p[1], p[0] + filled, p[1] + e.height(), e.color());
                }
            }
            case HudElement.Image e -> {
                int[] p = position(e.anchor(), e.x(), e.y(), e.width(), e.height(), sw, sh);
                Identifier texture = Identifier.tryParse(e.texture());
                if (texture != null) {
                    context.drawGuiTexture(texture, p[0], p[1], e.width(), e.height());
                }
            }
            case HudElement.Item e -> {
                int[] p = position(e.anchor(), e.x(), e.y(), 16, 16, sw, sh);
                Identifier id = Identifier.tryParse(e.itemId());
                if (id != null) {
                    context.drawItem(new ItemStack(Registries.ITEM.get(id)), p[0], p[1]);
                }
            }
            case HudElement.Pie e -> {
                int[] p = position(e.anchor(), e.x(), e.y(), e.radius() * 2, e.radius() * 2, sw, sh);
                renderPie(context, e, p[0], p[1]);
            }
            case HudElement.Graph e -> {
                int[] p = position(e.anchor(), e.x(), e.y(), e.width(), e.height(), sw, sh);
                renderGraph(context, e, p[0], p[1]);
            }
        }
    }

    /** Turns an anchor and offsets into an absolute (x, y), inset from the chosen screen edge/corner. */
    private static int[] position(HudAnchor anchor, int x, int y, int width, int height, int screenW, int screenH) {
        return switch (anchor) {
            case TOP_LEFT -> new int[]{x, y};
            case TOP_RIGHT -> new int[]{screenW - x - width, y};
            case BOTTOM_LEFT -> new int[]{x, screenH - y - height};
            case BOTTOM_RIGHT -> new int[]{screenW - x - width, screenH - y - height};
            case CENTER -> new int[]{screenW / 2 - width / 2 + x, screenH / 2 - height / 2 + y};
        };
    }

    private static double clamp01(double value) {
        return value < 0 ? 0 : Math.min(value, 1);
    }

    /** Draws a bar graph (columns scaled to the tallest value), like the F3 frame-time graph. */
    private static void renderGraph(DrawContext context, HudElement.Graph graph, int px, int py) {
        int w = graph.width();
        int h = graph.height();
        context.fill(px, py, px + w, py + h, 0x40000000);   // faint backing
        List<Double> values = graph.values();
        double max = 0;
        for (double value : values) {
            max = Math.max(max, Math.abs(value));
        }
        if (max <= 0) {
            return;
        }
        int n = values.size();
        for (int i = 0; i < n; i++) {
            int x0 = px + (int) ((long) i * w / n);
            int x1 = px + (int) ((long) (i + 1) * w / n);
            int barHeight = (int) Math.round(Math.abs(values.get(i)) / max * h);
            context.fill(x0, py + h - barHeight, Math.max(x1, x0 + 1), py + h, graph.color());
        }
    }

    /** Draws a pie chart as a set of coloured triangle fans (the F3-style custom vertex path). */
    private static void renderPie(DrawContext context, HudElement.Pie pie, int px, int py) {
        double total = 0;
        for (HudElement.Pie.Slice slice : pie.slices()) {
            total += Math.max(0, slice.value());
        }
        if (total <= 0) {
            return;
        }
        double centerX = px + pie.radius();
        double centerY = py + pie.radius();
        double radius = pie.radius();
        Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        BufferBuilder buffer = Tessellator.getInstance()
                .begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
        double angle = -Math.PI / 2;   // start at the top (12 o'clock), sweep clockwise
        for (HudElement.Pie.Slice slice : pie.slices()) {
            double share = Math.max(0, slice.value()) / total;
            if (share <= 0) {
                continue;
            }
            double sweep = share * (Math.PI * 2);
            int segments = Math.max(1, (int) Math.ceil(sweep / (Math.PI / 32)));
            double step = sweep / segments;
            for (int s = 0; s < segments; s++) {
                double a0 = angle + s * step;
                double a1 = angle + (s + 1) * step;
                vertex(buffer, matrix, centerX, centerY, slice.color());
                vertex(buffer, matrix, centerX + Math.cos(a0) * radius, centerY + Math.sin(a0) * radius, slice.color());
                vertex(buffer, matrix, centerX + Math.cos(a1) * radius, centerY + Math.sin(a1) * radius, slice.color());
            }
            angle += sweep;
        }
        BufferRenderer.drawWithGlobalProgram(buffer.end());
        RenderSystem.disableBlend();
    }

    private static void vertex(BufferBuilder buffer, Matrix4f matrix, double x, double y, int argb) {
        Vector3f p = new Vector3f((float) x, (float) y, 0).mulPosition(matrix);
        buffer.vertex(p.x, p.y, p.z).color(argb);
    }

    /** Wires up rendering for each scripted fluid: still/flow textures, tint, and a translucent layer. */
    private static void registerFluidRendering(List<FluidSpec> fluids) {
        for (FluidSpec spec : fluids) {
            Fluid still = Registries.FLUID.get(Identifier.of(Minelark.MOD_ID, spec.id()));
            Fluid flowing = Registries.FLUID.get(Identifier.of(Minelark.MOD_ID, "flowing_" + spec.id()));
            Identifier stillTex = Identifier.of(Minelark.MOD_ID, "block/" + spec.id() + "_still");
            Identifier flowTex = Identifier.of(Minelark.MOD_ID, "block/" + spec.id() + "_flow");
            FluidRenderHandlerRegistry.INSTANCE.register(still, flowing,
                    new SimpleFluidRenderHandler(stillTex, flowTex, spec.tint()));
            BlockRenderLayerMap.INSTANCE.putFluids(RenderLayer.getTranslucent(), still, flowing);
        }
    }

    private static void registerClientEvents() {
        ClientLifecycleEvents.CLIENT_STARTED.register(client ->
                dispatch("minelark:client_started", Map.of(), Set.of(), false));

        ClientLifecycleEvents.CLIENT_STOPPING.register(client ->
                dispatch("minelark:client_stopping", Map.of(), Set.of(), false));

        // Gate the per-tick event on having a listener so an idle client pays nothing.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (clientEvents.hasListeners("minelark:client_tick")) {
                dispatch("minelark:client_tick", Map.of(), Set.of(), false);
            }
        });

        // Tooltip: `ctx.lines` is seeded with the current lines and can be reassigned to add, remove,
        // or reorder them. Untouched (same list object) => leave the vanilla tooltip exactly as-is.
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            if (!clientEvents.hasListeners("minelark:item_tooltip")) {
                return;
            }
            StarlarkList<MineText> seeded = StarlarkList.immutableCopyOf(
                    lines.stream().map(MinelarkClient::mineTextFromMc).toList());
            Map<String, Object> data = Map.of("item", itemStackView(stack), "lines", seeded);
            EventContext ctx = dispatch("minelark:item_tooltip", data, Set.of("lines"), false);
            applyTooltipLines(ctx, seeded, lines);
        });

        // Incoming signed player chat: cancel to hide (signed text can't be rewritten in place).
        ClientReceiveMessageEvents.ALLOW_CHAT.register((message, signed, sender, params, timestamp) ->
                !cancelled(dispatch("minelark:client_chat_received",
                        Map.of("message", message.getString()), Set.of("message"), true)));

        // Incoming system/game messages: cancel to hide, or reassign ctx.message to rewrite.
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            String original = message.getString();
            EventContext ctx = dispatch("minelark:client_chat_received",
                    Map.of("message", original), Set.of("message"), true);
            if (ctx == null) {
                return true;
            }
            if (ctx.isCancelled()) {
                pendingReceivedGame.remove();
                return false;
            }
            stash(pendingReceivedGame, original, ctx.editedString("message"));
            return true;
        });
        ClientReceiveMessageEvents.MODIFY_GAME.register((message, overlay) -> {
            String edited = pendingReceivedGame.get();
            pendingReceivedGame.remove();
            return edited != null ? Text.literal(edited) : message;
        });

        // Outgoing chat the player types: cancel to stop it, or reassign ctx.message to rewrite it.
        ClientSendMessageEvents.ALLOW_CHAT.register(message -> {
            EventContext ctx = dispatch("minelark:client_chat_sent",
                    Map.of("message", message), Set.of("message"), true);
            if (ctx == null) {
                return true;
            }
            if (ctx.isCancelled()) {
                pendingSentChat.remove();
                return false;
            }
            stash(pendingSentChat, message, ctx.editedString("message"));
            return true;
        });
        ClientSendMessageEvents.MODIFY_CHAT.register(message -> {
            String edited = pendingSentChat.get();
            pendingSentChat.remove();
            return edited != null ? edited : message;
        });
    }

    /** Records an edit for the MODIFY pass only when the callback actually changed the text. */
    private static void stash(ThreadLocal<String> slot, String original, String edited) {
        if (edited != null && !edited.equals(original)) {
            slot.set(edited);
        } else {
            slot.remove();
        }
    }

    private static boolean cancelled(EventContext ctx) {
        return ctx != null && ctx.isCancelled();
    }

    /** Rebuilds the tooltip from {@code ctx.lines} if a callback reassigned it; otherwise leaves it. */
    private static void applyTooltipLines(EventContext ctx, Object seeded, List<Text> lines) {
        if (ctx == null) {
            return;
        }
        Object result = ctx.field("lines");
        if (result == seeded || !(result instanceof Sequence<?> seq)) {
            return;  // untouched (or replaced with a non-list) => keep the vanilla lines
        }
        lines.clear();
        for (Object line : seq) {
            if (line instanceof MineText text) {
                lines.add(Minelark.toMcText(text));
            } else if (line instanceof String s) {
                lines.add(Text.literal(s));
            } else {
                lines.add(Text.literal(String.valueOf(line)));
            }
        }
    }

    /**
     * Fires {@code id} into both the local and the pushed client callbacks (local first), returning the
     * shared, possibly-mutated ctx, or null if neither set is listening. A pushed script sees the same
     * client events as a local one, within its granted sandbox.
     */
    private static EventContext dispatch(String id, Map<String, Object> data, Set<String> editable, boolean cancellable) {
        boolean local = clientEvents.hasListeners(id);
        boolean pushed = pushedEvents.hasListeners(id);
        if (!local && !pushed) {
            return null;
        }
        EventContext ctx = new EventContext(id, data, editable, cancellable);
        if (local) {
            clientEvents.fire(id, ctx, Minelark.SCRIPT_LOG);
        }
        if (pushed) {
            pushedEvents.fire(id, ctx, Minelark.SCRIPT_LOG);
        }
        return ctx;
    }

    // --- the `client` namespace, reading MinecraftClient live ---

    private static final ClientAccess CLIENT_ACCESS = new ClientAccess() {
        @Override
        public PlayerView player() {
            ClientPlayerEntity player = MinecraftClient.getInstance().player;
            return player == null ? null : playerView(player);
        }

        @Override
        public LevelView world() {
            World world = MinecraftClient.getInstance().world;
            return world == null ? null : levelView(world);
        }

        @Override
        public void sendChat(String message) {
            ClientPlayerEntity player = MinecraftClient.getInstance().player;
            if (player == null) {
                return;
            }
            if (message.startsWith("/")) {
                player.networkHandler.sendChatCommand(message.substring(1));
            } else {
                player.networkHandler.sendChatMessage(message);
            }
        }

        @Override
        public void showMessage(MineText message) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.inGameHud != null) {
                mc.inGameHud.getChatHud().addMessage(Minelark.toMcText(message));
            }
        }
    };

    private static PlayerView playerView(ClientPlayerEntity player) {
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
                        CLIENT_ACCESS.showMessage(message);  // local-only, like the rest of the client API
                    }

                    @Override
                    public void give(String itemId, int count) {
                        Minelark.LOGGER.warn("Minelark client: player.give() is server-only; ignored on the client");
                    }

                    @Override
                    public void teleport(double x, double y, double z) {
                        Minelark.LOGGER.warn("Minelark client: player.teleport() is server-only; ignored on the client");
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

    /** Converts a Minecraft tooltip line to a {@link MineText}, preserving its top-level style. */
    private static MineText mineTextFromMc(Text text) {
        MineText result = MineText.literal(text.getString());
        Style style = text.getStyle();
        try {
            if (style.getColor() != null) {
                result = result.color(style.getColor().getName());
            }
        } catch (EvalException ignored) {
            // an unrecognised colour name just means no colour; keep the plain text
        }
        if (style.isBold()) {
            result = result.bold(true);
        }
        if (style.isItalic()) {
            result = result.italic(true);
        }
        if (style.isUnderlined()) {
            result = result.underline(true);
        }
        if (style.isStrikethrough()) {
            result = result.strikethrough(true);
        }
        if (style.isObfuscated()) {
            result = result.obfuscated(true);
        }
        return result;
    }
}
