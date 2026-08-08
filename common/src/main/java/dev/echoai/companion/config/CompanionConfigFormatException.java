package dev.echoai.companion.config;

/**
 * A deliberately sanitized decoding failure. The source JSON and nested Gson
 * exception are not retained because they may contain an API key.
 */
public final class CompanionConfigFormatException extends IllegalArgumentException {
    public CompanionConfigFormatException() {
        super("Invalid Echo Companion configuration.");
    }
}
