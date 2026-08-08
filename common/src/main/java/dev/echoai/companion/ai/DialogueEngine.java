package dev.echoai.companion.ai;

import java.util.concurrent.CompletableFuture;

/**
 * Non-blocking dialogue API shared by the offline and remote implementations.
 */
@FunctionalInterface
public interface DialogueEngine {
    CompletableFuture<DialogueResult> reply(DialogueContext context);

    /**
     * Readable alias for integrations that prefer a generation-style name.
     */
    default CompletableFuture<DialogueResult> generateReply(DialogueContext context) {
        return reply(context);
    }
}
