package io.github.ruok0214.ingametimer;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.PauseScreen;
import org.lwjgl.glfw.GLFW;

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
            context.getInput().pressKey(GLFW.GLFW_KEY_F8);
            context.waitForScreen(TimerScreen.class);
            context.clickScreenButton("Done");
            context.waitFor(client -> client.gui.screen() == null);

            world.getServer().runCommand("tick freeze");
            world.getServer().runCommand("tick rate 40");
            context.waitTicks(5);
            context.getInput().pressKey(GLFW.GLFW_KEY_F6);
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
            context.getInput().pressKey(GLFW.GLFW_KEY_F6);
            context.waitFor(client -> !timer.clock.isRunning());
            world.getServer().runCommand("tick unfreeze");
            context.waitTicks(5);
            context.runOnClient(client -> check(timer.clock.elapsedTicks() == 4, "Stopped clock counted new server ticks"));
            context.getInput().pressKey(GLFW.GLFW_KEY_F7);
            context.waitFor(client -> timer.clock.elapsedTicks() == 0);

            context.getInput().pressKey(GLFW.GLFW_KEY_F6);
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
            context.getInput().pressKey(GLFW.GLFW_KEY_F8);
            context.waitForScreen(TimerScreen.class);
            context.clickScreenButton("Mode: Stopwatch");
            context.clickScreenButton("HUD position: Top left");
            context.runOnClient(client -> {
                check(timer.settings.corner == TimerSettings.Corner.TOP_RIGHT, "HUD position did not change");
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

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
