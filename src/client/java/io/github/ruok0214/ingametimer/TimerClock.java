package io.github.ruok0214.ingametimer;

final class TimerClock {
    static final long NANOS_PER_SECOND = 1_000_000_000L;
    static final int TICKS_PER_SECOND = 20;
    static final long NANOS_PER_TICK = NANOS_PER_SECOND / TICKS_PER_SECOND;

    private final boolean gameBased;
    private long gameTime = -1;
    private long startedAt;
    private long savedNanos;
    private boolean running;

    TimerClock(boolean gameBased) {
        this.gameBased = gameBased;
    }

    void observeTick(long tick) {
        long sample = tick * NANOS_PER_TICK;
        if (gameTime < 0 && running) startedAt = sample;
        gameTime = sample;
    }

    void changeWorld() {
        reset();
        gameTime = -1;
    }

    boolean isAvailable() {
        return !gameBased || gameTime >= 0;
    }

    boolean isRunning() {
        return running;
    }

    void start(long now) {
        if (running) return;
        startedAt = sourceTime(now);
        running = true;
    }

    void stop(long now) {
        savedNanos = elapsedNanos(now);
        running = false;
    }

    void reset() {
        savedNanos = 0;
        running = false;
    }

    long elapsedNanos(long now) {
        return savedNanos + (running && isAvailable() ? sourceTime(now) - startedAt : 0);
    }

    private long sourceTime(long now) {
        return gameBased ? gameTime : now;
    }
}
