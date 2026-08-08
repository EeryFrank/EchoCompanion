package dev.echoai.companion.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Optional;

/** Pure Java file access kept separate from JSON encoding. */
public final class CompanionConfigFiles {
    private CompanionConfigFiles() {
    }

    public static Optional<String> read(Path path) throws IOException {
        Path target = requirePath(path);
        if (Files.notExists(target)) {
            return Optional.empty();
        }
        return Optional.of(Files.readString(target, StandardCharsets.UTF_8));
    }

    /**
     * Writes to a sibling temporary file and atomically replaces the target.
     * If the file system does not support atomic moves, no partial target is
     * installed and an exception is returned to the caller.
     */
    public static void writeAtomically(Path path, String content) throws IOException {
        Path target = requirePath(path).toAbsolutePath().normalize();
        Path parent = target.getParent();
        if (parent == null) {
            throw new IOException("Configuration path has no parent directory.");
        }

        Files.createDirectories(parent);
        String fileName = target.getFileName().toString();
        String prefix = "." + fileName + ".";
        if (prefix.length() < 3) {
            prefix = ".echo-config.";
        }

        Path temporary = Files.createTempFile(parent, prefix, ".tmp");
        boolean installed = false;
        try {
            Files.writeString(
                    temporary,
                    content,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE
            );
            try {
                Files.move(
                        temporary,
                        target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                );
            } catch (AtomicMoveNotSupportedException exception) {
                throw new IOException("Atomic configuration replacement is not supported.");
            }
            installed = true;
        } finally {
            if (!installed) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private static Path requirePath(Path path) {
        if (path == null) {
            throw new IllegalArgumentException("Configuration path is required.");
        }
        return path;
    }
}
