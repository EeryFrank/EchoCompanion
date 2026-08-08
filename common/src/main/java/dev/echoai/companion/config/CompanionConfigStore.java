package dev.echoai.companion.config;

import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/** Thread-safe owner of the active client configuration. */
public final class CompanionConfigStore {
    private final Path path;
    private final CompanionConfigCodec codec;
    private volatile CompanionConfig current = CompanionConfig.defaults();

    public CompanionConfigStore(Path path) {
        this(path, new CompanionConfigCodec());
    }

    CompanionConfigStore(Path path, CompanionConfigCodec codec) {
        this.path = Objects.requireNonNull(path, "Configuration path is required.");
        this.codec = Objects.requireNonNull(codec, "Configuration codec is required.");
    }

    public static CompanionConfigStore getInstance() {
        return Holder.INSTANCE;
    }

    public CompanionConfig get() {
        return current;
    }

    public Path path() {
        return path;
    }

    /**
     * Loads the file when present. A missing, unreadable, or invalid file
     * safely selects defaults without echoing file contents to logs.
     */
    public synchronized CompanionConfig load() {
        try {
            current = CompanionConfigFiles.read(path)
                    .map(codec::decode)
                    .orElseGet(CompanionConfig::defaults);
        } catch (IOException | RuntimeException exception) {
            current = CompanionConfig.defaults();
        }
        return current;
    }

    /** Saves and then publishes the new value in memory. */
    public synchronized void save(CompanionConfig config) {
        Objects.requireNonNull(config, "Configuration is required.");
        try {
            CompanionConfigFiles.writeAtomically(path, codec.encode(config));
            current = config;
        } catch (IOException exception) {
            // The cause is intentionally omitted: diagnostics must never gain
            // access to content-bearing exceptions from future file providers.
            throw new CompanionConfigPersistenceException("Unable to save Echo Companion configuration.");
        }
    }

    private static Path defaultPath() {
        return CompanionConfigPaths.fromGameDirectory(Minecraft.getInstance().gameDirectory.toPath());
    }

    private static final class Holder {
        private static final CompanionConfigStore INSTANCE = new CompanionConfigStore(defaultPath());
    }
}
