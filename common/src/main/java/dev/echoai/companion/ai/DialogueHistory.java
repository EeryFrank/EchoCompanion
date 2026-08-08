package dev.echoai.companion.ai;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A small, thread-safe in-memory history for one game conversation.
 */
public final class DialogueHistory {
    public static final int DEFAULT_MAX_MESSAGES = 10;

    private final int maxMessages;
    private final ArrayDeque<DialogueMessage> messages = new ArrayDeque<>();

    public DialogueHistory() {
        this(DEFAULT_MAX_MESSAGES);
    }

    public DialogueHistory(int maxMessages) {
        if (maxMessages < 1 || maxMessages > DialogueContext.MAX_HISTORY_MESSAGES) {
            throw new IllegalArgumentException(
                    "maxMessages must be between 1 and " + DialogueContext.MAX_HISTORY_MESSAGES
            );
        }
        this.maxMessages = maxMessages;
    }

    public synchronized void add(DialogueMessage message) {
        messages.addLast(Objects.requireNonNull(message, "message"));
        while (messages.size() > maxMessages) {
            messages.removeFirst();
        }
    }

    public void addUser(String content) {
        add(DialogueMessage.user(content));
    }

    public void addAssistant(String content) {
        add(DialogueMessage.assistant(content));
    }

    public synchronized List<DialogueMessage> snapshot() {
        return List.copyOf(new ArrayList<>(messages));
    }

    public synchronized int size() {
        return messages.size();
    }

    public synchronized void clear() {
        messages.clear();
    }
}
