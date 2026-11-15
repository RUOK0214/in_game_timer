package io.github.ruok0214.ingametimer.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.ruok0214.ingametimer.TimerClient;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
abstract class MouseHandlerMixin {
    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void inGameTimer$handleButton(long window, MouseButtonInfo button, int action, CallbackInfo callback) {
        if (TimerClient.handleInput(window, InputConstants.Type.MOUSE.getOrCreate(button.button()),
                action, button.modifiers())) {
            callback.cancel();
        }
    }
}
