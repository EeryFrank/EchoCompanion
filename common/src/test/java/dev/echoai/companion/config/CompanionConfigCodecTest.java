package dev.echoai.companion.config;

import dev.echoai.companion.ai.AiMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CompanionConfigCodecTest {
    private final CompanionConfigCodec codec = new CompanionConfigCodec();

    @Test
    void roundTripsEveryFieldWhenRememberKeyIsEnabled() {
        CompanionConfig original = CompanionConfig.builder()
                .mode(AiMode.REMOTE)
                .endpoint("https://example.com/v1/chat/completions")
                .model("example-model")
                .apiKey("sk-round-trip")
                .fallback(false)
                .timeoutSeconds(47)
                .maxHistory(33)
                .rememberKey(true)
                .build();

        assertEquals(original, codec.decode(codec.encode(original)));
    }

    @Test
    void omitsApiKeyFromJsonWhenRememberKeyIsDisabled() {
        String secret = "sk-must-not-touch-disk";
        CompanionConfig config = CompanionConfig.builder()
                .apiKey(secret)
                .rememberKey(false)
                .build();

        String json = codec.encode(config);

        assertFalse(json.contains("apiKey"));
        assertFalse(json.contains(secret));
        assertEquals("", codec.decode(json).apiKey());
    }

    @Test
    void ignoresLegacyOrInjectedApiKeyWhenRememberKeyIsDisabled() {
        CompanionConfig decoded = codec.decode("""
                {
                  "apiKey": "sk-ignore-me",
                  "rememberKey": false
                }
                """);

        assertEquals("", decoded.apiKey());
        assertFalse(decoded.rememberKey());
    }

    @Test
    void fillsMissingPropertiesFromDefaults() {
        CompanionConfig decoded = codec.decode("{}");

        assertEquals(CompanionConfig.defaults(), decoded);
    }

    @Test
    void decodingErrorsAreSanitizedAndDropTheirCause() {
        String secret = "sk-never-in-exception";
        CompanionConfigFormatException exception = assertThrows(
                CompanionConfigFormatException.class,
                () -> codec.decode("""
                        {
                          "apiKey": "%s",
                          "rememberKey": true,
                          "maxHistory": "not-a-number"
                        }
                        """.formatted(secret))
        );

        assertFalse(exception.getMessage().contains(secret));
        assertNull(exception.getCause());
    }

    @Test
    void rejectsDisallowedEndpointWithoutEchoingSource() {
        String endpoint = "http://remote.example/api";
        CompanionConfigFormatException exception = assertThrows(
                CompanionConfigFormatException.class,
                () -> codec.decode("""
                        {"endpoint": "%s"}
                        """.formatted(endpoint))
        );

        assertFalse(exception.getMessage().contains(endpoint));
        assertNull(exception.getCause());
    }
}
