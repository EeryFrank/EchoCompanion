package dev.echoai.companion.mixin;

import dev.echoai.companion.client.gui.EchoDialogueScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin extends Screen {
    protected PauseScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void echoCompanion$addDialogueButton(CallbackInfo callbackInfo) {
        addRenderableWidget(Button.builder(
                Component.translatable("screen.echo_companion.pause_button"),
                button -> minecraft.setScreen(new EchoDialogueScreen((Screen) (Object) this))
        ).bounds(width - 126, 6, 120, 20).build());
    }
}
