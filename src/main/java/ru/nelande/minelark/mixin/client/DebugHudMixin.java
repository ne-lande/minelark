package ru.nelande.minelark.mixin.client;

import net.minecraft.client.gui.hud.DebugHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.nelande.minelark.client.MinelarkClient;

import java.util.List;

/**
 * Appends the client scripts' {@code debug.set(...)} lines to the F3 overlay's left column. There is
 * no Fabric event for this, so a mixin on {@link DebugHud#getLeftText()} adds the lines to the list
 * the vanilla method just built (it is a fresh mutable list, safe to extend).
 */
@Mixin(DebugHud.class)
public class DebugHudMixin {
    @Inject(method = "getLeftText", at = @At("RETURN"))
    private void minelark$appendScriptLines(CallbackInfoReturnable<List<String>> cir) {
        List<String> lines = MinelarkClient.debugLines();
        if (!lines.isEmpty()) {
            List<String> left = cir.getReturnValue();
            left.add("");
            left.addAll(lines);
        }
    }
}
