package io.github.ruok0214.ingametimer.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.ruok0214.ingametimer.TimerClient;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
abstract class KeyboardHandlerMixin {
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void inGameTimer$handleKey(long window, int action, KeyEvent event, CallbackInfo callback) {
        if (TimerClient.handleInput(window, InputConstants.getKey(event), action, event.modifiers())) {
            callback.cancel();
        }
    }
}
