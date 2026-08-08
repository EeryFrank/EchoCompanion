package dev.echoai.companion.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompanionConfigFilesTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void constructsPathUnderGameConfigDirectory() {
        assertEquals(
                temporaryDirectory.resolve("config").resolve("echo_companion-client.json"),
                CompanionConfigPaths.fromGameDirectory(temporaryDirectory)
        );
    }

    @Test
    void readingMissingFileReturnsEmpty() throws IOException {
        assertTrue(CompanionConfigFiles.read(temporaryDirectory.resolve("missing.json")).isEmpty());
    }

    @Test
    void atomicallyCreatesAndReplacesFileWithoutLeavingTemporaryFiles() throws IOException {
        Path target = temporaryDirectory.resolve("nested").resolve("settings.json");

        CompanionConfigFiles.writeAtomically(target, "first");
        CompanionConfigFiles.writeAtomically(target, "second");

        assertEquals("second", CompanionConfigFiles.read(target).orElseThrow());
        try (var children = Files.list(target.getParent())) {
            assertEquals(1, children.count());
        }
    }
}
