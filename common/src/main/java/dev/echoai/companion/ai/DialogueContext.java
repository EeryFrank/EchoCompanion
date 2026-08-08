package dev.echoai.companion.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable input supplied to a {@link DialogueEngine}.
 *
 * <p>The history is always reduced to the most recent
 * {@value #MAX_HISTORY_MESSAGES} messages. This is a final safety bound even
 * when a caller does not use {@link DialogueHistory}.</p>
 */
public record DialogueContext(
        String playerName,
        String userMessage,
        List<DialogueMessage> history,
        String systemPrompt,
        Map<String, String> attributes
) {
    public static final int MAX_HISTORY_MESSAGES = 12;
    public static final String DEFAULT_PLAYER_NAME = "Player";

    public DialogueContext {
        playerName = normalizePlayerName(playerName);
        userMessage = requireText(userMessage, "userMessage");
        history = copyRecentHistory(history);
        systemPrompt = systemPrompt == null ? "" : systemPrompt.trim();
        attributes = copyAttributes(attributes);
    }

    public DialogueContext(String playerName, String userMessage) {
        this(playerName, userMessage, List.of(), "", Map.of());
    }

    public DialogueContext(String playerName, String userMessage, List<DialogueMessage> history) {
        this(playerName, userMessage, history, "", Map.of());
    }

    public static DialogueContext of(String userMessage) {
        return new DialogueContext(DEFAULT_PLAYER_NAME, userMessage);
    }

    public static DialogueContext of(String playerName, String userMessage) {
        return new DialogueContext(playerName, userMessage);
    }

    public DialogueContext withHistory(List<DialogueMessage> newHistory) {
        return new DialogueContext(playerName, userMessage, newHistory, systemPrompt, attributes);
    }

    private static String normalizePlayerName(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_PLAYER_NAME;
        }
        return value.trim();
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalized;
    }

    private static List<DialogueMessage> copyRecentHistory(List<DialogueMessage> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }

        int fromIndex = Math.max(0, source.size() - MAX_HISTORY_MESSAGES);
        List<DialogueMessage> copy = new ArrayList<>(source.size() - fromIndex);
        for (int index = fromIndex; index < source.size(); index++) {
            copy.add(Objects.requireNonNull(source.get(index), "history contains null"));
        }
        return List.copyOf(copy);
    }

    private static Map<String, String> copyAttributes(Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }

        Map<String, String> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            String normalizedKey = requireText(key, "attribute key");
            String normalizedValue = requireText(value, "attribute value");
            copy.put(normalizedKey, normalizedValue);
        });
        return Collections.unmodifiableMap(copy);
    }
}
