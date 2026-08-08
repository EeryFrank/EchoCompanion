package dev.echoai.companion.neoforge;

import dev.echoai.companion.EchoCompanion;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.common.Mod;

@Mod(EchoCompanion.MOD_ID)
public final class EchoCompanionNeoForge {
    public EchoCompanionNeoForge() {
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ClientBootstrap.init();
        }
    }

    private static final class ClientBootstrap {
        private static void init() {
            dev.echoai.companion.client.EchoCompanionClient.init();
        }
    }
}
