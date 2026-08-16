package ru.nelande.minelark.mixin;

import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.nelande.minelark.Minelark;
import ru.nelande.minelark.script.EventContext;

/**
 * Fires the {@code minelark:command} event before a command runs (there is no Fabric API event for
 * command execution). Cancelling blocks the command; reassigning {@code ctx.command} re-runs the
 * edited string instead. A thread-local guard stops the re-dispatch from re-entering this handler.
 */
@Mixin(CommandManager.class)
public abstract class CommandManagerMixin {

    @Unique
    private static final ThreadLocal<Boolean> minelark$reentry = ThreadLocal.withInitial(() -> false);

    @Inject(method = "executeWithPrefix", at = @At("HEAD"), cancellable = true)
    private void minelark$onCommand(ServerCommandSource source, String command, CallbackInfo ci) {
        if (minelark$reentry.get()) {
            return;
        }
        EventContext ctx = Minelark.fireCommand(source, command);
        if (ctx == null) {
            return;
        }
        if (ctx.isCancelled()) {
            ci.cancel();
            return;
        }
        String edited = ctx.editedString("command");
        if (edited != null && !edited.equals(command)) {
            ci.cancel();
            minelark$reentry.set(true);
            try {
                ((CommandManager) (Object) this).executeWithPrefix(source, edited);
            } finally {
                minelark$reentry.set(false);
            }
        }
    }
}
