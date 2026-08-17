package ru.nelande.minelark.fluid;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.FluidBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.fluid.FlowableFluid;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.Item;
import net.minecraft.state.StateManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;

/**
 * A minimal custom {@link FlowableFluid} for scripted fluids, modelled on vanilla {@code WaterFluid}.
 * The still/flowing pair, fluid block, and bucket item all reference each other, so they are linked
 * through a shared {@link Holder} that the adapter fills in after registration.
 */
public abstract class ScriptFluid extends FlowableFluid {
    /** The mutable link between a fluid's still/flowing forms, its block, and its bucket. */
    public static final class Holder {
        public FlowableFluid still;
        public FlowableFluid flowing;
        public Block block;
        public Item bucket;
    }

    protected final Holder holder;

    protected ScriptFluid(Holder holder) {
        this.holder = holder;
    }

    @Override
    public Fluid getStill() {
        return holder.still;
    }

    @Override
    public Fluid getFlowing() {
        return holder.flowing;
    }

    @Override
    public Item getBucketItem() {
        return holder.bucket;
    }

    @Override
    protected boolean isInfinite(World world) {
        return false;
    }

    @Override
    protected void beforeBreakingBlock(WorldAccess world, BlockPos pos, BlockState state) {
        BlockEntity blockEntity = state.hasBlockEntity() ? world.getBlockEntity(pos) : null;
        Block.dropStacks(state, world, pos, blockEntity);
    }

    @Override
    public int getMaxFlowDistance(WorldView world) {
        return 4;
    }

    @Override
    public int getLevelDecreasePerBlock(WorldView world) {
        return 1;
    }

    @Override
    public int getTickRate(WorldView world) {
        return 5;
    }

    @Override
    protected float getBlastResistance() {
        return 100.0F;
    }

    @Override
    public BlockState toBlockState(FluidState state) {
        return holder.block.getDefaultState().with(FluidBlock.LEVEL, getBlockStateLevel(state));
    }

    @Override
    public boolean matchesType(Fluid fluid) {
        return fluid == holder.still || fluid == holder.flowing;
    }

    @Override
    public boolean canBeReplacedWith(FluidState state, BlockView world, BlockPos pos, Fluid fluid, Direction direction) {
        return false;
    }

    /** The full-strength source form of the fluid. */
    public static final class Still extends ScriptFluid {
        public Still(Holder holder) {
            super(holder);
        }

        @Override
        public int getLevel(FluidState state) {
            return 8;
        }

        @Override
        public boolean isStill(FluidState state) {
            return true;
        }
    }

    /** The flowing form, which carries a level (1..8) as a blockstate property. */
    public static final class Flowing extends ScriptFluid {
        public Flowing(Holder holder) {
            super(holder);
        }

        @Override
        protected void appendProperties(StateManager.Builder<Fluid, FluidState> builder) {
            super.appendProperties(builder);
            builder.add(LEVEL);
        }

        @Override
        public int getLevel(FluidState state) {
            return state.get(LEVEL);
        }

        @Override
        public boolean isStill(FluidState state) {
            return false;
        }
    }
}
