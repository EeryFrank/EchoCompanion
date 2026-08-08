package dev.echoai.companion.config;

import dev.echoai.companion.ai.AiMode;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Objects;

/**
 * Immutable client-side settings for Echo Companion.
 *
 * <p>This class deliberately has a hand-written {@link #toString()} so an API
 * key can never be rendered by diagnostics.</p>
 */
public final class CompanionConfig {
    public static final String DEFAULT_ENDPOINT = "https://api.openai.com/v1/chat/completions";
    public static final String DEFAULT_MODEL = "gpt-4o-mini";
    public static final int DEFAULT_TIMEOUT_SECONDS = 30;
    public static final int DEFAULT_MAX_HISTORY = 12;

    private static final int MIN_TIMEOUT_SECONDS = 1;
    private static final int MAX_TIMEOUT_SECONDS = 300;
    private static final int MIN_MAX_HISTORY = 0;
    private static final int MAX_MAX_HISTORY = 200;

    private final AiMode mode;
    private final String endpoint;
    private final String model;
    private final String apiKey;
    private final boolean fallback;
    private final int timeoutSeconds;
    private final int maxHistory;
    private final boolean rememberKey;

    public CompanionConfig(
            AiMode mode,
            String endpoint,
            String model,
            String apiKey,
            boolean fallback,
            int timeoutSeconds,
            int maxHistory,
            boolean rememberKey
    ) {
        this.mode = Objects.requireNonNull(mode, "Mode is required.");
        this.endpoint = requireAllowedEndpoint(endpoint);
        this.model = requireNonBlank(model, "Model is required.");
        this.apiKey = apiKey == null ? "" : apiKey;
        this.fallback = fallback;
        this.timeoutSeconds = requireRange(
                timeoutSeconds,
                MIN_TIMEOUT_SECONDS,
                MAX_TIMEOUT_SECONDS,
                "Timeout must be between 1 and 300 seconds."
        );
        this.maxHistory = requireRange(
                maxHistory,
                MIN_MAX_HISTORY,
                MAX_MAX_HISTORY,
                "Maximum history must be between 0 and 200."
        );
        this.rememberKey = rememberKey;
    }

    public static CompanionConfig defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder(this);
    }

    public AiMode mode() {
        return mode;
    }

    public AiMode getMode() {
        return mode;
    }

    public String endpoint() {
        return endpoint;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String model() {
        return model;
    }

    public String getModel() {
        return model;
    }

    public String apiKey() {
        return apiKey;
    }

    public String getApiKey() {
        return apiKey;
    }

    public boolean fallback() {
        return fallback;
    }

    public boolean isFallback() {
        return fallback;
    }

    public int timeoutSeconds() {
        return timeoutSeconds;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public int maxHistory() {
        return maxHistory;
    }

    public int getMaxHistory() {
        return maxHistory;
    }

    public boolean rememberKey() {
        return rememberKey;
    }

    public boolean isRememberKey() {
        return rememberKey;
    }

    /**
     * Accepts HTTPS endpoints and HTTP endpoints on an exact loopback host.
     * Values are never included in validation exception messages.
     */
    public static boolean isEndpointAllowed(String value) {
        if (value == null || value.isBlank() || !value.equals(value.trim())) {
            return false;
        }

        final URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException exception) {
            return false;
        }

        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (scheme == null
                || host == null
                || uri.getRawUserInfo() != null
                || uri.getRawQuery() != null
                || uri.getRawFragment() != null) {
            return false;
        }

        String normalizedScheme = scheme.toLowerCase(Locale.ROOT);
        if (normalizedScheme.equals("https")) {
            return true;
        }
        if (!normalizedScheme.equals("http")) {
            return false;
        }

        String normalizedHost = host.toLowerCase(Locale.ROOT);
        return normalizedHost.equals("localhost")
                || normalizedHost.equals("127.0.0.1")
                || normalizedHost.equals("::1")
                || normalizedHost.equals("[::1]");
    }

    @Override
    public String toString() {
        return "CompanionConfig{" +
                "mode=" + mode +
                ", endpoint=<redacted>" +
                ", model='" + model + '\'' +
                ", apiKey=<redacted>" +
                ", fallback=" + fallback +
                ", timeoutSeconds=" + timeoutSeconds +
                ", maxHistory=" + maxHistory +
                ", rememberKey=" + rememberKey +
                '}';
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CompanionConfig that)) {
            return false;
        }
        return fallback == that.fallback
                && timeoutSeconds == that.timeoutSeconds
                && maxHistory == that.maxHistory
                && rememberKey == that.rememberKey
                && mode == that.mode
                && endpoint.equals(that.endpoint)
                && model.equals(that.model)
                && apiKey.equals(that.apiKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mode, endpoint, model, apiKey, fallback, timeoutSeconds, maxHistory, rememberKey);
    }

    private static String requireAllowedEndpoint(String value) {
        if (!isEndpointAllowed(value)) {
            throw new IllegalArgumentException(
                    "Endpoint must use HTTPS, or HTTP with localhost, 127.0.0.1, or [::1]."
            );
        }
        return value;
    }

    private static String requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static int requireRange(int value, int minimum, int maximum, String message) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    public static final class Builder {
        private AiMode mode = AiMode.SCRIPTED;
        private String endpoint = DEFAULT_ENDPOINT;
        private String model = DEFAULT_MODEL;
        private String apiKey = "";
        private boolean fallback = true;
        private int timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
        private int maxHistory = DEFAULT_MAX_HISTORY;
        private boolean rememberKey;

        private Builder() {
        }

        private Builder(CompanionConfig config) {
            mode = config.mode;
            endpoint = config.endpoint;
            model = config.model;
            apiKey = config.apiKey;
            fallback = config.fallback;
            timeoutSeconds = config.timeoutSeconds;
            maxHistory = config.maxHistory;
            rememberKey = config.rememberKey;
        }

        public Builder mode(AiMode value) {
            mode = value;
            return this;
        }

        public Builder endpoint(String value) {
            endpoint = value;
            return this;
        }

        public Builder model(String value) {
            model = value;
            return this;
        }

        public Builder apiKey(String value) {
            apiKey = value;
            return this;
        }

        public Builder fallback(boolean value) {
            fallback = value;
            return this;
        }

        public Builder timeoutSeconds(int value) {
            timeoutSeconds = value;
            return this;
        }

        public Builder maxHistory(int value) {
            maxHistory = value;
            return this;
        }

        public Builder rememberKey(boolean value) {
            rememberKey = value;
            return this;
        }

        public CompanionConfig build() {
            return new CompanionConfig(
                    mode,
                    endpoint,
                    model,
                    apiKey,
                    fallback,
                    timeoutSeconds,
                    maxHistory,
                    rememberKey
            );
        }
    }
}
