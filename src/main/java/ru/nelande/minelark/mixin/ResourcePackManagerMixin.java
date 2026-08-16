package ru.nelande.minelark.mixin;

import net.minecraft.resource.ResourcePackManager;
import net.minecraft.resource.ResourcePackProvider;
import net.minecraft.resource.VanillaDataPackProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import ru.nelande.minelark.pack.GeneratedPackProvider;

import java.util.Arrays;

/**
 * Adds Minelark's generated-pack provider to the <b>datapack</b> {@link ResourcePackManager}.
 *
 * <p>The manager is built with a varargs list of providers; the datapack manager is the one that
 * includes a {@link VanillaDataPackProvider}, which lets us leave the client asset manager alone.
 */
@Mixin(ResourcePackManager.class)
public class ResourcePackManagerMixin {
    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true)
    private static ResourcePackProvider[] minelark$injectGeneratedPack(ResourcePackProvider[] providers) {
        boolean isDataManager = Arrays.stream(providers).anyMatch(p -> p instanceof VanillaDataPackProvider);
        if (!isDataManager) {
            return providers;
        }
        ResourcePackProvider[] extended = Arrays.copyOf(providers, providers.length + 1);
        extended[providers.length] = new GeneratedPackProvider();
        return extended;
    }
}
