package dev.echoai.companion.client.gui;

import dev.echoai.companion.ai.AiMode;
import dev.echoai.companion.ai.DialogueResult;
import dev.echoai.companion.client.ClientDialogueController;
import dev.echoai.companion.config.CompanionConfigStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Small original companion presence rendered without copied art assets. */
public final class EchoOverlayRenderer {
    private EchoOverlayRenderer() {
    }

    public static void render(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }

        ClientDialogueController controller = ClientDialogueController.getInstance();
        int width = 106;
        int height = 34;
        int x = graphics.guiWidth() - width - 6;
        int y = 6;
        graphics.fill(x, y, x + width, y + height, 0xB0101722);
        graphics.renderOutline(x, y, width, height, 0xFF4BA3C7);
        graphics.fill(x + 6, y + 7, x + 27, y + 28, 0xFFFFD966);
        graphics.drawCenteredString(minecraft.font, ":)", x + 16, y + 13, 0xFF2A2A2A);
        graphics.drawString(minecraft.font, Component.literal("Echo"), x + 34, y + 6, 0xFFFFFFFF, false);

        String state;
        int color;
        if (controller.isPending()) {
            state = Component.translatable("overlay.echo_companion.thinking").getString();
            color = 0xFFFFD966;
        } else {
            DialogueResult last = controller.lastResult();
            AiMode active = last.actualMode();
            if (last.text().equals("Echo is ready.")) {
                active = CompanionConfigStore.getInstance().get().mode();
            }
            state = Component.translatable(
                    active == AiMode.REMOTE
                            ? "overlay.echo_companion.remote"
                            : "overlay.echo_companion.scripted"
            ).getString();
            color = active == AiMode.REMOTE ? 0xFF7FE59A : 0xFFB8C7D9;
        }
        graphics.drawString(minecraft.font, state, x + 34, y + 19, color, false);
    }
}
