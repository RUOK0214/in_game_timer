package io.github.ruok0214.ingametimer;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.fabric.mixin.client.gametest.input.KeyboardHandlerAccessor;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;

import java.util.Arrays;
import java.util.function.Predicate;

@SuppressWarnings("UnstableApiUsage")
public final class TimerGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        check(TimerHud.redstoneValue(5, true).equals("2"), "Running redstone display must use whole ticks");
        check(TimerHud.redstoneValue(5, false).equals("2.5"), "Stopped redstone display must show the half tick");
        check(TimerHud.redstoneValue(6, false).equals("3"), "Whole ticks must not gain a decimal suffix");
        TimerClient owner = (TimerClient) FabricLoader.getInstance()
                .getEntrypointContainers("client", ClientModInitializer.class).stream()
                .filter(entry -> entry.getProvider().getMetadata().getId().equals("in_game_timer"))
                .findFirst().orElseThrow().getEntrypoint();
        TimerSession timer = owner.game;
        TimerSession real = owner.real;
        try (var world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksDownload();
            context.waitFor(client -> timer.clock.isAvailable());
            verifyChatControls(context, owner);
            verifyTimerSettings(context, owner);

            world.getServer().runCommand("tick freeze");
            world.getServer().runCommand("tick rate 40");
            context.waitTicks(5);
            command(context, "start");
            context.waitFor(client -> timer.clock.isRunning());
            context.runOnClient(client -> check(!real.clock.isRunning(), "Game start affected the real clock"));
            command(context, "real start");
            context.waitFor(client -> real.displayTicks(System.nanoTime()) >= 4);
            context.runOnClient(client -> {
                check(timer.displayTicks(System.nanoTime()) == 0, "Frozen server advanced the game ticks");
                check(timer.displayNanos(System.nanoTime()) == 0, "Frozen server advanced the game clock time");
                check(real.displayNanos(System.nanoTime()) >= 4 * TimerClock.NANOS_PER_TICK,
                        "Real time and its tick units must both advance during freeze");
            });

            world.getServer().runCommand("tick step 4");
            context.waitFor(client -> timer.displayTicks(System.nanoTime()) >= 4);
            context.waitTicks(5);
            context.runOnClient(client -> {
                check(timer.displayTicks(System.nanoTime()) == 4, "Tick rate changed the step count");
                check(timer.displayNanos(System.nanoTime()) == 4 * TimerClock.NANOS_PER_TICK,
                        "Game time must use 20 ticks per second");
            });
            command(context, "stop");
            context.waitFor(client -> !timer.clock.isRunning());
            world.getServer().runCommand("tick unfreeze");
            context.waitTicks(5);
            context.runOnClient(client -> check(timer.displayTicks(System.nanoTime()) == 4, "Stopped clock counted new server ticks"));
            command(context, "resume");
            context.waitFor(client -> timer.displayTicks(System.nanoTime()) > 4);
            command(context, "reset");
            context.waitFor(client -> !timer.clock.isRunning() && timer.displayTicks(System.nanoTime()) == 0);

            command(context, "toggle");
            context.waitFor(client -> timer.displayTicks(System.nanoTime()) > 0);
            context.runOnClient(client -> client.gui.setScreen(new PauseScreen(true)));
            context.waitFor(client -> client.isPaused());
            context.waitTicks(5);
            long pausedTicks = context.computeOnClient(client -> timer.displayTicks(System.nanoTime()));
            long pausedNanos = context.computeOnClient(client -> real.displayNanos(System.nanoTime()));
            context.waitTicks(5);
            context.runOnClient(client -> {
                check(timer.displayTicks(System.nanoTime()) == pausedTicks
                                && timer.displayNanos(System.nanoTime()) == pausedTicks * TimerClock.NANOS_PER_TICK,
                        "Single-player pause advanced a game clock unit");
                check(real.displayTicks(System.nanoTime()) > pausedNanos / TimerClock.NANOS_PER_TICK,
                        "Single-player pause stopped the real clock tick units");
                client.gui.setScreen(null);
            });
            context.waitFor(client -> !client.isPaused());
            world.getServer().runCommand("tick freeze");
            context.waitTicks(5);
            command(context, "settings");
            context.waitForScreen(TimerScreen.class);
            context.clickScreenButton("Mode: Stopwatch");
            context.runOnClient(client -> {
                client.gui.screen().children().stream()
                        .filter(EditBox.class::isInstance).map(EditBox.class::cast)
                        .findFirst().orElseThrow().setValue("1");
            });
            context.clickScreenButton("Set seconds");
            context.clickScreenButton("Start");
            context.clickScreenButton("Done");
            command(context, "real settings");
            context.waitForScreen(TimerScreen.class);
            context.clickScreenButton("Mode: Stopwatch");
            context.runOnClient(client -> client.gui.screen().children().stream()
                    .filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow().setValue("1"));
            context.clickScreenButton("Set seconds");
            context.clickScreenButton("Start");
            context.clickScreenButton("Done");
            context.waitFor(client -> !real.clock.isRunning() && real.displayNanos(System.nanoTime()) == 0);
            context.runOnClient(client -> {
                check(timer.clock.isRunning(), "Game countdown completed while frozen");
                check(timer.displayTicks(System.nanoTime()) == 20, "Frozen countdown lost world ticks");
            });
            world.getServer().runCommand("tick step 20");
            context.waitFor(client -> !timer.clock.isRunning());
            context.runOnClient(client -> check(timer.clock.elapsedNanos(System.nanoTime()) == TimerClock.NANOS_PER_SECOND,
                    "One countdown second did not require exactly twenty game ticks"));
        }
    }

    private static void verifyChatControls(ClientGameTestContext context, TimerClient owner) {
        verifyBindings(context, owner);
        TimerSession timer = owner.game;
        context.runOnClient(client -> client.options.guiScale().set(2));
        openChat(context);
        clickWidget(context, label("Start"));
        context.waitFor(client -> timer.clock.isRunning());
        clickWidget(context, label("Stop"));
        context.waitFor(client -> !timer.clock.isRunning());
        clickWidget(context, label("Resume"));
        context.waitFor(client -> timer.clock.isRunning());
        clickWidget(context, label("Reset"));
        context.waitFor(client -> !timer.clock.isRunning() && timer.displayTicks(System.nanoTime()) == 0);

        Point start = widgetPoint(context, label("Start"), -8);
        Point before = widgetPoint(context, label("Settings"), 10);
        drag(context, start, new Point(start.x() + 100, start.y() + 94));
        context.runOnClient(client -> check(timer.settings.hudX > 0 && timer.settings.hudY > 0,
                "Dragging in chat must move the HUD"));
        Point after = widgetPoint(context, label("Settings"), 10);
        check(after.x() > before.x() && after.y() > before.y(), "HUD buttons must move with the timer");
        context.getInput().resizeWindow(900, 600);
        context.waitTicks(2);
        context.runOnClient(client -> {
            long controls = Screens.getWidgets(client.gui.screen()).stream()
                    .filter(label("Start").or(label("Reset")).or(label("Settings")).or(label("Hide HUD"))).count();
            check(controls == 8, "Resizing chat must keep one set of buttons per HUD");
        });
        Point bottomLeft = context.computeOnClient(client -> {
            double scale = client.getWindow().getGuiScale();
            return new Point(2 * scale, (client.gui.screen().height - 2) * scale);
        });
        drag(context, widgetPoint(context, label("Start"), -8), bottomLeft);
        context.runOnClient(client -> check(timer.settings.hudX == 0 && timer.settings.hudY == 1,
                "HUD must be draggable to the bottom-left bound"));

        clickWidget(context, label("Settings"));
        context.waitForScreen(TimerScreen.class);
        clickWidget(context, AbstractSliderButton.class::isInstance);
        clickWidget(context, label("HUD: On"));
        context.clickScreenButton("Done");
        context.waitFor(client -> client.gui.screen() == null);
        context.runOnClient(client -> {
            var saved = TimerSettings.load().game;
            check(saved.opacity >= 45 && saved.opacity <= 55 && !saved.hudVisible, "HUD controls were not saved");
            check(saved.hudX == timer.settings.hudX && saved.hudY == timer.settings.hudY, "Drag position was not saved");
        });

        openChat(context);
        context.runOnClient(client -> check(Screens.getWidgets(client.gui.screen()).stream()
                        .filter(widget -> widget.visible).filter(label("Settings")).count() == 1
                        && owner.real.settings.hudVisible && owner.real.settings.opacity == 100,
                "Hiding or fading one clock must not affect the other"));
        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        context.waitFor(client -> client.gui.screen() == null);
        command(context, "game hud");
        context.waitFor(client -> timer.settings.hudVisible);
        openChat(context);
        clickWidget(context, label("Hide HUD"));
        context.waitFor(client -> !timer.settings.hudVisible && owner.real.settings.hudVisible);
        context.runOnClient(client -> {
            check(!TimerSettings.load().game.hudVisible && client.gui.screen() instanceof ChatScreen,
                    "The chat button must save visibility and keep chat open");
            var widgets = Screens.getWidgets(client.gui.screen());
            check(widgets.stream().filter(widget -> widget.visible).filter(label("Settings")).count() == 1
                            && widgets.stream().noneMatch(label("Show Game Clock HUD")),
                    "A hidden HUD must have no visible controls or restore button");
        });
        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        context.waitFor(client -> client.gui.screen() == null);
        command(context, "hud");
        context.waitFor(client -> !timer.settings.hudVisible && !owner.real.settings.hudVisible);
        openChat(context);
        context.runOnClient(client -> check(Screens.getWidgets(client.gui.screen()).stream()
                        .noneMatch(label("Settings").or(label("Hide HUD"))),
                "Chat must show no timer controls when both HUDs are hidden"));
        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        context.waitFor(client -> client.gui.screen() == null);
        command(context, "real hud");
        context.waitFor(client -> !timer.settings.hudVisible && owner.real.settings.hudVisible);
        command(context, "game hud");
        context.waitFor(client -> client.gui.screen() == null && timer.settings.hudVisible);
    }

    private static void verifyBindings(ClientGameTestContext context, TimerClient timer) {
        context.runOnClient(client -> {
            var bindings = Arrays.stream(client.options.keyMappings)
                    .filter(key -> key.getName().startsWith("key.in_game_timer.")).toList();
            check(bindings.size() == 9 && bindings.stream().allMatch(KeyMapping::isUnbound),
                    "Timer bindings must exist and default to unbound");
            binding(client.options.keyMappings, "menu").setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_F8));
            binding(client.options.keyMappings, "toggle").setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_F6));
            binding(client.options.keyMappings, "reset").setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_K));
            timer.store.legacyBindingsCleared = false;
        });
        context.waitFor(client -> timer.store.legacyBindingsCleared);
        context.runOnClient(client -> {
            check(binding(client.options.keyMappings, "menu").isUnbound()
                            && binding(client.options.keyMappings, "toggle").isUnbound(),
                    "Previously saved default bindings must be cleared");
            KeyMapping reset = binding(client.options.keyMappings, "reset");
            check(KeyMappingHelper.getBoundKeyOf(reset).equals(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_K)),
                    "Custom bindings must survive the migration");
            reset.setKey(InputConstants.UNKNOWN);
            KeyMapping.resetMapping();
            client.options.save();
        });
    }

    private static void verifyTimerSettings(ClientGameTestContext context, TimerClient owner) {
        command(context, "game settings");
        context.waitForScreen(TimerScreen.class);
        clickWidget(context, label("Minimal: Off"));
        context.clickScreenButton("Key Bindings");
        context.waitForScreen(TimerKeyScreen.class);
        context.clickScreenButton("Start / stop / resume: Unbound");
        context.getInput().pressKey(GLFW.GLFW_KEY_J);
        context.clickScreenButton("Show / hide HUD: Unbound");
        context.getInput().pressKey(GLFW.GLFW_KEY_H);
        context.clickScreenButton("Show / hide both HUDs: Unbound");
        pressShiftF(context);
        context.clickScreenButton("Done");
        context.waitForScreen(TimerScreen.class);
        context.clickScreenButton("Done");
        context.waitFor(client -> client.gui.screen() == null);
        context.runOnClient(client -> {
            var saved = TimerSettings.load();
            check(saved.game.minimal && !saved.real.minimal, "Minimal display must be saved separately");
            check(owner.bindings(owner.real).toggle().isUnbound(), "Game shortcut changed the real clock");
        });

        command(context, "real settings");
        context.waitForScreen(TimerScreen.class);
        context.clickScreenButton("Key Bindings");
        context.waitForScreen(TimerKeyScreen.class);
        context.clickScreenButton("Start / stop / resume: Unbound");
        context.getInput().pressKey(GLFW.GLFW_KEY_K);
        context.clickScreenButton("Show / hide HUD: Unbound");
        context.getInput().pressKey(GLFW.GLFW_KEY_U);
        context.runOnClient(client -> check(Screens.getWidgets(client.gui.screen()).stream()
                        .anyMatch(label("Show / hide both HUDs: Shift+F")),
                "Both clocks must display the same shared binding"));
        context.clickScreenButton("Done");
        context.waitForScreen(TimerScreen.class);
        context.clickScreenButton("Done");
        context.waitFor(client -> client.gui.screen() == null);

        context.getInput().pressKey(GLFW.GLFW_KEY_J);
        context.waitFor(client -> owner.game.clock.isRunning() && !owner.real.clock.isRunning());
        context.getInput().pressKey(GLFW.GLFW_KEY_K);
        context.waitFor(client -> owner.game.clock.isRunning() && owner.real.clock.isRunning());
        context.getInput().pressKey(GLFW.GLFW_KEY_H);
        context.waitFor(client -> !owner.game.settings.hudVisible && owner.real.settings.hudVisible);
        context.runOnClient(client -> {
            check(!TimerSettings.load().game.hudVisible, "HUD shortcut must save visibility");
            check(owner.game.clock.isRunning() && owner.real.clock.isRunning(), "HUD shortcut must leave timing active");
        });
        context.getInput().pressKey(GLFW.GLFW_KEY_F);
        context.runOnClient(client -> check(!owner.game.settings.hudVisible && owner.real.settings.hudVisible,
                "A bare key must not trigger a modified shortcut"));
        pressShiftF(context);
        context.waitFor(client -> !owner.game.settings.hudVisible && !owner.real.settings.hudVisible);
        pressShiftF(context);
        context.waitFor(client -> owner.game.settings.hudVisible && owner.real.settings.hudVisible);
        context.runOnClient(client -> {
            var chord = TimerSettings.load().chords.get(owner.bothHuds.getName());
            check(chord != null && chord.modifiers() == GLFW.GLFW_MOD_SHIFT
                            && chord.key().equals(owner.bothHuds.saveString()),
                    "The modifier and base key must survive saving");
        });
        context.getInput().pressKey(GLFW.GLFW_KEY_H);
        context.getInput().pressKey(GLFW.GLFW_KEY_U);
        context.waitFor(client -> !owner.game.settings.hudVisible && !owner.real.settings.hudVisible);
        context.getInput().pressKey(GLFW.GLFW_KEY_H);
        context.getInput().pressKey(GLFW.GLFW_KEY_U);
        context.waitFor(client -> owner.game.settings.hudVisible && owner.real.settings.hudVisible);
        command(context, "game reset");
        command(context, "real reset");

        command(context, "game settings");
        context.waitForScreen(TimerScreen.class);
        context.clickScreenButton("Key Bindings");
        context.waitForScreen(TimerKeyScreen.class);
        clickWidget(context, widget -> widget.getMessage().getString().startsWith("Start / stop / resume:"));
        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        context.runOnClient(client -> check(owner.bindings(owner.game).toggle().isUnbound()
                        && !owner.bindings(owner.real).toggle().isUnbound(),
                "Esc must clear only the shortcut being edited"));
        context.clickScreenButton("Done");
        context.waitForScreen(TimerScreen.class);
        context.clickScreenButton("Done");
        context.waitFor(client -> client.gui.screen() == null);
    }

    private static void pressShiftF(ClientGameTestContext context) {
        context.runOnClient(client -> {
            // Fabric's test input sends a zero modifier mask even when holdShift() is active.
            var keyboard = (KeyboardHandlerAccessor) client.keyboardHandler;
            long window = client.getWindow().handle();
            var shift = new KeyEvent(GLFW.GLFW_KEY_LEFT_SHIFT, 0, GLFW.GLFW_MOD_SHIFT);
            var chord = new KeyEvent(GLFW.GLFW_KEY_F, 0, GLFW.GLFW_MOD_SHIFT);
            keyboard.invokeKeyPress(window, GLFW.GLFW_PRESS, shift);
            keyboard.invokeKeyPress(window, GLFW.GLFW_PRESS, chord);
            keyboard.invokeKeyPress(window, GLFW.GLFW_RELEASE, chord);
            keyboard.invokeKeyPress(window, GLFW.GLFW_RELEASE, new KeyEvent(GLFW.GLFW_KEY_LEFT_SHIFT, 0, 0));
        });
    }

    private static KeyMapping binding(KeyMapping[] keys, String action) {
        return Arrays.stream(keys).filter(key -> key.getName().equals("key.in_game_timer." + action))
                .findFirst().orElseThrow();
    }

    private static void command(ClientGameTestContext context, String command) {
        openChat(context);
        context.getInput().typeChars("/igt " + command);
        context.getInput().pressKey(GLFW.GLFW_KEY_ENTER);
        context.waitFor(client -> !(client.gui.screen() instanceof ChatScreen));
    }

    private static void openChat(ClientGameTestContext context) {
        context.getInput().pressKey(GLFW.GLFW_KEY_T);
        context.waitForScreen(ChatScreen.class);
    }

    private static Predicate<AbstractWidget> label(String label) {
        return widget -> widget.getMessage().getString().equals(label);
    }

    private static void clickWidget(ClientGameTestContext context, Predicate<AbstractWidget> matches) {
        Point point = widgetPoint(context, matches, 10);
        context.getInput().setCursorPos(point.x(), point.y());
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }

    private static void drag(ClientGameTestContext context, Point from, Point to) {
        context.getInput().setCursorPos(from.x(), from.y());
        context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        context.getInput().setCursorPos(to.x(), to.y());
        context.waitTick();
        context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }

    private static Point widgetPoint(ClientGameTestContext context, Predicate<AbstractWidget> matches, int yOffset) {
        return context.computeOnClient(client -> {
            AbstractWidget widget = Screens.getWidgets(client.gui.screen()).stream().filter(matches).findFirst().orElseThrow();
            double scale = client.getWindow().getGuiScale();
            return new Point((widget.getX() + widget.getWidth() / 2.0) * scale, (widget.getY() + yOffset) * scale);
        });
    }

    private record Point(double x, double y) {}

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
