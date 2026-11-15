package io.github.ruok0214.ingametimer;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import org.lwjgl.glfw.GLFW;

import java.util.Arrays;
import java.util.function.Predicate;

@SuppressWarnings("UnstableApiUsage")
public final class TimerGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        TimerClient timer = (TimerClient) FabricLoader.getInstance()
                .getEntrypointContainers("client", ClientModInitializer.class).stream()
                .filter(entry -> entry.getProvider().getMetadata().getId().equals("in_game_timer"))
                .findFirst().orElseThrow().getEntrypoint();
        try (var world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksDownload();
            context.waitFor(client -> timer.clock.hasTickClock());
            verifyChatControls(context, timer);

            world.getServer().runCommand("tick freeze");
            world.getServer().runCommand("tick rate 40");
            context.waitTicks(5);
            command(context, "start");
            context.waitFor(client -> timer.clock.isRunning());
            long nanos = context.computeOnClient(client -> timer.clock.elapsedNanos(System.nanoTime()));
            context.waitTicks(5);
            context.runOnClient(client -> {
                check(timer.clock.elapsedTicks() == 0, "Frozen server advanced the tick clock");
                check(timer.clock.elapsedNanos(System.nanoTime()) > nanos, "Real-time clock stopped during freeze");
            });

            world.getServer().runCommand("tick step 4");
            context.waitFor(client -> timer.clock.elapsedTicks() >= 4);
            context.waitTicks(5);
            context.runOnClient(client -> check(timer.clock.elapsedTicks() == 4, "Tick rate changed the step count"));
            command(context, "stop");
            context.waitFor(client -> !timer.clock.isRunning());
            world.getServer().runCommand("tick unfreeze");
            context.waitTicks(5);
            context.runOnClient(client -> check(timer.clock.elapsedTicks() == 4, "Stopped clock counted new server ticks"));
            command(context, "resume");
            context.waitFor(client -> timer.clock.elapsedTicks() > 4);
            command(context, "reset");
            context.waitFor(client -> !timer.clock.isRunning() && timer.clock.elapsedTicks() == 0);

            command(context, "toggle");
            context.waitFor(client -> timer.clock.elapsedTicks() > 0);
            context.runOnClient(client -> client.gui.setScreen(new PauseScreen(true)));
            context.waitFor(client -> client.isPaused());
            context.waitTicks(5);
            long pausedTicks = context.computeOnClient(client -> timer.clock.elapsedTicks());
            long pausedNanos = context.computeOnClient(client -> timer.clock.elapsedNanos(System.nanoTime()));
            context.waitTicks(5);
            context.runOnClient(client -> {
                check(timer.clock.elapsedTicks() == pausedTicks, "Single-player pause advanced the tick clock");
                check(timer.clock.elapsedNanos(System.nanoTime()) > pausedNanos, "Single-player pause stopped real time");
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
            context.waitFor(client -> timer.clock.elapsedNanos(System.nanoTime()) >= TimerClock.NANOS_PER_SECOND);
            context.runOnClient(client -> {
                check(timer.clock.isRunning(), "Countdown completed while the tick clock was frozen");
                check(timer.clock.elapsedTicks() == 0, "Frozen countdown counted world ticks");
            });
            world.getServer().runCommand("tick step 20");
            context.waitFor(client -> !timer.clock.isRunning());
            context.runOnClient(client -> check(timer.clock.elapsedTicks() == 20,
                    "One countdown second did not require exactly twenty game ticks"));
        }
    }

    private static void verifyChatControls(ClientGameTestContext context, TimerClient timer) {
        verifyBindings(context, timer);
        context.runOnClient(client -> client.options.guiScale().set(2));
        openChat(context);
        clickWidget(context, label("Start"));
        context.waitFor(client -> timer.clock.isRunning());
        clickWidget(context, label("Stop"));
        context.waitFor(client -> !timer.clock.isRunning());
        clickWidget(context, label("Resume"));
        context.waitFor(client -> timer.clock.isRunning());
        clickWidget(context, label("Reset"));
        context.waitFor(client -> !timer.clock.isRunning() && timer.clock.elapsedTicks() == 0);

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
                    .filter(label("Start").or(label("Reset")).or(label("Settings"))).count();
            check(controls == 3, "Resizing chat must keep a single set of HUD buttons");
        });
        Point bottomRight = context.computeOnClient(client -> {
            double scale = client.getWindow().getGuiScale();
            return new Point((client.gui.screen().width - 2) * scale, (client.gui.screen().height - 2) * scale);
        });
        drag(context, widgetPoint(context, label("Start"), -8), bottomRight);
        context.runOnClient(client -> check(timer.settings.hudX == 1 && timer.settings.hudY == 1,
                "HUD must be draggable to the bottom-right bound"));

        clickWidget(context, label("Settings"));
        context.waitForScreen(TimerScreen.class);
        clickWidget(context, AbstractSliderButton.class::isInstance);
        clickWidget(context, label("HUD: On"));
        context.clickScreenButton("Done");
        context.waitFor(client -> client.gui.screen() == null);
        context.runOnClient(client -> {
            var saved = TimerSettings.load();
            check(saved.opacity >= 45 && saved.opacity <= 55 && !saved.hudVisible, "HUD controls were not saved");
            check(saved.hudX == timer.settings.hudX && saved.hudY == timer.settings.hudY, "Drag position was not saved");
        });

        openChat(context);
        context.runOnClient(client -> check(Screens.getWidgets(client.gui.screen()).stream().noneMatch(label("Settings")),
                "Hidden HUD must not add chat buttons"));
        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        context.waitFor(client -> client.gui.screen() == null);
        command(context, "settings");
        context.waitForScreen(TimerScreen.class);
        clickWidget(context, label("HUD: Off"));
        context.clickScreenButton("Done");
        context.waitFor(client -> client.gui.screen() == null && timer.settings.hudVisible);
    }

    private static void verifyBindings(ClientGameTestContext context, TimerClient timer) {
        context.runOnClient(client -> {
            var bindings = Arrays.stream(client.options.keyMappings)
                    .filter(key -> key.getName().startsWith("key.in_game_timer.")).toList();
            check(bindings.size() == 3 && bindings.stream().allMatch(KeyMapping::isUnbound),
                    "Timer bindings must exist and default to unbound");
            binding(client.options.keyMappings, "menu").setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_F8));
            binding(client.options.keyMappings, "toggle").setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_F6));
            binding(client.options.keyMappings, "reset").setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_K));
            timer.settings.legacyBindingsCleared = false;
        });
        context.waitFor(client -> timer.settings.legacyBindingsCleared);
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
