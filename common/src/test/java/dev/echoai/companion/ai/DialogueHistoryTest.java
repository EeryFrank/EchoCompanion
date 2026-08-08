package dev.echoai.companion.ai;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DialogueHistoryTest {
    @Test
    void evictsOldestMessagesAtConfiguredLimit() {
        DialogueHistory history = new DialogueHistory(3);
        history.addUser("one");
        history.addAssistant("two");
        history.addUser("three");
        history.addAssistant("four");

        assertEquals(3, history.size());
        assertEquals(
                List.of("two", "three", "four"),
                history.snapshot().stream().map(DialogueMessage::content).toList()
        );
    }

    @Test
    void refusesAnUnboundedHistory() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new DialogueHistory(DialogueContext.MAX_HISTORY_MESSAGES + 1)
        );
    }
}
