package dev.echoai.companion.ai;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DialogueContextTest {
    @Test
    void keepsOnlyTheMostRecentHistoryAndCopiesInputs() {
        List<DialogueMessage> sourceHistory = new ArrayList<>();
        for (int index = 0; index < 20; index++) {
            sourceHistory.add(DialogueMessage.user("message-" + index));
        }
        Map<String, String> sourceAttributes = new LinkedHashMap<>();
        sourceAttributes.put("biome", "plains");

        DialogueContext context = new DialogueContext(
                " Alex ",
                " hello ",
                sourceHistory,
                " be brief ",
                sourceAttributes
        );
        sourceHistory.clear();
        sourceAttributes.clear();

        assertEquals("Alex", context.playerName());
        assertEquals("hello", context.userMessage());
        assertEquals("be brief", context.systemPrompt());
        assertEquals(DialogueContext.MAX_HISTORY_MESSAGES, context.history().size());
        assertEquals("message-8", context.history().getFirst().content());
        assertEquals("message-19", context.history().getLast().content());
        assertEquals(Map.of("biome", "plains"), context.attributes());
        assertThrows(UnsupportedOperationException.class,
                () -> context.history().add(DialogueMessage.user("mutate")));
        assertThrows(UnsupportedOperationException.class,
                () -> context.attributes().put("weather", "rain"));
    }

    @Test
    void rejectsBlankCurrentMessage() {
        assertThrows(IllegalArgumentException.class, () -> DialogueContext.of("   "));
    }

    @Test
    void rejectsOversizedHistoryEntries() {
        assertThrows(
                IllegalArgumentException.class,
                () -> DialogueMessage.assistant("x".repeat(DialogueMessage.MAX_CONTENT_CHARACTERS + 1))
        );
    }
}
