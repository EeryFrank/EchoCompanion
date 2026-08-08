package dev.echoai.companion.client.gui;

import dev.echoai.companion.ai.AiMode;
import dev.echoai.companion.ai.DialogueContext;
import dev.echoai.companion.ai.OpenAiCompatibleDialogueEngine;
import dev.echoai.companion.config.CompanionConfig;
import dev.echoai.companion.config.CompanionConfigStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.net.URI;
import java.time.Duration;

/** In-game settings for offline/remote mode and BYOK connection details. */
public final class EchoSettingsScreen extends Screen {
    private final Screen parent;
    private final CompanionConfigStore store = CompanionConfigStore.getInstance();
    private AiMode mode;
    private boolean fallback;
    private boolean rememberKey;
    private boolean revealKey;
    private EditBox endpointBox;
    private EditBox modelBox;
    private EditBox keyBox;
    private Button modeButton;
    private Button fallbackButton;
    private Button rememberButton;
    private Button revealButton;
    private Button testButton;
    private String status = "";
    private int statusColor = 0xFFB8C7D9;

    public EchoSettingsScreen(Screen parent) {
        super(Component.translatable("screen.echo_companion.settings.title"));
        this.parent = parent;
        CompanionConfig config = store.get();
        mode = config.mode();
        fallback = config.fallback();
        rememberKey = config.rememberKey();
    }

    @Override
    protected void init() {
        CompanionConfig config = store.get();
        int panelWidth = Math.min(420, width - 30);
        int left = (width - panelWidth) / 2;
        int top = Math.max(28, (height - 230) / 2);

        modeButton = addRenderableWidget(Button.builder(modeLabel(), button -> {
            mode = mode == AiMode.SCRIPTED ? AiMode.REMOTE : AiMode.SCRIPTED;
            button.setMessage(modeLabel());
        }).bounds(left, top, panelWidth, 20).build());

        endpointBox = new EditBox(font, left, top + 40, panelWidth, 20,
                Component.translatable("screen.echo_companion.settings.endpoint"));
        endpointBox.setMaxLength(512);
        endpointBox.setValue(config.endpoint());
        addRenderableWidget(endpointBox);

        modelBox = new EditBox(font, left, top + 76, panelWidth, 20,
                Component.translatable("screen.echo_companion.settings.model"));
        modelBox.setMaxLength(160);
        modelBox.setValue(config.model());
        addRenderableWidget(modelBox);

        keyBox = new EditBox(font, left, top + 112, panelWidth - 76, 20,
                Component.translatable("screen.echo_companion.settings.api_key"));
        keyBox.setMaxLength(512);
        keyBox.setValue(config.apiKey());
        updateKeyFormatter();
        addRenderableWidget(keyBox);

        revealButton = addRenderableWidget(Button.builder(revealLabel(), button -> {
            revealKey = !revealKey;
            button.setMessage(revealLabel());
            updateKeyFormatter();
        }).bounds(left + panelWidth - 70, top + 112, 70, 20).build());

        fallbackButton = addRenderableWidget(Button.builder(fallbackLabel(), button -> {
            fallback = !fallback;
            button.setMessage(fallbackLabel());
        }).bounds(left, top + 140, panelWidth / 2 - 3, 20).build());

        rememberButton = addRenderableWidget(Button.builder(rememberLabel(), button -> {
            rememberKey = !rememberKey;
            button.setMessage(rememberLabel());
        }).bounds(left + panelWidth / 2 + 3, top + 140, panelWidth / 2 - 3, 20).build());

        testButton = addRenderableWidget(Button.builder(
                Component.translatable("screen.echo_companion.settings.test"),
                button -> testConnection()
        ).bounds(left, top + 188, panelWidth / 3 - 3, 20).build());

        addRenderableWidget(Button.builder(
                Component.translatable("screen.echo_companion.settings.save"),
                button -> save()
        ).bounds(left + panelWidth / 3 + 2, top + 188, panelWidth / 3 - 3, 20).build());

        addRenderableWidget(Button.builder(
                Component.translatable("gui.cancel"),
                button -> onClose()
        ).bounds(left + panelWidth * 2 / 3 + 4, top + 188, panelWidth / 3 - 4, 20).build());
    }

    private Component modeLabel() {
        return Component.translatable(
                mode == AiMode.REMOTE
                        ? "screen.echo_companion.settings.mode_remote"
                        : "screen.echo_companion.settings.mode_scripted"
        );
    }

    private Component fallbackLabel() {
        return Component.translatable(
                fallback ? "screen.echo_companion.settings.fallback_on" : "screen.echo_companion.settings.fallback_off"
        );
    }

    private Component rememberLabel() {
        return Component.translatable(
                rememberKey ? "screen.echo_companion.settings.remember_on" : "screen.echo_companion.settings.remember_off"
        );
    }

    private Component revealLabel() {
        return Component.translatable(
                revealKey ? "screen.echo_companion.settings.hide" : "screen.echo_companion.settings.show"
        );
    }

    private void updateKeyFormatter() {
        if (keyBox == null) {
            return;
        }
        keyBox.setFormatter((value, cursor) -> FormattedCharSequence.forward(
                revealKey ? value : "•".repeat(value.length()),
                Style.EMPTY
        ));
    }

    private CompanionConfig readForm() {
        return store.get().toBuilder()
                .mode(mode)
                .endpoint(endpointBox.getValue().trim())
                .model(modelBox.getValue().trim())
                .apiKey(keyBox.getValue())
                .fallback(fallback)
                .rememberKey(rememberKey)
                .build();
    }

    private void save() {
        try {
            CompanionConfig config = readForm();
            store.save(config);
            status = Component.translatable("screen.echo_companion.settings.saved").getString();
            statusColor = 0xFF7FE59A;
        } catch (RuntimeException failure) {
            status = safeValidationMessage(failure);
            statusColor = 0xFFFF7777;
        }
    }

    private void testConnection() {
        final CompanionConfig config;
        try {
            config = readForm();
        } catch (RuntimeException failure) {
            status = safeValidationMessage(failure);
            statusColor = 0xFFFF7777;
            return;
        }

        status = Component.translatable("screen.echo_companion.settings.testing").getString();
        statusColor = 0xFFFFD966;
        testButton.active = false;
        new OpenAiCompatibleDialogueEngine(
                URI.create(config.endpoint()),
                config.apiKey(),
                config.model(),
                Duration.ofSeconds(config.timeoutSeconds())
        ).reply(new DialogueContext("Player", "Reply with OK only."))
                .whenComplete((result, failure) -> Minecraft.getInstance().execute(() -> {
                    testButton.active = true;
                    if (failure == null) {
                        status = Component.translatable("screen.echo_companion.settings.test_ok").getString();
                        statusColor = 0xFF7FE59A;
                    } else {
                        status = Component.translatable("screen.echo_companion.settings.test_failed").getString();
                        statusColor = 0xFFFF7777;
                    }
                }));
    }

    private static String safeValidationMessage(Throwable failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank() || message.length() > 180) {
            return "Invalid configuration.";
        }
        return message;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        int panelWidth = Math.min(420, width - 30);
        int left = (width - panelWidth) / 2;
        int top = Math.max(28, (height - 230) / 2);
        graphics.drawCenteredString(font, title, width / 2, top - 18, 0xFFFFFFFF);
        graphics.drawString(font, Component.translatable("screen.echo_companion.settings.endpoint"), left, top + 29, 0xFFC9D6E4);
        graphics.drawString(font, Component.translatable("screen.echo_companion.settings.model"), left, top + 65, 0xFFC9D6E4);
        graphics.drawString(font, Component.translatable("screen.echo_companion.settings.api_key"), left, top + 101, 0xFFC9D6E4);
        graphics.drawCenteredString(font,
                Component.translatable("screen.echo_companion.settings.key_warning"),
                width / 2,
                top + 165,
                0xFFFFB86C);
        if (!status.isBlank()) {
            graphics.drawCenteredString(font, status, width / 2, top + 176, statusColor);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
