package dev.echoai.companion.ai;

import java.util.Objects;

/**
 * One immutable entry in a dialogue's short history.
 */
public record DialogueMessage(DialogueRole role, String content) {
    public static final int MAX_CONTENT_CHARACTERS = 8_000;

    public DialogueMessage {
        role = Objects.requireNonNull(role, "role");
        content = requireText(content, "content");
        if (content.length() > MAX_CONTENT_CHARACTERS) {
            throw new IllegalArgumentException("content is too long");
        }
    }

    public static DialogueMessage user(String content) {
        return new DialogueMessage(DialogueRole.USER, content);
    }

    public static DialogueMessage assistant(String content) {
        return new DialogueMessage(DialogueRole.ASSISTANT, content);
    }

    private static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalized;
    }
}
