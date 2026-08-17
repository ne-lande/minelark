package ru.nelande.minelark.fluid;

import net.minecraft.block.FluidBlock;
import net.minecraft.fluid.FlowableFluid;

/** {@link FluidBlock} has only a protected constructor; this exposes it for scripted fluids. */
public final class ScriptFluidBlock extends FluidBlock {
    public ScriptFluidBlock(FlowableFluid fluid, Settings settings) {
        super(fluid, settings);
    }
}
