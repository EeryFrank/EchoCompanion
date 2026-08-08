package dev.echoai.companion.ai;

import java.util.Objects;

/**
 * A dialogue reply together with routing information suitable for displaying
 * an honest fallback state in the UI.
 */
public record DialogueResult(
        String text,
        AiMode requestedMode,
        AiMode actualMode,
        boolean fallback,
        String notice
) {
    public static final String FALLBACK_PREFIX = "[远程 AI 不可用，已回退到离线伪 AI] ";

    public DialogueResult {
        text = requireText(text, "text");
        requestedMode = Objects.requireNonNull(requestedMode, "requestedMode");
        actualMode = Objects.requireNonNull(actualMode, "actualMode");
        notice = notice == null ? "" : notice.trim();
    }

    public static DialogueResult scripted(String text) {
        return new DialogueResult(text, AiMode.SCRIPTED, AiMode.SCRIPTED, false, "");
    }

    public static DialogueResult remote(String text) {
        return new DialogueResult(text, AiMode.REMOTE, AiMode.REMOTE, false, "");
    }

    public static DialogueResult remoteFallback(String scriptedText, String safeReason) {
        String reason = safeReason == null || safeReason.isBlank()
                ? "Remote AI request failed."
                : safeReason.trim();
        return new DialogueResult(
                FALLBACK_PREFIX + requireText(scriptedText, "scriptedText"),
                AiMode.REMOTE,
                AiMode.SCRIPTED,
                true,
                "Remote AI unavailable; offline scripted fallback used. " + reason
        );
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
