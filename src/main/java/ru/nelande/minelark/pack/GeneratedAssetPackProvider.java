package ru.nelande.minelark.pack;

import net.minecraft.resource.ResourcePackProfile;
import net.minecraft.resource.ResourcePackProvider;

import java.util.function.Consumer;

/**
 * Offers Minelark's generated resource pack to the client's asset manager. Injected into the
 * manager's provider list by {@code ResourcePackManagerMixin} (client asset manager only).
 */
public final class GeneratedAssetPackProvider implements ResourcePackProvider {
    @Override
    public void register(Consumer<ResourcePackProfile> consumer) {
        ResourcePackProfile profile = GeneratedResourcePack.createProfile();
        if (profile != null) {
            consumer.accept(profile);
        }
    }
}
