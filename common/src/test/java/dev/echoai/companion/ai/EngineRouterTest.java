package dev.echoai.companion.ai;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EngineRouterTest {
    @Test
    void selectedScriptedModeNeverCallsRemoteEngine() {
        AtomicInteger remoteCalls = new AtomicInteger();
        DialogueEngine remote = context -> {
            remoteCalls.incrementAndGet();
            return CompletableFuture.completedFuture(DialogueResult.remote("remote"));
        };
        EngineRouter router = new EngineRouter(new ScriptedDialogueEngine(), remote, AiMode.SCRIPTED);

        DialogueResult result = router.reply(DialogueContext.of("hello")).join();

        assertEquals(0, remoteCalls.get());
        assertEquals(AiMode.SCRIPTED, result.actualMode());
        assertFalse(result.fallback());
    }

    @Test
    void returnsRemoteReplyWhenRemoteEngineSucceeds() {
        DialogueEngine remote = context ->
                CompletableFuture.completedFuture(DialogueResult.remote("real answer"));
        EngineRouter router = new EngineRouter(new ScriptedDialogueEngine(), remote, AiMode.REMOTE);

        DialogueResult result = router.reply(DialogueContext.of("question")).join();

        assertEquals("real answer", result.text());
        assertEquals(AiMode.REMOTE, result.requestedMode());
        assertEquals(AiMode.REMOTE, result.actualMode());
        assertFalse(result.fallback());
    }

    @Test
    void clearlyMarksFallbackWhenRemoteFutureFails() {
        DialogueEngine remote = context -> CompletableFuture.failedFuture(
                RemoteDialogueException.httpStatus(503)
        );
        EngineRouter router = new EngineRouter(new ScriptedDialogueEngine(), remote, AiMode.REMOTE);

        DialogueResult result = router.reply(DialogueContext.of("你好")).join();

        assertTrue(result.fallback());
        assertEquals(AiMode.REMOTE, result.requestedMode());
        assertEquals(AiMode.SCRIPTED, result.actualMode());
        assertTrue(result.text().startsWith(DialogueResult.FALLBACK_PREFIX));
        assertTrue(result.notice().contains("503"));
    }

    @Test
    void missingRemoteConfigurationAlsoFallsBack() {
        EngineRouter router = new EngineRouter(new ScriptedDialogueEngine(), null, AiMode.REMOTE);

        DialogueResult result = router.reply(DialogueContext.of("help")).join();

        assertTrue(result.fallback());
        assertTrue(result.notice().contains("not configured"));
    }

    @Test
    void arbitraryRemoteExceptionDetailsAreNotCopiedToUserFacingNotice() {
        String secret = "sk-do-not-expose";
        DialogueEngine remote = context -> CompletableFuture.failedFuture(
                new IllegalStateException("failed with " + secret)
        );
        EngineRouter router = new EngineRouter(new ScriptedDialogueEngine(), remote, AiMode.REMOTE);

        DialogueResult result = router.reply(DialogueContext.of("hello")).join();

        assertFalse(result.notice().contains(secret));
        assertEquals("Remote AI unavailable; offline scripted fallback used. Remote AI request failed.", result.notice());
    }
}
