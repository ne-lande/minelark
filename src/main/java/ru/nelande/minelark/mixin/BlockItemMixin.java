package ru.nelande.minelark.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.nelande.minelark.Minelark;

/**
 * Fires the {@code minelark:block_placed} event just before a player places a block (there is no
 * Fabric API event for placement). A cancelled event turns the placement into {@link ActionResult#FAIL}.
 */
@Mixin(BlockItem.class)
public abstract class BlockItemMixin {

    @Inject(
            method = "place(Lnet/minecraft/item/ItemPlacementContext;)Lnet/minecraft/util/ActionResult;",
            at = @At("HEAD"),
            cancellable = true)
    private void minelark$onPlace(ItemPlacementContext context, CallbackInfoReturnable<ActionResult> cir) {
        World world = context.getWorld();
        if (world.isClient || !(context.getPlayer() instanceof ServerPlayerEntity player)) {
            return;
        }
        BlockState state = ((BlockItem) (Object) this).getBlock().getDefaultState();
        if (Minelark.fireBlockPlaced(player, state, context.getBlockPos())) {
            cir.setReturnValue(ActionResult.FAIL);
        }
    }
}
