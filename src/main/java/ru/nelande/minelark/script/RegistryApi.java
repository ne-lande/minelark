package ru.nelande.minelark.script;

import net.starlark.java.annot.Param;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.Starlark;
import net.starlark.java.eval.StarlarkList;
import net.starlark.java.eval.StarlarkValue;

/**
 * The {@code registry} namespace (server + client scripts): check whether an item/block/entity/fluid
 * exists and list the ids other mods registered - the read-only, reflection-free way to discover the
 * game's content. Ids without a namespace default to {@code minecraft:} (the same convention the
 * {@code recipes} namespace uses). Backed by a {@link RegistryAccess} the adapter supplies.
 */
public final class RegistryApi implements StarlarkValue {
    private final RegistryAccess registry;

    public RegistryApi(RegistryAccess registry) {
        this.registry = registry;
    }

    @StarlarkMethod(
            name = "item_exists",
            doc = "Whether an item with the given id is registered. A bare id defaults to `minecraft:`.",
            parameters = {@Param(name = "id", doc = "The item id, e.g. `\"diamond\"` or `\"create:cogwheel\"`.")})
    public boolean itemExists(String id) {
        return registry.has(RegistryAccess.Kind.ITEM, resolve(id));
    }

    @StarlarkMethod(
            name = "block_exists",
            doc = "Whether a block with the given id is registered. A bare id defaults to `minecraft:`.",
            parameters = {@Param(name = "id", doc = "The block id, e.g. `\"stone\"`.")})
    public boolean blockExists(String id) {
        return registry.has(RegistryAccess.Kind.BLOCK, resolve(id));
    }

    @StarlarkMethod(
            name = "entity_exists",
            doc = "Whether an entity type with the given id is registered. A bare id defaults to `minecraft:`.",
            parameters = {@Param(name = "id", doc = "The entity type id, e.g. `\"creeper\"`.")})
    public boolean entityExists(String id) {
        return registry.has(RegistryAccess.Kind.ENTITY_TYPE, resolve(id));
    }

    @StarlarkMethod(
            name = "fluid_exists",
            doc = "Whether a fluid with the given id is registered. A bare id defaults to `minecraft:`.",
            parameters = {@Param(name = "id", doc = "The fluid id, e.g. `\"water\"`.")})
    public boolean fluidExists(String id) {
        return registry.has(RegistryAccess.Kind.FLUID, resolve(id));
    }

    @StarlarkMethod(
            name = "items",
            doc = "The ids of every registered item, sorted. Pass a `namespace` to list only one mod's "
                    + "items, e.g. `registry.items(\"minecraft\")`.",
            parameters = {@Param(
                    name = "namespace",
                    named = true,
                    defaultValue = "None",
                    doc = "A mod id to filter by, or `None` (the default) for all items.")})
    public StarlarkList<String> items(Object namespace) {
        return ids(RegistryAccess.Kind.ITEM, namespace);
    }

    @StarlarkMethod(
            name = "blocks",
            doc = "The ids of every registered block, sorted. Pass a `namespace` to list only one mod's blocks.",
            parameters = {@Param(
                    name = "namespace",
                    named = true,
                    defaultValue = "None",
                    doc = "A mod id to filter by, or `None` (the default) for all blocks.")})
    public StarlarkList<String> blocks(Object namespace) {
        return ids(RegistryAccess.Kind.BLOCK, namespace);
    }

    @StarlarkMethod(
            name = "entities",
            doc = "The ids of every registered entity type, sorted. Pass a `namespace` to list only one mod's.",
            parameters = {@Param(
                    name = "namespace",
                    named = true,
                    defaultValue = "None",
                    doc = "A mod id to filter by, or `None` (the default) for all entity types.")})
    public StarlarkList<String> entities(Object namespace) {
        return ids(RegistryAccess.Kind.ENTITY_TYPE, namespace);
    }

    @StarlarkMethod(
            name = "fluids",
            doc = "The ids of every registered fluid, sorted. Pass a `namespace` to list only one mod's fluids.",
            parameters = {@Param(
                    name = "namespace",
                    named = true,
                    defaultValue = "None",
                    doc = "A mod id to filter by, or `None` (the default) for all fluids.")})
    public StarlarkList<String> fluids(Object namespace) {
        return ids(RegistryAccess.Kind.FLUID, namespace);
    }

    private StarlarkList<String> ids(RegistryAccess.Kind kind, Object namespace) {
        String ns = namespace == Starlark.NONE ? "" : (String) namespace;
        return StarlarkList.immutableCopyOf(registry.ids(kind, ns));
    }

    /** A bare id (no {@code :}) defaults to the {@code minecraft:} namespace. */
    private static String resolve(String id) {
        return id.indexOf(':') >= 0 ? id : "minecraft:" + id;
    }
}
