package dev.echoai.companion.client.gui;

import dev.echoai.companion.ai.AiMode;
import dev.echoai.companion.ai.DialogueResult;
import dev.echoai.companion.ai.RemoteDialogueException;
import dev.echoai.companion.client.ClientDialogueController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Local companion conversation UI. Remote calls are always asynchronous. */
public final class EchoDialogueScreen extends Screen {
    private final Screen parent;
    private final ClientDialogueController controller = ClientDialogueController.getInstance();
    private final List<ChatEntry> entries = new ArrayList<>();
    private EditBox input;
    private Button sendButton;
    private String status = "";

    public EchoDialogueScreen(Screen parent) {
        super(Component.translatable("screen.echo_companion.dialogue.title"));
        this.parent = parent;
        DialogueResult last = controller.lastResult();
        entries.add(new ChatEntry("Echo", last.text(), 0xFFFFD966));
    }

    @Override
    protected void init() {
        int left = Math.max(16, width / 2 - Math.min(260, width / 2 - 16));
        int panelWidth = Math.min(520, width - 32);
        int inputY = height - 50;

        input = new EditBox(font, left, inputY, panelWidth - 86, 20,
                Component.translatable("screen.echo_companion.dialogue.input"));
        input.setMaxLength(500);
        input.setHint(Component.translatable("screen.echo_companion.dialogue.input_hint"));
        addRenderableWidget(input);

        sendButton = addRenderableWidget(Button.builder(
                Component.translatable("screen.echo_companion.dialogue.send"),
                button -> send()
        ).bounds(left + panelWidth - 80, inputY, 80, 20).build());

        addRenderableWidget(Button.builder(
                Component.translatable("screen.echo_companion.dialogue.settings"),
                button -> minecraft.setScreen(new EchoSettingsScreen(this))
        ).bounds(left, height - 25, 100, 20).build());

        addRenderableWidget(Button.builder(
                Component.translatable("screen.echo_companion.dialogue.clear"),
                button -> {
                    controller.clearHistory();
                    entries.clear();
                    status = Component.translatable("screen.echo_companion.dialogue.cleared").getString();
                }
        ).bounds(left + panelWidth / 2 - 50, height - 25, 100, 20).build());

        addRenderableWidget(Button.builder(
                Component.translatable("gui.done"),
                button -> onClose()
        ).bounds(left + panelWidth - 100, height - 25, 100, 20).build());

        setInitialFocus(input);
    }

    private void send() {
        String message = input.getValue().trim();
        if (message.isEmpty() || controller.isPending()) {
            return;
        }

        entries.add(new ChatEntry(Component.translatable("screen.echo_companion.dialogue.you").getString(), message, 0xFF8FD3FF));
        input.setValue("");
        status = Component.translatable("screen.echo_companion.dialogue.thinking").getString();
        sendButton.active = false;

        controller.ask(message).whenComplete((result, failure) -> Minecraft.getInstance().execute(() -> {
            sendButton.active = true;
            if (failure != null) {
                entries.add(new ChatEntry("Echo", safeFailure(failure), 0xFFFF7777));
                status = Component.translatable("screen.echo_companion.dialogue.failed").getString();
                return;
            }

            entries.add(new ChatEntry("Echo", result.text(), result.fallback() ? 0xFFFFB86C : 0xFFFFD966));
            status = result.actualMode() == AiMode.REMOTE
                    ? Component.translatable("screen.echo_companion.mode.remote").getString()
                    : Component.translatable("screen.echo_companion.mode.scripted").getString();
        }));
    }

    private static String safeFailure(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof RemoteDialogueException) {
                return current.getMessage();
            }
            if (current.getCause() == null || current.getCause() == current) {
                break;
            }
            current = current.getCause();
        }

        // Only expose our own fixed validation messages. Transport-library
        // exceptions can contain a configured endpoint and must stay hidden.
        if (current instanceof IllegalArgumentException || current instanceof IllegalStateException) {
            String message = current.getMessage();
            if (message != null && !message.isBlank() && message.length() <= 180) {
                return message;
            }
        }
        return "AI request failed.";
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) && input.isFocused()) {
            send();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 12, 0xFFFFFFFF);

        int left = Math.max(16, width / 2 - Math.min(260, width / 2 - 16));
        int panelWidth = Math.min(520, width - 32);
        int top = 30;
        int bottom = height - 58;
        graphics.fill(left, top, left + panelWidth, bottom, 0xB0101722);
        graphics.renderOutline(left, top, panelWidth, bottom - top, 0xFF4BA3C7);

        List<RenderedLine> lines = wrappedLines(panelWidth - 20);
        int maxLines = Math.max(1, (bottom - top - 18) / font.lineHeight);
        int start = Math.max(0, lines.size() - maxLines);
        int y = top + 9;
        for (int index = start; index < lines.size(); index++) {
            RenderedLine line = lines.get(index);
            graphics.drawString(font, line.text(), left + 10, y, line.color(), false);
            y += font.lineHeight;
        }

        if (!status.isBlank()) {
            graphics.drawCenteredString(font, status, width / 2, height - 62, 0xFFB8C7D9);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private List<RenderedLine> wrappedLines(int maxWidth) {
        List<RenderedLine> lines = new ArrayList<>();
        for (ChatEntry entry : entries) {
            Component text = Component.literal(entry.speaker() + ": " + entry.text());
            for (FormattedCharSequence part : font.split(text, maxWidth)) {
                lines.add(new RenderedLine(part, entry.color()));
            }
            lines.add(new RenderedLine(FormattedCharSequence.EMPTY, 0xFFFFFFFF));
        }
        return lines;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    private record ChatEntry(String speaker, String text, int color) {
    }

    private record RenderedLine(FormattedCharSequence text, int color) {
    }
}
