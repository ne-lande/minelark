package ru.nelande.minelark.script;

import java.util.List;

/**
 * Everything the startup scripts declared: the content the Minecraft adapter should register.
 */
public record StartupResult(List<ItemSpec> items, List<BlockSpec> blocks, List<FluidSpec> fluids) {
}
