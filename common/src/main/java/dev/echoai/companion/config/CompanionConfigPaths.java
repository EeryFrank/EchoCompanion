package dev.echoai.companion.config;

import java.nio.file.Path;

/** Pure Java path construction, separately testable from Minecraft. */
public final class CompanionConfigPaths {
    public static final String FILE_NAME = "echo_companion-client.json";

    private CompanionConfigPaths() {
    }

    public static Path fromGameDirectory(Path gameDirectory) {
        if (gameDirectory == null) {
            throw new IllegalArgumentException("Game directory is required.");
        }
        return gameDirectory.resolve("config").resolve(FILE_NAME);
    }
}
