package ru.nelande.minelark.client;

import net.fabricmc.api.ClientModInitializer;
import ru.nelande.minelark.Minelark;
import ru.nelande.minelark.script.StarlarkHost;

public final class MinelarkClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // Client scripts run once, on client startup.
        StarlarkHost.runClient(Minelark.scriptDir("client"), Minelark.SCRIPT_LOG);
    }
}
