package io.github.ruok0214.ingametimer;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class TimerClient implements ClientModInitializer {
    final TimerClock clock = new TimerClock();
    final TimerSettings settings = TimerSettings.load();

    @Override
    public void onInitializeClient() {
        var category = KeyMapping.Category.register(TimerMod.id("main"));
        KeyMapping menu = registerKey("menu", GLFW.GLFW_KEY_F8, category);
        KeyMapping toggle = registerKey("toggle", GLFW.GLFW_KEY_F6, category);
        KeyMapping reset = registerKey("reset", GLFW.GLFW_KEY_F7, category);

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clock.changeWorld());
        ClientPlayNetworking.registerGlobalReceiver(TimerMod.WorldTick.TYPE,
                (payload, context) -> clock.observeTick(payload.tick()));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            updateCompletion(System.nanoTime());
            if (client.level == null) return;
            while (toggle.consumeClick()) toggle();
            while (reset.consumeClick()) clock.reset();
            if (menu.consumeClick()) client.gui.setScreen(new TimerScreen(this));
        });

        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, TimerMod.id("timer"), this::drawHud);
    }

    private static KeyMapping registerKey(String action, int key, KeyMapping.Category category) {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.in_game_timer." + action, InputConstants.Type.KEYSYM, key, category));
    }

    static Component text(String key, Object... args) {
        return Component.translatable("in_game_timer." + key, args);
    }

    void toggle() {
        long now = System.nanoTime();
        updateCompletion(now);
        if (clock.isRunning()) {
            clock.stop(now);
        } else {
            if (settings.countdown && clock.expired(now, settings.seconds)) clock.reset();
            clock.start(now);
        }
    }

    private void updateCompletion(long now) {
        if (settings.countdown && clock.isRunning() && clock.expired(now, settings.seconds)) clock.stop(now);
    }

    private void drawHud(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;
        long now = System.nanoTime();
        List<Component> lines = hudLines(now);
        if (lines.isEmpty()) return;

        int width = lines.stream().mapToInt(client.font::width).max().orElse(0);
        int height = lines.size() * 11 - 2;
        int x = settings.corner.right ? graphics.guiWidth() - width - 8 : 8;
        int y = settings.corner.bottom ? graphics.guiHeight() - height - 8 : 8;
        graphics.fill(x - 4, y - 4, x + width + 4, y + height + 4, 0xA0000000);
        for (Component line : lines) {
            graphics.text(client.font, line, x, y, 0xFFFFFFFF);
            y += 11;
        }
    }

    private List<Component> hudLines(long now) {
        long ticks = clock.elapsedTicks();
        long nanos = clock.elapsedNanos(now);
        if (settings.countdown) {
            ticks = Math.max(0, settings.seconds * (long) TimerClock.TICKS_PER_SECOND - ticks);
            nanos = Math.max(0, settings.seconds * TimerClock.NANOS_PER_SECOND - nanos);
        }

        List<Component> lines = new ArrayList<>(3);
        if (clock.hasTickClock()) {
            if (settings.gameTicks) lines.add(text("hud.game", ticks, formatTime(ticks * 50)));
            if (settings.redstoneTicks) lines.add(text("hud.redstone", ticks / 2 + (ticks % 2 == 0 ? "" : ".5")));
        } else if (settings.gameTicks || settings.redstoneTicks) {
            lines.add(text("hud.no_tick_source"));
        }
        if (settings.realTime) lines.add(text("hud.real", formatTime(nanos / 1_000_000)));
        return lines;
    }

    private static String formatTime(long millis) {
        long seconds = millis / 1000;
        return String.format(Locale.ROOT, "%02d:%02d:%02d.%03d",
                seconds / 3600, seconds / 60 % 60, seconds % 60, millis % 1000);
    }
}
