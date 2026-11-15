package io.github.ruok0214.ingametimer;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

public final class TimerClient implements ClientModInitializer {
    final TimerClock clock = new TimerClock();
    final TimerSettings settings = TimerSettings.load();
    private boolean menuRequested;

    @Override
    public void onInitializeClient() {
        var category = KeyMapping.Category.register(TimerMod.id("main"));
        KeyMapping menu = registerKey("menu", category);
        KeyMapping toggle = registerKey("toggle", category);
        KeyMapping reset = registerKey("reset", category);

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            clock.changeWorld();
            menuRequested = false;
        });
        ClientPlayNetworking.registerGlobalReceiver(TimerMod.WorldTick.TYPE,
                (payload, context) -> clock.observeTick(payload.tick()));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!settings.legacyBindingsCleared) migrateBindings(client.options, menu, toggle, reset);
            updateCompletion(System.nanoTime());
            if (client.level == null) return;
            while (toggle.consumeClick()) toggle();
            while (reset.consumeClick()) clock.reset();
            if (menu.consumeClick() || menuRequested) {
                menuRequested = false;
                client.gui.setScreen(new TimerScreen(this));
            }
        });

        registerCommands();
        new TimerHud(this).register();
    }

    private static KeyMapping registerKey(String action, KeyMapping.Category category) {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.in_game_timer." + action, InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), category));
    }

    private void migrateBindings(Options options, KeyMapping menu, KeyMapping toggle, KeyMapping reset) {
        boolean changed = clearLegacyBinding(menu, GLFW.GLFW_KEY_F8);
        changed |= clearLegacyBinding(toggle, GLFW.GLFW_KEY_F6);
        changed |= clearLegacyBinding(reset, GLFW.GLFW_KEY_F7);
        if (changed) {
            KeyMapping.resetMapping();
            options.save();
        }
        settings.legacyBindingsCleared = true;
        settings.save();
    }

    private static boolean clearLegacyBinding(KeyMapping mapping, int oldKeyCode) {
        if (!KeyMappingHelper.getBoundKeyOf(mapping).equals(InputConstants.Type.KEYSYM.getOrCreate(oldKeyCode))) {
            return false;
        }
        mapping.setKey(InputConstants.UNKNOWN);
        return true;
    }

    private void registerCommands() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> dispatcher.register(literal("igt")
                .requires(FabricClientCommandSource::attended)
                .executes(context -> requestMenu())
                .then(literal("settings").executes(context -> requestMenu()))
                .then(literal("toggle").executes(context -> {
                    toggle();
                    return reportState(context.getSource());
                }))
                .then(literal("start").executes(context -> {
                    start();
                    return reportState(context.getSource());
                }))
                .then(literal("stop").executes(context -> {
                    clock.stop(System.nanoTime());
                    return reportState(context.getSource());
                }))
                .then(literal("resume").executes(context -> {
                    start();
                    return reportState(context.getSource());
                }))
                .then(literal("reset").executes(context -> {
                    clock.reset();
                    context.getSource().sendFeedback(text("command.reset"));
                    return 1;
                }))));
    }

    private int reportState(FabricClientCommandSource source) {
        source.sendFeedback(text(clock.isRunning() ? "command.running" : "command.stopped"));
        return 1;
    }

    int requestMenu() {
        menuRequested = true;
        return 1;
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
            start();
        }
    }

    private void start() {
        if (clock.isRunning()) return;
        long now = System.nanoTime();
        if (settings.countdown && clock.expired(now, settings.seconds)) clock.reset();
        clock.start(now);
    }

    Component toggleLabel() {
        if (clock.isRunning()) return text("stop");
        long now = System.nanoTime();
        boolean completed = settings.countdown && clock.expired(now, settings.seconds);
        return text(clock.elapsedNanos(now) > 0 && !completed ? "resume" : "start");
    }

    private void updateCompletion(long now) {
        if (settings.countdown && clock.isRunning() && clock.expired(now, settings.seconds)) clock.stop(now);
    }

}
