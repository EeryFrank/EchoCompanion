package dev.echoai.companion.fabric;

import dev.echoai.companion.client.EchoCompanionClient;
import net.fabricmc.api.ClientModInitializer;

public final class EchoCompanionFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EchoCompanionClient.init();
    }
}
