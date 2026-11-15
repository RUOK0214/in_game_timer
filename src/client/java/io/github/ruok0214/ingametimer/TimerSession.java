package io.github.ruok0214.ingametimer;

import net.minecraft.network.chat.Component;

import static io.github.ruok0214.ingametimer.TimerClient.text;

final class TimerSession {
    final String id;
    final TimerSettings settings;
    final TimerClock clock;
    final TimerSettings.Store store;
    private final boolean gameBased;

    TimerSession(String id, boolean gameBased, TimerSettings.Store store, TimerSettings settings) {
        this.id = id;
        this.gameBased = gameBased;
        this.store = store;
        this.settings = settings;
        clock = new TimerClock(gameBased);
    }

    Component title() {
        return text("clock." + id);
    }

    void start() {
        long now = System.nanoTime();
        if (clock.isRunning()) return;
        if (expired(now)) clock.reset();
        clock.start(now);
    }

    void stop() {
        clock.stop(System.nanoTime());
    }

    void toggle() {
        updateCompletion(System.nanoTime());
        if (clock.isRunning()) stop();
        else start();
    }

    void toggleHud() {
        settings.hudVisible = !settings.hudVisible;
        store.save();
    }

    Component toggleLabel() {
        if (clock.isRunning()) return text("stop");
        long now = System.nanoTime();
        return text(clock.elapsedNanos(now) > 0 && !expired(now) ? "resume" : "start");
    }

    void updateCompletion(long now) {
        if (clock.isRunning() && expired(now)) clock.stop(now);
    }

    long displayNanos(long now) {
        long elapsed = clock.elapsedNanos(now);
        return settings.countdown ? Math.max(0, duration() - elapsed) : elapsed;
    }

    long displayTicks(long now) {
        long nanos = displayNanos(now);
        if (settings.countdown && !gameBased) {
            return Math.ceilDiv(nanos, TimerClock.NANOS_PER_TICK);
        }
        return nanos / TimerClock.NANOS_PER_TICK;
    }

    private boolean expired(long now) {
        return settings.countdown && clock.elapsedNanos(now) >= duration();
    }

    private long duration() {
        return settings.seconds * TimerClock.NANOS_PER_SECOND;
    }
}
