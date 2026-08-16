package ru.nelande.minelark.script;

import net.starlark.java.annot.Param;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.Starlark;
import net.starlark.java.eval.StarlarkList;
import net.starlark.java.eval.StarlarkValue;

/**
 * The {@code mods} namespace (server + client scripts): ask which other mods are installed so a pack
 * can adapt to its environment - e.g. only add a recipe when the mod it depends on is present. Backed
 * by a {@link PlatformInfo} the adapter supplies (the {@code script} package stays MC-agnostic).
 */
public final class ModsApi implements StarlarkValue {
    private final PlatformInfo platform;

    public ModsApi(PlatformInfo platform) {
        this.platform = platform;
    }

    @StarlarkMethod(
            name = "loaded",
            doc = "Whether a mod with the given id is installed. Handy for compatibility branches, "
                    + "e.g. `if mods.loaded(\"create\"): ...`.",
            parameters = {@Param(name = "id", doc = "The mod id to check, e.g. `\"fabric\"`.")})
    public boolean loaded(String id) {
        return platform.isLoaded(id);
    }

    @StarlarkMethod(
            name = "version",
            doc = "The installed version of a mod as a string, or `None` if it is not loaded.",
            parameters = {@Param(name = "id", doc = "The mod id to look up.")})
    public Object version(String id) {
        String version = platform.version(id);
        return version != null ? version : Starlark.NONE;
    }

    @StarlarkMethod(
            name = "name",
            doc = "The human-readable name of a mod, or `None` if it is not loaded.",
            parameters = {@Param(name = "id", doc = "The mod id to look up.")})
    public Object name(String id) {
        String name = platform.name(id);
        return name != null ? name : Starlark.NONE;
    }

    @StarlarkMethod(
            name = "list",
            doc = "The ids of every loaded mod, sorted. Includes `minecraft` and Minelark itself.")
    public StarlarkList<String> list() {
        return StarlarkList.immutableCopyOf(platform.ids());
    }
}
