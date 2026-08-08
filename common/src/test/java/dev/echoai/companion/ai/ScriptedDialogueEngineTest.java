package dev.echoai.companion.ai;

import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScriptedDialogueEngineTest {
    private final ScriptedDialogueEngine engine = new ScriptedDialogueEngine();

    @Test
    void answersCommonChineseGreetingWithPlayerContext() {
        DialogueResult result = engine.reply(new DialogueContext("小明", "你好")).join();

        assertEquals(AiMode.SCRIPTED, result.actualMode());
        assertTrue(result.text().contains("小明"));
        assertTrue(result.text().contains("离线"));
    }

    @Test
    void answersEnglishLocationQuestionFromSuppliedGameContext() {
        DialogueContext context = new DialogueContext(
                "Alex",
                "Where am I?",
                List.of(),
                "",
                Map.of("dimension", "overworld", "biome", "plains", "position", "10, 64, -2")
        );

        String reply = engine.reply(context).join().text();

        assertTrue(reply.contains("overworld"));
        assertTrue(reply.contains("plains"));
        assertTrue(reply.contains("10, 64, -2"));
    }

    @Test
    void usesRecentConversationForAnUnknownQuestion() {
        DialogueContext context = new DialogueContext(
                "Alex",
                "然后呢？",
                List.of(
                        DialogueMessage.user("我们去找钻石吧"),
                        DialogueMessage.assistant("好啊")
                )
        );

        assertTrue(engine.reply(context).join().text().contains("我们去找钻石吧"));
    }

    @Test
    void doesNotTreatHiInsideAnotherEnglishWordAsAGreeting() {
        String reply = engine.reply(DialogueContext.of("This needs more detail")).join().text();

        assertTrue(reply.contains("offline scripted mode"));
    }

    @Test
    void implementationHasNoHttpClientDependency() {
        boolean hasNetworkField = List.of(ScriptedDialogueEngine.class.getDeclaredFields()).stream()
                .anyMatch(field -> HttpClient.class.isAssignableFrom(field.getType()));

        assertFalse(hasNetworkField);
    }
}
