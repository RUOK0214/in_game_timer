package io.github.ruok0214.ingametimer;

final class TimerClock {
    static final long NANOS_PER_SECOND = 1_000_000_000L;
    static final int TICKS_PER_SECOND = 20;
    private static final long UNKNOWN = -1;

    private long serverTick = UNKNOWN;
    private long startedTick = UNKNOWN;
    private long startedNanos;
    private long savedTicks;
    private long savedNanos;
    private boolean running;

    void observeTick(long tick) {
        serverTick = tick;
        if (running && startedTick == UNKNOWN) startedTick = tick;
    }

    void changeWorld() {
        reset();
        serverTick = UNKNOWN;
    }

    boolean hasTickClock() {
        return serverTick != UNKNOWN;
    }

    boolean isRunning() {
        return running;
    }

    void start(long now) {
        if (running) return;
        startedTick = serverTick;
        startedNanos = now;
        running = true;
    }

    void stop(long now) {
        savedTicks = elapsedTicks();
        savedNanos = elapsedNanos(now);
        running = false;
    }

    void reset() {
        running = false;
        savedTicks = 0;
        savedNanos = 0;
    }

    long elapsedTicks() {
        return savedTicks + (running && startedTick != UNKNOWN ? serverTick - startedTick : 0);
    }

    long elapsedNanos(long now) {
        return savedNanos + (running ? now - startedNanos : 0);
    }

    boolean expired(long now, int seconds) {
        return elapsedNanos(now) >= seconds * NANOS_PER_SECOND
                && (!hasTickClock() || elapsedTicks() >= seconds * (long) TICKS_PER_SECOND);
    }
}
