package dev.echoai.companion.config;

import dev.echoai.companion.ai.AiMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompanionConfigTest {
    @Test
    void defaultsToScriptedMode() {
        CompanionConfig config = CompanionConfig.defaults();

        assertEquals(AiMode.SCRIPTED, config.mode());
        assertEquals(CompanionConfig.DEFAULT_ENDPOINT, config.endpoint());
        assertEquals(CompanionConfig.DEFAULT_MODEL, config.model());
        assertEquals("", config.apiKey());
        assertTrue(config.fallback());
        assertEquals(CompanionConfig.DEFAULT_TIMEOUT_SECONDS, config.timeoutSeconds());
        assertEquals(CompanionConfig.DEFAULT_MAX_HISTORY, config.maxHistory());
        assertFalse(config.rememberKey());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://example.com/v1/chat/completions",
            "HTTPS://Example.com:8443/api",
            "http://localhost:8080/v1/chat/completions",
            "http://127.0.0.1:1234/api",
            "http://[::1]:11434/v1/chat/completions"
    })
    void allowsHttpsAndExactLoopbackHttp(String endpoint) {
        assertTrue(CompanionConfig.isEndpointAllowed(endpoint));
        assertEquals(endpoint, CompanionConfig.builder().endpoint(endpoint).build().endpoint());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://example.com/v1/chat/completions",
            "http://localhost.example.com/api",
            "http://127.0.0.2/api",
            "http://[::2]/api",
            "ftp://example.com/api",
            "example.com/api",
            " https://example.com/api",
            "https://user@example.com/api",
            "https://example.com/api?key=must-not-persist",
            "https://example.com/api#fragment"
    })
    void rejectsNonHttpsRemoteAndNonExactLoopbackHttp(String endpoint) {
        assertFalse(CompanionConfig.isEndpointAllowed(endpoint));
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> CompanionConfig.builder().endpoint(endpoint).build()
        );
        assertFalse(exception.getMessage().contains(endpoint));
    }

    @Test
    void toStringAlwaysRedactsApiKey() {
        String secret = "sk-test-do-not-print";
        CompanionConfig config = CompanionConfig.builder()
                .apiKey(secret)
                .rememberKey(true)
                .build();

        assertFalse(config.toString().contains(secret));
        assertFalse(config.toString().contains(config.endpoint()));
        assertTrue(config.toString().contains("apiKey=<redacted>"));
    }

    @Test
    void builderCanCopyForUiEdits() {
        CompanionConfig edited = CompanionConfig.defaults().toBuilder()
                .mode(AiMode.REMOTE)
                .timeoutSeconds(45)
                .maxHistory(24)
                .build();

        assertEquals(AiMode.REMOTE, edited.mode());
        assertEquals(45, edited.timeoutSeconds());
        assertEquals(24, edited.maxHistory());
    }
}
