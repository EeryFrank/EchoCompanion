package dev.echoai.companion.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class CompanionConfigStoreTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void missingFileLoadsDefaults() {
        CompanionConfigStore store = new CompanionConfigStore(temporaryDirectory.resolve("config.json"));

        assertEquals(CompanionConfig.defaults(), store.load());
    }

    @Test
    void saveKeepsTransientKeyInMemoryButNeverWritesIt() throws IOException {
        Path path = temporaryDirectory.resolve("config.json");
        String secret = "sk-runtime-only";
        CompanionConfig config = CompanionConfig.builder()
                .apiKey(secret)
                .rememberKey(false)
                .build();
        CompanionConfigStore store = new CompanionConfigStore(path);

        store.save(config);

        assertEquals(secret, store.get().apiKey());
        assertFalse(Files.readString(path, StandardCharsets.UTF_8).contains(secret));
        assertEquals("", new CompanionConfigStore(path).load().apiKey());
    }

    @Test
    void invalidFileFailsClosedToDefaults() throws IOException {
        Path path = temporaryDirectory.resolve("config.json");
        Files.writeString(path, "{not-json", StandardCharsets.UTF_8);
        CompanionConfigStore store = new CompanionConfigStore(path);

        assertEquals(CompanionConfig.defaults(), store.load());
    }
}
