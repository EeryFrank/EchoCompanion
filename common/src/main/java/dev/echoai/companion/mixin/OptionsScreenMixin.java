package dev.echoai.companion.mixin;

import dev.echoai.companion.client.gui.EchoSettingsScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(OptionsScreen.class)
public abstract class OptionsScreenMixin extends Screen {
    protected OptionsScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void echoCompanion$addSettingsButton(CallbackInfo callbackInfo) {
        addRenderableWidget(Button.builder(
                Component.translatable("screen.echo_companion.options_button"),
                button -> minecraft.setScreen(new EchoSettingsScreen((Screen) (Object) this))
        ).bounds(width - 156, 6, 150, 20).build());
    }
}
