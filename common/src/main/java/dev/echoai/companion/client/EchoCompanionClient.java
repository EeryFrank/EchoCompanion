package dev.echoai.companion.client;

import dev.echoai.companion.config.CompanionConfigStore;

import java.util.concurrent.atomic.AtomicBoolean;

public final class EchoCompanionClient {
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean();

    private EchoCompanionClient() {
    }

    public static void init() {
        if (INITIALIZED.compareAndSet(false, true)) {
            CompanionConfigStore.getInstance().load();
        }
    }
}
