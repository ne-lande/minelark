package ru.nelande.minelark.mixin;

import net.minecraft.resource.ResourcePackManager;
import net.minecraft.resource.ResourcePackProvider;
import net.minecraft.resource.VanillaDataPackProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import ru.nelande.minelark.pack.GeneratedAssetPackProvider;
import ru.nelande.minelark.pack.GeneratedPackProvider;

import java.util.Arrays;

/**
 * Adds Minelark's generated packs to the {@link ResourcePackManager}: the generated <b>datapack</b>
 * (tags/recipes/loot) to the datapack manager, and the generated <b>resource pack</b> (models,
 * blockstates, textures) to the client asset manager.
 *
 * <p>The manager is built with a varargs list of providers; the datapack manager is the one that
 * includes a {@link VanillaDataPackProvider}. Any other manager is an asset manager - and since the
 * generated resource pack is only ever produced on the client, injecting the asset provider
 * elsewhere (e.g. on a dedicated server) is harmless: {@code createProfile()} returns null there.
 */
@Mixin(ResourcePackManager.class)
public class ResourcePackManagerMixin {
    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true)
    private static ResourcePackProvider[] minelark$injectGeneratedPack(ResourcePackProvider[] providers) {
        boolean isDataManager = Arrays.stream(providers).anyMatch(p -> p instanceof VanillaDataPackProvider);
        ResourcePackProvider extra = isDataManager ? new GeneratedPackProvider() : new GeneratedAssetPackProvider();
        ResourcePackProvider[] extended = Arrays.copyOf(providers, providers.length + 1);
        extended[providers.length] = extra;
        return extended;
    }
}
