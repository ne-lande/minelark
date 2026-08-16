package ru.nelande.minelark.pack;

import net.minecraft.resource.ResourcePackProfile;
import net.minecraft.resource.ResourcePackProvider;

import java.util.function.Consumer;

/**
 * Offers Minelark's generated data pack to the server's datapack manager. Injected into the
 * manager's provider list by {@code ResourcePackManagerMixin}.
 */
public final class GeneratedPackProvider implements ResourcePackProvider {
    @Override
    public void register(Consumer<ResourcePackProfile> consumer) {
        ResourcePackProfile profile = GeneratedDataPack.createProfile();
        if (profile != null) {
            consumer.accept(profile);
        }
    }
}
