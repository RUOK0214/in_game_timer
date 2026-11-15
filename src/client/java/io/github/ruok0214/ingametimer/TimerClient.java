package io.github.ruok0214.ingametimer;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
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
    final TimerSettings.Store store = TimerSettings.load();
    final TimerSession game = new TimerSession("game", true, store, store.game);
    final TimerSession real = new TimerSession("real", false, store, store.real);
    private TimerSession menuRequested;
    private KeyBindings gameBindings;
    private KeyBindings realBindings;

    @Override
    public void onInitializeClient() {
        var category = KeyMapping.Category.register(TimerMod.id("main"));
        gameBindings = registerKeys("", category);
        realBindings = registerKeys("real_", category);

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            game.clock.changeWorld();
            real.clock.changeWorld();
            menuRequested = null;
        });
        ClientPlayNetworking.registerGlobalReceiver(TimerMod.WorldTick.TYPE,
                (payload, context) -> game.clock.observeTick(payload.tick()));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!store.legacyBindingsCleared) migrateBindings(client.options);
            long now = System.nanoTime();
            game.updateCompletion(now);
            real.updateCompletion(now);
            if (client.level == null) return;
            handleKeys(game, gameBindings);
            handleKeys(real, realBindings);
            if (menuRequested != null) {
                client.gui.setScreen(new TimerScreen(this, menuRequested));
                menuRequested = null;
            }
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> dispatcher.register(
                commands(literal("igt").requires(FabricClientCommandSource::attended), game)
                        .then(commands(literal("game"), game))
                        .then(commands(literal("real"), real))));
        TimerHud.register(this);
    }

    private void handleKeys(TimerSession timer, KeyBindings bindings) {
        while (bindings.toggle().consumeClick()) timer.toggle();
        while (bindings.reset().consumeClick()) timer.clock.reset();
        if (bindings.menu().consumeClick()) requestMenu(timer);
    }

    KeyBindings bindings(TimerSession timer) {
        return timer == game ? gameBindings : realBindings;
    }

    private static KeyBindings registerKeys(String prefix, KeyMapping.Category category) {
        return new KeyBindings(registerKey(prefix + "menu", category),
                registerKey(prefix + "toggle", category), registerKey(prefix + "reset", category));
    }

    private static KeyMapping registerKey(String action, KeyMapping.Category category) {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.in_game_timer." + action, InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), category));
    }

    private void migrateBindings(Options options) {
        boolean changed = clearLegacyBinding(gameBindings.menu(), GLFW.GLFW_KEY_F8);
        changed |= clearLegacyBinding(gameBindings.toggle(), GLFW.GLFW_KEY_F6);
        changed |= clearLegacyBinding(gameBindings.reset(), GLFW.GLFW_KEY_F7);
        if (changed) {
            KeyMapping.resetMapping();
            options.save();
        }
        store.legacyBindingsCleared = true;
        store.save();
    }

    private static boolean clearLegacyBinding(KeyMapping mapping, int oldKeyCode) {
        if (!KeyMappingHelper.getBoundKeyOf(mapping).equals(InputConstants.Type.KEYSYM.getOrCreate(oldKeyCode))) {
            return false;
        }
        mapping.setKey(InputConstants.UNKNOWN);
        return true;
    }

    private LiteralArgumentBuilder<FabricClientCommandSource> commands(
            LiteralArgumentBuilder<FabricClientCommandSource> root, TimerSession timer) {
        return root.executes(context -> requestMenu(timer))
                .then(literal("settings").executes(context -> requestMenu(timer)))
                .then(action("toggle", timer, timer::toggle, false))
                .then(action("start", timer, timer::start, false))
                .then(action("stop", timer, timer::stop, false))
                .then(action("resume", timer, timer::start, false))
                .then(action("reset", timer, timer.clock::reset, true));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> action(
            String name, TimerSession timer, Runnable run, boolean reset) {
        return literal(name).executes(context -> {
            run.run();
            String message = reset ? "command.reset" : timer.clock.isRunning() ? "command.running" : "command.stopped";
            context.getSource().sendFeedback(text("command.result", timer.title(), text(message)));
            return 1;
        });
    }

    int requestMenu(TimerSession timer) {
        menuRequested = timer;
        return 1;
    }

    static Component text(String key, Object... args) {
        return Component.translatable("in_game_timer." + key, args);
    }

    record KeyBindings(KeyMapping menu, KeyMapping toggle, KeyMapping reset) {}
}
