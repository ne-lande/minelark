package ru.nelande.minelark.mixin;

import net.minecraft.world.World;
import net.minecraft.world.explosion.Explosion;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.nelande.minelark.Minelark;

/**
 * Fires the {@code minelark:explosion} notification whenever an explosion processes its blast (there
 * is no Fabric API event for explosions). {@code collectBlocksAndDamageEntities} is the single
 * server-side chokepoint every explosion runs through. Notification only - not cancellable.
 */
@Mixin(Explosion.class)
public abstract class ExplosionMixin {

    @Shadow
    @Final
    private World world;

    @Inject(method = "collectBlocksAndDamageEntities", at = @At("HEAD"))
    private void minelark$onExplode(CallbackInfo ci) {
        if (world.isClient) {
            return;
        }
        Explosion self = (Explosion) (Object) this;
        Minelark.fireExplosion(world, self.getPosition(), self.getPower());
    }
}
