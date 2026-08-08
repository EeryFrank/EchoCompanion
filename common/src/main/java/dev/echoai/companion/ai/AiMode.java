package dev.echoai.companion.ai;

/**
 * Selects which dialogue implementation should answer a request.
 */
public enum AiMode {
    /** A deterministic, completely offline dialogue engine. */
    SCRIPTED,

    /** An OpenAI-compatible remote Chat Completions endpoint. */
    REMOTE
}
