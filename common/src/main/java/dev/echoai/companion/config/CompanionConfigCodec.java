package dev.echoai.companion.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.echoai.companion.ai.AiMode;

import java.util.Locale;

/** Pure Java JSON encoding and decoding with no Minecraft runtime dependency. */
public final class CompanionConfigCodec {
    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .setPrettyPrinting()
            .create();

    public String encode(CompanionConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("Configuration is required.");
        }

        JsonObject json = new JsonObject();
        json.addProperty("mode", config.mode().name());
        json.addProperty("endpoint", config.endpoint());
        json.addProperty("model", config.model());
        if (config.rememberKey() && !config.apiKey().isEmpty()) {
            json.addProperty("apiKey", config.apiKey());
        }
        json.addProperty("fallback", config.fallback());
        json.addProperty("timeoutSeconds", config.timeoutSeconds());
        json.addProperty("maxHistory", config.maxHistory());
        json.addProperty("rememberKey", config.rememberKey());
        return GSON.toJson(json) + System.lineSeparator();
    }

    public CompanionConfig decode(String source) {
        try {
            JsonElement element = GSON.fromJson(source, JsonElement.class);
            if (element == null || !element.isJsonObject()) {
                throw new CompanionConfigFormatException();
            }

            JsonObject json = element.getAsJsonObject();
            CompanionConfig defaults = CompanionConfig.defaults();
            boolean rememberKey = readBoolean(json, "rememberKey", defaults.rememberKey());

            return CompanionConfig.builder()
                    .mode(readMode(json, defaults.mode()))
                    .endpoint(readString(json, "endpoint", defaults.endpoint()))
                    .model(readString(json, "model", defaults.model()))
                    .apiKey(rememberKey ? readString(json, "apiKey", "") : "")
                    .fallback(readBoolean(json, "fallback", defaults.fallback()))
                    .timeoutSeconds(readInt(json, "timeoutSeconds", defaults.timeoutSeconds()))
                    .maxHistory(readInt(json, "maxHistory", defaults.maxHistory()))
                    .rememberKey(rememberKey)
                    .build();
        } catch (CompanionConfigFormatException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            // Never attach the Gson exception: its message can contain source data.
            throw new CompanionConfigFormatException();
        }
    }

    private static AiMode readMode(JsonObject json, AiMode fallback) {
        if (!json.has("mode")) {
            return fallback;
        }
        String raw = json.get("mode").getAsString();
        return AiMode.valueOf(raw.toUpperCase(Locale.ROOT));
    }

    private static String readString(JsonObject json, String name, String fallback) {
        if (!json.has(name) || json.get(name).isJsonNull()) {
            return fallback;
        }
        return json.get(name).getAsString();
    }

    private static boolean readBoolean(JsonObject json, String name, boolean fallback) {
        if (!json.has(name)) {
            return fallback;
        }
        return json.get(name).getAsBoolean();
    }

    private static int readInt(JsonObject json, String name, int fallback) {
        if (!json.has(name)) {
            return fallback;
        }
        return json.get(name).getAsInt();
    }
}
