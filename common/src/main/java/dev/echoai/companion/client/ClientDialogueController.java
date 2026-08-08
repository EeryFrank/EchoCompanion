package dev.echoai.companion.client;

import dev.echoai.companion.ai.AiMode;
import dev.echoai.companion.ai.DialogueContext;
import dev.echoai.companion.ai.DialogueEngine;
import dev.echoai.companion.ai.DialogueHistory;
import dev.echoai.companion.ai.DialogueResult;
import dev.echoai.companion.ai.EngineRouter;
import dev.echoai.companion.ai.OpenAiCompatibleDialogueEngine;
import dev.echoai.companion.ai.ScriptedDialogueEngine;
import dev.echoai.companion.config.CompanionConfig;
import dev.echoai.companion.config.CompanionConfigStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/** Owns the local conversation state. No API key ever crosses a game packet. */
public final class ClientDialogueController {
    private static final ClientDialogueController INSTANCE = new ClientDialogueController();
    private static final String SYSTEM_PROMPT = "You are Echo, a friendly original Minecraft companion. "
            + "Reply in the player's language, keep answers concise, and never claim that you changed the game world.";

    private final DialogueHistory history = new DialogueHistory(DialogueContext.MAX_HISTORY_MESSAGES);
    private final AtomicBoolean pending = new AtomicBoolean();
    private volatile DialogueResult lastResult = DialogueResult.scripted("Echo is ready.");

    private ClientDialogueController() {
    }

    public static ClientDialogueController getInstance() {
        return INSTANCE;
    }

    public CompletableFuture<DialogueResult> ask(String userMessage) {
        String normalized = userMessage == null ? "" : userMessage.trim();
        if (normalized.isEmpty()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Message must not be blank."));
        }
        if (normalized.length() > 500) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Message is longer than 500 characters."));
        }
        if (!pending.compareAndSet(false, true)) {
            return CompletableFuture.failedFuture(new IllegalStateException("A reply is already in progress."));
        }

        CompanionConfig config = CompanionConfigStore.getInstance().get();
        DialogueContext context = new DialogueContext(
                playerName(),
                normalized,
                history.snapshot(),
                SYSTEM_PROMPT,
                collectGameContext()
        );
        history.addUser(normalized);

        DialogueEngine engine;
        try {
            engine = createEngine(config);
        } catch (RuntimeException failure) {
            pending.set(false);
            return CompletableFuture.failedFuture(failure);
        }

        return engine.reply(context).whenComplete((result, failure) -> {
            pending.set(false);
            if (failure == null && result != null) {
                history.addAssistant(result.text());
                lastResult = result;
            }
        });
    }

    public void clearHistory() {
        history.clear();
    }

    public boolean isPending() {
        return pending.get();
    }

    public DialogueResult lastResult() {
        return lastResult;
    }

    private static DialogueEngine createEngine(CompanionConfig config) {
        if (config.mode() == AiMode.SCRIPTED) {
            return new ScriptedDialogueEngine();
        }

        DialogueEngine remote = new OpenAiCompatibleDialogueEngine(
                URI.create(config.endpoint()),
                config.apiKey(),
                config.model(),
                Duration.ofSeconds(config.timeoutSeconds())
        );
        if (!config.fallback()) {
            return remote;
        }
        return new EngineRouter(new ScriptedDialogueEngine(), remote, AiMode.REMOTE);
    }

    private static String playerName() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player == null ? DialogueContext.DEFAULT_PLAYER_NAME : player.getGameProfile().getName();
    }

    private static Map<String, String> collectGameContext() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        Level level = minecraft.level;
        if (player == null || level == null) {
            return Map.of();
        }

        Map<String, String> attributes = new LinkedHashMap<>();
        ResourceKey<Level> dimension = level.dimension();
        attributes.put("dimension", dimension.location().toString());
        attributes.put("position", String.format(
                Locale.ROOT,
                "%.1f, %.1f, %.1f",
                player.getX(),
                player.getY(),
                player.getZ()
        ));
        attributes.put("health", String.format(
                Locale.ROOT,
                "%.1f / %.1f",
                player.getHealth(),
                player.getMaxHealth()
        ));
        attributes.put("time", Long.toString(level.getDayTime() % 24000L));
        attributes.put("weather", level.isThundering() ? "thunder" : level.isRaining() ? "rain" : "clear");

        BlockPos position = player.blockPosition();
        level.getBiome(position).unwrapKey().ifPresent(key -> attributes.put("biome", key.location().toString()));
        return Map.copyOf(attributes);
    }
}
