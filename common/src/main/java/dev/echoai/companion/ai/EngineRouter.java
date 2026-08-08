package dev.echoai.companion.ai;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * Runtime-selectable router with an explicit, honest offline fallback when the
 * remote engine is missing or fails.
 */
public final class EngineRouter implements DialogueEngine {
    private final DialogueEngine scriptedEngine;
    private final AtomicReference<AiMode> mode;
    private volatile DialogueEngine remoteEngine;

    public EngineRouter(DialogueEngine remoteEngine) {
        this(new ScriptedDialogueEngine(), remoteEngine, AiMode.SCRIPTED);
    }

    public EngineRouter(DialogueEngine scriptedEngine, DialogueEngine remoteEngine, AiMode initialMode) {
        this.scriptedEngine = Objects.requireNonNull(scriptedEngine, "scriptedEngine");
        this.remoteEngine = remoteEngine;
        this.mode = new AtomicReference<>(Objects.requireNonNull(initialMode, "initialMode"));
    }

    public AiMode mode() {
        return mode.get();
    }

    public void setMode(AiMode newMode) {
        mode.set(Objects.requireNonNull(newMode, "newMode"));
    }

    /** Replaces remote credentials/endpoint atomically from the caller's perspective. */
    public void setRemoteEngine(DialogueEngine newRemoteEngine) {
        remoteEngine = newRemoteEngine;
    }

    @Override
    public CompletableFuture<DialogueResult> reply(DialogueContext context) {
        return reply(mode.get(), context);
    }

    public CompletableFuture<DialogueResult> reply(AiMode requestedMode, DialogueContext context) {
        Objects.requireNonNull(requestedMode, "requestedMode");
        Objects.requireNonNull(context, "context");

        if (requestedMode == AiMode.SCRIPTED) {
            return invoke(scriptedEngine, context);
        }

        DialogueEngine selectedRemote = remoteEngine;
        if (selectedRemote == null) {
            return fallback(context, new RemoteUnavailableException());
        }

        return invoke(selectedRemote, context)
                .<CompletableFuture<DialogueResult>>handle((result, failure) -> {
                    if (failure == null && result != null) {
                        return CompletableFuture.completedFuture(new DialogueResult(
                                result.text(),
                                AiMode.REMOTE,
                                result.actualMode(),
                                result.fallback(),
                                result.notice()
                        ));
                    }
                    Throwable cause = failure == null
                            ? new RemoteUnavailableException()
                            : unwrap(failure);
                    return fallback(context, cause);
                })
                .thenCompose(Function.identity());
    }

    private CompletableFuture<DialogueResult> fallback(DialogueContext context, Throwable remoteFailure) {
        String safeReason = safeReason(remoteFailure);
        return invoke(scriptedEngine, context)
                .handle((scriptedResult, scriptedFailure) -> {
                    if (scriptedFailure != null || scriptedResult == null) {
                        throw new CompletionException(new IllegalStateException(
                                "Both remote and offline scripted dialogue engines failed."
                        ));
                    }
                    return DialogueResult.remoteFallback(scriptedResult.text(), safeReason);
                });
    }

    private static CompletableFuture<DialogueResult> invoke(
            DialogueEngine engine,
            DialogueContext context
    ) {
        try {
            CompletableFuture<DialogueResult> future = engine.reply(context);
            if (future == null) {
                return CompletableFuture.failedFuture(
                        new IllegalStateException("Dialogue engine returned a null future.")
                );
            }
            return future;
        } catch (RuntimeException failure) {
            return CompletableFuture.failedFuture(failure);
        }
    }

    private static String safeReason(Throwable failure) {
        if (failure instanceof RemoteDialogueException remoteFailure) {
            return remoteFailure.getMessage();
        }
        if (failure instanceof RemoteUnavailableException) {
            return "Remote AI is not configured.";
        }
        return "Remote AI request failed.";
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static final class RemoteUnavailableException extends RuntimeException {
        private RemoteUnavailableException() {
            super("Remote AI is not configured.");
        }
    }
}
